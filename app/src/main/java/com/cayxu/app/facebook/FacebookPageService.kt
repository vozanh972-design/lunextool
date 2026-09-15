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
        category: String = "180164648685982"
    ): JSONObject {
        val cleanToken = userToken.removePrefix("OAuth ").trim()
        
        // Danh sách các tổ hợp Category chuẩn Graph API để tự động fallback nếu dính #152
        val categoryPayloads = listOf(
            mapOf("category_list" to "[\"180164648685982\"]", "category_enum" to "SHOPPING_RETAIL"),
            mapOf("category_list" to "[\"2200\"]", "category_enum" to "COMMUNITY"),
            mapOf("category_list" to "[\"180164648685982\"]"),
            mapOf("category_list" to "[\"2200\"]"),
            mapOf("category_enum" to "COMMUNITY"),
            mapOf("category_enum" to "SHOPPING_RETAIL")
        )

        var lastException: Exception? = null

        for (payload in categoryPayloads) {
            try {
                val builder = FormBody.Builder()
                    .add("name", pageName)
                    .add("about", "Trang $pageName")
                    .add("access_token", cleanToken)

                payload.forEach { (k, v) ->
                    builder.add(k, v)
                }

                val request = Request.Builder()
                    .url("$GRAPH_BASE_URL/v19.0/me/accounts")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .post(builder.build())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: "{}"
                    val json = try {
                        JSONObject(body)
                    } catch (_: Throwable) {
                        JSONObject().put("error", JSONObject().put("message", "Phản hồi không hợp lệ từ máy chủ"))
                    }

                    if (json.has("error")) {
                        val errObj = json.optJSONObject("error")
                        val errMsg = errObj?.optString("message", "Lỗi tạo Page") ?: "Lỗi tạo Page"
                        val errCode = errObj?.optInt("code", 0) ?: 0
                        val ex = Exception("(#$errCode) $errMsg")
                        lastException = ex

                        // Nếu lỗi do category (#152), thử payload category tiếp theo
                        if (errCode == 152 || errMsg.contains("Category", ignoreCase = true)) {
                            return@use
                        } else {
                            throw ex
                        }
                    } else {
                        return json
                    }
                }
            } catch (e: Exception) {
                if (e.message?.contains("152") == true || e.message?.contains("Category", ignoreCase = true) == true) {
                    lastException = e
                    continue
                } else {
                    throw e
                }
            }
        }

        throw (lastException ?: Exception("Không thể tạo Page: Lỗi phân loại danh mục"))
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

