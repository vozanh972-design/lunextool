package com.cayxu.app.automation.tiktok

import com.cayxu.app.data.local.TikTokAppVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Yêu cầu TikTokAccessibilityService: mở Hồ sơ -> menu (3 gạch) -> "Cài đặt và quyền riêng
 * tư" -> "Chuyển đổi tài khoản" (TÁI SỬ DỤNG các hàm dò UI đã có sẵn cho luồng "check acc
 * tiktok", KHÔNG đụng tới luồng đó) -> tìm ĐÚNG tài khoản [targetHandle] trong danh sách và
 * bấm chọn -> đợi chuyển xong -> rồi mới mở deep link tới [followTargetUsername] để follow
 * (BỎ QUA nếu [skipFollow] = true - dùng cho "Làm NV", chỉ cần chuyển đúng acc, không follow
 * gì cả).
 *
 * CHỈ áp dụng cho TikTokAppVariant.STANDARD - vì màn "Chuyển đổi tài khoản" chỉ có ở bản
 * TikTok chuẩn (xem comment gốc trong TikTokAccessibilityService.startPollingSwitchAccountList).
 */
sealed class GolikeSwitchAccountState {
    object Idle : GolikeSwitchAccountState()
    data class Pending(
        val targetHandle: String,
        val followTargetUsername: String,
        val packageName: String,
        val variant: TikTokAppVariant,
        val skipFollow: Boolean = false
    ) : GolikeSwitchAccountState()
}

object GolikeSwitchAccountBridge {
    private val _state = MutableStateFlow<GolikeSwitchAccountState>(GolikeSwitchAccountState.Idle)
    val state: StateFlow<GolikeSwitchAccountState> = _state

    fun requestSwitch(
        targetHandle: String,
        followTargetUsername: String,
        packageName: String,
        variant: TikTokAppVariant,
        skipFollow: Boolean = false
    ) {
        _state.value = GolikeSwitchAccountState.Pending(targetHandle, followTargetUsername, packageName, variant, skipFollow)
    }

    fun clear() {
        _state.value = GolikeSwitchAccountState.Idle
    }
}
