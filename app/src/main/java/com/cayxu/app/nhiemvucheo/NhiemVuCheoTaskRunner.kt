package com.cayxu.app.nhiemvucheo

import android.content.Context
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.data.local.NhiemVuCheoStore
import com.cayxu.app.facebook.FacebookTuongTacEngine
import com.cayxu.app.facebook.Page615TuongTacEngine
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Runner thực thi tự động nhiệm vụ Nhiệm Vụ Chéo (NVC) cho tài khoản / Page Facebook.
 * Chuẩn OpenAPI 3.0.3: Claim -> Execute FB -> Submit (hoặc Skip).
 */
object NhiemVuCheoTaskRunner {

    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun isRunning(uidKey: String): Boolean = activeJobs[uidKey]?.isActive == true

    fun stop(uidKey: String) {
        activeJobs.remove(uidKey)?.cancel()
    }

    fun stopAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun start(
        scope: CoroutineScope,
        context: Context,
        account: FacebookAccount,
        matchedPage: FacebookPageItem? = null,
        onStatusUpdate: ((msg: String) -> Unit)? = null,
        onSuccessCountChange: ((count: Int) -> Unit)? = null,
        onErrorCountChange: ((count: Int) -> Unit)? = null,
        onErrorDetail: ((uid: String, detail: String) -> Unit)? = null,
        onBalanceUpdate: ((newBalance: String) -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ): Job {
        val uidKey = matchedPage?.pageId?.takeIf { it.isNotBlank() } ?: account.uid
        stop(uidKey)

        val job = scope.launch(Dispatchers.IO) {
            try {
                runSingleSession(
                    context = context,
                    account = account,
                    matchedPage = matchedPage,
                    onStatusUpdate = onStatusUpdate,
                    onSuccessCountChange = onSuccessCountChange,
                    onErrorCountChange = onErrorCountChange,
                    onErrorDetail = onErrorDetail,
                    onBalanceUpdate = onBalanceUpdate
                )
            } finally {
                activeJobs.remove(uidKey)
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            }
        }

        activeJobs[uidKey] = job
        return job
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private suspend fun runSingleSession(
        context: Context,
        account: FacebookAccount,
        matchedPage: FacebookPageItem?,
        onStatusUpdate: ((msg: String) -> Unit)?,
        onSuccessCountChange: ((count: Int) -> Unit)?,
        onErrorCountChange: ((count: Int) -> Unit)?,
        onErrorDetail: ((uid: String, detail: String) -> Unit)?,
        onBalanceUpdate: ((newBalance: String) -> Unit)?
    ) {
        val isPage = (matchedPage != null)
        val targetUid = if (isPage) {
            matchedPage!!.pageId.takeIf { it.isNotBlank() } ?: account.uid
        } else {
            account.uid
        }
        val targetName = if (isPage) "Page: ${matchedPage!!.pageName.ifBlank { targetUid }}" else account.name

        fun notify(msg: String) {
            onStatusUpdate?.invoke(msg)
        }

        val nvcToken = NhiemVuCheoStore.getToken(context)
        if (nvcToken.isNullOrBlank()) {
            notify("Chưa đăng nhập tài khoản Nhiệm Vụ Chéo")
            return
        }

        val config = NvcRunConfigStore.get(context)
        val categories = config.enabledCategories.ifEmpty { listOf("reaction") }
        var currentCategoryIndex = 0

        var totalSuccess = 0
        var totalErrors = 0
        var consecutiveErrors = 0

        val proxyParts = account.phone.ifBlank { null }?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        notify("Bắt đầu chạy Nhiệm Vụ Chéo cho $targetName...")

        while (coroutineContext.isActive) {
            if (config.taskCountTarget > 0 && totalSuccess >= config.taskCountTarget) {
                notify("Đã đạt mục tiêu $totalSuccess nhiệm vụ. Dừng.")
                break
            }

            if (config.failJobLimit > 0 && consecutiveErrors >= config.failJobLimit) {
                notify("Lỗi liên tiếp $consecutiveErrors lần. Dừng nick để bảo vệ.")
                break
            }

            val category = categories[currentCategoryIndex % categories.size]
            val catLabel = when (category) {
                "reaction" -> "Cảm xúc"
                "comment"  -> "Bình luận"
                "follow"   -> "Theo dõi"
                else       -> category
            }

            notify("Đang lấy nhiệm vụ ($catLabel)...")
            val claimRes = NhiemVuCheoApiClient.claimTasks(
                token = nvcToken,
                uid = targetUid,
                category = category,
                limit = config.claimLimit,
                quality = config.quality
            )

            if (!coroutineContext.isActive) break

            when (claimRes) {
                is NvcClaimResult.Empty -> {
                    if (categories.size > 1) {
                        currentCategoryIndex++
                        val nextCat = categories[currentCategoryIndex % categories.size]
                        notify("Hết NV ($catLabel). Chuyển sang: $nextCat...")
                        delay(1500L)
                    } else {
                        val waitSec = config.delaySeconds.coerceIn(5, 60)
                        for (s in waitSec downTo 1) {
                            if (!coroutineContext.isActive) break
                            notify("Hết NV ($catLabel). Chờ ${s}s thử lại...")
                            delay(1000L)
                        }
                    }
                    continue
                }
                is NvcClaimResult.Error -> {
                    consecutiveErrors++
                    totalErrors++
                    onErrorCountChange?.invoke(totalErrors)
                    val detail = "[${currentTime()}] Lỗi claim nhiệm vụ NVC ($catLabel):\n• Mã lỗi: ${claimRes.code}\n• Phản hồi: ${claimRes.message}"
                    onErrorDetail?.invoke(targetUid, detail)
                    notify("Lỗi lấy NV: ${claimRes.message}")
                    delay(3000L)
                    if (categories.size > 1) currentCategoryIndex++
                    continue
                }
                is NvcClaimResult.Success -> {
                    val assignments = claimRes.assignments
                    notify("Đã nhận ${assignments.size} nhiệm vụ ($catLabel)")

                    for ((idx, task) in assignments.withIndex()) {
                        if (!coroutineContext.isActive) break

                        val pos = "[${idx + 1}/${assignments.size}]"
                        val fbTarget = task.targetId.ifBlank {
                            if (isPage) Page615TuongTacEngine.extractId(task.targetUrl)
                            else FacebookTuongTacEngine.extractId(task.targetUrl)
                        }

                        if (fbTarget.isBlank()) {
                            notify("$pos Bỏ qua: Không trích xuất được ID đối tượng")
                            NhiemVuCheoApiClient.skipTask(nvcToken, task.id, task.version, "invalid_target_id")
                            continue
                        }

                        // Thực thi thao tác Facebook
                        val (fbSuccess, fbMsg) = executeFbAction(
                            isPage = isPage,
                            account = account,
                            matchedPage = matchedPage,
                            targetId = fbTarget,
                            actionFamily = task.actionFamily,
                            reaction = task.reaction ?: "LIKE",
                            commentText = task.commentText ?: "",
                            proxyHost = proxyHost,
                            proxyPort = proxyPort,
                            pos = pos,
                            notify = ::notify
                        )

                        if (!coroutineContext.isActive) break

                        if (fbSuccess) {
                            notify("$pos Thao tác FB xong. Báo hoàn thành...")
                            var submitRes = NhiemVuCheoApiClient.submitTask(nvcToken, task.id, task.version)

                            // Xử lý mã chờ xác minh POINT_VERIFY_UID_MISSING
                            if (submitRes is NvcSubmitResult.RetryWait) {
                                notify("$pos Chờ xác minh, thử gửi lại sau 3s...")
                                delay(3000L)
                                submitRes = NhiemVuCheoApiClient.submitTask(nvcToken, task.id, task.version)
                            }

                            when (submitRes) {
                                is NvcSubmitResult.Success -> {
                                    totalSuccess++
                                    consecutiveErrors = 0
                                    onSuccessCountChange?.invoke(totalSuccess)

                                    val coins = submitRes.earnedCoins
                                    val coinText = if (coins > 0) "+$coins xu" else "Thành công"
                                    notify("$pos Hoàn thành: $coinText")

                                    val newBal = submitRes.newBalance
                                    if (!newBal.isNullOrBlank()) {
                                        NhiemVuCheoStore.updateCoinBalance(context, newBal)
                                        withContext(Dispatchers.Main) {
                                            onBalanceUpdate?.invoke(newBal)
                                        }
                                    }
                                }
                                is NvcSubmitResult.Error -> {
                                    consecutiveErrors++
                                    totalErrors++
                                    onErrorCountChange?.invoke(totalErrors)
                                    val detail = "[${currentTime()}] Lỗi submit NVC (${task.id}):\n• Mã lỗi: ${submitRes.code}\n• Phản hồi: ${submitRes.message}"
                                    onErrorDetail?.invoke(targetUid, detail)
                                    notify("$pos Lỗi submit: ${submitRes.message}")
                                }
                                is NvcSubmitResult.RetryWait -> {
                                    notify("$pos Chưa xác minh được sau khi thử lại")
                                }
                            }
                        } else {
                            consecutiveErrors++
                            totalErrors++
                            onErrorCountChange?.invoke(totalErrors)
                            val detail = "[${currentTime()}] Thao tác FB thất bại:\n• Nhiệm vụ: ${task.actionFamily} (${task.reaction ?: ""})\n• Target: $fbTarget\n• Chi tiết: $fbMsg"
                            onErrorDetail?.invoke(targetUid, detail)
                            notify("$pos Lỗi FB: $fbMsg")

                            // Bỏ qua nhiệm vụ trên NVC để giải phóng slot
                            NhiemVuCheoApiClient.skipTask(nvcToken, task.id, task.version, "task_failed")
                        }

                        // Delay giữa các nhiệm vụ theo cấu hình
                        if (config.delaySeconds > 0 && coroutineContext.isActive) {
                            delay(config.delaySeconds * 1000L)
                        }
                    }
                }
            }
        }

        notify("Đã dừng chạy Nhiệm Vụ Chéo ($targetName)")
    }

    private fun executeFbAction(
        isPage: Boolean,
        account: FacebookAccount,
        matchedPage: FacebookPageItem?,
        targetId: String,
        actionFamily: String,
        reaction: String,
        commentText: String,
        proxyHost: String?,
        proxyPort: Int?,
        pos: String,
        notify: (String) -> Unit
    ): Pair<Boolean, String> {
        return try {
            if (isPage) {
                val pageToken = matchedPage?.pageToken?.takeIf { it.isNotBlank() } ?: account.bio.trim()
                val pageId = matchedPage?.pageId?.takeIf { it.isNotBlank() } ?: account.uid
                val pageEngine = Page615TuongTacEngine(
                    pageToken = pageToken,
                    pageId615 = pageId,
                    userToken = account.bio.trim(),
                    proxyHost = proxyHost,
                    proxyPort = proxyPort
                )

                when (actionFamily.lowercase()) {
                    "reaction" -> {
                        val reactType = Page615TuongTacEngine.ReactionType.fromString(reaction)
                        notify("$pos Đang thả ${reactType.value} (Page) · ID: $targetId")
                        val res = pageEngine.reactPost(targetId, reactType)
                        Pair(res.isSuccess, res.message ?: res.rawResponse)
                    }
                    "comment" -> {
                        val displayCmt = if (commentText.length > 25) commentText.take(22) + "..." else commentText
                        notify("$pos Đang cmt (Page): \"$displayCmt\"")
                        val res = pageEngine.commentPost(targetId, commentText)
                        Pair(res.isSuccess, res.message ?: res.rawResponse)
                    }
                    "follow" -> {
                        notify("$pos Đang theo dõi (Page) · ID: $targetId")
                        val res = pageEngine.followTarget(targetId)
                        Pair(res.isSuccess, res.message ?: res.rawResponse)
                    }
                    else -> Pair(false, "Loại nhiệm vụ không hỗ trợ: $actionFamily")
                }
            } else {
                val token = account.bio.trim()
                val engine = FacebookTuongTacEngine(
                    accessToken = token,
                    userId = account.uid,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort
                )

                when (actionFamily.lowercase()) {
                    "reaction" -> {
                        val reactType = FacebookTuongTacEngine.ReactionType.fromString(reaction)
                        notify("$pos Đang thả ${reactType.name} · ID: $targetId")
                        val res = engine.react(targetId, reactType)
                        Pair(res.isSuccess, res.message ?: res.rawResponse)
                    }
                    "comment" -> {
                        val displayCmt = if (commentText.length > 25) commentText.take(22) + "..." else commentText
                        notify("$pos Đang cmt: \"$displayCmt\"")
                        val res = engine.comment(targetId, commentText)
                        Pair(res.isSuccess, res.message ?: res.rawResponse)
                    }
                    "follow" -> {
                        notify("$pos Đang theo dõi · ID: $targetId")
                        val res = engine.follow(targetId)
                        Pair(res.isSuccess, res.message ?: res.rawResponse)
                    }
                    else -> Pair(false, "Loại nhiệm vụ không hỗ trợ: $actionFamily")
                }
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Lỗi ngoại lệ khi thao tác Facebook")
        }
    }
}
