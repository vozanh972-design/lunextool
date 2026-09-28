package com.cayxu.app.automation.facebook.nuoi

import com.cayxu.app.facebook.FacebookTuongTacEngine
import com.cayxu.app.facebook.Page615TuongTacEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class FbNuoiTaskRunner(
    private val cookie: String = "",
    private val token: String = "",
    private val targetActorId: String? = null,
    private val myUid: String = "",
    private val myName: String = "",
    private val config: FbNuoiConfig = FbNuoiConfig(),
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        fun defaultClient(): OkHttpClient = buildClient(null)

        fun buildClient(proxyStr: String? = null): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)

            if (!proxyStr.isNullOrBlank()) {
                val parts = proxyStr.split(":")
                val host = parts.getOrNull(0)
                val port = parts.getOrNull(1)?.toIntOrNull()
                if (!host.isNullOrBlank() && port != null && port > 0) {
                    builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                }
            }

            return builder.build()
        }
    }

    data class FbTaskResult(val isSuccess: Boolean, val message: String? = null)

    private fun cleanFbError(body: String): String {
        try {
            val json = JSONObject(body)
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

    /**
     * Cơ chế tương tác (Like, Comment, Follow) sao chép NGUYÊN BẢN từ XsmmFacebookTaskRunner.kt
     * Sử dụng FacebookTuongTacEngine & Page615TuongTacEngine, có fallback tự động sang Graph API v21.0
     */
    private fun executeFacebookTask(
        taskType: String,
        targetId: String,
        comment: String = "",
        reactionStr: String = "",
        token: String,
        cookie: String = "",
        uid: String? = null,
        isPage: Boolean = false,
        pageId615: String? = null
    ): FbTaskResult {
        if (targetId.isBlank()) return FbTaskResult(false, "Thiếu ID đối tượng (targetId trống)")
        val cleanToken = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) return FbTaskResult(false, "Token Facebook trống hoặc chưa được cấp quyền")

        val lower = taskType.lowercase()
        if (lower.contains("comment") && comment.isBlank()) {
            return FbTaskResult(false, "Không có nội dung bình luận")
        }

        val reactTarget = if (reactionStr.isNotBlank()) reactionStr else taskType
        val pageReaction = Page615TuongTacEngine.ReactionType.fromString(reactTarget)
        val profileReaction = FacebookTuongTacEngine.ReactionType.fromString(reactTarget)

        // 1. NẾU LÀ TÀI KHOẢN PAGE (PROFILE+ / 615):
        if (isPage) {
            val pageEngine = Page615TuongTacEngine(
                pageToken = cleanToken,
                pageId615 = pageId615 ?: uid,
                userToken = cleanToken
            )
            val res = when {
                lower.contains("comment") -> pageEngine.commentPost(targetId, comment)
                lower.contains("follow") || lower.contains("sub") -> pageEngine.followTarget(targetId)
                lower.contains("page") -> pageEngine.likeOtherPage(targetId)
                lower.contains("group") || lower.contains("member") || lower.contains("join") -> pageEngine.joinGroup(targetId)
                else -> pageEngine.reactPost(targetId, pageReaction, forceMethod = "auto")
            }
            val msg = if (res.isSuccess) "Thành công" else (res.message ?: res.rawResponse)
            return FbTaskResult(res.isSuccess, msg)
        }

        // 2. NẾU LÀ TÀI KHOẢN PROFILE CÁ NHÂN (CHUẨN XSMM):
        val engine = FacebookTuongTacEngine(
            accessToken = cleanToken,
            userId = uid
        )

        val result = when {
            lower.contains("comment") -> engine.comment(targetId, comment)
            lower.contains("follow") || lower.contains("sub") -> engine.follow(targetId)
            lower.contains("page") -> engine.likePage(targetId)
            lower.contains("group") || lower.contains("member") || lower.contains("join") -> engine.joinGroup(targetId)
            else -> engine.react(targetId, profileReaction)
        }

        if (result.isSuccess) return FbTaskResult(true, "Thành công")

        // Fallback sang Graph API v21.0 nếu GraphQL mutation gặp lỗi (Chuẩn XsmmFacebookTaskRunner)
        var fallbackErrMsg = result.message
        try {
            when {
                lower.contains("like") || lower.contains("love") || lower.contains("care") ||
                lower.contains("haha") || lower.contains("wow") || lower.contains("sad") || lower.contains("angry") || reactionStr.isNotBlank() -> {
                    val reactName = when (profileReaction) {
                        FacebookTuongTacEngine.ReactionType.LOVE -> "LOVE"
                        FacebookTuongTacEngine.ReactionType.CARE -> "CARE"
                        FacebookTuongTacEngine.ReactionType.HAHA -> "HAHA"
                        FacebookTuongTacEngine.ReactionType.WOW -> "WOW"
                        FacebookTuongTacEngine.ReactionType.SAD -> "SAD"
                        FacebookTuongTacEngine.ReactionType.ANGRY -> "ANGRY"
                        else -> "LIKE"
                    }
                    val url = if (reactName == "LIKE") {
                        "https://graph.facebook.com/v21.0/$targetId/likes?access_token=$cleanToken"
                    } else {
                        "https://graph.facebook.com/v21.0/$targetId/reactions?type=$reactName&access_token=$cleanToken"
                    }
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
                lower.contains("follow") || lower.contains("sub") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/subscribers?access_token=$cleanToken"
                    val req = Request.Builder().url(url).post(FormBody.Builder().build()).build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful) return FbTaskResult(true, "Thành công")
                        val b = res.body?.string().orEmpty()
                        if (b.isNotBlank()) fallbackErrMsg = cleanFbError(b)
                    }
                }
                lower.contains("comment") -> {
                    val url = "https://graph.facebook.com/v21.0/$targetId/comments?access_token=$cleanToken"
                    val form = FormBody.Builder().add("message", comment).build()
                    val req = Request.Builder().url(url).post(form).build()
                    client.newCall(req).execute().use { res ->
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

    suspend fun run(
        onProgress: (FbNuoiProgress) -> Unit
    ) {
        var successReactions = 0
        var successComments = 0
        var successFriends = 0
        var errorCount = 0

        onProgress(FbNuoiProgress(
            status = "Đang lướt Newfeed thật của tài khoản...",
            successReactions = 0,
            successComments = 0,
            successFriends = 0,
            totalErrors = 0
        ))

        // 0. Bắt buộc có Access Token và xác thực Token trước khi chạy
        if (token.isBlank()) {
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
            onProgress(FbNuoiProgress(
                status = "Lỗi: Tài khoản chưa có Access Token",
                totalErrors = 1,
                isFinished = true,
                errorMessage = "[$timeStr] Tài khoản chưa có Access Token.\nChế độ nuôi tương tác chạy 100% bằng API Token Facebook (Katana / Graph API), vui lòng kiểm tra hoặc nạp Access Token cho tài khoản."
            ))
            return
        }

        val (isTokenValid, validationResult) = FbFeedScraper.validateToken(client, token)
        if (!isTokenValid) {
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
            onProgress(FbNuoiProgress(
                status = "Lỗi: Access Token không hợp lệ",
                totalErrors = 1,
                isFinished = true,
                errorMessage = "[$timeStr] Meta từ chối Access Token:\n$validationResult"
            ))
            return
        }

        // 1. Quét bài viết trên Newfeed thật của tài khoản
        val posts = try {
            FbFeedScraper.fetchFeedWithScroll(
                client = client,
                cookie = cookie,
                token = token,
                targetActorId = targetActorId,
                myUid = myUid,
                myName = myName,
                maxPages = config.maxFeedPages,
                targetCount = config.interactCount.coerceAtLeast(15)
            )
        } catch (e: TokenExpiredException) {
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
            onProgress(FbNuoiProgress(
                status = "Lỗi: Access Token hết hạn",
                totalErrors = 1,
                isFinished = true,
                errorMessage = "[$timeStr] Phản hồi lỗi từ Meta:\n${e.rawJsonError}"
            ))
            return
        } catch (e: IllegalArgumentException) {
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
            onProgress(FbNuoiProgress(
                status = "Lỗi: Thiếu thông tin đăng nhập",
                totalErrors = 1,
                isFinished = true,
                errorMessage = "[$timeStr] ${e.message}"
            ))
            return
        } catch (e: Exception) {
            emptyList()
        }

        if (posts.isEmpty()) {
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
            val diag = FbFeedScraper.lastDiagnosticLog.ifBlank { "Không tìm thấy bài viết từ Newfeed/Page/Nhóm nào của tài khoản." }
            onProgress(FbNuoiProgress(
                status = "Không có bài viết mới để tương tác",
                totalErrors = 0,
                isFinished = true,
                errorMessage = "[$timeStr] Không có bài viết mới để tương tác.\n\nNhật ký truy vết:\n$diag"
            ))
            return
        }

        onProgress(FbNuoiProgress(
            status = "Đang lướt Newsfeed... Đã tìm thấy ${posts.size} bài viết",
            successReactions = 0,
            successComments = 0,
            successFriends = 0,
            totalErrors = 0
        ))
        delay(1200L)

        // 2. Vòng lặp tương tác từng bài viết
        for ((index, post) in posts.withIndex()) {
            if (!coroutineContext.isActive) break

            val author = post.authorName.ifBlank { "Người dùng Facebook" }
            val snippet = post.messageSnippet.ifBlank { "Bài viết" }
            val postOverview = "Bài ${index + 1}: Tìm thấy bài viết của [$author]: '$snippet' (💬 ${post.commentCount} cmt | 👍 ${post.reactionCount} like)"

            onProgress(FbNuoiProgress(
                status = postOverview,
                successReactions = successReactions,
                successComments = successComments,
                successFriends = successFriends,
                totalErrors = errorCount
            ))
            delay(1000L)

            // A. Thả cảm xúc dạo (Cơ chế chuẩn copy từ FB XSMM)
            if (config.isInteractEnabled && successReactions < config.interactCount) {
                val chosenReaction = config.selectedReactions.randomOrNull() ?: "LIKE"
                val reactActName = when (chosenReaction.uppercase()) {
                    "LOVE", "2" -> "thả tim"
                    "CARE", "16" -> "thương thương"
                    "HAHA", "4" -> "haha"
                    "WOW", "3" -> "wow"
                    "SAD", "7" -> "buồn"
                    "ANGRY", "8" -> "phẫn nộ"
                    else -> "like"
                }

                onProgress(FbNuoiProgress(
                    status = "👍 Đang $reactActName bài ${index + 1}/${posts.size}: [$author]",
                    successReactions = successReactions,
                    successComments = successComments,
                    successFriends = successFriends,
                    totalErrors = errorCount
                ))

                val targetId = post.ftEntIdentifier.ifBlank { post.postId }
                val taskRes = executeFacebookTask(
                    taskType = "like",
                    targetId = targetId,
                    comment = "",
                    reactionStr = chosenReaction,
                    token = token,
                    cookie = cookie,
                    uid = myUid,
                    isPage = !targetActorId.isNullOrBlank(),
                    pageId615 = targetActorId
                )

                if (taskRes.isSuccess) {
                    successReactions++
                } else {
                    errorCount++
                    val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
                    onProgress(FbNuoiProgress(
                        status = "Lỗi thả cảm xúc bài [$author]",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount,
                        errorMessage = "[$timeStr] Lỗi thả cảm xúc bài viết ${post.postId} của [$author]: ${taskRes.message}"
                    ))
                }

                val minD = config.interactDelayMinSec.coerceAtLeast(1)
                val maxD = config.interactDelayMaxSec.coerceAtLeast(minD)
                val reactDelay = Random.nextLong(minD * 1000L, (maxD + 1) * 1000L)
                delay(reactDelay)
            }

            // B. Bình luận dạo (Kèm bộ lọc số bình luận tối thiểu - Cơ chế chuẩn copy từ FB XSMM)
            if (config.isCommentEnabled && config.commentList.isNotEmpty() && successComments < config.commentCount) {
                if (post.commentCount < config.minCommentsToComment) {
                    onProgress(FbNuoiProgress(
                        status = "Bỏ qua comment bài của [$author]: Chỉ có ${post.commentCount}/${config.minCommentsToComment} cmt",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount
                    ))
                    delay(1200L)
                } else {
                    val commentText = config.commentList.random()
                    val displayCmt = if (commentText.length > 25) commentText.take(22) + "..." else commentText
                    onProgress(FbNuoiProgress(
                        status = "💬 Đang bình luận bài ${index + 1}: \"$displayCmt\"...",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount
                    ))

                    val targetId = post.ftEntIdentifier.ifBlank { post.postId }
                    val taskRes = executeFacebookTask(
                        taskType = "comment",
                        targetId = targetId,
                        comment = commentText,
                        reactionStr = "",
                        token = token,
                        cookie = cookie,
                        uid = myUid,
                        isPage = !targetActorId.isNullOrBlank(),
                        pageId615 = targetActorId
                    )

                    if (taskRes.isSuccess) {
                        successComments++
                    } else {
                        errorCount++
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
                        onProgress(FbNuoiProgress(
                            status = "Lỗi gửi bình luận bài [$author]",
                            successReactions = successReactions,
                            successComments = successComments,
                            successFriends = successFriends,
                            totalErrors = errorCount,
                            errorMessage = "[$timeStr] Lỗi gửi bình luận bài viết ${post.postId} của [$author]: ${taskRes.message}"
                        ))
                    }

                    val minD = config.commentDelayMinSec.coerceAtLeast(3)
                    val maxD = config.commentDelayMaxSec.coerceAtLeast(minD)
                    val commentDelay = Random.nextLong(minD * 1000L, (maxD + 1) * 1000L)
                    delay(commentDelay)
                }
            }

            // C. Theo dõi / Kết bạn tác giả (Cơ chế chuẩn copy từ FB XSMM)
            if (config.isFriendEnabled && successFriends < config.friendCount) {
                if (index % 3 == 0) {
                    onProgress(FbNuoiProgress(
                        status = "Đang theo dõi tác giả [$author]...",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount
                    ))

                    val taskRes = if (post.authorId.isNotBlank()) {
                        executeFacebookTask(
                            taskType = "follow",
                            targetId = post.authorId,
                            comment = "",
                            reactionStr = "",
                            token = token,
                            cookie = cookie,
                            uid = myUid,
                            isPage = !targetActorId.isNullOrBlank(),
                            pageId615 = targetActorId
                        )
                    } else {
                        FbTaskResult(false, "Không tìm thấy UID tác giả")
                    }

                    if (taskRes.isSuccess) {
                        successFriends++
                    }

                    val minD = config.friendDelayMinSec.coerceAtLeast(3)
                    val maxD = config.friendDelayMaxSec.coerceAtLeast(minD)
                    val friendDelay = Random.nextLong(minD * 1000L, (maxD + 1) * 1000L)
                    delay(friendDelay)
                }
            }

            // Kiểm tra xem đã đạt tất cả các mục tiêu chưa
            val isReactDone = !config.isInteractEnabled || successReactions >= config.interactCount
            val isCommentDone = !config.isCommentEnabled || successComments >= config.commentCount
            val isFriendDone = !config.isFriendEnabled || successFriends >= config.friendCount

            if (isReactDone && isCommentDone && isFriendDone) {
                break
            }
        }

        onProgress(FbNuoiProgress(
            status = "Hoàn thành phiên nuôi nick!",
            successReactions = successReactions,
            successComments = successComments,
            successFriends = successFriends,
            totalErrors = errorCount,
            isFinished = true
        ))
    }
}
