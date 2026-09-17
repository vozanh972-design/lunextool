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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

/**
 * Runner chạy tự động nhiệm vụ Instagram trên XSMM áp dụng 100% logic từ script Python TA Tool:
 * 1. Kiểm tra Cookie Live qua checkCookieIg (/api/v1/accounts/edit/web_form_data/).
 * 2. Đồng bộ Nick lên XSMM (accounts2 -> add_account nếu chưa có).
 * 3. Chế độ nhiệm vụ: Tym (Like), Follow, Comment, hoặc Ngẫu nhiên (random.choice).
 * 4. Luồng Follow: Tự động gom đủ 10 job -> Gửi duyệt nhận xu và break để refresh task mới.
 * 5. Luồng Tym & Comment: Duyệt nhận xu từng job ngay lập tức kèm cookie_check.
 * 6. Xử lý retry: True (chờ 10-15s thử lại) và countdown.
 * 7. Tự động đổi nick khi quá 4 lỗi liên tiếp (soloi > 4) hoặc đạt mốc nhiệm vụ (max_job >= doi).
 */
object XsmmInstagramTaskRunner {

    data class RunResult(
        val totalCompleted: Int,
        val totalErrors: Int,
        val totalEarnedPoints: Int,
        val message: String
    )

    private fun extractCsrfToken(cookie: String): String {
        val matcher = Pattern.compile("csrftoken=([^;]+)").matcher(cookie)
        return if (matcher.find()) matcher.group(1).orEmpty() else ""
    }

    suspend fun run(
        context: Context,
        accountUsernames: List<String>,
        onStatusUpdate: ((String) -> Unit)? = null
    ): RunResult {
        var totalCompleted = 0
        var totalErrors = 0
        var totalPoints = 0
        val messages = mutableListOf<String>()

        for (u in accountUsernames) {
            val res = runSingleAccount(
                context = context,
                accountUsername = u,
                onStatusUpdate = onStatusUpdate
            )
            totalCompleted += res.totalCompleted
            totalErrors += res.totalErrors
            totalPoints += res.totalEarnedPoints
            messages.add(res.message)
        }
        return RunResult(totalCompleted, totalErrors, totalPoints, messages.joinToString(" | "))
    }

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

        // ====================================================================
        // 1. KIỂM TRA ĐỘ SỐNG CỦA COOKIE INSTAGRAM & KHỞI TẠO SESSION
        // ====================================================================
        notify("Kiểm tra acc")
        val proxyConfig = InstagramApiClient.parseProxy(account.proxy)
        val igClient = InstagramApiClient(
            cookie = account.cookie,
            userAgent = account.userAgent,
            proxyConfig = proxyConfig,
            initialFbDtsg = account.fbDtsg,
            initialLsd = account.lsd
        )
        val session = igClient.ensureSession()

        val checkRes = igClient.checkCookieIg()
        if (!checkRes.isLive || checkRes.userId.isBlank()) {
            val errMsg = "Cookie Die / Proxy lỗi"
            notify(errMsg)
            reportError(cleanUsername, errMsg)
            val deadAccount = account.copy(isLive = false)
            InstagramAccountsStore.updateAccount(context, deadAccount)
            return RunResult(0, 1, 0, errMsg)
        }

        val tenfb = checkRes.username.ifBlank { cleanUsername }
        val idfb = checkRes.userId
        notify("Nick Live [$tenfb]")

        // Cập nhật lại thông tin mới nhất vào store (kèm avatar & token)
        val updatedLiveAccount = account.copy(
            username = tenfb,
            userId = idfb,
            fullName = checkRes.fullName.ifBlank { account.fullName },
            biography = checkRes.biography.ifBlank { account.biography },
            avatar = checkRes.profilePicUrl.ifBlank { account.avatar },
            fbDtsg = checkRes.fbDtsg.ifBlank { account.fbDtsg }.ifBlank { session.fbDtsg },
            lsd = checkRes.lsd.ifBlank { account.lsd }.ifBlank { session.lsd },
            isLive = true
        )
        InstagramAccountsStore.updateAccount(context, updatedLiveAccount)

