package com.cayxu.app.ui.f4f

import androidx.annotation.Keep

/**
 * Cầu nối JNI kết nối module C++ OLLVM-Hardened độc lập của Follow4Follow (libf4f_sec.so).
 * Tuyệt đối KHÔNG chứa chuỗi tĩnh (plaintext string constants) trong DEX.
 * Cơ chế Fallback giải mã byte array động trong RAM đảm bảo an toàn 100% không gãy logic.
 */
@Keep
object F4fNativeBridge {

    private var isLoaded = false

    init {
        try {
            System.loadLibrary("f4f_sec")
            isLoaded = true
        } catch (e: Throwable) {
            isLoaded = false
            android.util.Log.w("F4fNativeBridge", "Dùng cơ chế an toàn phân tán F4F: ${e.message}")
        }
    }

    // --- Khai báo hàm Native C++ JNI ---
    @JvmStatic external fun getNativeBaseUrl(): String
    @JvmStatic external fun getNativeUserAgent(): String
    @JvmStatic external fun getNativeOrigin(): String
    @JvmStatic external fun getNativeReferer(): String
    @JvmStatic external fun getNativePathAuthMe(): String
    @JvmStatic external fun getNativePathAccountList(): String
    @JvmStatic external fun getNativePathAccountAdd(): String
    @JvmStatic external fun getNativePathEarnCoin(): String
    @JvmStatic external fun getNativePathMarkStart(): String
    @JvmStatic external fun getNativePathCompleteJob(): String
    @JvmStatic external fun getNativePathActiveAccount(): String
    @JvmStatic external fun getNativeAcceptJson(): String
    @JvmStatic external fun getNativePrefsAccount(): String
    @JvmStatic external fun getNativeKeyToken(): String
    @JvmStatic external fun getNativeKeyUsername(): String
    @JvmStatic external fun getNativeKeyPoints(): String
    @JvmStatic external fun getNativeKeyAccountsMap(): String
    @JvmStatic external fun getNativeKeyInternalIdsMap(): String
    @JvmStatic external fun getNativeKeySavedAccounts(): String
    @JvmStatic external fun getNativePrefsRunConfig(): String
    @JvmStatic external fun getNativeKeyRunConfig(): String

    // --- Mảng byte mã hóa nhị phân để giải mã động trong RAM (Không lưu chuỗi tĩnh trong DEX) ---
    private fun decode(data: ByteArray, baseKey: Int = 0x5A, step: Int = 0x17): String {
        val out = CharArray(data.size)
        for (i in data.indices) {
            val k = (baseKey + i * step) and 0xFF
            val raw = data[i].toInt() and 0xFF
            val dec = (raw or k) - (raw and k)
            out[i] = dec.toChar()
        }
        return String(out)
    }

