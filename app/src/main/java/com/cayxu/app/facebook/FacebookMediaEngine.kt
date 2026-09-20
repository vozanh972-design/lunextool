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
                        val validId = if (id != "me" && id.isNotBlank()) id else (if (target != "me") target else "")
                        avatarUrl = if (validId.isNotEmpty()) "$GRAPH_API_URL/$validId/picture?type=large"
                                    else "$GRAPH_API_URL/me/picture?type=large&access_token=$token"
                    }

                    return ProfileMediaInfo(id, name, avatarUrl, isSilhouette, coverUrl, coverId)
                }
            } catch (_: Exception) {}
        }

        val fallbackId = if (target == "me") "me" else target
        return ProfileMediaInfo(
            id = fallbackId,
            name = null,
            avatarUrl = if (fallbackId != "me") "$GRAPH_API_URL/$fallbackId/picture?type=large"
                        else "$GRAPH_API_URL/me/picture?type=large${if (token.isNotEmpty()) "&access_token=$token" else ""}",
            isSilhouette = false,
            coverUrl = null,
            coverId = null
        )
    }

    /**
     * 100% Graph API: Cập nhật Avatar qua Token (không dùng cookie, không lai tạp)
     * Thử qua targetId và me để hỗ trợ cả Profile lẫn Page Token
     */
    /**
     * Lấy direct CDN URL từ Photo ID đã upload qua Graph API
     */
    fun getPhotoDirectUrl(photoId: String, tokenParam: String? = null): String? {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty() || photoId.isBlank()) return null
        return try {
            val req = Request.Builder()
                .url("$GRAPH_API_URL/$photoId?fields=id,source,images&access_token=$token")
                .get()
                .header("User-Agent", KATANA_USER_AGENT)
                .build()
            httpClient.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val src = json.optString("source", "")
                if (src.isNotBlank()) src
                else {
                    val images = json.optJSONArray("images")
                    images?.optJSONObject(0)?.optString("source", null)
                }
            }
        } catch (_: Exception) { null }
    }

    /**
     * 100% Graph API: Cập nhật Avatar qua Token (không dùng cookie, không lai tạp)
     * Thử qua targetId và me để hỗ trợ cả Profile lẫn Page Token
     */
    fun updateAvatar(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Cần có Access Token để đổi avatar", "")

        val mediaType = mimeType.toMediaTypeOrNull()
        val fileBody = imageBytes.toRequestBody(mediaType)

        val candidateEndpoints = mutableListOf<String>()
        if (!targetId.isNullOrBlank() && targetId != "me") {
            candidateEndpoints.add(targetId)
        }

        // Tự động kiểm tra ID thật mà Token gắn kèm nếu chưa có targetId cụ thể
        if (candidateEndpoints.isEmpty()) {
            try {
                val meReq = Request.Builder()
                    .url("$GRAPH_API_URL/me?fields=id&access_token=$token")
                    .get()
                    .header("User-Agent", KATANA_USER_AGENT)
                    .build()
                httpClient.newCall(meReq).execute().use { meRes ->
                    val meBody = meRes.body?.string() ?: ""
                    val meJson = try { JSONObject(meBody) } catch (_: Exception) { null }
                    val realId = meJson?.optString("id", "") ?: ""
                    if (realId.isNotEmpty()) {
                        candidateEndpoints.add(realId)
                    }
                }
            } catch (_: Exception) {}
        }
        candidateEndpoints.add("me")

        fun doUploadPhoto(ep: String): Pair<String, String> {
            val uploadBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", token)
                .addFormDataPart("published", "true")
                .addFormDataPart("source", "avatar_${System.currentTimeMillis()}.jpg", fileBody)
                .build()

            val uploadReq = Request.Builder()
                .url("$GRAPH_API_URL/$ep/photos")
                .post(uploadBody)
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()

            return try {
                httpClient.newCall(uploadReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val id = json?.optString("id", "") ?: ""
                    val err = json?.optJSONObject("error")?.optString("message") ?: body
                    Pair(id, err)
                }
            } catch (e: Exception) {
                Pair("", e.message ?: "Network error")
            }
        }

        var photoId = ""
        var errMsg = ""
        var successfulEndpoint = ""

        for (ep in candidateEndpoints) {
            val res = doUploadPhoto(ep)
            if (res.first.isNotEmpty()) {
                photoId = res.first
                successfulEndpoint = ep
                break
            } else {
                errMsg = res.second
            }
        }

        val targetForPic = if (successfulEndpoint.isNotEmpty()) successfulEndpoint else (targetId ?: "me")

        if (photoId.isNotEmpty()) {
            // Gán photoId làm Avatar qua /{target}/picture
            val setPicBody = FormBody.Builder()
                .add("access_token", token)
                .add("photo_id", photoId)
                .add("photo", photoId)
                .add("picture", photoId)
                .build()

            val setPicReq = Request.Builder()
                .url("$GRAPH_API_URL/$targetForPic/picture")
                .post(setPicBody)
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()

            try {
                val res = httpClient.newCall(setPicReq).execute()
                val body = res.body?.string() ?: ""
                if (res.isSuccessful && !body.contains("\"error\"")) {
                    return MediaResult(true, photoId, "Cập nhật ảnh đại diện thành công", body)
                }
            } catch (_: Exception) {}
        }

        // Fallback: Direct upload vào /{target}/picture
        try {
            val directPart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", token)
                .addFormDataPart("source", "avatar.jpg", fileBody)
                .build()
            val directReq = Request.Builder()
                .url("$GRAPH_API_URL/$targetForPic/picture")
                .post(directPart)
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()
            val directRes = httpClient.newCall(directReq).execute()
            val directStr = directRes.body?.string() ?: ""
            if (directRes.isSuccessful && !directStr.contains("\"error\"")) {
                val directJson = try { JSONObject(directStr) } catch (_: Exception) { null }
                val dId = directJson?.optString("id", photoId).orEmpty().ifBlank { photoId }
                return MediaResult(true, dId, "Cập nhật ảnh đại diện thành công", directStr)
            }
        } catch (_: Exception) {}

        return MediaResult(false, null, if (errMsg.isNotBlank()) "Lỗi tải ảnh đại diện: $errMsg" else "Lỗi cập nhật ảnh đại diện", "")
    }

    /**
     * 100% Graph API: Cập nhật Ảnh Bìa (Cover Photo) qua Token
     * Thử qua targetId và me để hỗ trợ cả Profile lẫn Page Token
     */
    fun updateCoverPhoto(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        targetId: String? = null,
        tokenParam: String? = null,
        cookieParam: String? = null
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Cần có Access Token để đổi ảnh bìa", "")

        val mediaType = mimeType.toMediaTypeOrNull()
        val fileBody = imageBytes.toRequestBody(mediaType)

        val candidateEndpoints = mutableListOf<String>()
        if (!targetId.isNullOrBlank() && targetId != "me") {
            candidateEndpoints.add(targetId)
        }

        // Tự động kiểm tra ID thật mà Token gắn kèm nếu chưa có targetId cụ thể
        if (candidateEndpoints.isEmpty()) {
            try {
                val meReq = Request.Builder()
                    .url("$GRAPH_API_URL/me?fields=id&access_token=$token")
                    .get()
                    .header("User-Agent", KATANA_USER_AGENT)
                    .build()
                httpClient.newCall(meReq).execute().use { meRes ->
                    val meBody = meRes.body?.string() ?: ""
                    val meJson = try { JSONObject(meBody) } catch (_: Exception) { null }
                    val realId = meJson?.optString("id", "") ?: ""
                    if (realId.isNotEmpty()) {
                        candidateEndpoints.add(realId)
                    }
                }
            } catch (_: Exception) {}
        }
        candidateEndpoints.add("me")

        fun doUploadCover(endpointTarget: String, withProfileCover: Boolean): Pair<String, String> {
            val uploadBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("access_token", token)
                .addFormDataPart("published", "true")
                .addFormDataPart("source", "cover_${System.currentTimeMillis()}.jpg", fileBody)
            if (withProfileCover) {
                uploadBodyBuilder.addFormDataPart("is_profile_cover", "true")
            }
            val uploadBody = uploadBodyBuilder.build()

            val uploadReq = Request.Builder()
                .url("$GRAPH_API_URL/$endpointTarget/photos")
                .post(uploadBody)
                .header("User-Agent", KATANA_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()

            return try {
                httpClient.newCall(uploadReq).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val id = json?.optString("id", "") ?: ""
                    val err = json?.optJSONObject("error")?.optString("message") ?: body
                    Pair(id, err)
                }
            } catch (e: Exception) {
                Pair("", e.message ?: "Network error")
            }
        }

        var photoId = ""
        var errMsg = ""
        var successfulEndpoint = ""

        for (ep in candidateEndpoints) {
            // Cách 1: Upload chuẩn không có cờ is_profile_cover (bắt buộc cho Page và hoạt động tốt cho Profile)
            var res = doUploadCover(ep, withProfileCover = false)
            if (res.first.isNotEmpty()) {
                photoId = res.first
                successfulEndpoint = ep
                break
            } else {
                errMsg = res.second
                // Cách 2: Nếu ep là me hoặc profile cá nhân, thử thêm cờ is_profile_cover
                if (ep == "me") {
                    res = doUploadCover(ep, withProfileCover = true)
                    if (res.first.isNotEmpty()) {
                        photoId = res.first
                        successfulEndpoint = ep
                        break
                    }
                }
            }
        }

        // Nếu cả các candidate đều lỗi (ví dụ token thuộc về Page ID khác chưa nằm trong list):
        if (photoId.isEmpty()) {
            try {
                val meReq = Request.Builder()
                    .url("$GRAPH_API_URL/me?fields=id&access_token=$token")
                    .get()
                    .header("User-Agent", KATANA_USER_AGENT)
                    .build()
                httpClient.newCall(meReq).execute().use { meRes ->
                    val meBody = meRes.body?.string() ?: ""
                    val meJson = try { JSONObject(meBody) } catch (_: Exception) { null }
                    val realId = meJson?.optString("id", "") ?: ""
                    if (realId.isNotEmpty() && realId !in candidateEndpoints) {
                        val res = doUploadCover(realId, withProfileCover = false)
                        if (res.first.isNotEmpty()) {
                            photoId = res.first
                            successfulEndpoint = realId
                        } else {
                            errMsg = res.second
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (photoId.isEmpty()) {
            return MediaResult(false, null, "Lỗi tải ảnh bìa lên: $errMsg", errMsg)
        }

        // Bước 2: Set photo này làm cover qua POST /{endpoint}
        val targetForCover = if (successfulEndpoint.isNotEmpty()) successfulEndpoint else (targetId ?: "me")
        val res1 = updateCoverPhotoWithPhotoId(photoId, targetForCover, tokenParam = token)
        if (res1.isSuccess) return res1

        if (targetForCover != "me") {
            val res2 = updateCoverPhotoWithPhotoId(photoId, "me", tokenParam = token)
            if (res2.isSuccess) return res2
        }
        return res1
    }

    fun updateCoverPhotoWithPhotoId(
        photoId: String,
        targetId: String? = null,
        offsetX: Int = 0,
        offsetY: Int = 0,
        tokenParam: String? = null
    ): MediaResult {
        val token = (tokenParam ?: accessToken ?: "").removePrefix("OAuth ").removePrefix("Bearer ").trim()
        if (token.isEmpty()) return MediaResult(false, null, "Cần có Access Token để đổi ảnh bìa", "")

        val target = if (targetId.isNullOrBlank() || targetId == "me") "me" else targetId

        // Thử cách 1: cover={"cover_id":"<photoId>","offset_x":0,"offset_y":0}
        val coverJson = """{"cover_id":"$photoId","offset_x":$offsetX,"offset_y":$offsetY}"""
        val formBody1 = FormBody.Builder()
            .add("access_token", token)
            .add("cover", coverJson)
            .build()

        val req1 = Request.Builder()
            .url("$GRAPH_API_URL/$target")
            .post(formBody1)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        try {
            val res1 = httpClient.newCall(req1).execute()
            val body1 = res1.body?.string() ?: ""
            val json1 = try { JSONObject(body1) } catch (_: Exception) { null }
            val hasError1 = json1?.has("error") == true
            if (res1.isSuccessful && !hasError1) {
                return MediaResult(true, photoId, "Cập nhật ảnh bìa thành công", body1)
            }
        } catch (_: Exception) {}

        // Thử cách 2: cover=<photoId> string đơn giản
        val formBody2 = FormBody.Builder()
            .add("access_token", token)
            .add("cover", photoId)
            .add("offset_x", offsetX.toString())
            .add("offset_y", offsetY.toString())
            .build()

        val req2 = Request.Builder()
            .url("$GRAPH_API_URL/$target")
            .post(formBody2)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("Authorization", "OAuth $token")
            .build()

        try {
            val res2 = httpClient.newCall(req2).execute()
            val body2 = res2.body?.string() ?: ""
            val json2 = try { JSONObject(body2) } catch (_: Exception) { null }
            val hasError2 = json2?.has("error") == true
            if (res2.isSuccessful && !hasError2) {
                return MediaResult(true, photoId, "Cập nhật ảnh bìa thành công", body2)
            }
        } catch (_: Exception) {}

        // Thử cách 3: fallback POST /me nếu target != "me"
        if (target != "me") {
            try {
                val fallbackReq = Request.Builder()
                    .url("$GRAPH_API_URL/me")
                    .post(formBody1)
                    .header("User-Agent", KATANA_USER_AGENT)
                    .header("Authorization", "OAuth $token")
                    .build()
                val res3 = httpClient.newCall(fallbackReq).execute()
                val body3 = res3.body?.string() ?: ""
                val json3 = try { JSONObject(body3) } catch (_: Exception) { null }
                if (res3.isSuccessful && json3?.has("error") != true) {
                    return MediaResult(true, photoId, "Cập nhật ảnh bìa thành công", body3)
                }
            } catch (_: Exception) {}
        }

        return MediaResult(false, null, "Không thể thiết lập ảnh bìa", "")
    }
}
