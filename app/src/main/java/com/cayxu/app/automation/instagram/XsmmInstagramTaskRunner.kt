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
     * Chạy nhiệm vụ cho từng tài khoản Instagram độc lập (hỗ trợ chạy đa luồng đồng thời)
     */
    suspend fun runSingleAccount(
        context: Context,
        accountUsername: String,
        onProgressUpdate: ((status: String, successCount: Int, errorCount: Int) -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        onErrorDetail: ((username: String, detail: String) -> Unit)? = null
    ): RunResult {
        val cleanUsername = accountUsername.trim().removePrefix("@").lowercase()
        val token = XsmmAccountStore.getToken(context)
        if (token.isNullOrBlank()) {
            val msg = "Chưa đăng nhập XSMM"
            onStatusUpdate?.invoke(msg)
            onProgressUpdate?.invoke(msg, 0, 1)
            return RunResult(0, 1, 0, msg)
        }

        val account = InstagramAccountsStore.getAccount(context, cleanUsername)
        if (account == null || account.cookie.isBlank()) {
            val msg = "[$cleanUsername] Không tìm thấy cookie tài khoản"
            onStatusUpdate?.invoke(msg)
            onProgressUpdate?.invoke(msg, 0, 1)
            return RunResult(0, 1, 0, msg)
        }

        val config = XsmmRunConfigStore.get(context, "instagram")
        var totalCompleted = 0
        var totalErrors = 0
        var totalEarnedPoints = 0

        fun notify(status: String) {
            onStatusUpdate?.invoke(status)
            onProgressUpdate?.invoke(status, totalCompleted, totalErrors)
            XsmmJobStatusBridge.update("[$cleanUsername] $status")
        }

        fun reportError(user: String, detail: String) {
            onErrorDetail?.invoke(user, detail)
        }

        notify("Đang kiểm tra liên kết trên XSMM...")

        // 1. Kiểm tra tài khoản đã có trên XSMM chưa
        val xsmmAccountsRes = XsmmAccountsRepository.getAccounts(token, accountType = "instagram")
        val xsmmAccounts = (xsmmAccountsRes as? XsmmAccountsResult.Success)?.accounts.orEmpty().toMutableList()

        var xsmmAcc = xsmmAccounts.firstOrNull {
            it.linkAccount.substringAfterLast("/").trim().lowercase() == cleanUsername ||
            it.name.trim().lowercase() == cleanUsername ||
            it.linkAccount.contains(cleanUsername, ignoreCase = true)
        }

        if (xsmmAcc == null) {
            notify("Đang thêm tài khoản vào XSMM...")
            when (val addRes = XsmmAccountsRepository.addInstagramAccount(token, cleanUsername, setActive = true)) {
                is XsmmAddAccountResult.Success -> {
                    xsmmAcc = addRes.account
                    notify("Đã thêm vào XSMM thành công")
                }
                is XsmmAddAccountResult.Error -> {
                    totalErrors++
                    notify("Thêm vào XSMM thất bại: ${addRes.message}")
                }
            }
        }

        val xsmmUid = xsmmAcc?.accountId?.ifBlank { xsmmAcc.id } ?: cleanUsername

        // 2. Đặt làm nick chạy mặc định
        if (xsmmAcc != null && xsmmAcc.id.isNotBlank()) {
            XsmmAccountsRepository.setActiveAccount(token, xsmmAcc.id)
        }

        val defaultUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"
        val ua = if (account.userAgent.isNotBlank()) account.userAgent else defaultUA
        val proxyConfig = InstagramApiClient.parseProxy(account.proxy)
        val apiClient = InstagramApiClient(cookie = account.cookie, userAgent = ua, proxyConfig = proxyConfig)

        // Luôn luôn fetch fresh fb_dtsg + lsd + actorId trước khi chạy (bắt buộc với đa luồng)
        var activeDtsg = ""
        var activeLsd = ""
        var activeActorId = account.userId

        notify("Đang xác thực cookie Instagram...")
        try {
            val profile = apiClient.fetchUserInfo()
            if (!profile.fbDtsg.isNullOrBlank()) {
                activeDtsg = profile.fbDtsg
            }
            if (!profile.lsd.isNullOrBlank()) {
                activeLsd = profile.lsd
            }
            if (!profile.actorId.isNullOrBlank()) {
                activeActorId = profile.actorId
            }
            // Cập nhật lại vào Store với token mới nhất
            val updatedAccount = account.copy(fbDtsg = activeDtsg, lsd = activeLsd, userId = activeActorId, isLive = true)
            InstagramAccountsStore.updateAccount(context, updatedAccount)
            notify("Cookie hợp lệ - Sẵn sàng chạy nhiệm vụ")
        } catch (e: Exception) {
            // Cookie DIE hoặc checkpoint - dừng luôn, không chạy
            val errMsg = e.message ?: "Cookie không hợp lệ hoặc đã hết hạn"
            notify("Lỗi xác thực: $errMsg")
            reportError(cleanUsername, errMsg)
            val deadAccount = account.copy(isLive = false)
            InstagramAccountsStore.updateAccount(context, deadAccount)
            return RunResult(0, 1, 0, "Dừng: $errMsg")
        }

        if (activeDtsg.isBlank() || activeLsd.isBlank()) {
            // Fallback: dùng token đã lưu nếu có
            if (account.fbDtsg.isNotBlank()) activeDtsg = account.fbDtsg
            if (account.lsd.isNotBlank()) activeLsd = account.lsd
        }

        // 3. Vòng lặp lấy nhiệm vụ: Tuỳ theo cấu hình người dùng chọn:
        // - Random (Ngẫu nhiên): Ưu tiên Follow, hết job thì chuyển Like
        // - Chỉ Follow: chỉ lấy Follow
        // - Chỉ Like: chỉ lấy Like
        val taskTypesToTry = when (config.taskType.lowercase()) {
            "instagram_follow" -> listOf("instagram_follow")
            "instagram_like" -> listOf("instagram_like")
            else -> listOf("instagram_follow", "instagram_like")
        }
        var consecutiveNoTasks = 0
        val maxNoTaskRetries = config.stopAfterNoTaskCount.coerceAtLeast(3)
        val pendingFollowTaskIds = mutableListOf<String>()
        var lastLiveCheckTime = System.currentTimeMillis()

        while (coroutineContext.isActive) {
            var foundAnyTasks = false

            // Cơ chế kiểm tra Live định kỳ 20 giây / lần: Nếu die hay lỗi báo ngay và chuyển sang DIE
            val now = System.currentTimeMillis()
            if (now - lastLiveCheckTime >= 20_000L) {
                lastLiveCheckTime = now
                val isLiveNow = apiClient.checkAccountLive()
                if (!isLiveNow) {
                    totalErrors++
                    val dieMsg = "Tài khoản đã DIE / Checkpoint / Hết phiên đăng nhập"
                    reportError(cleanUsername, dieMsg)
                    notify("Dừng tài khoản: $dieMsg")
                    val deadAccount = account.copy(isLive = false)
                    InstagramAccountsStore.updateAccount(context, deadAccount)
                    return RunResult(totalCompleted, totalErrors, totalEarnedPoints, dieMsg)
                } else {
                    val liveAccount = account.copy(isLive = true)
                    InstagramAccountsStore.updateAccount(context, liveAccount)
                }
            }

            for (taskType in taskTypesToTry) {
                if (!coroutineContext.isActive) break

                val isFollow = taskType.contains("follow", ignoreCase = true)
                val readableType = if (isFollow) "Theo dõi (Follow)" else "Thích (Like)"
                notify("Đang tìm job $readableType...")
                val tasksRes = XsmmTasksRepository.getTasks2(token, taskType, xsmmUid)

                when (tasksRes) {
                    is XsmmTasks2Result.Error -> {
                        totalErrors++
                        notify("Lỗi lấy NV ($readableType): ${tasksRes.message}")
                    }
                    is XsmmTasks2Result.Success -> {
                        val tasks = tasksRes.tasks
                        if (tasks.isNotEmpty()) {
                            foundAnyTasks = true
                            consecutiveNoTasks = 0
                            notify("Nhận được ${tasks.size} nhiệm vụ $readableType")

                            for (task in tasks) {
                                if (!coroutineContext.isActive) break

                                // Kiểm tra Live mỗi 20 giây trong suốt quá trình chạy
                                if (System.currentTimeMillis() - lastLiveCheckTime >= 20_000L) {
                                    lastLiveCheckTime = System.currentTimeMillis()
                                    val isLiveNow = apiClient.checkAccountLive()
                                    if (!isLiveNow) {
                                        totalErrors++
                                        val dieMsg = "Tài khoản đã DIE / Checkpoint / Hết phiên đăng nhập"
                                        reportError(cleanUsername, dieMsg)
                                        notify("Dừng tài khoản: $dieMsg")
                                        val deadAccount = account.copy(isLive = false)
                                        InstagramAccountsStore.updateAccount(context, deadAccount)
                                        return RunResult(totalCompleted, totalErrors, totalEarnedPoints, dieMsg)
                                    } else {
                                        val liveAccount = account.copy(isLive = true)
                                        InstagramAccountsStore.updateAccount(context, liveAccount)
                                    }
                                }

                                val target = task.idorlink.ifBlank { task.targetUrl }.ifBlank { task.id }
                                notify("Đang làm $readableType: $target")

                                var actionSuccess = false
                                var lastActionError: String? = null
                                try {
                                    if (isFollow || task.type.contains("follow", ignoreCase = true)) {
                                        val followTarget = task.idorlink.ifBlank { task.targetUrl }
                                        actionSuccess = apiClient.followTarget(followTarget, fbDtsg = activeDtsg, lsd = activeLsd, actorId = activeActorId)
                                    } else {
                                        val likeTarget = task.idorlink.ifBlank { task.targetUrl }
                                        actionSuccess = apiClient.likeTarget(likeTarget, fbDtsg = activeDtsg, lsd = activeLsd, actorId = activeActorId)
                                    }
                                    if (!actionSuccess) {
                                        lastActionError = "Instagram trả về thất bại (Không thể hoàn thành hành động)"
                                        notify("Instagram không phản hồi thành công")
                                    }
                                } catch (e: Exception) {
                                    actionSuccess = false
                                    val err = e.message ?: "Lỗi ngoại lệ khi gửi request Instagram"
                                    if (err.contains("429")) {
                                        lastActionError = "Instagram giới hạn tạm thời (HTTP 429)"
                                        notify("Instagram 429: Giới hạn tạm thời - Đang giữ Live và giãn cách an toàn...")
                                        // TUYỆT ĐỐI KHÔNG gán isLive = false khi gặp 429 vì acc vẫn Live bình thường
                                    } else if (err.contains("DIE") || err.contains("checkpoint") || err.contains("login_required") || err.contains("hết hạn")) {
                                        lastActionError = err
                                        notify("Tài khoản DIE / Checkpoint: $err")
                                        val deadAccount = account.copy(isLive = false)
                                        InstagramAccountsStore.updateAccount(context, deadAccount)
                                    } else {
                                        lastActionError = err
                                        notify("Lỗi Instagram: $err")
                                    }
                                }

                                // Đếm ngược từng giây an toàn sau khi tương tác
                                val delaySec = config.doTaskDurationSeconds.coerceAtLeast(3)
                                for (s in delaySec downTo 1) {
                                    if (!coroutineContext.isActive) break
                                    notify("Chờ ${s}s an toàn...")
                                    delay(1000L)
                                }
                                if (!coroutineContext.isActive) break

                                // Nếu tài khoản thật sự bị DIE/Checkpoint -> Dừng luồng và báo lỗi
                                if (lastActionError?.contains("DIE") == true || lastActionError?.contains("checkpoint") == true || lastActionError?.contains("login_required") == true) {
                                    totalErrors++
                                    reportError(cleanUsername, lastActionError ?: "Tài khoản DIE")
                                    notify("Dừng tài khoản: $lastActionError")
                                    return RunResult(totalCompleted, totalErrors, totalEarnedPoints, "Dừng tài khoản: $lastActionError")
                                }

                                // Nếu gặp HTTP 429 -> Giãn cách thêm 20 giây an toàn mà KHÔNG biến tài khoản thành DIE
                                if (lastActionError?.contains("429") == true) {
                                    totalErrors++
                                    reportError(cleanUsername, "Instagram giới hạn tần suất (HTTP 429) - Giãn cách chờ")
                                    for (s in 20 downTo 1) {
                                        if (!coroutineContext.isActive) break
                                        notify("Instagram 429: Chờ hạ nhiệt còn ${s}s...")
                                        delay(1000L)
                                    }
                                }

                                // 4. Xử lý nhận xu theo cơ chế 12 Follow / lần
                                if (isFollow) {
                                    if (actionSuccess) {
                                         pendingFollowTaskIds.add(task.id)
                                         totalCompleted++
                                         notify("Đã Follow (${pendingFollowTaskIds.size}/12) - Đủ 12 job sẽ nhận xu")
                                    } else {
                                         totalErrors++
                                         val detailStr = lastActionError ?: "Lỗi Follow: Instagram từ chối / không thể Follow đối tượng $target"
                                         reportError(cleanUsername, detailStr)
                                         notify("Lỗi Follow: $detailStr")
                                    }

                                    // Khi đủ 12 job Follow -> Gửi nhận xu 1 lần
                                    if (pendingFollowTaskIds.size >= 12) {
                                        val batchToClaim = pendingFollowTaskIds.take(12)
                                        notify("Đã tích lũy đủ 12 job Follow, đang gửi nhận xu...")
                                        val compRes = XsmmTasksRepository.completeTasks2(
                                            rawToken = token,
                                            type = "instagram_follow",
                                            taskIds = batchToClaim,
                                            uid = xsmmUid,
                                            cookieCheck = account.cookie
                                        )

                                        if (compRes.success || compRes.points > 0) {
                                            val earned = if (compRes.points > 0) compRes.points else 0
                                            totalEarnedPoints += earned

                                            // Cập nhật điểm thời gian thực ngay lập tức
                                            if (compRes.totalPoints != null && compRes.totalPoints > 0) {
                                                synchronized(XsmmAccountStore) {
                                                    XsmmAccountStore.updatePoints(context, compRes.totalPoints)
                                                }
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    XsmmSession.points.value = compRes.totalPoints
                                                }
                                            } else if (earned > 0) {
                                                val newPts = synchronized(XsmmAccountStore) {
                                                    val currentPts = XsmmAccountStore.getPoints(context) + earned
                                                    XsmmAccountStore.updatePoints(context, currentPts)
                                                    currentPts
                                                }
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    XsmmSession.points.value = newPts
                                                }
                                            }

                                            // Đồng bộ thêm với server để đảm bảo số dư chính xác tuyệt đối
                                            try {
                                                when (val userRes = XsmmAuthRepository.fetchUser(token)) {
                                                    is XsmmLoginResult.Success -> {
                                                        synchronized(XsmmAccountStore) {
                                                            XsmmAccountStore.updatePoints(context, userRes.info.points)
                                                        }
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            XsmmSession.points.value = userRes.info.points
                                                        }
                                                    }
                                                    is XsmmLoginResult.Error -> Unit
                                                }
                                            } catch (_: Exception) {}

                                            pendingFollowTaskIds.removeAll(batchToClaim)
                                            val successMsg = if (earned > 0) "Hoàn thành 12 Follow: +$earned xu" else (if (compRes.message.isNotBlank()) compRes.message else "Đã nhận xu (12 job Follow)")
                                            notify(successMsg)
                                        } else {
                                            totalErrors++
                                            val errMsg = if (compRes.message.isNotBlank()) compRes.message else "Lỗi nhận xu 12 job"
                                            notify("Thất bại: $errMsg")
                                            pendingFollowTaskIds.removeAll(batchToClaim)
                                        }

                                        val waitAfter = if (compRes.countdown > 0) compRes.countdown else config.fetchTaskIntervalSeconds.coerceAtLeast(2)
                                        for (s in waitAfter downTo 1) {
                                            if (!coroutineContext.isActive) break
                                            notify("Chờ ${s}s lấy nhiệm vụ tiếp theo...")
                                            delay(1000L)
                                        }
                                    } else {
                                        // Chưa đủ 12 job -> đếm ngược giãn cách lấy job tiếp theo
                                        val waitAfter = config.fetchTaskIntervalSeconds.coerceAtLeast(2)
                                        for (s in waitAfter downTo 1) {
                                            if (!coroutineContext.isActive) break
                                            notify("Đã xong ${pendingFollowTaskIds.size}/12 Follow. Chờ ${s}s tiếp tục...")
                                            delay(1000L)
                                        }
                                    }
                                } else {
                                    // Với Like: Gửi hoàn thành nhận xu theo từng job
                                    if (actionSuccess) {
                                        notify("Đang gửi xác nhận hoàn thành Like...")
                                        val compRes = XsmmTasksRepository.completeTasks2(
                                            rawToken = token,
                                            type = task.type.ifBlank { taskType },
                                            taskIds = listOf(task.id),
                                            uid = xsmmUid,
                                            cookieCheck = account.cookie
                                        )

                                        if (compRes.success || compRes.points > 0) {
                                            totalCompleted++
                                            val earned = if (compRes.points > 0) compRes.points else 0
                                            totalEarnedPoints += earned

                                            // Cập nhật điểm thời gian thực ngay lập tức
                                            if (compRes.totalPoints != null && compRes.totalPoints > 0) {
                                                synchronized(XsmmAccountStore) {
                                                    XsmmAccountStore.updatePoints(context, compRes.totalPoints)
                                                }
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    XsmmSession.points.value = compRes.totalPoints
                                                }
                                            } else if (earned > 0) {
                                                val newPts = synchronized(XsmmAccountStore) {
                                                    val currentPts = XsmmAccountStore.getPoints(context) + earned
                                                    XsmmAccountStore.updatePoints(context, currentPts)
                                                    currentPts
                                                }
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    XsmmSession.points.value = newPts
                                                }
                                            }

                                            // Đồng bộ thêm với server để đảm bảo số dư chính xác tuyệt đối
                                            try {
                                                when (val userRes = XsmmAuthRepository.fetchUser(token)) {
                                                    is XsmmLoginResult.Success -> {
                                                        synchronized(XsmmAccountStore) {
                                                            XsmmAccountStore.updatePoints(context, userRes.info.points)
                                                        }
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            XsmmSession.points.value = userRes.info.points
                                                        }
                                                    }
                                                    is XsmmLoginResult.Error -> Unit
                                                }
                                            } catch (_: Exception) {}

                                            val successMsg = if (earned > 0) "Hoàn thành Like: +$earned xu" else (if (compRes.message.isNotBlank()) compRes.message else "Hoàn thành Like")
                                            notify(successMsg)
                                        } else {
                                            totalErrors++
                                            val errMsg = if (compRes.message.isNotBlank()) compRes.message else "XSMM không duyệt (Không nhận được xu)"
                                            reportError(cleanUsername, "XSMM trả về: $errMsg")
                                            notify("Thất bại nhận xu: $errMsg")
                                        }

                                        val waitAfter = if (compRes.countdown > 0) compRes.countdown else config.fetchTaskIntervalSeconds.coerceAtLeast(2)
                                        for (s in waitAfter downTo 1) {
                                            if (!coroutineContext.isActive) break
                                            notify("Chờ ${s}s lấy nhiệm vụ tiếp theo...")
                                            delay(1000L)
                                        }
                                    } else {
                                        totalErrors++
                                        val detailStr = lastActionError ?: "Lỗi Like: Instagram từ chối / không thể Like bài viết $target"
                                        reportError(cleanUsername, detailStr)
                                        notify("Lỗi Like: $detailStr")
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
                    notify("Đã hết nhiệm vụ sau $consecutiveNoTasks lần thử.")
                    break
                }
                val waitNoTask = config.fetchTaskIntervalSeconds.coerceAtLeast(3)
                for (s in waitNoTask downTo 1) {
                    if (!coroutineContext.isActive) break
                    notify("Hết job, tự thử lại sau ${s}s ($consecutiveNoTasks/$maxNoTaskRetries)...")
                    delay(1000L)
                }
            }
        }

        val finalMsg = "Hoàn tất: $totalCompleted thành công, $totalErrors lỗi (+$totalEarnedPoints điểm)"
        notify(finalMsg)
        return RunResult(totalCompleted, totalErrors, totalEarnedPoints, finalMsg)
    }

    /**
     * Chạy nhiệm vụ cho 1 danh sách tài khoản Instagram (chạy lần lượt nếu gọi trực tiếp hàm này)
     */
    suspend fun run(
        context: Context,
        accountUsernames: List<String>,
        onProgressUpdate: ((status: String, successCount: Int, errorCount: Int) -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        onErrorDetail: ((username: String, detail: String) -> Unit)? = null
    ): RunResult {
        var totalCompleted = 0
        var totalErrors = 0
        var totalEarnedPoints = 0
        for (username in accountUsernames) {
            if (!coroutineContext.isActive) break
            val result = runSingleAccount(
                context = context,
                accountUsername = username,
                onProgressUpdate = onProgressUpdate,
                onStatusUpdate = onStatusUpdate,
                onErrorDetail = onErrorDetail
            )
            totalCompleted += result.totalCompleted
            totalErrors += result.totalErrors
            totalEarnedPoints += result.totalEarnedPoints
        }
        val finalMsg = "Hoàn tất danh sách: $totalCompleted thành công, $totalErrors lỗi (+$totalEarnedPoints điểm)"
        return RunResult(totalCompleted, totalErrors, totalEarnedPoints, finalMsg)
    }
}

