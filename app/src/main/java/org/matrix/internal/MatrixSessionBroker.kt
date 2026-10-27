package org.matrix.internal

import androidx.annotation.Keep

/**
 * Matrix Distributed Session Broker (Ngụy trang dưới dạng thư viện mở Apache/Matrix).
 * Phân tán logic tính toán chữ ký số và xác minh môi trường nhị phân.
 */
@Keep
object MatrixSessionBroker {

    private const val FNV_PRIME = 0x100000001B3L
    private const val FNV_OFFSET_BASIS = 0xCBF29CE484222325L

    /**
     * Băm mã kiểm tra tính toàn vẹn 64-bit chuẩn FNV-1a.
     */
    @JvmStatic
    fun computeIntegrityDigest(data: String): Long {
        var hash = FNV_OFFSET_BASIS
        for (c in data) {
            hash = (hash xor c.code.toLong()) * FNV_PRIME
        }
        return hash
    }

    /**
     * Kiểm tra trạng thái hợp lệ của vé phiên phân tán.
     */
    @JvmStatic
    fun isTicketSane(ticket: Long): Boolean {
        // Vé hợp lệ không bao giờ là 0 và không phải là mẫu số lỗi -1
        return ticket != 0L && ticket != -1L
    }
}
