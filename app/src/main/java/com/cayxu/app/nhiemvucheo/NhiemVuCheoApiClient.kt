package com.cayxu.app.nhiemvucheo

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class NvcUser(
    val id: Long = 0,
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val role: String = "member",
    val status: String = "active",
    val coinBalance: String = "0",
    val lockedCoinBalance: String = "0"
)

sealed class NvcProfileResult {
    data class Success(val user: NvcUser) : NvcProfileResult()
    data class Error(val code: String, val message: String) : NvcProfileResult()
}

/**
 * Client kết nối NhiemVuCheo Member Tool API (OpenAPI 3.0.3).
 * Base URL: https://nhiemvucheo.com/api/v1
 * Auth: Authorization: Bearer <nvc_sk_...>
 */
object NhiemVuCheoApiClient {
    const val BASE_URL = "https://nhiemvucheo.com/api/v1"
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Lấy thông tin tài khoản và số dư coin.
     * GET /account/profile
     */
    suspend fun fetchProfile(token: String): NvcProfileResult = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext NvcProfileResult.Error("EMPTY_TOKEN", "Vui lòng nhập API Token")
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/profile")
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")
                .header("User-Agent", "CayXu-Tool/1.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext NvcProfileResult.Error(
                        "EMPTY_RESPONSE",
                        "Máy chủ phản hồi rỗng (HTTP ${response.code})"
                    )
                }

                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (_: Exception) {
                    return@withContext NvcProfileResult.Error(
                        "PARSE_ERROR",
                        "Không thể đọc dữ liệu phản hồi từ máy chủ"
                    )
                }

                val isSuccess = json.has("success") && json.get("success").asBoolean
                if (isSuccess && json.has("data")) {
                    val dataObj = json.getAsJsonObject("data")
                    if (dataObj.has("user")) {
                        val userObj = dataObj.getAsJsonObject("user")
                        val user = NvcUser(
                            id = userObj.get("id")?.asLong ?: 0L,
                            username = userObj.get("username")?.asString.orEmpty(),
                            displayName = userObj.get("displayName")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                            avatarUrl = userObj.get("avatarUrl")?.takeIf { !it.isJsonNull }?.asString,
                            role = userObj.get("role")?.takeIf { !it.isJsonNull }?.asString ?: "member",
                            status = userObj.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "active",
                            coinBalance = userObj.get("coinBalance")?.asString ?: "0",
                            lockedCoinBalance = userObj.get("lockedCoinBalance")?.asString ?: "0"
                        )
                        return@withContext NvcProfileResult.Success(user)
                    }
                }

                // Error response
                var errCode = "UNKNOWN_ERROR"
                var errMsg = "Đăng nhập thất bại (HTTP ${response.code})"
                if (json.has("error") && json.get("error").isJsonObject) {
                    val errObj = json.getAsJsonObject("error")
                    errCode = errObj.get("code")?.asString ?: errCode
                    errMsg = errObj.get("message")?.asString ?: errMsg
                }

                return@withContext NvcProfileResult.Error(errCode, errMsg)
            }
        } catch (e: Exception) {
            return@withContext NvcProfileResult.Error(
                "NETWORK_ERROR",
                "Lỗi kết nối mạng: ${e.message ?: "Không xác định"}"
            )
        }
    }
}
