package com.cayxu.app.automation.instagram

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.cayxu.app.data.local.InstagramAccountsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton quản lý tác vụ chạy ngầm Instagram cho XSMM theo 100% mô hình Python TA Tool:
 * - Hỗ trợ chạy đơn lẻ từng tài khoản.
 * - Hỗ trợ chạy xoay vòng liên tục danh sách tài khoản (Nick 1 -> Nick 2 -> ... -> lặp lại).
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
        return runningAccounts.contains(clean) || runningJobs.containsKey(clean)
    }

    fun isAnyRunning(): Boolean {
        return runningJobs.isNotEmpty() || runningAccounts.isNotEmpty()
    }

    fun start(context: Context, accountUsername: String, startDelayMs: Long = 0L) {
        val clean = accountUsername.trim().lowercase()
        if (clean.isBlank() || runningJobs.containsKey(clean)) {
            return
        }

        if (!runningAccounts.contains(clean)) {
            runningAccounts.add(clean)
        }
        statusMap[clean] = if (startDelayMs > 0) "Chờ khởi động (${startDelayMs / 1000}s)..." else "Bắt đầu khởi động tác vụ..."
        successCountMap[clean] = 0
        errorCountMap[clean] = 0

        val job = scope.launch {
            if (startDelayMs > 0) {
                val totalSec = (startDelayMs / 1000).toInt()
                for (s in totalSec downTo 1) {
                    scope.launch(Dispatchers.Main) {
                        statusMap[clean] = "Chờ khởi động (${s}s)..."
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
     * Chạy xoay vòng danh sách tài khoản theo đúng 100% vòng lặp của Python TA Tool
     * (Nick 1 làm xong mốc job / gặp lỗi > 4 lần -> chuyển sang Nick 2 -> ... -> lặp lại vô tận)
     */
    fun startAccounts(context: Context, accountUsernames: List<String>) {
        val cleanList = accountUsernames.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        if (cleanList.isEmpty()) return

        if (cleanList.size == 1) {
            start(context, cleanList.first())
            return
        }

        stopAll()
        val rotationKey = "__rotation__"

        val job = scope.launch {
            scope.launch(Dispatchers.Main) {
                cleanList.forEach { user ->
                    if (!runningAccounts.contains(user)) {
                        runningAccounts.add(user)
                    }
                    statusMap[user] = "Trong hàng đợi xoay vòng..."
                    successCountMap[user] = 0
                    errorCountMap[user] = 0
                }
            }

            try {
                while (isActive) {
                    var anyLive = false
                    for (user in cleanList) {
                        if (!isActive) break

                        val acc = InstagramAccountsStore.getAccount(context, user)
                        if (acc == null || !acc.isLive) {
                            scope.launch(Dispatchers.Main) {
                                statusMap[user] = "Bỏ qua (Cookie Die / Không tồn tại)"
                            }
                            continue
                        }
                        anyLive = true

                        scope.launch(Dispatchers.Main) {
                            statusMap[user] = "Đang chạy phiên làm việc..."
                        }

                        val result = XsmmInstagramTaskRunner.runSingleAccount(
                            context = context.applicationContext,
                            accountUsername = user,
                            onStatusUpdate = { status ->
                                scope.launch(Dispatchers.Main) {
                                    statusMap[user] = status
                                }
                            },
                            onProgressUpdate = { status, success, errors ->
                                scope.launch(Dispatchers.Main) {
                                    statusMap[user] = status
                                    successCountMap[user] = (successCountMap[user] ?: 0) + success
                                    errorCountMap[user] = (errorCountMap[user] ?: 0) + errors
                                }
                            },
                            onErrorDetail = { _, detail ->
                                scope.launch(Dispatchers.Main) {
                                    lastErrorDetail[user] = detail
                                }
                            }
                        )

                        scope.launch(Dispatchers.Main) {
                            statusMap[user] = "Nghỉ chuyển nick (${result.totalCompleted} job xong)"
                        }

                        // Giãn cách ngắn khi đổi nick
                        delay(2000L)
                    }

                    if (!anyLive) {
                        scope.launch(Dispatchers.Main) {
                            cleanList.forEach { u -> statusMap[u] = "Tất cả cookie đều Die" }
                        }
                        break
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                scope.launch(Dispatchers.Main) {
                    cleanList.forEach { u -> statusMap[u] = "Đã dừng chạy" }
                }
            } finally {
                runningJobs.remove(rotationKey)
                scope.launch(Dispatchers.Main) {
                    runningAccounts.clear()
                }
            }
        }

        runningJobs[rotationKey] = job
    }

    fun stop(accountUsername: String) {
        val clean = accountUsername.trim().lowercase()
        val job = runningJobs.remove(clean)
        job?.cancel()
        runningAccounts.remove(clean)
        statusMap[clean] = "Đã dừng chạy"

        // Nếu đang chạy rotation mà user bấm dừng thì dừng rotation
        val rotationJob = runningJobs.remove("__rotation__")
        if (rotationJob != null) {
            rotationJob.cancel()
            runningAccounts.clear()
        }
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
