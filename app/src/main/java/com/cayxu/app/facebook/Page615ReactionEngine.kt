package com.cayxu.app.facebook

import android.util.Base64
import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * =========================================================================================
 * BỘ ENGINE TƯƠNG TÁC CẢM XÚC DÀNH CHO PAGE 615 / PROFILE (CHUẨN BUMX1.PY)
 * Cơ chế UFIReactionMutation với client_doc_id 2857784093518205785115255697
 * =========================================================================================
 */
@Keep
object Page615ReactionWorker {

    val REACTION_IDS = mapOf(
        "LIKE"  to "1635855486666999",
        "LOVE"  to "1678524932434102",
        "CARE"  to "2229796038974261",
        "HAHA"  to "115940658764963",
        "WOW"   to "478547315650144",
        "SAD"   to "908563459236466",
        "ANGRY" to "444813342392137"
    )

    const val REACTION_DOC_ID = "2857784093518205785115255697"
    const val GRAPHQL_URL = "https://graph.facebook.com/graphql"
    const val FB_UA = "[FBAN/FB4A;FBAV/552.1.0.45.68;FBBV/906187048;FBDM/{density=3.0,width=1080,height=1920};FBLC/en_US;FBRV/0;FBCR/MobiFone;FBMF/Samsung;FBBD/Samsung;FBPN/com.facebook.katana;FBDV/Samsung C3560;FBSV/9;FBOP/1;FBCA/x86_64:arm64-v8a;]"

    fun parseObjectId(objectId: String): Pair<String, String> {
        var s = objectId.trim()
        if (s.startsWith("http")) {
            s = s.substringAfter("facebook.com/").substringBefore("?")
        }
        s = s.trimEnd('/')
        if (s.contains("/posts/")) {
            val parts = s.split("/posts/")
            return Pair(parts[0], parts[1].substringBefore("/"))
        }
        if (s.contains("/")) {
            val parts = s.split("/")
            if (parts[0].all { it.isDigit() }) return Pair(parts[0], parts.last())
        }
        if (s.contains("_")) {
            val idx = s.indexOf('_')
            return Pair(s.substring(0, idx), s.substring(idx + 1))
        }
        return Pair("", s)
    }

    fun buildFeedbackId(objectId: String): Triple<String, String, String> {
        val (owner, fbid) = parseObjectId(objectId)
        val tok = if (owner.isNotEmpty()) "${owner}_$fbid" else fbid
        val b64 = Base64.encodeToString("feedback:$tok".toByteArray(), Base64.NO_WRAP)
        return Triple(b64, owner, fbid)
    }

    fun isReactionSuccess(json: JSONObject): Boolean {
        if (json.has("errors")) return false
        val data = json.optJSONObject("data") ?: return false
        val ufi = data.optJSONObject("ufi_reaction") ?: return data.length() > 0
        if (ufi.has("error") || ufi.has("error_message")) return false
        if (ufi.has("feedback_reaction") || ufi.has("viewer_feedback_reaction")) return true
        val fb = ufi.optJSONObject("feedback")
        if (fb != null && (fb.has("viewer_feedback_reaction") || fb.has("feedback_reaction"))) return true
        return ufi.has("id")
    }

    fun executeReaction(
        objectId: String,
        reactionType: String,
        token: String,
        pageUid: String?
    ): Boolean {
        val detail = executeReactionDetail(objectId, reactionType, token, pageUid)
        return detail.isSuccess
    }

    data class ReactionExecutionResult(
        val isSuccess: Boolean,
        val message: String,
        val rawResponse: String = ""
    )

