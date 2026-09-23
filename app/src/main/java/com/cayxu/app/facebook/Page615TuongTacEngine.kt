package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Keep class Page615TuongTacEngine(
    private var pageToken: String? = null,
    private var pageId615: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        // Lazy cache: gọi FbVault 1 lần, tái sử dụng cho mọi request
        private val GRAPH_API by lazy { FbVault.graphApiUrl() }
        private val GRAPHQL   by lazy { FbVault.graphqlUrl()  }
        private val UA        by lazy { FbVault.userAgent()   }
        private fun graphApi() = GRAPH_API
        private fun graphql()  = GRAPHQL
        private fun ua()       = UA

        /**
         * Tự động trích xuất ID (Post ID, Reel ID, UID, Feedback ID) độc lập cho Page 615
         */
        fun extractId(rawTarget: String): String {
            var trimmed = rawTarget.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                return trimmed
            }
            if (trimmed.contains("login.php") && trimmed.contains("next=")) {
                try {
                    val nextUrl = trimmed.substringAfter("next=").substringBefore("&")
                    val decoded = java.net.URLDecoder.decode(nextUrl, "UTF-8")
                    if (decoded.isNotBlank()) trimmed = decoded
                } catch (_: Exception) {}
            }
            val patterns = listOf(
                Regex("""(?:\/posts\/|\/videos\/|\/reels\/|\/reel\/|\/stories\/|story_fbid=|fbid=)(\d+)"""),
                Regex("""(?:[?&](?:id|v)=)(\d+)"""),
                Regex("""facebook\.com\/(\d{10,})"""),
                Regex("""facebook\.com\/[^\/]+\/posts\/(\d+)"""),
                Regex("""(?:\/posts\/|\/reels\/|\/reel\/)(pfbid[A-Za-z0-9]+)""")
            )
            for (p in patterns) {
                val match = p.find(trimmed)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) return match
            }
            val cleanUrl = trimmed.trimEnd('/')
            return cleanUrl.substringAfterLast("/").substringBefore("?").ifBlank { trimmed }
        }
    }

    @Keep enum class ReactionType(val value: String, val graphqlCode: Int) {
        LIKE("LIKE", 1),
        LOVE("LOVE", 2),
        WOW("WOW", 3),
        HAHA("HAHA", 4),
        SAD("SAD", 7),
        ANGRY("ANGRY", 8),
        CARE("CARE", 16);

        companion object {
            fun fromString(str: String): ReactionType {
                val upper = str.uppercase().trim()
                return when {
                    upper.contains("CARE") || upper.contains("THUONG") || upper.contains("THƯƠNG") -> CARE
                    upper.contains("LOVE") || upper.contains("TYM") || upper.contains("TIM") || upper.contains("YÊU") || upper.contains("YEU") -> LOVE
                    upper.contains("HAHA") || upper.contains("CUOI") || upper.contains("CƯỜI") -> HAHA
                    upper.contains("WOW") || upper.contains("NGAC") || upper.contains("NGẠC") || upper.contains("BAT_NGO") || upper.contains("NGO") || upper.contains("NGỜ") -> WOW
                    upper.contains("SAD") || upper.contains("BUON") || upper.contains("BUỒN") -> SAD
                    upper.contains("ANGRY") || upper.contains("PHAN_NO") || upper.contains("PHẪN") || upper.contains("PHANNO") -> ANGRY
                    upper.contains("LIKE") || upper.contains("THICH") || upper.contains("THÍCH") -> LIKE
                    else -> LIKE
                }
            }
        }
    }

    @Keep data class InteractionResult(
        val isSuccess: Boolean,
        val targetId: String,
        val actionType: String,
        val resultId: String? = null,
        val message: String? = null,
        val rawResponse: String = ""
    )

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            builder.proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
        }
        builder.build()
    }

    fun setPageToken(token: String) { this.pageToken = token }
    fun setPageId615(id: String)    { this.pageId615 = id }

    private fun getCleanToken(overrideToken: String?): String =
        (overrideToken ?: pageToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()

    private fun resolveCanonicalTargetId(rawId: String): List<String> {
        val candidates = mutableListOf<String>()
        val clean = rawId.trim()
        val url = if (clean.startsWith("http://") || clean.startsWith("https://")) {
            clean
        } else {
            "https://www.facebook.com/$clean"
        }

        try {
            val noRedirectClient = httpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "curl/7.88.1")
                .build()

            noRedirectClient.newCall(headReq).execute().use { res ->
                val location = res.header("Location")
                if (!location.isNullOrBlank()) {
                    var targetLocation: String = location
                    if (targetLocation.contains("login.php") && targetLocation.contains("next=")) {
                        try {
                            val nextUrl = targetLocation.substringAfter("next=").substringBefore("&")
                            val decoded = java.net.URLDecoder.decode(nextUrl, "UTF-8")
                            if (!decoded.isNullOrBlank()) targetLocation = decoded
                        } catch (_: Exception) {}
                    }
                    val extracted = extractId(targetLocation)
                    if (extracted.isNotBlank() && extracted != clean && !candidates.contains(extracted)) {
                        candidates.add(extracted)
                    }
                    val fbid = Regex("""(?:story_fbid=|fbid=)(\d+)""").find(targetLocation)?.groupValues?.getOrNull(1)
                    val authorId = Regex("""(?:[?&]id=)(\d+)""").find(targetLocation)?.groupValues?.getOrNull(1)
                    if (!fbid.isNullOrBlank() && !authorId.isNullOrBlank()) {
                        val scoped = "${authorId}_$fbid"
                        if (!candidates.contains(scoped)) candidates.add(scoped)
                        if (!candidates.contains(fbid)) candidates.add(fbid)
                    }
                }
            }
        } catch (_: Exception) {}

        return candidates
    }

    fun reactPost(
        postId: String,
        reactionType: ReactionType = ReactionType.LIKE,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, postId, "REACT", null, "Page token required", "")

        val cleanTargetId = if (!postId.startsWith("http")) postId.trim() else extractId(postId)

        val ft = FbVault.fieldType()
        val fat = FbVault.fieldAccessToken()

        val formBody = FormBody.Builder()
            .add(ft, reactionType.value)
            .add(fat, token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$cleanTargetId${FbVault.pathReactions()}")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        var restErrorMsg = ""
        var restBody = ""

        try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                restBody = body
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                if (isOk) {
                    return InteractionResult(true, cleanTargetId, "REACT_${reactionType.value}", null, "Success", body)
                }

                // Fallback nếu gọi /reactions thất bại: thử gọi /likes nếu là LIKE
                if (reactionType == ReactionType.LIKE) {
                    val likeBody = FormBody.Builder()
                        .add(fat, token)
                        .build()
                    val likeReq = Request.Builder()
                        .url("${graphApi()}/$cleanTargetId${FbVault.pathLikes()}")
                        .post(likeBody)
                        .header("User-Agent", ua())
                        .build()
                    try {
                        httpClient.newCall(likeReq).execute().use { res2 ->
                            val body2 = res2.body?.string() ?: ""
                            val isOk2 = res2.isSuccessful && (body2.contains("\"success\":true") || !body2.contains("\"error\""))
                            if (isOk2) {
                                return InteractionResult(true, cleanTargetId, "REACT_LIKE_FALLBACK", null, "Success", body2)
                            }
                        }
                    } catch (_: Exception) {}
                }

                restErrorMsg = parseErrorMessage(body)
            }
        } catch (e: Exception) {
            restErrorMsg = e.message ?: "Lỗi kết nối mạng"
        }

        // Phân giải và xử lý nếu gặp lỗi (#12) singular status deprecated hoặc không hỗ trợ REST Graph API
        if (restErrorMsg.contains("deprecated") || restErrorMsg.contains("(#12)") || restErrorMsg.contains("does not exist") || restErrorMsg.contains("Unsupported post request")) {
            // Thử các ID canonical (pfbid, scoped {author}_{fbid}, Reel numeric ID) đã phân giải
            val canonicalList = resolveCanonicalTargetId(cleanTargetId)
            for (cId in canonicalList) {
                if (cId != cleanTargetId && cId.isNotBlank()) {
                    try {
                        val cReq = Request.Builder()
                            .url("${graphApi()}/$cId${FbVault.pathReactions()}")
                            .post(formBody)
                            .header("User-Agent", ua())
                            .build()
                        val cRes = httpClient.newCall(cReq).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                            if (isOk) InteractionResult(true, cId, "REACT_${reactionType.value}", null, "Success", body) else null
                        }
                        if (cRes != null) return cRes

                        if (reactionType == ReactionType.LIKE) {
                            val cLikeReq = Request.Builder()
                                .url("${graphApi()}/$cId${FbVault.pathLikes()}")
                                .post(FormBody.Builder().add(fat, token).build())
                                .header("User-Agent", ua())
                                .build()
                            val cLikeRes = httpClient.newCall(cLikeReq).execute().use { res ->
                                val body = res.body?.string() ?: ""
                                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                                if (isOk) InteractionResult(true, cId, "REACT_LIKE_FALLBACK", null, "Success", body) else null
                            }
                            if (cLikeRes != null) return cLikeRes
                        }
                    } catch (_: Exception) {}
                }
            }

            return InteractionResult(false, cleanTargetId, "REACT_${reactionType.value}", null, restErrorMsg.ifBlank { "Lỗi tương tác Facebook (#12/deprecated)" }, restBody)
        }

        return InteractionResult(false, cleanTargetId, "REACT_${reactionType.value}", null, restErrorMsg.ifBlank { "Lỗi tương tác Facebook" }, restBody)
    }

    fun commentPost(
        postId: String,
        message: String,
        attachmentId: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        if (message.isBlank()) return InteractionResult(false, postId, "COMMENT", null, "Nội dung comment trống", "")
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, postId, "COMMENT", null, "Page token required", "")

        val cleanTargetId = if (!postId.startsWith("http")) postId.trim() else FacebookTuongTacEngine.extractId(postId)

        val fm  = FbVault.fieldMessage()
        val fat = FbVault.fieldAccessToken()
        val fai = FbVault.fieldAttachmentId()

        val formBuilder = FormBody.Builder()
            .add(fm, message)
            .add(fat, token)

        if (!attachmentId.isNullOrEmpty()) formBuilder.add(fai, attachmentId)

        val request = Request.Builder()
            .url("${graphApi()}/$cleanTargetId${FbVault.pathComments()}")
            .post(formBuilder.build())
            .header("User-Agent", ua())
            .build()

        var lastErrorMsg = ""
        var lastBody = ""

        try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                lastBody = body
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                if (isOk) {
                    return InteractionResult(true, cleanTargetId, "COMMENT", commentId, "Success", body)
                }

                lastErrorMsg = parseErrorMessage(body)
            }
        } catch (e: Exception) {
            lastErrorMsg = e.message ?: "Lỗi kết nối mạng"
        }

        // Nếu gặp lỗi (#12) singular statuses API is deprecated hoặc không tìm thấy:
        if (lastErrorMsg.contains("deprecated") || lastErrorMsg.contains("(#12)") || lastErrorMsg.contains("does not exist") || lastErrorMsg.contains("missing permissions")) {
            // 2. Phân giải Node ID đầy đủ {author_id}_{post_id} qua Graph API
            try {
                val resolveReq = Request.Builder()
                    .url("${graphApi()}/$cleanTargetId?fields=id&access_token=$token")
                    .get()
                    .header("User-Agent", ua())
                    .build()
                val scopedId = httpClient.newCall(resolveReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    json?.optString("id", null)
                }
                if (!scopedId.isNullOrBlank() && scopedId != cleanTargetId && scopedId.contains("_")) {
                    val scopedReq = Request.Builder()
                        .url("${graphApi()}/$scopedId${FbVault.pathComments()}")
                        .post(formBuilder.build())
                        .header("User-Agent", ua())
                        .build()
                    val scopedRes = httpClient.newCall(scopedReq).execute().use { res ->
                        val body = res.body?.string() ?: ""
                        val json = try { JSONObject(body) } catch (_: Exception) { null }
                        val commentId = json?.optString("id", null)
                        val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                        if (isOk) {
                            InteractionResult(true, scopedId, "COMMENT", commentId, "Success", body)
                        } else null
                    }
                    if (scopedRes != null) return scopedRes
                }
            } catch (_: Exception) {}

            // 3. Thử gọi unversioned Graph API endpoint (https://graph.facebook.com/{id}/comments)
            // Endpoint unversioned không áp dụng hạn chế v2.4+ cho status
            try {
                val unversionedReq = Request.Builder()
                    .url("https://graph.facebook.com/$cleanTargetId${FbVault.pathComments()}")
                    .post(formBuilder.build())
                    .header("User-Agent", ua())
                    .build()
                val unversionedRes = httpClient.newCall(unversionedReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val commentId = json?.optString("id", null)
                    val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                    if (isOk) {
                        InteractionResult(true, cleanTargetId, "COMMENT", commentId, "Success", body)
                    } else null
                }
                if (unversionedRes != null) return unversionedRes
            } catch (_: Exception) {}

            // 4. Thử ghép ID Page nếu là bài viết trên trang
            val pId = pageId615
            if (!pId.isNullOrBlank() && !cleanTargetId.contains("_")) {
                try {
                    val pageScopedReq = Request.Builder()
                        .url("${graphApi()}/${pId}_$cleanTargetId${FbVault.pathComments()}")
                        .post(formBuilder.build())
                        .header("User-Agent", ua())
                        .build()
                    val pageRes = httpClient.newCall(pageScopedReq).execute().use { res ->
                        val body = res.body?.string() ?: ""
                        val json = try { JSONObject(body) } catch (_: Exception) { null }
                        val commentId = json?.optString("id", null)
                        val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                        if (isOk) {
                            InteractionResult(true, "${pId}_$cleanTargetId", "COMMENT", commentId, "Success", body)
                        } else null
                    }
                    if (pageRes != null) return pageRes
                } catch (_: Exception) {}
            }
        }

        return InteractionResult(false, cleanTargetId, "COMMENT", null, lastErrorMsg.ifBlank { "Bình luận không thành công" }, lastBody)
    }

    private fun parseErrorMessage(body: String): String {
        try {
            val json = JSONObject(body)
            if (json.has("errors")) {
                val errs = json.optJSONArray("errors")
                if (errs != null && errs.length() > 0) {
                    val first = errs.optJSONObject(0)
                    val msg = first?.optString("message")
                    val summary = first?.optString("summary")
                    if (!msg.isNullOrBlank()) return if (!summary.isNullOrBlank()) "$summary: $msg" else msg
                }
            }
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                val code = err?.optInt("code", 0) ?: 0
                val subcode = err?.optInt("error_subcode", 0) ?: 0
                val title = err?.optString("error_user_title")?.takeIf { it.isNotBlank() }
                val userMsg = err?.optString("error_user_msg")?.takeIf { it.isNotBlank() }
                if (!title.isNullOrBlank() || !userMsg.isNullOrBlank()) {
                    return listOfNotNull(title, userMsg).joinToString(": ")
                }
                if (code == 368 || subcode == 1390008) {
                    return "Tài khoản bị Facebook giới hạn tính năng tạm thời (Spam Block - Mã 368)"
                }
                val msg = err?.optString("message")
                if (!msg.isNullOrBlank()) return msg
                val errStr = json.optString("error")
                if (errStr.isNotBlank()) return errStr
            }
        } catch (_: Exception) {}
        return if (body.isNotBlank()) body.take(300) else "Phản hồi lỗi không xác định từ Facebook"
    }

    fun replyComment(
        parentCommentId: String,
        message: String,
        attachmentId: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, parentCommentId, "REPLY_COMMENT", null, "Page token required", "")

        val fm  = FbVault.fieldMessage()
        val fat = FbVault.fieldAccessToken()
        val fai = FbVault.fieldAttachmentId()

        val formBuilder = FormBody.Builder()
            .add(fm, message)
            .add(fat, token)

        if (!attachmentId.isNullOrEmpty()) formBuilder.add(fai, attachmentId)

        val request = Request.Builder()
            .url("${graphApi()}/$parentCommentId${FbVault.pathComments()}")
            .post(formBuilder.build())
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val commentId = json?.optString("id", null)
                val isOk = res.isSuccessful && !commentId.isNullOrEmpty()
                InteractionResult(isOk, parentCommentId, "REPLY_COMMENT", commentId, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, parentCommentId, "REPLY_COMMENT", null, e.message, "")
        }
    }

    fun followTarget(
        targetId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetId, "FOLLOW", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$targetId${FbVault.pathSubscribers()}")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetId, "FOLLOW", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetId, "FOLLOW", null, e.message, "")
        }
    }

    fun unfollowTarget(
        targetId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetId, "UNFOLLOW", null, "Page token required", "")

        val request = Request.Builder()
            .url("${graphApi()}/$targetId${FbVault.pathSubscribers()}?${FbVault.fieldAccessToken()}=$token")
            .delete()
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetId, "UNFOLLOW", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetId, "UNFOLLOW", null, e.message, "")
        }
    }

    fun likeOtherPage(
        targetPageId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPageId, "LIKE_PAGE", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$targetPageId${FbVault.pathLikes()}")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetPageId, "LIKE_PAGE", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetPageId, "LIKE_PAGE", null, e.message, "")
        }
    }

    fun unlikeOtherPage(
        targetPageId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPageId, "UNLIKE_PAGE", null, "Page token required", "")

        val request = Request.Builder()
            .url("${graphApi()}/$targetPageId${FbVault.pathLikes()}?${FbVault.fieldAccessToken()}=$token")
            .delete()
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetPageId, "UNLIKE_PAGE", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetPageId, "UNLIKE_PAGE", null, e.message, "")
        }
    }

    fun joinGroup(
        groupId: String,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, groupId, "JOIN_GROUP", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$groupId/members")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, groupId, "JOIN_GROUP", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, groupId, "JOIN_GROUP", null, e.message, "")
        }
    }

    fun reviewOtherPage(
        targetPageId: String,
        reviewText: String,
        recommendationType: String = "positive",
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPageId, "REVIEW_PAGE", null, "Page token required", "")

        val formBody = FormBody.Builder()
            .add("recommendation_type", recommendationType)
            .add("review_text", reviewText)
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$targetPageId/ratings")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                InteractionResult(isOk, targetPageId, "REVIEW_PAGE", null, if (isOk) "Success" else body, body)
            }
        } catch (e: Exception) {
            InteractionResult(false, targetPageId, "REVIEW_PAGE", null, e.message, "")
        }
    }

    fun sharePost(
        targetPostId: String,
        message: String? = null,
        overrideToken: String? = null
    ): InteractionResult {
        val token = getCleanToken(overrideToken)
        if (token.isEmpty()) return InteractionResult(false, targetPostId, "SHARE", null, "Page token required", "")

        val cleanId = if (!targetPostId.startsWith("http")) targetPostId.trim() else FacebookTuongTacEngine.extractId(targetPostId)
        val pId = pageId615?.takeIf { it.isNotBlank() } ?: "me"

        val formBuilder = FormBody.Builder()
            .add("link", "https://www.facebook.com/$cleanId")
            .add(FbVault.fieldAccessToken(), token)
        if (!message.isNullOrBlank()) {
            formBuilder.add(FbVault.fieldMessage(), message)
        }

        val request = Request.Builder()
            .url("${graphApi()}/$pId/feed")
            .post(formBuilder.build())
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"id\":") || body.contains("\"success\":true") || !body.contains("\"error\""))
                if (isOk) {
                    val shareId = try { JSONObject(body).optString("id", null) } catch (_: Exception) { null }
                    InteractionResult(true, cleanId, "SHARE", shareId, "Success", body)
                } else {
                    val fb2 = FormBody.Builder().add(FbVault.fieldAccessToken(), token)
                    if (!message.isNullOrBlank()) fb2.add(FbVault.fieldMessage(), message)
                    val req2 = Request.Builder()
                        .url("${graphApi()}/$cleanId/sharedposts")
                        .post(fb2.build())
                        .header("User-Agent", ua())
                        .build()
                    val res2 = httpClient.newCall(req2).execute().use { r2 ->
                        val b2 = r2.body?.string() ?: ""
                        val ok2 = r2.isSuccessful && (b2.contains("\"id\":") || !b2.contains("\"error\""))
                        InteractionResult(ok2, cleanId, "SHARE", null, if (ok2) "Success" else parseErrorMessage(b2), b2)
                    }
                    if (res2.isSuccess) res2 else InteractionResult(false, cleanId, "SHARE", null, parseErrorMessage(body), body)
                }
            }
        } catch (e: Exception) {
            InteractionResult(false, cleanId, "SHARE", null, e.message ?: "Lỗi mạng", "")
        }
    }
}
