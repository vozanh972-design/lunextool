package com.cayxu.app.automation.facebook.nuoi

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TokenExpiredException(val rawJsonError: String) : Exception(rawJsonError)

object FbFeedScraper {
    private const val TAG = "FbFeedScraper"
    private const val GRAPH_API_BASE = "https://graph.facebook.com/v21.0"
    const val KATANA_UA = "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"

    var lastDiagnosticLog: String = ""

    private fun logDiagnostic(msg: String, diag: StringBuilder? = null) {
        Log.d(TAG, msg)
        diag?.appendLine(msg)
    }

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
     * Thu thập danh sách bài viết trên Newsfeed 100% bằng Access Token (Chuẩn KaharaMod: Pages đã Like & Groups đã tham gia)
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
        val diag = StringBuilder()
        val collectedPosts = mutableListOf<FbPost>()
        val seenPostIds = mutableSetOf<String>()

        if (token.isBlank()) {
            throw IllegalArgumentException("Tài khoản chưa có Access Token (Vui lòng kiểm tra lại Token)")
        }

        logDiagnostic("=== BẮT ĐẦU CÀO BÀI VIẾT BẰNG TOKEN (CHUẨN KAHARAMOD) ===", diag)
        logDiagnostic("UID: $myUid | TargetCount: $targetCount", diag)

        // 1. NGUỒN 1: Lấy bài viết từ các Page đã Like / Follow (/me/likes)
        val pagePosts = fetchPostsFromLikedPages(client, token, myUid, myName, targetCount, seenPostIds, diag)
        collectedPosts.addAll(pagePosts)
        logDiagnostic("-> Tổng bài lấy được từ Pages đã Like: ${pagePosts.size}", diag)

        // 2. NGUỒN 2: Lấy bài viết từ các Group đã tham gia (/me/groups)
        if (collectedPosts.size < targetCount) {
            val remaining = targetCount - collectedPosts.size
            val groupPosts = fetchPostsFromJoinedGroups(client, token, myUid, myName, remaining, seenPostIds, diag)
            collectedPosts.addAll(groupPosts)
            logDiagnostic("-> Tổng bài lấy được từ Groups đã tham gia: ${groupPosts.size}", diag)
        }

        // 3. NGUỒN 3 (Dự phòng): Native Katana GraphQL (doc_id: 7112046835581177 / 4463426747065985)
        if (collectedPosts.isEmpty()) {
            logDiagnostic("-> Pages & Groups không có bài, thử Native Katana GraphQL...", diag)
            val nativePosts = fetchFeedViaNativeGraphQL(client, token, targetActorId, myUid, myName, maxPages, targetCount, seenPostIds, diag)
            collectedPosts.addAll(nativePosts)
            logDiagnostic("-> Tổng bài từ Native GraphQL Katana: ${nativePosts.size}", diag)
        }

        // 4. NGUỒN 4 (Dự phòng mở): Lấy bài viết từ cộng đồng mở nếu nick mới chưa like/join gì
        if (collectedPosts.isEmpty()) {
            logDiagnostic("-> Nick chưa có Pages/Groups, quét bài từ cộng đồng mở...", diag)
            val fallbackPosts = fetchPostsFromPublicPages(client, token, myUid, myName, targetCount, seenPostIds, diag)
            collectedPosts.addAll(fallbackPosts)
            logDiagnostic("-> Tổng bài từ cộng đồng mở: ${fallbackPosts.size}", diag)
        }

