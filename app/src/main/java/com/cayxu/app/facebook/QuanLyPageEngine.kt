package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Keep
class QuanLyPageEngine(
    private var accessToken: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPH_API_URL = "https://graph.facebook.com/v21.0"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"

        private val GRAPH_API by lazy { FbVault.graphApiUrl() }
        private val UA        by lazy { FbVault.userAgent() }
        private fun graphApi() = GRAPH_API
        private fun ua()       = UA
    }

    @Keep
    data class PageActionResult(
        val isSuccess: Boolean,
        val pageId: String,
        val targetUserId: String? = null,
        val message: String? = null,
        val rawResponse: String = ""
    ) {
        fun asResult(): Result<Boolean> =
            if (isSuccess) Result.success(true) else Result.failure(Exception(message ?: rawResponse))
    }

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

    fun setAccessToken(token: String) {
        this.accessToken = token
    }

    private fun getCleanToken(overrideToken: String?): String =
        (overrideToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()

    /**
     * Tự động đọc ID gốc của Page phục vụ riêng cho lệnh Graph API (KHÔNG ĐỔI UID 615 TRONG APP)
     * Giữ nguyên 100% UID 615 hiển thị và lưu trữ trên app.
     */
    fun resolveGraphPageId(pageUid615: String, motherToken: String = ""): String {
        val cleanUid = pageUid615.trim()
        if (!cleanUid.startsWith("615")) return cleanUid
        val cleanToken = (if (motherToken.isNotBlank()) motherToken else (accessToken ?: ""))
            .removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (cleanToken.isEmpty()) return cleanUid

        // 1. Thử gọi Graph API: GET /v21.0/$cleanUid?fields=id,name,delegate_page_id&access_token=$cleanToken
        try {
            val url = "${graphApi()}/$cleanUid?fields=id,name,delegate_page_id&${FbVault.fieldAccessToken()}=$cleanToken"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", ua())
                .build()
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val delegateId = json.optString("delegate_page_id", "")
                if (delegateId.isNotBlank() && !delegateId.startsWith("615")) {
                    return delegateId
                }
                val id = json.optString("id", "")
                if (id.isNotBlank() && !id.startsWith("615")) {
                    return id
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback: Truy vấn /me/accounts để tìm ID đối ứng với UID 615
        try {
            val url = "${graphApi()}/me/accounts?fields=id,additional_profile_id,delegate_page_id&limit=100&${FbVault.fieldAccessToken()}=$cleanToken"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", ua())
                .build()
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val p = data.getJSONObject(i)
                        val addId = p.optString("additional_profile_id", "")
                        val delId = p.optString("delegate_page_id", "")
                        val pageId = p.optString("id", "")
                        if (addId == cleanUid || delId == cleanUid) {
                            if (delId.isNotBlank() && !delId.startsWith("615")) return delId
                            if (pageId.isNotBlank() && !pageId.startsWith("615")) return pageId
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return cleanUid
    }

    /**
     * Tự động lấy Page Access Token chính xác của Page để gọi các lệnh quản trị
     */
    fun resolvePageAccessToken(
        realPageId: String,
        pageUid615: String,
        pageAccessToken: String?,
        motherToken: String = ""
    ): String {
        val cleanPageToken = pageAccessToken?.removePrefix("OAuth ")?.removePrefix("Bearer ")?.trim() ?: ""
        val cleanMotherToken = (if (motherToken.isNotBlank()) motherToken else (accessToken ?: ""))
            .removePrefix("OAuth ").removePrefix("Bearer ").trim()

        if (cleanPageToken.isNotEmpty() && cleanPageToken != cleanMotherToken) {
            return cleanPageToken
        }

        // Tự động truy vấn Page Access Token từ Facebook bằng motherToken
        if (cleanMotherToken.isNotEmpty()) {
            try {
                val url = "${graphApi()}/me/accounts?fields=id,access_token,additional_profile_id,delegate_page_id&limit=100&${FbVault.fieldAccessToken()}=$cleanMotherToken"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", ua())
                    .build()
                httpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data")
                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val p = data.getJSONObject(i)
                            val id = p.optString("id", "")
                            val addId = p.optString("additional_profile_id", "")
                            val delId = p.optString("delegate_page_id", "")
                            val token = p.optString("access_token", "")
                            if ((id == realPageId || addId == pageUid615 || delId == pageUid615 || id == pageUid615) && token.isNotBlank()) {
                                return token
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val url = "${graphApi()}/$realPageId?fields=access_token&${FbVault.fieldAccessToken()}=$cleanMotherToken"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", ua())
                    .build()
                httpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = JSONObject(body)
                    val token = json.optString("access_token", "")
                    if (token.isNotBlank()) return token
                }
            } catch (_: Exception) {}
        }

        return if (cleanPageToken.isNotEmpty()) cleanPageToken else cleanMotherToken
    }

    /**
     * Chuyển quyền Fanpage Profile Plus / Page 615 sang UID mới FULL QUYỀN (Toàn quyền quản trị Admin)
     * Endpoint: POST /v21.0/{realPageId}/assigned_users
     * Tasks chuẩn Kahara: MANAGE, CREATE_CONTENT, MESSAGING, MODERATE, ADVERTISE, ANALYZE (TUYỆT ĐỐI KHÔNG CÓ COMMUNITY_ACTIVITY)
     */
    fun chuyenPageFullQuyen(
        pageUid615: String,
        targetUserId: String,
        pageAccessToken: String? = null,
        motherToken: String? = null
    ): PageActionResult {
        val cleanTargetId = targetUserId.trim()
        val effectiveMotherToken = (motherToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val realPageId = resolveGraphPageId(pageUid615, effectiveMotherToken)
        val token = resolvePageAccessToken(realPageId, pageUid615, pageAccessToken, effectiveMotherToken)
        if (token.isEmpty()) return PageActionResult(false, pageUid615, cleanTargetId, "Thiếu Token thực thi", "")

        // Mảng task Full quyền CHUẨN KAHARA (TUYỆT ĐỐI KHÔNG CÓ COMMUNITY_ACTIVITY):
        val tasksJson = JSONArray().apply {
            put("MANAGE")
            put("CREATE_CONTENT")
            put("MESSAGING")
            put("MODERATE")
            put("ADVERTISE")
            put("ANALYZE")
        }.toString()

        val formBody = FormBody.Builder()
            .add("user", cleanTargetId)
            .add("tasks", tasksJson)
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$realPageId/assigned_users")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || (!body.contains("\"error\"") && !body.contains("\"errors\"")))
                if (isOk) {
                    PageActionResult(true, pageUid615, cleanTargetId, "Chuyển Full quyền thành công", body)
                } else {
                    // Fallback nếu Page truyền thống (Classic Page) không hỗ trợ assigned_users: thử gọi /roles
                    val classicRes = fallbackRoles(realPageId, cleanTargetId, "ADMIN", token)
                    if (classicRes.isSuccess) {
                        classicRes.copy(pageId = pageUid615)
                    } else {
                        PageActionResult(false, pageUid615, cleanTargetId, parseErrorMessage(body), body)
                    }
                }
            }
        } catch (e: Exception) {
            PageActionResult(false, pageUid615, cleanTargetId, e.message ?: "Lỗi kết nối", "")
        }
    }

    /**
     * Chuyển quyền Fanpage Profile Plus / Page 615 sang UID mới KHÔNG FULL QUYỀN (No full - Quyền tác vụ)
     * Endpoint: POST /v21.0/{realPageId}/assigned_users
     * Tasks: CREATE_CONTENT, MESSAGING, MODERATE, ADVERTISE, ANALYZE (Không có MANAGE và không có COMMUNITY_ACTIVITY)
     */
    fun chuyenPageKhongFullQuyen(
        pageUid615: String,
        targetUserId: String,
        pageAccessToken: String? = null,
        motherToken: String? = null,
        customTasks: List<String>? = null
    ): PageActionResult {
        val cleanTargetId = targetUserId.trim()
        val effectiveMotherToken = (motherToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val realPageId = resolveGraphPageId(pageUid615, effectiveMotherToken)
        val token = resolvePageAccessToken(realPageId, pageUid615, pageAccessToken, effectiveMotherToken)
        if (token.isEmpty()) return PageActionResult(false, pageUid615, cleanTargetId, "Thiếu Token thực thi", "")

        val tasksJson = if (customTasks != null) {
            JSONArray().apply {
                customTasks.filter { it != "MANAGE" && it != "COMMUNITY_ACTIVITY" }.forEach { put(it) }
            }.toString()
        } else {
            JSONArray().apply {
                put("CREATE_CONTENT")
                put("MESSAGING")
                put("MODERATE")
                put("ADVERTISE")
                put("ANALYZE")
            }.toString()
        }

        val formBody = FormBody.Builder()
            .add("user", cleanTargetId)
            .add("tasks", tasksJson)
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$realPageId/assigned_users")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || (!body.contains("\"error\"") && !body.contains("\"errors\"")))
                if (isOk) {
                    PageActionResult(true, pageUid615, cleanTargetId, "Chuyển quyền (No full) thành công", body)
                } else {
                    // Fallback nếu Page truyền thống (Classic Page) không hỗ trợ assigned_users: thử gọi /roles
                    val classicRes = fallbackRoles(realPageId, cleanTargetId, "EDITOR", token)
                    if (classicRes.isSuccess) {
                        classicRes.copy(pageId = pageUid615)
                    } else {
                        PageActionResult(false, pageUid615, cleanTargetId, parseErrorMessage(body), body)
                    }
                }
            }
        } catch (e: Exception) {
            PageActionResult(false, pageUid615, cleanTargetId, e.message ?: "Lỗi kết nối", "")
        }
    }

    private fun fallbackRoles(pageId: String, targetUserId: String, role: String, token: String): PageActionResult {
        return try {
            val formBody = FormBody.Builder()
                .add("user", targetUserId)
                .add("role", role)
                .add(FbVault.fieldAccessToken(), token)
                .build()

            val request = Request.Builder()
                .url("${graphApi()}/$pageId/roles")
                .post(formBody)
                .header("User-Agent", ua())
                .build()

            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                PageActionResult(isOk, pageId, targetUserId, if (isOk) "Chuyển vai trò thành công (Classic)" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            PageActionResult(false, pageId, targetUserId, e.message, "")
        }
    }

    /**
     * Rời khỏi Page (Out Page / Gỡ quyền bản thân khỏi Trang)
     */
    fun outPage(pageId: String, myUserId: String = "me", pageToken: String? = null, motherToken: String? = null): PageActionResult {
        val effectiveMotherToken = (motherToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val realPageId = resolveGraphPageId(pageId, effectiveMotherToken)
        val token = resolvePageAccessToken(realPageId, pageId, pageToken, effectiveMotherToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, myUserId, "Thiếu Token thực thi", "")

        val request = Request.Builder()
            .url("${graphApi()}/$realPageId/assigned_users?user=$myUserId&${FbVault.fieldAccessToken()}=$token")
            .delete()
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                PageActionResult(isOk, pageId, myUserId, if (isOk) "Đã out khỏi Page" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            PageActionResult(false, pageId, myUserId, e.message, "")
        }
    }

    /**
     * Kích hoạt Page ẩn (Đăng Trang công khai)
     */
    fun kichHoatPageAn(pageId: String, pageToken: String? = null, motherToken: String? = null): PageActionResult {
        val effectiveMotherToken = (motherToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val realPageId = resolveGraphPageId(pageId, effectiveMotherToken)
        val token = resolvePageAccessToken(realPageId, pageId, pageToken, effectiveMotherToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, null, "Thiếu Token thực thi", "")

        val formBody = FormBody.Builder()
            .add("is_published", "true")
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$realPageId")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                PageActionResult(isOk, pageId, null, if (isOk) "Đã kích hoạt Page" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            PageActionResult(false, pageId, null, e.message, "")
        }
    }

    /**
     * Ẩn Page (Hủy đăng Trang)
     */
    fun anPage(pageId: String, pageToken: String? = null, motherToken: String? = null): PageActionResult {
        val effectiveMotherToken = (motherToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val realPageId = resolveGraphPageId(pageId, effectiveMotherToken)
        val token = resolvePageAccessToken(realPageId, pageId, pageToken, effectiveMotherToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, null, "Thiếu Token thực thi", "")

        val formBody = FormBody.Builder()
            .add("is_published", "false")
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$realPageId")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                PageActionResult(isOk, pageId, null, if (isOk) "Đã ẩn Page" else parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            PageActionResult(false, pageId, null, e.message, "")
        }
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
                if (code == 100 && subcode == 33) {
                    return "Đối tượng không tồn tại hoặc tài khoản không có quyền thao tác"
                }
                if (code == 200) {
                    return "Không đủ quyền quản trị đối với Page này"
                }
                val msg = err?.optString("message")
                if (!msg.isNullOrBlank()) return msg
                val errStr = json.optString("error")
                if (errStr.isNotBlank()) return errStr
            }
        } catch (_: Exception) {}
        return if (body.isNotBlank()) body.take(250) else "Phản hồi lỗi không xác định từ Facebook"
    }
}
