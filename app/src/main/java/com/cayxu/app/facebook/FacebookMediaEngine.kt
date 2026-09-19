package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
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

    fun getProfileMedia(targetId: String? = null, tokenParam: String? = null): ProfileMediaInfo? {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return null

        val target = targetId ?: userId ?: "me"
        val url = "$GRAPH_API_URL/$target?fields=id,name,picture.width(1024).height(1024){url,is_silhouette},cover{id,source}&access_token=$token"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", KATANA_USER_AGENT)
            .build()

        return try {
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

                ProfileMediaInfo(id, name, avatarUrl, isSilhouette, coverUrl, coverId)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun updateAvatar(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Token required", "")

        val target = targetId ?: userId ?: "me"
        val mediaType = MediaType.parse(mimeType)
        val fileBody = RequestBody.create(mediaType, imageBytes)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", "avatar.jpg", fileBody)
            .build()

        val request = Request.Builder()
            .url("$GRAPH_API_URL/$target/picture")
            .post(multipart)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val isOk = res.isSuccessful && !body.contains("error")
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val photoId = json?.optString("id", null)
                MediaResult(isOk, photoId, if (isOk) "Cập nhật ảnh đại diện thành công" else body, body)
            }
        } catch (e: Exception) {
            MediaResult(false, null, e.message, "")
        }
    }

    fun updateCoverPhoto(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Token required", "")

        val target = targetId ?: userId ?: "me"
        val mediaType = MediaType.parse(mimeType)
        val fileBody = RequestBody.create(mediaType, imageBytes)

        val uploadBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", "cover.jpg", fileBody)
            .addFormDataPart("no_feed", "true")
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
                return MediaResult(false, null, uploadRespStr, uploadRespStr)
            }
        } catch (e: Exception) {
            return MediaResult(false, null, e.message, "")
        }

        return updateCoverPhotoWithPhotoId(photoId, target, tokenParam = token)
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

        val target = targetId ?: userId ?: "me"
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
                MediaResult(isOk, photoId, if (isOk) "Cập nhật ảnh bìa thành công" else body, body)
            }
        } catch (e: Exception) {
            MediaResult(false, null, e.message, "")
        }
    }
}
