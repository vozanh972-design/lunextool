package com.cayxu.app.automation.instagram

import android.content.Context
import com.cayxu.app.data.local.InstagramAccount
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.data.repository.*
import com.cayxu.app.instagram.InstagramApiClient
import com.cayxu.app.ui.overlay.xsmm.XsmmJobStatusBridge
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Runner tự động chạy nhiệm vụ Instagram trên XSMM:
 * 1. Thêm tài khoản Instagram vào XSMM (nếu chưa có).
 * 2. Đặt tài khoản làm nick chạy mặc định (set active).
 * 3. Tự động tìm nhiệm vụ: Ưu tiên Follow, nếu hết job Follow tự chuyển sang Like.
 * 4. Tương tác trực tiếp bằng InstagramApiClient (Follow / Like qua Cookie).
 * 5. Đếm ngược từng giây an toàn.
 * 6. Gửi xác nhận hoàn thành (tasks2/complete), thống kê số job thành công / lỗi và cộng điểm.
 */
object XsmmInstagramTaskRunner {

    data class RunResult(
        val totalCompleted: Int,
        val totalErrors: Int,
        val totalEarnedPoints: Int,
        val message: String
    )

    /**
     * Chạy nhiệm vụ cho 1 danh sách tài khoản Instagram
     */
    suspend fun run(
        context: Context,
        accountUsernames: List<String>,
        onProgressUpdate: ((status: String, successCount: Int, errorCount: Int) -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        onErrorDetail: ((username: String, detail: String) -> Unit)? = null
    ): RunResult {
        val token = XsmmAccountStore.getToken(context)
        if (token.isNullOrBlank()) {
            val msg = "Chưa đăng nhập XSMM"
            onStatusUpdate?.invoke(msg)
            onProgressUpdate?.invoke(msg, 0, 1)
            XsmmJobStatusBridge.update(msg)
            return RunResult(0, 1, 0, msg)
        }

        val config = XsmmRunConfigStore.get(context, "instagram")
        var totalCompleted = 0
        var totalErrors = 0
        var totalEarnedPoints = 0

        fun notify(status: String) {
            onStatusUpdate?.invoke(status)
            onProgressUpdate?.invoke(status, totalCompleted, totalErrors)
            XsmmJobStatusBridge.update(status)
        }

        fun reportError(user: String, detail: String) {
            onErrorDetail?.invoke(user, detail)
        }

        val targetAccounts = accountUsernames.mapNotNull { name ->
            InstagramAccountsStore.getAccount(context, name)
        }

        if (targetAccounts.isEmpty()) {
            val msg = "Không tìm thấy cookie của tài khoản Instagram đã chọn"
            notify(msg)
            return RunResult(0, 1, 0, msg)
        }

        notify("Bắt đầu chuẩn bị chạy ${targetAccounts.size} tài khoản Instagram...")

        // Lấy danh sách tài khoản hiện có trên XSMM để tránh thêm trùng
        val xsmmAccountsRes = XsmmAccountsRepository.getAccounts(token, accountType = "instagram")
        val xsmmAccounts = (xsmmAccountsRes as? XsmmAccountsResult.Success)?.accounts.orEmpty().toMutableList()

        for (account in targetAccounts) {
            if (!coroutineContext.isActive) break

            val cleanUsername = account.username.trim().removePrefix("@").lowercase()
            notify("[$cleanUsername] Đang kiểm tra liên kết trên XSMM...")

            // 1. Kiểm tra tài khoản đã có trên XSMM chưa
            var xsmmAcc = xsmmAccounts.firstOrNull {
                it.linkAccount.substringAfterLast("/").trim().lowercase() == cleanUsername ||
                it.name.trim().lowercase() == cleanUsername ||
                it.linkAccount.contains(cleanUsername, ignoreCase = true)
            }

            if (xsmmAcc == null) {
                notify("[$cleanUsername] Đang thêm tài khoản vào XSMM...")
                when (val addRes = XsmmAccountsRepository.addInstagramAccount(token, cleanUsername, setActive = true)) {
                    is XsmmAddAccountResult.Success -> {
                        xsmmAcc = addRes.account
                        xsmmAccounts.add(addRes.account)
                        notify("[$cleanUsername] Đã thêm vào XSMM thành công")
                    }
                    is XsmmAddAccountResult.Error -> {
                        totalErrors++
                        notify("[$cleanUsername] Thêm vào XSMM thất bại: ${addRes.message}")
                    }
                }
            }

            val xsmmUid = xsmmAcc?.accountId?.ifBlank { xsmmAcc.id } ?: cleanUsername

            // 2. Đặt làm nick chạy mặc định
            if (xsmmAcc != null && xsmmAcc.id.isNotBlank()) {
                XsmmAccountsRepository.setActiveAccount(token, xsmmAcc.id)
            }

            val apiClient = InstagramApiClient(cookie = account.cookie)

            // 3. Vòng lặp lấy nhiệm vụ: Tự động Follow -> hết thì chuyển Like
            val taskTypesToTry = listOf("instagram_follow", "instagram_like")
            var consecutiveNoTasks = 0
            val maxNoTaskRetries = config.stopAfterNoTaskCount.coerceAtLeast(3)
            val pendingFollowTaskIds = mutableListOf<String>()

            while (coroutineContext.isActive) {
                var foundAnyTasks = false

                for (taskType in taskTypesToTry) {
                    if (!coroutineContext.isActive) break

                    val isFollow = taskType.contains("follow", ignoreCase = true)
                    val readableType = if (isFollow) "Theo dõi (Follow)" else "Thích (Like)"
                    notify("[$cleanUsername] Đang tìm job $readableType...")
                    val tasksRes = XsmmTasksRepository.getTasks2(token, taskType, xsmmUid)

                    when (tasksRes) {
                        is XsmmTasks2Result.Error -> {
                            totalErrors++
                            notify("[$cleanUsername] Lỗi lấy NV ($readableType): ${tasksRes.message}")
                        }
                        is XsmmTasks2Result.Success -> {
                            val tasks = tasksRes.tasks
                            if (tasks.isNotEmpty()) {
                                foundAnyTasks = true
                                consecutiveNoTasks = 0
                                notify("[$cleanUsername] Nhận được ${tasks.size} nhiệm vụ $readableType")

                                for (task in tasks) {
                                    if (!coroutineContext.isActive) break

                                    val target = task.idorlink.ifBlank { task.targetUrl }.ifBlank { task.id }
                                    notify("[$cleanUsername] Đang làm $readableType: $target")

                                    var actionSuccess = false
                                    var lastActionError: String? = null
                                    try {
                                        if (isFollow || task.type.contains("follow", ignoreCase = true)) {
                                            val followTarget = task.idorlink.ifBlank { task.targetUrl }
                                            actionSuccess = apiClient.followTarget(followTarget, fbDtsg = account.fbDtsg, lsd = account.lsd)
                                        } else {
                                            val likeTarget = task.idorlink.ifBlank { task.targetUrl }
                                            actionSuccess = apiClient.likeTarget(likeTarget, fbDtsg = account.fbDtsg)
                                        }
                                        if (!actionSuccess) {
                                            lastActionError = "Instagram trả về thất bại (Không thể hoàn thành hành động)"
                                            notify("[$cleanUsername] Instagram không phản hồi thành công")
                                        }
                                    } catch (e: Exception) {
                                        actionSuccess = false
                                        lastActionError = e.message ?: "Lỗi ngoại lệ khi gửi request Instagram"
                                        notify("[$cleanUsername] Lỗi Instagram: ${e.message}")
                                    }

                                    // Đếm ngược từng giây an toàn sau khi tương tác
                                    val delaySec = config.doTaskDurationSeconds.coerceAtLeast(3)
                                    for (s in delaySec downTo 1) {
                                        if (!coroutineContext.isActive) break
                                        notify("[$cleanUsername] Chờ ${s}s an toàn...")
                                        delay(1000L)
                                    }
                                    if (!coroutineContext.isActive) break

                                    // 4. Xử lý nhận xu theo cơ chế 12 Follow / lần
                                    if (isFollow) {
                                        if (actionSuccess) {
                                             pendingFollowTaskIds.add(task.id)
                                             totalCompleted++
                                             notify("[$cleanUsername] Đã Follow (${pendingFollowTaskIds.size}/12) - Đủ 12 job sẽ nhận xu")
                                        } else {
                                             totalErrors++
                                             val detailStr = lastActionError ?: "Lỗi Follow: Instagram từ chối / không thể Follow đối tượng $target"
                                             reportError(cleanUsername, detailStr)
                                             notify("[$cleanUsername] Lỗi Follow: $detailStr")
                                        }

                                        // Khi đủ 12 job Follow -> Gửi nhận xu 1 lần
                                        if (pendingFollowTaskIds.size >= 12) {
                                            val batchToClaim = pendingFollowTaskIds.take(12)
                                            notify("[$cleanUsername] Đã tích lũy đủ 12 job Follow, đang gửi nhận xu...")
                                            val compRes = XsmmTasksRepository.completeTasks2(
                                                rawToken = token,
                                                type = "instagram_follow",
                                                taskIds = batchToClaim,
                                                uid = xsmmUid
                                            )

                                            if (compRes.success || compRes.points > 0) {
                                                val earned = if (compRes.points > 0) compRes.points else 0
                                                totalEarnedPoints += earned

                                                if (earned > 0) {
                                                    val currentPts = XsmmAccountStore.getPoints(context) + earned
                                                    XsmmAccountStore.updatePoints(context, currentPts)
                                                    XsmmSession.points.value = currentPts
                                                }

                                                pendingFollowTaskIds.removeAll(batchToClaim)
                                                val successMsg = if (compRes.message.isNotBlank()) compRes.message else "Nhận xu thành công +$earned điểm (12 job Follow)"
                                                notify("[$cleanUsername] $successMsg")
                                            } else {
                                                totalErrors++
                                                val errMsg = if (compRes.message.isNotBlank()) compRes.message else "Lỗi nhận xu 12 job"
                                                notify("[$cleanUsername] Thất bại: $errMsg")
                                                pendingFollowTaskIds.removeAll(batchToClaim)
                                            }

                                            val waitAfter = if (compRes.countdown > 0) compRes.countdown else config.fetchTaskIntervalSeconds.coerceAtLeast(2)
                                            for (s in waitAfter downTo 1) {
                                                if (!coroutineContext.isActive) break
                                                notify("[$cleanUsername] Chờ ${s}s lấy nhiệm vụ tiếp theo...")
                                                delay(1000L)
                                            }
                                        } else {
                                            // Chưa đủ 12 job -> đếm ngược giãn cách lấy job tiếp theo
                                            val waitAfter = config.fetchTaskIntervalSeconds.coerceAtLeast(2)
                                            for (s in waitAfter downTo 1) {
                                                if (!coroutineContext.isActive) break
                                                notify("[$cleanUsername] Đã xong ${pendingFollowTaskIds.size}/12 Follow. Chờ ${s}s tiếp tục...")
                                                delay(1000L)
                                            }
                                        }
                                    } else {
                                        // Với Like: Gửi hoàn thành nhận xu theo từng job
                                        if (actionSuccess) {
                                            notify("[$cleanUsername] Đang gửi xác nhận hoàn thành Like...")
                                            val compRes = XsmmTasksRepository.completeTasks2(
                                                rawToken = token,
                                                type = task.type.ifBlank { taskType },
                                                taskIds = listOf(task.id),
                                                uid = xsmmUid
                                            )

                                            if (compRes.success || compRes.points > 0) {
                                                totalCompleted++
                                                val earned = if (compRes.points > 0) compRes.points else 0
                                                totalEarnedPoints += earned

                                                if (earned > 0) {
                                                    val currentPts = XsmmAccountStore.getPoints(context) + earned
                                                    XsmmAccountStore.updatePoints(context, currentPts)
                                                    XsmmSession.points.value = currentPts
                                                }

                                                val successMsg = if (compRes.message.isNotBlank()) compRes.message else "Hoàn thành +$earned điểm"
                                                notify("[$cleanUsername] $successMsg")
                                            } else {
                                                totalErrors++
                                                val errMsg = if (compRes.message.isNotBlank()) compRes.message else "Không được duyệt"
                                                notify("[$cleanUsername] Thất bại: $errMsg")
                                            }

                                            val waitAfter = if (compRes.countdown > 0) compRes.countdown else config.fetchTaskIntervalSeconds.coerceAtLeast(2)
                                            for (s in waitAfter downTo 1) {
                                                if (!coroutineContext.isActive) break
                                                notify("[$cleanUsername] Chờ ${s}s lấy nhiệm vụ tiếp theo...")
                                                delay(1000L)
                                            }
                                        } else {
                                            totalErrors++
                                        }
                                    }

                                    if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                                        notify("Đã hoàn thành mục tiêu $totalCompleted nhiệm vụ!")
                                        return RunResult(totalCompleted, totalErrors, totalEarnedPoints, "Hoàn thành mục tiêu $totalCompleted nhiệm vụ")
                                    }
                                }
                            }
                        }
                    }
                }

                if (!foundAnyTasks) {
                    consecutiveNoTasks++
                    if (consecutiveNoTasks >= maxNoTaskRetries) {
                        notify("[$cleanUsername] Đã hết nhiệm vụ sau $consecutiveNoTasks lần thử.")
                        break
                    }
                    val waitNoTask = config.fetchTaskIntervalSeconds.coerceAtLeast(3)
                    for (s in waitNoTask downTo 1) {
                        if (!coroutineContext.isActive) break
                        notify("[$cleanUsername] Hết job, tự thử lại sau ${s}s ($consecutiveNoTasks/$maxNoTaskRetries)...")
                        delay(1000L)
                    }
                }
            }
        }

        val finalMsg = "Hoàn tất: $totalCompleted thành công, $totalErrors lỗi (+$totalEarnedPoints điểm)"
        notify(finalMsg)
        return RunResult(totalCompleted, totalErrors, totalEarnedPoints, finalMsg)
    }
}

