package com.cayxu.app.data.repository

import com.cayxu.app.data.api.RetrofitClient
import com.cayxu.app.data.model.VerifyKeyResponse

/**
 * Kết quả bọc lại để phân biệt lỗi mạng / lỗi server / thành công
 * mà không thay đổi bất kỳ field nào trong response gốc.
 */
sealed class AuthResult {
    data class Success(val data: VerifyKeyResponse) : AuthResult()
    data class ApiError(val message: String) : AuthResult()
    data class NetworkError(val message: String) : AuthResult()
}

class AuthRepository {

    private val api = RetrofitClient.apiService

    suspend fun verifyKey(key: String, deviceId: String): AuthResult {
        return try {
            val response = api.verifyKey(key, deviceId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                if (body.isSuccess) {
                    AuthResult.Success(body)
                } else {
                    // Hiện đúng message server trả về, không tự sửa
                    AuthResult.ApiError(body.message ?: "Đã có lỗi xảy ra")
                }
            } else {
                AuthResult.ApiError("Không thể kết nối tới máy chủ")
            }
        } catch (e: Exception) {
            AuthResult.NetworkError(e.message ?: "Lỗi kết nối mạng")
        }
    }
}
