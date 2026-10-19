package com.cayxu.app.ui.screens.golike

import android.content.Context
import androidx.annotation.Keep

/**
 * Cầu nối JNI kết nối module bảo mật C++ OLLVM-Hardened (libgolike_sec.so).
 * Giấu kín toàn bộ chuỗi User-Agent, Base URLs, Thuật toán giải Captcha và Vault WASM.
 * Tích hợp Ma trận 32-byte chống máy chủ giả mạo (Anti-Fake-Server) và kiểm tra bản quyền.
 */
@Keep
object GolikeNativeBridge {

    private var isLoaded = false

    init {
        try {
            System.loadLibrary("golike_sec")
            isLoaded = true
        } catch (e: Throwable) {
            isLoaded = false
            android.util.Log.w("GolikeNative", "Chưa nạp được libgolike_sec.so, sử dụng cơ chế an toàn: ${e.message}")
        }
    }

    @JvmStatic
    external fun getUserAgent(): String

    @JvmStatic
    external fun getBaseUrl(): String

    @JvmStatic
    external fun getSecurityUrl(): String

    @JvmStatic
    external fun getPathGetJob(): String

    @JvmStatic
    external fun getPathCompleteJob(): String

    @JvmStatic
    external fun getPathToken(): String

    @JvmStatic
    external fun getPathChallenge(): String

    @JvmStatic
    external fun getPathVerify(): String

    @JvmStatic
    external fun getWasmBytes(): ByteArray?

    @JvmStatic
    external fun getTelemetryJson(type: Int): String

    @JvmStatic
    external fun solveBezierPoints(
        startX: Double, startY: Double,
        waypointX: Double, waypointY: Double,
        targetX: Double, targetY: Double
    ): String

    @JvmStatic
    external fun routeSessionSync(
        p0: String,
        p1: String,
        p2: String,
        p3: String
    ): Int

    // ─────────────────────────────────────────────────────────────────────────
    // Safe Wrapper Methods với 100% Fallback đảm bảo không bao giờ gián đoạn
    // ─────────────────────────────────────────────────────────────────────────

    fun fetchUserAgent(): String {
        if (isLoaded) {
            try {
                val s = getUserAgent()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "Mozilla/5.0 (iPhone; CPU iPhone OS 15_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.6 Mobile/15E148 Safari/604.1"
    }

    fun fetchBaseUrl(): String {
        if (isLoaded) {
            try {
                val s = getBaseUrl()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "https://gateway.golike.net/api"
    }

    fun fetchSecurityUrl(): String {
        if (isLoaded) {
            try {
                val s = getSecurityUrl()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "https://api.golike.net/api/v1/security"
    }

    fun fetchPathGetJob(): String {
        if (isLoaded) {
            try {
                val s = getPathGetJob()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "/advertising/publishers/tiktok/jobs"
    }

    fun fetchPathCompleteJob(): String {
        if (isLoaded) {
            try {
                val s = getPathCompleteJob()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "/advertising/publishers/tiktok/complete-jobs"
    }

    fun fetchPathToken(): String {
        if (isLoaded) {
            try {
                val s = getPathToken()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "/api/v1/security/token"
    }

    fun fetchPathChallenge(): String {
        if (isLoaded) {
            try {
                val s = getPathChallenge()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "/api/v1/security/captcha/challenge"
    }

    fun fetchPathVerify(): String {
        if (isLoaded) {
            try {
                val s = getPathVerify()
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        return "/api/v1/security/captcha/verify"
    }

    fun fetchWasmBytes(): ByteArray? {
        if (isLoaded) {
            try {
                val b = getWasmBytes()
                if (b != null && b.isNotEmpty()) return b
            } catch (_: Throwable) {}
        }
        return null
    }

    fun fetchTelemetryJson(type: Int): String {
        if (isLoaded) {
            try {
                val s = getTelemetryJson(type)
                if (s.isNotBlank()) return s
            } catch (_: Throwable) {}
        }
        val idle = if (type == 0) (90..130).random() else (70..110).random()
        val iev = if (type == 0) (6..12).random() else (12..18).random()
        return "{\"trg\":\"click\",\"aut\":0,\"idle\":$idle,\"iev\":$iev,\"vis\":1,\"foc\":1}"
    }

    fun fetchBezierPoints(
        startX: Double, startY: Double,
        waypointX: Double, waypointY: Double,
        targetX: Double, targetY: Double
    ): String? {
        if (isLoaded) {
            try {
                val s = solveBezierPoints(startX, startY, waypointX, waypointY, targetX, targetY)
                if (s.isNotBlank() && s.startsWith("[")) return s
            } catch (_: Throwable) {}
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bộ đồng bộ và kiểm tra tính toàn vẹn phiên làm việc Golike (Stealth Guard)
    // ─────────────────────────────────────────────────────────────────────────

    fun checkSessionIntegrity(context: Context): String? {
        val prefs = com.cayxu.app.data.local.SecurePrefs(context)
        val k = prefs.getKey()
        if (k.isNullOrBlank()) {
            return "Phiên làm việc Golike chưa được xác thực (Mã 0x10)"
        }
        val d = com.cayxu.app.util.DeviceUtils.getAndroidId(context)
        val f = prefs.getKeyFingerprint().orEmpty()
        val s = com.cayxu.app.util.NativeSecurity.getSecureBaseUrl()

        if (isLoaded) {
            try {
                val code = routeSessionSync(k, d, f, s)
                if (code != 1) {
                    return "Lỗi đồng bộ giao thức Golike [0x7E]: Gói tin định tuyến không khớp. Vui lòng thử lại!"
                }
                return null
            } catch (_: Throwable) {}
        }

        // Fallback kiểm tra ngầm
        if (k.length < 8 || !s.contains("lunex.io.vn") || f.length < 16) {
            return "Lỗi đồng bộ giao thức Golike [0x7E]: Gói tin định tuyến không khớp. Vui lòng thử lại!"
        }
        return null
    }
}
