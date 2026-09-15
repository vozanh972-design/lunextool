package com.cayxu.app.facebook

import androidx.annotation.Keep
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Keep
object TotpGenerator {

    /**
     * Tạo mã 2FA TOTP 6 chữ số từ secret key (RFC 6238).
     * Hỗ trợ secret có khoảng trắng, chữ hoa/thường.
     */
    fun generateTotp(secret: String, timeStepSeconds: Long = 30L): String {
        val cleanSecret = secret.replace(" ", "").replace("-", "").trim().uppercase()
        if (cleanSecret.isEmpty()) return ""

        try {
            val keyBytes = decodeBase32(cleanSecret)
            if (keyBytes.isEmpty()) return ""

            val currentTimeSeconds = System.currentTimeMillis() / 1000L
            val timeIndex = currentTimeSeconds / timeStepSeconds

            val data = ByteBuffer.allocate(8).putLong(timeIndex).array()
            val sign = hmacSha1(keyBytes, data)

            val offset = sign[sign.size - 1].toInt() and 0x0F
            val truncatedHash = ((sign[offset].toInt() and 0x7F) shl 24) or
                    ((sign[offset + 1].toInt() and 0xFF) shl 16) or
                    ((sign[offset + 2].toInt() and 0xFF) shl 8) or
                    (sign[offset + 3].toInt() and 0xFF)

            val otp = truncatedHash % 1_000_000
            return "%06d".format(otp)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }

    private fun decodeBase32(base32: String): ByteArray {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = base32.trimEnd('=')
        var buffer = 0
        var bitsLeft = 0
        val out = mutableListOf<Byte>()

        for (c in clean) {
            val valIndex = base32Chars.indexOf(c)
            if (valIndex < 0) continue
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}