    private val fbBaseUrl = byteArrayOf(0x32.toByte(), 0x05.toByte(), 0xFC.toByte(), 0xEF.toByte(), 0xC5.toByte(), 0xF7.toByte(), 0xCB.toByte(), 0xD4.toByte(), 0x73.toByte(), 0x59.toByte(), 0x29.toByte(), 0x79.toByte(), 0x08.toByte(), 0xEA.toByte(), 0xF0.toByte(), 0xDF.toByte(), 0xA5.toByte(), 0x96.toByte(), 0xCC.toByte(), 0x69.toByte(), 0x49.toByte(), 0x51.toByte(), 0x38.toByte(), 0x04.toByte(), 0xF5.toByte(), 0xB7.toByte(), 0xDE.toByte(), 0xA2.toByte(), 0xAA.toByte())
    private val fbUserAgent = byteArrayOf(0x17.toByte(), 0x1E.toByte(), 0xF2.toByte(), 0xF6.toByte(), 0xDA.toByte(), 0xA1.toByte(), 0x85.toByte(), 0xD4.toByte(), 0x27.toByte(), 0x07.toByte(), 0x70.toByte(), 0x77.toByte(), 0x46.toByte(), 0xC9.toByte(), 0xF5.toByte(), 0xDD.toByte(), 0xBF.toByte(), 0x99.toByte(), 0xC3.toByte(), 0x2F.toByte(), 0x67.toByte(), 0x53.toByte(), 0x30.toByte(), 0x19.toByte(), 0xED.toByte(), 0xF0.toByte(), 0xD4.toByte(), 0xE7.toByte(), 0xEF.toByte(), 0xC1.toByte(), 0x37.toByte(), 0x03.toByte(), 0x69.toByte(), 0x1C.toByte(), 0x45.toByte(), 0x3E.toByte(), 0xA3.toByte(), 0x98.toByte(), 0xF2.toByte(), 0x99.toByte(), 0xDB.toByte(), 0x29.toByte(), 0x61.toByte(), 0x47.toByte(), 0x3E.toByte(), 0x09.toByte(), 0x19.toByte(), 0xC4.toByte(), 0xCF.toByte(), 0xA3.toByte(), 0x93.toByte(), 0x86.toByte(), 0x72.toByte(), 0x32.toByte(), 0x01.toByte(), 0x78.toByte(), 0x55.toByte(), 0x57.toByte(), 0xA3.toByte(), 0x91.toByte(), 0x9E.toByte(), 0xFD.toByte(), 0xA7.toByte(), 0x4B.toByte(), 0x4E.toByte(), 0x7C.toByte(), 0x04.toByte(), 0x73.toByte(), 0x56.toByte(), 0xE1.toByte(), 0xCD.toByte(), 0xD0.toByte(), 0xB7.toByte(), 0xC9.toByte(), 0x47.toByte(), 0x72.toByte(), 0x4D.toByte(), 0x2E.toByte(), 0x33.toByte(), 0x5A.toByte(), 0xAA.toByte(), 0xE2.toByte(), 0xD0.toByte(), 0xBD.toByte(), 0x89.toByte(), 0x90.toByte(), 0x71.toByte(), 0x04.toByte(), 0x73.toByte(), 0x6C.toByte(), 0x45.toByte(), 0xA9.toByte(), 0xAE.toByte(), 0x9B.toByte(), 0xFC.toByte(), 0xCD.toByte(), 0xCA.toByte(), 0x31.toByte(), 0x65.toByte(), 0x50.toByte(), 0x34.toByte(), 0x04.toByte(), 0xE8.toByte(), 0xFE.toByte(), 0x92.toByte(), 0x9A.toByte(), 0x81.toByte(), 0x91.toByte(), 0x6F.toByte(), 0x57.toByte(), 0x55.toByte(), 0x7C.toByte(), 0x5F.toByte(), 0xB2.toByte(), 0xAF.toByte(), 0x81.toByte(), 0xF5.toByte(), 0xEB.toByte())
    private val fbOrigin = byteArrayOf(0x32.toByte(), 0x05.toByte(), 0xFC.toByte(), 0xEF.toByte(), 0xC5.toByte(), 0xF7.toByte(), 0xCB.toByte(), 0xD4.toByte(), 0x74.toByte(), 0x46.toByte(), 0x2C.toByte(), 0x3B.toByte(), 0x01.toByte(), 0xF2.toByte(), 0xA8.toByte(), 0xD5.toByte(), 0xA5.toByte(), 0x8D.toByte(), 0x94.toByte(), 0x60.toByte(), 0x51.toByte(), 0x13.toByte(), 0x3A.toByte(), 0x0E.toByte(), 0xF6.toByte())
    private val fbReferer = byteArrayOf(0x32.toByte(), 0x05.toByte(), 0xFC.toByte(), 0xEF.toByte(), 0xC5.toByte(), 0xF7.toByte(), 0xCB.toByte(), 0xD4.toByte(), 0x74.toByte(), 0x46.toByte(), 0x2C.toByte(), 0x3B.toByte(), 0x01.toByte(), 0xF2.toByte(), 0xA8.toByte(), 0xD5.toByte(), 0xA5.toByte(), 0x8D.toByte(), 0x94.toByte(), 0x60.toByte(), 0x51.toByte(), 0x13.toByte(), 0x3A.toByte(), 0x0E.toByte(), 0xF6.toByte(), 0xB6.toByte())
    private val fbPathAuthMe = byteArrayOf(0x75.toByte(), 0x04.toByte(), 0xFB.toByte(), 0xFA.toByte(), 0xC4.toByte(), 0xE2.toByte(), 0x85.toByte(), 0x8E.toByte(), 0x66.toByte(), 0x41.toByte(), 0x6F.toByte(), 0x3A.toByte(), 0x0B.toByte())
    private val fbPathAccountList = byteArrayOf(0x75.toByte(), 0x04.toByte(), 0xFB.toByte(), 0xFA.toByte(), 0xC4.toByte(), 0xE2.toByte(), 0x85.toByte(), 0x98.toByte(), 0x71.toByte(), 0x46.toByte(), 0x35.toByte(), 0x39.toByte(), 0x1A.toByte(), 0xAA.toByte(), 0xF0.toByte(), 0xDA.toByte(), 0xB9.toByte(), 0x95.toByte())
    private val fbPathAccountAdd = byteArrayOf(0x75.toByte(), 0x04.toByte(), 0xFB.toByte(), 0xFA.toByte(), 0xC4.toByte(), 0xE2.toByte(), 0x85.toByte(), 0x98.toByte(), 0x71.toByte(), 0x46.toByte(), 0x35.toByte(), 0x39.toByte(), 0x1A.toByte(), 0xAA.toByte(), 0xFD.toByte(), 0xD7.toByte(), 0xAE.toByte())
    private val fbPathEarnCoin = byteArrayOf(0x75.toByte(), 0x04.toByte(), 0xFB.toByte(), 0xFA.toByte(), 0xC4.toByte(), 0xE2.toByte(), 0x81.toByte(), 0x9A.toByte(), 0x60.toByte(), 0x47.toByte(), 0x6D.toByte(), 0x34.toByte(), 0x01.toByte(), 0xEC.toByte(), 0xF2.toByte())
    private val fbPathMarkStart = byteArrayOf(0x75.toByte(), 0x04.toByte(), 0xFB.toByte(), 0xFA.toByte(), 0xC4.toByte(), 0xE2.toByte(), 0x81.toByte(), 0x9A.toByte(), 0x60.toByte(), 0x47.toByte(), 0x6D.toByte(), 0x34.toByte(), 0x01.toByte(), 0xEC.toByte(), 0xF2.toByte(), 0x9C.toByte(), 0xBE.toByte(), 0x88.toByte(), 0x93.toByte(), 0x7B.toByte(), 0x49.toByte(), 0x56.toByte(), 0x79.toByte(), 0x07.toByte(), 0xEB.toByte(), 0xF2.toByte(), 0xD5.toByte(), 0xE8.toByte(), 0xB3.toByte(), 0x94.toByte(), 0x7E.toByte(), 0x48.toByte(), 0x17.toByte(), 0x22.toByte(), 0x1C.toByte(), 0x1E.toByte(), 0xE4.toByte(), 0xD9.toByte())
    private val fbPathCompleteJob = byteArrayOf(0x75.toByte(), 0x04.toByte(), 0xFB.toByte(), 0xFA.toByte(), 0xC4.toByte(), 0xE2.toByte(), 0x81.toByte(), 0x9A.toByte(), 0x60.toByte(), 0x47.toByte(), 0x6D.toByte(), 0x34.toByte(), 0x01.toByte(), 0xEC.toByte(), 0xF2.toByte(), 0x9C.toByte(), 0xA9.toByte(), 0x8E.toByte(), 0x95.toByte(), 0x7F.toByte(), 0x4A.toByte(), 0x58.toByte(), 0x20.toByte(), 0x0E.toByte(), 0xAF.toByte(), 0xF3.toByte(), 0xDF.toByte(), 0xA5.toByte())
    private val fbPathActiveAccount = byteArrayOf(0x75.toByte(), 0x04.toByte(), 0xFB.toByte(), 0xFA.toByte(), 0xC4.toByte(), 0xE2.toByte(), 0x85.toByte(), 0x98.toByte(), 0x71.toByte(), 0x46.toByte(), 0x35.toByte(), 0x39.toByte(), 0x1A.toByte(), 0xAA.toByte(), 0xFD.toByte(), 0xD0.toByte(), 0xBE.toByte(), 0x88.toByte(), 0x8E.toByte(), 0x6A.toByte())
    private val fbAcceptJson = byteArrayOf(0x3B.toByte(), 0x01.toByte(), 0xF8.toByte(), 0xF3.toByte(), 0xDF.toByte(), 0xAE.toByte(), 0x85.toByte(), 0x8F.toByte(), 0x7B.toByte(), 0x46.toByte(), 0x2E.toByte(), 0x78.toByte(), 0x04.toByte(), 0xF6.toByte(), 0xF3.toByte(), 0xDD.toByte())
    private val fbPrefsAccount = byteArrayOf(0x39.toByte(), 0x10.toByte(), 0xF1.toByte(), 0xE7.toByte(), 0xC3.toByte(), 0x92.toByte(), 0x82.toByte(), 0xCF.toByte(), 0x74.toByte(), 0x76.toByte(), 0x21.toByte(), 0x34.toByte(), 0x0D.toByte(), 0xEA.toByte(), 0xE9.toByte(), 0xDD.toByte(), 0xBE.toByte())
    private val fbKeyToken = byteArrayOf(0x3C.toByte(), 0x45.toByte(), 0xEE.toByte(), 0xC0.toByte(), 0xC2.toByte(), 0xA2.toByte(), 0x8F.toByte(), 0x9E.toByte(), 0x7C.toByte())
    private val fbKeyUsername = byteArrayOf(0x3C.toByte(), 0x45.toByte(), 0xEE.toByte(), 0xC0.toByte(), 0xC3.toByte(), 0xBE.toByte(), 0x81.toByte(), 0x89.toByte(), 0x7C.toByte(), 0x48.toByte(), 0x2D.toByte(), 0x32.toByte())
    private val fbKeyPoints = byteArrayOf(0x3C.toByte(), 0x45.toByte(), 0xEE.toByte(), 0xC0.toByte(), 0xC6.toByte(), 0xA2.toByte(), 0x8D.toByte(), 0x95.toByte(), 0x66.toByte(), 0x5A.toByte())
    private val fbKeyAccountsMap = byteArrayOf(0x3C.toByte(), 0x45.toByte(), 0xEE.toByte(), 0xC0.toByte(), 0xD7.toByte(), 0xAE.toByte(), 0x87.toByte(), 0x94.toByte(), 0x67.toByte(), 0x47.toByte(), 0x34.toByte(), 0x24.toByte(), 0x31.toByte(), 0xE8.toByte(), 0xFD.toByte(), 0xC3.toByte())
    private val fbKeyInternalIdsMap = byteArrayOf(0x3C.toByte(), 0x45.toByte(), 0xEE.toByte(), 0xC0.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x90.toByte(), 0x9E.toByte(), 0x60.toByte(), 0x47.toByte(), 0x21.toByte(), 0x3B.toByte(), 0x31.toByte(), 0xEC.toByte(), 0xF8.toByte(), 0xC0.toByte(), 0x95.toByte(), 0x8C.toByte(), 0x99.toByte(), 0x7F.toByte())
    private val fbKeySavedAccounts = byteArrayOf(0x3C.toByte(), 0x45.toByte(), 0xEE.toByte(), 0xC0.toByte(), 0xC2.toByte(), 0xA4.toByte(), 0x8F.toByte(), 0x8F.toByte(), 0x7D.toByte(), 0x42.toByte(), 0x1F.toByte(), 0x36.toByte(), 0x0D.toByte(), 0xE6.toByte(), 0xF3.toByte(), 0xC6.toByte(), 0xA4.toByte(), 0x95.toByte(), 0x8B.toByte(), 0x50.toByte(), 0x55.toByte(), 0x5C.toByte(), 0x22.toByte(), 0x0E.toByte(), 0xE6.toByte())
    private val fbPrefsRunConfig = byteArrayOf(0x39.toByte(), 0x10.toByte(), 0xF1.toByte(), 0xE7.toByte(), 0xC3.toByte(), 0x92.toByte(), 0x82.toByte(), 0xCF.toByte(), 0x74.toByte(), 0x76.toByte(), 0x32.toByte(), 0x22.toByte(), 0x00.toByte(), 0xDA.toByte(), 0xFF.toByte(), 0xDC.toByte(), 0xA4.toByte(), 0x87.toByte(), 0x91.toByte(), 0x68.toByte())
    private val fbKeyRunConfig = byteArrayOf(0x28.toByte(), 0x04.toByte(), 0xE6.toByte(), 0xC0.toByte(), 0xD5.toByte(), 0xA2.toByte(), 0x8A.toByte(), 0x9D.toByte(), 0x7B.toByte(), 0x4E.toByte())

