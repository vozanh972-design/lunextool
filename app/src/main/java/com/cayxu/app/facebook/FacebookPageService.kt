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
 * 1. Tạo Page / Profile Plus mới (Nút "+ Reg").
 * 2. Chuyển quyền Page / Chuyển Profile (Nút "Chuyển").
 * 3. Lấy danh sách Page của tài khoản.
 */
@Keep
class FacebookPageService {

    companion object {
        const val GRAPHQL_ENDPOINT = "https://graph.facebook.com/graphql"
        const val GRAPH_BASE_URL = "https://graph.facebook.com"
        const val BLOKS_PAGE_CREATION_APP_ID = "com.bloks.www.additional.profile.plus.creation.action.category.submit"
        const val BLOKS_PAGE_CREATION_VERSION = "338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d"
        const val PAGE_CREATION_CLIENT_DOC_ID = "119940804239956818821550724"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 1. Tạo Fanpage / Profile Plus mới (Nút "+ Reg Page")
     */
    @Throws(Exception::class)
    fun createProfilePlusPage(
        pageName: String,
        userToken: String,
        categoryId: String = "180164648685982" // Danh mục mặc định
    ): JSONObject {
        val clientInputParams = JSONObject().apply {
            put("page_id", "0")
            put("profile_plus_id", "0")
            put("cp_upsell_declined", 0)
            put("off_platform_creator_reachout_id", "")
            put("category_ids", JSONArray().put(categoryId))
            put("nav_chain", "...")
        }

        val serverParams = JSONObject().apply {
            put("referrer", "pages_tab_launch_point")
            put("INTERNAL__latency_qpl_marker_id", 36707139)
            put("creation_source", "android")
            put("name", pageName)
            put("variant", 5)
            put("screen", "category")
            put("INTERNAL__latency_qpl_instance_id", 55098533200051.0)
        }

        val rootParams = JSONObject().apply {
            put("client_input_params", clientInputParams)
            put("server_params", serverParams)
        }

        val outerParams = JSONObject().apply {
            put("params", rootParams.toString())
            put("bloks_versioning_id", BLOKS_PAGE_CREATION_VERSION)
            put("app_id", BLOKS_PAGE_CREATION_APP_ID)
        }

        val formBody = FormBody.Builder()
            .add("params", JSONObject().put("params", outerParams.toString()).toString())
            .add("bloks_versioning_id", BLOKS_PAGE_CREATION_VERSION)
            .add("app_id", BLOKS_PAGE_CREATION_APP_ID)
            .add("client_doc_id", PAGE_CREATION_CLIENT_DOC_ID)
            .add("method", "post")
            .add("format", "json")
            .add("locale", "vi_VN")
            .build()

        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .header("User-Agent", NativeSecurity.getFbKatanaUA())
            .header("Authorization", "OAuth $userToken")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            return JSONObject(body)
        }
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
     * Sinh tên ngẫu nhiên (Tên Việt hoặc Tên Tây)
     */
    fun generateRandomName(nameType: String): String {
        val vietnameseFirst = listOf("Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý")
        val vietnameseMiddle = listOf("Văn", "Thị", "Đức", "Minh", "Quốc", "Thanh", "Hải", "Tuấn", "Hoàng", "Gia", "Bảo", "Xuân", "Thu", "Ngọc")
        val vietnameseLast = listOf("Anh", "Bình", "Cường", "Dũng", "Em", "Giang", "Hương", "Huy", "Khánh", "Linh", "Long", "Mai", "Nam", "Nhi", "Phúc", "Quân", "Sơn", "Tâm", "Thảo", "Trang", "Tuấn", "Vy", "Yến")

        val westernFirst = listOf("James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles", "Emma", "Olivia", "Sophia", "Ava", "Isabella", "Mia", "Emily", "Abigail", "Harper", "Ella")
        val westernLast = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Garcia", "Rodriguez", "Wilson", "Martinez", "Anderson", "Taylor", "Thomas", "Hernandez", "Moore", "Martin", "Jackson", "Thompson", "White")

        val topics = listOf("Store", "Shop", "Official", "Studio", "Media", "Digital", "Vlog", "Channel", "Daily", "Blog")

        return if (nameType.contains("tây", ignoreCase = true) || nameType.contains("western", ignoreCase = true)) {
            val f = westernFirst.random()
            val l = westernLast.random()
            val t = topics.random()
            "$f $l $t"
        } else {
            val f = vietnameseFirst.random()
            val m = vietnameseMiddle.random()
            val l = vietnameseLast.random()
            val t = listOf("Shop", "Store", "Fashion", "Boutique", "Official", "Online", "Gia Dụng", "Review").random()
            "$f $m $l $t"
        }
    }
}

