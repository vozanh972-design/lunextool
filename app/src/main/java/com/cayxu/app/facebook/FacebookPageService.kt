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
     * 1. Tạo Profile Plus / Fanpage Facebook bằng Bloks GraphQL chuẩn App Katana (Android FB4A)
     * Trích xuất trực tiếp từ logic Python client_doc_id: 119940804239956818821550724
     */
    @Throws(Exception::class)
    fun createFacebookPage(
        pageName: String,
        userToken: String,
        category: String = "180164648685982"
    ): JSONObject {
        val cleanToken = userToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val url = "https://b-graph.facebook.com/graphql"

        val innerParams = JSONObject().apply {
            put("client_input_params", JSONObject().apply {
                put("page_id", "0")
                put("profile_plus_id", "0")
                put("cp_upsell_declined", 0)
                put("off_platform_creator_reachout_id", "")
                put("category_ids", JSONArray().apply { put(category) })
                put("nav_chain", "...")
            })
            put("server_params", JSONObject().apply {
                put("referrer", "pages_tab_launch_point")
                put("INTERNAL__latency_qpl_marker_id", 36707139)
                put("creation_source", "android")
                put("name", pageName)
                put("variant", 5)
                put("screen", "category")
                put("INTERNAL__latency_qpl_instance_id", 55098533200051L)
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
            .header("X-Tigon-Is-Retry", "False")
            .header("X-Graphql-Request-Purpose", "fetch")
            .header("X-Fb-Device-Group", "5427")
            .header("X-Graphql-Client-Library", "graphservice")
            .header("X-Fb-Net-Hni", "45201")
            .header("X-Fb-Sim-Hni", "45201")
            .header("X-Fb-Request-Analytics-Tags", "{\"network_tags\":{\"product\":\"350685531728\",\"purpose\":\"fetch\",\"request_category\":\"graphql\",\"retry_attempt\":\"0\"},\"application_tags\":\"graphservice\"}")
            .post(formBody)
            .build()

        return httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            if (body.contains("create_success")) {
                return@use JSONObject().put("status", 200).put("msg", "success").put("page_name", pageName)
            }

            if (body.contains("error", ignoreCase = true)) {
                val toastRegex = Regex("""Toast,\s*"([^"]+)"""")
                val match = toastRegex.find(body)
                if (match != null) {
                    val toastMsg = match.groupValues[1]
                    throw Exception(toastMsg)
                }
                if (body.contains("create_error") || body.contains("profile_creation_error")) {
                    throw Exception("Facebook lỗi tạo page (Giới hạn tài khoản)")
                }
            }

            val json = try { JSONObject(body) } catch (_: Throwable) { JSONObject() }
            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                val msg = errObj?.optString("message", "Lỗi tạo Page") ?: "Lỗi tạo Page"
                val code = errObj?.optInt("code", 0) ?: 0
                throw Exception("(#$code) $msg")
            }

            if (resp.isSuccessful) {
                json
            } else {
                throw Exception("HTTP ${resp.code}: $body")
            }
        }
    }

    /**
     * Tìm Page vừa tạo theo tên để lấy ID và Page Token
     */
    fun findPageByName(token: String, pageName: String, maxRetries: Int = 5, delayMs: Long = 2000L): FacebookPageItem? {
        val cleanToken = token.removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val url = "$GRAPH_BASE_URL/v19.0/me/accounts?access_token=$cleanToken&fields=id,name,access_token&limit=100"
        for (attempt in 0 until maxRetries) {
            try {
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.has("data")) {
                        val arr = json.getJSONArray("data")
                        for (i in 0 until arr.length()) {
                            val p = arr.getJSONObject(i)
                            val name = p.optString("name", "")
                            if (name.equals(pageName, ignoreCase = true)) {
                                val id = p.optString("id", "")
                                val pageToken = p.optString("access_token", "")
                                return FacebookPageItem(
                                    pageId = id,
                                    pageName = name,
                                    pageToken = pageToken,
                                    avatar = "$GRAPH_BASE_URL/$id/picture?type=large",
                                    isLive = true
                                )
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
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
     * 3. Lấy danh sách Pages của tài khoản
     */
    fun getPages(userToken: String): List<FacebookPageItem> {
        val list = mutableListOf<FacebookPageItem>()
        try {
            val url = "$GRAPH_BASE_URL/me/accounts?access_token=$userToken"
            val request = Request.Builder().url(url).get().build()
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
                        list.add(
                            FacebookPageItem(
                                pageId = id,
                                pageName = name,
                                pageToken = token,
                                avatar = "$GRAPH_BASE_URL/$id/picture?type=large",
                                isLive = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
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

