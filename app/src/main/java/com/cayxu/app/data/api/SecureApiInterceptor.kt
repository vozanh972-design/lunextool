package com.cayxu.app.data.api

import com.cayxu.app.util.CryptoUtil
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * Lớp mã hoá/ký NGOÀI CÙNG cho mọi request tới Cloudflare Worker:
 *   - Body gốc (key=...&device_id=...) được mã hoá AES-GCM trước khi rời máy.
 *   - Kèm header X-Ts (timestamp) + X-Sign (HMAC-SHA256 theo timestamp+body đã mã hoá) để
 *     Worker xác minh đúng là app thật gửi lên, chặn giả mạo/replay request.
 *   - Response Worker trả về (cũng là ciphertext) được giải mã lại thành JSON gốc TRƯỚC KHI
 *     Gson đọc - nên ApiService/AuthRepository/VerifyKeyResponse không cần biết gì về lớp
 *     mã hoá này, không phải sửa 1 dòng nào ở đó.
 *
 * verify_key.php ở origin KHÔNG hề biết tới lớp này - Worker tự giải mã trước khi chuyển tiếp
 * dữ liệu gốc cho PHP, và tự mã hoá lại response PHP trước khi trả về app.
 */
class SecureApiInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val plainBody = original.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()

        val secret = SharedSecret.value()
        val encryptedBody = CryptoUtil.encrypt(secret, plainBody)
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = CryptoUtil.hmacSha256Hex(secret, "$timestamp.$encryptedBody")

        val securedRequest = original.newBuilder()
            .method(original.method, encryptedBody.toRequestBody("text/plain".toMediaType()))
            .header("X-Ts", timestamp)
            .header("X-Sign", signature)
            .build()

        val response = chain.proceed(securedRequest)
        val rawResponseText = response.body?.string().orEmpty()

        val decryptedText = try {
            CryptoUtil.decrypt(secret, rawResponseText)
        } catch (e: Exception) {
            // Lỗi ở tầng trước Worker (vd 429/5xx do Cloudflare trả thẳng, không phải ciphertext
            // của Worker) - giữ nguyên text gốc để AuthRepository vẫn có thông báo lỗi hợp lý,
            // thay vì crash toàn bộ luồng đăng nhập.
            rawResponseText
        }

        return response.newBuilder()
            .body(decryptedText.toResponseBody(response.body?.contentType()))
            .build()
    }
}
