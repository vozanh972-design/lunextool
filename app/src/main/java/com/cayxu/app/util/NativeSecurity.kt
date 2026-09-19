package com.cayxu.app.util

import android.content.Context
import androidx.annotation.Keep

/**
 * Native Bundle Architecture (Hệ thống 14 module C++ phân mảnh & mồi nhử)
 * Đánh lừa hoàn toàn công cụ phân tích tĩnh/động giống như các ứng dụng lớn (Meta, TikTok).
 */
@Keep
object NativeSecurity {

    private var isPathLoaded = false
    private var isDistractLoaded = false
    private var isSqliteLoaded = false
    private var isImageLoaded = false

    init {
        // Tự động quét và nạp an toàn các thư viện .so mồi nhử ngẫu nhiên
        val knownDecoys = listOf(
            "breakpad", "breakpad_cpp_helper", "fbunwindstack", "superpack_common",
            "superpack-jni", "dextricks-early", "appcomponentfactory-jni", "fb_so_loader",
            "achilles-jni", "fb_audiopipeline"
        )
        knownDecoys.forEach { lib ->
            try {
                System.loadLibrary(lib)
            } catch (_: Throwable) {}
        }

        // Nạp các thư viện phân mảnh chức năng thật
        try {
            System.loadLibrary("androidx.graphics.path")
            isPathLoaded = true
        } catch (_: Throwable) {
            isPathLoaded = false
        }

        try {
            System.loadLibrary("distract-config")
            isDistractLoaded = true
        } catch (_: Throwable) {
            isDistractLoaded = false
        }

        try {
            System.loadLibrary("sqlitejni")
            isSqliteLoaded = true
        } catch (_: Throwable) {
            isSqliteLoaded = false
        }

        try {
            System.loadLibrary("imagepipeline")
            isImageLoaded = true
        } catch (_: Throwable) {
            isImageLoaded = false
        }
    }

    /**
     * Module: libandroidx.graphics.path.so (Kiểm tra phần cứng & an toàn nhân Linux)
     */
    fun checkSecurityEnvironment(context: Context): Int {
        try {
            DecoySecurityShield.verifyDecoyTokenHierarchy(context.packageName, 3)
        } catch (_: Throwable) {}
        if (!isPathLoaded) return 0
        return try {
            _pathVal(context)
        } catch (_: Throwable) {
            0
        }
    }

    fun isDeviceCompromised(context: Context): Boolean {
        val code = checkSecurityEnvironment(context)
        // Chỉ coi là compromised khi có can thiệp bộ nhớ (Frida/Xposed/Hooking - mã 3)
        // Không chặn nhầm thiết bị thật của người dùng
        return code == 3
    }

    /**
     * Module: libandroidx.graphics.path.so (Base URL)
     */
    fun getSecureBaseUrl(): String {
        if (isPathLoaded) {
            try {
                val url = _pathSrc()
                if (url.isNotBlank()) return url
            } catch (_: Throwable) {}
        }
        return "https://lunex.io.vn/"
    }

    /**
     * Module: libdistract-config.so (Endpoint)
     */
    fun getSecureVerifyPath(): String {
        if (isDistractLoaded) {
            try {
                val path = _distractEp()
                if (path.isNotBlank()) return path
            } catch (_: Throwable) {}
        }
        return "api/verify_key.php"
    }

    /**
     * Module: libdistract-config.so (Băm chữ ký SHA-256 kèm Salt)
     */
    fun computeNativeKeyHash(key: String, deviceId: String, sigHash: String): String {
        if (isDistractLoaded) {
            try {
                val hash = _distractSig(key, deviceId, sigHash)
                if (hash.isNotBlank()) return hash
            } catch (_: Throwable) {}
        }
        return sha256("$key|$deviceId|$sigHash|fallback_distract_2026")
    }

    /**
     * Module: libsqlitejni.so (API Shared Secret)
     */
    fun getApiSharedSecret(): String {
        if (isSqliteLoaded) {
            try {
                val sec = _sqliteSec()
                if (sec.isNotBlank()) return sec
            } catch (_: Throwable) {}
        }
        return "9f3a7c1e0b6d4a2f8e5c1d7a9b0c2e4f6a8b1c3d5e7f9a0b2c4d6e8f0a1b3c5e"
    }

    /**
     * Module: libimagepipeline.so (Bộ lọc ảnh đồ họa thực tế)
     */
    fun applyFastBlur(pixels: IntArray, width: Int, height: Int, radius: Int): Boolean {
        if (!isImageLoaded) return false
        return try {
            _imgBlr(pixels, width, height, radius)
        } catch (_: Throwable) {
            false
        }
    }

    fun adjustContrast(pixels: IntArray, width: Int, height: Int, contrast: Float): Boolean {
        if (!isImageLoaded) return false
        return try {
            _imgCts(pixels, width, height, contrast)
        } catch (_: Throwable) {
            false
        }
    }

    fun getFbOAuthToken(): String {
        if (!isSqliteLoaded) return "OAuth 350685531728|62f8ce9f74b12f84c123cc23437a4a32"
        return try {
            _fbOAuth()
        } catch (_: Throwable) {
            "OAuth 350685531728|62f8ce9f74b12f84c123cc23437a4a32"
        }
    }

    fun getFbAppToken(): String {
        if (!isSqliteLoaded) return "350685531728|62f8ce9f74b12f84c123cc23437a4a32"
        return try {
            _fbAppToken()
        } catch (_: Throwable) {
            "350685531728|62f8ce9f74b12f84c123cc23437a4a32"
        }
    }

    fun getFbApiKey(): String {
        if (!isSqliteLoaded) return "882a8490361da98702bf97a021ddc14d"
        return try {
            _fbApiKey()
        } catch (_: Throwable) {
            "882a8490361da98702bf97a021ddc14d"
        }
    }

