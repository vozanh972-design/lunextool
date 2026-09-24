package com.cayxu.app.facebook

import android.util.Base64
import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * =========================================================================================
 * BỘ ENGINE TƯƠNG TÁC CẢM XÚC DÀNH CHO PAGE 615 (PROFILE PLUS / NEW PAGES EXPERIENCE)
 * Trích xuất 100% từ cấu trúc Facebook Katana v548 (KaharaMod)
 *
 * ÁP DỤNG CƠ CHẾ DUY NHẤT CHUẨN XÁC VĨNH VIỄN:
 * 1. Base64 Feedback Node ID: Base64.encode("feedback:" + targetId)
 * 2. POST https://graph.facebook.com/v21.0/{base64FeedbackId}/reactions
 * 3. Page Access Token của Page 615
 * =========================================================================================
 */
@Keep
class Page615ReactionEngine(
    private var pageToken: String? = null,
    private var pageId615: String? = null,
    private var userToken: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPH_API_BASE = "https://graph.facebook.com/v21.0"
        const val GRAPHQL_ENDPOINT = "https://graph.facebook.com/graphql"

        // User-Agent chuẩn Facebook Katana v548 (Android) từ Kahara
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"

        /**
         * Quy đổi targetId sang Base64 Feedback Node ID chuẩn:
         * Ví dụ: 1488740023061437 -> "feedback:1488740023061437" -> "ZmVlZGJhY2s6MTQ4ODc0MDAyMzA2MTQzNw=="
         */
        fun toBase64FeedbackId(targetId: String): String {
            val clean = targetId.trim()
            if (clean.startsWith("ZmVlZGJhY2s6")) {
                return clean
            }
            val feedbackString = if (clean.startsWith("feedback:")) clean else "feedback:$clean"
            return Base64.encodeToString(
                feedbackString.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
        }
    }

    /**
     * Bảng ánh xạ 7 loại cảm xúc chuẩn Facebook:
     * - restValue: Giá trị chuỗi cho Graph API REST
     * - graphqlCode: Mã số nguyên (Integer) cho Katana GraphQL
     */
    @Keep
    enum class ReactionType(val restValue: String, val graphqlCode: Int, val labelVi: String) {
        LIKE("LIKE", 1, "Thích"),
        LOVE("LOVE", 2, "Yêu thích / Thả tim"),
        WOW("WOW", 3, "Bất ngờ"),
        HAHA("HAHA", 4, "Haha"),
        SAD("SAD", 7, "Buồn"),
        ANGRY("ANGRY", 8, "Phẫn nộ"),
        CARE("CARE", 16, "Thương thương");

        companion object {
            fun fromString(name: String): ReactionType {
                val upper = name.trim().uppercase()
                return values().find { it.name == upper || it.restValue == upper } ?: LIKE
            }
        }
    }

    @Keep
    data class ReactionResult(
        val isSuccess: Boolean,
        val targetId: String,
        val reaction: ReactionType,
        val methodUsed: String,
        val message: String,
        val rawResponse: String = ""
    )

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            builder.proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
        }
        builder.build()
    }

    fun setPageToken(token: String) { this.pageToken = cleanToken(token) }
    fun setPageId615(id: String) { this.pageId615 = id.trim() }
    fun setUserToken(token: String) { this.userToken = cleanToken(token) }

    private fun cleanToken(token: String?): String {
        return (token ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
    }

    /**
     * TỰ ĐỘNG BÓC TÁCH PAGE ACCESS TOKEN TỪ USER TOKEN
     * Giải quyết tận gốc lỗi #200 Permissions error khi người dùng chỉ có User Token mẹ (EAAB/EAAG).
     */
    fun extractPageTokenFromUserToken(targetPageId615: String, uToken: String? = null): String? {
        val token = cleanToken(uToken ?: userToken)
        if (token.isEmpty()) return null

        val request = Request.Builder()
            .url("$GRAPH_API_BASE/me/accounts?fields=id,name,access_token&limit=100&access_token=$token")
            .get()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val pageObj = data.getJSONObject(i)
                    if (pageObj.optString("id") == targetPageId615) {
                        val extracted = pageObj.optString("access_token")
                        if (extracted.isNotEmpty()) {
                            this.pageToken = extracted
                            this.pageId615 = targetPageId615
                            return extracted
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Log error if needed
        }
        return null
    }

    /**
     * TỰ ĐỘNG QUY ĐỔI TARGET ID SANG FEEDBACK ID CHUẨN
     * Giải quyết triệt để lỗi #12 khi Target ID là Reels, Video, Story hoặc Share post.
     */
    fun resolveTargetToFeedbackId(targetId: String, token: String): String {
        if (targetId.startsWith("feedback:") || targetId.startsWith("ZmVlZGJhY2s6")) {
            return targetId
        }

        val request = Request.Builder()
            .url("$GRAPH_API_BASE/$targetId?fields=id,feedback.fields(id)&access_token=$token")
            .get()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val feedbackObj = json.optJSONObject("feedback")
                val feedbackId = feedbackObj?.optString("id")
                if (!feedbackId.isNullOrEmpty()) feedbackId else targetId
            }
        } catch (_: Exception) {
            targetId
        }
    }

    /**
     * HÀM THỰC THI TƯƠNG TÁC CẢM XÚC CHUẨN XÁC VĨNH VIỄN
     * 1. Nhận targetId
     * 2. Quy đổi targetId sang Feedback Node ID bằng Base64: "feedback:" + targetId -> Base64
     * 3. Gửi request REST Graph API v21.0: POST https://graph.facebook.com/v21.0/{base64FeedbackId}/reactions
     *    Body: type={REACTION}, access_token={PAGE_ACCESS_TOKEN}
     */
    fun react(
        targetId: String,
        reaction: ReactionType = ReactionType.LOVE,
        customPageToken: String? = null,
        customUserToken: String? = null,
        customPageId615: String? = null,
        forceMethod: String = "auto"
    ): ReactionResult {
        val pToken = cleanToken(customPageToken ?: pageToken)
        val uToken = cleanToken(customUserToken ?: userToken)
        val pageId = customPageId615 ?: pageId615

        // BƯỚC 1: Xác định Page Access Token của Page 615
        var activePageToken = pToken
        if (activePageToken.isEmpty() && uToken.isNotEmpty() && !pageId.isNullOrEmpty()) {
            activePageToken = extractPageTokenFromUserToken(pageId, uToken) ?: ""
        }

        if (activePageToken.isEmpty()) {
            return ReactionResult(
                isSuccess = false,
                targetId = targetId,
                reaction = reaction,
                methodUsed = "REST_FEEDBACK_NODE",
                message = "Thiếu Page Access Token của Page 615 để thực hiện reaction"
            )
        }

        // BƯỚC 2 & 3: Quy đổi sang Base64 Feedback Node ID và gọi REST Graph API v21.0
        val base64FeedbackId = toBase64FeedbackId(targetId)
        val result = executeRestReaction(base64FeedbackId, targetId, reaction, activePageToken)

        // Nếu token bị hết hạn, thử refresh lại Page Token 1 lần từ User Token mẹ /me/accounts
        if (!result.isSuccess && (result.rawResponse.contains("Error validating access token") || result.rawResponse.contains("\"code\":190"))) {
            if (uToken.isNotEmpty() && !pageId.isNullOrEmpty()) {
                val refreshed = extractPageTokenFromUserToken(pageId, uToken)
                if (!refreshed.isNullOrEmpty() && refreshed != activePageToken) {
                    return executeRestReaction(base64FeedbackId, targetId, reaction, refreshed)
                }
            }
        }

        return result
    }

    /**
     * Gửi request REST Graph API v21.0 vào đúng Feedback Node:
     * Endpoint: POST https://graph.facebook.com/v21.0/{base64FeedbackId}/reactions
     * Form Body: type={LIKE|LOVE|CARE|HAHA|WOW|SAD|ANGRY}&access_token={PAGE_ACCESS_TOKEN}
     */
    private fun executeRestReaction(
        base64FeedbackId: String,
        originalTargetId: String,
        reaction: ReactionType,
        token: String
    ): ReactionResult {
        val formBody = FormBody.Builder()
            .add("type", reaction.restValue)
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_BASE/$base64FeedbackId/reactions")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || body.contains("\"success\": true") || body.contains("\"id\":"))
                ReactionResult(
                    isSuccess = isOk,
                    targetId = originalTargetId,
                    reaction = reaction,
                    methodUsed = "REST_FEEDBACK_NODE",
                    message = if (isOk) "Thành công (REST Feedback Node)" else "Thất bại: $body",
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            ReactionResult(false, originalTargetId, reaction, "REST_FEEDBACK_NODE", e.message ?: "Exception", "")
        }
    }
}

