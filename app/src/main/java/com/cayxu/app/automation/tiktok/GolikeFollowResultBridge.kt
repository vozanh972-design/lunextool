package com.cayxu.app.automation.tiktok

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phát KẾT QUẢ của luồng tự follow (GolikeFollowBridge) từ TikTokAccessibilityService sang
 * nơi khác (GolikeAddAccountOverlayService) - để biết CHÍNH XÁC lúc nào follow xong (thay vì
 * đợi 1 khoảng thời gian cố định), rồi mới gọi API xác nhận với GoLike.
 */
sealed class GolikeFollowResult {
    object Idle : GolikeFollowResult()

    /** Vừa phát hiện acc ĐÃ follow sẵn từ trước (thấy nút "Nhắn tin"/"Message" thay vì
     *  "Follow") - không cần bấm gì cả, coi như xong ngay. */
    data class AlreadyFollowed(val targetUsername: String, val packageName: String) : GolikeFollowResult()

    /** Vừa tìm thấy và bấm nút "Follow"/"Theo dõi" xong. */
    data class Clicked(val targetUsername: String, val packageName: String) : GolikeFollowResult()

    /** Đã thử hết số lần cho phép mà không thấy nút Follow lẫn nút Nhắn tin - có thể trang
     *  chưa load kịp hoặc giao diện TikTok khác dự kiến. */
    data class NotFound(val targetUsername: String, val packageName: String) : GolikeFollowResult()
}

object GolikeFollowResultBridge {
    private val _result = MutableStateFlow<GolikeFollowResult>(GolikeFollowResult.Idle)
    val result: StateFlow<GolikeFollowResult> = _result

    fun publish(result: GolikeFollowResult) {
        _result.value = result
    }

    fun clear() {
        _result.value = GolikeFollowResult.Idle
    }
}