    fun getFbSig(): String {
        if (!isSqliteLoaded) return "214049b9f17c38bd767de53752b53946"
        return try {
            _fbSig()
        } catch (_: Throwable) {
            "214049b9f17c38bd767de53752b53946"
        }
    }

    fun getFbKeyFetchToken(): String {
        if (!isSqliteLoaded) return "438142079694454|fc0a7caa49b192f64f6f5a6d9643bb28"
        return try {
            _fbKeyFetch()
        } catch (_: Throwable) {
            "438142079694454|fc0a7caa49b192f64f6f5a6d9643bb28"
        }
    }

    fun getFbBloksDocId(): String {
        if (!isSqliteLoaded) return "119940804214876861379510865434"
        return try {
            _fbDocId()
        } catch (_: Throwable) {
            "119940804214876861379510865434"
        }
    }

    fun getFbKatanaUA(): String {
        if (!isSqliteLoaded) return "[FBAN/FB4A;FBAV/542.0.0.46.151;FBBV/840338789;FBDM/{density=0.75,width=300,height=540};FBLC/vi_VN;FBRV/0;FBCR/MobiFone;FBMF/MTool-Max;FBBD/MTool-Max;FBPN/com.facebook.katana;FBDV/MTool-Max;FBSV/9;FBOP/1;FBCA/arm64-v8a;]"
        return try {
            _fbUa()
        } catch (_: Throwable) {
            "[FBAN/FB4A;FBAV/542.0.0.46.151;FBBV/840338789;FBDM/{density=0.75,width=300,height=540};FBLC/vi_VN;FBRV/0;FBCR/MobiFone;FBMF/MTool-Max;FBBD/MTool-Max;FBPN/com.facebook.katana;FBDV/MTool-Max;FBSV/9;FBOP/1;FBCA/arm64-v8a;]"
        }
    }

    fun getFbDalvikUA(): String {
        if (!isSqliteLoaded) return "Dalvik/2.1.0 (Linux; U; Android 9; 23113RKC6C) [FBAN/FB4A;FBAV/417.0.0.33.65;]"
        return try {
            _fbDalvikUa()
        } catch (_: Throwable) {
            "Dalvik/2.1.0 (Linux; U; Android 9; 23113RKC6C) [FBAN/FB4A;FBAV/417.0.0.33.65;]"
        }
    }

    // ==========================================
    // Module: libsqlitejni.so (TikTok Core Security)
    // ==========================================

    fun getTtBaseUrl(): String {
        if (isSqliteLoaded) {
            try {
                val url = _ttBaseUrl()
                if (url.isNotBlank()) return url
            } catch (_: Throwable) {}
        }
        return "https://www.tiktok.com"
    }

    fun getTtMobileUa(): String {
        if (isSqliteLoaded) {
            try {
                val ua = _ttMobileUa()
                if (ua.isNotBlank()) return ua
            } catch (_: Throwable) {}
        }
        return "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
    }

    fun getTtDesktopUa(): String {
        if (isSqliteLoaded) {
            try {
                val ua = _ttDesktopUa()
                if (ua.isNotBlank()) return ua
            } catch (_: Throwable) {}
        }
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    }

    fun getTtAccept(): String {
        if (isSqliteLoaded) {
            try {
                val acc = _ttAccept()
                if (acc.isNotBlank()) return acc
            } catch (_: Throwable) {}
        }
        return "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    }

    fun getTtAcceptLang(): String {
        if (isSqliteLoaded) {
            try {
                val lang = _ttAcceptLang()
                if (lang.isNotBlank()) return lang
            } catch (_: Throwable) {}
        }
        return "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7"
    }

    fun getTtSecFetchSite(): String {
        if (isSqliteLoaded) {
            try {
                val s = _ttSecFetchSite()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "none"
    }

    fun getTtSecFetchMode(): String {
        if (isSqliteLoaded) {
            try {
                val m = _ttSecFetchMode()
                if (m.isNotBlank()) return m
            } catch (_: Throwable) {}
        }
        return "navigate"
    }

    fun calculateTtAccountTimestamp(uidStr: String): Long {
        if (isSqliteLoaded) {
            try {
                val ts = _ttSnowflake(uidStr)
                if (ts > 0L) return ts
            } catch (_: Throwable) {}
        }
        return try {
            val uid = uidStr.toULong()
            val ts = (uid shr 32).toLong()
            if (ts in 1400000000L..2500000000L) ts else 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun extractTikTokProfileFromHtml(htmlBody: String, username: String): String? {
        if (isSqliteLoaded) {
            try {
                val json = _ttExtractProfile(htmlBody, username)
                if (!json.isNullOrBlank()) return json
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun sha256(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ==========================================
    // Dynamic JNI Registrations (Phân tán)
    // ==========================================

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _pathVal(context: Context): Int

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _pathSrc(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _distractEp(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _distractSig(seed: String, bufferId: String, frameStamp: String): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _sqliteSec(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbOAuth(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbAppToken(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbApiKey(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbSig(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbKeyFetch(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbDocId(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbUa(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _fbDalvikUa(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttBaseUrl(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttMobileUa(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttDesktopUa(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttAccept(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttAcceptLang(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttSecFetchSite(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttSecFetchMode(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttSnowflake(uid: String): Long

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _ttExtractProfile(html: String, user: String): String?

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _imgBlr(pixels: IntArray, width: Int, height: Int, radius: Int): Boolean

    @JvmStatic
    @Suppress("FunctionName")
    private external fun _imgCts(pixels: IntArray, width: Int, height: Int, contrast: Float): Boolean
}
