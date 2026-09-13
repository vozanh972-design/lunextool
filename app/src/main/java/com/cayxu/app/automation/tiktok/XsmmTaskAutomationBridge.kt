package com.cayxu.app.automation.tiktok

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class XsmmTaskAction {
    data object Idle : XsmmTaskAction()
    data class DoTask(
        val taskType: String,
        val swipeBefore: Boolean,
        val returnHomeAndSwipe: Boolean,
        val durationSeconds: Int,
        val actionId: Long = System.currentTimeMillis()
    ) : XsmmTaskAction()
}

sealed class XsmmTaskActionResult {
    data object Idle : XsmmTaskActionResult()
    data class InProgress(val message: String) : XsmmTaskActionResult()
    data class Completed(val actionId: Long, val success: Boolean, val message: String) : XsmmTaskActionResult()
}

/**
 * Cầu nối giữa XsmmJobRunnerOverlayService và TikTokAccessibilityService.
 * Giúp tự động bấm Follow, thả tim (Like), quay về Home và lướt tin video TikTok.
 */
object XsmmTaskAutomationBridge {
    private val _action = MutableStateFlow<XsmmTaskAction>(XsmmTaskAction.Idle)
    val action: StateFlow<XsmmTaskAction> = _action.asStateFlow()

    private val _result = MutableStateFlow<XsmmTaskActionResult>(XsmmTaskActionResult.Idle)
    val result: StateFlow<XsmmTaskActionResult> = _result.asStateFlow()

    fun triggerTask(
        taskType: String,
        swipeBefore: Boolean,
        returnHomeAndSwipe: Boolean,
        durationSeconds: Int
    ): Long {
        val id = System.currentTimeMillis()
        _result.value = XsmmTaskActionResult.InProgress("Đang chờ TikTok mở...")
        _action.value = XsmmTaskAction.DoTask(
            taskType = taskType,
            swipeBefore = swipeBefore,
            returnHomeAndSwipe = returnHomeAndSwipe,
            durationSeconds = durationSeconds,
            actionId = id
        )
        return id
    }

    fun updateProgress(message: String) {
        _result.value = XsmmTaskActionResult.InProgress(message)
    }

    fun completeTask(actionId: Long, success: Boolean, message: String) {
        _result.value = XsmmTaskActionResult.Completed(actionId, success, message)
        _action.value = XsmmTaskAction.Idle
    }

    fun reset() {
        _action.value = XsmmTaskAction.Idle
        _result.value = XsmmTaskActionResult.Idle
    }
}
