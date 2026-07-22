package com.cayxu.app.automation.tiktok

import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cầu nối trạng thái giữa TikTokAccessibilityService / lớp nổi (overlay) và màn hình
 * quản lý tài khoản TikTok trong app. Chỉ dùng RIÊNG cho luồng lấy tài khoản TikTok,
 * không đụng tới các nền tảng khác.
 */
sealed class TikTokCaptureState {
    data object Idle : TikTokCaptureState()

    /** Đang chờ người dùng bấm "Lưu @" trên lớp nổi trong khi ở app TikTok/Lite/Studio. */
    data class Waiting(val variant: TikTokAppVariant) : TikTokCaptureState()

    /** Đã quét được @handle, chờ lưu vào danh sách trong tool. */
    data class Captured(
        val handle: String,
        val displayName: String,
        val avatarUrl: String,
        val variant: TikTokAppVariant
    ) : TikTokCaptureState()

    data class Failed(val reason: String) : TikTokCaptureState()
}

object TikTokCaptureBridge {
    private val _state = MutableStateFlow<TikTokCaptureState>(TikTokCaptureState.Idle)
    val state: StateFlow<TikTokCaptureState> = _state.asStateFlow()

    // Thông báo tiến trình chi tiết (vd "Đang đợi TikTok tải xong...", "Đang tìm tab Tôi...")
    // để lớp nổi hiển thị đúng bước đang làm - KHÔNG phải chữ "Tôi" cố định trên lớp nổi,
    // mà là trạng thái tự dò tab "Tôi" ở THANH ĐIỀU HƯỚNG DƯỚI CÙNG của app TikTok thật.
    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    fun updateProgress(message: String) {
        _progress.value = message
    }

    /** Gọi khi người dùng bấm "TikTok Lite/..." và app đã mở, chờ bấm nút quét trên lớp nổi. */
    fun startWaiting(variant: TikTokAppVariant) {
        _progress.value = ""
        _state.value = TikTokCaptureState.Waiting(variant)
    }

    /** Gọi từ TikTokAccessibilityService khi lớp nổi được bấm và tìm thấy @handle trên màn hình. */
    fun onCaptured(handle: String, displayName: String, avatarUrl: String, variant: TikTokAppVariant) {
        _state.value = TikTokCaptureState.Captured(handle, displayName, avatarUrl, variant)
    }

    fun onFailed(reason: String) {
        _state.value = TikTokCaptureState.Failed(reason)
    }

    /** Gọi sau khi màn hình quản lý TikTok đã lưu xong tài khoản mới lấy được. */
    fun reset() {
        _progress.value = ""
        _state.value = TikTokCaptureState.Idle
    }
}
