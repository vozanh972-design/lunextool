package com.cayxu.app.automation.facebook.nuoi

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class TokenExpiredException(val rawJsonError: String) : Exception(rawJsonError)

object FbFeedScraper {
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v19.0"
    const val KATANA_UA = "[FBAN/FB4A;FBAV/548.0.0.38.106;FBBV/587429112;FBDM/{density=2.6,width=1080,height=2400};FBLC/vi_VN;FBCR/Viettel;FBMF/Xiaomi;FBBD/Redmi;FBPN/com.facebook.katana;FBDV/Redmi Note 12;FBSV/13;FBOP/1;FBCA/arm64-v8a:;]"

    /**
     * Xác thực Token nhanh trước khi chạy tác vụ.
     * Trả về (true, rawResponse) nếu Live, hoặc (false, rawJsonError) nếu Token bị từ chối.
     */
    fun validateToken(client: OkHttpClient, token: String): Pair<Boolean, String> {
        if (token.isBlank()) {
            return false to "Tài khoản chưa có Access Token (Vui lòng kiểm tra lại Token)"
        }
        val url = "$GRAPH_API_BASE/me?fields=id,name&access_token=$token"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful && !body.contains("\"error\"")) {
                    true to body
                } else {
                    val rawError = try {
                        val root = JSONObject(body)
                        root.optJSONObject("error")?.toString(2) ?: body
                    } catch (_: Exception) {
                        body.ifBlank { "HTTP ${res.code}: Không nhận được phản hồi từ Meta" }
                    }
                    false to rawError
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: "Lỗi kết nối mạng khi xác thực Token")
        }
    }

    fun parseCount(raw: String): Int {
        val clean = raw.trim().lowercase()
        if (clean.isBlank()) return 0
        return try {
            when {
                clean.endsWith("k") -> {
                    val num = clean.removeSuffix("k").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                    (num * 1000).toInt()
                }
                clean.endsWith("m") -> {
                    val num = clean.removeSuffix("m").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                    (num * 1000000).toInt()
                }
                clean.contains(".") && clean.indexOf(".") == clean.length - 4 -> {
                    clean.replace(".", "").toIntOrNull() ?: 0
                }
                clean.contains(",") && clean.indexOf(",") == clean.length - 4 -> {
                    clean.replace(",", "").toIntOrNull() ?: 0
                }
                clean.contains(",") -> {
                    clean.replace(",", ".").toDoubleOrNull()?.toInt() ?: 0
                }
                else -> clean.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun createSnippet(text: String, maxLength: Int = 28): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return "Bài viết không có văn bản"
        return if (clean.length <= maxLength) clean else clean.take(maxLength).trimEnd() + "..."
    }

    /**
     * Thu thập danh sách bài viết trên Newsfeed 100% bằng Access Token (Native GraphQL Katana / Graph API)
     */
    fun fetchFeedWithScroll(
        client: OkHttpClient,
        token: String,
        targetActorId: String? = null,
        myUid: String = "",
        myName: String = "",
        maxPages: Int = 4,
        targetCount: Int = 15
    ): List<FbPost> {
        val collectedPosts = mutableListOf<FbPost>()
        val seenPostIds = mutableSetOf<String>()

        if (token.isBlank()) {
            throw IllegalArgumentException("Tài khoản chưa có Access Token (Vui lòng kiểm tra lại Token)")
        }

        // 1. Ưu tiên 1: Native Katana GraphQL API (POST /graphql với DocID chuẩn Facebook Katana)
        val nativePosts = fetchFeedViaNativeGraphQL(
            client = client,
            token = token,
            targetActorId = targetActorId,
            myUid = myUid,
            myName = myName,
            maxPages = maxPages,
            targetCount = targetCount,
            seenPostIds = seenPostIds
        )
        collectedPosts.addAll(nativePosts)

        // 2. Ưu tiên 2 (Dự phòng): Graph API Feed chuẩn (/me/home hoặc /me/feed)
        if (collectedPosts.isEmpty()) {
            val graphApiPosts = fetchFeedViaGraphApi(
                client = client,
                token = token,
                targetActorId = targetActorId,
                myUid = myUid,
                myName = myName,
                maxPages = maxPages,
                targetCount = targetCount,
                seenPostIds = seenPostIds
            )
            collectedPosts.addAll(graphApiPosts)
        }

        return collectedPosts
    }

    /**
     * Cào Newfeed qua Native Katana Android GraphQL (DocID HomeFeedQuery / FeedUnitsPaginatingQuery)
     */
    private fun fetchFeedViaNativeGraphQL(
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
        var currentCursor: String? = null
        var pagesLoaded = 0

        val docIds = listOf("7112046835581177", "4463426747065985", "5579753175402517")
        var currentDocIndex = 0

        while (pagesLoaded < maxPages && result.size < targetCount) {
            pagesLoaded++
            val docId = docIds.getOrElse(currentDocIndex) { docIds[0] }
            try {
                val vars = JSONObject().apply {
                    put("count", 10)
                    put("cursor", if (currentCursor.isNullOrBlank()) JSONObject.NULL else currentCursor)
                    put("feedLocation", "NEWSFEED")
                    put("feedbackSource", 1)
                }

                val formBuilder = FormBody.Builder()
                    .add("doc_id", docId)
                    .add("variables", vars.toString())
                    .add("access_token", token)
                    .add("method", "post")

                if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                    formBuilder.add("actor_id", targetActorId)
                }

                val req = Request.Builder()
                    .url("https://graph.facebook.com/graphql")
                    .header("Authorization", "OAuth $token")
                    .header("User-Agent", KATANA_UA)
                    .header("X-FB-Connection-Type", "WIFI")
                    .header("Accept-Encoding", "gzip, deflate")
                    .post(formBuilder.build())
                    .build()

                val responseBody = client.newCall(req).execute().use { res ->
                    res.body?.string().orEmpty()
                }

                if (responseBody.isBlank()) break

                checkTokenError(responseBody)

                val parsed = parseGraphQLResponse(
                    rawBody = responseBody,
                    myUid = myUid,
                    myName = myName,
                    targetActorId = targetActorId,
                    seenPostIds = seenPostIds,
                    targetCount = targetCount - result.size
                )

                if (parsed.posts.isNotEmpty()) {
                    result.addAll(parsed.posts)
                    if (result.size >= targetCount || parsed.endCursor.isNullOrBlank()) {
                        break
                    }
                    currentCursor = parsed.endCursor
                } else if (pagesLoaded == 1 && currentDocIndex < docIds.size - 1) {
                    currentDocIndex++
                    pagesLoaded--
                } else {
                    break
                }
            } catch (e: Exception) {
                if (e is TokenExpiredException) throw e
                break
            }
        }

        return result
    }

    /**
     * Cào Newfeed qua Graph API (/me/feed hoặc /{actorId}/feed) bằng Token 100% (Không dùng /me/home đã deprecated)
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
        val fields = "fields=id,from{id,name},message,story,created_time,reactions.summary(true),comments.summary(true)"

        val endpoints = mutableListOf<String>()
        if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            endpoints.add("$GRAPH_API_BASE/$targetActorId/feed")
        }
        endpoints.add("$GRAPH_API_BASE/me/feed")

        for (baseEndpoint in endpoints) {
            var nextUrl: String? = "$baseEndpoint?$fields&limit=10&access_token=$token"
            var pagesLoaded = 0

            while (!nextUrl.isNullOrBlank() && pagesLoaded < maxPages && result.size < targetCount) {
                pagesLoaded++
                try {
                    val req = Request.Builder()
                        .url(nextUrl)
                        .header("Authorization", "OAuth $token")
                        .header("User-Agent", KATANA_UA)
                        .header("X-FB-Connection-Type", "WIFI")
                        .get()
                        .build()

                    val responseBody = client.newCall(req).execute().use { res ->
                        res.body?.string().orEmpty()
                    }

                    if (responseBody.isBlank()) break

                    checkTokenError(responseBody)

                    val rootJson = JSONObject(responseBody)
                    val dataArray = rootJson.optJSONArray("data") ?: JSONArray()

                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.optJSONObject(i) ?: continue
                        val rawPostId = item.optString("id")
                        if (rawPostId.isBlank()) continue

                        val realPostId = if (rawPostId.contains("_")) rawPostId.substringAfter("_") else rawPostId
                        if (!seenPostIds.add(realPostId)) continue

                        val from = item.optJSONObject("from")
                        val authorName = from?.optString("name") ?: "Người dùng Facebook"
                        val authorId = from?.optString("id") ?: ""

                        // Chặn bài của chính mình
                        val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || rawPostId.startsWith("${myUid}_") || realPostId.startsWith("${myUid}_"))) ||
                            (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                            authorId == "me" ||
                            (!targetActorId.isNullOrBlank() && (authorId == targetActorId || rawPostId.startsWith("${targetActorId}_")))

                        if (isSelfPost) continue

                        val message = item.optString("message").ifBlank { item.optString("story", "") }

                        val reactionCount = item.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
                            ?: item.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
                            ?: 0

                        val commentCount = item.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
                            ?: 0

                        result.add(
                            FbPost(
                                postId = rawPostId,
                                authorName = authorName,
                                authorId = authorId,
                                message = message,
                                messageSnippet = createSnippet(message),
                                commentCount = commentCount,
                                reactionCount = reactionCount,
                                ftEntIdentifier = realPostId
                            )
                        )

                        if (result.size >= targetCount) break
                    }

                    val paging = rootJson.optJSONObject("paging")
                    val afterCursor = paging?.optJSONObject("cursors")?.optString("after")
                    val nextFromPaging = paging?.optString("next")

                    nextUrl = when {
                        !nextFromPaging.isNullOrBlank() -> nextFromPaging
                        !afterCursor.isNullOrBlank() -> "$baseEndpoint?$fields&limit=15&access_token=$token&after=$afterCursor"
                        else -> null
                    }
                } catch (e: Exception) {
                    if (e is TokenExpiredException) throw e
                    break
                }
            }

            if (result.isNotEmpty()) break
        }

        return result
    }

    private data class ParsedFeedResult(
        val posts: List<FbPost>,
        val endCursor: String?
    )

    /**
     * Bóc tách JSON thuần từ Native GraphQL Response
     */
    private fun parseGraphQLResponse(
        rawBody: String,
        myUid: String,
        myName: String,
        targetActorId: String?,
        seenPostIds: MutableSet<String>,
        targetCount: Int
    ): ParsedFeedResult {
        val posts = mutableListOf<FbPost>()
        var endCursor: String? = null

        val jsonChunks = rawBody.lines()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }

        val rootObjects = if (jsonChunks.isNotEmpty()) {
            jsonChunks.mapNotNull {
                try { JSONObject(it) } catch (_: Exception) { null }
            }
        } else {
            try { listOf(JSONObject(rawBody)) } catch (_: Exception) { emptyList() }
        }

        for (root in rootObjects) {
            val data = root.optJSONObject("data") ?: root
            val viewer = data.optJSONObject("viewer") ?: data

            val newsFeedObj = viewer.optJSONObject("news_feed")
                ?: viewer.optJSONObject("home_feed")
                ?: data.optJSONObject("news_feed")
                ?: data.optJSONObject("home_feed")

            val edges = newsFeedObj?.optJSONArray("edges")
                ?: data.optJSONArray("edges")
                ?: JSONArray()

            val pageInfo = newsFeedObj?.optJSONObject("page_info") ?: data.optJSONObject("page_info")
            val cursorCandidate = pageInfo?.optString("end_cursor")
            if (!cursorCandidate.isNullOrBlank()) {
                endCursor = cursorCandidate
            }

            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue

                // 1. Post ID
                val rawPostId = node.optString("id").ifBlank { node.optString("post_id") }
                if (rawPostId.isBlank()) continue

                val realPostId = if (rawPostId.contains("_")) rawPostId.substringAfter("_") else rawPostId
                if (!seenPostIds.add(realPostId)) continue

                // 2. Tác giả
                val from = node.optJSONObject("from")
                val actors = node.optJSONArray("actors")
                    ?: node.optJSONObject("comet_sections")?.optJSONObject("header")?.optJSONObject("story")?.optJSONArray("actors")
                val actor0 = actors?.optJSONObject(0)
                val authorId = from?.optString("id") ?: actor0?.optString("id") ?: ""
                val authorName = from?.optString("name") ?: actor0?.optString("name") ?: "Người dùng Facebook"

                // Chặn bài của chính mình
                val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || rawPostId.startsWith("${myUid}_") || realPostId.startsWith("${myUid}_"))) ||
                    (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                    authorId == "me" ||
                    (!targetActorId.isNullOrBlank() && (authorId == targetActorId || rawPostId.startsWith("${targetActorId}_")))

                if (isSelfPost) continue

                // 3. Nội dung (Caption)
                val messageText = node.optString("message")
                    .ifBlank { node.optString("story") }
                    .ifBlank { node.optJSONObject("message")?.optString("text").orEmpty() }
                    .ifBlank { node.optJSONObject("comet_sections")?.optJSONObject("content")?.optJSONObject("story")?.optJSONObject("message")?.optString("text").orEmpty() }

                // 4. Lượt Reaction / Like
                val feedback = node.optJSONObject("feedback")
                    ?: node.optJSONObject("comet_sections")?.optJSONObject("content")?.optJSONObject("story")?.optJSONObject("feedback")
                val reactionCount = node.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
                    ?: feedback?.optJSONObject("reactors")?.optInt("count")
                    ?: feedback?.optJSONObject("reaction_count")?.optInt("count")
                    ?: feedback?.optInt("reaction_count")
                    ?: node.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
                    ?: 0

                // 5. Lượt Bình luận (Comments) - Số nguyên chuẩn xác 100%
                val commentCount = node.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
                    ?: feedback?.optInt("total_comment_count")
                    ?: feedback?.optJSONObject("comments_count")?.optJSONObject("summary")?.optInt("total_count")
                    ?: feedback?.optJSONObject("total_comment_count")?.optInt("count")
                    ?: 0

                posts.add(
                    FbPost(
                        postId = rawPostId,
                        authorName = authorName,
                        authorId = authorId,
                        message = messageText,
                        messageSnippet = createSnippet(messageText),
                        commentCount = commentCount,
                        reactionCount = reactionCount,
                        ftEntIdentifier = realPostId
                    )
                )

                if (posts.size >= targetCount) break
            }

            if (posts.size >= targetCount) break
        }

        return ParsedFeedResult(posts, endCursor)
    }

    private fun checkTokenError(jsonStr: String) {
        if (!jsonStr.contains("\"error\"")) return
        try {
            val root = JSONObject(jsonStr)
            val error = root.optJSONObject("error")
            if (error != null) {
                val code = error.optInt("code")
                val subcode = error.optInt("error_subcode")
                val message = error.optString("message")
                val isTokenError = code == 190 || subcode in listOf(458, 459, 460, 463, 467, 490) ||
                        (message.contains("Session has expired", ignoreCase = true) ||
                         message.contains("Error validating access token", ignoreCase = true) ||
                         message.contains("The access token could not be decrypted", ignoreCase = true))

                if (isTokenError) {
                    val rawJson = error.toString(2)
                    throw TokenExpiredException(rawJson)
                }
            }
        } catch (e: Exception) {
            if (e is TokenExpiredException) throw e
        }
    }

    /**
     * Thả cảm xúc 100% bằng Access Token & Katana User-Agent
     */
    fun sendReaction(
        client: OkHttpClient,
        token: String,
        post: FbPost,
        selectedReactionTypes: Set<String>,
        targetActorId: String? = null
    ): Boolean {
        val rawTarget = selectedReactionTypes.randomOrNull() ?: "LIKE"
        val reactionType = when (rawTarget.uppercase()) {
            "1", "LIKE" -> "LIKE"
            "2", "LOVE" -> "LOVE"
            "16", "CARE" -> "CARE"
            "4", "HAHA" -> "HAHA"
            "3", "WOW" -> "WOW"
            "7", "SAD" -> "SAD"
            "8", "ANGRY" -> "ANGRY"
            else -> "LIKE"
        }
        val candidateIds = linkedSetOf(post.postId, post.ftEntIdentifier).filter { it.isNotBlank() }
        for (targetId in candidateIds) {
            val url = "$GRAPH_API_BASE/$targetId/reactions"
            val formBody = FormBody.Builder()
                .add("type", reactionType)
                .add("access_token", token)
                .build()

            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .post(formBody)

            if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                reqBuilder.header("X-FB-Actor-ID", targetActorId)
            }

            try {
                val ok = client.newCall(reqBuilder.build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    res.isSuccessful || body.contains("\"success\":true") || body.contains("\"success\": true")
                }
                if (ok) return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Gửi bình luận 100% bằng Access Token & Katana User-Agent
     */
    fun sendComment(
        client: OkHttpClient,
        token: String,
        post: FbPost,
        commentList: List<String>,
        targetActorId: String? = null
    ): Boolean {
        val validComments = commentList.map { it.trim() }.filter { it.isNotBlank() }
        if (validComments.isEmpty()) return false
        val commentText = validComments.random()

        val candidateIds = linkedSetOf(post.postId, post.ftEntIdentifier).filter { it.isNotBlank() }
        for (targetId in candidateIds) {
            val url = "$GRAPH_API_BASE/$targetId/comments"
            val formBody = FormBody.Builder()
                .add("message", commentText)
                .add("access_token", token)
                .build()

            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .post(formBody)

            if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
                reqBuilder.header("X-FB-Actor-ID", targetActorId)
            }

            try {
                val ok = client.newCall(reqBuilder.build()).execute().use { res ->
                    res.isSuccessful || res.body?.string().orEmpty().contains("\"id\":")
                }
                if (ok) return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Kết bạn hoặc Follow 100% bằng Access Token & Katana User-Agent
     */
    fun followOrAddFriend(
        client: OkHttpClient,
        token: String,
        authorId: String,
        targetActorId: String? = null
    ): Boolean {
        if (authorId.isBlank() || !authorId.matches(Regex("^\\d+$"))) return false
        val friendUrl = "$GRAPH_API_BASE/me/friends/$authorId"
        val reqBuilder = Request.Builder()
            .url(friendUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .post(FormBody.Builder().add("access_token", token).build())

        if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            reqBuilder.header("X-FB-Actor-ID", targetActorId)
        }

        try {
            val ok = client.newCall(reqBuilder.build()).execute().use { res ->
                res.isSuccessful || res.body?.string().orEmpty().contains("\"success\":true")
            }
            if (ok) return true
        } catch (_: Exception) {}

        val followUrl = "$GRAPH_API_BASE/$authorId/subscribers"
        val followReq = Request.Builder()
            .url(followUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .post(FormBody.Builder().add("access_token", token).build())

        if (!targetActorId.isNullOrBlank() && targetActorId.matches(Regex("^\\d+$"))) {
            followReq.header("X-FB-Actor-ID", targetActorId)
        }

        return try {
            client.newCall(followReq.build()).execute().use { res ->
                res.isSuccessful || res.body?.string().orEmpty().contains("\"success\":true")
            }
        } catch (_: Exception) {
            false
        }
    }
}
