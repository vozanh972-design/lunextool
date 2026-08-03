package com.cayxu.app.automation.tiktok

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cầu nối phát yêu cầu "tự bấm Follow" từ luồng Thêm tài khoản vào GoLike sang
 * TikTokAccessibilityService. Chỉ có tác dụng nếu người dùng đã bật Accessibility Service
 * của tool; nếu chưa bật thì không có gì xảy ra, không lỗi gì cả.
 */
sealed class GolikeFollowState {
    object Idle : GolikeFollowState()
    data class Pending(
        val targetUsername: String,
        val packageName: String,
        val requestId: Long
    ) : GolikeFollowState()
}

object GolikeFollowBridge {
    private val _state = MutableStateFlow<GolikeFollowState>(GolikeFollowState.Idle)
    val state: StateFlow<GolikeFollowState> = _state

    private var counter = 0L

    fun requestFollow(targetUsername: String, packageName: String) {
        counter++
        _state.value = GolikeFollowState.Pending(
            targetUsername = targetUsername,
            packageName = packageName,
            requestId = counter
        )
    }

    fun clear() {
        _state.value = GolikeFollowState.Idle
    }
}
