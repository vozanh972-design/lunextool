package com.cayxu.app.automation.instagram

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton quản lý tác vụ chạy ngầm đa luồng (multi-account concurrent) Instagram cho XSMM.
 * Mỗi tài khoản được chạy trên một Coroutine độc lập trong Global Scope (Dispatchers.IO + SupervisorJob).
 */
object XsmmInstagramManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = ConcurrentHashMap<String, Job>()

    // Trạng thái quan sát trực tiếp bởi Jetpack Compose
    val runningAccounts = mutableStateListOf<String>()
    val statusMap = mutableStateMapOf<String, String>()
    val successCountMap = mutableStateMapOf<String, Int>()
    val errorCountMap = mutableStateMapOf<String, Int>()
    val lastErrorDetail = mutableStateMapOf<String, String>()

    fun isRunning(accountUsername: String): Boolean {
        val clean = accountUsername.trim().lowercase()
        return runningJobs.containsKey(clean)
    }

    fun isAnyRunning(): Boolean {
        return runningJobs.isNotEmpty()
    }

    fun start(context: Context, accountUsername: String) {
        val clean = accountUsername.trim().lowercase()
        if (clean.isBlank() || runningJobs.containsKey(clean)) {
            return
        }

        if (!runningAccounts.contains(clean)) {
            runningAccounts.add(clean)
        }
        statusMap[clean] = "Bắt đầu khởi động tác vụ..."
        successCountMap[clean] = 0
        errorCountMap[clean] = 0

        val job = scope.launch {
            try {
                XsmmInstagramTaskRunner.runSingleAccount(
                    context = context.applicationContext,
                    accountUsername = clean,
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
                    },
                    onErrorDetail = { username, detail ->
                        scope.launch(Dispatchers.Main) {
                            lastErrorDetail[clean] = detail
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                scope.launch(Dispatchers.Main) {
                    statusMap[clean] = "Đã dừng chạy"
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) {
                    statusMap[clean] = "Lỗi: ${e.message ?: "Không xác định"}"
                }
            } finally {
                runningJobs.remove(clean)
                scope.launch(Dispatchers.Main) {
                    runningAccounts.remove(clean)
                }
            }
        }

        runningJobs[clean] = job
    }

    fun startAccounts(context: Context, accountUsernames: List<String>) {
        val cleanList = accountUsernames.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        for (username in cleanList) {
            start(context, username)
        }
    }

    fun stop(accountUsername: String) {
        val clean = accountUsername.trim().lowercase()
        val job = runningJobs.remove(clean)
        job?.cancel()
        runningAccounts.remove(clean)
        statusMap[clean] = "Đã dừng chạy"
    }

    fun stopAll() {
        for ((user, job) in runningJobs) {
            job.cancel()
            statusMap[user] = "Đã dừng chạy"
        }
        runningJobs.clear()
        runningAccounts.clear()
    }
}
