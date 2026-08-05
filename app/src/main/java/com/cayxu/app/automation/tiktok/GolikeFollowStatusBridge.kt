package com.cayxu.app.automation.tiktok

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phát text mô tả BƯỚC HIỆN TẠI của luồng tự follow (TikTokAccessibilityService), vd
 * "Đang vuốt tải lại (2/3)...", "Đã bấm Follow, đang xác nhận với GoLike..." - để lớp nổi
 * (GolikeAddAccountOverlayService) hiện LIVE ngay tại chỗ trước đây hiện URL tĩnh, cho người
 * dùng thấy đang làm tới đâu thay vì chỉ thấy 1 dòng URL không đổi suốt quá trình.
 */
object GolikeFollowStatusBridge {
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun update(text: String) {
        _status.value = text
    }

    fun clear() {
        _status.value = ""
    }
}
