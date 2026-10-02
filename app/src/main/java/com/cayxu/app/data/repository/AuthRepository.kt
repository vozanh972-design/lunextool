package com.cayxu.app.data.repository

import com.cayxu.app.data.api.RetrofitClient
import com.cayxu.app.data.model.VerifyKeyResponse
import com.cayxu.app.util.LunexCrypto
import com.google.gson.Gson

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
    private val gson = Gson()

    suspend fun verifyKey(key: String, deviceId: String): AuthResult {
        return try {
            val response = api.verifyKey(RetrofitClient.VERIFY_KEY_PATH, key, deviceId)
            val rawString = response.body()?.string() ?: ""
            if (response.isSuccessful && rawString.isNotBlank()) {
                val json = LunexCrypto.parseResponse(rawString)
                val verifyKeyResponse = gson.fromJson(json.toString(), VerifyKeyResponse::class.java)
                if (verifyKeyResponse != null && verifyKeyResponse.isSuccess) {
                    AuthResult.Success(verifyKeyResponse)
                } else {
                    // Hiện đúng message server trả về, không tự sửa
                    AuthResult.ApiError(verifyKeyResponse?.message ?: json.optString("message", "Đã có lỗi xảy ra"))
                }
            } else {
                AuthResult.ApiError("Không thể kết nối tới máy chủ (mã lỗi HTTP: ${response.code()})")
            }
        } catch (e: Exception) {
            AuthResult.NetworkError(e.message ?: "Lỗi kết nối mạng")
        }
    }
}