        // ====================================================================
        // 2. ĐỒNG BỘ NICK LÊN XSMM (AN TOÀN - CHUẨN 100% PYTHON)
        // ====================================================================
        var finalUid = idfb
        try {
            notify("Đồng bộ acc XSMM...")
            val accListRes = XsmmAccountsRepository.getAccounts(token, accountType = "instagram", search = idfb)
            val xsmmAccounts = (accListRes as? XsmmAccountsResult.Success)?.accounts.orEmpty()
            val matchedAcc = xsmmAccounts.firstOrNull { acc ->
                acc.accountId == idfb || acc.name.equals(tenfb, ignoreCase = true)
            }
            if (matchedAcc == null) {
                val addRes = XsmmAccountsRepository.addInstagramAccount(token, tenfb)
                if (addRes is XsmmAddAccountResult.Success) {
                    notify("Đã thêm acc [$tenfb] vào XSMM")
                    if (addRes.account.accountId.isNotBlank()) {
                        finalUid = addRes.account.accountId
                    }
                    delay(1000L)
                } else if (addRes is XsmmAddAccountResult.Error) {
                    notify("Thêm acc: ${addRes.message}")
                    reportError(cleanUsername, "Lỗi thêm acc XSMM: ${addRes.message}")
                    delay(2500L)
                }
            } else {
                notify("Acc [$tenfb] đã có trên XSMM")
                if (matchedAcc.accountId.isNotBlank()) {
                    finalUid = matchedAcc.accountId
                }
            }
        } catch (e: Exception) {
            notify("Lỗi đồng bộ: ${e.message}")
            delay(1500L)
        }

        // ====================================================================
        // 3. CẤU HÌNH LOẠI NHIỆM VỤ & THỜI GIAN DELAY
        // ====================================================================
        val listnv = mutableListOf<String>()
        when (config.taskType.lowercase()) {
            "instagram_like" -> listnv.add("instagram_like")
            "instagram_follow" -> listnv.add("instagram_follow")
            "instagram_comment" -> listnv.add("instagram_comment")
            else -> {
                listnv.add("instagram_like")
                listnv.add("instagram_follow")
                listnv.add("instagram_comment")
            }
        }

        val timedelaytym = config.doTaskDurationSeconds.coerceAtLeast(10)
        val timedelaysub = config.doTaskDurationSeconds.coerceAtLeast(10)
        val timedelaycmt = config.doTaskDurationSeconds.coerceAtLeast(10)
        val dl = config.fetchTaskIntervalSeconds.coerceAtLeast(10)
        val doi = if (config.taskCountTarget > 0) config.taskCountTarget else 99999

        var maxJob = 0
        var consecutiveNoTasks = 0

        suspend fun updatePointsUi(pts: Long?) {
            if (pts != null && pts > 0) {
                synchronized(XsmmAccountStore) {
                    XsmmAccountStore.updatePoints(context, pts)
                }
                withContext(Dispatchers.Main) {
                    XsmmSession.points.value = pts
                }
            }
        }

