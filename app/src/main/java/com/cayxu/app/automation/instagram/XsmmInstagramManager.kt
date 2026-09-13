package com.cayxu.app.automation.instagram

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Singleton quản lý tác vụ chạy ngầm Instagram cho XSMM.
 * Tác vụ chạy trên Global Scope (Dispatchers.IO + SupervisorJob)
 * giúp việc chuyển màn hình, out ra ngoài (minimize app) không làm gián đoạn tool.
 */
object XsmmInstagramManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null

    // Trạng thái quan sát trực tiếp bởi Jetpack Compose
    val runningAccount = mutableStateOf<String?>(null)
    val statusMap = mutableStateMapOf<String, String>()
    val successCountMap = mutableStateMapOf<String, Int>()
    val errorCountMap = mutableStateMapOf<String, Int>()

    fun isRunning(accountUsername: String): Boolean {
        val clean = accountUsername.trim().lowercase()
        return runningAccount.value?.trim()?.lowercase() == clean
    }

    fun isAnyRunning(): Boolean {
        return runningAccount.value != null
    }

    fun start(context: Context, accountUsername: String) {
        val clean = accountUsername.trim()
        if (runningAccount.value != null) {
            return
        }

        runningAccount.value = clean
        statusMap[clean] = "Bắt đầu khởi động tác vụ..."
        successCountMap[clean] = 0
        errorCountMap[clean] = 0

        currentJob = scope.launch {
            try {
                XsmmInstagramTaskRunner.run(
                    context = context.applicationContext,
                    accountUsernames = listOf(clean),
                    onStatusUpdate = { status ->
                        scope.launch(Dispatchers.Main) {
                            statusMap[clean] = status
                        }
                    },
                    onProgressUpdate = { status, success, errors ->
                        scope.launch(Dispatchers.Main) {
                            statusMap[clean] = status
                            successCountMap[clean] = success
                            errorCountMap[clean] = errors
                        }
                    }
                )
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) {
                    statusMap[clean] = "Lỗi ngoài ý muốn: ${e.message}"
                }
            } finally {
                scope.launch(Dispatchers.Main) {
                    if (runningAccount.value == clean) {
                        runningAccount.value = null
                    }
                }
            }
        }
    }

    fun stop(accountUsername: String) {
        val clean = accountUsername.trim()
        if (runningAccount.value?.trim()?.lowercase() == clean.lowercase()) {
            currentJob?.cancel()
            currentJob = null
            runningAccount.value = null
            statusMap[clean] = "Đã dừng chạy"
        }
    }

    fun stopAll() {
        currentJob?.cancel()
        currentJob = null
        val cur = runningAccount.value
        if (cur != null) {
            statusMap[cur] = "Đã dừng chạy"
        }
        runningAccount.value = null
    }
}
