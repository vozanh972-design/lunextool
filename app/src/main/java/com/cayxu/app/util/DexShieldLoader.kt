package com.cayxu.app.util

import android.content.Context
import androidx.annotation.Keep
import java.nio.ByteBuffer

/**
 * InMemory Dex Shield & Native Memory Loader
 * Ho tro nap cac payload DEX ma hoa truc tiep tu RAM (InMemoryDexClassLoader)
 * khong ghi ra file he thong de chong trich xuat / dump DEX.
 */
@Keep
object DexShieldLoader {

    /**
     * Giai ma payload DEX tu bo dem bo nho RAM
     */
    @JvmStatic
    fun decryptDexPayload(encryptedBytes: ByteArray, key: ByteArray): ByteBuffer {
        val decrypted = ByteArray(encryptedBytes.size)
        var state = 0x55AA55AA
        var i = 0
        while (state != 0) {
            when (state) {
                0x55AA55AA -> { i = 0; state = 0x12345678 }
                0x12345678 -> {
                    if (i < encryptedBytes.size) {
                        state = 0x87654321
                    } else {
                        state = 0
                    }
                }
                0x87654321 -> {
                    val k = key[i % key.size].toInt() and 0xFF
                    val b = encryptedBytes[i].toInt() and 0xFF
                    val dec = (b or k) - (b and k)
                    decrypted[i] = dec.toByte()
                    i++
                    state = 0x12345678
                }
                else -> state = 0
            }
        }
        return ByteBuffer.wrap(decrypted)
    }

    /**
     * Khoi tao va kiem tra tinh toan ven bo dem RAM
     */
    @JvmStatic
    fun initShield(context: Context): Boolean {
        return NativeSecurity.checkSecurityEnvironment(context) == 0
    }
}