        // ====================================================================
        // 4. VÒNG LẶP CHẠY NHIỆM VỤ
        // ====================================================================
        while (coroutineContext.isActive) {
            if (maxJob >= doi) {
                notify("Đủ $maxJob job -> Đổi nick")
                break
            }

            val randJob = listnv.random()
            val readableName = when (randJob) {
                "instagram_like" -> "Tym"
                "instagram_follow" -> "Follow"
                "instagram_comment" -> "Comment"
                else -> randJob
            }

            notify("Nhận việc: $readableName")
            val tasksRes = XsmmTasksRepository.getTasks2(token, randJob, finalUid, typejob = "normal,better,best")

            val tasks = when (tasksRes) {
                is XsmmTasks2Result.Error -> {
                    notify("Lỗi XSMM: ${tasksRes.message}")
                    reportError(cleanUsername, "Lỗi XSMM: ${tasksRes.message}")
                    delay(3000L)
                    emptyList()
                }
                is XsmmTasks2Result.Success -> tasksRes.tasks
            }

            if (tasks.isEmpty()) {
                consecutiveNoTasks++
                if (tasksRes is XsmmTasks2Result.Success) {
                    notify("Hết job $readableName")
                }
                val waitSec = dl
                for (j in waitSec downTo 1) {
                    if (!coroutineContext.isActive) break
                    notify("Chờ ${j}s...")
                    delay(1000L)
                }
                if (consecutiveNoTasks >= config.stopAfterNoTaskCount) {
                    notify("Hết job liên tục -> Dừng")
                    break
                }
                continue
            }

            consecutiveNoTasks = 0

            // ================================================================
            // XỬ LÝ NHIỆM VỤ TYM
            // ================================================================
            if (randJob == "instagram_like") {
                var soloitym = 0
                for (nv in tasks) {
                    if (!coroutineContext.isActive) break
                    val taskId = nv.id
                    val idm = nv.targetId.ifBlank { nv.idorlink }
                    val linkJob = nv.targetUrl
                    notify("Job Tym: $idm")
                    val chayfl = igClient.tym(idm, linkJob)
                    maxJob++

                    if (chayfl.success) {
                        notify("Tym xong -> Nhận xu...")
                        val claimRes = XsmmTasksRepository.completeTasks2(
                            rawToken = token,
                            type = "instagram_like",
                            taskIds = listOf(taskId),
                            uid = finalUid,
                            cookieCheck = account.cookie
                        )
                        if (claimRes.success || claimRes.points > 0 || claimRes.isTimeout) {
                            totalCompleted++
                            val earned = claimRes.points
                            totalEarnedPoints += earned
                            updatePointsUi(claimRes.totalPoints)
                            notify("Tym: +${earned} xu")
                        } else {
                            totalErrors++
                            notify("Lỗi nhận xu: ${claimRes.message}")
                        }
                        soloitym = 0
                        if (claimRes.countdown > 0) {
                            notify("Nghỉ ${claimRes.countdown}s...")
                            delay(claimRes.countdown * 1000L)
                        }
                    } else {
                        totalErrors++
                        soloitym++
                        notify("Tym lỗi: ${chayfl.message}")
                        reportError(cleanUsername, "Tym lỗi: ${chayfl.message}")
                    }

                    // Delay
                    for (x in timedelaytym downTo 1) {
                        if (!coroutineContext.isActive) break
                        notify("Delay: ${x}s")
                        delay(1000L)
                    }

                    if (soloitym > 4) {
                        notify("Lỗi liên tiếp -> Đổi nick")
                        break
                    }
                    if (maxJob >= doi) {
                        break
                    }
                }
                if (soloitym > 4) {
                    break
                }
            }

            // ================================================================
            // XỬ LÝ NHIỆM VỤ FOLLOW
            // ================================================================
            else if (randJob == "instagram_follow") {
                var soloisub = 0
                val cacheBatchNv = mutableListOf<String>()

                for (nv in tasks) {
                    if (!coroutineContext.isActive) break
                    val taskId = nv.id
                    var targetId = nv.targetId.trim().ifBlank { nv.idorlink.trim() }
                    val linkJob = nv.targetUrl

                    if (targetId.isBlank() || !targetId.all { it.isDigit() }) {
                        val resolved = InstagramApiClient.resolveTargetUserId(linkJob, account.proxy)
                        if (!resolved.isNullOrBlank()) {
                            targetId = resolved
                        }
                    }

                    notify("Follow: $targetId")

                    if (targetId.isBlank() || !targetId.all { it.isDigit() }) {
                        notify("Bỏ qua: Không có ID")
                        continue
                    }

                    val chaySub = igClient.follow(targetId, linkJob)
                    maxJob++

                    if (chaySub.success) {
                        notify("Follow xong: $targetId")
                        cacheBatchNv.add(taskId)
                        soloisub = 0
                        totalCompleted++

                        // Gom đủ 10 nhiệm vụ: Gửi nhận xu và break ngay để refresh lấy nhóm task mới
                        if (cacheBatchNv.size >= 10) {
                            notify("Gom đủ 10 -> Nhận xu...")
                            val claimRes = XsmmTasksRepository.completeTasks2(
                                rawToken = token,
                                type = "instagram_follow",
                                taskIds = cacheBatchNv.toList(),
                                uid = finalUid,
                                cookieCheck = account.cookie
                            )
                            if (claimRes.success || claimRes.points > 0 || claimRes.isTimeout) {
                                val earned = claimRes.points
                                totalEarnedPoints += earned
                                updatePointsUi(claimRes.totalPoints)
                                notify("Follow: +${earned} xu")
                            } else {
                                totalErrors++
                                notify("Lỗi nhận xu: ${claimRes.message}")
                            }
                            cacheBatchNv.clear()
                            if (claimRes.countdown > 0) {
                                notify("Nghỉ ${claimRes.countdown}s...")
                                delay(claimRes.countdown * 1000L)
                            }
                            notify("Xong đợt 10 -> Lấy job mới")
                            break
                        }
                    } else {
                        totalErrors++
                        soloisub++
                        notify("Follow lỗi: ${chaySub.message}")
                        reportError(cleanUsername, "Follow thất bại: ${chaySub.message}")
                    }

                    // Delay
                    for (x in timedelaysub downTo 1) {
                        if (!coroutineContext.isActive) break
                        notify("Delay: ${x}s")
                        delay(1000L)
                    }

                    if (soloisub > 4) {
                        notify("Lỗi liên tiếp -> Đổi nick")
                        break
                    }
                    if (maxJob >= doi) {
                        break
                    }
                }

                // Gửi nhận số task còn dư lại (nếu danh sách ban đầu ít hơn 10 task)
                if (cacheBatchNv.isNotEmpty()) {
                    notify("Gửi ${cacheBatchNv.size} job Follow còn lại...")
                    val claimRes = XsmmTasksRepository.completeTasks2(
                        rawToken = token,
                        type = "instagram_follow",
                        taskIds = cacheBatchNv.toList(),
                        uid = finalUid,
                        cookieCheck = account.cookie
                    )
                    if (claimRes.success || claimRes.points > 0 || claimRes.isTimeout) {
                        val earned = claimRes.points
                        totalEarnedPoints += earned
                        updatePointsUi(claimRes.totalPoints)
                        notify("Follow: +${earned} xu")
                    } else {
                        totalErrors++
                        notify("Lỗi nhận xu: ${claimRes.message}")
                    }
                    cacheBatchNv.clear()
                    if (claimRes.countdown > 0) {
                        notify("Nghỉ ${claimRes.countdown}s...")
                        delay(claimRes.countdown * 1000L)
                    }
                }

                if (soloisub > 4) {
                    break
                }
            }

            // ================================================================
            // XỬ LÝ NHIỆM VỤ COMMENT
            // ================================================================
            else if (randJob == "instagram_comment") {
                var soloicmt = 0
                for (nv in tasks) {
                    if (!coroutineContext.isActive) break
                    val taskId = nv.id
                    var idm = nv.targetId.trim().ifBlank { nv.idorlink.trim() }
                    val noidung = nv.comment.ifBlank { "❤️❤️❤️" }
                    val linkJob = nv.targetUrl

                    if (idm.isBlank()) {
                        val resolved = InstagramApiClient.resolveMediaId(linkJob, account.proxy)
                        if (!resolved.isNullOrBlank()) {
                            idm = resolved
                        }
                    }

                    notify("Job CMT: $idm")

                    if (idm.isBlank()) {
                        notify("CMT lỗi: Thiếu ID")
                        soloicmt++
                        totalErrors++
                        continue
                    }

                    val chayCmt = igClient.cmt(idm, noidung, linkJob)
                    maxJob++

                    if (chayCmt.success) {
                        notify("CMT xong -> Nhận xu...")
                        val claimRes = XsmmTasksRepository.completeTasks2(
                            rawToken = token,
                            type = "instagram_comment",
                            taskIds = listOf(taskId),
                            uid = finalUid,
                            cookieCheck = account.cookie
                        )
                        if (claimRes.success || claimRes.points > 0 || claimRes.isTimeout) {
                            totalCompleted++
                            val earned = claimRes.points
                            totalEarnedPoints += earned
                            updatePointsUi(claimRes.totalPoints)
                            notify("CMT: +${earned} xu")
                        } else {
                            totalErrors++
                            notify("Lỗi nhận xu: ${claimRes.message}")
                        }
                        soloicmt = 0
                        if (claimRes.countdown > 0) {
                            notify("Nghỉ ${claimRes.countdown}s...")
                            delay(claimRes.countdown * 1000L)
                        }
                    } else {
                        totalErrors++
                        soloicmt++
                        notify("CMT lỗi: ${chayCmt.message}")
                        reportError(cleanUsername, "Comment thất bại: ${chayCmt.message}")
                    }

                    // Delay
                    for (x in timedelaycmt downTo 1) {
                        if (!coroutineContext.isActive) break
                        notify("Delay: ${x}s")
                        delay(1000L)
                    }

                    if (soloicmt > 4) {
                        notify("Lỗi liên tiếp -> Đổi nick")
                        break
                    }
                    if (maxJob >= doi) {
                        break
                    }
                }

                if (soloicmt > 4) {
                    break
                }
            }
        }

        val finalMsg = "Hoàn tất: $totalCompleted thành công, $totalErrors lỗi (+${totalEarnedPoints} xu)"
        notify(finalMsg)
        return RunResult(totalCompleted, totalErrors, totalEarnedPoints, finalMsg)
    }
}
