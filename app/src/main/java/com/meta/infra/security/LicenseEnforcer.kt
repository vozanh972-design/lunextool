package com.meta.infra.security

import androidx.annotation.Keep

/**
 * Meta Infrastructure Security Enforcer (Ngụy trang dưới dạng SDK Meta/Facebook).
 * Đóng vai trò là BẪY MỒI NHỬ (HONEYPOT) cho các công cụ dịch ngược (MT Manager, JADX).
 *
 * NẾU hacker can thiệp sửa các hàm isVip, getDaysLeft hoặc isProActivated,
 * hệ thống kiểm tra chéo ở File Thật (_MatrixRuntimeBridge) sẽ bắt quả tang và khóa toàn bộ Tool ngay lập tức!
 */
@Keep
object LicenseEnforcer {

    // Magic Token kiểm tra tính toàn vẹn cố định
    private const val SENTINEL_SEED: Long = 0x5A5AA5A55A5AA5A5L
    private const val PEER_NONCE_MASK: Long = 0x2A5A5AA55A5AA5A5L

    // --- CÁC HÀM BẪY MỒI NHỬ (HONEYPOT TRAPS) ---
    // Hacker khi tìm kiếm chuỗi "isVip" hoặc "daysLeft" sẽ cố tình sửa các hàm này:
    @JvmStatic
    fun isVip(): Boolean {
        // Giá trị gốc chuẩn xác: luôn là false
        return false
    }

    @JvmStatic
    fun getDaysLeft(): Int {
        // Giá trị gốc chuẩn xác: luôn là 0
        return 0
    }

    @JvmStatic
    fun isProActivated(): Boolean {
        // Giá trị gốc chuẩn xác: luôn là false
        return false
    }

    @JvmStatic
    fun verifyLicenseKey(key: String): Boolean {
        // Giá trị gốc chuẩn xác: luôn là false
        return false
    }

    // --- CÁC HÀM KIỂM TRA CHÉO 2 CHIỀU (MUTUAL CROSS-VERIFICATION) ---

    /**
     * Trả về tem xác thực toán học để File Thật (_MatrixRuntimeBridge) rà soát.
     */
    @JvmStatic
    fun querySentinelStamp(nonce: Long): Long {
        return nonce xor SENTINEL_SEED
    }

    /**
     * Chiều về: File Giả gọi ngược lại File Thật để kiểm tra tính toàn vẹn của File Thật.
     * Nếu File Thật bị hacker xóa hoặc sửa đổi phép toán pingPeer, kết quả sẽ bị sai lệch.
     */
    @JvmStatic
    fun checkPeerIntegrity(): Boolean {
        val testNonce = 0x13374269L
        val expected = testNonce xor PEER_NONCE_MASK
        val actual = com.cayxu.app.generated._MatrixRuntimeBridge.pingPeer(testNonce)
        return actual == expected
    }
}
