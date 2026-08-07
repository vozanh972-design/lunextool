package com.cayxu.app.ui.overlay.golike

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phát text trạng thái/lỗi LIVE cho màn nổi "Làm NV" (GolikeJobRunnerOverlayService) - dùng
 * khi hệ thống chạy job thật (chưa có trong project này) muốn cập nhật dòng thông báo phía
 * dưới link job, kiểu "Web CayXu báo lỗi: HTTP 400: ..." như trong ảnh mẫu, mà KHÔNG cần
 * đóng/mở lại màn nổi.
 */
object GolikeJobStatusBridge {
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun update(text: String) {
        _status.value = text
    }

    fun clear() {
        _status.value = ""
    }
}
