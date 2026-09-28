package com.cayxu.app.automation.facebook

import android.content.Context
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FbNuoiConfig
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.facebook.FacebookTuongTacEngine
import com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class FbFeedPost(
    val postId: String,
    val feedbackId: String,
    val authorUid: String = "",
    val authorName: String = ""
)

class FbNuoiTaskRunner(
    private val context: Context,
    private val account: FacebookAccount,
    private val config: FbNuoiConfig,
    private val onStatus: (String) -> Unit,
    private val onSuccess: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val httpClient: OkHttpClient by lazy {
        val proxyParts = account.phone.ifBlank { null }?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        val builder = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
        }
        builder.build()
    }

    suspend fun run() {
        onStatus("Đang khởi tạo phiên nuôi nick...")

        // 1. Chuẩn bị Token & Proxy
        var effectiveToken = account.bio.trim()
        val cookie = account.note.trim()
        val proxyStr = account.phone.trim().ifBlank { null }

        if (effectiveToken.isBlank() && cookie.isNotBlank()) {
            onStatus("Đang trích xuất Token từ Cookie...")
            try {
                val derived = FacebookAccountManager().getTokenFromCookie(cookie, proxyStr)
                if (derived != null && derived.bio.isNotBlank()) {
                    effectiveToken = derived.bio.trim()
                }
            } catch (_: Exception) {}
        }

        val proxyParts = proxyStr?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        val tuongTacEngine = FacebookTuongTacEngine(
            accessToken = effectiveToken,
            userId = account.uid,
            proxyHost = proxyHost,
            proxyPort = proxyPort
        )

        onStatus("Đang quét bài viết trên Newsfeed Facebook...")
        val posts = fetchNewsfeedPosts(effectiveToken, cookie)

        if (posts.isEmpty()) {
            onError("Không lấy được bài viết trên Feed (Kiểm tra lại Cookie/Token)")
            onStatus("Lỗi: Không tìm thấy bài viết trên Newsfeed")
            return
        }

        onStatus("Đã tìm thấy ${posts.size} bài viết trên Newsfeed")
        delay(1500)

        var interactedCount = 0
        var commentedCount = 0
        var followedCount = 0

        val reactionPool = if (config.selectedReactions.isNotEmpty()) {
            config.selectedReactions.toList()
        } else {
            listOf("LIKE", "LOVE")
        }

        for (post in posts) {
            // Kiểm tra điều kiện hoàn thành toàn bộ tác vụ đã bật
            val isInteractDone = !config.isInteractEnabled || (interactedCount >= config.interactCount)
            val isCommentDone = !config.isCommentEnabled || (commentedCount >= config.commentCount) || config.commentList.isEmpty()
            val isFollowDone = !config.isFollowEnabled || (followedCount >= config.followCount)

            if (isInteractDone && isCommentDone && isFollowDone) {
                break
            }

            // --- 1. TƯƠNG TÁC DẠO (THẢ CẢM XÚC) ---
            if (config.isInteractEnabled && interactedCount < config.interactCount) {
                val chosenReactionStr = reactionPool.random()
                val reactionType = ReactionType.fromString(chosenReactionStr)

                onStatus("Đang thả $chosenReactionStr vào bài ${post.postId}...")
                val res = tuongTacEngine.react(post.feedbackId, reactionType)

                if (res.isSuccess) {
                    interactedCount++
                    onSuccess()
                    onStatus("✓ Đã thả $chosenReactionStr bài ${post.postId} ($interactedCount/${config.interactCount})")
                } else {
                    onError("Lỗi thả $chosenReactionStr: ${res.message}")
                }

                // Delay tương tác dạo
                val minDelay = config.interactDelayMinSec.coerceAtLeast(1)
                val maxDelay = config.interactDelayMaxSec.coerceAtLeast(minDelay)
                val waitSec = Random.nextInt(minDelay, maxDelay + 1)
                onStatus("Chờ $waitSec giây xem bài tiếp...")
                delay(waitSec * 1000L)
            }

            // --- 2. COMMENT DẠO ---
            if (config.isCommentEnabled && commentedCount < config.commentCount && config.commentList.isNotEmpty()) {
                val chosenComment = config.commentList.random().trim()
                if (chosenComment.isNotBlank()) {
                    onStatus("Đang bình luận vào bài ${post.postId}...")
                    val res = tuongTacEngine.comment(post.feedbackId, chosenComment)

                    if (res.isSuccess) {
                        commentedCount++
                        onSuccess()
                        onStatus("✓ Đã comment bài ${post.postId} ($commentedCount/${config.commentCount})")
                    } else {
                        onError("Lỗi comment: ${res.message}")
                    }

                    // Delay comment dạo
                    val minDelay = config.commentDelayMinSec.coerceAtLeast(3)
                    val maxDelay = config.commentDelayMaxSec.coerceAtLeast(minDelay)
                    val waitSec = Random.nextInt(minDelay, maxDelay + 1)
                    onStatus("Nghỉ $waitSec giây sau khi comment...")
                    delay(waitSec * 1000L)
                }
            }

            // --- 3. FOLLOW DẠO ---
            if (config.isFollowEnabled && followedCount < config.followCount && post.authorUid.isNotBlank() && post.authorUid != account.uid) {
                val authorDisplay = post.authorName.ifBlank { post.authorUid }
                onStatus("Đang theo dõi tác giả $authorDisplay...")
                val res = tuongTacEngine.follow(post.authorUid)

                if (res.isSuccess) {
                    followedCount++
                    onSuccess()
                    onStatus("✓ Đã theo dõi $authorDisplay ($followedCount/${config.followCount})")
                } else {
                    onError("Lỗi follow: ${res.message}")
                }

                // Delay follow dạo
                val minDelay = config.followDelayMinSec.coerceAtLeast(3)
                val maxDelay = config.followDelayMaxSec.coerceAtLeast(minDelay)
                val waitSec = Random.nextInt(minDelay, maxDelay + 1)
                onStatus("Nghỉ $waitSec giây sau khi follow...")
                delay(waitSec * 1000L)
            }
        }

        val totalDone = interactedCount + commentedCount + followedCount
        onStatus("Hoàn thành nuôi nick Facebook (✓ $totalDone hành động)")
    }

    private fun fetchNewsfeedPosts(token: String, cookie: String): List<FbFeedPost> {
        val results = mutableListOf<FbFeedPost>()

        // Cách 1: Thử qua Graph API me/home hoặc me/feed nếu có Token
        if (token.isNotBlank()) {
            try {
                val url = "https://graph.facebook.com/v21.0/me/home?fields=id,from,message&limit=30&access_token=$token"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful && body.contains("\"data\":")) {
                        val json = JSONObject(body)
                        val dataArr = json.optJSONArray("data")
                        if (dataArr != null) {
                            for (i in 0 until dataArr.length()) {
                                val item = dataArr.getJSONObject(i)
                                val fullId = item.optString("id")
                                val fromObj = item.optJSONObject("from")
                                val authorId = fromObj?.optString("id").orEmpty()
                                val authorName = fromObj?.optString("name").orEmpty()

                                val feedbackId = fullId.substringAfter("_").ifBlank { fullId }
                                if (feedbackId.isNotBlank()) {
                                    results.add(
                                        FbFeedPost(
                                            postId = feedbackId,
                                            feedbackId = feedbackId,
                                            authorUid = authorId,
                                            authorName = authorName
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (results.isNotEmpty()) {
            return results.distinctBy { it.feedbackId }
        }

        // Cách 2: Quét trực tiếp qua Web Mobile (mbasic.facebook.com) bằng Cookie
        if (cookie.isNotBlank()) {
            try {
                val req = Request.Builder()
                    .url("https://mbasic.facebook.com/home.php")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Cookie", cookie)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8")
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    val html = res.body?.string().orEmpty()

                    // Quét các ft_ent_identifier (Feedback ID bài viết)
                    val entRegex = Regex("""ft_ent_identifier=(\d+)""")
                    val fbidRegex = Regex("""story_fbid=(\d+)""")
                    val idRegex = Regex("""(?:id=|owner_id=)(\d{8,})""")

                    val entMatches = entRegex.findAll(html).map { it.groupValues[1] }.toList()
                    val fbidMatches = fbidRegex.findAll(html).map { it.groupValues[1] }.toList()
                    val allPostIds = (entMatches + fbidMatches).distinct().filter { it.length >= 8 }

                    val authorMatches = idRegex.findAll(html).map { it.groupValues[1] }.toList().distinct()

                    allPostIds.forEachIndexed { idx, pid ->
                        val author = authorMatches.getOrNull(idx) ?: ""
                        results.add(
                            FbFeedPost(
                                postId = pid,
                                feedbackId = pid,
                                authorUid = author,
                                authorName = ""
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return results.distinctBy { it.feedbackId }
    }
}
