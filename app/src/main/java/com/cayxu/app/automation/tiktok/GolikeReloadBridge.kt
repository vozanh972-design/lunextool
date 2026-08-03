package com.cayxu.app.automation.tiktok

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cầu nối để yêu cầu TikTokAccessibilityService thực hiện 1 lần "vuốt từ giữa màn hình
 * xuống dưới cùng" để ép TikTok tải lại - một số máy sau khi mở link/chuyển sang TikTok bị
 * lag/đứng hình, không tự load được gì, phải tự vuốt tay mới tải lại - thao tác này làm y
 * hệt đúng cử chỉ đó.
 *
 * CHỈ thực hiện đúng 1 cử chỉ vuốt xuống để tải lại - KHÔNG follow/like/comment hay bất kỳ
 * thao tác nào khác. Đây là cách khắc phục lỗi tải chậm/lag, không phải tự động hoá tương tác.
 */
object GolikeReloadBridge {
    // Dùng số đếm tăng dần thay vì Boolean - để MỖI lần gọi requestReload() đều kích hoạt
    // lại được (kể cả khi gọi liên tiếp trước khi lần trước kịp xử lý xong).
    private val _requestTick = MutableStateFlow(0L)
    val requestTick: StateFlow<Long> = _requestTick

    fun requestReload() {
        _requestTick.value = _requestTick.value + 1
    }
}
