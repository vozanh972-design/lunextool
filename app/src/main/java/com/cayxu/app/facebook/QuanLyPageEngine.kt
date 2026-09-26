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

    fun setAccessToken(token: String) {
        this.accessToken = token
    }

    private fun getCleanToken(overrideToken: String?): String =
        (overrideToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()

    /**
     * Chuyển quyền Fanpage Profile Plus / Page 615 sang UID mới FULL QUYỀN (Toàn quyền quản trị Admin)
     * Endpoint: POST /v21.0/{page_id}/assigned_users
     * Tasks: MANAGE (Facebook Graph API tự động cấp toàn bộ quyền Full Admin khi có MANAGE)
     */
    fun chuyenPageFullQuyen(pageId: String, targetUserId: String, pageToken: String? = null): PageActionResult {
        val token = getCleanToken(pageToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, targetUserId, "Thiếu Token thực thi", "")
        val cleanTargetId = targetUserId.trim()
        val cleanPageId = pageId.trim()

        val tasks = JSONArray().apply {
            put("MANAGE")
        }

        val formBody = FormBody.Builder()
            .add("user", cleanTargetId)
            .add("tasks", tasks.toString())
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$cleanPageId/assigned_users")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || (!body.contains("\"error\"") && !body.contains("\"errors\"")))
                if (isOk) {
                    return PageActionResult(true, cleanPageId, cleanTargetId, "Chuyển Full quyền thành công", body)
                }

                // Fallback nếu Page truyền thống (Classic Page) không hỗ trợ assigned_users: thử gọi /roles
                val classicRes = fallbackRoles(cleanPageId, cleanTargetId, "ADMIN", token)
                if (classicRes.isSuccess) {
                    return classicRes
                }

                PageActionResult(false, cleanPageId, cleanTargetId, parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            PageActionResult(false, cleanPageId, cleanTargetId, e.message ?: "Lỗi kết nối", "")
        }
    }

    /**
     * Chuyển quyền Fanpage Profile Plus / Page 615 sang UID mới KHÔNG FULL QUYỀN (No full - Quyền tác vụ)
     * Endpoint: POST /v21.0/{page_id}/assigned_users
     * Tasks: Không có quyền MANAGE (CREATE_CONTENT, MESSAGING, MODERATE, ADVERTISE, ANALYZE, MODERATE_COMMUNITY)
     */
    fun chuyenPageKhongFullQuyen(
        pageId: String,
        targetUserId: String,
        customTasks: List<String> = listOf("CREATE_CONTENT", "MESSAGING", "MODERATE", "ADVERTISE", "ANALYZE", "MODERATE_COMMUNITY"),
        pageToken: String? = null
    ): PageActionResult {
        val token = getCleanToken(pageToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, targetUserId, "Thiếu Token thực thi", "")
        val cleanTargetId = targetUserId.trim()
        val cleanPageId = pageId.trim()

        val jsonTasks = JSONArray().apply {
            customTasks.filter { it != "MANAGE" && it != "COMMUNITY_ACTIVITY" }.forEach { put(it) }
        }

        val formBody = FormBody.Builder()
            .add("user", cleanTargetId)
            .add("tasks", jsonTasks.toString())
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$cleanPageId/assigned_users")
            .post(formBody)
            .header("User-Agent", ua())
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || (!body.contains("\"error\"") && !body.contains("\"errors\"")))
                if (isOk) {
                    return PageActionResult(true, cleanPageId, cleanTargetId, "Chuyển quyền (No full) thành công", body)
                }

                // Fallback nếu Page truyền thống (Classic Page) không hỗ trợ assigned_users: thử gọi /roles
                val classicRes = fallbackRoles(cleanPageId, cleanTargetId, "EDITOR", token)
                if (classicRes.isSuccess) {
                    return classicRes
                }

                PageActionResult(false, cleanPageId, cleanTargetId, parseErrorMessage(body), body)
            }
        } catch (e: Exception) {
            PageActionResult(false, cleanPageId, cleanTargetId, e.message ?: "Lỗi kết nối", "")
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
    fun outPage(pageId: String, myUserId: String = "me", pageToken: String? = null): PageActionResult {
        val token = getCleanToken(pageToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, myUserId, "Thiếu Token thực thi", "")

        val request = Request.Builder()
            .url("${graphApi()}/$pageId/assigned_users?user=$myUserId&${FbVault.fieldAccessToken()}=$token")
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
    fun kichHoatPageAn(pageId: String, pageToken: String? = null): PageActionResult {
        val token = getCleanToken(pageToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, null, "Thiếu Token thực thi", "")

        val formBody = FormBody.Builder()
            .add("is_published", "true")
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$pageId")
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
    fun anPage(pageId: String, pageToken: String? = null): PageActionResult {
        val token = getCleanToken(pageToken)
        if (token.isEmpty()) return PageActionResult(false, pageId, null, "Thiếu Token thực thi", "")

        val formBody = FormBody.Builder()
            .add("is_published", "false")
            .add(FbVault.fieldAccessToken(), token)
            .build()

        val request = Request.Builder()
            .url("${graphApi()}/$pageId")
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
