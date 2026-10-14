package com.cayxu.app.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.Keep

/**
 * Cầu nối tầng Native C++: Giải mã ma trận ảnh và URL banner từ libapp_vault.so.
 * 100% dữ liệu nhị phân của ảnh và URL đều nằm trong C++, không có bất kỳ thông tin
 * nào về file ảnh hay link web ở tầng Java/Kotlin/Resources.
 */
@Keep
object NativeBannerBridge {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("app_vault")
            isLoaded = true
        } catch (e: Throwable) {
            Log.e("NativeBannerBridge", "Failed to load app_vault: ${e.message}", e)
        }
    }

    @JvmStatic
    private external fun getBannerData(index: Int): ByteArray?

    @JvmStatic
    private external fun getBannerUrl(index: Int): String?

    @JvmStatic
    private external fun getBannerDesc(index: Int): String?

    /**
     * Giải mã dữ liệu nhị phân ảnh trực tiếp trong RAM thành Bitmap.
     * Độ phân giải giữ nguyên 100% gốc 1000×571 px.
     */
    fun loadBannerBitmap(index: Int): Bitmap? {
        if (!isLoaded) {
            Log.w("NativeBannerBridge", "loadBannerBitmap($index) skipped: app_vault not loaded")
            return null
        }
        return try {
            val bytes = getBannerData(index)
            if (bytes == null || bytes.isEmpty()) {
                Log.w("NativeBannerBridge", "getBannerData($index) returned empty")
                return null
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp == null) {
                Log.e("NativeBannerBridge", "BitmapFactory decode failed for banner $index (bytes: ${bytes.size})")
            }
            bmp
        } catch (e: Throwable) {
            Log.e("NativeBannerBridge", "Failed to decode banner $index: ${e.message}", e)
            null
        }
    }

    /**
     * Giải mã URL từ C++ runtime. Hoàn toàn không chứa URL trong chuỗi Kotlin.
     */
    fun loadBannerUrl(index: Int): String {
        if (!isLoaded) return ""
        return try {
            getBannerUrl(index).orEmpty()
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Giải mã mô tả từ C++ runtime.
     */
    fun loadBannerDesc(index: Int): String {
        if (!isLoaded) return ""
        return try {
            getBannerDesc(index).orEmpty()
        } catch (e: Throwable) {
            ""
        }
    }
}
