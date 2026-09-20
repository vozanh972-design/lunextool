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
                    p.displayUid.trim().lowercase() == cleanUid ||
                    p.additionalProfileId.trim().lowercase() == cleanUid
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
            p.displayUid.trim().lowercase() == cleanUid ||
            p.additionalProfileId.trim().lowercase() == cleanUid
        }

        // Ưu tiên lấy UID thật (615...) của Page khi làm việc với XSMM API
        val targetUidForXsmm = if (matchedPage != null) {
            (matchedPage.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                ?: matchedPage.displayUid.takeIf { it.isNotBlank() && it.startsWith("615") }
                ?: matchedPage.additionalProfileId.takeIf { it.isNotBlank() }
                ?: matchedPage.displayUid.takeIf { it.isNotBlank() }
                ?: cleanUid).trim()
        } else {
            cleanUid
        }

        var fbToken = (matchedPage?.pageToken?.takeIf { it.isNotBlank() } ?: account.bio.trim()).orEmpty()
        if (matchedPage != null && (matchedPage.pageToken.isBlank() || fbToken.isBlank())) {
            try {
                val parentToken = account.bio.takeIf { it.isNotBlank() } ?: run {
                    if (account.note.contains("c_user=")) {
                        val mgr = FacebookAccountManager()
                        mgr.getTokenFromCookie(account.note, account.phone.ifBlank { null })?.bio.orEmpty()
                    } else ""
                }
                if (parentToken.isNotBlank()) {
                    val service = com.cayxu.app.facebook.FacebookPageService()
                    val refreshedPages = service.getPages(parentToken)
                    val refreshedMatched = refreshedPages.firstOrNull { p ->
                        p.pageId == matchedPage.pageId || (p.additionalProfileId.isNotBlank() && p.additionalProfileId == matchedPage.additionalProfileId)
                    }
                    if (refreshedMatched != null && refreshedMatched.pageToken.isNotBlank()) {
                        fbToken = refreshedMatched.pageToken
                        val currentStored = FacebookAccountsStore.getAccount(context, account.uid) ?: account
                        val updatedPages = currentStored.pages.map { p ->
                            if (p.pageId == refreshedMatched.pageId) p.copy(pageToken = refreshedMatched.pageToken) else p
                        }
                        val updatedAcc = currentStored.copy(pages = updatedPages)
                        FacebookAccountsStore.addAccount(context, updatedAcc)
                    }
                }
            } catch (_: Exception) {}
        }
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
        if (matchedPage != null) {
            // ĐANG CHẠY PAGE/PROFILE+: Tuyệt đối KHÔNG gán tên/avatar của Page lên tài khoản mẹ!
            // Tự động khôi phục lại tên tài khoản mẹ nếu trước đó từng bị gán nhầm tên Page
            if (account.name.equals(matchedPage.pageName, ignoreCase = true) && account.note.contains("c_user=")) {
                try {
                    val direct = mgr.getTokenFromCookie(account.note, account.phone.ifBlank { null })
                    if (direct != null && direct.name.isNotBlank() && !direct.name.equals(matchedPage.pageName, ignoreCase = true)) {
                        val fixedAcc = account.copy(name = direct.name, avatar = direct.avatar.ifBlank { account.avatar }, isLive = true)
                        FacebookAccountsStore.addAccount(context, fixedAcc)
                    }
                } catch (_: Exception) {}
            }

            // Cập nhật thông tin của riêng Page đó nếu cần
            if (fbToken.isNotBlank()) {
                try {
                    val details = mgr.fetchAccountDetailsWithToken(fbToken, account.phone.ifBlank { null })
                    if (details.name.isNotBlank()) {
                        val currentAcc = FacebookAccountsStore.getAccount(context, account.uid) ?: account
                        val updatedPages = currentAcc.pages.map { p ->
                            if (p.pageId == matchedPage.pageId || 
                                (matchedPage.additionalProfileId.isNotBlank() && p.additionalProfileId == matchedPage.additionalProfileId)) {
                                p.copy(
                                    pageName = details.name,
                                    avatar = details.avatar.ifBlank { p.avatar }
                                )
                            } else {
                                p
                            }
                        }
                        val updated = currentAcc.copy(pages = updatedPages, isLive = true)
                        FacebookAccountsStore.addAccount(context, updated)
                    }
                } catch (_: Exception) {}
            }
        } else {
            // ĐANG CHẠY CHÍNH TÀI KHOẢN MẸ (PROFILE)
            if (fbToken.isNotBlank()) {
                try {
                    val details = mgr.fetchAccountDetailsWithToken(fbToken, account.phone.ifBlank { null })
                    if (details.name.isNotBlank()) {
                        val currentAcc = FacebookAccountsStore.getAccount(context, account.uid) ?: account
                        val updated = currentAcc.copy(name = details.name, avatar = details.avatar.ifBlank { currentAcc.avatar }, isLive = true)
                        FacebookAccountsStore.addAccount(context, updated)
                    }
                } catch (_: Exception) {}
            }
        }

        // Đồng bộ và tự động thêm nick lên XSMM nếu chưa có, đồng thời set làm nick chạy mặc định
        try {
            notify("Kiểm tra nick trên XSMM...")
            var internalId = XsmmAccountStore.getInternalIdMap(context)[targetUidForXsmm].orEmpty()
            if (internalId.isBlank() && cleanUid != targetUidForXsmm) {
                internalId = XsmmAccountStore.getInternalIdMap(context)[cleanUid].orEmpty()
            }
            if (internalId.isBlank()) {
                val syncRes = XsmmAccountsRepository.getAccounts(token, "facebook", search = targetUidForXsmm)
                val xsmmAccounts = (syncRes as? XsmmAccountsResult.Success)?.accounts.orEmpty()
                val matchedAcc = xsmmAccounts.firstOrNull { acc ->
                    acc.accountId == targetUidForXsmm || acc.linkAccount.contains(targetUidForXsmm) ||
                    (cleanUid.isNotBlank() && (acc.accountId == cleanUid || acc.linkAccount.contains(cleanUid)))
                }
                if (matchedAcc == null) {
                    notify("Đang thêm nick [$targetUidForXsmm] lên XSMM...")
                    val addRes = XsmmAccountsRepository.addFacebookAccount(token, targetUidForXsmm)
                    if (addRes is com.cayxu.app.data.repository.XsmmAddAccountResult.Success) {
                        internalId = addRes.account.id
                        notify("Đã thêm nick lên XSMM thành công")
                        delay(1000L)
                    } else if (addRes is com.cayxu.app.data.repository.XsmmAddAccountResult.Error) {
                        notify("Thêm XSMM: ${addRes.message}")
                        delay(1500L)
                    }
                } else {
                    internalId = matchedAcc.id
                    notify("Nick đã liên kết trên XSMM")
                }
            } else {
                notify("Nick đã liên kết trên XSMM")
            }

            // Đặt tài khoản này làm mặc định (Active) trên XSMM trước khi nhận nhiệm vụ
            if (internalId.isNotBlank()) {
                notify("Đặt làm nick mặc định trên XSMM...")
                XsmmAccountsRepository.setActiveAccount(token, internalId)
                val internalMap = XsmmAccountStore.getInternalIdMap(context).toMutableMap()
                internalMap[targetUidForXsmm] = internalId
                internalMap[cleanUid] = internalId
                XsmmAccountStore.saveInternalIdMap(context, internalMap)
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

            notify("Lấy nhiệm vụ Facebook (${config.taskType})...")
            val taskResult = XsmmTasksRepository.getTasks2(token, config.taskType, targetUidForXsmm)

            when (taskResult) {
                is XsmmTasks2Result.Error -> {
                    noTaskConsecutiveCount++
                    val xsmmDetail = "Lỗi lấy nhiệm vụ từ XSMM:\n• Server phản hồi: ${taskResult.message}"
                    onErrorDetail?.invoke(cleanUid, xsmmDetail)
                    if (cleanUid != targetUidForXsmm) onErrorDetail?.invoke(targetUidForXsmm, xsmmDetail)
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

                            val target = if (task.idorlink.contains("_")) {
                                task.idorlink
                            } else {
                                task.targetId.ifBlank { task.idorlink }.ifBlank { task.targetUrl }
                            }
                            val shortTarget = if (target.length > 20) target.take(17) + "..." else target
                            val pos = "[${idx + 1}/${taskResult.tasks.size}]"

                            notify("$pos Đang làm: $shortTarget")

                            val taskRes = executeFacebookTask(
                                taskType = task.type.ifBlank { config.taskType },
                                targetId = target,
                                comment = task.comment,
                                token = fbToken,
                                cookie = account.note,
                                proxyStr = account.phone.ifBlank { null },
                                uid = targetUidForXsmm,
                                isPage = (matchedPage != null),
                                pageId615 = if (matchedPage != null) targetUidForXsmm else null
                            )

                            // Delay mô phỏng thời gian thao tác
                            if (config.doTaskDurationSeconds > 0) {
                                delay(config.doTaskDurationSeconds * 1000L)
                            }

                            if (taskRes.isSuccess) {
                                consecutiveErrors = 0
                                pendingBatchTaskIds.add(task.id)
                                totalCompleted++
                                notify("$pos Xong (Đã gom ${pendingBatchTaskIds.size}/10)")
                            } else {
                                consecutiveErrors++
                                totalErrors++
                                notify("$pos Lỗi tác vụ ($consecutiveErrors/${config.failJobCountToSwitchAccount})")
                                val detailMsg = buildString {
                                    append("Nhiệm vụ: ${task.type.ifBlank { config.taskType }}\n")
                                    append("Target: $target\n")
                                    append("Lỗi từ Facebook: ${taskRes.message ?: "Thao tác không thành công"}")
                                }
                                onErrorDetail?.invoke(cleanUid, detailMsg)
                                if (cleanUid != targetUidForXsmm) {
                                    onErrorDetail?.invoke(targetUidForXsmm, detailMsg)
                                }
                                if (config.failJobCountToSwitchAccount > 0 && consecutiveErrors >= config.failJobCountToSwitchAccount) {
                                    notify("Nick $cleanUid lỗi liên tiếp $consecutiveErrors job. Dừng.")
                                    break
                                }
                            }

                            // follow → gom đủ 10 rồi mới nhận xu
                            // comment / like (cảm xúc) / các loại khác → nhận xu ngay sau mỗi job
                            val currentTaskType = (task.type.ifBlank { config.taskType }).lowercase()
                            val isFollowTask = currentTaskType.contains("follow") || currentTaskType.contains("sub")
                            val batchLimit = if (isFollowTask) 10 else 1
                            val isLast = (idx == taskResult.tasks.size - 1)
                            if (pendingBatchTaskIds.size >= batchLimit || (isLast && pendingBatchTaskIds.isNotEmpty())) {
                                val bSize = pendingBatchTaskIds.size
                                if (isFollowTask) notify("Gửi nhận xu $bSize job follow...")
                                else notify("Gửi nhận xu job...")

                                val compRes = XsmmTasksRepository.completeTasks2(
                                    token,
                                    task.type.ifBlank { config.taskType },
                                    pendingBatchTaskIds.toList(),
                                    targetUidForXsmm
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
                                    val compErr = "Lỗi nhận xu từ XSMM:\n• Server phản hồi: ${compRes.message}"
                                    onErrorDetail?.invoke(cleanUid, compErr)
                                    if (cleanUid != targetUidForXsmm) onErrorDetail?.invoke(targetUidForXsmm, compErr)
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

    data class FbTaskResult(val isSuccess: Boolean, val message: String? = null)

    private fun executeFacebookTask(
        taskType: String,
        targetId: String,
        comment: String,
        token: String,
        cookie: String,
        proxyStr: String?,
        uid: String? = null,
        isPage: Boolean = false,
        pageId615: String? = null
    ): FbTaskResult {
        if (targetId.isBlank()) return FbTaskResult(false, "Thiếu ID đối tượng (targetId trống)")
        val cleanToken = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return FbTaskResult(false, "Token Facebook trống hoặc chưa được cấp quyền")

        val proxyParts = proxyStr?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        // =========================================================================
        // 1. NẾU LÀ TÀI KHOẢN PAGE (PROFILE+ / 615):
        // SỬ DỤNG FULL LOGIC TƯƠNG TÁC TỪ Page615TuongTacEngine (Graph API v21.0)
        // =========================================================================
        if (isPage) {
            val pageEngine = com.cayxu.app.facebook.Page615TuongTacEngine(
                pageToken = cleanToken,
                pageId615 = pageId615 ?: uid,
                proxyHost = proxyHost,
                proxyPort = proxyPort
            )
            val lower = taskType.lowercase()
            val res = when {
                lower.contains("comment") -> {
                    pageEngine.commentPost(targetId, comment.ifBlank { "❤️❤️❤️" })
                }
                lower.contains("follow") || lower.contains("sub") -> {
                    pageEngine.followTarget(targetId)
                }
                lower.contains("page") -> {
                    pageEngine.likeOtherPage(targetId)
                }
                lower.contains("group") || lower.contains("member") || lower.contains("join") -> {
                    pageEngine.joinGroup(targetId)
                }
                lower.contains("review") || lower.contains("danhgia") -> {
                    pageEngine.reviewOtherPage(targetId, reviewText = comment.ifBlank { "Rất tuyệt vời!" }, recommendationType = "positive")
                }
                lower.contains("like") || lower.contains("love") || lower.contains("care") ||
                lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") -> {
                    val reaction = when {
                        lower.contains("love") || lower.contains("tym") -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.LOVE
                        lower.contains("care") || lower.contains("thuongthuong") -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.CARE
                        lower.contains("haha") -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.HAHA
                        lower.contains("wow") -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.WOW
                        lower.contains("sad") -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.SAD
                        lower.contains("angry") -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.ANGRY
                        else -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.LIKE
                    }
                    pageEngine.reactPost(targetId, reaction)
                }
                else -> {
                    pageEngine.reactPost(targetId, com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.LIKE)
                }
            }
            val msg = if (res.isSuccess) "Thành công" else (res.message ?: res.rawResponse)
            return FbTaskResult(res.isSuccess, msg)
        }

        // =========================================================================
        // 2. NẾU LÀ TÀI KHOẢN PROFILE CÁ NHÂN MẸ:
        // GIỮ NGUYÊN 100% LOGIC TRƯỚC ĐÓ CỦA FacebookTuongTacEngine
        // =========================================================================
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
                val r = engine.likePage(targetId)
                if (!r.isSuccess && targetId.startsWith("615")) {
                    engine.follow(targetId)
                } else {
                    r
                }
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

        if (result.isSuccess) return FbTaskResult(true, "Thành công")

        // Fallback sang Graph API v21.0 nếu GraphQL mutation gặp lỗi
        var fallbackErrMsg = result.message
        try {
            when {
                lower.contains("page") -> {
                    val fRes = engine.follow(targetId)
                    if (fRes.isSuccess) return FbTaskResult(true, "Thành công")
                    fallbackErrMsg = fRes.message ?: fallbackErrMsg
                }
                lower.contains("like") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/likes?access_token=$cleanToken"
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = b
                    }
                }
                lower.contains("follow") || lower.contains("sub") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/subscribers?access_token=$cleanToken"
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = b
                    }
                }
                lower.contains("comment") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/comments?access_token=$cleanToken"
                    val form = FormBody.Builder().add("message", comment.ifBlank { "❤️❤️" }).build()
                    val req = Request.Builder().url(url).post(form).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = b
                    }
                }
            }
        } catch (e: Exception) {
            fallbackErrMsg = e.message ?: fallbackErrMsg
        }

        return FbTaskResult(false, fallbackErrMsg ?: "Lỗi thực hiện tương tác Facebook")
    }
}
