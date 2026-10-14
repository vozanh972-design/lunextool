package com.cayxu.app.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * Cầu nối tầng Native C++: Giải mã ma trận ảnh và URL banner từ libimagepipeline.so.
 * 100% dữ liệu nhị phân của ảnh và URL đều nằm trong C++, không có bất kỳ thông tin
 * nào về file ảnh hay link web ở tầng Java/Kotlin/Resources.
 */
object NativeBannerBridge {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("imagepipeline")
            isLoaded = true
        } catch (e: Throwable) {
            Log.e("NativeBannerBridge", "Native lib load error: ${e.message}")
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
        if (!isLoaded) return null
        return try {
            val bytes = getBannerData(index) ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Throwable) {
            Log.e("NativeBannerBridge", "Failed to decode banner $index: ${e.message}")
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
