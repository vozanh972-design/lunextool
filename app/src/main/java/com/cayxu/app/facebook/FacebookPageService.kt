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
     * 1. Tạo Fanpage Facebook mới chuẩn Graph API (/me/accounts)
     */
    @Throws(Exception::class)
    fun createFacebookPage(
        pageName: String,
        userToken: String,
        category: String = "180164648685982" // Shopping & Retail ID
    ): JSONObject {
        val cleanToken = userToken.removePrefix("OAuth ").trim()
        
        // Category format chuẩn cho Facebook Graph API (category_list nhận JSON array các ID category)
        val categoryListJson = JSONArray().apply {
            put(category)
        }

        val formBody = FormBody.Builder()
            .add("name", pageName)
            .add("category", "COMMUNITY")
            .add("category_enum", "SHOPPING_RETAIL")
            .add("category_list", categoryListJson.toString())
            .add("about", "Trang thông tin $pageName")
            .add("access_token", cleanToken)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_BASE_URL/v19.0/me/accounts")
            .header("User-Agent", NativeSecurity.getFbKatanaUA())
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (json.has("error")) {
                val errObj = json.getJSONObject("error")
                val errMsg = errObj.optString("message", "Lỗi tạo Page Facebook")
                val errCode = errObj.optInt("code", 0)
                throw Exception("(#$errCode) $errMsg")
            }
            return json
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

