package com.cayxu.app.automation.tiktok

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phát kết quả của luồng CHỈ chuyển tài khoản, không follow (GolikeSwitchAccountBridge với
 * skipFollow = true) - dùng cho "Làm NV". Tách RIÊNG khỏi GolikeFollowResultBridge (bridge
 * đó dành cho luồng "Thêm", có nơi khác đang lắng nghe để gọi API verify-account-id - nếu
 * dùng chung dễ bị gọi nhầm API đó trong lúc đang Làm NV).
 */
sealed class GolikeSwitchOnlyResult {
    object Idle : GolikeSwitchOnlyResult()
    data class Ready(val handle: String, val packageName: String) : GolikeSwitchOnlyResult()
    data class NotFound(val handle: String) : GolikeSwitchOnlyResult()
}

object GolikeSwitchOnlyResultBridge {
    private val _result = MutableStateFlow<GolikeSwitchOnlyResult>(GolikeSwitchOnlyResult.Idle)
    val result: StateFlow<GolikeSwitchOnlyResult> = _result

    fun publish(result: GolikeSwitchOnlyResult) {
        _result.value = result
    }

    fun clear() {
        _result.value = GolikeSwitchOnlyResult.Idle
    }
}
