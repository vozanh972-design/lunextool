package com.cayxu.app.automation.facebook.nuoi

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class FbNuoiTaskRunner(
    private val cookie: String,
    private val targetActorId: String? = null,
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

    suspend fun run(
        onProgress: (FbNuoiProgress) -> Unit
    ) {
        var successReactions = 0
        var successComments = 0
        var successFriends = 0
        var errorCount = 0

        onProgress(FbNuoiProgress(
            status = "Đang quét Newsfeed (Trượt từ dưới lên)...",
            successReactions = 0,
            successComments = 0,
            successFriends = 0,
            totalErrors = 0
        ))

        // 1. Quét bài viết Newsfeed với cơ chế cuộn trang đa tầng
        val posts = FbFeedScraper.fetchFeedWithScroll(
            client = client,
            cookie = cookie,
            targetActorId = targetActorId,
            maxPages = config.maxFeedPages
        )

        if (posts.isEmpty()) {
            onProgress(FbNuoiProgress(
                status = "Lỗi: Không tìm thấy bài viết trên Newsfeed",
                totalErrors = 1,
                isFinished = true,
                errorMessage = "Không lấy được bài viết trên Feed (Kiểm tra lại Cookie/Token)"
            ))
            return
        }

        onProgress(FbNuoiProgress(
            status = "Đã tìm thấy ${posts.size} bài viết. Bắt đầu tương tác...",
            successReactions = 0,
            successComments = 0,
            successFriends = 0,
            totalErrors = 0
        ))

        // 2. Vòng lặp tương tác từng bài viết
        for ((index, post) in posts.withIndex()) {
            if (!coroutineContext.isActive) break

            // A. Thả cảm xúc dạo
            if (config.isInteractEnabled && successReactions < config.interactCount) {
                onProgress(FbNuoiProgress(
                    status = "Thả cảm xúc bài ${index + 1}/${posts.size}...",
                    successReactions = successReactions,
                    successComments = successComments,
                    successFriends = successFriends,
                    totalErrors = errorCount
                ))

                val reactSuccess = FbReactionHelper.sendReaction(
                    client = client,
                    cookie = cookie,
                    post = post,
                    selectedReactionTypes = config.selectedReactions,
                    targetActorId = targetActorId
                )

                if (reactSuccess) {
                    successReactions++
                } else {
                    errorCount++
                }

                val minD = config.interactDelayMinSec.coerceAtLeast(1)
                val maxD = config.interactDelayMaxSec.coerceAtLeast(minD)
                val reactDelay = Random.nextLong(minD * 1000L, (maxD + 1) * 1000L)
                delay(reactDelay)
            }

            // B. Bình luận dạo (Nếu bài viết hợp lệ và chưa đủ số lượng)
            if (config.isCommentEnabled && config.commentList.isNotEmpty() && successComments < config.commentCount) {
                // Cách mỗi 2-3 bài thì comment 1 lần cho tự nhiên
                if (index % 2 == 0 || successComments == 0) {
                    onProgress(FbNuoiProgress(
                        status = "Đang gửi bình luận bài ${index + 1}...",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount
                    ))

                    val commentSuccess = FbCommentHelper.sendComment(
                        client = client,
                        cookie = cookie,
                        post = post,
                        commentList = config.commentList,
                        targetActorId = targetActorId
                    )

                    if (commentSuccess) {
                        successComments++
                    } else {
                        errorCount++
                    }

                    val minD = config.commentDelayMinSec.coerceAtLeast(3)
                    val maxD = config.commentDelayMaxSec.coerceAtLeast(minD)
                    val commentDelay = Random.nextLong(minD * 1000L, (maxD + 1) * 1000L)
                    delay(commentDelay)
                }
            }

            // C. Kết bạn dạo (Theo dõi tác giả bài viết hoặc gợi ý kết bạn)
            if (config.isFriendEnabled && successFriends < config.friendCount) {
                if (index % 3 == 0) {
                    onProgress(FbNuoiProgress(
                        status = "Đang gửi lời mời kết bạn dạo...",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount
                    ))

                    val friendSuccess = if (post.authorId.isNotBlank()) {
                        FbFriendHelper.followAuthor(client, cookie, post.authorId, targetActorId)
                    } else {
                        FbFriendHelper.sendFriendRequestFromSuggestions(client, cookie, targetActorId)
                    }

                    if (friendSuccess) {
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
