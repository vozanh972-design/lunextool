package com.cayxu.app.util

import android.content.Context

/**
 * Cầu nối bảo mật JNI / Native C++:
 * - Chống Root, Máy ảo (Emulator), Debugger, Hook (Frida/Xposed) trực tiếp qua kernel Linux
 * - Ẩn URL Server, Endpoint và tính toán băm chữ ký trong mã máy nhị phân (.so)
 * - Tích hợp fallback an toàn đảm bảo app không bao giờ bị crash
 */
object NativeSecurity {

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("security_guard")
            isNativeLoaded = true
        } catch (e: Throwable) {
            isNativeLoaded = false
        }
    }

    /**
     * Kiểm tra môi trường an toàn từ tầng C++:
     * 0: An toàn (Clean)
     * 1: Phát hiện Root (Rooted)
     * 2: Phát hiện Máy ảo (Emulator)
     * 3: Phát hiện Debugger / Hook (Frida / Xposed / Tracer)
     */
    fun checkSecurityEnvironment(context: Context): Int {
        if (!isNativeLoaded) return 0
        return try {
            nativeCheckSecurityEnvironment(context)
        } catch (e: Throwable) {
            0
        }
    }

    fun isDeviceCompromised(context: Context): Boolean {
        val code = checkSecurityEnvironment(context)
        return code in 1..3
    }

    fun getSecureBaseUrl(): String {
        if (isNativeLoaded) {
            try {
                val url = nativeGetSecureBaseUrl()
                if (url.isNotBlank()) return url
            } catch (_: Throwable) {}
        }
        return "https://lunex.io.vn/"
    }

    fun getSecureVerifyPath(): String {
        if (isNativeLoaded) {
            try {
                val path = nativeGetSecureVerifyPath()
                if (path.isNotBlank()) return path
            } catch (_: Throwable) {}
        }
        return "api/verify_key.php"
    }

    fun computeNativeKeyHash(key: String, deviceId: String, sigHash: String): String {
        if (isNativeLoaded) {
            try {
                val hash = nativeComputeNativeKeyHash(key, deviceId, sigHash)
                if (hash.isNotBlank()) return hash
            } catch (_: Throwable) {}
        }
        return sha256("$key|$deviceId|$sigHash|fallback_guard_2026")
    }

    @JvmStatic
    @Suppress("FunctionName")
    private external fun nativeCheckSecurityEnvironment(context: Context): Int

    @JvmStatic
    @Suppress("FunctionName")
    private external fun nativeGetSecureBaseUrl(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun nativeGetSecureVerifyPath(): String

    @JvmStatic
    @Suppress("FunctionName")
    private external fun nativeComputeNativeKeyHash(key: String, deviceId: String, sigHash: String): String
}