        lastDiagnosticLog = diag.toString()
        logDiagnostic("=== HOÀN TẤT THU THẬP: ${collectedPosts.size} bài viết ===", diag)
        return collectedPosts
    }

    /**
     * Nguồn 1: Lấy bài viết từ các Page mà tài khoản đã bấm Like / Follow
     * Endpoint: GET /v21.0/me/likes?fields=id,name&limit=15
     */
    private fun fetchPostsFromLikedPages(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val likesUrl = "$GRAPH_API_BASE/me/likes?fields=id,name&limit=15&access_token=$token"
        logDiagnostic("[GET Page Likes] URL: $likesUrl", diag)

        val req = Request.Builder()
            .url(likesUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        val responseBody = try {
            client.newCall(req).execute().use { res ->
                val code = res.code
                val body = res.body?.string().orEmpty()
                logDiagnostic("[Page Likes Response] HTTP $code | Length: ${body.length}", diag)
                if (code !in 200..299) {
                    logDiagnostic("[Page Likes Error] $body", diag)
                }
                body
            }
        } catch (e: Exception) {
            logDiagnostic("[Page Likes Exception] ${e.message}", diag)
            ""
        }

        if (responseBody.isBlank()) return result
        checkTokenError(responseBody)

        val root = try { JSONObject(responseBody) } catch (_: Exception) { return result }
        val dataArray = root.optJSONArray("data") ?: JSONArray()
        logDiagnostic("-> Đã tìm thấy ${dataArray.length()} Page đã Like", diag)

        for (i in 0 until dataArray.length()) {
            if (result.size >= targetCount) break
            val pageObj = dataArray.optJSONObject(i) ?: continue
            val pageId = pageObj.optString("id")
            val pageName = pageObj.optString("name")
            if (pageId.isBlank()) continue

            // Lấy 2-3 bài viết mới nhất của Page
            val postsUrl = "$GRAPH_API_BASE/$pageId/posts?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=3&access_token=$token"
            logDiagnostic("  [GET Page Posts] Page: [$pageName] ($pageId)", diag)

            val pReq = Request.Builder()
                .url(postsUrl)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .get()
                .build()

            try {
                client.newCall(pReq).execute().use { pRes ->
                    val pCode = pRes.code
                    val pBody = pRes.body?.string().orEmpty()
                    if (pCode in 200..299 && pBody.isNotBlank()) {
                        val pRoot = JSONObject(pBody)
                        val pData = pRoot.optJSONArray("data") ?: JSONArray()
                        logDiagnostic("  -> Page [$pageName] có ${pData.length()} bài viết", diag)

                        for (j in 0 until pData.length()) {
                            val item = pData.optJSONObject(j) ?: continue
                            val parsedPost = extractPostFromJson(item, pageName, pageId, myUid, myName, seenPostIds)
                            if (parsedPost != null) {
                                result.add(parsedPost)
                                if (result.size >= targetCount) break
                            }
                        }
                    } else {
                        logDiagnostic("  -> Page [$pageName] HTTP $pCode: ${pBody.take(120)}", diag)
                    }
                }
            } catch (e: Exception) {
                logDiagnostic("  -> Lỗi lấy bài Page [$pageName]: ${e.message}", diag)
            }
        }

        return result
    }

    /**
     * Nguồn 2: Lấy bài viết từ các Nhóm / Group đã tham gia
     * Endpoint: GET /v21.0/me/groups?fields=id,name&limit=15
     */
    private fun fetchPostsFromJoinedGroups(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val groupsUrl = "$GRAPH_API_BASE/me/groups?fields=id,name&limit=15&access_token=$token"
        logDiagnostic("[GET Groups] URL: $groupsUrl", diag)

        val req = Request.Builder()
            .url(groupsUrl)
            .header("Authorization", "OAuth $token")
            .header("User-Agent", KATANA_UA)
            .header("X-FB-Connection-Type", "WIFI")
            .get()
            .build()

        val responseBody = try {
            client.newCall(req).execute().use { res ->
                val code = res.code
                val body = res.body?.string().orEmpty()
                logDiagnostic("[Groups Response] HTTP $code | Length: ${body.length}", diag)
                if (code !in 200..299) {
                    logDiagnostic("[Groups Error] $body", diag)
                }
                body
            }
        } catch (e: Exception) {
            logDiagnostic("[Groups Exception] ${e.message}", diag)
            ""
        }

        if (responseBody.isBlank()) return result
        checkTokenError(responseBody)

        val root = try { JSONObject(responseBody) } catch (_: Exception) { return result }
        val dataArray = root.optJSONArray("data") ?: JSONArray()
        logDiagnostic("-> Đã tìm thấy ${dataArray.length()} Group đã tham gia", diag)

        for (i in 0 until dataArray.length()) {
            if (result.size >= targetCount) break
            val grpObj = dataArray.optJSONObject(i) ?: continue
            val groupId = grpObj.optString("id")
            val groupName = grpObj.optString("name")
            if (groupId.isBlank()) continue

            // Lấy 2-3 bài viết thảo luận trong Group
            val feedUrl = "$GRAPH_API_BASE/$groupId/feed?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=3&access_token=$token"
            logDiagnostic("  [GET Group Feed] Group: [$groupName] ($groupId)", diag)

            val gReq = Request.Builder()
                .url(feedUrl)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .get()
                .build()

            try {
                client.newCall(gReq).execute().use { gRes ->
                    val gCode = gRes.code
                    val gBody = gRes.body?.string().orEmpty()
                    if (gCode in 200..299 && gBody.isNotBlank()) {
                        val gRoot = JSONObject(gBody)
                        val gData = gRoot.optJSONArray("data") ?: JSONArray()
                        logDiagnostic("  -> Group [$groupName] có ${gData.length()} bài viết", diag)

                        for (j in 0 until gData.length()) {
                            val item = gData.optJSONObject(j) ?: continue
                            val parsedPost = extractPostFromJson(item, groupName, groupId, myUid, myName, seenPostIds)
                            if (parsedPost != null) {
                                result.add(parsedPost)
                                if (result.size >= targetCount) break
                            }
                        }
                    } else {
                        logDiagnostic("  -> Group [$groupName] HTTP $gCode: ${gBody.take(120)}", diag)
                    }
                }
            } catch (e: Exception) {
                logDiagnostic("  -> Lỗi lấy bài Group [$groupName]: ${e.message}", diag)
            }
        }

        return result
    }

    /**
     * Nguồn 3: Cào Newfeed qua Native Katana Android GraphQL (DocID HomeFeedQuery / FeedUnitsPaginatingQuery)
     */
    private fun fetchFeedViaNativeGraphQL(
        client: OkHttpClient,
        token: String,
        targetActorId: String?,
        myUid: String,
        myName: String,
        maxPages: Int,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
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

                logDiagnostic("  [POST Native GraphQL] doc_id: $docId | vars: $vars", diag)

                val req = Request.Builder()
                    .url("https://graph.facebook.com/graphql")
                    .header("Authorization", "OAuth $token")
                    .header("User-Agent", KATANA_UA)
                    .header("X-FB-Connection-Type", "WIFI")
                    .header("Accept-Encoding", "gzip, deflate")
                    .post(formBuilder.build())
                    .build()

                val responseBody = client.newCall(req).execute().use { res ->
                    val code = res.code
                    val body = res.body?.string().orEmpty()
                    logDiagnostic("  -> GraphQL Response HTTP $code | Length: ${body.length}", diag)
                    if (code !in 200..299) {
                        logDiagnostic("  -> GraphQL Error: ${body.take(150)}", diag)
                    }
                    body
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
                logDiagnostic("  -> GraphQL Exception: ${e.message}", diag)
                break
            }
        }

        return result
    }

    /**
     * Nguồn 4: Lấy bài viết từ các trang cộng đồng công khai nếu nick mới tinh chưa follow page nào
     */
    private fun fetchPostsFromPublicPages(
        client: OkHttpClient,
        token: String,
        myUid: String,
        myName: String,
        targetCount: Int,
        seenPostIds: MutableSet<String>,
        diag: StringBuilder
    ): List<FbPost> {
        val result = mutableListOf<FbPost>()
        val publicPages = listOf("thongtinchinhphu", "baothanhnien", "kenh14.vn")

        for (page in publicPages) {
            if (result.size >= targetCount) break
            val url = "$GRAPH_API_BASE/$page/posts?fields=id,from,message,story,created_time,reactions.summary(true),comments.summary(true)&limit=3&access_token=$token"
            logDiagnostic("  [Public Page] Quét $page", diag)

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "OAuth $token")
                .header("User-Agent", KATANA_UA)
                .header("X-FB-Connection-Type", "WIFI")
                .get()
                .build()

            try {
                client.newCall(req).execute().use { res ->
                    val code = res.code
                    val body = res.body?.string().orEmpty()
                    if (code in 200..299 && body.isNotBlank()) {
                        val root = JSONObject(body)
                        val data = root.optJSONArray("data") ?: JSONArray()
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i) ?: continue
                            val parsedPost = extractPostFromJson(item, page, "", myUid, myName, seenPostIds)
                            if (parsedPost != null) {
                                result.add(parsedPost)
                                if (result.size >= targetCount) break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }

    private fun extractPostFromJson(
        item: JSONObject,
        fallbackAuthorName: String,
        fallbackAuthorId: String,
        myUid: String,
        myName: String,
        seenPostIds: MutableSet<String>
    ): FbPost? {
        val rawPostId = item.optString("id")
        if (rawPostId.isBlank()) return null

        val realPostId = if (rawPostId.contains("_")) rawPostId.substringAfter("_") else rawPostId
        if (!seenPostIds.add(realPostId)) return null

        val from = item.optJSONObject("from")
        val authorName = from?.optString("name")?.ifBlank { fallbackAuthorName } ?: fallbackAuthorName
        val authorId = from?.optString("id")?.ifBlank { fallbackAuthorId } ?: fallbackAuthorId

        // Chặn bài của chính nick đang nuôi
        val isSelfPost = (myUid.isNotBlank() && (authorId == myUid || rawPostId.startsWith("${myUid}_") || realPostId == myUid)) ||
                (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                authorId == "me"

        if (isSelfPost) return null

        val message = item.optString("message").ifBlank { item.optString("story", "") }
        val reactionCount = item.optJSONObject("reactions")?.optJSONObject("summary")?.optInt("total_count")
            ?: item.optJSONObject("likes")?.optJSONObject("summary")?.optInt("total_count")
            ?: 0

        val commentCount = item.optJSONObject("comments")?.optJSONObject("summary")?.optInt("total_count")
            ?: 0

        val feedbackId = item.optJSONObject("feedback")?.optString("id")?.ifBlank { realPostId } ?: realPostId

        return FbPost(
            postId = realPostId,
            authorName = authorName.ifBlank { "Trang Facebook" },
            authorId = authorId,
            message = message,
            messageSnippet = createSnippet(message),
            commentCount = commentCount,
            reactionCount = reactionCount,
            ftEntIdentifier = feedbackId
        )
    }

    private data class ParsedFeedResult(
        val posts: List<FbPost>,
        val endCursor: String?
    )

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

        val root = try { JSONObject(rawBody) } catch (_: Exception) { return ParsedFeedResult(emptyList(), null) }
        val data = root.optJSONObject("data") ?: return ParsedFeedResult(emptyList(), null)

        val viewer = data.optJSONObject("viewer")
        val feed = viewer?.optJSONObject("news_feed")
            ?: viewer?.optJSONObject("home_feed")
            ?: data.optJSONObject("viewer_feed")
            ?: data.optJSONObject("feed")

        if (feed != null) {
            val pageInfo = feed.optJSONObject("page_info")
            endCursor = pageInfo?.optString("end_cursor")

            val edges = feed.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val rawPostId = node.optString("id")
                if (rawPostId.isBlank()) continue

                val realPostId = if (rawPostId.contains("_")) rawPostId.substringAfter("_") else rawPostId
                if (!seenPostIds.add(realPostId)) continue

                val actors = node.optJSONArray("actors")
                val firstActor = actors?.optJSONObject(0)
                val authorName = firstActor?.optString("name") ?: "Người dùng Facebook"
                val authorId = firstActor?.optString("id") ?: ""

                val isSelf = (myUid.isNotBlank() && (authorId == myUid || rawPostId.startsWith("${myUid}_") || realPostId.startsWith("${myUid}_"))) ||
                        (myName.isNotBlank() && authorName.equals(myName, ignoreCase = true)) ||
                        authorId == "me" ||
                        (!targetActorId.isNullOrBlank() && (authorId == targetActorId || rawPostId.startsWith("${targetActorId}_")))

                if (isSelf) continue

                val msgObj = node.optJSONObject("message")
                val messageText = msgObj?.optString("text")
                    ?: node.optString("story", "")

                val feedback = node.optJSONObject("feedback")
                val reactionCount = feedback?.optJSONObject("reactors")?.optInt("count")
                    ?: feedback?.optJSONObject("reaction_count")?.optInt("count")
                    ?: 0

                val commentCount = feedback?.optJSONObject("total_comment_count")?.optInt("count")
                    ?: feedback?.optInt("total_comment_count")
                    ?: feedback?.optJSONObject("comment_count")?.optInt("count")
                    ?: 0

                val feedbackId = feedback?.optString("id")?.ifBlank { realPostId } ?: realPostId

                posts.add(
                    FbPost(
                        postId = realPostId,
                        authorName = authorName.ifBlank { "Người dùng Facebook" },
                        authorId = authorId,
                        message = messageText,
                        messageSnippet = createSnippet(messageText),
                        commentCount = commentCount,
                        reactionCount = reactionCount,
                        ftEntIdentifier = feedbackId
                    )
                )

                if (posts.size >= targetCount) break
            }
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
     * Thả Reaction / Like chuẩn Katana Mutation (UFIFeedbackReactMutation doc_id: 5411782298894101)
     */
    fun sendReaction(
        client: OkHttpClient,
        token: String,
        post: FbPost,
        selectedReactionTypes: Set<String>,
        targetActorId: String? = null,
        myUid: String = ""
    ): Boolean {
        val rawTarget = selectedReactionTypes.randomOrNull() ?: "LIKE"
        val (reactionType, reactionNum) = when (rawTarget.uppercase()) {
            "1", "LIKE" -> "LIKE" to 1
            "2", "LOVE" -> "LOVE" to 2
            "16", "CARE" -> "CARE" to 16
            "4", "HAHA" -> "HAHA" to 4
            "3", "WOW" -> "WOW" to 3
            "7", "SAD" -> "SAD" to 7
            "8", "ANGRY" -> "ANGRY" to 8
            else -> "LIKE" to 1
        }

        val actor = if (!targetActorId.isNullOrBlank()) targetActorId else myUid
        val feedbackId = post.ftEntIdentifier.ifBlank { post.postId }

        // 1. Thử Mutation chuẩn Katana: UFIFeedbackReactMutation
        if (feedbackId.isNotBlank() && actor.isNotBlank()) {
            try {
                val inputObj = JSONObject().apply {
                    put("client_mutation_id", UUID.randomUUID().toString())
                    put("actor_id", actor)
                    put("feedback_id", feedbackId)
                    put("feedback_reaction", reactionNum)
                }
                val vars = JSONObject().apply {
                    put("input", inputObj)
                }

                val formBody = FormBody.Builder()
                    .add("doc_id", "5411782298894101")
                    .add("variables", vars.toString())
                    .add("access_token", token)
                    .add("method", "post")
                    .build()

                val req = Request.Builder()
                    .url("https://graph.facebook.com/graphql")
                    .header("Authorization", "OAuth $token")
                    .header("User-Agent", KATANA_UA)
                    .header("X-FB-Connection-Type", "WIFI")
                    .header("X-FB-Friendly-Name", "UFIFeedbackReactMutation")
                    .post(formBody)
                    .build()

                val success = client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    res.isSuccessful && !body.contains("\"errors\"")
                }
                if (success) return true
            } catch (_: Exception) {}
        }

        // 2. Fallback Graph API: POST /v21.0/{postId}/reactions
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
     * Viết Comment chuẩn Katana Mutation (CommentCreateMutation doc_id: 6739921102758190)
     */
    fun sendComment(
        client: OkHttpClient,
        token: String,
        post: FbPost,
        commentList: List<String>,
        targetActorId: String? = null,
        myUid: String = ""
    ): Boolean {
        val validComments = commentList.map { it.trim() }.filter { it.isNotBlank() }
        if (validComments.isEmpty()) return false
        val commentText = validComments.random()

        val actor = if (!targetActorId.isNullOrBlank()) targetActorId else myUid
        val feedbackId = post.ftEntIdentifier.ifBlank { post.postId }

        // 1. Thử Mutation chuẩn Katana: CommentCreateMutation
        if (feedbackId.isNotBlank() && actor.isNotBlank()) {
            try {
                val inputObj = JSONObject().apply {
                    put("client_mutation_id", UUID.randomUUID().toString())
                    put("actor_id", actor)
                    put("feedback_id", feedbackId)
                    put("message", JSONObject().put("text", commentText))
                }
                val vars = JSONObject().apply {
                    put("input", inputObj)
                }

                val formBody = FormBody.Builder()
                    .add("doc_id", "6739921102758190")
                    .add("variables", vars.toString())
                    .add("access_token", token)
                    .add("method", "post")
                    .build()

                val req = Request.Builder()
                    .url("https://graph.facebook.com/graphql")
                    .header("Authorization", "OAuth $token")
                    .header("User-Agent", KATANA_UA)
                    .header("X-FB-Connection-Type", "WIFI")
                    .header("X-FB-Friendly-Name", "CommentCreateMutation")
                    .post(formBody)
                    .build()

                val success = client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    res.isSuccessful && !body.contains("\"errors\"")
                }
                if (success) return true
            } catch (_: Exception) {}
        }

        // 2. Fallback Graph API: POST /v21.0/{postId}/comments
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
