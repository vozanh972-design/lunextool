package com.cayxu.app.tiktok.checker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Kết quả trả về từ API Passport TikTok (Hướng 1: 100% API qua Cookie / SessionId)
 */
data class TikTokSessionInfo(
    val isValid: Boolean,
    val username: String? = null,    // Username gốc chuẩn 100% (ví dụ: hsisha.hssosu)
    val nickname: String? = null,    // Tên hiển thị (screen_name: Trần Ngọc)
    val userId: String? = null,      // TikTok UID dạng số nguyên 64-bit
    val avatarUrl: String? = null,   // URL avatar thật từ CDN TikTok
    val rawMessage: String? = null
)

/**
 * CLIENT CHECK TÀI KHOẢN TIKTOK 100% BẰNG API PASSPORT (HƯỚNG 1).
 * Không phụ thuộc giao diện app, không quét màn hình, check thẳng vào máy chủ ByteDance.
 */
class TikTokPassportClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    /**
     * HƯỚNG 1: Gọi trực tiếp endpoint Passport của TikTok để lấy thông tin tài khoản thật 100%.
     * Endpoint: GET https://www.tiktok.com/passport/web/account/info/
     *
     * @param sessionIdOrCookie Chuỗi sessionid hoặc toàn bộ Cookie chứa sessionid
     */
    suspend fun getAccountInfoFromSession(sessionIdOrCookie: String): TikTokSessionInfo = withContext(Dispatchers.IO) {
        val cleanInput = sessionIdOrCookie.trim()
        if (cleanInput.isBlank()) {
            return@withContext TikTokSessionInfo(isValid = false, rawMessage = "SessionId/Cookie rỗng")
        }

        // Tách lấy giá trị sessionid chuẩn xác
        val cleanSession = if (cleanInput.contains("sessionid=")) {
            cleanInput.substringAfter("sessionid=").substringBefore(";").trim()
        } else {
            cleanInput.split(";")[0].trim()
        }

        if (cleanSession.isBlank()) {
            return@withContext TikTokSessionInfo(isValid = false, rawMessage = "Không tìm thấy sessionid hợp lệ")
        }

        val request = Request.Builder()
            .url("https://www.tiktok.com/passport/web/account/info/")
            .get()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept", "application/json")
            .header("Cookie", "sessionid=$cleanSession")
            .build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val message = json.optString("message", "")
                val data = json.optJSONObject("data")

                if (message == "success" && data != null && data.has("username")) {
                    val uname = data.optString("username", "").trim()
                    if (uname.isNotBlank()) {
                        TikTokSessionInfo(
                            isValid = true,
                            username = uname,
                            nickname = data.optString("screen_name", uname),
                            userId = data.optString("user_id", ""),
                            avatarUrl = data.optString("avatar_url", ""),
                            rawMessage = "success"
                        )
                    } else {
                        TikTokSessionInfo(isValid = false, rawMessage = "Username rỗng trong phản hồi")
                    }
                } else {
                    TikTokSessionInfo(
                        isValid = false,
                        rawMessage = if (message.isNotBlank()) message else "Cookie/Session hết hạn hoặc không hợp lệ"
                    )
                }
            }
        } catch (e: Exception) {
            TikTokSessionInfo(isValid = false, rawMessage = e.message ?: "Lỗi kết nối mạng")
        }
    }
}
