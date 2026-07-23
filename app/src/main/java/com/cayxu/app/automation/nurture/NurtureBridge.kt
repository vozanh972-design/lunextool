package com.cayxu.app.automation.nurture

import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cầu nối trạng thái phiên "Nuôi tài khoản" - RIÊNG cho tính năng này, không đụng tới
 * TikTokCaptureBridge (dùng cho luồng thêm tài khoản). TikTokAccessibilityService đọc
 * state này để biết: đang nuôi tài khoản nào, bật những hành động gì (chỉ chạy ĐÚNG những
 * cái người dùng đã bật trong cấu hình, không tự thêm hành động ngoài ý muốn), và nuôi tới
 * mốc thời gian nào thì dừng.
 */
sealed class NurtureState {
    data object Idle : NurtureState()

    data class Running(
        val variant: TikTokAppVariant,
        val autoWatch: Boolean,
        val viewComments: Boolean,
        val copyLink: Boolean,
        val repost: Boolean,
        val endAtMillis: Long
    ) : NurtureState()
}

object NurtureBridge {
    private val _state = MutableStateFlow<NurtureState>(NurtureState.Idle)
    val state: StateFlow<NurtureState> = _state.asStateFlow()

    fun start(
        variant: TikTokAppVariant,
        autoWatch: Boolean,
        viewComments: Boolean,
        copyLink: Boolean,
        repost: Boolean,
        durationMinutes: Int
    ) {
        _state.value = NurtureState.Running(
            variant = variant,
            autoWatch = autoWatch,
            viewComments = viewComments,
            copyLink = copyLink,
            repost = repost,
            endAtMillis = System.currentTimeMillis() + durationMinutes.coerceAtLeast(1) * 60_000L
        )
    }

    fun stop() {
        _state.value = NurtureState.Idle
    }
}
