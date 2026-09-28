package com.cayxu.app.automation.facebook.nuoi

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

object FbFeedScraper {
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v19.0"

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

    /**
     * Thu thập danh sách bài viết trên Newsfeed bằng Mobile Web / Token & Phân trang cuộn (Trượt từ dưới lên)
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

        // 1. Ưu tiên 1: Cào Newsfeed thật từ m.facebook.com bằng Cookie (Lấy trọn vẹn số cmt thật, avatar, tác giả)
        if (cookie.isNotBlank()) {
            val webPosts = fetchFeedViaMobileWeb(
                client = client,
                cookie = cookie,
                myUid = myUid,
                myName = myName,
                targetActorId = targetActorId,
                maxPages = maxPages,
                targetCount = targetCount,
                seenPostIds = seenPostIds
            )
            collectedPosts.addAll(webPosts)
        }

        // 2. Ưu tiên 2: Graph API Feed bằng Token nếu web không trả về bài
        if (collectedPosts.isEmpty() && token.isNotBlank()) {
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

        // 3. Fallback: GraphQL Native (Chuẩn GraphQLFeedUnitEdge của Kahara)
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

        // 4. Fallback: Nếu vẫn chưa có bài (Page mới tạo hoặc nick chưa có bạn), nạp ngẫu nhiên các fanpage công khai
        if (collectedPosts.isEmpty() && token.isNotBlank()) {
            collectedPosts.addAll(fetchPublicPostsViaGraphApi(client, token, cookie, myUid, myName, seenPostIds))
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
     * Cào trực tiếp Newsfeed thật từ m.facebook.com bằng Cookie tài khoản (Bóc trọn vẹn số cmt, like thật)
     * Có hỗ trợ cuộn phân trang tiếp tục qua cursor / stories.php / xem thêm tin tức
     */
    private fun fetchFeedViaMobileWeb(
        client: OkHttpClient,
        cookie: String,
        myUid: String,
        myName: String,
        targetActorId: String?,
        maxPages: Int,
        targetCount: Int,
        seenPostIds: MutableSet<String>
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        if (cookie.isBlank()) return result

        var currentUrl: String? = "https://m.facebook.com/"
        var pagesLoaded = 0

        val cmtRegex = Regex("""([0-9.,KkMm]+)\s*(?:bình luận|comments|bl|comment)""", RegexOption.IGNORE_CASE)
        val likeRegex = Regex("""([0-9.,KkMm]+)\s*(?:lượt thích|likes|thích|reactions|người khác)""", RegexOption.IGNORE_CASE)

        while (!currentUrl.isNullOrBlank() && pagesLoaded < maxPages && result.size < targetCount) {
            pagesLoaded++
            try {
                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Cookie", cookie)
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .get()
                    .build()

                val html = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string().orEmpty() else ""
                }
                if (html.isBlank()) break

                // Bóc tách từng khối bài viết: <article ...>...</article> hoặc <div role="article" ...>...</div> hoặc khối có data-ft
                val articleRegex = Regex("""<(?:article|div[^>]*role=["']article["'])[\s\S]*?<\/(?:article|div)""", RegexOption.IGNORE_CASE)
                var matches = articleRegex.findAll(html).map { it.value }.toList()

                if (matches.isEmpty()) {
                    val dataFtBlocks = html.split("data-ft=").drop(1)
                    matches = dataFtBlocks.map { "data-ft=" + it.take(4000) }
                }

                for (block in matches) {
                    // 1. Trích xuất ID bài viết
                    var postId = ""
                    val mPostId = Pattern.compile("""(?:"top_level_post_id"|"mf_story_key"|"story_fbid"):\s*"?(\d+)"?""").matcher(block)
                    if (mPostId.find()) {
                        postId = mPostId.group(1) ?: ""
                    }
                    if (postId.isBlank()) {
                        val mFtEnt = Pattern.compile("""ft_ent_identifier=(\d+)""").matcher(block)
                        if (mFtEnt.find()) postId = mFtEnt.group(1) ?: ""
                    }
                    if (postId.isBlank()) {
                        val mLink = Pattern.compile("""/(?:posts|story\.php|permalink\.php)\?[^\s"'<>]*?(?:story_fbid|fbid|id)=(\d+)""").matcher(block)
                        if (mLink.find()) postId = mLink.group(1) ?: ""
                    }
                    if (postId.isBlank()) {
                        val mLink2 = Pattern.compile("""/(?:posts|photos)/(\d+)""").matcher(block)
                        if (mLink2.find()) postId = mLink2.group(1) ?: ""
                    }

                    if (postId.isBlank() || !seenPostIds.add(postId)) continue

                    // 2. Trích xuất tác giả
                    var authorId = ""
                    var authorName = ""
                    val mAuthorId = Pattern.compile("""(?:"content_owner_id_new"|"page_id"|"actor_id"):\s*"?(\d+)"?""").matcher(block)
                    if (mAuthorId.find()) authorId = mAuthorId.group(1) ?: ""

                    val mAuthorName = Pattern.compile("""<h3[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>""").matcher(block)
                    if (mAuthorName.find()) {
                        authorName = mAuthorName.group(1)?.trim() ?: ""
                    }
                    if (authorName.isBlank()) {
                        val mStrong = Pattern.compile("""<strong[^>]*>([^<]+)</strong>""").matcher(block)
                        if (mStrong.find()) authorName = mStrong.group(1)?.trim() ?: ""
                    }

                    // Chặn bài viết của chính mình
                    val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || postId.startsWith("${myUid}_"))) ||
                        (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                        authorId == "me" ||
                        (!targetActorId.isNullOrBlank() && authorId == targetActorId)

                    if (isSelfPost) continue

                    // 3. Trích xuất nội dung
                    var message = ""
                    val mMsg = Pattern.compile("""<p[^>]*>([\s\S]*?)</p>""").matcher(block)
                    if (mMsg.find()) {
                        message = mMsg.group(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""
                    }

                    // 4. Trích xuất số bình luận bằng Regex
                    var commentCount = 0
                    val cmtMatch = cmtRegex.find(block)
                    if (cmtMatch != null) {
                        commentCount = parseCount(cmtMatch.groupValues[1])
                    }

                    // 5. Trích xuất số lượt thích / cảm xúc
                    var reactionCount = 0
                    val likeMatch = likeRegex.find(block)
                    if (likeMatch != null) {
                        reactionCount = parseCount(likeMatch.groupValues[1])
                    }

                    result.add(
                        FbPost(
                            postId = postId,
                            authorName = authorName.ifBlank { "Người dùng Facebook" },
                            authorId = authorId,
                            message = message,
                            messageSnippet = createSnippet(message),
                            commentCount = commentCount,
                            reactionCount = reactionCount,
                            ftEntIdentifier = postId
                        )
                    )

                    if (result.size >= targetCount) break
                }

                if (result.size >= targetCount) break

                // Bóc link phân trang tiếp theo
                var nextLink: String? = null
                val mNext = Pattern.compile("""href="([^"]*(?:cursor=|stories\.php|\/home\.php\?)[^"]*)"""").matcher(html)
                while (mNext.find()) {
                    val linkCandidate = mNext.group(1)?.replace("&amp;", "&")
                    if (linkCandidate != null && (linkCandidate.contains("cursor=") || linkCandidate.contains("page_id="))) {
                        nextLink = linkCandidate
                        break
                    }
                }

                currentUrl = if (!nextLink.isNullOrBlank()) {
                    if (nextLink.startsWith("http")) nextLink else "https://m.facebook.com$nextLink"
                } else null

            } catch (_: Exception) {
                break
            }
        }
        return result
    }

