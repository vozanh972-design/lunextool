package com.cayxu.app.automation.facebook

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton quản lý tác vụ chạy ngầm ĐA LUỒNG (Multi-thread) Facebook cho XSMM:
 * - Hoàn toàn độc lập, tách biệt 100% với TikTok và Instagram.
 * - Mỗi tài khoản chạy trên 1 Coroutine riêng biệt trong Dispatchers.IO.
 * - Theo dõi trạng thái, số job thành công/thất bại, lỗi chi tiết.
 */
object XsmmFacebookManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = ConcurrentHashMap<String, Job>()

    // Trạng thái quan sát trực tiếp bởi Jetpack Compose
    val runningAccounts = mutableStateListOf<String>()
    val statusMap = mutableStateMapOf<String, String>()
    val successCountMap = mutableStateMapOf<String, Int>()
    val errorCountMap = mutableStateMapOf<String, Int>()
    val lastErrorDetail = mutableStateMapOf<String, String>()

    fun isRunning(accountUid: String): Boolean {
        val clean = accountUid.trim().lowercase()
        return runningJobs.containsKey(clean)
    }

    fun isAnyRunning(): Boolean {
        return runningJobs.isNotEmpty()
    }

    fun start(context: Context, accountUid: String, startDelayMs: Long = 0L) {
        val clean = accountUid.trim().lowercase()
        if (clean.isBlank() || runningJobs.containsKey(clean)) {
            return
        }

        if (!runningAccounts.contains(clean)) {
            runningAccounts.add(clean)
        }
        statusMap[clean] = if (startDelayMs > 0) "Chờ chạy (${startDelayMs / 1000}s)..." else "Bắt đầu chạy..."
        successCountMap[clean] = 0
        errorCountMap[clean] = 0

        val job = scope.launch {
            if (startDelayMs > 0) {
                val totalSec = (startDelayMs / 1000).toInt()
                for (s in totalSec downTo 1) {
                    scope.launch(Dispatchers.Main) {
                        statusMap[clean] = "Chờ chạy (${s}s)..."
                    }
                    delay(1000L)
                }
            }
            try {
                XsmmFacebookTaskRunner.runSingleAccount(
                    context = context.applicationContext,
                    accountUid = clean,
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
                    onErrorDetail = { _, detail ->
                        scope.launch(Dispatchers.Main) {
                            lastErrorDetail[clean] = detail
                        }
                    }
                )
            } catch (_: kotlinx.coroutines.CancellationException) {
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
                    if (statusMap[clean]?.startsWith("Đang") == true || statusMap[clean]?.startsWith("Lấy") == true) {
                        statusMap[clean] = "Hoàn thành phiên chạy"
                    }
                }
            }
        }
        runningJobs[clean] = job
    }

    fun startAccounts(context: Context, accountUids: List<String>, staggerDelayMs: Long = 2000L) {
        val unique = accountUids.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        var accumulatedDelay = 0L
        for (uid in unique) {
            if (!isRunning(uid)) {
                start(context, uid, startDelayMs = accumulatedDelay)
                accumulatedDelay += staggerDelayMs
            }
        }
    }

    fun stop(accountUid: String) {
        val clean = accountUid.trim().lowercase()
        val job = runningJobs.remove(clean)
        job?.cancel()
        runningAccounts.remove(clean)
        statusMap[clean] = "Đã dừng"
    }

    fun stopAll() {
        for ((_, job) in runningJobs) {
            job.cancel()
        }
        runningJobs.clear()
        runningAccounts.clear()
        for (k in statusMap.keys) {
            statusMap[k] = "Đã dừng tất cả"
        }
    }
}
