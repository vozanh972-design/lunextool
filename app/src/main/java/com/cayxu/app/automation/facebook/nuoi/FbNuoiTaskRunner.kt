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

    suspend fun run(
        onProgress: (FbNuoiProgress) -> Unit
    ) {
        var successReactions = 0
        var successComments = 0
        var successFriends = 0
        var errorCount = 0

        onProgress(FbNuoiProgress(
            status = "Đang lướt Newsfeed GraphQL...",
            successReactions = 0,
            successComments = 0,
            successFriends = 0,
            totalErrors = 0
        ))

        // 0. Xác thực Token trước khi chạy bằng Graph API /me (Katana UA)
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

        // 1. Quét bài viết Newsfeed với 100% GraphQL API Token Engine (Chuẩn KaharaMod / Katana Native)
        val posts = try {
            FbFeedScraper.fetchFeedWithScroll(
                client = client,
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
                status = "Lỗi: Thiếu Access Token",
                totalErrors = 1,
                isFinished = true,
                errorMessage = "[$timeStr] ${e.message}"
            ))
            return
        } catch (e: Exception) {
            emptyList()
        }

        if (posts.isEmpty()) {
            onProgress(FbNuoiProgress(
                status = "Không có bài viết mới để tương tác",
                totalErrors = 0,
                isFinished = true,
                errorMessage = null
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

            // A. Thả cảm xúc dạo
            if (config.isInteractEnabled && successReactions < config.interactCount) {
                onProgress(FbNuoiProgress(
                    status = "👍 Thả cảm xúc bài ${index + 1}/${posts.size}: [$author]",
                    successReactions = successReactions,
                    successComments = successComments,
                    successFriends = successFriends,
                    totalErrors = errorCount
                ))

                val reactSuccess = FbFeedScraper.sendReaction(
                    client = client,
                    token = token,
                    post = post,
                    selectedReactionTypes = config.selectedReactions,
                    targetActorId = targetActorId
                )

                if (reactSuccess) {
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
                        errorMessage = "[$timeStr] Lỗi thả cảm xúc bài viết ${post.postId} của [$author]. Vui lòng kiểm tra Token Facebook."
                    ))
                }

                val minD = config.interactDelayMinSec.coerceAtLeast(1)
                val maxD = config.interactDelayMaxSec.coerceAtLeast(minD)
                val reactDelay = Random.nextLong(minD * 1000L, (maxD + 1) * 1000L)
                delay(reactDelay)
            }

            // B. Bình luận dạo (Kèm bộ lọc số bình luận tối thiểu)
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
                    onProgress(FbNuoiProgress(
                        status = "💬 Bình luận bài ${index + 1}: [$author] (đạt ${post.commentCount}/${config.minCommentsToComment} cmt)...",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount
                    ))

                    val commentSuccess = FbFeedScraper.sendComment(
                        client = client,
                        token = token,
                        post = post,
                        commentList = config.commentList,
                        targetActorId = targetActorId
                    )

                    if (commentSuccess) {
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
                            errorMessage = "[$timeStr] Lỗi gửi bình luận bài viết ${post.postId} của [$author]. Vui lòng kiểm tra Token Facebook."
                        ))
                    }

                    val minD = config.commentDelayMinSec.coerceAtLeast(3)
                    val maxD = config.commentDelayMaxSec.coerceAtLeast(minD)
                    val commentDelay = Random.nextLong(minD * 1000L, (maxD + 1) * 1000L)
                    delay(commentDelay)
                }
            }

            // C. Kết bạn dạo (hoặc theo dõi tác giả bài viết)
            if (config.isFriendEnabled && successFriends < config.friendCount) {
                if (index % 3 == 0) {
                    onProgress(FbNuoiProgress(
                        status = "Đang gửi lời mời kết bạn / theo dõi tác giả...",
                        successReactions = successReactions,
                        successComments = successComments,
                        successFriends = successFriends,
                        totalErrors = errorCount
                    ))

                    val friendSuccess = if (post.authorId.isNotBlank()) {
                        FbFeedScraper.followOrAddFriend(client, token, post.authorId, targetActorId)
                    } else {
                        false
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
