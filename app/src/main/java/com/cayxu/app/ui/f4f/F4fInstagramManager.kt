package com.cayxu.app.ui.f4f

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
 * Singleton quản lý luồng chạy ngầm Instagram đa tài khoản độc lập cho Follow4Follow (F4F)
 */
object F4fInstagramManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = ConcurrentHashMap<String, Job>()

    val runningAccounts = mutableStateListOf<String>()
    val statusMap = mutableStateMapOf<String, String>()
    val successCountMap = mutableStateMapOf<String, Int>()
    val errorCountMap = mutableStateMapOf<String, Int>()
    val lastErrorDetail = mutableStateMapOf<String, String>()

    fun isRunning(accountUsername: String): Boolean {
        val clean = accountUsername.trim().lowercase().removePrefix("@")
        return runningJobs.containsKey(clean) || runningAccounts.contains(clean)
    }

    fun isAnyRunning(): Boolean {
        return runningJobs.isNotEmpty() || runningAccounts.isNotEmpty()
    }

    fun start(context: Context, accountUsername: String) {
        val clean = accountUsername.trim().lowercase().removePrefix("@")
        if (clean.isBlank() || runningJobs.containsKey(clean)) return

        if (!runningAccounts.contains(clean)) {
            runningAccounts.add(clean)
        }
        statusMap[clean] = "Bắt đầu chạy..."
        successCountMap[clean] = 0
        errorCountMap[clean] = 0

        val job = scope.launch {
            try {
                F4fInstagramTaskRunner.runSingleAccount(
                    context = context.applicationContext,
                    accountUsername = clean,
                    onStatusUpdate = { status ->
                        scope.launch(Dispatchers.Main) {
                            if (runningAccounts.contains(clean)) {
                                statusMap[clean] = status
                            }
                        }
                    },
                    onProgressUpdate = { status, success, errors ->
                        scope.launch(Dispatchers.Main) {
                            if (runningAccounts.contains(clean)) {
                                statusMap[clean] = status
                                successCountMap[clean] = success
                                errorCountMap[clean] = errors
                            }
                        }
                    },
                    onErrorDetail = { user, detail ->
                        scope.launch(Dispatchers.Main) {
                            val u = user.trim().lowercase().removePrefix("@")
                            lastErrorDetail[u] = detail
                        }
                    }
                )
            } finally {
                scope.launch(Dispatchers.Main) {
                    runningAccounts.remove(clean)
                    runningJobs.remove(clean)
                }
            }
        }
        runningJobs[clean] = job
    }

    fun stop(accountUsername: String) {
        val clean = accountUsername.trim().lowercase().removePrefix("@")
        runningJobs.remove(clean)?.cancel()
        runningAccounts.remove(clean)
        statusMap[clean] = "Đã tạm dừng"
    }

    fun stopAll() {
        runningJobs.forEach { (clean, job) ->
            job.cancel()
            statusMap[clean] = "Đã tạm dừng"
        }
        runningJobs.clear()
        runningAccounts.clear()
    }
}
