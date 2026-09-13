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
 * 3. Lấy danh sách nhiệm vụ (tasks2) cho instagram_follow và instagram_like.
 * 4. Tương tác trực tiếp bằng InstagramApiClient (Follow / Like qua Cookie).
 * 5. Delay an toàn.
 * 6. Gửi xác nhận hoàn thành (tasks2/complete) và cộng điểm.
 */
object XsmmInstagramTaskRunner {

    data class RunResult(
        val totalCompleted: Int,
        val totalEarnedPoints: Int,
        val message: String
    )

    /**
     * Chạy nhiệm vụ cho 1 danh sách tài khoản Instagram
     */
    suspend fun run(
        context: Context,
        accountUsernames: List<String>,
        onStatusUpdate: ((String) -> Unit)? = null
    ): RunResult {
        val token = XsmmAccountStore.getToken(context)
        if (token.isNullOrBlank()) {
            val msg = "Chưa đăng nhập XSMM"
            onStatusUpdate?.invoke(msg)
            XsmmJobStatusBridge.update(msg)
            return RunResult(0, 0, msg)
        }

        val config = XsmmRunConfigStore.get(context)
        var totalCompleted = 0
        var totalEarnedPoints = 0

        fun notify(status: String) {
            onStatusUpdate?.invoke(status)
            XsmmJobStatusBridge.update(status)
        }

        val targetAccounts = accountUsernames.mapNotNull { name ->
            InstagramAccountsStore.getAccount(context, name)
        }

        if (targetAccounts.isEmpty()) {
            val msg = "Không tìm thấy cookie của tài khoản Instagram đã chọn"
            notify(msg)
            return RunResult(0, 0, msg)
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

            // 3. Vòng lặp lấy nhiệm vụ
            val taskTypes = if (config.taskType.contains("follow", ignoreCase = true)) {
                listOf("instagram_follow", "instagram_like")
            } else if (config.taskType.contains("like", ignoreCase = true)) {
                listOf("instagram_like", "instagram_follow")
            } else {
                listOf("instagram_follow", "instagram_like")
            }

            var noTaskCount = 0

            for (taskType in taskTypes) {
                if (!coroutineContext.isActive) break

                notify("[$cleanUsername] Đang lấy nhiệm vụ $taskType...")
                val tasksRes = XsmmTasksRepository.getTasks2(token, taskType, xsmmUid)

                when (tasksRes) {
                    is XsmmTasks2Result.Error -> {
                        notify("[$cleanUsername] Không lấy được NV ($taskType): ${tasksRes.message}")
                    }
                    is XsmmTasks2Result.Success -> {
                        val tasks = tasksRes.tasks
                        if (tasks.isEmpty()) {
                            noTaskCount++
                            notify("[$cleanUsername] Tạm hết nhiệm vụ $taskType")
                        } else {
                            notify("[$cleanUsername] Tìm thấy ${tasks.size} nhiệm vụ $taskType")

                            for (task in tasks) {
                                if (!coroutineContext.isActive) break

                                val target = task.idorlink.ifBlank { task.targetUrl }.ifBlank { task.id }
                                notify("[$cleanUsername] Đang thực hiện $taskType: $target")

                                var actionSuccess = false
                                try {
                                    if (task.type.contains("follow", ignoreCase = true)) {
                                        val followTarget = task.idorlink.ifBlank { task.targetUrl }
                                        actionSuccess = apiClient.followTarget(followTarget)
                                    } else if (task.type.contains("like", ignoreCase = true)) {
                                        val likeTarget = task.idorlink.ifBlank { task.targetUrl }
                                        actionSuccess = apiClient.likeTarget(likeTarget, fbDtsg = account.fbDtsg)
                                    }
                                } catch (e: Exception) {
                                    notify("[$cleanUsername] Lỗi tương tác Instagram: ${e.message}")
                                }

                                // Delay sau khi làm nhiệm vụ
                                val delaySec = config.doTaskDurationSeconds.coerceAtLeast(3)
                                notify("[$cleanUsername] Chờ ${delaySec}s để xác nhận hoàn thành...")
                                delay(delaySec * 1000L)

                                // 4. Gửi xác nhận hoàn thành (tasks2/complete)
                                notify("[$cleanUsername] Đang gửi xác nhận hoàn thành nhiệm vụ...")
                                val compRes = XsmmTasksRepository.completeTasks2(
                                    rawToken = token,
                                    type = task.type.ifBlank { taskType },
                                    taskIds = listOf(task.id),
                                    uid = xsmmUid
                                )

                                totalCompleted++
                                val earned = if (compRes.points > 0) compRes.points else 0
                                totalEarnedPoints += earned

                                if (earned > 0) {
                                    val currentPts = XsmmAccountStore.getPoints(context) + earned
                                    XsmmAccountStore.updatePoints(context, currentPts)
                                    XsmmSession.points.value = currentPts
                                }

                                val resultMsg = if (compRes.success) {
                                    if (compRes.message.isNotBlank()) compRes.message else "Thành công +$earned điểm!"
                                } else {
                                    if (compRes.message.isNotBlank()) compRes.message else "Đã gửi hoàn thành"
                                }
                                notify("[$cleanUsername] $resultMsg (Tổng: $totalCompleted NV, +$totalEarnedPoints điểm)")

                                if (compRes.countdown > 0) {
                                    delay(compRes.countdown * 1000L)
                                } else {
                                    delay(config.fetchTaskIntervalSeconds * 1000L)
                                }

                                if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                                    notify("Đã hoàn thành mục tiêu $totalCompleted nhiệm vụ!")
                                    return RunResult(totalCompleted, totalEarnedPoints, "Hoàn thành mục tiêu $totalCompleted nhiệm vụ")
                                }
                            }
                        }
                    }
                }
            }
        }

        val finalMsg = "Hoàn tất phiên chạy: $totalCompleted nhiệm vụ thành công (+$totalEarnedPoints điểm)"
        notify(finalMsg)
        return RunResult(totalCompleted, totalEarnedPoints, finalMsg)
    }
}
