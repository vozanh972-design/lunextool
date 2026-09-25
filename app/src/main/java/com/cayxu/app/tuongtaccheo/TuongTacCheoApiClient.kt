package com.cayxu.app.tuongtaccheo

import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Trích xuất chuẩn 100% từ class `LF2/G;` và `Ll2/w;` trong APK.
 * Client giao tiếp toàn bộ API của TuongTacCheo (TTC).
 */
class TuongTacCheoApiClient(
    var tokenTTC: String = "",
    var sessionCookie: String = "",
    proxyStr: String? = null
) {
    companion object {
        const val BASE_URL = "https://tuongtaccheo.com"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mi 9T Pro) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/12.1 Chrome/79.0.3945.136 Mobile Safari/537.36"

        val SODU_REGEX = Pattern.compile("class=\"soduchinh\">([0-9.,]+)")
    }

    private var httpClient: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)

        if (!proxyStr.isNullOrEmpty()) {
            setupProxy(builder, proxyStr)
        }
        httpClient = builder.build()
    }

    private fun setupProxy(builder: OkHttpClient.Builder, proxyStr: String) {
        try {
            val clean = proxyStr.trim()
            val parts = clean.split(":")
            if (parts.size >= 2) {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: return
                builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))

                if (parts.size >= 4) {
                    val user = parts[2].trim()
                    val pass = parts[3].trim()
                    builder.proxyAuthenticator { _, response ->
                        val credential = Credentials.basic(user, pass)
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildHeaders(): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Accept", "application/json, text/javascript, */*; q=0.01")
            .add("Accept-Language", "vi")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Host", "tuongtaccheo.com")
            .add("Origin", BASE_URL)
            .add("Referer", "$BASE_URL/home.php")

        if (sessionCookie.isNotEmpty()) {
            builder.add("Cookie", sessionCookie)
        }
        return builder.build()
    }

    /**
     * 1. Đăng nhập TTC bằng Token (logintoken.php)
     */
    @Throws(Exception::class)
    fun loginWithToken(token: String): TuongTacCheoAccount {
        this.tokenTTC = token
        val formBody = FormBody.Builder()
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/logintoken.php")
            .headers(buildHeaders())
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IllegalStateException("Lỗi server HTTP: ${response.code}")
            }

            // Trích xuất Session Cookie từ Set-Cookie
            val cookies = response.headers("Set-Cookie")
            val cookieSb = StringBuilder()
            for (c in cookies) {
                val part = c.split(";")[0]
                cookieSb.append(part).append("; ")
            }
            sessionCookie = cookieSb.toString().trim()

            val json = JSONObject(body)
            val status = json.optString("status", "")
            if (status != "success" && !body.contains("user")) {
                val msg = json.optString("mess", json.optString("error", "Không Thể Login Token TTC"))
                throw IllegalStateException(msg)
            }

            val dataObj = json.optJSONObject("data")
            val user = dataObj?.optString("user")?.takeIf { it.isNotBlank() }
                ?: json.optString("user").takeIf { it.isNotBlank() }
                ?: "Unknown"
            val sodu = dataObj?.optLong("sodu")
                ?: run {
                    val soduRaw = dataObj?.optString("sodu") ?: json.optString("sodu", "0")
                    val soduClean = soduRaw.replace(",", "").replace(".", "").trim()
                    soduClean.toLongOrNull() ?: 0L
                }

            return TuongTacCheoAccount(
                username = user,
                token = token,
                cookie = sessionCookie,
                sodu = sodu
            )
        }
    }

    data class SetNickResult(
        val isSuccess: Boolean,
        val message: String = "",
        val rawResponse: String = "",
        val httpCode: Int = 200
    )

    private fun parseTtcErrorMessage(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Phản hồi rỗng từ máy chủ"
        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)
                for (key in listOf("mess", "message", "error", "msg", "thongbao", "noti")) {
                    if (json.has(key)) {
                        val v = json.optString(key, "")
                        if (v.isNotBlank()) return v
                    }
                }
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val arr = JSONArray(trimmed)
                if (arr.length() > 0) {
                    val first = arr.optJSONObject(0)
                    if (first != null) {
                        for (key in listOf("mess", "message", "error", "msg", "thongbao", "noti")) {
                            if (first.has(key)) {
                                val v = first.optString(key, "")
                                if (v.isNotBlank()) return v
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val stripped = trimmed.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        return if (stripped.isNotBlank()) stripped.take(150) else trimmed.take(150)
    }

    /**
     * 2. Cấu hình Nick chạy Job (cauhinh/datnick.php)
     * @param uid ID Facebook hoặc username TikTok cần đặt làm nick chạy
     * @param loai "fb" hoặc "tiktok"
     */
    fun setNickRunDetailed(uid: String, loai: String = "fb"): SetNickResult {
        var lastRawResponse = ""
        var lastHttpCode = 200
        var lastErrorMsg = ""

        // 1. Thử gọi API đặt nick trực tiếp: api.php?do=datnick&id=<UID>
        try {
            val reqApi = Request.Builder()
                .url("$BASE_URL/api.php?do=datnick&id=$uid")
                .headers(buildHeaders())
                .get()
                .build()

            httpClient.newCall(reqApi).execute().use { response ->
                lastHttpCode = response.code
                val rawResponse = response.body?.string() ?: ""
                lastRawResponse = rawResponse
                Log.e("TTC_DEBUG", "Response TTC: " + rawResponse)

                if (!response.isSuccessful) {
                    lastErrorMsg = "Lỗi kết nối TTC (Mã: ${response.code})"
                } else {
                    val isSuccess = rawResponse.contains("\"status\":1") || 
                        rawResponse.contains("\"status\":\"success\"") || 
                        rawResponse.contains("Cấu hình thành công") || 
                        rawResponse.contains("Thành công") || 
                        rawResponse.contains("\"success\"") ||
                        rawResponse.trim() == "1"

                    if (isSuccess) {
                        return SetNickResult(isSuccess = true, message = "Cấu hình thành công", rawResponse = rawResponse, httpCode = response.code)
                    }

                    val serverMsg = parseTtcErrorMessage(rawResponse)
                    lastErrorMsg = "Lỗi TTC: $serverMsg"
                    return SetNickResult(isSuccess = false, message = lastErrorMsg, rawResponse = rawResponse, httpCode = response.code)
                }
            }
        } catch (e: Exception) {
            Log.e("TTC_DEBUG", "Response TTC (Exception): " + e.message, e)
            lastErrorMsg = "Lỗi kết nối TTC (Mã: Mất mạng)"
        }

        // 2. Thử gọi qua endpoint web nếu endpoint 1 lỗi kết nối: cauhinh/datnick.php
        try {
            val formBody = FormBody.Builder()
                .add("iddat[]", uid)
                .add("loai", loai)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/cauhinh/datnick.php")
                .headers(buildHeaders())
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                lastHttpCode = response.code
                val rawResponse = response.body?.string() ?: ""
                lastRawResponse = rawResponse
                Log.e("TTC_DEBUG", "Response TTC: " + rawResponse)

                if (!response.isSuccessful) {
                    lastErrorMsg = "Lỗi kết nối TTC (Mã: ${response.code})"
                } else {
                    val isSuccess = rawResponse.contains("\"status\":1") || 
                        rawResponse.contains("\"status\":\"success\"") || 
                        rawResponse.contains("Cấu hình thành công") || 
                        rawResponse.contains("Thành công")

                    if (isSuccess) {
                        return SetNickResult(isSuccess = true, message = "Cấu hình thành công", rawResponse = rawResponse, httpCode = response.code)
                    }

                    val serverMsg = parseTtcErrorMessage(rawResponse)
                    lastErrorMsg = "Lỗi TTC: $serverMsg"
                }
            }
        } catch (e: Exception) {
            Log.e("TTC_DEBUG", "Response TTC (Exception): " + e.message, e)
            if (lastErrorMsg.isBlank()) {
                lastErrorMsg = "Lỗi kết nối TTC (Mã: Mất mạng)"
            }
        }

        return SetNickResult(
            isSuccess = false,
            message = lastErrorMsg.ifBlank { "Lỗi TTC: Không nhận được phản hồi hợp lệ" },
            rawResponse = lastRawResponse,
            httpCode = lastHttpCode
        )
    }

    @Throws(Exception::class)
    fun setNickRun(uid: String, loai: String = "fb"): Boolean {
        return setNickRunDetailed(uid, loai).isSuccess
    }

    /**
     * 3. Lấy danh sách nhiệm vụ (kiemtien/getpost.php)
     */
    @Throws(Exception::class)
    fun getJobs(jobType: TTCJobType): List<TTCJob> {
        val jobs = fetchJobsForType(jobType.apiType)
        if (jobs.isEmpty() && !jobType.alternateApiType.isNullOrBlank()) {
            return fetchJobsForType(jobType.alternateApiType)
        }
        return jobs
    }

    private fun fetchJobsForType(apiType: String): List<TTCJob> {
        val url = "$BASE_URL/kiemtien/getpost.php?type=$apiType"
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (body.contains("Hết Job") || body.contains("countdown") || body.contains("\"error\"")) {
                return emptyList()
            }

            val jobList = mutableListOf<TTCJob>()
            if (body.trim().startsWith("[")) {
                val jsonArr = JSONArray(body)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    jobList.add(
                        TTCJob(
                            id = obj.optString("id", obj.optString("idpost", "")),
                            idfb = obj.optString("idfb", null),
                            idpost = obj.optString("idpost", null),
                            link = obj.optString("link", null),
                            loaicx = obj.optString("loaicx", null),
                            cmt = obj.optString("nd").takeIf { it.isNotBlank() }
                                ?: obj.optString("noidung").takeIf { it.isNotBlank() }
                                ?: obj.optString("cmt").takeIf { it.isNotBlank() }
                                ?: obj.optString("comment", null),
                            uid = obj.optString("uid", null)
                        )
                    )
                }
            }
            jobList
        }
    }

    /**
     * 4. Nhận Tiền / Nhận Xu sau khi hoàn thành nhiệm vụ (kiemtien/nhantien.php)
     */
    @Throws(Exception::class)
    fun claimReward(jobId: String, jobType: TTCJobType): TTCNhanTienResult {
        val res = claimRewardForType(jobId, jobType.apiType)
        if (!res.isSuccess && !jobType.alternateApiType.isNullOrBlank()) {
            val res2 = claimRewardForType(jobId, jobType.alternateApiType)
            if (res2.isSuccess) return res2
        }
        return res
    }

    private fun claimRewardForType(jobId: String, apiType: String): TTCNhanTienResult {
        val formBody = FormBody.Builder()
            .add("id", jobId)
            .add("loai", apiType)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/kiemtien/nhantien.php")
            .headers(buildHeaders())
            .post(formBody)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val isSuccess = response.isSuccessful && (body.contains("\"success\"") || body.contains("\"status\":\"success\"") || body.contains("Thành công"))
            
            val json = if (body.trim().startsWith("{")) JSONObject(body) else JSONObject()
            val soduStr = json.optString("sodu", "0").replace(",", "").replace(".", "")
            val sodu = soduStr.toLongOrNull() ?: 0L
            val xuThem = json.optLong("xu", json.optLong("xu_them", 0L))
            val msg = json.optString("mess", if (isSuccess) "Nhận xu thành công!" else "Nhận xu thất bại: $body")

            TTCNhanTienResult(
                isSuccess = isSuccess,
                sodu = sodu,
                xuThem = xuThem,
                message = msg
            )
        }
    }

    /**
     * 5. Thêm tài khoản Facebook vào TTC (cauhinh/themacc.php)
     * @param uidOrLink UID số Facebook hoặc link profile
     * @return true nếu thêm thành công, false nếu thất bại
     */
    @Throws(Exception::class)
    fun addFacebookAccountToTtc(uidOrLink: String): Boolean {
        val cleanInput = uidOrLink.trim()

        // Thử endpoint 1: cauhinh/themacc.php
        val formBody = FormBody.Builder()
            .add("idfacebook", cleanInput)
            .add("loai", "fb")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/cauhinh/themacc.php")
            .headers(buildHeaders())
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val ok = response.isSuccessful && (
                body.contains("\"status\":1") ||
                body.contains("\"status\":\"success\"") ||
                body.contains("Thêm thành công") ||
                body.contains("success") ||
                body.contains("\"result\":1")
            )
            if (ok) return true

            // Thử endpoint 2: cauhinh/facebook.php
            val formBody2 = FormBody.Builder()
                .add("idfacebook", cleanInput)
                .build()

            val request2 = Request.Builder()
                .url("$BASE_URL/cauhinh/facebook.php")
                .headers(buildHeaders())
                .post(formBody2)
                .build()

            httpClient.newCall(request2).execute().use { response2 ->
                val body2 = response2.body?.string() ?: ""
                return response2.isSuccessful && (
                    body2.contains("\"status\":1") ||
                    body2.contains("\"status\":\"success\"") ||
                    body2.contains("Thêm thành công") ||
                    body2.contains("success") ||
                    body2.contains("\"result\":1")
                )
            }
        }
    }
}
