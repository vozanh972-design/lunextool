package com.cayxu.app.data.repository

import com.cayxu.app.data.api.RetrofitClient
import com.cayxu.app.ui.f4f.F4fNativeBridge
import com.cayxu.app.util.LunexCrypto

/**
 * Trạng thái phiên bản quyền đã được tầng C++ thẩm định và cấp Session Ticket trong RAM.
 * Không dùng chú thích Gson (@SerializedName) để ProGuard/R8 làm rối hoàn toàn tên trường trong DEX.
 */
data class AuthSession(
    val username: String? = null,
    val packageName: String? = null,
    val expiresAt: String? = null,
    val daysLeft: Int? = null
)

/**
 * Kết quả bọc lại để phân biệt lỗi mạng / lỗi server / thành công
 * mà không thay đổi bất kỳ field nào trong response gốc.
 */
sealed class AuthResult {
    data class Success(val data: AuthSession) : AuthResult()
    data class ApiError(val message: String) : AuthResult()
    data class NetworkError(val message: String) : AuthResult()
}

class AuthRepository {

    private val api = RetrofitClient.apiService

    suspend fun verifyKey(key: String, deviceId: String): AuthResult {
        return try {
            val response = api.verifyKey(RetrofitClient.VERIFY_KEY_PATH, key, deviceId)
            val rawString = response.body()?.string() ?: ""
            if (response.isSuccessful && rawString.isNotBlank()) {
                val json = LunexCrypto.parseResponse(rawString)
                val status = json.optString("status")
                if (status.equals("success", ignoreCase = true)) {
                    val packed = F4fNativeBridge.registerLicenseSession(json.toString(), key, deviceId)
                    if (packed.startsWith("OK|")) {
                        val parts = packed.split("|")
                        val uName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        val pkg = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                        val exp = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                        val days = parts.getOrNull(4)?.toIntOrNull()
                        AuthResult.Success(AuthSession(uName, pkg, exp, days))
                    } else {
                        AuthResult.ApiError("Xác thực bản quyền không hợp lệ")
                    }
                } else {
                    AuthResult.ApiError(json.optString("message", "Mã key không hợp lệ hoặc đã bị khóa"))
                }
            } else {
                AuthResult.ApiError("Không thể kết nối tới máy chủ (mã lỗi HTTP: ${response.code()})")
            }
        } catch (e: Exception) {
            AuthResult.NetworkError(e.message ?: "Lỗi kết nối mạng")
        }
    }
}
