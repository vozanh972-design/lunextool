package com.cayxu.app.generated

import androidx.annotation.Keep

/**
 * AOV Anti-Cheat & Dynamic Patch Guard (Ngụy trang bẫy Honeypot kiểu Tencent/AOV).
 * Đóng vai trò là bẫy mồi nhử cho các công cụ dịch ngược (MT Manager, JADX).
 *
 * NẾU cracker can thiệp sửa các hàm này (như đổi isUpdateBypassed thành true,
 * hoặc sửa getPatchSignatureStamp), tầng C++ sẽ phát hiện tem toán học bị lệch
 * và lập tức vô hiệu hóa ma trận cập nhật, khóa hoàn toàn URL tải APK!
 */
@Keep
object _AovSecurityPatchGuard {

    private const val PATCH_SEED_MASK: Long = 0x4B9D2AC58F61D31EL
    private const val AOV_SENTINEL_NONCE: Long = 0x74BE073A6CA25809L

    // --- CÁC HÀM BẪY MỒI NHỬ (HONEYPOT TRAPS) ---
    // Cracker khi tìm từ khóa "update", "bypass", "license" sẽ cố tình sửa các hàm này:
    @JvmStatic
    fun isUpdateBypassed(): Boolean {
        // Giá trị gốc chuẩn xác: luôn là false
        return false
    }

    @JvmStatic
    fun isLicenseExempt(): Boolean {
        // Giá trị gốc chuẩn xác: luôn là false
        return false
    }

    @JvmStatic
    fun getDebugBypassToken(): String {
        // Giá trị gốc chuẩn xác: luôn là chuỗi rỗng
        return ""
    }

    // --- TEM TOÁN HỌC XÁC THỰC TÍNH TOÀN VẸN 2 CHIỀU VỚI C++ ---
    @JvmStatic
    fun queryPatchStamp(nonce: Long): Long {
        return (nonce xor PATCH_SEED_MASK) + AOV_SENTINEL_NONCE
    }

    @JvmStatic
    fun verifyDecoyIntegrity(): Boolean {
        if (isUpdateBypassed()) return false
        if (isLicenseExempt()) return false
        if (getDebugBypassToken().isNotEmpty()) return false
        return true
    }
}
