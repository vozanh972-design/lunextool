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
    val lastErrorDetail = mutableStateMapOf<String, String>()

    fun isRunning(accountUsername: String): Boolean {
        val clean = accountUsername.trim().lowercase()
        return runningAccount.value?.trim()?.lowercase() == clean
    }

    fun isAnyRunning(): Boolean {
        return runningAccount.value != null
    }

    fun start(context: Context, accountUsername: String) {
        startAccounts(context, listOf(accountUsername))
    }

    fun startAccounts(context: Context, accountUsernames: List<String>) {
        val cleanList = accountUsernames.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanList.isEmpty() || runningAccount.value != null) {
            return
        }

        val primary = cleanList.first()
        runningAccount.value = primary
        statusMap[primary] = "Bắt đầu khởi động tác vụ..."
        successCountMap[primary] = 0
        errorCountMap[primary] = 0

        currentJob = scope.launch {
            try {
                XsmmInstagramTaskRunner.run(
                    context = context.applicationContext,
                    accountUsernames = cleanList,
                    onStatusUpdate = { status ->
                        scope.launch(Dispatchers.Main) {
                            val cur = runningAccount.value ?: primary
                            statusMap[cur] = status
                        }
                    },
                    onProgressUpdate = { status, success, errors ->
                        scope.launch(Dispatchers.Main) {
                            val cur = runningAccount.value ?: primary
                            statusMap[cur] = status
                            successCountMap[cur] = success
                            errorCountMap[cur] = errors
                        }
                    },
                    onErrorDetail = { username, detail ->
                        scope.launch(Dispatchers.Main) {
                            lastErrorDetail[username] = detail
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Người dùng chủ động bấm Dừng -> không coi là lỗi
                val cur = runningAccount.value ?: primary
                scope.launch(Dispatchers.Main) {
                    statusMap[cur] = "Đã dừng chạy"
                }
            } catch (e: Exception) {
                val cur = runningAccount.value ?: primary
                scope.launch(Dispatchers.Main) {
                    statusMap[cur] = "Lỗi: ${e.message ?: "Không xác định"}"
                }
            } finally {
                scope.launch(Dispatchers.Main) {
                    runningAccount.value = null
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
