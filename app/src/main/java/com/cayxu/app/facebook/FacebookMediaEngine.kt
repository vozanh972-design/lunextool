package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Keep
class FacebookMediaEngine(
    private var accessToken: String? = null,
    private var userId: String? = null,
    private var cookieStr: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPH_API_URL = "https://graph.facebook.com/v21.0"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    @Keep
    data class ProfileMediaInfo(
        val id: String,
        val name: String? = null,
        val avatarUrl: String? = null,
        val isSilhouette: Boolean = false,
        val coverUrl: String? = null,
        val coverId: String? = null
    )

    @Keep
    data class MediaResult(
        val isSuccess: Boolean,
        val mediaId: String? = null,
        val message: String? = null,
        val rawResponse: String = ""
    )

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            builder.proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
        }
        builder.build()
    }

    fun setAccessToken(token: String) {
        this.accessToken = token
    }

    fun setUserId(uid: String) {
        this.userId = uid
    }

    fun setCookie(cookie: String) {
        this.cookieStr = cookie
    }

    /**
     * Lấy thông tin Avatar HD 1024x1024 và Ảnh bìa (Cover) qua Graph API hoặc mbasic Cookie
     */
    fun getProfileMedia(
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null
    ): ProfileMediaInfo? {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val cookie = (cookieParam ?: cookieStr)?.trim().orEmpty()

        // 1. Ưu tiên Graph API nếu có Token
        if (token.isNotEmpty()) {
            val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId
            val url = "$GRAPH_API_URL/$target?fields=id,name,picture.width(1024).height(1024){url,is_silhouette},cover{id,source}&access_token=$token"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", KATANA_USER_AGENT)
                .build()

            try {
                httpClient.newCall(request).execute().use { res ->
                    val body = res.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val id = json.optString("id", target)
                    val name = json.optString("name", null)

                    var avatarUrl: String? = null
                    var isSilhouette = false
                    if (json.has("picture")) {
                        val picObj = json.optJSONObject("picture")
                        val picData = picObj?.optJSONObject("data")
                        if (picData != null) {
                            avatarUrl = picData.optString("url", null)
                            isSilhouette = picData.optBoolean("is_silhouette", false)
                        }
                    }

                    var coverUrl: String? = null
                    var coverId: String? = null
                    if (json.has("cover")) {
                        val coverData = json.optJSONObject("cover")
                        if (coverData != null) {
                            coverUrl = coverData.optString("source", null)
                            coverId = coverData.optString("id", null)
                        }
                    }

                    if (avatarUrl.isNullOrBlank()) {
                        avatarUrl = "$GRAPH_API_URL/$id/picture?type=large&access_token=$token"
                    }

                    return ProfileMediaInfo(id, name, avatarUrl, isSilhouette, coverUrl, coverId)
                }
            } catch (_: Exception) {}
        }

        // 2. Fallback sang mbasic.facebook.com nếu có Cookie
        if (cookie.isNotEmpty() && (cookie.contains("c_user=") || cookie.contains("xs="))) {
            try {
                val req = Request.Builder()
                    .url("https://mbasic.facebook.com/me")
                    .header("User-Agent", WEB_USER_AGENT)
                    .header("Cookie", cookie)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { res ->
                    val html = res.body?.string() ?: ""
                    val cUserMatcher = Regex("""c_user=(\d+)""").find(cookie)
                    val uid = cUserMatcher?.groupValues?.get(1) ?: (targetId ?: "me")

                    // Avatar từ mbasic
                    var avatarUrl: String? = Regex("""<img[^>]+src="([^"]+)"[^>]+(?:class="[^"]*profpic[^"]*"|alt="[^"]*(?:ảnh đại diện|profile picture|avatar)[^"]*)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                        ?: Regex("""<img[^>]+(?:class="[^"]*profpic[^"]*"|alt="[^"]*(?:ảnh đại diện|profile picture|avatar)[^"]*")[^>]+src="([^"]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

                    // Ảnh bìa từ mbasic
                    var coverUrl: String? = Regex("""<img[^>]+src="([^"]+)"[^>]+(?:class="[^"]*cover[^"]*"|alt="[^"]*(?:ảnh bìa|cover photo)[^"]*)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                        ?: Regex("""<img[^>]+(?:class="[^"]*cover[^"]*"|alt="[^"]*(?:ảnh bìa|cover photo)[^"]*")[^>]+src="([^"]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

                    avatarUrl = avatarUrl?.replace("&amp;", "&")
                    coverUrl = coverUrl?.replace("&amp;", "&")

                    if (avatarUrl.isNullOrBlank()) {
                        avatarUrl = "$GRAPH_API_URL/$uid/picture?type=large"
                    }

                    return ProfileMediaInfo(uid, null, avatarUrl, false, coverUrl, null)
                }
            } catch (_: Exception) {}
        }

        val id = targetId ?: "me"
        return ProfileMediaInfo(id, null, "$GRAPH_API_URL/$id/picture?type=large", false, null, null)
    }

    /**
     * Cập nhật Avatar thật 100%:
     * Ưu tiên 1: Thực hiện qua Cookie Web (mbasic.facebook.com/profile_picture/) - chuẩn 100% tài khoản cá nhân.
     * Ưu tiên 2: Thực hiện qua Graph API /photos + /picture (dành cho Page hoặc Token hợp lệ).
     */
    fun updateAvatar(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null
    ): MediaResult {
        val cookie = (cookieParam ?: cookieStr)?.trim().orEmpty()
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()

        // -------------------------------------------------------------
        // PHƯƠNG PHÁP 1: Đổi qua Cookie Web mbasic.facebook.com (Chuẩn cá nhân)
        // -------------------------------------------------------------
        if (cookie.isNotBlank() && (cookie.contains("c_user=") || cookie.contains("xs="))) {
            try {
                val getReq = Request.Builder()
                    .url("https://mbasic.facebook.com/profile_picture/")
                    .header("User-Agent", WEB_USER_AGENT)
                    .header("Cookie", cookie)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                    .get()
                    .build()

                val getRes = httpClient.newCall(getReq).execute()
                val getHtml = getRes.body?.string() ?: ""

                val formAction = Regex("""<form[^>]+action="([^"]+)"""", RegexOption.IGNORE_CASE).find(getHtml)?.groupValues?.get(1)?.replace("&amp;", "&")
                val fbDtsg = Regex("""name="fb_dtsg"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE).find(getHtml)?.groupValues?.get(1)
                    ?: Regex("""value="([^"]+)"\s+name="fb_dtsg"""", RegexOption.IGNORE_CASE).find(getHtml)?.groupValues?.get(1)
                    ?: ""
                val jazoest = Regex("""name="jazoest"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE).find(getHtml)?.groupValues?.get(1)
                    ?: Regex("""value="([^"]+)"\s+name="jazoest"""", RegexOption.IGNORE_CASE).find(getHtml)?.groupValues?.get(1)
                    ?: ""

                if (!formAction.isNullOrBlank() && fbDtsg.isNotBlank()) {
                    val fullActionUrl = if (formAction.startsWith("http")) formAction else "https://mbasic.facebook.com$formAction"
                    val fileBody = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())

                    val postBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("fb_dtsg", fbDtsg)
                        .addFormDataPart("jazoest", jazoest)
                        .addFormDataPart("pic", "avatar.jpg", fileBody)
                        .addFormDataPart("submit", "Lưu")
                        .build()

                    val postReq = Request.Builder()
                        .url(fullActionUrl)
                        .header("User-Agent", WEB_USER_AGENT)
                        .header("Cookie", cookie)
                        .header("Referer", "https://mbasic.facebook.com/profile_picture/")
                        .post(postBody)
                        .build()

                    val postRes = httpClient.newCall(postReq).execute()
                    val postHtml = postRes.body?.string() ?: ""
                    if (postRes.isSuccessful && !postHtml.contains("login_form") && !postHtml.contains("checkpoint")) {
                        return MediaResult(true, null, "Cập nhật ảnh đại diện thành công", postHtml)
                    }
                }
            } catch (e: Exception) {
                if (token.isBlank()) {
                    return MediaResult(false, null, "Lỗi đổi avatar qua Cookie: ${e.message}", "")
                }
            }
        }

        // -------------------------------------------------------------
        // PHƯƠNG PHÁP 2: Đổi qua Graph API (Page hoặc User có Token)
        // -------------------------------------------------------------
        if (token.isNotBlank()) {
            val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId
            val mediaType = mimeType.toMediaTypeOrNull()
            val fileBody = imageBytes.toRequestBody(mediaType)

            // 1. Tải ảnh lên /photos
            val uploadBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("source", "avatar.jpg", fileBody)
                .addFormDataPart("no_feed", "true")
                .addFormDataPart("published", "true")
                .build()

            val uploadReq = Request.Builder()
                .url("$GRAPH_API_URL/$target/photos")
                .post(uploadBody)
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()

            val photoId: String
            try {
                val uploadRes = httpClient.newCall(uploadReq).execute()
                val uploadRespStr = uploadRes.body?.string() ?: ""
                val json = try { JSONObject(uploadRespStr) } catch (_: Exception) { null }
                photoId = json?.optString("id", "") ?: ""
                if (photoId.isEmpty()) {
                    val errMsg = json?.optJSONObject("error")?.optString("message") ?: uploadRespStr
                    return MediaResult(false, null, "Lỗi tải ảnh lên: $errMsg", uploadRespStr)
                }
            } catch (e: Exception) {
                return MediaResult(false, null, e.message, "")
            }

            // 2. Gán làm Avatar Page qua /{target}/picture với photo={photoId}
            val setPicReq = Request.Builder()
                .url("$GRAPH_API_URL/$target/picture")
                .post(FormBody.Builder().add("photo", photoId).add("picture", photoId).build())
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()

            try {
                val setPicRes = httpClient.newCall(setPicReq).execute()
                val setPicStr = setPicRes.body?.string() ?: ""
                if (setPicRes.isSuccessful && !setPicStr.contains("error")) {
                    return MediaResult(true, photoId, "Cập nhật ảnh đại diện thành công", setPicStr)
                }
            } catch (_: Exception) {}

            // Fallback sang endpoint /picture?photo_id=...
            val setPicReq2 = Request.Builder()
                .url("$GRAPH_API_URL/$target/picture?photo_id=$photoId")
                .post(FormBody.Builder().build())
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()

            return try {
                httpClient.newCall(setPicReq2).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val isOk = res.isSuccessful && !body.contains("error")
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val errMsg = json?.optJSONObject("error")?.optString("message") ?: body
                    MediaResult(isOk, photoId, if (isOk) "Cập nhật ảnh đại diện thành công" else "Lỗi đặt Avatar: $errMsg", body)
                }
            } catch (e: Exception) {
                MediaResult(false, null, e.message, "")
            }
        }

        return MediaResult(false, null, "Cần có Cookie hoặc Token để đổi ảnh đại diện", "")
    }

    /**
     * Cập nhật Ảnh Bìa (Cover Photo) thật 100%:
     * Ưu tiên 1: Thực hiện qua Cookie Web mbasic.facebook.com (Chuẩn cá nhân không bị OAuthException code 1).
     * Ưu tiên 2: Thực hiện qua Graph API /photos + POST cover (Page hoặc Token).
     */
    fun updateCoverPhoto(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null
    ): MediaResult {
        val cookie = (cookieParam ?: cookieStr)?.trim().orEmpty()
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()

        // -------------------------------------------------------------
        // PHƯƠNG PHÁP 1: Đổi qua Cookie Web mbasic.facebook.com
        // -------------------------------------------------------------
        if (cookie.isNotBlank() && (cookie.contains("c_user=") || cookie.contains("xs="))) {
            try {
                // Lấy HTML trang me để tìm form đổi bìa
                val meReq = Request.Builder()
                    .url("https://mbasic.facebook.com/me")
                    .header("User-Agent", WEB_USER_AGENT)
                    .header("Cookie", cookie)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                    .get()
                    .build()

                val meRes = httpClient.newCall(meReq).execute()
                val meHtml = meRes.body?.string() ?: ""

                var coverUploadUrl = Regex("""href="(/photos/upload/\?[^"]*upload_source=cover_photo[^"]*)"""", RegexOption.IGNORE_CASE).find(meHtml)?.groupValues?.get(1)?.replace("&amp;", "&")
                if (coverUploadUrl.isNullOrBlank()) {
                    coverUploadUrl = Regex("""href="(/photos/upload/\?[^"]*)"""", RegexOption.IGNORE_CASE).find(meHtml)?.groupValues?.get(1)?.replace("&amp;", "&")
                }
                if (coverUploadUrl.isNullOrBlank()) {
                    coverUploadUrl = "/photos/upload/?upload_source=cover_photo"
                }

                val fullCoverUploadUrl = if (coverUploadUrl.startsWith("http")) coverUploadUrl else "https://mbasic.facebook.com$coverUploadUrl"
                val getFormReq = Request.Builder()
                    .url(fullCoverUploadUrl)
                    .header("User-Agent", WEB_USER_AGENT)
                    .header("Cookie", cookie)
                    .get()
                    .build()

                val getFormRes = httpClient.newCall(getFormReq).execute()
                val formHtml = getFormRes.body?.string() ?: ""

                val formAction = Regex("""<form[^>]+action="([^"]+)"""", RegexOption.IGNORE_CASE).find(formHtml)?.groupValues?.get(1)?.replace("&amp;", "&")
                val fbDtsg = Regex("""name="fb_dtsg"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE).find(formHtml)?.groupValues?.get(1)
                    ?: Regex("""value="([^"]+)"\s+name="fb_dtsg"""", RegexOption.IGNORE_CASE).find(formHtml)?.groupValues?.get(1)
                    ?: ""
                val jazoest = Regex("""name="jazoest"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE).find(formHtml)?.groupValues?.get(1)
                    ?: Regex("""value="([^"]+)"\s+name="jazoest"""", RegexOption.IGNORE_CASE).find(formHtml)?.groupValues?.get(1)
                    ?: ""

                val fileFieldName = Regex("""<input[^>]+type="file"[^>]+name="([^"]+)"""", RegexOption.IGNORE_CASE).find(formHtml)?.groupValues?.get(1) ?: "file"

                if (!formAction.isNullOrBlank() && fbDtsg.isNotBlank()) {
                    val fullPostUrl = if (formAction.startsWith("http")) formAction else "https://mbasic.facebook.com$formAction"
                    val filePart = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())

                    val postBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("fb_dtsg", fbDtsg)
                        .addFormDataPart("jazoest", jazoest)
                        .addFormDataPart(fileFieldName, "cover.jpg", filePart)
                        .addFormDataPart("submit", "Lưu")
                        .build()

                    val postReq = Request.Builder()
                        .url(fullPostUrl)
                        .header("User-Agent", WEB_USER_AGENT)
                        .header("Cookie", cookie)
                        .header("Referer", fullCoverUploadUrl)
                        .post(postBody)
                        .build()

                    val postRes = httpClient.newCall(postReq).execute()
                    val postHtml = postRes.body?.string() ?: ""
                    if (postRes.isSuccessful && !postHtml.contains("login_form") && !postHtml.contains("checkpoint")) {
                        return MediaResult(true, null, "Cập nhật ảnh bìa thành công", postHtml)
                    }
                }
            } catch (e: Exception) {
                if (token.isBlank()) {
                    return MediaResult(false, null, "Lỗi đổi ảnh bìa qua Cookie: ${e.message}", "")
                }
            }
        }

        // -------------------------------------------------------------
        // PHƯƠNG PHÁP 2: Đổi qua Graph API (Page hoặc Token)
        // -------------------------------------------------------------
        if (token.isNotBlank()) {
            val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId
            val mediaType = mimeType.toMediaTypeOrNull()
            val fileBody = imageBytes.toRequestBody(mediaType)

            // 1. Tải ảnh lên /photos
            val uploadBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("source", "cover.jpg", fileBody)
                .addFormDataPart("no_feed", "true")
                .addFormDataPart("published", "true")
                .build()

            val uploadReq = Request.Builder()
                .url("$GRAPH_API_URL/$target/photos")
                .post(uploadBody)
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()

            val photoId: String
            try {
                val uploadRes = httpClient.newCall(uploadReq).execute()
                val uploadRespStr = uploadRes.body?.string() ?: ""
                val json = try { JSONObject(uploadRespStr) } catch (_: Exception) { null }
                photoId = json?.optString("id", "") ?: ""
                if (photoId.isEmpty()) {
                    val errMsg = json?.optJSONObject("error")?.optString("message") ?: uploadRespStr
                    return MediaResult(false, null, "Lỗi tải ảnh bìa: $errMsg", uploadRespStr)
                }
            } catch (e: Exception) {
                return MediaResult(false, null, e.message, "")
            }

            // 2. Gán làm Cover qua POST /{target} với cover=photoId
            val res1 = updateCoverPhotoWithPhotoId(photoId, target, tokenParam = token)
            if (res1.isSuccess) return res1

            if (target != "me") {
                val res2 = updateCoverPhotoWithPhotoId(photoId, "me", tokenParam = token)
                if (res2.isSuccess) return res2
            }
            return res1
        }

        return MediaResult(false, null, "Cần có Cookie hoặc Token để đổi ảnh bìa", "")
    }

    fun updateCoverPhotoWithPhotoId(
        photoId: String,
        targetId: String? = null,
        offsetX: Int = 50,
        offsetY: Int = 50,
        tokenParam: String? = null
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Token required", "")

        val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId
        val formBody = FormBody.Builder()
            .add("cover", photoId)
            .add("offset_x", offsetX.toString())
            .add("offset_y", offsetY.toString())
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$target")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("error")
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val errMsg = json?.optJSONObject("error")?.optString("message") ?: body
                MediaResult(isOk, photoId, if (isOk) "Cập nhật ảnh bìa thành công" else "Lỗi đặt ảnh bìa: $errMsg", body)
            }
        } catch (e: Exception) {
            MediaResult(false, null, e.message, "")
        }
    }
}