    fun executeReactionDetail(
        objectId: String,
        reactionType: String,
        token: String,
        pageUid: String?,
        proxyHost: String? = null,
        proxyPort: Int? = null,
        proxyType: Proxy.Type = Proxy.Type.HTTP
    ): ReactionExecutionResult {
        val cleanToken = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val (feedbackId, owner, fbid) = buildFeedbackId(objectId)
        val reactKey = Page615ReactionEngine.parseReactionType(reactionType)
        val reactId = REACTION_IDS[reactKey] ?: REACTION_IDS["LIKE"]!!

        val proxy = if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort))
        } else {
            null
        }

        fun callApi(fbId: String): Pair<JSONObject?, String> {
            val inputObj = JSONObject().apply {
                put("feedback_id", fbId)
                put("feedback_reaction_id", reactId)
                if (!pageUid.isNullOrEmpty()) {
                    put("actor_id", pageUid) // Bắt buộc cho Page 615 hoạt động độc lập
                }
            }

            val bodyParams = listOf(
                "method" to "post",
                "pretty" to "false",
                "format" to "json",
                "server_timestamps" to "true",
                "locale" to "vi_VN",
                "fb_api_req_friendly_name" to "UFIReactionMutation",
                "fb_api_caller_class" to "graphservice",
                "client_doc_id" to REACTION_DOC_ID,
                "variables" to inputObj.toString(),
                "client_trace_id" to UUID.randomUUID().toString()
            )

            val bodyStr = bodyParams.joinToString("&") {
                "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}"
            }

            return try {
                val conn = (if (proxy != null) URL(GRAPHQL_URL).openConnection(proxy) else URL(GRAPHQL_URL).openConnection()) as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", FB_UA)
                    setRequestProperty("Accept-Encoding", "gzip, deflate")
                    setRequestProperty("authorization", "OAuth $cleanToken")
                    setRequestProperty("x-graphql-client-library", "graphservice")
                    setRequestProperty("x-fb-http-engine", "Tigon/Liger")
                    setRequestProperty("x-fb-friendly-name", "UFIReactionMutation")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                conn.outputStream.use { it.write(bodyStr.toByteArray()) }
                val stream: InputStream = if (conn.responseCode == 200) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                val finalStream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                    GZIPInputStream(stream)
                } else {
                    stream
                }
                val resText = finalStream.bufferedReader().readText()
                val json = try { JSONObject(resText) } catch (_: Exception) { null }
                Pair(json, resText)
            } catch (e: Exception) {
                Pair(null, e.message ?: "Connection error")
            }
        }

        // Lần 1: Gọi với full feedback_id: feedback:{owner}_{fbid}
        val (res1, raw1) = callApi(feedbackId)
        if (res1 != null) {
            if (isReactionSuccess(res1)) {
                return ReactionExecutionResult(true, "Thành công", raw1)
            }
            val rawLower = raw1.lowercase()
            if (rawLower.contains("already") && !rawLower.contains("error")) {
                return ReactionExecutionResult(true, "Đã thả cảm xúc trước đó", raw1)
            }

            // Lần 2 (Retry): Nếu lỗi hoặc noncoercible, thử bóc tách chỉ dùng feedback:fbid
            if (owner.isNotEmpty() && (rawLower.contains("noncoercible") || rawLower.contains("error") || !isReactionSuccess(res1))) {
                val fallbackB64 = Base64.encodeToString("feedback:$fbid".toByteArray(), Base64.NO_WRAP)
                val (res2, raw2) = callApi(fallbackB64)
                if (res2 != null) {
                    if (isReactionSuccess(res2)) {
                        return ReactionExecutionResult(true, "Thành công (fallback fbid)", raw2)
                    }
                    val rawLower2 = raw2.lowercase()
                    if (rawLower2.contains("already") && !rawLower2.contains("error")) {
                        return ReactionExecutionResult(true, "Đã thả cảm xúc trước đó", raw2)
                    }
                }
            }
        }

        return ReactionExecutionResult(false, "Thất bại: $raw1", raw1)
    }
}

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
        const val GRAPHQL_ENDPOINT = "https://graph.facebook.com/graphql"

        const val KATANA_USER_AGENT = Page615ReactionWorker.FB_UA

        fun parseReactionType(rawInput: String): String {
            val upper = rawInput.uppercase()
            return when {
                upper.contains("CARE") || upper.contains("THƯƠNG") || upper.contains("THUONG") || upper == "16" -> "CARE"
                upper.contains("LOVE") || upper.contains("TIM") || upper.contains("TYM") || upper == "2" -> "LOVE"
                upper.contains("LIKE") || upper.contains("THÍCH") || upper.contains("THICH") || upper == "1" -> "LIKE"
                upper.contains("HAHA") || upper.contains("CƯỜI") || upper.contains("CUOI") || upper == "4" -> "HAHA"
                upper.contains("WOW") || upper.contains("BẤT NGỜ") || upper == "3" -> "WOW"
                upper.contains("SAD") || upper.contains("BUỒN") || upper.contains("BUON") || upper == "7" -> "SAD"
                upper.contains("ANGRY") || upper.contains("PHẪN NỘ") || upper.contains("PHAN_NO") || upper == "8" -> "ANGRY"
                else -> "LIKE"
            }
        }
    }

    @Keep
    enum class ReactionType(val restValue: String, val entityId: String, val graphqlCode: Int, val labelVi: String) {
        LIKE("LIKE", "1635855486666999", 1, "Thích"),
        LOVE("LOVE", "1678524932434102", 2, "Yêu thích / Thả tim"),
        CARE("CARE", "2229796038974261", 16, "Thương thương"),
        HAHA("HAHA", "115940658764963", 4, "Haha"),
        WOW("WOW", "478547315650144", 3, "Bất ngờ"),
        SAD("SAD", "908563459236466", 7, "Buồn"),
        ANGRY("ANGRY", "444813342392137", 8, "Phẫn nộ");

        companion object {
            fun fromString(name: String): ReactionType {
                val parsed = parseReactionType(name)
                return values().find { it.name == parsed || it.restValue == parsed } ?: LIKE
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
        } catch (_: Exception) {}
        return null
    }

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
        val pageId = (customPageId615 ?: pageId615)?.trim()

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
                methodUsed = "UFIReactionMutation",
                message = "Thiếu Page Access Token của Page 615 để thực hiện reaction"
            )
        }

        // BƯỚC 2: Gọi UFIReactionMutation chuẩn bumx1.py
        var execRes = Page615ReactionWorker.executeReactionDetail(
            objectId = cleanId,
            reactionType = reaction.name,
            token = activePageToken,
            pageUid = pageId,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyType = proxyType
        )

        // Nếu token bị hết hạn (#190), thử refresh lại Page Token 1 lần từ User Token mẹ /me/accounts
        if (!execRes.isSuccess && (execRes.rawResponse.contains("Error validating access token") || execRes.rawResponse.contains("\"code\":190") || execRes.rawResponse.contains("\"code\": 190"))) {
            if (uToken.isNotEmpty() && !pageId.isNullOrEmpty()) {
                val refreshed = extractPageTokenFromUserToken(pageId, uToken)
                if (!refreshed.isNullOrEmpty() && refreshed != activePageToken) {
                    activePageToken = refreshed
                    execRes = Page615ReactionWorker.executeReactionDetail(
                        objectId = cleanId,
                        reactionType = reaction.name,
                        token = activePageToken,
                        pageUid = pageId,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort,
                        proxyType = proxyType
                    )
                }
            }
        }

        return ReactionResult(
            isSuccess = execRes.isSuccess,
            targetId = cleanId,
            reaction = reaction,
            methodUsed = "UFIReactionMutation",
            message = execRes.message,
            rawResponse = execRes.rawResponse
        )
    }
}
