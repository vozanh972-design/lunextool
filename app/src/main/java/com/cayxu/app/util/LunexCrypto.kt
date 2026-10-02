package com.cayxu.app.util

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object LunexCrypto {
    // Khóa bí mật 32 ký tự (256-bit) đồng bộ tuyệt đối với Server verify_key.php
    private const val AES_KEY = "LUNEX_SECURE_KEY_2026_@ENCRYPT#!"

    /**
     * Giải mã chuỗi Base64 (16 bytes IV + ciphertext) thành JSONObject gốc
     */
    fun decryptPayload(base64Payload: String): JSONObject {
        val fullBytes = Base64.decode(base64Payload, Base64.DEFAULT)
        require(fullBytes.size >= 16) { "Dữ liệu mã hóa không hợp lệ (nhỏ hơn 16 bytes)" }
        val iv = fullBytes.copyOfRange(0, 16)
        val cipherBytes = fullBytes.copyOfRange(16, fullBytes.size)
        val keySpec = SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
        val decryptedString = String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        return JSONObject(decryptedString)
    }

    /**
     * Tự động nhận diện và parse phản hồi từ Server:
     * - Nếu có "encrypted": true -> Giải mã qua decryptPayload
     * - Nếu là JSON thường (fallback) -> Parse trực tiếp
     */
    fun parseResponse(rawResponse: String): JSONObject {
        val root = JSONObject(rawResponse)
        return if (root.optBoolean("encrypted", false)) {
            val decrypted = decryptPayload(root.getString("data"))
            if (!decrypted.has("signature") && root.has("signature")) {
                decrypted.put("signature", root.getString("signature"))
            }
            decrypted
        } else {
            root
        }
    }
}
