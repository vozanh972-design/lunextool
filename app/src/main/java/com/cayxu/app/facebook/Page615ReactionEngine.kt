package com.cayxu.app.facebook

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
 * CƠ CHẾ DUY NHẤT CHUẨN XÁC 100%:
 * 1. Bóc tách chính xác 7 loại cảm xúc: LIKE, LOVE, CARE, HAHA, WOW, SAD, ANGRY
 * 2. Gửi duy nhất 1 lệnh HTTP POST form-data lên Graph API:
 *    POST https://graph.facebook.com/{TARGET_ID}/reactions
 *    Header: User-Agent Katana v548
 *    Body: type={REACTION_TYPE}&access_token={PAGE_TOKEN}
 * 3. Tự động giải quyết lỗi #12 khi gặp Status ID đơn lẻ
 * 4. Page Access Token của Page 615
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
        const val GRAPH_BASE = "https://graph.facebook.com"

        // User-Agent chuẩn Facebook Katana v548 (Android) từ Kahara
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"

        /**
         * Bóc tách chính xác 7 loại cảm xúc từ mọi định dạng chuỗi của XSMM
         * Ví dụ: "facebook_reaction (CARE)" -> "CARE", "facebook_reaction (LOVE)" -> "LOVE"
         */
        fun parseReactionType(rawInput: String): String {
            val upper = rawInput.uppercase()
            return when {
                upper.contains("CARE") || upper.contains("THƯƠNG") || upper.contains("THUONG") || upper == "16" -> "CARE"
                upper.contains("LOVE") || upper.contains("TIM") || upper == "2" -> "LOVE"
                upper.contains("LIKE") || upper.contains("THÍCH") || upper.contains("THICH") || upper == "1" -> "LIKE"
                upper.contains("HAHA") || upper.contains("CƯỜI") || upper.contains("CUOI") || upper == "4" -> "HAHA"
                upper.contains("WOW") || upper.contains("BẤT NGỜ") || upper == "3" -> "WOW"
                upper.contains("SAD") || upper.contains("BUỒN") || upper.contains("BUON") || upper == "7" -> "SAD"
                upper.contains("ANGRY") || upper.contains("PHẪN NỘ") || upper.contains("PHAN_NO") || upper == "8" -> "ANGRY"
                else -> "LOVE"
            }
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
                val parsed = parseReactionType(name)
                return values().find { it.name == parsed || it.restValue == parsed } ?: LOVE
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
     * HÀM THỰC THI TƯƠNG TÁC CẢM XÚC CHUẨN XÁC 100%
     * 1. Nhận targetId (Numeric ID thô, không Base64)
     * 2. POST https://graph.facebook.com/{TARGET_ID}/reactions với type và access_token của Page 615
     * 3. Tự động bắt lỗi #12 (singular statuses API is deprecated):
     *    -> GET https://graph.facebook.com/{TARGET_ID}?fields=from&access_token={PAGE_TOKEN}
     *    -> Trích xuất owner_id = from.id
     *    -> POST https://graph.facebook.com/{owner_id}_{TARGET_ID}/reactions
     */
    fun react(
        targetId: String,
        reaction: ReactionType = ReactionType.LOVE,
        customPageToken: String? = null,
        customUserToken: String? = null,
        customPageId615: String? = null,
        forceMethod: String = "auto"
    ): ReactionResult {
        val cleanId = if (targetId.startsWith("http://") || targetId.startsWith("https://")) {
            Page615TuongTacEngine.extractId(targetId)
        } else {
            targetId.trim().trimEnd('/')
        }
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
                targetId = cleanId,
                reaction = reaction,
                methodUsed = "REST_DIRECT_NUMERIC",
                message = "Thiếu Page Access Token của Page 615 để thực hiện reaction"
            )
        }

        var result = executeReaction(cleanId, reaction, activePageToken)

        // Nếu token bị hết hạn (#190), thử refresh lại Page Token 1 lần từ User Token mẹ /me/accounts
        if (!result.isSuccess && (result.rawResponse.contains("Error validating access token") || result.rawResponse.contains("\"code\":190") || result.rawResponse.contains("\"code\": 190"))) {
            if (uToken.isNotEmpty() && !pageId.isNullOrEmpty()) {
                val refreshed = extractPageTokenFromUserToken(pageId, uToken)
                if (!refreshed.isNullOrEmpty() && refreshed != activePageToken) {
                    activePageToken = refreshed
                    result = executeReaction(cleanId, reaction, activePageToken)
                }
            }
        }

        return result
    }

    /**
     * BẮN DUY NHẤT 1 LỆNH HTTP POST THẲNG VÀO GRAPH API:
     * POST https://graph.facebook.com/{target_id}/reactions
     * Body: type={REACTION_TYPE}&access_token={PAGE_TOKEN}
     * Header: User-Agent Katana v548
     */
    private fun executeReaction(
        cleanId: String,
        reaction: ReactionType,
        token: String
    ): ReactionResult {
        val cleanToken = cleanToken(token)

        val formBody = FormBody.Builder()
            .add("type", reaction.restValue)
            .add("access_token", cleanToken)
            .build()

        val request = Request.Builder()
            .url("https://graph.facebook.com/$cleanId/reactions")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && (body.contains("\"success\":true") || body.contains("\"success\": true") || body.contains("\"id\":"))) {
                return ReactionResult(
                    isSuccess = true,
                    targetId = cleanId,
                    reaction = reaction,
                    methodUsed = "GRAPH_API_POST_REACTIONS",
                    message = "Thành công",
                    rawResponse = body
                )
            }

            // Tự động giải quyết lỗi #12 khi gặp Status ID đơn lẻ
            if (body.contains("singular statuses API is deprecated") || body.contains("\"code\":12") || body.contains("\"code\": 12")) {
                val ownerReq = Request.Builder()
                    .url("https://graph.facebook.com/$cleanId?fields=from&access_token=$cleanToken")
                    .get()
                    .header("User-Agent", KATANA_USER_AGENT)
                    .build()

                val ownerRes = httpClient.newCall(ownerReq).execute()
                val ownerBody = ownerRes.body?.string() ?: ""
                val ownerId = JSONObject(ownerBody).optJSONObject("from")?.optString("id")

                if (!ownerId.isNullOrEmpty()) {
                    val fullPostId = "${ownerId}_$cleanId"
                    val retryBody = FormBody.Builder()
                        .add("type", reaction.restValue)
                        .add("access_token", cleanToken)
                        .build()

                    val retryReq = Request.Builder()
                        .url("https://graph.facebook.com/$fullPostId/reactions")
                        .post(retryBody)
                        .header("User-Agent", KATANA_USER_AGENT)
                        .build()

                    val retryRes = httpClient.newCall(retryReq).execute()
                    val retryBodyStr = retryRes.body?.string() ?: ""
                    val isRetryOk = retryRes.isSuccessful && (retryBodyStr.contains("\"success\":true") || retryBodyStr.contains("\"success\": true") || retryBodyStr.contains("\"id\":"))

                    return ReactionResult(
                        isSuccess = isRetryOk,
                        targetId = fullPostId,
                        reaction = reaction,
                        methodUsed = "PAGE_615_RESOLVED_STATUS",
                        message = if (isRetryOk) "Thành công" else "Thất bại: $retryBodyStr",
                        rawResponse = retryBodyStr
                    )
                }
            }

            ReactionResult(
                isSuccess = false,
                targetId = cleanId,
                reaction = reaction,
                methodUsed = "GRAPH_API_POST_REACTIONS",
                message = "Thất bại: $body",
                rawResponse = body
            )
        } catch (e: Exception) {
            ReactionResult(
                isSuccess = false,
                targetId = cleanId,
                reaction = reaction,
                methodUsed = "GRAPH_API_POST_REACTIONS",
                message = e.message ?: "Exception",
                rawResponse = ""
            )
        }
    }
}

