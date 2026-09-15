package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.util.NativeSecurity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Xử lý:
 * 1. Tạo Fanpage Facebook (/me/accounts).
 * 2. Chuyển quyền quản trị Fanpage sang UID mới (/roles).
 * 3. Lấy danh sách Fanpage của tài khoản.
 */
@Keep
class FacebookPageService {

    companion object {
        const val GRAPH_BASE_URL = "https://graph.facebook.com"
        const val GRAPH_API_VERSION = "v19.0"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Lấy danh sách ID danh mục hợp lệ từ Graph API
     */
    fun fetchValidCategoryIds(token: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val clean = token.removePrefix("OAuth ").trim()
            val url = "$GRAPH_BASE_URL/v19.0/fb_page_categories?access_token=$clean"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                if (json.has("data")) {
                    val arr = json.getJSONArray("data")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id", "")
                        if (id.isNotBlank()) list.add(id)
                    }
                }
            }
        } catch (_: Throwable) {}
        return list
    }

    /**
     * Kết quả tạo Facebook Profile Plus Page
     */
    @Keep
    data class RegPageResult(
        val isSuccess: Boolean,
        val pageId: String? = null,
        val profilePlusId: String? = null,
        val pageName: String = "",
        val errorMessage: String? = null,
        val rawResponse: String = ""
    )

    /**
     * 1. Tạo Profile Plus / Fanpage Facebook bằng Bloks GraphQL chuẩn App Katana (Android FB4A) (Li2/n;)
     * Endpoint: POST https://graph.facebook.com/graphql
     * App ID: com.bloks.www.additional.profile.plus.creation.action.category.submit
     * Bloks Versioning ID: 338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d
     * Styles ID: 588d028b36bed0e1889e09b60e0f9aea
     * Client Doc ID: 119940804239956818821550724
     * User-Agent Katana: [FBAN/FB4A;FBAV/537.0.0.47.77;FBPN/com.facebook.katana;]
     * Cấu trúc tham số: category_ids: ["180164648685982"], referrer: "pages_tab_launch_point",
     *                   creation_source: "android", variant: 5, screen: "category", name: <pageName>
     *
     * 2. Cơ chế bóc tách Page ID / Profile Plus ID chuẩn từ phản hồi Bloks (Li2/i0;)
     */
    @Throws(Exception::class)
    fun createFacebookPage(
        pageName: String,
        userToken: String,
        category: String = "180164648685982"
    ): RegPageResult {
        val cleanToken = userToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val url = "https://graph.facebook.com/graphql"

        val innerParams = JSONObject().apply {
            put("client_input_params", JSONObject().apply {
                put("page_id", "0")
                put("profile_plus_id", "0")
                put("cp_upsell_declined", 0)
                put("off_platform_creator_reachout_id", "")
                put("category_ids", JSONArray().put(category))
                put("nav_chain", "...")
            })
            put("server_params", JSONObject().apply {
                put("referrer", "pages_tab_launch_point")
                put("INTERNAL__latency_qpl_marker_id", 36707139)
                put("creation_source", "android")
                put("name", pageName)
                put("variant", 5)
                put("screen", "category")
                put("INTERNAL__latency_qpl_instance_id", 55098533200051.0)
            })
        }

        val level1 = JSONObject().apply {
            put("params", JSONObject().apply { put("params", innerParams.toString()) }.toString())
            put("bloks_versioning_id", "338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d")
            put("app_id", "com.bloks.www.additional.profile.plus.creation.action.category.submit")
        }

        val ntContext = JSONObject().apply {
            put("using_white_navbar", true)
            put("styles_id", "588d028b36bed0e1889e09b60e0f9aea")
            put("pixel_ratio", 2)
            put("is_push_on", true)
            put("debug_tooling_metadata_token", JSONObject.NULL)
            put("is_flipper_enabled", false)
            put("theme_params", JSONArray().apply {
                put(JSONObject().apply {
                    put("value", JSONArray())
                    put("design_system_name", "FDS")
                })
            })
            put("bloks_version", "338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d")
        }

        val variables = JSONObject().apply {
            put("params", level1)
            put("scale", "2")
            put("nt_context", ntContext)
        }

        val formBody = FormBody.Builder()
            .add("method", "post")
            .add("pretty", "false")
            .add("format", "json")
            .add("server_timestamps", "true")
            .add("locale", "vi_VN")
            .add("client_doc_id", "119940804239956818821550724")
            .add("variables", variables.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "[FBAN/FB4A;FBAV/537.0.0.47.77;FBPN/com.facebook.katana;]")
            .header("Authorization", "OAuth $cleanToken")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Http-Engine", "Tigon/Liger")
            .header("X-Fb-Client-Ip", "True")
            .header("X-Fb-Server-Cluster", "True")
            .header("X-Graphql-Request-Purpose", "fetch")
            .header("X-Graphql-Client-Library", "graphservice")
            .post(formBody)
            .build()

        return httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"

            // 1. Bóc tách Page ID / Profile Plus ID theo 4 mẫu định dạng chuẩn (Li2/i0;)
            var extractedPageId: String? = null
            var extractedProfilePlusId: String? = null

            // Mẫu 1: WriteGlobalConsistencyStore
            val pattern1Page = Regex("""\(bk\.action\.bloks\.WriteGlobalConsistencyStore,\s*"ADDITIONAL_PROFILE_PLUS_CREATION:page_id"\s*,\s*"(\d+)"""")
            val pattern1ProfilePlus = Regex("""\(bk\.action\.bloks\.WriteGlobalConsistencyStore,\s*"ADDITIONAL_PROFILE_PLUS_CREATION:profile_plus_id"\s*,\s*"(\d+)"""")
            pattern1Page.find(body)?.let { extractedPageId = it.groupValues[1] }
            pattern1ProfilePlus.find(body)?.let { extractedProfilePlusId = it.groupValues[1] }

            // Mẫu 2: dq8 action
            if (extractedPageId == null) {
                val pattern2Page = Regex("""\(dq8\s+"ADDITIONAL_PROFILE_PLUS_CREATION:page_id"\s+"(\d+)"""")
                pattern2Page.find(body)?.let { extractedPageId = it.groupValues[1] }
            }
            if (extractedProfilePlusId == null) {
                val pattern2ProfilePlus = Regex("""\(dq8\s+"ADDITIONAL_PROFILE_PLUS_CREATION:profile_plus_id"\s+"(\d+)"""")
                pattern2ProfilePlus.find(body)?.let { extractedProfilePlusId = it.groupValues[1] }
            }

            // Mẫu 3: JSON key-value
            if (extractedPageId == null) {
                val pattern3Page = Regex(""""page_id"\s*[:=]\s*"?(\d{6,})"?""")
                pattern3Page.find(body)?.let {
                    val candidate = it.groupValues[1]
                    if (candidate != "0") extractedPageId = candidate
                }
            }
            if (extractedProfilePlusId == null) {
                val pattern3ProfilePlus = Regex(""""profile_plus_id"\s*[:=]\s*"?(\d{6,})"?""")
                pattern3ProfilePlus.find(body)?.let {
                    val candidate = it.groupValues[1]
                    if (candidate != "0") extractedProfilePlusId = candidate
                }
            }

            // Mẫu 4: Word boundary
            if (extractedPageId == null) {
                val pattern4Page = Regex("""\bpage_id\b[^\d]*(\d{6,})""")
                pattern4Page.find(body)?.let {
                    val candidate = it.groupValues[1]
                    if (candidate != "0") extractedPageId = candidate
                }
            }

            // Kiểm tra cờ thành công create_success hoặc đã trích xuất được Page ID hợp lệ
            val isSuccess = body.contains("create_success") || (extractedPageId != null && extractedPageId != "0")

            if (isSuccess) {
                return@use RegPageResult(
                    isSuccess = true,
                    pageId = extractedPageId,
                    profilePlusId = extractedProfilePlusId,
                    pageName = pageName,
                    rawResponse = body
                )
            }

            // 2. Bóc tách Toast lỗi và Error Marker
            var bloksError: String? = null

            // Bắt Toast: \(bk\.action\.io\.Toast,\s*"([^"]+)"
            val toastRegex = Regex("""\(bk\.action\.io\.Toast,\s*"([^"]+)"""")
            toastRegex.find(body)?.let {
                bloksError = it.groupValues[1]
            }

            if (bloksError.isNullOrBlank()) {
                val genericToast = Regex("""Toast,\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                genericToast.find(body)?.let {
                    bloksError = it.groupValues[1]
                }
            }

            // Kiểm tra Error Marker: create_error hoặc profile_creation_error
            if (bloksError.isNullOrBlank()) {
                if (body.contains("profile_creation_error") || body.contains("create_error")) {
                    val msgPattern = Regex("""["'](Bạn đã tạo quá nhiều|Tài khoản của bạn|Không thể tạo [tT]rang|Vui lòng thử lại|You've created too many|You cannot create)[^"']*["']""", RegexOption.IGNORE_CASE)
                    val m = msgPattern.find(body)
                    if (m != null) {
                        bloksError = m.value.trim('"', '\'')
                    } else {
                        bloksError = "Không thể tạo Trang: Gần đây bạn đã thử tạo Trang quá nhiều lần. Hãy thử lại vào lúc khác."
                    }
                }
            }

            val finalError = bloksError ?: extractDetailedFacebookError(body)
            val errorWithRaw = "$finalError\n\n[Raw Facebook Response]:\n${if (body.length > 500) body.take(500) + "..." else body}"
            throw Exception(errorWithRaw)
        }
    }

    /**
     * Bóc tách thông điệp lỗi chi tiết từ Facebook
     */
        // 1. Kiểm tra các lỗi phổ biến đặc trưng của Meta
        val lowerBody = body.lowercase()
        if (lowerBody.contains("phone_verification") || lowerBody.contains("confirm_phone") || lowerBody.contains("sms_code") || lowerBody.contains("xác minh số điện thoại") || lowerBody.contains("xác thực sms")) {
            return "Tài khoản yêu cầu xác thực Số điện thoại / SMS (Checkpoint)"
        }
        if (lowerBody.contains("checkpoint_required") || lowerBody.contains("account_checkpoint") || lowerBody.contains("checkpoint")) {
            return "Tài khoản bị Checkpoint yêu cầu xác minh bảo mật"
        }
        if (lowerBody.contains("profile_creation_error") || lowerBody.contains("quá nhiều") || lowerBody.contains("too many") || lowerBody.contains("limit_reached")) {
            return "Tài khoản bị giới hạn tạo Trang (Đã tạo quá nhiều Trang gần đây, hãy thử lại sau)"
        }
        if (lowerBody.contains("invalid_name") || lowerBody.contains("tên không hợp lệ")) {
            return "Tên Page không hợp lệ hoặc chứa ký tự/từ khóa bị Meta từ chối"
        }

        // 2. Tìm thông báo Toast trong Bloks Action: \(bk\.action\.io\.Toast, "..."
        val toastRegex = Regex("""\(bk\.action\.io\.Toast,\s*"([^"]+)"""")
        val toastMatch = toastRegex.find(body)
        if (toastMatch != null && toastMatch.groupValues[1].isNotBlank()) {
            return toastMatch.groupValues[1]
        }

        // 3. Phân tích cấu trúc JSON errors / error
        try {
            val json = JSONObject(body)
            if (json.has("errors")) {
                val errArr = json.getJSONArray("errors")
                if (errArr.length() > 0) {
                    val err = errArr.getJSONObject(0)
                    val desc = err.optString("description", "")
                    val summary = err.optString("summary", "")
                    val msg = err.optString("message", "")
                    val code = err.optInt("code", 0)
                    val text = desc.ifBlank { summary.ifBlank { msg } }
                    if (text.isNotBlank()) {
                        return if (code != 0) "(#$code) $text" else text
                    }
                }
            }
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                if (err != null) {
                    val userMsg = err.optString("error_user_msg", "")
                    val userTitle = err.optString("error_user_title", "")
                    val msg = err.optString("message", "")
                    val code = err.optInt("code", 0)
                    val text = userMsg.ifBlank { userTitle.ifBlank { msg } }
                    if (text.isNotBlank()) {
                        return if (code != 0) "(#$code) $text" else text
                    }
                }
            }
        } catch (_: Throwable) {}

        // 4. Tìm các thuộc tính lỗi trong chuỗi JSON
        val messageRegexes = listOf(
            Regex("""["']error_user_msg["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']error_description["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']error_message["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']description["']\s*:\s*["']([^"']+)["']"""),
            Regex("""["']message["']\s*:\s*["']([^"']+)["']""")
        )
        for (regex in messageRegexes) {
            val m = regex.find(body)
            if (m != null && m.groupValues[1].isNotBlank()) {
                val foundMsg = m.groupValues[1]
                if (!foundMsg.equals("null", ignoreCase = true) && !foundMsg.startsWith("http") && !foundMsg.contains("graphql", ignoreCase = true)) {
                    return foundMsg
                }
            }
        }

        // 5. Nếu không khớp mẫu, trả về chuỗi phản hồi trực tiếp từ Facebook
        val clean = body.replace("\n", " ").replace("\r", " ").replace("\\", "").trim()
        return if (clean.length > 120) clean.take(120) + "..." else clean.ifBlank { "Lỗi không xác định từ Facebook" }
    }

    /**
     * Tìm Page vừa tạo theo tên để lấy UID và Page Token (Chuẩn Lz2/m)
     */
    fun findPageByName(token: String, pageName: String, maxRetries: Int = 5, delayMs: Long = 2000L): FacebookPageItem? {
        for (attempt in 0 until maxRetries) {
            val pages = getPages(token)
            val found = pages.firstOrNull { it.pageName.equals(pageName, ignoreCase = true) }
            if (found != null) return found
            if (attempt < maxRetries - 1) {
                try { Thread.sleep(delayMs) } catch (_: Throwable) {}
            }
        }
        return null
    }

    /**
     * 2. Chuyển quyền quản trị Page sang UID khác (Nút "Chuyển Page")
     */
    @Throws(Exception::class)
    fun transferPageRole(
        pageId: String,
        pageToken: String,
        targetUserId: String
    ): Boolean {
        val url = "https://graph.facebook.com/v19.0/$pageId/roles"
        val formBody = FormBody.Builder()
            .add("user", targetUserId)
            .add("role", "ADMIN")
            .add("access_token", pageToken)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            return response.isSuccessful && body.contains("\"success\":true")
        }
    }

    /**
     * 3. Lấy danh sách Pages của tài khoản (Thống nhất 1 chuẩn duy nhất Lz2/m)
     * Endpoint: GET /v24.0/me?fields=facebook_pages{access_token,additional_profile_id,id,name}
     */
    fun getPages(userToken: String): List<FacebookPageItem> {
        val list = mutableListOf<FacebookPageItem>()
        val cleanToken = userToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        
        // Cơ chế chuẩn Lz2/m
        try {
            val url = "$GRAPH_BASE_URL/v24.0/me?fields=facebook_pages{access_token,additional_profile_id,id,name}&access_token=$cleanToken"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val pagesObj = json.optJSONObject("facebook_pages")
                val arr = pagesObj?.optJSONArray("data")
                if (arr != null && arr.length() > 0) {
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        val id = p.optString("id", "")
                        val name = p.optString("name", "")
                        val token = p.optString("access_token", "")
                        val addId = p.optString("additional_profile_id", "")
                        if (id.isNotBlank()) {
                            list.add(
                                FacebookPageItem(
                                    pageId = id,
                                    pageName = name,
                                    pageToken = token,
                                    additionalProfileId = addId,
                                    avatar = "$GRAPH_BASE_URL/$id/picture?type=large",
                                    isLive = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        if (list.isNotEmpty()) return list

        // Fallback chuẩn khi token app katana không hỗ trợ v24.0 facebook_pages
        try {
            val fallbackUrl = "$GRAPH_BASE_URL/v19.0/me/accounts?fields=id,name,access_token,additional_profile_id&limit=100&access_token=$cleanToken"
            val request = Request.Builder().url(fallbackUrl).get().build()
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                if (json.has("data")) {
                    val arr = json.getJSONArray("data")
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        val id = p.optString("id", "")
                        val name = p.optString("name", "")
                        val token = p.optString("access_token", "")
                        val addId = p.optString("additional_profile_id", "")
                        if (id.isNotBlank()) {
                            list.add(
                                FacebookPageItem(
                                    pageId = id,
                                    pageName = name,
                                    pageToken = token,
                                    additionalProfileId = addId,
                                    avatar = "$GRAPH_BASE_URL/$id/picture?type=large",
                                    isLive = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return list
    }

    /**
     * 4. Upload ảnh đại diện cho Page sau khi tạo
     */
    fun uploadPageAvatar(pageId: String, pageToken: String, imageBytes: ByteArray): Boolean {
        return try {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", pageToken)
                .addFormDataPart("published", "true")
                .addFormDataPart(
                    "source",
                    "avatar_${System.currentTimeMillis()}.jpg",
                    imageBytes.toRequestBody(mediaType)
                )
                .build()

            val request = Request.Builder()
                .url("$GRAPH_BASE_URL/$pageId/photos")
                .post(reqBody)
                .build()

            httpClient.newCall(request).execute().use { res ->
                res.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sinh tên ngẫu nhiên:
     * - Tên Việt: 100% Họ + Đệm + Tên người Việt thuần túy (ví dụ: Thùy Dung, Nguyễn Thị Linh, Trần Đức Anh...) KHÔNG chèn hậu tố lạ.
     * - Tên Tây: 100% First Name + Last Name người phương Tây chuẩn (ví dụ: James Smith, Emma Johnson, Michael Brown...) KHÔNG chèn hậu tố lạ.
     */
    fun generateRandomName(nameType: String): String {
        val vietnameseFirst = listOf(
            "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ",
            "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý", "Đinh", "Đoàn", "Lâm", "Trịnh"
        )
        val vietnameseMiddle = listOf(
            "Thị", "Văn", "Thùy", "Ngọc", "Thu", "Xuân", "Thanh", "Minh", "Đức",
            "Hải", "Tuấn", "Hoàng", "Gia", "Bảo", "Khánh", "Phương", "Diệu", "Mỹ", "Quỳnh"
        )
        val vietnameseLast = listOf(
            "Dung", "Anh", "Linh", "Trang", "Hương", "Hà", "Nhi", "Mai", "Thảo",
            "Uyên", "Yến", "Vy", "Huyền", "Ngân", "Tâm", "Hằng", "Chi", "Quân", "Nam", "Phong", "Huy", "Sơn"
        )

        val westernFirst = listOf(
            "James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles",
            "Emma", "Olivia", "Sophia", "Ava", "Isabella", "Mia", "Emily", "Abigail", "Harper", "Ella",
            "Alexander", "Daniel", "Matthew", "Lucas", "Henry", "Sebastian", "Jack", "Chloe", "Grace", "Zoey"
        )
        val westernLast = listOf(
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Garcia", "Rodriguez", "Wilson",
            "Martinez", "Anderson", "Taylor", "Thomas", "Hernandez", "Moore", "Martin", "Jackson", "Thompson", "White",
            "Harris", "Clark", "Lewis", "Robinson", "Walker", "Young", "Allen", "King", "Wright", "Scott"
        )

        return if (nameType.contains("tây", ignoreCase = true) || nameType.contains("western", ignoreCase = true)) {
            val f = westernFirst.random()
            val l = westernLast.random()
            "$f $l"
        } else {
            // Tên Việt chuẩn: 50% 3 từ (Họ + Đệm + Tên) và 50% 2 từ (Đệm/Họ + Tên) như Thùy Dung, Ngọc Anh, Nguyễn Linh
            val isThreeWords = (1..100).random() > 40
            if (isThreeWords) {
                val f = vietnameseFirst.random()
                val m = vietnameseMiddle.random()
                val l = vietnameseLast.random()
                "$f $m $l"
            } else {
                val m = vietnameseMiddle.random()
                val l = vietnameseLast.random()
                "$m $l"
            }
        }
    }
}

