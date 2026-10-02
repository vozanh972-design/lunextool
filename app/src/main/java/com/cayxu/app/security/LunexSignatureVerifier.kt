package com.cayxu.app.security

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object LunexSignatureVerifier {

    // Khóa Public Key RSA chuẩn Base64 (App chỉ dùng để KIỂM TRA, không thể dùng để tạo chữ ký)
    private const val RSA_PUBLIC_KEY_BASE64 = """
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtVIhJjYWoumo6Dg1INMygydLbkmKQVTF366MYaRJM1uJkQ5/Xh+sVdCSo7PPUxjfg0og54RVPB8tRBgrC0L0tx+U7+zac4w1q3vnwzvJGYK1QYMgBRbH5uijiefgrrJIZ9iiMxhIjujqfOGcnM2FjDRAx9eUxJ/ID41ZR1f/6u/+qLoodHhxqNFGk9S6kY7A9a8EIXhjb6L8q/GppcshlUl8qQbpyHiUF+3C088X4qQRmWxxT+s4KGOTKLD485Ft6/ZyPBDKFOJdd0nrDrOIGXXdeoo1hYNA97BkA8wmghw1GPfpCV/HtQM9udAYPpYNgVd784a9Xf7xQTMlX0lZmQIDAQAB
    """

    private fun getPublicKey(): PublicKey {
        val cleanKey = RSA_PUBLIC_KEY_BASE64
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(cleanKey, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(spec)
    }

    /**
     * Xác thực xem dữ liệu có đúng do Server thật (có Private Key) ký hay không.
     * @param plainData: Chuỗi dữ liệu cần kiểm tra (ví dụ: "$status|$key|$username|$expires_at")
     * @param signatureBase64: Chữ ký số do Server gửi kèm trong response
     */
    fun verifyServerSignature(plainData: String, signatureBase64: String): Boolean {
        if (signatureBase64.isBlank()) return false
        return try {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(getPublicKey())
            signature.update(plainData.toByteArray(Charsets.UTF_8))
            val sigBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            signature.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }
}
