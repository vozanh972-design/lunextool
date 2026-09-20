package com.cayxu.app.automation.facebook

import android.content.Context
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.data.repository.XsmmAccountsRepository
import com.cayxu.app.data.repository.XsmmAccountsResult
import com.cayxu.app.data.repository.XsmmTasks2Result
import com.cayxu.app.data.repository.XsmmTasksRepository
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Runner chạy tự động nhiệm vụ Facebook trên XSMM:
 * - Hoàn toàn độc lập, tách biệt 100% với TikTok và Instagram.
 * - Xử lý từng tác vụ (Like, Follow, Comment, Share...) qua Facebook Graph API / HTTP.
 * - Tự động gom 10 nhiệm vụ gửi nhận xu 1 lần.
 * - Tự động đổi nick / dừng khi đạt giới hạn lỗi hoặc mục tiêu.
 */
object XsmmFacebookTaskRunner {

    data class RunResult(
        val totalCompleted: Int,
        val totalErrors: Int,
        val totalEarnedPoints: Int,
        val message: String
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun run(
        context: Context,
        accountUids: List<String>,
        onStatusUpdate: ((String) -> Unit)? = null
    ): RunResult {
        var totalCompleted = 0
        var totalErrors = 0
        var totalPoints = 0
        val messages = mutableListOf<String>()

        for (u in accountUids) {
            val res = runSingleAccount(
                context = context,
                accountUid = u,
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
        accountUid: String,
        onProgressUpdate: ((status: String, successCount: Int, errorCount: Int) -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        onErrorDetail: ((uid: String, detail: String) -> Unit)? = null
    ): RunResult {
        val cleanUid = accountUid.trim().lowercase()
        val token = XsmmAccountStore.getToken(context)
        if (token.isNullOrBlank()) {
            val msg = "Chưa đăng nhập XSMM"
            onStatusUpdate?.invoke(msg)
            onProgressUpdate?.invoke(msg, 0, 1)
            return RunResult(0, 1, 0, msg)
        }

        val allFb = FacebookAccountsStore.getAccounts(context)
        val account = allFb.firstOrNull { it.uid.trim().lowercase() == cleanUid }
            ?: allFb.firstOrNull { acc ->
                acc.pages.any { p ->
                    p.pageId.trim().lowercase() == cleanUid ||
                    p.displayUid.trim().lowercase() == cleanUid
                }
            }
        if (account == null) {
            val msg = "[$cleanUid] Không tìm thấy tài khoản Facebook"
            onStatusUpdate?.invoke(msg)
            onProgressUpdate?.invoke(msg, 0, 1)
            return RunResult(0, 1, 0, msg)
        }

        val matchedPage = account.pages.firstOrNull { p ->
            p.pageId.trim().lowercase() == cleanUid ||
            p.displayUid.trim().lowercase() == cleanUid
        }
        var fbToken = (matchedPage?.pageToken?.takeIf { it.isNotBlank() } ?: account.bio.trim()).orEmpty()
        if (fbToken.isBlank() && account.note.contains("c_user=")) {
            try {
                val mgr = FacebookAccountManager()
                val direct = mgr.getTokenFromCookie(account.note, account.phone.ifBlank { null })
                if (direct != null && direct.bio.isNotBlank()) {
                    fbToken = direct.bio
                    val updated = account.copy(bio = fbToken, isLive = true)
                    FacebookAccountsStore.addAccount(context, updated)
                }
            } catch (_: Exception) {}
        }

        if (fbToken.isBlank() && !account.note.contains("c_user=")) {
            val msg = "[$cleanUid] Thiếu Token và Cookie Facebook"
            onStatusUpdate?.invoke(msg)
            onProgressUpdate?.invoke(msg, 0, 1)
            onErrorDetail?.invoke(cleanUid, msg)
            return RunResult(0, 1, 0, msg)
        }

        val config = XsmmRunConfigStore.get(context, "facebook")
        var totalCompleted = 0
        var totalErrors = 0
        var totalEarnedPoints = 0
        var consecutiveErrors = 0
        var noTaskConsecutiveCount = 0

        fun notify(status: String) {
            onStatusUpdate?.invoke(status)
            onProgressUpdate?.invoke(status, totalCompleted, totalErrors)
        }

        notify("Kiểm tra nick Facebook...")
        val mgr = FacebookAccountManager()
        if (fbToken.isNotBlank()) {
            try {
                val details = mgr.fetchAccountDetailsWithToken(fbToken, account.phone.ifBlank { null })
                if (details.name.isNotBlank()) {
                    val updated = account.copy(name = details.name, avatar = details.avatar.ifBlank { account.avatar }, isLive = true)
                    FacebookAccountsStore.addAccount(context, updated)
                }
            } catch (_: Exception) {}
        }

        // Đồng bộ và tự động thêm nick lên XSMM nếu chưa có
        try {
            notify("Kiểm tra nick trên XSMM...")
            val syncRes = XsmmAccountsRepository.getAccounts(token, "facebook", search = cleanUid)
            val xsmmAccounts = (syncRes as? XsmmAccountsResult.Success)?.accounts.orEmpty()
            val matchedAcc = xsmmAccounts.firstOrNull { acc ->
                acc.accountId == cleanUid || acc.linkAccount.contains(cleanUid)
            }
            if (matchedAcc == null) {
                notify("Đang thêm nick lên XSMM...")
                val addRes = XsmmAccountsRepository.addFacebookAccount(token, cleanUid)
                if (addRes is com.cayxu.app.data.repository.XsmmAddAccountResult.Success) {
                    notify("Đã thêm nick lên XSMM thành công")
                    delay(1000L)
                } else if (addRes is com.cayxu.app.data.repository.XsmmAddAccountResult.Error) {
                    notify("Thêm XSMM: ${addRes.message}")
                    delay(1500L)
                }
            } else {
                notify("Nick đã liên kết trên XSMM")
            }
        } catch (e: Exception) {
            notify("Lỗi kiểm tra XSMM: ${e.message}")
        }

        val pendingBatchTaskIds = mutableListOf<String>()

        while (coroutineContext.isActive) {
            if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                notify("Đạt mục tiêu $totalCompleted nhiệm vụ")
                break
            }
            if (config.stopAfterCompletedCount > 0 && totalCompleted >= config.stopAfterCompletedCount) {
                notify("Đạt giới hạn $totalCompleted nhiệm vụ")
                break
            }

            notify("Lấy nhiệm vụ Facebook...")
            val taskResult = XsmmTasksRepository.getTasks2(token, config.taskType, cleanUid)

            when (taskResult) {
                is XsmmTasks2Result.Error -> {
                    noTaskConsecutiveCount++
                    notify("Hết nhiệm vụ (${taskResult.message}). Chờ ${config.fetchTaskIntervalSeconds}s...")
                    if (config.stopAfterNoTaskCount > 0 && noTaskConsecutiveCount >= config.stopAfterNoTaskCount) {
                        notify("Hết nhiệm vụ liên tiếp $noTaskConsecutiveCount lần. Dừng.")
                        break
                    }
                    delay(config.fetchTaskIntervalSeconds * 1000L)
                }
                is XsmmTasks2Result.Success -> {
                    if (taskResult.tasks.isEmpty()) {
                        noTaskConsecutiveCount++
                        notify("Hết nhiệm vụ (${config.taskType}). Chờ ${config.fetchTaskIntervalSeconds}s...")
                        if (config.stopAfterNoTaskCount > 0 && noTaskConsecutiveCount >= config.stopAfterNoTaskCount) {
                            notify("Hết nhiệm vụ liên tiếp $noTaskConsecutiveCount lần. Dừng.")
                            break
                        }
                        delay(config.fetchTaskIntervalSeconds * 1000L)
                    } else {
                        noTaskConsecutiveCount = 0

                        for ((idx, task) in taskResult.tasks.withIndex()) {
                            if (!coroutineContext.isActive) break

                            if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) break
                            if (config.stopAfterCompletedCount > 0 && totalCompleted >= config.stopAfterCompletedCount) break

                            val target = task.targetId.ifBlank { task.idorlink }.ifBlank { task.targetUrl }
                            val shortTarget = if (target.length > 20) target.take(17) + "..." else target
                            val pos = "[${idx + 1}/${taskResult.tasks.size}]"

                            notify("$pos Đang làm: $shortTarget")

                            val success = executeFacebookTask(
                                taskType = task.type.ifBlank { config.taskType },
                                targetId = target,
                                comment = task.comment,
                                token = fbToken,
                                cookie = account.note,
                                proxyStr = account.phone.ifBlank { null },
                                uid = cleanUid
                            )

                            // Delay mô phỏng thời gian thao tác
                            if (config.doTaskDurationSeconds > 0) {
                                delay(config.doTaskDurationSeconds * 1000L)
                            }

                            if (success) {
                                consecutiveErrors = 0
                                pendingBatchTaskIds.add(task.id)
                                totalCompleted++
                                notify("$pos Xong (Đã gom ${pendingBatchTaskIds.size}/10)")
                            } else {
                                consecutiveErrors++
                                totalErrors++
                                notify("$pos Lỗi tác vụ ($consecutiveErrors/${config.failJobCountToSwitchAccount})")
                                onErrorDetail?.invoke(cleanUid, "Lỗi thực hiện job Facebook: $shortTarget")
                                if (config.failJobCountToSwitchAccount > 0 && consecutiveErrors >= config.failJobCountToSwitchAccount) {
                                    notify("Nick $cleanUid lỗi liên tiếp $consecutiveErrors job. Dừng.")
                                    break
                                }
                            }

                            val isLast = (idx == taskResult.tasks.size - 1)
                            if (pendingBatchTaskIds.size >= 10 || (isLast && pendingBatchTaskIds.isNotEmpty())) {
                                val bSize = pendingBatchTaskIds.size
                                notify("Gửi nhận xu $bSize job...")

                                val compRes = XsmmTasksRepository.completeTasks2(
                                    token,
                                    task.type.ifBlank { config.taskType },
                                    pendingBatchTaskIds.toList(),
                                    cleanUid
                                )

                                val pts = if (compRes.points > 0) compRes.points else 0
                                if (pts > 0) {
                                    totalEarnedPoints += pts
                                    withContext(Dispatchers.Main) {
                                        val currentPts = XsmmAccountStore.getPoints(context) + pts
                                        XsmmAccountStore.updatePoints(context, currentPts)
                                        XsmmSession.points.value = currentPts
                                    }
                                }

                                if (compRes.success && pts > 0) {
                                    notify("+$pts xu ($bSize job) (Tổng $totalCompleted NV)")
                                    if (compRes.countdown > 0) {
                                        delay(compRes.countdown * 1000L)
                                    }
                                } else {
                                    notify("Nhận xu: ${compRes.message}")
                                }
                                pendingBatchTaskIds.clear()
                            }
                        }
                    }
                }
            }
        }

        val summary = "Hoàn thành: $totalCompleted NV, Lỗi: $totalErrors, Nhận: $totalEarnedPoints xu"
        notify(summary)
        return RunResult(totalCompleted, totalErrors, totalEarnedPoints, summary)
    }

    private fun executeFacebookTask(
        taskType: String,
        targetId: String,
        comment: String,
        token: String,
        cookie: String,
        proxyStr: String?,
        uid: String? = null
    ): Boolean {
        if (targetId.isBlank()) return false
        val cleanToken = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()

        val proxyParts = proxyStr?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        val engine = com.cayxu.app.facebook.FacebookTuongTacEngine(
            accessToken = cleanToken,
            userId = uid,
            proxyHost = proxyHost,
            proxyPort = proxyPort
        )

        val lower = taskType.lowercase()
        val result = when {
            lower.contains("comment") -> {
                engine.comment(targetId, comment.ifBlank { "❤️❤️❤️" })
            }
            lower.contains("follow") || lower.contains("sub") -> {
                engine.follow(targetId)
            }
            lower.contains("page") -> {
                engine.likePage(targetId)
            }
            lower.contains("group") || lower.contains("member") || lower.contains("join") -> {
                engine.joinGroup(targetId)
            }
            lower.contains("review") || lower.contains("danhgia") -> {
                engine.reviewPage(targetId, isPositive = true, reviewText = comment.ifBlank { "Rất tuyệt vời!" })
            }
            lower.contains("like") || lower.contains("love") || lower.contains("care") ||
            lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") -> {
                val reaction = when {
                    lower.contains("love") || lower.contains("tym") -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LOVE
                    lower.contains("care") || lower.contains("thuongthuong") -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.CARE
                    lower.contains("haha") -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.HAHA
                    lower.contains("wow") -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.WOW
                    lower.contains("sad") -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.SAD
                    lower.contains("angry") -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.ANGRY
                    else -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LIKE
                }
                engine.react(targetId, reaction)
            }
            else -> {
                engine.react(targetId, com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LIKE)
            }
        }

        if (result.isSuccess) return true

        // Fallback sang Graph API v21.0 nếu GraphQL mutation gặp lỗi
        return try {
            when {
                lower.contains("like") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/likes?access_token=$cleanToken"
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { it.isSuccessful }
                }
                lower.contains("follow") || lower.contains("sub") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/subscribers?access_token=$cleanToken"
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { it.isSuccessful }
                }
                lower.contains("comment") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/comments?access_token=$cleanToken"
                    val form = FormBody.Builder().add("message", comment.ifBlank { "❤️❤️" }).build()
                    val req = Request.Builder().url(url).post(form).build()
                    httpClient.newCall(req).execute().use { it.isSuccessful }
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
