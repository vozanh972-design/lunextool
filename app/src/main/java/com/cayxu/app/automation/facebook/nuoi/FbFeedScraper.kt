package com.cayxu.app.automation.facebook.nuoi

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object FbFeedScraper {
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v19.0"

    /**
     * Thu thập danh sách bài viết trên Newsfeed bằng Token & Phân trang cuộn (Trượt từ dưới lên)
     * Chuẩn GraphQLFeedUnitEdge & Graph API của Kahara Mod
     */
    fun fetchFeedWithScroll(
        client: OkHttpClient,
        cookie: String,
        token: String,
        targetActorId: String? = null,
        myUid: String = "",
        myName: String = "",
        maxPages: Int = 4,
        targetCount: Int = 15
    ): List<FbPost> {
        val collectedPosts = mutableListOf<FbPost>()
        val seenPostIds = mutableSetOf<String>()

        // 1. Ưu tiên: Graph API Feed bằng Token (Tốc độ < 0.5s, cực kỳ ổn định)
        if (token.isNotBlank()) {
            val tokenPosts = fetchFeedViaGraphApi(
                client = client,
                token = token,
                targetActorId = targetActorId,
                myUid = myUid,
                myName = myName,
                maxPages = maxPages,
                targetCount = targetCount,
                seenPostIds = seenPostIds
            )
            collectedPosts.addAll(tokenPosts)
        }

        // 2. Fallback: GraphQL Native (Chuẩn GraphQLFeedUnitEdge của Kahara)
        if (collectedPosts.isEmpty()) {
            val graphQLPosts = fetchFeedViaGraphQL(
                client = client,
                cookie = cookie,
                token = token,
                targetActorId = targetActorId,
                myUid = myUid,
                myName = myName,
                seenPostIds = seenPostIds
            )
            collectedPosts.addAll(graphQLPosts)
        }

        // 3. Fallback: Nếu vẫn chưa có bài (Page mới tạo hoặc nick chưa kết bạn), nạp bài từ trang công khai
        if (collectedPosts.isEmpty() && token.isNotBlank()) {
            collectedPosts.addAll(fetchPublicPostsViaGraphApi(client, token, myUid, myName, seenPostIds))
        }

        return collectedPosts
    }

    fun createSnippet(text: String, maxLength: Int = 28): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return "Bài viết không có văn bản"
        return if (clean.length <= maxLength) clean else clean.take(maxLength).trimEnd() + "..."
    }

    /**
     * Lấy bài viết Newsfeed bằng Graph API (/me/feed hoặc /{targetActorId}/feed)
     * Hỗ trợ phân trang vô tận bằng con trỏ after (Trượt từ dưới lên)
     */
    private fun fetchFeedViaGraphApi(
        client: OkHttpClient,
        token: String,
        targetActorId: String?,
        myUid: String,
        myName: String,
        maxPages: Int,
        targetCount: Int,
        seenPostIds: MutableSet<String>
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val endpoint = if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            "$GRAPH_API_BASE/$targetActorId/feed"
        } else {
            "$GRAPH_API_BASE/me/feed"
        }

        val fieldsParam = "fields=id,message,from{id,name},created_time,comments.summary(true).limit(0),reactions.summary(true).limit(0)"
        var nextUrl: String? = "$endpoint?$fieldsParam&limit=15&access_token=$token"
        var pagesLoaded = 0

        while (!nextUrl.isNullOrBlank() && pagesLoaded < maxPages && result.size < targetCount) {
            pagesLoaded++
            try {
                val req = Request.Builder()
                    .url(nextUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .get()
                    .build()

                val responseBody = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string().orEmpty() else ""
                }

                if (responseBody.isBlank()) break

                val rootJson = JSONObject(responseBody)
                val dataArray = rootJson.optJSONArray("data") ?: JSONArray()
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    val fullId = item.optString("id")
                    if (fullId.isBlank()) continue

                    val realPostId = if (fullId.contains("_")) fullId.substringAfter("_") else fullId
                    val fromObj = item.optJSONObject("from")
                    val authorId = fromObj?.optString("id") ?: ""
                    val authorName = fromObj?.optString("name") ?: ""

                    // ⚠️ CHẶN BÀI VIẾT CÁ NHÂN / TƯỜNG CỦA CHÍNH MÌNH
                    val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || fullId.startsWith("${myUid}_") || realPostId.startsWith("${myUid}_"))) ||
                        (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                        authorId == "me" ||
                        (!targetActorId.isNullOrBlank() && (authorId == targetActorId || fullId.startsWith("${targetActorId}_")))

                    if (isSelfPost) {
                        continue
                    }

                    if (seenPostIds.add(realPostId)) {
                        val message = item.optString("message", "")
                        val commentCount = item.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
                            ?: item.optJSONObject("feedback")?.optInt("total_comment_count")
                            ?: item.optJSONObject("feedback")?.optJSONObject("total_comment_count")?.optInt("count")
                            ?: 0
                        val reactionCount = item.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
                            ?: item.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
                            ?: item.optJSONObject("feedback")?.optJSONObject("reaction_count")?.optInt("count")
                            ?: item.optJSONObject("feedback")?.optInt("reaction_count")
                            ?: 0

                        result.add(
                            FbPost(
                                postId = fullId,
                                authorName = authorName,
                                authorId = authorId,
                                message = message,
                                messageSnippet = createSnippet(message),
                                commentCount = commentCount,
                                reactionCount = reactionCount,
                                ftEntIdentifier = realPostId
                            )
                        )
                    }
                }

                if (result.size >= targetCount) break

                // Bóc con trỏ phân trang (after cursor hoặc next URL) để cuộn tiếp từ dưới lên
                val paging = rootJson.optJSONObject("paging")
                val afterCursor = paging?.optJSONObject("cursors")?.optString("after")
                val nextFromPaging = paging?.optString("next")

                nextUrl = when {
                    !nextFromPaging.isNullOrBlank() -> nextFromPaging
                    !afterCursor.isNullOrBlank() -> "$endpoint?$fieldsParam&limit=15&access_token=$token&after=$afterCursor"
                    else -> null
                }
            } catch (_: Exception) {
                break
            }
        }

        // Nếu /feed chưa có bài, thử tiếp /me/home (Newfeed bạn bè / trang theo dõi)
        if (result.isEmpty()) {
            try {
                val homeUrl = "$GRAPH_API_BASE/me/home?$fieldsParam&limit=15&access_token=$token"
                val req = Request.Builder()
                    .url(homeUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                    .build()

                val body = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string().orEmpty() else ""
                }
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val fullId = item.optString("id")
                        if (fullId.isBlank()) continue
                        val realPostId = if (fullId.contains("_")) fullId.substringAfter("_") else fullId
                        val from = item.optJSONObject("from")
                        val authorName = from?.optString("name") ?: ""
                        val authorId = from?.optString("id") ?: ""

                        val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || fullId.startsWith("${myUid}_") || realPostId.startsWith("${myUid}_"))) ||
                            (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                            authorId == "me" ||
                            (!targetActorId.isNullOrBlank() && (authorId == targetActorId || fullId.startsWith("${targetActorId}_")))

                        if (isSelfPost) {
                            continue
                        }

                        if (seenPostIds.add(realPostId)) {
                            val message = item.optString("message", "")
                            val commentCount = item.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
                                ?: item.optJSONObject("feedback")?.optInt("total_comment_count")
                                ?: 0
                            val reactionCount = item.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
                                ?: item.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
                                ?: item.optJSONObject("feedback")?.optJSONObject("reaction_count")?.optInt("count")
                                ?: 0

                            result.add(
                                FbPost(
                                    postId = fullId,
                                    authorName = authorName,
                                    authorId = authorId,
                                    message = message,
                                    messageSnippet = createSnippet(message),
                                    commentCount = commentCount,
                                    reactionCount = reactionCount,
                                    ftEntIdentifier = realPostId
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }

    /**
     * Fallback Endpoint 2: GraphQL Native (Chuẩn Kahara GraphQLFeedUnitEdge)
     */
    private fun fetchFeedViaGraphQL(
        client: OkHttpClient,
        cookie: String,
        token: String,
        targetActorId: String?,
        myUid: String,
        myName: String,
        seenPostIds: MutableSet<String>
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        try {
            val url = "https://graph.facebook.com/graphql"
            val queryDoc = "query NewsFeedQuery { viewer { news_feed { edges { node { id post_id message { text } actors { id name } feedback { total_comment_count reaction_count { count } } } } } } }"
            val formBuilder = FormBody.Builder()
                .add("q", queryDoc)

            if (token.isNotBlank()) {
                formBuilder.add("access_token", token)
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(formBuilder.build())

            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "OAuth $token")
            }
            if (cookie.isNotBlank()) {
                reqBuilder.header("Cookie", cookie)
            }
            if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                reqBuilder.header("X-FB-Actor-ID", targetActorId)
            }

            val body = client.newCall(reqBuilder.build()).execute().use { res ->
                if (res.isSuccessful) res.body?.string().orEmpty() else ""
            }

            if (body.isNotBlank()) {
                val root = JSONObject(body)
                val edges = root.optJSONObject("data")
                    ?.optJSONObject("viewer")
                    ?.optJSONObject("news_feed")
                    ?.optJSONArray("edges")
                    ?: root.optJSONArray("edges")
                    ?: JSONArray()

                for (i in 0 until edges.length()) {
                    val edge = edges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val postId = node.optString("post_id").ifBlank { node.optString("id") }
                    if (postId.isBlank()) continue

                    val realPostId = if (postId.contains("_")) postId.substringAfter("_") else postId
                    val actors = node.optJSONArray("actors")
                    val actor0 = actors?.optJSONObject(0)
                    val authorId = actor0?.optString("id") ?: ""
                    val authorName = actor0?.optString("name") ?: ""

                    // ⚠️ CHẶN BÀI VIẾT CỦA CHÍNH MÌNH
                    val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || postId.startsWith("${myUid}_") || realPostId.startsWith("${myUid}_"))) ||
                        (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                        authorId == "me" ||
                        (!targetActorId.isNullOrBlank() && (authorId == targetActorId || postId.startsWith("${targetActorId}_")))

                    if (isSelfPost) {
                        continue
                    }

                    if (seenPostIds.add(realPostId)) {
                        val msg = node.optJSONObject("message")?.optString("text") ?: ""
                        val fb = node.optJSONObject("feedback")
                        val commentCount = fb?.optInt("total_comment_count")
                            ?: fb?.optJSONObject("total_comment_count")?.optInt("count")
                            ?: fb?.optJSONObject("comments")?.optInt("total_count")
                            ?: 0
                        val reactionCount = fb?.optJSONObject("reaction_count")?.optInt("count")
                            ?: fb?.optInt("reaction_count")
                            ?: fb?.optJSONObject("reactors")?.optInt("count")
                            ?: 0

                        result.add(
                            FbPost(
                                postId = postId,
                                authorName = authorName,
                                authorId = authorId,
                                message = msg,
                                messageSnippet = createSnippet(msg),
                                commentCount = commentCount,
                                reactionCount = reactionCount,
                                ftEntIdentifier = realPostId
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Fallback các trang công cộng mở qua Graph API khi newfeed trống
     */
    private fun fetchPublicPostsViaGraphApi(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        seenPostIds: MutableSet<String>
    ): List<FbPost> {
        val publicPages = listOf("thongtinchinhphu", "vtv24", "dantri.com.vn", "vnexpress.net", "kenh14.vn")
        val result = mutableListOf<FbPost>()

        for (pageName in publicPages) {
            try {
                val url = "$GRAPH_API_BASE/$pageName/posts?fields=id,message,from{id,name},comments.summary(true).limit(0),reactions.summary(true).limit(0)&limit=10&access_token=$token"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                    .build()

                val body = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string().orEmpty() else ""
                }
                if (body.isNotBlank()) {
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank()) continue
                        val realId = if (id.contains("_")) id.substringAfter("_") else id
                        val from = item.optJSONObject("from")
                        val authorName = from?.optString("name") ?: ""
                        val authorId = from?.optString("id") ?: ""

                        val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || id.startsWith("${myUid}_") || realId.startsWith("${myUid}_"))) ||
                            (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                            authorId == "me"

                        if (isSelfPost) {
                            continue
                        }

                        if (seenPostIds.add(realId)) {
                            val message = item.optString("message", "")
                            val commentCount = item.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
                                ?: item.optJSONObject("feedback")?.optInt("total_comment_count")
                                ?: 0
                            val reactionCount = item.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
                                ?: item.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
                                ?: 0

                            result.add(
                                FbPost(
                                    postId = id,
                                    authorName = authorName,
                                    authorId = authorId,
                                    message = message,
                                    messageSnippet = createSnippet(message),
                                    commentCount = commentCount,
                                    reactionCount = reactionCount,
                                    ftEntIdentifier = realId
                                )
                            )
                        }
                    }
                }
                if (result.size >= 10) break
            } catch (_: Exception) {}
        }
        return result
    }
}
