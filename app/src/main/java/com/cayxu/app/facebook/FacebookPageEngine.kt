package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookPageItem
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Keep
class FacebookPageEngine(
    private var accessToken: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPHQL_URL = "https://graph.facebook.com/graphql"
        const val GRAPH_API_URL = "https://graph.facebook.com/v21.0"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
    }

    @Keep
    data class FacebookPage(
        val pageId: String,
        val pageName: String,
        val pageToken: String? = null,
        val category: String? = null,
        val uid615: String = pageId,
        val isProfilePlus: Boolean = pageId.startsWith("615")
    )

    @Keep
    data class PageCreationResult(
        val isSuccess: Boolean,
        val pageId: String? = null,
        val profilePlusId: String? = null,
        val pageName: String = "",
        val message: String? = null,
        val rawResponse: String = ""
    )

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

    /**
     * Lấy Profile Plus ID (UID 615) cho Page.
     * delegate_page_id chính là UID 615 của Page Pro5!
     */
    fun fetchProfilePlusIdForPage(pageId: String, tokenParam: String? = null): String? {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return null

        val url = "$GRAPH_API_URL/$pageId?fields=id,name,delegate_page_id&access_token=$token"
        val request = Request.Builder().url(url).get().header("User-Agent", KATANA_USER_AGENT).build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: "{}"
                val json = JSONObject(body)
                val delegateId = json.optString("delegate_page_id", "")
                if (delegateId.isNotEmpty()) delegateId else json.optString("id", pageId)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lấy danh sách Pages với UID 615 (Ưu tiên tuyệt đối UID 615, không dùng ID page thông thường).
     */
    fun getAdminedPages(tokenParam: String? = null): List<FacebookPageItem> {
        val list = mutableListOf<FacebookPageItem>()
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return list

        val url = "$GRAPH_API_URL/me/accounts?fields=id,name,access_token,category,tasks&limit=100&access_token=$token"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: "{}"
                val json = JSONObject(body)
                if (json.has("data")) {
                    val arr = json.getJSONArray("data")
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val id = item.optString("id", "")
                        val name = item.optString("name", "")
                        val pToken = item.optString("access_token", "")
                        if (id.isNotEmpty()) {
                            // Xác định UID 615
                            var uid615 = id
                            if (id.startsWith("615")) {
                                uid615 = id
                            } else {
                                val fetched615 = fetchProfilePlusIdForPage(id, token)
                                if (!fetched615.isNullOrBlank() && fetched615.startsWith("615")) {
                                    uid615 = fetched615
                                }
                            }

                            list.add(
                                FacebookPageItem(
                                    pageId = id,
                                    pageName = name,
                                    pageToken = pToken,
                                    additionalProfileId = uid615,
                                    avatar = "https://graph.facebook.com/v21.0/$uid615/picture?type=large",
                                    isLive = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun createPage(pageName: String, categoryId: String = "180164648685982", tokenParam: String? = null): PageCreationResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) {
            return PageCreationResult(false, null, null, pageName, "Token required", "")
        }

        val innerParams = JSONObject().apply {
            put("client_input_params", JSONObject().apply {
                put("page_id", "0")
                put("profile_plus_id", "0")
                put("category_ids", JSONArray().put(categoryId))
            })
            put("server_params", JSONObject().apply {
                put("referrer", "pages_tab_launch_point")
                put("creation_source", "android")
                put("name", pageName)
                put("variant", 5)
                put("screen", "category")
            })
        }

        val level1 = JSONObject().apply {
            put("params", JSONObject().apply { put("params", innerParams.toString()) }.toString())
            put("bloks_versioning_id", "338f8ead5977a2c41eba3e92584dcf1d132e8b7928f1f5796662ec064023047d")
            put("app_id", "com.bloks.www.additional.profile.plus.creation.action.category.submit")
        }

        val variables = JSONObject().apply {
            put("params", level1)
            put("scale", "2")
        }

        val formBody = FormBody.Builder()
            .add("method", "post")
            .add("format", "json")
            .add("client_doc_id", "119940804239956818821550724")
            .add("variables", variables.toString())
            .build()

        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .header("X-FB-Friendly-Name", "AdditionalProfilePlusCreation")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                var pageId: String? = null
                var profilePlusId: String? = null

                val p615Regex = Regex("""615\d{12,}""")
                p615Regex.find(body)?.let { profilePlusId = it.value }

                val idRegex = Regex("""["'](?:ADDITIONAL_PROFILE_PLUS_CREATION:)?(?:profile_plus_id|page_id)["']\s*,\s*["'](\d+)["']""")
                idRegex.find(body)?.let {
                    val foundId = it.groupValues[1]
                    if (foundId.startsWith("615")) profilePlusId = foundId else pageId = foundId
                }

                val finalId = profilePlusId ?: pageId
                val isOk = finalId != null && finalId.isNotEmpty()

                PageCreationResult(
                    isSuccess = isOk,
                    pageId = finalId,
                    profilePlusId = profilePlusId,
                    pageName = pageName,
                    message = if (isOk) "Tạo trang thành công" else body,
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            PageCreationResult(false, null, null, pageName, e.message, "")
        }
    }

    fun chuyenPageFullQuyen(pageId: String, targetUserId: String, pageToken: String? = null): PageActionResult {
        val token = (pageToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return PageActionResult(false, pageId, targetUserId, "Token required", "")

        val tasks = JSONArray().apply {
            put("MANAGE")
            put("CREATE_CONTENT")
            put("MESSAGING")
            put("MODERATE")
            put("COMMUNITY_ACTIVITY")
            put("ADVERTISE")
            put("ANALYZE")
        }

        val formBody = FormBody.Builder()
            .add("user", targetUserId)
            .add("tasks", tasks.toString())
            .add("access_token", token)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$pageId/assigned_users")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                PageActionResult(isOk, pageId, targetUserId, if (isOk) "Chuyển full quyền thành công" else body, body)
            }
        } catch (e: Exception) {
            PageActionResult(false, pageId, targetUserId, e.message, "")
        }
    }

    fun leavePage(pageId: String, myUserId: String = "me", pageToken: String? = null): PageActionResult {
        val token = (pageToken ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return PageActionResult(false, pageId, myUserId, "Token required", "")

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$pageId/assigned_users?user=$myUserId&access_token=$token")
            .delete()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && (body.contains("\"success\":true") || !body.contains("\"error\""))
                PageActionResult(isOk, pageId, myUserId, if (isOk) "Rời page thành công" else body, body)
            }
        } catch (e: Exception) {
            PageActionResult(false, pageId, myUserId, e.message, "")
        }
    }
}
