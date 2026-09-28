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
     * Cào trực tiếp Newsfeed thật từ m.facebook.com / home.php bằng Cookie tài khoản (Bóc trọn vẹn số cmt, like thật)
     * Có hỗ trợ cuộn phân trang tiếp tục qua cursor / aftercursorr / stories.php
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

        val cmtRegex = Regex("""([0-9.,KkMm]+)\s*(?:bình luận|comments|bl|comment)""", RegexOption.IGNORE_CASE)
        val likeRegex = Regex("""([0-9.,KkMm]+)\s*(?:lượt thích|likes|like|thích|reactions|người khác)""", RegexOption.IGNORE_CASE)

        val baseUrls = listOf("https://m.facebook.com/", "https://m.facebook.com/home.php")
        var currentUrl: String? = baseUrls[0]
        var pagesLoaded = 0
        var baseUrlIndex = 0

        while (!currentUrl.isNullOrBlank() && pagesLoaded < maxPages && result.size < targetCount) {
            pagesLoaded++
            try {
                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Cookie", cookie)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                    .header("Sec-Ch-Ua-Mobile", "?1")
                    .header("Sec-Ch-Ua-Platform", "\"Android\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .get()
                    .build()

                val html = client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string().orEmpty() else ""
                }
                if (html.isBlank()) {
                    if (pagesLoaded == 1 && baseUrlIndex < baseUrls.size - 1) {
                        baseUrlIndex++
                        currentUrl = baseUrls[baseUrlIndex]
                        continue
                    }
                    break
                }

                // 1. Phân tách danh sách bài viết từ HTML (ưu tiên <article>, nếu không có thì phân tách theo mốc data-ft=)
                val blocks = mutableListOf<String>()

                val articleMatcher = Pattern.compile("""<article[^>]*>([\s\S]*?)</article>""", Pattern.CASE_INSENSITIVE).matcher(html)
                while (articleMatcher.find()) {
                    blocks.add(articleMatcher.group(0))
                }

                if (blocks.isEmpty()) {
                    val rawChunks = html.split(Regex("""(?=<article[\s>]|<div[^>]*role=["']article["']|data-ft=)""", RegexOption.IGNORE_CASE))
                    for (chunk in rawChunks) {
                        if (chunk.contains("top_level_post_id") || chunk.contains("mf_story_key") || chunk.contains("story_fbid") || chunk.contains("ft_ent_identifier") || chunk.contains("data-ft")) {
                            blocks.add(chunk)
                        }
                    }
                }

                for (block in blocks) {
                    // 1. Trích xuất ID bài viết (Post ID)
                    var postId = ""
                    val idPatterns = listOf(
                        Pattern.compile("""(?:"|&quot;)(?:top_level_post_id|mf_story_key|story_fbid|target_fbid)(?:"|&quot;):\s*(?:"|&quot;)?(\d+)"""),
                        Pattern.compile("""ft_ent_identifier[=:]\s*"?(\d+)"""),
                        Pattern.compile("""(?:story_fbid|fbid)[=:]\s*"?(\d+)"""),
                        Pattern.compile("""/(?:posts|photos|videos|reel)/(\d+)"""),
                        Pattern.compile("""/(?:story\.php|permalink\.php)\?[^\s"'<>]*?(?:story_fbid|fbid|id)=(\d+)""")
                    )
                    for (p in idPatterns) {
                        val m = p.matcher(block)
                        if (m.find()) {
                            val candidate = m.group(1) ?: ""
                            if (candidate.isNotBlank() && candidate != myUid && candidate != targetActorId) {
                                postId = candidate
                                break
                            }
                        }
                    }

                    if (postId.isBlank() || !seenPostIds.add(postId)) continue

                    // 2. Trích xuất tác giả (Author ID & Name)
                    var authorId = ""
                    var authorName = ""
                    val mAuthorId = Pattern.compile("""(?:"|&quot;)(?:content_owner_id_new|page_id|actor_id)(?:"|&quot;):\s*(?:"|&quot;)?(\d+)""").matcher(block)
                    if (mAuthorId.find()) authorId = mAuthorId.group(1) ?: ""

                    val authorPatterns = listOf(
                        Pattern.compile("""<h3[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>"""),
                        Pattern.compile("""<header[^>]*>[\s\S]*?<strong[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>"""),
                        Pattern.compile("""<header[^>]*>[\s\S]*?<strong[^>]*>([\s\S]*?)</strong>"""),
                        Pattern.compile("""<strong[^>]*><a[^>]*>([^<]+)</a></strong>"""),
                        Pattern.compile("""<strong[^>]*>([^<]+)</strong>"""),
                        Pattern.compile("""<span[^>]*class="[^"]*actor[^"]*"[^>]*>([^<]+)</span>"""),
                        Pattern.compile("""role=["']link["'][^>]*>([^<]+)</a>""")
                    )
                    for (ap in authorPatterns) {
                        val m = ap.matcher(block)
                        if (m.find()) {
                            val rawName = m.group(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""
                            if (rawName.isNotBlank() && !rawName.equals("Facebook", ignoreCase = true) && !rawName.startsWith("http")) {
                                authorName = rawName
                                break
                            }
                        }
                    }

                    // Chặn bài viết của chính mình
                    val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || postId.startsWith("${myUid}_"))) ||
                        (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                        authorId == "me" ||
                        (!targetActorId.isNullOrBlank() && authorId == targetActorId)

                    if (isSelfPost) continue

                    // 3. Trích xuất nội dung bài viết (Message/Caption)
                    var message = ""
                    val msgPatterns = listOf(
                        Pattern.compile("""<div[^>]*data-ad-preview=["']message["'][^>]*>([\s\S]*?)</div>"""),
                        Pattern.compile("""<div[^>]*class="[^"]*(?:story_body_container|userContent|msg|text|body)[^"]*"[^>]*>([\s\S]*?)</div>"""),
                        Pattern.compile("""<p[^>]*>([\s\S]*?)</p>"""),
                        Pattern.compile("""<span[^>]*dir=["']auto["'][^>]*>([\s\S]*?)</span>""")
                    )
                    for (mp in msgPatterns) {
                        val m = mp.matcher(block)
                        if (m.find()) {
                            val rawMsg = m.group(1)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""
                            if (rawMsg.isNotBlank() && rawMsg.length > message.length) {
                                message = rawMsg
                            }
                        }
                    }
                    val cleanMessage = message
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#039;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    // 4. Trích xuất số bình luận (Comment Count)
                    var commentCount = 0
                    val textWithoutTags = block.replace(Regex("<[^>]+>"), " ")
                    val cmtMatch = cmtRegex.find(textWithoutTags)
                    if (cmtMatch != null) {
                        commentCount = parseCount(cmtMatch.groupValues[1])
                    }
                    if (commentCount == 0) {
                        val mCmtRaw = Regex("""([0-9.,KkMm]+)\s*(?:<[^>]+>\s*)*(?:bình luận|comments|bl|comment)""", RegexOption.IGNORE_CASE).find(block)
                        if (mCmtRaw != null) {
                            commentCount = parseCount(mCmtRaw.groupValues[1])
                        }
                    }
                    if (commentCount == 0) {
                        val jsonCmt = Pattern.compile("""(?:"|&quot;)(?:total_comment_count|comment_count|comments_count)(?:"|&quot;):\s*(?:"|&quot;)?(\d+)""").matcher(block)
                        if (jsonCmt.find()) {
                            commentCount = jsonCmt.group(1)?.toIntOrNull() ?: 0
                        }
                    }
                    if (commentCount == 0) {
                        val ariaCmt = Pattern.compile("""aria-label=["']([0-9.,KkMm]+)\s*(?:bình luận|comments|bl)""", Pattern.CASE_INSENSITIVE).matcher(block)
                        if (ariaCmt.find()) {
                            commentCount = parseCount(ariaCmt.group(1) ?: "")
                        }
                    }

                    // 5. Trích xuất số lượt thích / cảm xúc (Reaction Count)
                    var reactionCount = 0
                    val likeMatch = likeRegex.find(textWithoutTags)
                    if (likeMatch != null) {
                        reactionCount = parseCount(likeMatch.groupValues[1])
                    }
                    if (reactionCount == 0) {
                        val mLikeRaw = Regex("""([0-9.,KkMm]+)\s*(?:<[^>]+>\s*)*(?:lượt thích|likes|like|thích|reactions|người khác)""", RegexOption.IGNORE_CASE).find(block)
                        if (mLikeRaw != null) {
                            reactionCount = parseCount(mLikeRaw.groupValues[1])
                        }
                    }
                    if (reactionCount == 0) {
                        val jsonLike = Pattern.compile("""(?:"|&quot;)(?:reaction_count|like_count|reactors_count)(?:"|&quot;):\s*(?:"|&quot;)?(\d+)""").matcher(block)
                        if (jsonLike.find()) {
                            reactionCount = jsonLike.group(1)?.toIntOrNull() ?: 0
                        }
                    }
                    if (reactionCount == 0) {
                        val ariaLike = Pattern.compile("""aria-label=["']([0-9.,KkMm]+)\s*(?:lượt thích|likes|like|thích|reactions|người khác)""", Pattern.CASE_INSENSITIVE).matcher(block)
                        if (ariaLike.find()) {
                            reactionCount = parseCount(ariaLike.group(1) ?: "")
                        }
                    }

                    result.add(
                        FbPost(
                            postId = postId,
                            authorName = authorName.ifBlank { "Người dùng Facebook" },
                            authorId = authorId,
                            message = cleanMessage,
                            messageSnippet = createSnippet(cleanMessage),
                            commentCount = commentCount,
                            reactionCount = reactionCount,
                            ftEntIdentifier = postId
                        )
                    )

                    if (result.size >= targetCount) break
                }

                // 2. Dự phòng: Nếu HTML khối không bóc được bài, quét trực tiếp cấu trúc JSON trong script
                if (result.isEmpty()) {
                    val scriptMatcher = Pattern.compile("""<script[^>]*>([\s\S]*?)</script>""", Pattern.CASE_INSENSITIVE).matcher(html)
                    while (scriptMatcher.find()) {
                        val scriptContent = scriptMatcher.group(1) ?: ""
                        if (scriptContent.contains("feedback") || scriptContent.contains("post_id") || scriptContent.contains("top_level_post_id")) {
                            val jsonPostMatcher = Pattern.compile("""(?:"|&quot;)(?:post_id|top_level_post_id)(?:"|&quot;):\s*(?:"|&quot;)?(\d{10,25})(?:"|&quot;)?""").matcher(scriptContent)
                            while (jsonPostMatcher.find()) {
                                val postId = jsonPostMatcher.group(1) ?: continue
                                if (postId.isBlank() || postId == myUid || postId == targetActorId || !seenPostIds.add(postId)) continue

                                val startIdx = (jsonPostMatcher.start() - 500).coerceAtLeast(0)
                                val endIdx = (jsonPostMatcher.end() + 2500).coerceAtMost(scriptContent.length)
                                val window = scriptContent.substring(startIdx, endIdx)

                                var authorName = ""
                                val authorM = Pattern.compile("""(?:"|&quot;)name(?:"|&quot;):\s*(?:"|&quot;)([^"\\]+)(?:"|&quot;)""").matcher(window)
                                if (authorM.find()) authorName = authorM.group(1) ?: ""

                                var message = ""
                                val msgM = Pattern.compile("""(?:"|&quot;)text(?:"|&quot;):\s*(?:"|&quot;)([^"\\]+)(?:"|&quot;)""").matcher(window)
                                if (msgM.find()) message = msgM.group(1) ?: ""

                                var cmt = 0
                                val cmtM = Pattern.compile("""(?:"|&quot;)total_comment_count(?:"|&quot;):\s*(\d+)""").matcher(window)
                                if (cmtM.find()) cmt = cmtM.group(1)?.toIntOrNull() ?: 0

                                var reaction = 0
                                val reactM = Pattern.compile("""(?:"|&quot;)(?:reaction_count|count)(?:"|&quot;):\s*(\d+)""").matcher(window)
                                if (reactM.find()) reaction = reactM.group(1)?.toIntOrNull() ?: 0

                                result.add(
                                    FbPost(
                                        postId = postId,
                                        authorName = authorName.ifBlank { "Người dùng Facebook" },
                                        authorId = "",
                                        message = message,
                                        messageSnippet = createSnippet(message),
                                        commentCount = cmt,
                                        reactionCount = reaction,
                                        ftEntIdentifier = postId
                                    )
                                )
                                if (result.size >= targetCount) break
                            }
                        }
                        if (result.size >= targetCount) break
                    }
                }

                if (result.size >= targetCount) break

                // 3. Bóc link phân trang tiếp theo (Pagination Cursor)
                var nextLink: String? = null
                val nextPatterns = listOf(
                    Pattern.compile("""href="([^"]*(?:cursor=|aftercursorr=|section_id=|stories\.php|\/home\.php\?[^"]*cursor)[^"]*)"""", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("""href="([^"]*)"[^>]*>(?:Xem thêm bài viết|Xem thêm tin|Xem thêm|See more stories|See More)""", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("""(?:Xem thêm bài viết|Xem thêm tin|Xem thêm|See more stories|See More)[\s\S]*?href="([^"]*)"""", Pattern.CASE_INSENSITIVE)
                )
                for (np in nextPatterns) {
                    val m = np.matcher(html)
                    if (m.find()) {
                        val raw = m.group(1)?.replace("&amp;", "&")
                        if (!raw.isNullOrBlank() && (raw.contains("cursor") || raw.contains("stories.php") || raw.contains("home.php") || raw.contains("aftercursorr"))) {
                            nextLink = raw
                            break
                        }
                    }
                }

                currentUrl = when {
                    nextLink.isNullOrBlank() -> {
                        if (pagesLoaded == 1 && result.isEmpty() && baseUrlIndex < baseUrls.size - 1) {
                            baseUrlIndex++
                            baseUrls[baseUrlIndex]
                        } else {
                            null
                        }
                    }
                    nextLink.startsWith("http") -> nextLink
                    nextLink.startsWith("/") -> "https://m.facebook.com$nextLink"
                    else -> "https://m.facebook.com/$nextLink"
                }

            } catch (_: Exception) {
                break
            }
        }
        return result
    }
}
