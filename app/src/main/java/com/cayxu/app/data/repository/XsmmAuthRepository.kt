package com.cayxu.app.data.repository

import com.cayxu.app.data.api.XsmmRetrofitClient
import com.google.gson.JsonParser

data class XsmmUserInfo(val username: String, val points: Long)

sealed class XsmmLoginResult {
    data class Success(val info: XsmmUserInfo) : XsmmLoginResult()
    data class Error(val message: String) : XsmmLoginResult()
}

/**
 * Gọi THẬT GET https://xsmm.net/api/taskapi/user để xác nhận access token do người dùng dán
 * vào có hợp lệ không, đồng thời lấy username + số dư (points) hiện tại.
 */
object XsmmAuthRepository {

    suspend fun fetchUser(rawToken: String): XsmmLoginResult {
        val token = rawToken.trim()
        if (token.isBlank()) return XsmmLoginResult.Error("Chưa nhập token")

        val authHeader = if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

        return try {
            val response = XsmmRetrofitClient.api.getUser(authHeader)

            if (!response.isSuccessful) {
                val errorText = response.errorBody()?.string()
                val message = errorText?.let { text ->
                    runCatching {
                        JsonParser.parseString(text).asJsonObject
                            .get("error")?.takeIf { it.isJsonPrimitive }?.asString
                    }.getOrNull()
                }
                return XsmmLoginResult.Error(message ?: "Token không hợp lệ (mã lỗi HTTP: ${response.code()})")
            }

            val json = response.body()
            val errorField = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            if (!errorField.isNullOrBlank()) {
                return XsmmLoginResult.Error(errorField)
            }

            val user = json?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return XsmmLoginResult.Error("Phản hồi không hợp lệ từ server")

            val username = user.get("username")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val points = user.get("points")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L

            XsmmLoginResult.Success(XsmmUserInfo(username, points))
        } catch (e: Exception) {
            XsmmLoginResult.Error(e.message ?: "Lỗi kết nối mạng")
        }
    }
}
