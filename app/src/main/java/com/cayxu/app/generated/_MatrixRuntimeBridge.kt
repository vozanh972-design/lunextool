package com.cayxu.app.generated

import androidx.annotation.Keep
import com.meta.infra.security.LicenseEnforcer

/**
 * Synthetic Generated Runtime Bridge (Ngụy trang dưới dạng class tự sinh bởi Compiler).
 * Thực hiện cơ chế Kiểm tra chéo 2 chiều (Mutual Cross-Verification):
 * File Thật rà soát File Giả (Honeypot) và File Giả rà soát ngược lại File Thật.
 */
@Keep
object _MatrixRuntimeBridge {

    private const val PEER_NONCE_MASK: Long = 0x2A5A5AA55A5AA5A5L
    private const val SENTINEL_SEED: Long = 0x5A5AA5A55A5AA5A5L

    /**
     * Hàm bắt tay cho File Giả (LicenseEnforcer) gọi ngược lại kiểm tra File Thật.
     */
    @JvmStatic
    fun pingPeer(nonce: Long): Long {
        return nonce xor PEER_NONCE_MASK
    }

    /**
     * Kiểm tra chéo toàn diện 2 chiều giữa File Thật và File Giả:
     * 1. Kiểm tra xem bẫy Honeypot có bị hacker sửa thành "true" hay "99999" không.
     * 2. Kiểm tra tem toán học của File Giả.
     * 3. Kích hoạt File Giả gọi ngược lại kiểm tra File Thật.
     *
     * @return true nếu cả 2 file còn nguyên vẹn 100% logic gốc từ chính chủ.
     */
    @JvmStatic
    fun verifyMutualIntegrity(): Boolean {
        // [Kiểm tra 1] Rà soát các hàm bẫy trong File Giả (Honeypot Trap)
        // Nếu kẻ crack dùng MT Manager đổi isVip() thành true, hoặc getDaysLeft() > 365:
        if (LicenseEnforcer.isVip()) {
            return false
        }
        if (LicenseEnforcer.getDaysLeft() > 365) {
            return false
        }
        if (LicenseEnforcer.isProActivated()) {
            return false
        }

        // [Kiểm tra 2] Kiểm tra tem toán học của File Giả
        val nonce = System.currentTimeMillis()
        val stamp = LicenseEnforcer.querySentinelStamp(nonce)
        if (stamp != (nonce xor SENTINEL_SEED)) {
            return false
        }

        // [Kiểm tra 3] Chiều về: Yêu cầu File Giả gọi ngược lại File Thật để xác nhận tính toàn vẹn 2 chiều
        if (!LicenseEnforcer.checkPeerIntegrity()) {
            return false
        }

        return true
    }
}