    // --- Các hàm an toàn gọi ra ngoài (ưu tiên Native C++, tự động fallback sang dynamic RAM bytes) ---
    fun fetchBaseUrl(): String {
        if (isLoaded) {
            try {
                val s = getNativeBaseUrl()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbBaseUrl)
    }

    fun fetchUserAgent(): String {
        if (isLoaded) {
            try {
                val s = getNativeUserAgent()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbUserAgent)
    }

    fun fetchOrigin(): String {
        if (isLoaded) {
            try {
                val s = getNativeOrigin()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbOrigin)
    }

    fun fetchReferer(): String {
        if (isLoaded) {
            try {
                val s = getNativeReferer()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbReferer)
    }

    fun fetchPathAuthMe(): String {
        if (isLoaded) {
            try {
                val s = getNativePathAuthMe()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPathAuthMe)
    }

    fun fetchPathAccountList(): String {
        if (isLoaded) {
            try {
                val s = getNativePathAccountList()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPathAccountList)
    }

    fun fetchPathAccountAdd(): String {
        if (isLoaded) {
            try {
                val s = getNativePathAccountAdd()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPathAccountAdd)
    }

    fun fetchPathEarnCoin(): String {
        if (isLoaded) {
            try {
                val s = getNativePathEarnCoin()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPathEarnCoin)
    }

    fun fetchPathMarkStart(): String {
        if (isLoaded) {
            try {
                val s = getNativePathMarkStart()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPathMarkStart)
    }

    fun fetchPathCompleteJob(): String {
        if (isLoaded) {
            try {
                val s = getNativePathCompleteJob()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPathCompleteJob)
    }

    fun fetchPathActiveAccount(): String {
        if (isLoaded) {
            try {
                val s = getNativePathActiveAccount()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPathActiveAccount)
    }

    fun fetchAcceptJson(): String {
        if (isLoaded) {
            try {
                val s = getNativeAcceptJson()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbAcceptJson)
    }

    fun fetchPrefsAccount(): String {
        if (isLoaded) {
            try {
                val s = getNativePrefsAccount()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPrefsAccount)
    }

    fun fetchKeyToken(): String {
        if (isLoaded) {
            try {
                val s = getNativeKeyToken()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbKeyToken)
    }

    fun fetchKeyUsername(): String {
        if (isLoaded) {
            try {
                val s = getNativeKeyUsername()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbKeyUsername)
    }

    fun fetchKeyPoints(): String {
        if (isLoaded) {
            try {
                val s = getNativeKeyPoints()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbKeyPoints)
    }

    fun fetchKeyAccountsMap(): String {
        if (isLoaded) {
            try {
                val s = getNativeKeyAccountsMap()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbKeyAccountsMap)
    }

    fun fetchKeyInternalIdsMap(): String {
        if (isLoaded) {
            try {
                val s = getNativeKeyInternalIdsMap()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbKeyInternalIdsMap)
    }

    fun fetchKeySavedAccounts(): String {
        if (isLoaded) {
            try {
                val s = getNativeKeySavedAccounts()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbKeySavedAccounts)
    }

    fun fetchPrefsRunConfig(): String {
        if (isLoaded) {
            try {
                val s = getNativePrefsRunConfig()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbPrefsRunConfig)
    }

    fun fetchKeyRunConfig(): String {
        if (isLoaded) {
            try {
                val s = getNativeKeyRunConfig()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return decode(fbKeyRunConfig)
    }
}
