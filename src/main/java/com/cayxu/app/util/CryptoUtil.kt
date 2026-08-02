package com.cayxu.app.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Mã hoá/giải mã + ký request ở lớp NGOÀI CÙNG, giữa app <-> Cloudflare Worker - hoàn toàn
 * TÁCH BIỆT với nội dung key/device_id thật gửi cho verify_key.php (PHP không biết gì về
 * lớp này, không cần đổi gì cả). Cùng thuật toán với Worker (Web Crypto API) để 2 bên đọc
 * được của nhau:
 *   - Khoá AES-256 = SHA-256(SHARED_SECRET)
 *   - Payload = IV (12 byte, ngẫu nhiên mỗi lần) + ciphertext (kèm tag GCM 128-bit) → Base64
 *   - Chữ ký = HMAC-SHA256(SHA-256(SHARED_SECRET), "<timestamp>.<payload đã mã hoá>")
 */
object CryptoUtil {

    private fun deriveKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    fun encrypt(secret: String, plainText: String): String {
        val key = deriveKey(secret)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(secret: String, base64CipherText: String): String {
        val combined = Base64.decode(base64CipherText, Base64.NO_WRAP)
        require(combined.size > 12) { "Payload mã hoá không hợp lệ" }
        val iv = combined.copyOfRange(0, 12)
        val cipherBytes = combined.copyOfRange(12, combined.size)
        val key = deriveKey(secret)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun hmacSha256Hex(secret: String, message: String): String {
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val sig = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(sig.size * 2)
        for (b in sig) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
