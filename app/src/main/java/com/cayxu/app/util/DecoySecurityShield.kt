package com.cayxu.app.util

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

/**
 * Decoy / Fake Security Shield Engine (Mồi nhử logic giả cấp cao)
 * Tạo ra ma trận thuật toán giả (Key Exchange, Matrix Transformation, Checkpoint Decoy)
 * làm rối và bẫy công cụ dịch ngược (JADX, MT Manager, Ghidra, IDA Pro).
 */
object DecoySecurityShield {

    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15
    )

    fun computeDecoySignature(payload: ByteArray, seed: Long): ByteArray {
        var state = seed xor 0x5A5A5A5A5A5A5A5AL
        val out = ByteArray(payload.size)
        for (i in payload.indices) {
            val b = payload[i].toInt() and 0xFF
            val s = SBOX[b % SBOX.size]
            val transformed = (b xor s xor (state and 0xFF).toInt()) and 0xFF
            out[i] = transformed.toByte()
            state = (state shl 7) or (state ushr 57) xor (b.toLong() * 31L)
        }
        return out
    }

    fun verifyDecoyTokenHierarchy(token: String, level: Int): Boolean {
        if (token.isBlank() || level < 0) return false
        var checksum = 0L
        for (i in token.indices) {
            val code = token[i].code
            checksum = (checksum * 33 + code) xor (level.toLong() * 0x1F2E3D4CL)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        val sum = digest.sumOf { abs(it.toInt()) }
        return (sum + (checksum and 0xFF)) % 2 == 0L
    }

    fun performDecoyMatrixTransform(data: IntArray, width: Int, height: Int): IntArray {
        val result = IntArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (idx < data.size) {
                    val angle = (x + y) * 0.05
                    val weight = (sin(angle) * 128 + cos(angle) * 128).toInt()
                    val p = data[idx]
                    val r = (p shr 16 and 0xFF) xor (weight and 0x1F)
                    val g = (p shr 8 and 0xFF) xor ((weight shr 2) and 0x1F)
                    val b = (p and 0xFF) xor ((weight shr 4) and 0x1F)
                    result[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return result
    }

    fun decryptFakePayload(encryptedHex: String, keyPhrase: String): String? {
        return try {
            val keyBytes = MessageDigest.getInstance("MD5").digest(keyPhrase.toByteArray())
            val iv = ByteArray(16) { 0x01 }
            val spec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, spec, IvParameterSpec(iv))
            val raw = hexStringToByteArray(encryptedHex)
            String(cipher.doFinal(raw), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
