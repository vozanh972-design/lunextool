package com.cayxu.app.ui.crossttc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
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
}