    /**
     * Fallback các trang công cộng mở qua Graph API khi newfeed trống (Xáo trộn ngẫu nhiên nhiều trang)
     */
    private fun fetchPublicPostsViaGraphApi(
        client: OkHttpClient,
        token: String,
        cookie: String,
        myUid: String,
        myName: String,
        seenPostIds: MutableSet<String>
    ): List<FbPost> {
        val publicPages = listOf(
            "vtv24", "dantri.com.vn", "vnexpress.net", "kenh14.vn",
            "tuoitre.vn", "thanhnien", "tinmoi.vn", "tiin.vn",
            "beatvn.network", "honghotshowbiz", "thongtinchinhphu"
        ).shuffled()
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
                            var commentCount = item.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
                                ?: item.optJSONObject("feedback")?.optInt("total_comment_count")
                                ?: 0
                            val reactionCount = item.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
                                ?: item.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
                                ?: 0

                            // Nếu Graph API không trả số cmt do quyền hạn, cào nhanh từ mobile web bài viết nếu có cookie
                            if (commentCount == 0 && cookie.isNotBlank() && realId.isNotBlank()) {
                                try {
                                    val postReq = Request.Builder()
                                        .url("https://m.facebook.com/$realId")
                                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
                                        .header("Cookie", cookie)
                                        .get()
                                        .build()
                                    val postHtml = client.newCall(postReq).execute().use { it.body?.string().orEmpty() }
                                    val cmtM = Regex("""([0-9.,KkMm]+)\s*(?:bình luận|comments|bl|comment)""", RegexOption.IGNORE_CASE).find(postHtml)
                                    if (cmtM != null) {
                                        commentCount = parseCount(cmtM.groupValues[1])
                                    }
                                } catch (_: Exception) {}
                            }

                            result.add(
                                FbPost(
                                    postId = id,
                                    authorName = authorName.ifBlank { pageName },
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
