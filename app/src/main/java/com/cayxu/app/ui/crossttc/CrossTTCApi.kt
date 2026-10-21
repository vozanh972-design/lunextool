package com.cayxu.app.ui.crossttc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class CrossTTCLoginResult(
    val isSuccess: Boolean,
    val username: String = "",
    val coins: Long = 0L,
    val sessionCookie: String = "",
    val errorMessage: String = "",
)

data class CrossTTCTikTokAccountInfo(
    val id: String,
    val username: String,
    val isActive: Boolean = false,
)

data class CrossTTCLinkResult(
    val isSuccess: Boolean,
    val message: String = "",
)

data class CrossTTCTaskJob(
    val id: String,
    val link: String,
    val type: String = "follow",
    val uid: String = "",
)

data class CrossTTCClaimResult(
    val isSuccess: Boolean,
    val earnedCoins: Long = 0L,
    val totalCoins: Long = 0L,
    val message: String = "",
)

/**
 * Client kết nối mạng độc lập cho Tương tác chéo TikTok.
 * Chỉ phục vụ riêng module TikTok TTC, không chia sẻ hay phụ thuộc vào module khác.
 */
object CrossTTCApi {
    private const val BASE_URL = "https://tuongtaccheo.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-A556B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun buildHeaders(sessionCookie: String = ""): Headers {
        val b = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Accept", "application/json, text/javascript, */*; q=0.01")
            .add("Accept-Language", "vi-VN,vi;q=0.9")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Origin", BASE_URL)
            .add("Host", "tuongtaccheo.com")
        if (sessionCookie.isNotBlank()) {
            b.add("Cookie", sessionCookie)
        }
        return b.build()
    }

    /**
     * Gọi API đăng nhập bằng Access Token chuẩn từ cURL (POST /logintoken.php).
     */
    suspend fun loginWithToken(token: String): CrossTTCLoginResult = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) {
            return@withContext CrossTTCLoginResult(false, errorMessage = "Vui lòng nhập Access Token")
        }

        try {
            val body = FormBody.Builder()
                .add("access_token", trimmedToken)
                .build()

            val req = Request.Builder()
                .url("$BASE_URL/logintoken.php")
                .headers(buildHeaders())
                .post(body)
                .build()

            httpClient.newCall(req).execute().use { res ->
                val resStr = res.body?.string()?.trim().orEmpty()
                val setCookies = res.headers("Set-Cookie")
                val cookieSb = StringBuilder()
                for (c in setCookies) {
                    cookieSb.append(c.split(";")[0]).append("; ")
                }
                val sessionCookie = cookieSb.toString().trim()

                if (resStr.isBlank()) {
                    return@withContext CrossTTCLoginResult(false, errorMessage = "Máy chủ TTC không phản hồi")
                }

                val json = if (resStr.startsWith("{")) JSONObject(resStr) else JSONObject()
                val status = json.optString("status")

                if (status == "fail" || status == "error") {
                    val msg = json.optString("mess").ifBlank {
                        json.optString("message", "Sai Access Token hoặc tài khoản không hợp lệ")
                    }
                    return@withContext CrossTTCLoginResult(false, errorMessage = msg)
                }

                val dataObj = json.optJSONObject("data")

                // Trích xuất username TTC
                val username = json.optString("user")
                    .ifBlank { dataObj?.optString("user") ?: "" }
                    .ifBlank { json.optString("username") }
                    .ifBlank { dataObj?.optString("username") ?: "" }
                    .trim()

                // Trích xuất số dư xu TTC
                val rawSodu = json.optString("sodu")
                    .ifBlank { dataObj?.optString("sodu") ?: "" }
                    .ifBlank { json.optString("xu") }
                    .ifBlank { dataObj?.optString("xu") ?: "" }
                    .ifBlank { if (json.has("sodu")) json.optLong("sodu").toString() else "" }
                    .ifBlank { if (dataObj?.has("sodu") == true) dataObj.optLong("sodu").toString() else "" }

                val coins = rawSodu.replace(",", "").replace(".", "").trim().toLongOrNull() ?: 0L

                if (username.isBlank() && status != "success") {
                    val errMsg = json.optString("mess").ifBlank { "Access Token không chính xác" }
                    return@withContext CrossTTCLoginResult(false, errorMessage = errMsg)
                }

                CrossTTCLoginResult(
                    isSuccess = true,
                    username = if (username.isNotBlank()) username else "TTC_$trimmedToken",
                    coins = coins,
                    sessionCookie = sessionCookie,
                )
            }
        } catch (e: Exception) {
            CrossTTCLoginResult(
                isSuccess = false,
                errorMessage = "Lỗi kết nối: ${e.localizedMessage ?: "Vui lòng kiểm tra lại mạng"}",
            )
        }
    }

    /**
     * Lấy danh sách tài khoản TikTok đã cấu hình trên TTC (từ /cauhinh/tiktok.php).
     */
    suspend fun getTtcTikTokAccounts(sessionCookie: String): List<CrossTTCTikTokAccountInfo> = withContext(Dispatchers.IO) {
        if (sessionCookie.isBlank()) return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url("$BASE_URL/cauhinh/tiktok.php")
                .headers(buildHeaders(sessionCookie))
                .header("Referer", "$BASE_URL/home.php")
                .get()
                .build()

            httpClient.newCall(req).execute().use { res ->
                val html = res.body?.string().orEmpty()
                val list = mutableListOf<CrossTTCTikTokAccountInfo>()

                // Tìm nick đang chạy
                val activePattern = Pattern.compile("tiktok\\.com/@([^?\'\"]+)")
                val activeMatcher = activePattern.matcher(html)
                val activeNick = if (activeMatcher.find()) activeMatcher.group(1).orEmpty().trim() else ""

                // Tìm danh sách nick trong dsnicktt
                val itemPattern = Pattern.compile("value=['\"](\\d+)['\"].*?/>\\s*([^<]+)")
                val itemMatcher = itemPattern.matcher(html)
                while (itemMatcher.find()) {
                    val uid = itemMatcher.group(1).orEmpty().trim()
                    val handle = itemMatcher.group(2).orEmpty().trim()
                    if (uid.isNotBlank() && handle.isNotBlank()) {
                        list.add(
                            CrossTTCTikTokAccountInfo(
                                id = uid,
                                username = handle,
                                isActive = handle.equals(activeNick, ignoreCase = true)
                            )
                        )
                    }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Thêm / liên kết nick TikTok thật vào Tương tác chéo (POST /cauhinh/nhapnick.php).
     */
    suspend fun linkTikTokAccount(sessionCookie: String, handle: String): CrossTTCLinkResult = withContext(Dispatchers.IO) {
        val cleanHandle = handle.trim().removePrefix("@")
        if (cleanHandle.isBlank()) {
            return@withContext CrossTTCLinkResult(false, "Username TikTok không hợp lệ")
        }
        try {
            val formBody = FormBody.Builder()
                .add("link", cleanHandle)
                .add("loainick", "tiktok")
                .add("recaptcha", "1")
                .build()

            val req = Request.Builder()
                .url("$BASE_URL/cauhinh/nhapnick.php")
                .headers(buildHeaders(sessionCookie))
                .header("Referer", "$BASE_URL/cauhinh/tiktok.php")
                .post(formBody)
                .build()

            httpClient.newCall(req).execute().use { res ->
                val resStr = res.body?.string().orEmpty().trim()
                var msg = resStr
                var isOk = false
                if (resStr.startsWith("{")) {
                    try {
                        val json = JSONObject(resStr)
                        val status = json.optInt("status", -1)
                        msg = json.optString("mess").ifBlank { json.optString("message", resStr) }
                        isOk = status == 1 || resStr.contains("Thành công") || resStr.contains("thành công")
                    } catch (_: Exception) {}
                } else {
                    isOk = resStr.contains("Thành công") || resStr.contains("thành công") || resStr == "1"
                }

                if (isOk) {
                    CrossTTCLinkResult(true, if (msg.isNotBlank()) msg else "Thêm nick thành công")
                } else {
                    val errMsg = if (msg.isNotBlank()) msg else "Không thể liên kết nick TikTok trên TTC"
                    CrossTTCLinkResult(false, errMsg)
                }
            }
        } catch (e: Exception) {
            CrossTTCLinkResult(false, "Lỗi kết nối: ${e.localizedMessage ?: "Vui lòng thử lại"}")
        }
    }

    /**
     * Đặt nick TikTok làm nick chạy mặc định trên TTC (POST /cauhinh/datnick.php với iddat[]=uid & loai=tt).
     */
    suspend fun setDefaultTikTokAccount(sessionCookie: String, uid: String): CrossTTCLinkResult = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return@withContext CrossTTCLinkResult(false, "UID tài khoản TikTok không hợp lệ")
        }
        try {
            val formBody = FormBody.Builder()
                .add("iddat[]", cleanUid)
                .add("loai", "tt")
                .build()

            val req = Request.Builder()
                .url("$BASE_URL/cauhinh/datnick.php")
                .headers(buildHeaders(sessionCookie))
                .header("Referer", "$BASE_URL/cauhinh/tiktok.php")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBody)
                .build()

            httpClient.newCall(req).execute().use { res ->
                val resStr = res.body?.string().orEmpty().trim()
                val isSuccess = res.isSuccessful && (resStr == "1" || resStr.contains("\"status\":1") || resStr.contains("Thành công") || resStr.contains("thành công") || resStr.contains("Cấu hình thành công"))
                val msg = when {
                    isSuccess -> "Đặt nick chạy mặc định thành công"
                    resStr == "2" || resStr.contains("chưa thêm") -> "Nick chưa được liên kết trên TTC, vui lòng liên kết trước"
                    resStr == "-1" || resStr.contains("chậm lại") -> "Thao tác quá nhanh, vui lòng thử lại sau vài giây"
                    else -> resStr.ifBlank { "Lỗi cấu hình nick mặc định" }
                }
                CrossTTCLinkResult(isSuccess, msg)
            }
        } catch (e: Exception) {
            CrossTTCLinkResult(false, "Lỗi kết nối đặt nick: ${e.localizedMessage ?: "Vui lòng thử lại"}")
        }
    }

    /**
     * Lấy danh sách nhiệm vụ TikTok từ TTC (GET /tiktok/kiemtien/subcheo/getpost.php).
     */
    suspend fun getTikTokJobs(
        sessionCookie: String,
        accessToken: String,
        jobType: String = "subcheo",
    ): List<CrossTTCTaskJob> = withContext(Dispatchers.IO) {
        val path = if (jobType.contains("tim", ignoreCase = true) || jobType.contains("like", ignoreCase = true)) "cheotim" else "subcheo"
        val url = "$BASE_URL/tiktok/kiemtien/$path/getpost.php"
        // Referer chuẩn trên web TTC không chứa access_token trong query param
        val referer = "$BASE_URL/tiktok/kiemtien/$path/"
        try {
            val req = Request.Builder()
                .url(url)
                .headers(buildHeaders(sessionCookie))
                .header("Referer", referer)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .get()
                .build()

            httpClient.newCall(req).execute().use { res ->
                val resStr = res.body?.string().orEmpty().trim()
                if (resStr.contains("Hết Job") || resStr.contains("countdown") || resStr.contains("\"error\"") || resStr.isBlank()) {
                    return@withContext emptyList()
                }
                val list = mutableListOf<CrossTTCTaskJob>()
                if (resStr.startsWith("[")) {
                    val arr = JSONArray(resStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("idpost").ifBlank { obj.optString("id") }
                        val link = obj.optString("link").ifBlank { obj.optString("uid") }
                        val uid = obj.optString("uid")
                        if (id.isNotBlank() && link.isNotBlank()) {
                            list.add(CrossTTCTaskJob(id, link, if (path == "cheotim") "like" else "follow", uid))
                        }
                    }
                } else if (resStr.startsWith("{")) {
                    val j = JSONObject(resStr)
                    val arr = j.optJSONArray("data") ?: j.optJSONArray("posts")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val id = obj.optString("idpost").ifBlank { obj.optString("id") }
                            val link = obj.optString("link").ifBlank { obj.optString("uid") }
                            val uid = obj.optString("uid")
                            if (id.isNotBlank() && link.isNotBlank()) {
                                list.add(CrossTTCTaskJob(id, link, if (path == "cheotim") "like" else "follow", uid))
                            }
                        }
                    }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Nhận xu thưởng sau khi hoàn thành nhiệm vụ TikTok trên TTC (POST /tiktok/kiemtien/subcheo/nhantien.php).
     * Hỗ trợ nhận gom nhiều nhiệm vụ theo format web TTC: id=id1,id2,id3...
     */
    suspend fun claimTikTokJobReward(
        sessionCookie: String,
        accessToken: String,
        jobType: String = "subcheo",
        jobIds: List<String>,
    ): CrossTTCClaimResult = withContext(Dispatchers.IO) {
        if (jobIds.isEmpty()) {
            return@withContext CrossTTCClaimResult(false, 0L, 0L, "Danh sách nhiệm vụ rỗng")
        }
        val path = if (jobType.contains("tim", ignoreCase = true) || jobType.contains("like", ignoreCase = true)) "cheotim" else "subcheo"
        val url = "$BASE_URL/tiktok/kiemtien/$path/nhantien.php"
        val referer = "$BASE_URL/tiktok/kiemtien/$path/"
        val idPayload = jobIds.joinToString(",")
        try {
            val formBody = FormBody.Builder()
                .add("id", idPayload)
                .build()

            val req = Request.Builder()
                .url(url)
                .headers(buildHeaders(sessionCookie))
                .header("Referer", referer)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBody)
                .build()

            httpClient.newCall(req).execute().use { res ->
                val resStr = res.body?.string().orEmpty().trim()
                val isOk = res.isSuccessful && (resStr.contains("\"success\"") || resStr.contains("\"status\":\"success\"") || resStr.contains("Thành công") || resStr.contains("thành công") || resStr.contains("xu"))
                var earned = 0L
                var sodu = 0L
                var msg = resStr
                if (resStr.startsWith("{")) {
                    try {
                        val j = JSONObject(resStr)
                        earned = j.optLong("xu", j.optLong("xu_them", 0L))
                        val rawSodu = j.optString("sodu", "0").replace(",", "").replace(".", "").trim()
                        sodu = rawSodu.toLongOrNull() ?: 0L
                        msg = j.optString("mess").ifBlank { j.optString("message", resStr) }
                    } catch (_: Exception) {}
                }
                CrossTTCClaimResult(isOk, earned, sodu, msg)
            }
        } catch (e: Exception) {
            CrossTTCClaimResult(false, 0L, 0L, "Lỗi nhận xu: ${e.localizedMessage ?: "Vui lòng thử lại"}")
        }
    }
}

