package com.cayxu.app.facebook

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Trích xuất từ class `Li2/n;`, `Li2/l;`, `Li2/k;` trong APK.
 * Xử lý:
 * 1. Tạo Page / Profile Plus mới (Nút "+ Reg").
 * 2. Chuyển quyền Page / Chuyển Profile (Nút "Chuyển").
 */
class FacebookPageService {

    companion object {
        const val GRAPHQL_ENDPOINT = "https://graph.facebook.com/graphql"
        const val BLOKS_PAGE_CREATION_APP_ID = "com.bloks.www.additional.profile.plus.creation.action.category.submit"
        const val BLOKS_PAGE_CREATION_VERSION = "338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d"
        const val PAGE_CREATION_CLIENT_DOC_ID = "119940804239956818821550724"

        const val FB_KATANA_UA = "[FBAN/FB4A;FBAV/537.0.0.47.77;FBPN/com.facebook.katana;]"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 1. Tạo Fanpage / Profile Plus mới (Tương ứng Nút "+ Reg" trong Ảnh 2)
     */
    @Throws(Exception::class)
    fun createProfilePlusPage(
        pageName: String,
        userToken: String,
        categoryId: String = "180164648685982" // Danh mục mặc định từ APK
    ): String {
        val clientInputParams = JSONObject().apply {
            put("page_id", "0")
            put("profile_plus_id", "0")
            put("cp_upsell_declined", 0)
            put("off_platform_creator_reachout_id", "")
            put("category_ids", org.json.JSONArray().put(categoryId))
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
            .header("User-Agent", FB_KATANA_UA)
            .header("Authorization", "OAuth $userToken")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    /**
     * 2. Chuyển quyền quản trị Page sang UID khác (Tương ứng Nút "Chuyển" trong Ảnh 2)
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
            return response.isSuccessful && body.contains(""success":true")
        }
    }
}
