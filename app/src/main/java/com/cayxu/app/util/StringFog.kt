package com.cayxu.app.util

import androidx.annotation.Keep

/**
 * StringFog DEX String Encryption Engine
 * Ma hoa toan bo chuoi ky tu nhay cam (URLs, App IDs, Regexes, Headers, Tokens) trong DEX classes.
 * Bang string_ids trong DEX chi chua mang byte ngau nhien vo nghia, giai ma an toan tren RAM trong runtime.
 */
@Keep
object StringFog {

    private const val DEFAULT_KEY: Byte = 0x5A

    @JvmStatic
    fun decrypt(encrypted: ByteArray, key: Byte = DEFAULT_KEY): String {
        val result = CharArray(encrypted.size)
        var state = 0x1A2B3C4D
        var idx = 0

        // Control Flow Flattening (CFF) state machine dispatch
        while (state != 0) {
            when (state) {
                0x1A2B3C4D -> {
                    idx = 0
                    state = 0x5E6F7A8B
                }
                0x5E6F7A8B -> {
                    if (idx < encrypted.size) {
                        state = 0x9C8D7E6F
                    } else {
                        state = 0x11223344
                    }
                }
                0x9C8D7E6F -> {
                    val k = ((key.toInt() + (idx * 13)) and 0xFF)
                    val raw = encrypted[idx].toInt() and 0xFF
                    // Mixed Boolean-Arithmetic (MBA) substitution: x ^ k == (x | k) - (x & k)
                    val dec = (raw or k) - (raw and k)
                    result[idx] = dec.toChar()
                    idx++
                    state = 0x5E6F7A8B
                }
                0x11223344 -> {
                    state = 0
                }
                else -> {
                    state = 0
                }
            }
        }
        return String(result)
    }

    @JvmStatic
    fun encrypt(plain: String, key: Byte = DEFAULT_KEY): ByteArray {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            val k = ((key.toInt() + (i * 13)) and 0xFF)
            out[i] = (bytes[i].toInt() xor k).toByte()
        }
        return out
    }
}