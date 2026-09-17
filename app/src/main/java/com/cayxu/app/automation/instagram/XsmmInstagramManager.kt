package com.cayxu.app.automation.instagram

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
 * Singleton quản lý tác vụ chạy ngầm ĐA LUỒNG (Multi-thread Concurrent) Instagram cho XSMM:
 * - Mỗi tài khoản chạy trên 1 luồng độc lập (Coroutine riêng trong Dispatchers.IO).
 * - Tất cả các nick được chạy SONG SONG cùng lúc (Đa luồng thực sự).
 * - Từng luồng áp dụng 100% logic tương tác, bóc tách ID, GraphQL, gom 10 follow, retry... từ Python TA Tool.
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

    /**
     * Khởi chạy 1 tài khoản trên 1 luồng Coroutine độc lập
     */
    fun start(context: Context, accountUsername: String, startDelayMs: Long = 0L) {
        val clean = accountUsername.trim().lowercase()
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
                }
            }
        }

        runningJobs[clean] = job
    }

    /**
     * Chạy danh sách tài khoản theo đúng 100% logic vòng lặp Python:
     * - Nếu các nick không có proxy riêng (hoặc chung IP): Chạy tuần tự xoay vòng (Nick 1 -> Nick 2 -> ... -> lặp lại vô tận)
     *   để tránh bị Instagram đánh mã 429 (Too Many Requests).
     * - Nếu từng nick có proxy riêng biệt: Chạy đa luồng song song trên các IP độc lập.
     */
    fun startAccounts(context: Context, accountUsernames: List<String>) {
        val cleanList = accountUsernames.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        if (cleanList.isEmpty()) return

        // Kiểm tra xem các tài khoản có proxy riêng biệt không
        val accounts = cleanList.mapNotNull { InstagramAccountsStore.getAccount(context, it) }
        val hasDistinctProxies = accounts.size > 1 && accounts.all { it.proxy.isNotBlank() } &&
                accounts.map { it.proxy.trim() }.distinct().size == accounts.size

        if (hasDistinctProxies) {
            // Có proxy riêng từng nick -> Chạy đa luồng song song
            for ((index, username) in cleanList.withIndex()) {
                start(context, username, startDelayMs = index * 2000L)
            }
        } else {
            // Không proxy hoặc chung IP -> Chạy xoay vòng tuần tự y hệt vòng lặp `while True: for ...` của Python
            val rotationJobKey = "__rotation_loop__"
            val existingJob = runningJobs[rotationJobKey]
            if (existingJob != null && existingJob.isActive) return

            cleanList.forEach { user ->
                if (!runningAccounts.contains(user)) runningAccounts.add(user)
                statusMap[user] = "Chờ tới lượt..."
                successCountMap[user] = 0
                errorCountMap[user] = 0
            }

            val job = scope.launch {
                try {
                    while (kotlinx.coroutines.isActive) {
                        for (username in cleanList) {
                            if (!kotlinx.coroutines.isActive) break
                            val currentAcc = InstagramAccountsStore.getAccount(context, username)
                            if (currentAcc == null || !currentAcc.isLive || currentAcc.cookie.isBlank()) {
                                scope.launch(Dispatchers.Main) {
                                    statusMap[username] = "Cookie Die / Bỏ qua"
                                }
                                continue
                            }

                            scope.launch(Dispatchers.Main) {
                                statusMap[username] = "Đang chạy..."
                            }

                            XsmmInstagramTaskRunner.runSingleAccount(
                                context = context.applicationContext,
                                accountUsername = username,
                                onStatusUpdate = { status ->
                                    scope.launch(Dispatchers.Main) {
                                        statusMap[username] = status
                                    }
                                },
                                onProgressUpdate = { status, success, errors ->
                                    scope.launch(Dispatchers.Main) {
                                        statusMap[username] = status
                                        successCountMap[username] = success
                                        errorCountMap[username] = errors
                                    }
                                },
                                onErrorDetail = { _, detail ->
                                    scope.launch(Dispatchers.Main) {
                                        lastErrorDetail[username] = detail
                                    }
                                }
                            )

                            scope.launch(Dispatchers.Main) {
                                statusMap[username] = "Đã đổi nick"
                            }
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    cleanList.forEach { user ->
                        scope.launch(Dispatchers.Main) { statusMap[user] = "Đã dừng chạy" }
                    }
                } catch (e: Exception) {
                    cleanList.forEach { user ->
                        scope.launch(Dispatchers.Main) { statusMap[user] = "Lỗi: ${e.message}" }
                    }
                } finally {
                    runningJobs.remove(rotationJobKey)
                    scope.launch(Dispatchers.Main) {
                        runningAccounts.clear()
                    }
                }
            }
            runningJobs[rotationJobKey] = job
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
        for ((_, job) in runningJobs) {
            job.cancel()
        }
        runningJobs.clear()
        for (user in runningAccounts) {
            statusMap[user] = "Đã dừng chạy"
        }
        runningAccounts.clear()
    }
}
