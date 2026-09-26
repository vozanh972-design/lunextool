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
        const val LUNEX_GRAPHQL_URL = "https://b-graph.facebook.com/graphql"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
        const val LUNEX_KATANA_UA =
            "[FBAN/FB4A;FBAV/542.0.0.46.151;FBBV/840338789;FBDM/{density=0.75,width=300,height=540};FBLC/vi_VN;FBRV/0;FBCR/MobiFone;FBMF/MTool-Max;FBBD/MTool-Max;FBPN/com.facebook.katana;FBDV/MTool-Max;FBSV/9;FBOP/1;FBCA/x86_64:arm64-v8a;]"

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
            .connectTimeout(35, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)

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
     * Tự động đọc ID gốc của Page phục vụ riêng cho lệnh Graph API / GraphQL (KHÔNG ĐỔI UID 615 TRONG APP)
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
     * Tự động lấy Page Access Token chính xác của Page để gọi các lệnh quản trị nếu cần
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

    // =========================================================================
    // NATIVE TEMPLATE GRAPHQL CHUYỂN PAGE 4 BƯỚC CHUẨN TỪ LUNEXAUTO
    // =========================================================================

    private fun buildGraphQLRequest(
        token: String,
        docId: String,
        friendlyName: String,
        variablesJson: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Request {
        val cleanToken = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val formBody = FormBody.Builder()
            .add("client_doc_id", docId)
            .add("fb_api_req_friendly_name", friendlyName)
            .add("fb_api_caller_class", "graphservice")
            .add("variables", variablesJson)
            .build()

        val reqBuilder = Request.Builder()
            .url(LUNEX_GRAPHQL_URL)
            .post(formBody)
            .header("User-Agent", LUNEX_KATANA_UA)
            .header("Authorization", "OAuth $cleanToken")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Device-Group", "4789")
            .header("X-Graphql-Client-Library", "graphservice")
            .header("x-fb-friendly-name", friendlyName)
            .header("x-fb-request-analytics-tags", "{\"network_tags\":{\"product\":\"350685531728\",\"request_category\":\"graphql\",\"purpose\":\"none\",\"retry_attempt\":\"0\"},\"application_tags\":\"graphservice\"}")

        extraHeaders.forEach { (k, v) ->
            reqBuilder.header(k, v)
        }

        return reqBuilder.build()
    }

    /**
     * BƯỚC 1: XÁC THỰC MẬT KHẨU NICK GỬI (STEP_1_SEND_INVITATION)
     * Chuẩn Bytecode 0x28E2C4 - 0x28E2F2 từ lunexAUTO: sensitive_string_value là JSON mảng 2 chiều [["password", "<PASS>"]]
     */
    fun step1SendInvitation(
        senderToken: String,
        senderPassword: String,
        pageId: String,
        targetUserId: String,
        adminType: String
    ): Result<String> {
        val inner = JSONArray().apply {
            put("password")
            put(senderPassword)
        }
        val outer = JSONArray().apply {
            put(inner)
        }
        val clientData = JSONObject().apply {
            put("sensitive_string_value", outer.toString())
        }

        val path = "/nt/profile/admin_management/permissions_reauth?admin_id=$targetUserId&admin_rows_container_id=%5B%228bxxoh%3A2%22%2Cnull%5D&admin_type=$adminType&entry_point_screen_id=%5B%227o2xil%3A5%22%2Cnull%5D&profile_id=$pageId&state_ids%5Bauthenticated%5D=8csr9g%3A0&state_ids%5Bauthentication_attempted%5D=8csr9g%3A1&state_ids%5Bshow_entry_point_saving_spinner%5D=8bxxoh%3A0&state_ids%5Bshow_saving_spinner%5D=8csr9g%3A3&state_ids%5Bads%5D=8clnk7%3A2&state_ids%5Bcontent%5D=8clnk7%3A3&state_ids%5Binsights%5D=8clnk7%3A4&state_ids%5Bmessages%5D=8clnk7%3A5&state_ids%5Bmoderate%5D=8clnk7%3A6"

        val paramsObj = JSONObject().apply {
            put("path", path)
            put("client_data", clientData)
            put("nt_context", JSONObject().apply {
                put("styles_id", "588d028b36bed0e1889e09b60e0f9aea")
                put("using_white_navbar", true)
                put("pixel_ratio", 2)
                put("theme_params", JSONObject().apply {
                    put("design_system_name", "FDS")
                })
                put("bloks_version", "338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d")
            })
        }

        val variablesObj = JSONObject().apply {
            put("params", paramsObj)
        }

        val request = buildGraphQLRequest(
            token = senderToken,
            docId = "30749539927629244093798192451",
            friendlyName = "NativeTemplateAsyncQuery",
            variablesJson = variablesObj.toString()
        )

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val hasErrors = body.contains("\"errors\":") || (body.contains("\"error\":") && !body.contains("\"error\":false"))
                if (res.isSuccessful && !hasErrors) {
                    Result.success(body)
                } else {
                    val parsedErr = parseErrorMessage(body)
                    Result.failure(Exception(parsedErr))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Lỗi kết nối bước 1"))
        }
    }

    /**
     * BƯỚC 2: CẤP QUYỀN VÀ BẮN LỜI MỜI (STEP_2_ACTIVATE_ADMIN)
     */
    fun step2ActivateAdmin(
        senderToken: String,
        pageId: String,
        targetUserId: String,
        adminType: String,
        boolAds: Boolean = true,
        boolContent: Boolean = true,
        boolInsights: Boolean = true,
        boolMessages: Boolean = true,
        boolModerate: Boolean = true
    ): Result<String> {
        val path = "/nt/profile/admin_management/permissions/update?admin_rows_container_id=%5B%228bxxoh%3A2%22%2Cnull%5D&entry_point_screen_id=%5B%227o2xil%3A5%22%2Cnull%5D&admin_type=$adminType&profile_id=$pageId&target_admin_id=$targetUserId&secured_sensitive_actions%5B0%5D=page_admin_access_addition&state_ids%5Bshow_entry_point_saving_spinner%5D=8bxxoh%3A0&state_ids%5Bads%5D=8clnk7%3A2&state_ids%5Bcontent%5D=8clnk7%3A3&state_ids%5Binsights%5D=8clnk7%3A4&state_ids%5Bmessages%5D=8clnk7%3A5&state_ids%5Bmoderate%5D=8clnk7%3A6"

        val paramsObj = JSONObject().apply {
            put("path", path)
            put("payload", JSONObject().apply {
                put("state_data", JSONObject().apply {
                    put("ads", boolAds)
                    put("content", boolContent)
                    put("insights", boolInsights)
                    put("messages", boolMessages)
                    put("moderate", boolModerate)
                    put("show_entry_point_saving_spinner", "NONE")
                })
            })
            put("nt_context", JSONObject().apply {
                put("styles_id", "588d028b36bed0e1889e09b60e0f9aea")
                put("using_white_navbar", true)
                put("pixel_ratio", 2)
                put("theme_params", JSONObject().apply {
                    put("design_system_name", "FDS")
                })
                put("bloks_version", "338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d")
            })
        }

        val variablesObj = JSONObject().apply {
            put("params", paramsObj)
        }

        val request = buildGraphQLRequest(
            token = senderToken,
            docId = "30749539927629244093798192451",
            friendlyName = "NativeTemplateAsyncQuery",
            variablesJson = variablesObj.toString()
        )

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val hasErrors = body.contains("\"errors\":") || (body.contains("\"error\":") && !body.contains("\"error\":false"))
                if (res.isSuccessful && !hasErrors) {
                    Result.success(body)
                } else {
                    val parsedErr = parseErrorMessage(body)
                    Result.failure(Exception(parsedErr))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Lỗi kết nối bước 2"))
        }
    }

    /**
     * BƯỚC 3: LẤY INVITATION ID TỪ ACC NHẬN (STEP_3_GET_INVITATION)
     */
    fun step3GetInvitationId(
        receiverToken: String,
        pageId: String,
        receiverUid: String
    ): Result<String> {
        val paramsObj = JSONObject().apply {
            put("path", "/nt/profile/admin_management/invitation")
            put("screen_id", "[\"sz9fnp:17\",null]")
            put("profile_id", pageId)
            put("invitee_id", receiverUid)
            put("scale", "2")
            put("use_native_entrypoint_for_stars_on_reels", false)
            put("nt_context", JSONObject().apply {
                put("styles_id", "588d028b36bed0e1889e09b60e0f9aea")
            })
        }

        val variablesObj = JSONObject().apply {
            put("params", paramsObj)
        }

        val request = buildGraphQLRequest(
            token = receiverToken,
            docId = "221080835214205752894689842827",
            friendlyName = "NativeTemplateScreenQuery",
            variablesJson = variablesObj.toString(),
            extraHeaders = mapOf("x-graphql-request-purpose" to "fetch")
        )

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val regexes = listOf(
                    Regex("""invitation_id(?:%3D|=)([\d]+)"""),
                    Regex("""["']invitation_id["']\s*:\s*["']?([\d]+)"""),
                    Regex("""(?:%22|")invitation_id(?:%22|")%3A(?:%22|")([\d]+)"""),
                    Regex("""invitation_id\\":\\"([\d]+)"""),
                    Regex("""invitation_id\\%3D([\d]+)"""),
                    Regex("""intent_value["']?\s*:\s*["'][^"']*(?:invitation_id|invite_id)(?:%3D|=)([\d]+)"""),
                    Regex("""intent_value["']?\s*:\s*["'][^"']*?([\d]{8,})""")
                )
                for (rg in regexes) {
                    val match = rg.find(body)
                    if (match != null && match.groupValues[1].isNotBlank()) {
                        return Result.success(match.groupValues[1])
                    }
                }
                if (body.contains("\"errors\":") || body.contains("\"error\":")) {
                    Result.failure(Exception(parseErrorMessage(body)))
                } else {
                    Result.failure(Exception("Không tìm thấy invitation_id trong phản hồi của acc nhận"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Lỗi kết nối bước 3"))
        }
    }

    /**
     * BƯỚC 4: ACC NHẬN CHẤP NHẬN LỜI MỜI (STEP_4_ACCEPT_INVITATION)
     */
    fun step4AcceptInvitation(
        receiverToken: String,
        invitationId: String,
        adminType: String = "full_access"
    ): Result<String> {
        val path = "/nt/profile/admin_management/invitation_response?accept_invitation=1&invitation_id=$invitationId&admin_type=$adminType"

        val paramsObj = JSONObject().apply {
            put("path", path)
            put("nt_context", JSONObject().apply {
                put("styles_id", "588d028b36bed0e1889e09b60e0f9aea")
            })
        }

        val variablesObj = JSONObject().apply {
            put("params", paramsObj)
        }

        val request = buildGraphQLRequest(
            token = receiverToken,
            docId = "30749539927629244093798192451",
            friendlyName = "NativeTemplateAsyncQuery",
            variablesJson = variablesObj.toString()
        )

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val hasErrors = body.contains("\"errors\":") || (body.contains("\"error\":") && !body.contains("\"error\":false"))
                if (res.isSuccessful && !hasErrors) {
                    Result.success(body)
                } else {
                    val parsedErr = parseErrorMessage(body)
                    Result.failure(Exception(parsedErr))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Lỗi kết nối bước 4"))
        }
    }

    /**
     * QUY TRÌNH CHUYỂN PAGE HOÀN CHỈNH 4 BƯỚC THEO CHUẨN LUNEXAUTO
     */
    fun chuyenPageLunexAuto(
        pageUid615: String,
        targetUserId: String,
        senderToken: String,
        senderPassword: String = "",
        receiverToken: String = "",
        isFullPermission: Boolean = true
    ): PageActionResult {
        val cleanTargetId = targetUserId.trim()
        val cleanSenderToken = senderToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (cleanSenderToken.isEmpty()) {
            return PageActionResult(false, pageUid615, cleanTargetId, "Thiếu Token nick gửi", "")
        }

        // 1. Tự động đọc ID gốc của Page phục vụ riêng lệnh GraphQL (Không ghi đè UID 615 trong app)
        val realPageId = resolveGraphPageId(pageUid615, cleanSenderToken)
        val adminType = if (isFullPermission) "full_access" else "task_access"

        // MẸO TỐI ƯU CHUẨN LUNEXAUTO:
        // Thử gọi thẳng Bước 2 trước. Nếu token của nick gửi đang hợp lệ và chưa bị Facebook bắt re-auth mật khẩu, Bước 2 sẽ thành công ngay lập tức!
        var step2Res = step2ActivateAdmin(
            senderToken = cleanSenderToken,
            pageId = realPageId,
            targetUserId = cleanTargetId,
            adminType = adminType,
            boolAds = true,
            boolContent = true,
            boolInsights = true,
            boolMessages = true,
            boolModerate = true
        )

        // Chỉ khi nào Bước 2 gặp lỗi (cần re-auth mật khẩu hoặc gặp lỗi), mới kích hoạt Bước 1 rồi gọi lại Bước 2
        if (step2Res.isFailure) {
            if (senderPassword.isNotBlank()) {
                val step1Res = step1SendInvitation(
                    senderToken = cleanSenderToken,
                    senderPassword = senderPassword,
                    pageId = realPageId,
                    targetUserId = cleanTargetId,
                    adminType = adminType
                )
                if (step1Res.isFailure) {
                    val s1Err = step1Res.exceptionOrNull()?.message ?: "Xác thực mật khẩu thất bại"
                    return PageActionResult(false, pageUid615, cleanTargetId, s1Err, "")
                }
                // Sau khi re-auth thành công, gọi lại Bước 2
                step2Res = step2ActivateAdmin(
                    senderToken = cleanSenderToken,
                    pageId = realPageId,
                    targetUserId = cleanTargetId,
                    adminType = adminType,
                    boolAds = true,
                    boolContent = true,
                    boolInsights = true,
                    boolMessages = true,
                    boolModerate = true
                )
            } else {
                val err2 = step2Res.exceptionOrNull()?.message ?: "Facebook yêu cầu xác thực mật khẩu"
                return PageActionResult(false, pageUid615, cleanTargetId, "$err2 (Vui lòng nhập mật khẩu tài khoản gửi)", "")
            }
        }

        if (step2Res.isFailure) {
            val err = step2Res.exceptionOrNull()?.message ?: "Gửi lời mời quản trị thất bại"
            return PageActionResult(false, pageUid615, cleanTargetId, err, "")
        }

        val cleanReceiverToken = receiverToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()

        // BƯỚC 3 & 4: Nếu có Token nick nhận -> Tự động tìm Invitation ID và Accept luôn
        if (cleanReceiverToken.isNotEmpty()) {
            val step3Res = step3GetInvitationId(
                receiverToken = cleanReceiverToken,
                pageId = realPageId,
                receiverUid = cleanTargetId
            )

            if (step3Res.isSuccess) {
                val invitationId = step3Res.getOrNull() ?: ""
                if (invitationId.isNotEmpty()) {
                    val step4Res = step4AcceptInvitation(
                        receiverToken = cleanReceiverToken,
                        invitationId = invitationId,
                        adminType = adminType
                    )
                    if (step4Res.isSuccess) {
                        return PageActionResult(
                            isSuccess = true,
                            pageId = pageUid615,
                            targetUserId = cleanTargetId,
                            message = "Chuyển và tự động nhận Admin thành công (4 bước hoàn tất)",
                            rawResponse = step4Res.getOrNull() ?: ""
                        )
                    } else {
                        val err4 = step4Res.exceptionOrNull()?.message ?: ""
                        return PageActionResult(
                            isSuccess = true,
                            pageId = pageUid615,
                            targetUserId = cleanTargetId,
                            message = "Đã gửi lời mời thành công (Lỗi tự động Accept bước 4: $err4)",
                            rawResponse = ""
                        )
                    }
                }
            } else {
                val err3 = step3Res.exceptionOrNull()?.message ?: ""
                return PageActionResult(
                    isSuccess = true,
                    pageId = pageUid615,
                    targetUserId = cleanTargetId,
                    message = "Đã gửi lời mời thành công (Lỗi tìm lời mời bước 3: $err3)",
                    rawResponse = ""
                )
            }
        }

        // Trường hợp không có token nick nhận: Đã gửi lời mời thành công ở bước 2
        return PageActionResult(
            isSuccess = true,
            pageId = pageUid615,
            targetUserId = cleanTargetId,
            message = "Đã gửi lời mời quản trị thành công (Chờ nick nhận chấp nhận)",
            rawResponse = step2Res.getOrNull() ?: ""
        )
    }

    /**
     * Chuyển Full quyền (Toàn quyền quản trị Admin)
     */
    fun chuyenPageFullQuyen(
        pageUid615: String,
        targetUserId: String,
        pageAccessToken: String? = null,
        motherToken: String? = null,
        senderPassword: String = "",
        receiverToken: String = ""
    ): PageActionResult {
        val effectiveSenderToken = (motherToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        return chuyenPageLunexAuto(
            pageUid615 = pageUid615,
            targetUserId = targetUserId,
            senderToken = effectiveSenderToken,
            senderPassword = senderPassword,
            receiverToken = receiverToken,
            isFullPermission = true
        )
    }

    /**
     * Chuyển Không Full quyền (Quyền tác vụ)
     */
    fun chuyenPageKhongFullQuyen(
        pageUid615: String,
        targetUserId: String,
        pageAccessToken: String? = null,
        motherToken: String? = null,
        senderPassword: String = "",
        receiverToken: String = "",
        customTasks: List<String>? = null
    ): PageActionResult {
        val effectiveSenderToken = (motherToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        return chuyenPageLunexAuto(
            pageUid615 = pageUid615,
            targetUserId = targetUserId,
            senderToken = effectiveSenderToken,
            senderPassword = senderPassword,
            receiverToken = receiverToken,
            isFullPermission = false
        )
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
                    val desc = first?.optString("description")
                    val text = desc?.takeIf { it.isNotBlank() } ?: summary?.takeIf { it.isNotBlank() } ?: msg
                    if (!text.isNullOrBlank()) return text
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
