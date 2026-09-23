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

    private fun currentTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    }

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
        val displayName = (matchedPage?.pageName?.takeIf { it.isNotBlank() } ?: account.name).ifBlank { cleanUid }

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
            val msg = "[${currentTime()}] [$cleanUid] Thiếu Token và Cookie Facebook"
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

        // Đồng bộ và kích hoạt nick Facebook làm nick chạy trên XSMM theo cơ chế ĐƠN LUỒNG (GET/POST /api/taskapi/accounts, PUT set-active)
        notify("Kích hoạt nick [$targetUidForXsmm] trên XSMM...")
        val syncResult = XsmmAccountsRepository.syncAndActivateFacebookAccount(token, targetUidForXsmm)
        val xsmmUidToRun = syncResult.uid.ifBlank { targetUidForXsmm }

        if (syncResult.isSuccess) {
            notify("Nick [$xsmmUidToRun] đã active trên XSMM")
            if (syncResult.internalId.isNotBlank()) {
                val internalMap = XsmmAccountStore.getInternalIdMap(context).toMutableMap()
                internalMap[targetUidForXsmm] = syncResult.internalId
                XsmmAccountStore.saveInternalIdMap(context, internalMap)
            }
        } else {
            notify("Cảnh báo: ${syncResult.message}. Vẫn tiếp tục thử lấy nhiệm vụ...")
        }


        val pendingBatchTaskIds = mutableListOf<String>()
        val activeTaskTypes = config.effectiveTaskTypes()
        var currentTypeIndex = 0
        var currentTypeRetryCount = 0

        fun getTaskName(type: String): String {
            val full = XsmmRunConfigStore.facebookTaskTypes.firstOrNull { it.first == type }?.second ?: type
            return when {
                type == "facebook_like" -> "Cảm xúc"
                type == "facebook_follow" -> "Theo dõi"
                type == "facebook_comment" -> "Comment"
                type == "facebook_share" -> "Share"
                type == "facebook_likepage" -> "Like Page"
                type == "facebook_member" -> "Nhóm"
                type == "facebook_likecmt" -> "Cảm xúc cmt"
                type == "facebook_review" -> "Đánh giá"
                else -> full
            }
        }

        while (coroutineContext.isActive) {
            if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) {
                notify("Đạt mục tiêu $totalCompleted nhiệm vụ")
                break
            }
            if (config.stopAfterCompletedCount > 0 && totalCompleted >= config.stopAfterCompletedCount) {
                notify("Đạt giới hạn $totalCompleted nhiệm vụ")
                break
            }

            val currentActiveTaskType = activeTaskTypes.getOrElse(currentTypeIndex) { config.taskType }
            val currentTaskLabel = getTaskName(currentActiveTaskType)

            notify("Lấy nhiệm vụ Facebook ($currentTaskLabel)...")
            var actualTaskType = currentActiveTaskType
            var taskResult = XsmmTasksRepository.getTasks(token, currentActiveTaskType)

            if (taskResult is XsmmTasks2Result.Error && (
                taskResult.message.contains("cần thêm tài khoản", ignoreCase = true) ||
                taskResult.message.contains("chưa thêm", ignoreCase = true) ||
                taskResult.message.contains("chưa kích hoạt", ignoreCase = true) ||
                taskResult.message.contains("không tìm thấy", ignoreCase = true) ||
                taskResult.message.contains("active", ignoreCase = true) ||
                taskResult.message.contains("not found", ignoreCase = true)
            )) {
                val internalId = XsmmAccountStore.getInternalIdMap(context)[targetUidForXsmm] ?: syncResult.internalId
                if (internalId.isNotBlank()) {
                    notify("Kích hoạt lại phiên nick trên XSMM...")
                    XsmmAccountsRepository.setActiveAccount(token, internalId)
                    delay(1000L)
                } else {
                    notify("Kích hoạt lại nick [$xsmmUidToRun] trên XSMM...")
                    XsmmAccountsRepository.syncAndActivateFacebookAccount(token, targetUidForXsmm)
                    delay(1500L)
                }
                taskResult = XsmmTasksRepository.getTasks(token, currentActiveTaskType)
            }



            val (isNoTask, errorMsg) = when (taskResult) {
                is XsmmTasks2Result.Error -> {
                    val xsmmDetail = "[${currentTime()}] Lỗi lấy nhiệm vụ từ XSMM:\n• Server phản hồi: ${taskResult.message}"
                    onErrorDetail?.invoke(cleanUid, xsmmDetail)
                    if (cleanUid != targetUidForXsmm) onErrorDetail?.invoke(targetUidForXsmm, xsmmDetail)
                    Pair(true, taskResult.message)
                }
                is XsmmTasks2Result.Success -> {
                    Pair(taskResult.tasks.isEmpty(), "Hết nhiệm vụ")
                }
            }

            if (isNoTask) {
                noTaskConsecutiveCount++
                if (config.stopAfterNoTaskCount > 0 && noTaskConsecutiveCount >= config.stopAfterNoTaskCount) {
                    notify("Hết nhiệm vụ liên tiếp $noTaskConsecutiveCount lần. Dừng.")
                    break
                }

                val isServerError = taskResult is XsmmTasks2Result.Error
                if (isServerError) {
                    for (sec in 5 downTo 1) {
                        if (!coroutineContext.isActive) break
                        notify("Lỗi server XSMM: $errorMsg (${sec}s)...")
                        delay(1000L)
                    }
                } else if (activeTaskTypes.size > 1) {
                    if (currentTypeRetryCount == 0) {
                        // Lần đầu hết NV của loại này: chờ 5s tự reload lại đúng loại đó
                        currentTypeRetryCount++
                        for (sec in 5 downTo 1) {
                            if (!coroutineContext.isActive) break
                            notify("Hết NV ($currentTaskLabel). Chờ reload (${sec}s)...")
                            delay(1000L)
                        }
                    } else {
                        // Đã reload sau 5s mà vẫn hết NV -> chuyển sang loại nhiệm vụ khác trong danh sách
                        currentTypeRetryCount = 0
                        currentTypeIndex = (currentTypeIndex + 1) % activeTaskTypes.size
                        val nextTaskType = activeTaskTypes[currentTypeIndex]
                        val nextTaskLabel = getTaskName(nextTaskType)
                        notify("Hết NV ($currentTaskLabel). Chuyển sang: $nextTaskLabel...")
                        delay(1_000L)
                    }
                } else {
                    // Chỉ chọn 1 loại nhiệm vụ: chờ 5s rồi thử lại
                    val waitSec = if (config.fetchTaskIntervalSeconds in 1..5) config.fetchTaskIntervalSeconds else 5
                    for (sec in waitSec downTo 1) {
                        if (!coroutineContext.isActive) break
                        notify("Hết NV ($currentTaskLabel). Chờ ${sec}s...")
                        delay(1000L)
                    }
                }
                continue
            }

            // Có nhiệm vụ
            currentTypeRetryCount = 0
            noTaskConsecutiveCount = 0
            val taskList = (taskResult as XsmmTasks2Result.Success).tasks

            for ((idx, task) in taskList.withIndex()) {
                if (!coroutineContext.isActive) break

                if (config.taskCountTarget > 0 && totalCompleted >= config.taskCountTarget) break
                if (config.stopAfterCompletedCount > 0 && totalCompleted >= config.stopAfterCompletedCount) break

                val target = task.targetId.takeIf { it.isNotBlank() }
                    ?: if (matchedPage != null) {
                        com.cayxu.app.facebook.Page615TuongTacEngine.extractId(task.idorlink.ifBlank { task.targetUrl })
                    } else {
                        com.cayxu.app.facebook.FacebookTuongTacEngine.extractId(task.idorlink.ifBlank { task.targetUrl })
                    }
                val shortTarget = if (target.length > 20) target.take(17) + "..." else target
                val pos = "[${idx + 1}/${taskList.size}]"

                val isCommentTask = currentActiveTaskType.contains("comment", ignoreCase = true) ||
                    task.type.contains("comment", ignoreCase = true)

                if (isCommentTask && task.comment.isBlank()) {
                    notify("$pos Bỏ qua: XSMM không trả về nội dung comment cho task ${task.id}")
                    continue
                }

                val isFollowTask = currentActiveTaskType.contains("follow") || currentActiveTaskType.contains("sub") ||
                    task.type.contains("follow") || task.type.contains("sub") ||
                    actualTaskType.contains("follow") || actualTaskType.contains("sub")

                val effectiveTaskType = when {
                    isFollowTask -> "facebook_follow"
                    task.type.isNotBlank() -> task.type
                    else -> actualTaskType
                }

                val effectiveReaction = when {
                    isFollowTask || isCommentTask -> ""
                    task.reaction.isNotBlank() -> task.reaction.uppercase()
                    task.type.contains("love") || actualTaskType.contains("love") -> "LOVE"
                    task.type.contains("care") || actualTaskType.contains("care") -> "CARE"
                    task.type.contains("haha") || actualTaskType.contains("haha") -> "HAHA"
                    task.type.contains("wow") || actualTaskType.contains("wow") -> "WOW"
                    task.type.contains("sad") || actualTaskType.contains("sad") -> "SAD"
                    task.type.contains("angry") || actualTaskType.contains("angry") -> "ANGRY"
                    currentActiveTaskType == "facebook_like" || actualTaskType == "facebook_like" -> "LIKE"
                    else -> ""
                }

                if (isCommentTask) {
                    val displayCmt = if (task.comment.length > 25) task.comment.take(22) + "..." else task.comment
                    notify("$pos Đang làm ($currentTaskLabel): \"$displayCmt\"")
                } else if (isFollowTask) {
                    notify("Đang theo dõi · UID: $target")
                } else if (effectiveReaction.isNotBlank()) {
                    val reactAct = when (effectiveReaction) {
                        "LOVE" -> "thả tim"
                        "CARE" -> "thương thương"
                        "HAHA" -> "haha"
                        "WOW" -> "wow"
                        "SAD" -> "buồn"
                        "ANGRY" -> "phẫn nộ"
                        else -> "like"
                    }
                    notify("Đang $reactAct · UID: $target")
                } else {
                    val actName = when {
                        currentActiveTaskType.contains("likepage") || task.type.contains("likepage") -> "like page"
                        currentActiveTaskType.contains("member") || task.type.contains("member") -> "tham gia nhóm"
                        currentActiveTaskType.contains("share") || task.type.contains("share") -> "chia sẻ"
                        else -> currentTaskLabel.lowercase()
                    }
                    notify("Đang $actName · UID: $target")
                }

                val taskRes = executeFacebookTask(
                    taskType = effectiveTaskType,
                    targetId = target,
                    comment = task.comment,
                    reactionStr = effectiveReaction,
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
                    val fbErr = taskRes.message ?: "Thao tác Facebook thất bại"
                    notify("$pos Lỗi FB: $fbErr ($consecutiveErrors/${config.failJobCountToSwitchAccount})")
                    val detailMsg = buildString {
                        append("[${currentTime()}] Thao tác Facebook thất bại:\n")
                        val displayTaskType = if (effectiveReaction.isNotBlank()) {
                            "facebook_reaction ($effectiveReaction)"
                        } else {
                            task.type.ifBlank { currentActiveTaskType }
                        }
                        append("• Nhiệm vụ: $displayTaskType\n")
                        append("• Target: $target\n")
                        append("• Chi tiết: $fbErr")
                    }
                    onErrorDetail?.invoke(cleanUid, detailMsg)
                    if (cleanUid != targetUidForXsmm) {
                        onErrorDetail?.invoke(targetUidForXsmm, detailMsg)
                    }
                    if (consecutiveErrors >= 3) {
                        com.cayxu.app.worker.AppAlertNotifier.notifyAccountError(
                            context = context,
                            platform = "Facebook",
                            accountName = displayName,
                            accountUid = cleanUid,
                            consecutiveErrors = consecutiveErrors,
                            errorDetail = fbErr
                        )
                    }
                    if (config.failJobCountToSwitchAccount > 0 && consecutiveErrors >= config.failJobCountToSwitchAccount) {
                        notify("Nick $cleanUid lỗi liên tiếp $consecutiveErrors job. Dừng.")
                        com.cayxu.app.worker.AppAlertNotifier.notifyAccountError(
                            context = context,
                            platform = "Facebook",
                            accountName = displayName,
                            accountUid = cleanUid,
                            consecutiveErrors = consecutiveErrors,
                            errorDetail = "Đã dừng chạy: Đạt giới hạn $consecutiveErrors job lỗi liên tiếp"
                        )
                        break
                    }
                }

                // follow → gom đủ 10 rồi mới nhận xu
                // comment / like (cảm xúc) / các loại khác → nhận xu ngay sau mỗi job
                val isCommentTaskType = currentActiveTaskType.contains("comment")

                val batchLimit = if (isFollowTask) 10 else 1
                val isLast = (idx == taskList.size - 1)
                if (pendingBatchTaskIds.size >= batchLimit || (isLast && pendingBatchTaskIds.isNotEmpty())) {
                    val bSize = pendingBatchTaskIds.size

                    // Riêng job Comment (đặc biệt là Page FB): Cần đợi 60s để Facebook hiển thị và server XSMM quét duyệt comment rồi mới gửi hoàn thành
                    if (isCommentTaskType) {
                        val cmtNotice = "[${currentTime()}] Đang đợi 60s để Facebook hiển thị comment và XSMM quét duyệt trước khi gửi hoàn thành..."
                        onErrorDetail?.invoke(cleanUid, cmtNotice)
                        if (cleanUid != targetUidForXsmm) onErrorDetail?.invoke(targetUidForXsmm, cmtNotice)
                        for (sec in 60 downTo 1) {
                            if (!coroutineContext.isActive) break
                            notify("$pos Đã cmt xong. Đợi ${sec}s gửi hoàn thành...")
                            delay(1000L)
                        }
                    }

                    notify("Gửi nhận xu $bSize job...")

                    // Chuẩn API XSMM đơn luồng: POST /api/taskapi/tasks/complete (Body: {"type": ..., "task_id": [...]})
                    val apiCompleteType = when {
                        isFollowTask -> if (actualTaskType.contains("sub") || task.type.contains("sub")) "facebook_sub" else "facebook_follow"
                        task.type.isNotBlank() && task.type.startsWith("facebook_") -> task.type
                        actualTaskType.startsWith("facebook_") -> actualTaskType
                        currentActiveTaskType.startsWith("facebook_") -> currentActiveTaskType
                        else -> "facebook_like"
                    }

                    // GỌI HOÀN THÀNH JOB ĐƠN LUỒNG (1 lần duy nhất, không thử lại)
                    val compRes = XsmmTasksRepository.completeTasks(
                        rawToken = token,
                        type = apiCompleteType,
                        taskIds = pendingBatchTaskIds.toList()
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

                    if (compRes.success || pts > 0) {
                        val succText = if (pts > 0) "+$pts xu ($bSize job)" else (compRes.message.ifBlank { "Thành công $bSize job" })
                        val succDetail = "[${currentTime()}] Nhận xu thành công:\n• Nhận được: +$pts xu ($bSize nhiệm vụ)\n• Server phản hồi: ${compRes.message.ifBlank { "Hoàn thành" }}"
                        onErrorDetail?.invoke(cleanUid, succDetail)
                        if (cleanUid != targetUidForXsmm) onErrorDetail?.invoke(targetUidForXsmm, succDetail)

                        // Đã nhận xu: dùng ĐÚNG cấu hình người dùng (fetchTaskIntervalSeconds)
                        // Countdown server chỉ là fallback khi chưa cấu hình (= 0)
                        val waitSec = if (config.fetchTaskIntervalSeconds > 0) {
                            config.fetchTaskIntervalSeconds
                        } else if (compRes.countdown > 0) {
                            compRes.countdown
                        } else {
                            5
                        }

                        for (sec in waitSec downTo 1) {
                            if (!coroutineContext.isActive) break
                            notify("$succText. Đợi ${sec}s nhận job tiếp...")
                            delay(1000L)
                        }
                    } else {
                        consecutiveErrors++
                        totalErrors++
                        val errMsg = compRes.message.ifBlank { "Không nhận được xu" }
                        notify("Lỗi nhận xu: $errMsg")
                        val compErr = "[${currentTime()}] Lỗi nhận xu từ XSMM:\n• Server phản hồi: $errMsg"
                        onErrorDetail?.invoke(cleanUid, compErr)
                        if (cleanUid != targetUidForXsmm) onErrorDetail?.invoke(targetUidForXsmm, compErr)
                        if (consecutiveErrors >= 3) {
                            com.cayxu.app.worker.AppAlertNotifier.notifyAccountError(
                                context = context,
                                platform = "Facebook",
                                accountName = displayName,
                                accountUid = cleanUid,
                                consecutiveErrors = consecutiveErrors,
                                errorDetail = "Lỗi nhận xu: $errMsg"
                            )
                        }
                        delay(2000L)
                    }
                    pendingBatchTaskIds.clear()
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
        reactionStr: String = "",
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

        val lower = taskType.lowercase()
        if (lower.contains("comment") && comment.isBlank()) {
            return FbTaskResult(false, "Không có nội dung bình luận từ nhiệm vụ XSMM")
        }

        // Xác định chính xác loại cảm xúc cần tương tác (LOVE, CARE, HAHA, WOW, SAD, ANGRY, LIKE)
        val reactTarget = if (reactionStr.isNotBlank()) reactionStr else taskType
        val pageReaction = com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.fromString(reactTarget)
        val profileReaction = com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.fromString(reactTarget)

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
            val res = when {
                lower.contains("comment") -> {
                    pageEngine.commentPost(targetId, comment)
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
                    pageEngine.reviewOtherPage(targetId, reviewText = comment.ifBlank { "Tuyệt vời!" }, recommendationType = "positive")
                }
                lower.contains("like") || lower.contains("love") || lower.contains("care") ||
                lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") || reactionStr.isNotBlank() -> {
                    pageEngine.reactPost(targetId, pageReaction)
                }
                else -> {
                    pageEngine.reactPost(targetId, pageReaction)
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

        val result = when {
            lower.contains("comment") -> {
                engine.comment(targetId, comment)
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
                engine.reviewPage(targetId, isPositive = true, reviewText = comment.ifBlank { "Tuyệt vời!" })
            }
            lower.contains("like") || lower.contains("love") || lower.contains("care") ||
            lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") || reactionStr.isNotBlank() -> {
                engine.react(targetId, profileReaction)
            }
            else -> {
                engine.react(targetId, profileReaction)
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
                lower.contains("like") || lower.contains("love") || lower.contains("care") ||
                lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || lower.contains("tym") || reactionStr.isNotBlank() -> {
                    val reactName = when (profileReaction) {
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LOVE -> "LOVE"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.CARE -> "CARE"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.HAHA -> "HAHA"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.WOW -> "WOW"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.SAD -> "SAD"
                        com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.ANGRY -> "ANGRY"
                        else -> "LIKE"
                    }
                    val url = if (reactName == "LIKE") {
                        "https://graph.facebook.com/v21.0/$targetId/likes?access_token=$cleanToken"
                    } else {
                        "https://graph.facebook.com/v21.0/$targetId/reactions?type=$reactName&access_token=$cleanToken"
                    }
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
                lower.contains("follow") || lower.contains("sub") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/subscribers?access_token=$cleanToken"
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
                lower.contains("comment") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/comments?access_token=$cleanToken"
                    val form = FormBody.Builder().add("message", comment).build()
                    val req = Request.Builder().url(url).post(form).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
            }
        } catch (e: Exception) {
            fallbackErrMsg = e.message ?: fallbackErrMsg
        }

        return FbTaskResult(false, fallbackErrMsg ?: "Lỗi thực hiện tương tác Facebook")
    }

    private fun cleanFbError(body: String): String {
        try {
            val json = org.json.JSONObject(body)
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                val title = err?.optString("error_user_title")?.takeIf { it.isNotBlank() }
                val userMsg = err?.optString("error_user_msg")?.takeIf { it.isNotBlank() }
                if (!title.isNullOrBlank() || !userMsg.isNullOrBlank()) {
                    return listOfNotNull(title, userMsg).joinToString(": ")
                }
                val code = err?.optInt("code", 0) ?: 0
                val subcode = err?.optInt("error_subcode", 0) ?: 0
                if (code == 368 || subcode == 1390008) {
                    return "Tài khoản bị Facebook giới hạn tính năng tạm thời (Spam Block - Mã 368)"
                }
                val msg = err?.optString("message")
                if (!msg.isNullOrBlank()) return msg
            }
        } catch (_: Exception) {}
        return body
    }
}
