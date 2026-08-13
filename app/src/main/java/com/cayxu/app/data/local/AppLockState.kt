package com.cayxu.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cầu nối in-memory giữa các nơi phát hiện sự cố bảo mật (KeyRecheckWorker chạy
 * nền, CayXuApp lúc khởi động...) và NavGraph, để ÉP UI phản ứng NGAY LẬP TỨC dù
 * người dùng đang đứng ở bất kỳ màn nào (Golike, Home...), thay vì chỉ âm thầm
 * lưu prefs rồi đợi họ tự thoát app mở lại mới thấy thay đổi.
 *
 * Có 2 tín hiệu RIÊNG BIỆT, dẫn tới 2 kết quả khác nhau:
 *
 * - [blocked] (khoá vĩnh viễn): CHỈ dùng khi phát hiện app bị patch/bypass/crack
 *   (xem IntegrityGuard). Đây là hành vi can thiệp vào chính ứng dụng, không có
 *   đường quay lại - NavGraph sẽ điều hướng thẳng tới BlockedScreen.
 *
 * - [keyRevoked] (key hết hạn/bị server thu hồi): KHÔNG phải bị crack, chỉ là key
 *   không còn hợp lệ (hết hạn, đang dùng máy khác...). NavGraph sẽ điều hướng về
 *   màn nhập Key (Login) để người dùng tự nhập key mới/gia hạn, KHÔNG khoá chết.
 */
object AppLockState {
    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked

    /** Gọi khi phát hiện app bị patch/bypass/crack - khoá vĩnh viễn, không có đường lùi. */
    fun markBlocked() {
        _blocked.value = true
    }

    private val _keyRevoked = MutableStateFlow(false)
    val keyRevoked: StateFlow<Boolean> = _keyRevoked

    /** Gọi khi key bị server thu hồi/hết hạn - đưa về màn nhập key, không khoá chết. */
    fun markKeyRevoked() {
        _keyRevoked.value = true
    }

    /** Reset lại tín hiệu sau khi NavGraph đã xử lý xong (điều hướng về Login). */
    fun consumeKeyRevoked() {
        _keyRevoked.value = false
    }
}
