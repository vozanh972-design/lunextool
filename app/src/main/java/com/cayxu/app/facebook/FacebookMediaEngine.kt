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
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPH_API_URL = "https://graph.facebook.com/v21.0"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
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

    /**
     * 100% Graph API: Lấy thông tin Avatar HD 1024x1024 và Ảnh bìa (Cover) qua Token
     */
    fun getProfileMedia(
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null // Giữ tương thích signature cũ nếu có caller
    ): ProfileMediaInfo? {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId

        if (token.isNotEmpty()) {
            val url = "$GRAPH_API_URL/$target?fields=id,name,picture.width(1024).height(1024){url,is_silhouette},cover{id,source}&access_token=$token"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
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

        val fallbackId = if (target == "me") "me" else target
        return ProfileMediaInfo(
            id = fallbackId,
            name = null,
            avatarUrl = "$GRAPH_API_URL/$fallbackId/picture?type=large${if (token.isNotEmpty()) "&access_token=$token" else ""}",
            isSilhouette = false,
            coverUrl = null,
            coverId = null
        )
    }

    /**
     * 100% Graph API: Cập nhật Avatar qua Token (không dùng cookie, không lai tạp)
     * Bước 1: Upload file ảnh vào /{target}/photos (published=true) lấy photo_id
     * Bước 2: Thiết lập làm Avatar qua /{target}/picture với tham số photo={photo_id}
     */
    fun updateAvatar(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null // Giữ tham số tương thích, không dùng cookie
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Cần có Access Token để đổi avatar", "")

        val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId
        val mediaType = mimeType.toMediaTypeOrNull()
        val fileBody = imageBytes.toRequestBody(mediaType)

        // 1. Upload ảnh lên Graph API /{target}/photos
        val uploadBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("access_token", token)
            .addFormDataPart("published", "true")
            .addFormDataPart("source", "avatar_${System.currentTimeMillis()}.jpg", fileBody)
            .build()

        val uploadReq = Request.Builder()
            .url("$GRAPH_API_URL/$target/photos?access_token=$token")
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

        // 2. Gán photoId làm Avatar qua /{target}/picture
        val setPicBody = FormBody.Builder()
            .add("access_token", token)
            .add("photo", photoId)
            .add("picture", photoId)
            .build()

        val setPicReq = Request.Builder()
            .url("$GRAPH_API_URL/$target/picture?access_token=$token")
            .post(setPicBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        try {
            val setPicRes = httpClient.newCall(setPicReq).execute()
            val setPicStr = setPicRes.body?.string() ?: ""
            if (setPicRes.isSuccessful && !setPicStr.contains("\"error\"")) {
                return MediaResult(true, photoId, "Cập nhật ảnh đại diện thành công", setPicStr)
            }
        } catch (_: Exception) {}

        // Fallback: POST /{target} với picture=photoId
        val setPicBody2 = FormBody.Builder()
            .add("access_token", token)
            .add("picture", photoId)
            .build()

        val setPicReq2 = Request.Builder()
            .url("$GRAPH_API_URL/$target?access_token=$token")
            .post(setPicBody2)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(setPicReq2).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"error\"")
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val errMsg = json?.optJSONObject("error")?.optString("message") ?: body
                MediaResult(isOk, photoId, if (isOk) "Cập nhật ảnh đại diện thành công" else "Lỗi đặt Avatar: $errMsg", body)
            }
        } catch (e: Exception) {
            MediaResult(false, null, e.message, "")
        }
    }

    /**
     * 100% Graph API: Cập nhật Ảnh Bìa (Cover Photo) qua Token (không dùng cookie, không lai tạp)
     * Bước 1: Upload ảnh vào /{target}/photos (published=true) lấy photo_id
     * Bước 2: Thiết lập làm Cover qua POST /{target} với tham số cover={photo_id}, offset_x, offset_y
     */
    fun updateCoverPhoto(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null // Giữ tham số tương thích, không dùng cookie
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Cần có Access Token để đổi ảnh bìa", "")

        val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId
        val mediaType = mimeType.toMediaTypeOrNull()
        val fileBody = imageBytes.toRequestBody(mediaType)

        // 1. Upload ảnh bìa lên Graph API /{target}/photos
        val uploadBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("access_token", token)
            .addFormDataPart("published", "true")
            .addFormDataPart("source", "cover_${System.currentTimeMillis()}.jpg", fileBody)
            .build()

        val uploadReq = Request.Builder()
            .url("$GRAPH_API_URL/$target/photos?access_token=$token")
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
                return MediaResult(false, null, "Lỗi tải ảnh bìa lên: $errMsg", uploadRespStr)
            }
        } catch (e: Exception) {
            return MediaResult(false, null, e.message, "")
        }

        // 2. Gán photoId làm Cover qua POST /{target}
        val res1 = updateCoverPhotoWithPhotoId(photoId, target, tokenParam = token)
        if (res1.isSuccess) return res1

        // Nếu target đang là numeric UID mà bị lỗi thì thử gọi trực tiếp với "me"
        if (target != "me") {
            val res2 = updateCoverPhotoWithPhotoId(photoId, "me", tokenParam = token)
            if (res2.isSuccess) return res2
        }
        return res1
    }

    fun updateCoverPhotoWithPhotoId(
        photoId: String,
        targetId: String? = null,
        offsetX: Int = 50,
        offsetY: Int = 50,
        tokenParam: String? = null
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Cần có Access Token để đổi ảnh bìa", "")

        val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId
        val formBody = FormBody.Builder()
            .add("access_token", token)
            .add("cover", photoId)
            .add("offset_x", offsetX.toString())
            .add("offset_y", offsetY.toString())
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$target?access_token=$token")
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("\"error\"")
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val errMsg = json?.optJSONObject("error")?.optString("message") ?: body
                MediaResult(isOk, photoId, if (isOk) "Cập nhật ảnh bìa thành công" else "Lỗi đặt ảnh bìa: $errMsg", body)
            }
        } catch (e: Exception) {
            MediaResult(false, null, e.message, "")
        }
    }
}
