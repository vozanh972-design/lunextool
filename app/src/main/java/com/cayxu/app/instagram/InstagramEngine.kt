package com.cayxu.app.instagram

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import com.cayxu.app.facebook.TotpGenerator

/**
 * 100% INSTAGRAM APP NATIVE ENGINE
 * Trích xuất nguyên bản từ Instagram 447.0.0.55.81 Android APK.
 * Bao gồm đầy đủ logic:
 * - Đăng nhập tài khoản mật khẩu + 2FA TOTP
 * - Lấy Avatar HD & Profile Info
 * - Đổi ảnh đại diện (Upload Avatar) & Xóa ảnh đại diện (Remove Avatar)
 * - Follow (Theo dõi) & Like (Thả tim) & Comment (Bình luận)
 */
class InstagramEngine(
    var sessionId: String? = null,
    var userId: String? = null,
    var csrfToken: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyUser: String? = null,
    private val proxyPass: String? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val BASE_URL = "https://i.instagram.com/api/v1"
        const val IG_APP_ID = "567067343352427"
        const val USER_AGENT =
            "Instagram 447.0.0.55.81 Android (36/15; 480dpi; 1080x2340; samsung; SM-S928B; e3q; qcom; vi_VN; 385311890)"
        const val PREFIX_PWD_INSTAGRAM = "#PWD_INSTAGRAM:4:"
        const val CONNECTION_TYPE = "WIFI"
        const val CAPABILITIES = "3brTvw=="
    }

    data class LoginResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val username: String? = null,
        val fullName: String? = null,
        val sessionId: String? = null,
        val csrfToken: String? = null,
        val cookieString: String = "",
        val avatarUrl: String? = null,
        val isTwoFactorRequired: Boolean = false,
        val message: String? = null,
        val rawResponse: String = ""
    )

    data class AvatarInfo(
        val userId: String,
        val username: String? = null,
        val fullName: String? = null,
        val avatarUrl: String? = null,
        val hdAvatarUrl: String? = null,
        val isDefault: Boolean = false
    )

    data class UploadAvatarResult(
        val isSuccess: Boolean,
        val newAvatarUrl: String? = null,
        val message: String? = null,
        val rawResponse: String = ""
    )

    data class ActionResult(
        val isSuccess: Boolean,
        val message: String? = null,
        val rawResponse: String = ""
    )

    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
                if (cookie.name == "sessionid") sessionId = cookie.value
                if (cookie.name == "csrftoken") csrfToken = cookie.value
                if (cookie.name == "ds_user_id") userId = cookie.value
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = cookieStore[url.host] ?: mutableListOf()
            if (!sessionId.isNullOrBlank() && list.none { it.name == "sessionid" }) {
                list.add(Cookie.Builder().domain(url.host).name("sessionid").value(sessionId!!).build())
            }
            if (!csrfToken.isNullOrBlank() && list.none { it.name == "csrftoken" }) {
                list.add(Cookie.Builder().domain(url.host).name("csrftoken").value(csrfToken!!).build())
            }
            if (!userId.isNullOrBlank() && list.none { it.name == "ds_user_id" }) {
                list.add(Cookie.Builder().domain(url.host).name("ds_user_id").value(userId!!).build())
            }
            return list
        }

        fun getAllCookiesString(): String {
            val sb = StringBuilder()
            cookieStore.values.flatten().distinctBy { it.name }.forEach { c ->
                sb.append("${c.name}=${c.value}; ")
            }
            if (sessionId != null && !sb.contains("sessionid=")) sb.append("sessionid=$sessionId; ")
            if (userId != null && !sb.contains("ds_user_id=")) sb.append("ds_user_id=$userId; ")
            if (csrfToken != null && !sb.contains("csrftoken=")) sb.append("csrftoken=$csrfToken; ")
            return sb.toString().trim()
        }
    }

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            builder.proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
            if (!proxyUser.isNullOrBlank()) {
                builder.proxyAuthenticator { _, response ->
                    val cred = Credentials.basic(proxyUser, proxyPass.orEmpty())
                    response.request.newBuilder().header("Proxy-Authorization", cred).build()
                }
            }
        }
        builder.build()
    }

    private val deviceId: String by lazy {
        "android-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }
    private val phoneId: String by lazy { UUID.randomUUID().toString() }
    private val guid: String by lazy { UUID.randomUUID().toString() }

    fun fetchPasswordEncryptionKey(): Pair<Int, String>? {
        val endpoints = listOf(
            "https://i.instagram.com/api/v1/qe/sync/",
            "https://i.instagram.com/api/v2/qe/sync/"
        )
        for (ep in endpoints) {
            try {
                val req = Request.Builder()
                    .url(ep)
                    .post(FormBody.Builder().build())
                    .header("User-Agent", USER_AGENT)
                    .header("X-IG-App-ID", IG_APP_ID)
                    .header("X-IG-Connection-Type", CONNECTION_TYPE)
                    .header("X-IG-Capabilities", CAPABILITIES)
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    val pubKey = res.header("ig-set-password-encryption-pub-key")
                    val keyIdStr = res.header("ig-set-password-encryption-key-id")
                    if (!pubKey.isNullOrEmpty() && !keyIdStr.isNullOrEmpty()) {
                        return Pair(keyIdStr.toInt(), pubKey)
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun encryptPassword(password: String, keyId: Int, publicKeyBase64OrPem: String): String {
        val timestampSeconds = System.currentTimeMillis() / 1000L
        val timestampStr = timestampSeconds.toString()

        val random = SecureRandom()
        val aesKeyBytes = ByteArray(32).also { random.nextBytes(it) }
        val ivBytes = ByteArray(12).also { random.nextBytes(it) }

        var pemText = publicKeyBase64OrPem.trim()
        if (!pemText.contains("-----BEGIN")) {
            try {
                val decodedBytes = Base64.decode(pemText, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, Charsets.UTF_8)
                if (decodedStr.contains("-----BEGIN")) pemText = decodedStr
            } catch (_: Exception) {}
        }

        val cleanKey = pemText
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        val keyBytes = Base64.decode(cleanKey, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val rsaPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKeyBytes)

        val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        gcmCipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        gcmCipher.updateAAD(timestampStr.toByteArray(Charsets.UTF_8))

        val gcmCiphertextAndTag = gcmCipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val tagLength = 16
        val ciphertextLength = gcmCiphertextAndTag.size - tagLength
        val tagBytes = gcmCiphertextAndTag.copyOfRange(ciphertextLength, gcmCiphertextAndTag.size)
        val ciphertextBytes = gcmCiphertextAndTag.copyOfRange(0, ciphertextLength)

        val output = ByteArrayOutputStream()
        output.write(1)
        output.write(keyId)
        output.write(ivBytes)
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(encryptedAesKey.size.toShort()).array())
        output.write(encryptedAesKey)
        output.write(tagBytes)
        output.write(ciphertextBytes)

        val b64Payload = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "$PREFIX_PWD_INSTAGRAM$timestampStr:$b64Payload"
    }

    /**
     * Đăng nhập chuẩn 100% từ Instagram Engine (hỗ trợ 2FA TOTP tự động)
     */
    fun login(
        username: String,
        passwordRaw: String,
        twoFaSecret: String? = null
    ): LoginResult {
        val keyInfo = fetchPasswordEncryptionKey()
        val encPassword = if (keyInfo != null) {
            try {
                encryptPassword(passwordRaw, keyInfo.first, keyInfo.second)
            } catch (_: Exception) {
                "#PWD_INSTAGRAM:0:${System.currentTimeMillis() / 1000L}:$passwordRaw"
            }
        } else {
            "#PWD_INSTAGRAM:0:${System.currentTimeMillis() / 1000L}:$passwordRaw"
        }

        val formBody = FormBody.Builder()
            .add("username", username.trim())
            .add("enc_password", encPassword)
            .add("device_id", deviceId)
            .add("phone_id", phoneId)
            .add("guid", guid)
            .add("login_attempt_count", "0")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/accounts/login/")
            .post(formBody)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-IG-Connection-Type", CONNECTION_TYPE)
            .header("X-IG-Capabilities", CAPABILITIES)
            .build()

        val primaryResult = try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (json != null) {
                    val loggedInUser = json.optJSONObject("logged_in_user")
                    val isTwoFactor = json.optBoolean("two_factor_required", false)

                    if (loggedInUser != null) {
                        val uid = loggedInUser.optString("pk", "")
                        val uname = loggedInUser.optString("username", username)
                        val pUrl = loggedInUser.optString("profile_pic_url", null)
                        val fName = loggedInUser.optString("full_name", null)
                        this.userId = uid

                        LoginResult(
                            isSuccess = true,
                            userId = uid,
                            username = uname,
                            fullName = fName,
                            sessionId = this.sessionId,
                            csrfToken = this.csrfToken,
                            cookieString = cookieJar.getAllCookiesString(),
                            avatarUrl = pUrl,
                            message = "Đăng nhập thành công",
                            rawResponse = body
                        )
                    } else if (isTwoFactor) {
                        handleTwoFactor(username, json, twoFaSecret)
                    } else {
                        LoginResult(false, message = json.optString("message", "Đăng nhập không thành công"), rawResponse = body)
                    }
                } else {
                    LoginResult(false, message = "Phản hồi không hợp lệ", rawResponse = body)
                }
            }
        } catch (e: Exception) {
            LoginResult(false, message = e.message, rawResponse = "")
        }

        // Nếu máy chủ yêu cầu nâng cấp bản app, hoàn tất đăng nhập an toàn bằng kết nối song song
        if (!primaryResult.isSuccess && primaryResult.rawResponse.contains("needs_upgrade")) {
            val fallbackRes = InstagramApiClient.loginWithCredentials(username, passwordRaw, twoFaSecret)
            if (fallbackRes.isSuccess) {
                this.sessionId = Regex("sessionid=([^;]+)").find(fallbackRes.cookie)?.groupValues?.get(1)
                this.userId = fallbackRes.userId
                this.csrfToken = Regex("csrftoken=([^;]+)").find(fallbackRes.cookie)?.groupValues?.get(1)
                return LoginResult(
                    isSuccess = true,
                    userId = fallbackRes.userId,
                    username = fallbackRes.username,
                    fullName = fallbackRes.fullName,
                    sessionId = this.sessionId,
                    csrfToken = this.csrfToken,
                    cookieString = fallbackRes.cookie,
                    avatarUrl = fallbackRes.avatarUrl,
                    message = "Đăng nhập thành công",
                    rawResponse = fallbackRes.rawResponse
                )
            }
        }

        return primaryResult
    }

    private fun handleTwoFactor(username: String, json: JSONObject, twoFaSecret: String?): LoginResult {
        val twoFactorInfo = json.optJSONObject("two_factor_info")
        val identifier = twoFactorInfo?.optString("two_factor_identifier").orEmpty()
        val secretClean = twoFaSecret?.trim().orEmpty()

        if (secretClean.isNotBlank()) {
            val code = if (secretClean.length == 6 && secretClean.all { it.isDigit() }) {
                secretClean
            } else {
                TotpGenerator.generateTotp(secretClean)
            }

            if (code.isNotBlank()) {
                val twoFaBody = FormBody.Builder()
                    .add("username", username.trim())
                    .add("verification_code", code)
                    .add("two_factor_identifier", identifier)
                    .add("trust_this_device", "1")
                    .add("device_id", deviceId)
                    .add("guid", guid)
                    .build()

                val twoFaReq = Request.Builder()
                    .url("$BASE_URL/accounts/two_factor_login/")
                    .post(twoFaBody)
                    .header("User-Agent", USER_AGENT)
                    .header("X-IG-App-ID", IG_APP_ID)
                    .header("X-IG-Connection-Type", CONNECTION_TYPE)
                    .header("X-IG-Capabilities", CAPABILITIES)
                    .build()

                return try {
                    httpClient.newCall(twoFaReq).execute().use { res ->
                        val body = res.body?.string() ?: ""
                        val resJson = try { JSONObject(body) } catch (_: Exception) { null }
                        val user = resJson?.optJSONObject("logged_in_user")
                        if (user != null) {
                            val uid = user.optString("pk", "")
                            val uname = user.optString("username", username)
                            val pUrl = user.optString("profile_pic_url", null)
                            val fName = user.optString("full_name", null)
                            this.userId = uid

                            LoginResult(
                                isSuccess = true,
                                userId = uid,
                                username = uname,
                                fullName = fName,
                                sessionId = this.sessionId,
                                csrfToken = this.csrfToken,
                                cookieString = cookieJar.getAllCookiesString(),
                                avatarUrl = pUrl,
                                message = "Xác thực 2FA thành công",
                                rawResponse = body
                            )
                        } else {
                            LoginResult(false, isTwoFactorRequired = true, message = resJson?.optString("message", "Mã 2FA không chính xác"), rawResponse = body)
                        }
                    }
                } catch (e: Exception) {
                    LoginResult(false, isTwoFactorRequired = true, message = e.message, rawResponse = "")
                }
            }
        }

        return LoginResult(
            isSuccess = false,
            isTwoFactorRequired = true,
            message = "Tài khoản yêu cầu mã 2FA. Vui lòng cung cấp 2FA Secret hoặc OTP!",
            rawResponse = json.toString()
        )
    }

    /**
     * Lấy Avatar HD & thông tin User (100% App API: GET /api/v1/users/{target}/info/)
     */
    fun getAvatar(targetUserId: String? = null): AvatarInfo? {
        val target = targetUserId ?: userId ?: return null
        val request = Request.Builder()
            .url("$BASE_URL/users/$target/info/")
            .get()
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-IG-Connection-Type", CONNECTION_TYPE)
            .header("X-IG-Capabilities", CAPABILITIES)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: "{}"
                val json = JSONObject(body)
                val user = json.optJSONObject("user") ?: return null

                val pUrl = user.optString("profile_pic_url", null)
                val hdObj = user.optJSONObject("hd_profile_pic_url_info")
                val hdUrl = hdObj?.optString("url", pUrl) ?: pUrl
                val uname = user.optString("username", null)
                val fName = user.optString("full_name", null)
                val isDefault = user.optBoolean("has_anonymous_profile_picture", false)

                AvatarInfo(target, uname, fName, pUrl, hdUrl, isDefault)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Đổi ảnh đại diện (100% App API: POST /api/v1/accounts/change_profile_picture/)
     */
    fun uploadAvatar(imageBytes: ByteArray, mimeType: String = "image/jpeg"): UploadAvatarResult {
        val mediaType = mimeType.toMediaType()
        val fileBody = imageBytes.toRequestBody(mediaType)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("profile_pic", "profile_pic.jpg", fileBody)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/accounts/change_profile_picture/")
            .post(multipart)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-IG-Connection-Type", CONNECTION_TYPE)
            .header("X-IG-Capabilities", CAPABILITIES)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val isOk = res.isSuccessful && json?.optString("status") == "ok"
                val user = json?.optJSONObject("user")
                val newUrl = user?.optString("profile_pic_url", null)

                UploadAvatarResult(
                    isSuccess = isOk,
                    newAvatarUrl = newUrl,
                    message = if (isOk) "Cập nhật ảnh đại diện thành công" else body,
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            UploadAvatarResult(false, null, e.message, "")
        }
    }

    /**
     * Xóa ảnh đại diện (100% App API: POST /api/v1/accounts/remove_profile_picture/)
     */
    fun removeAvatar(): Boolean {
        val formBody = FormBody.Builder().build()
        val request = Request.Builder()
            .url("$BASE_URL/accounts/remove_profile_picture/")
            .post(formBody)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-IG-Connection-Type", CONNECTION_TYPE)
            .header("X-IG-Capabilities", CAPABILITIES)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res -> res.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Theo dõi (Follow) (100% App API: POST /api/v1/friendships/create/{targetId}/)
     */
    fun follow(targetUserId: String): ActionResult {
        val formBody = FormBody.Builder()
            .add("user_id", targetUserId)
            .add("radio_type", "wifi-none")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/friendships/create/$targetUserId/")
            .post(formBody)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-IG-Connection-Type", CONNECTION_TYPE)
            .header("X-IG-Capabilities", CAPABILITIES)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val isOk = res.isSuccessful && (json?.optString("status") == "ok" || body.contains("\"following\":true"))
                ActionResult(isOk, if (isOk) "Theo dõi thành công" else json?.optString("message", "Lỗi Follow"), body)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message, "")
        }
    }

    /**
     * Thả tim (Like) (100% App API: POST /api/v1/media/{mediaId}/like/)
     */
    fun like(mediaId: String): ActionResult {
        val formBody = FormBody.Builder()
            .add("media_id", mediaId)
            .add("radio_type", "wifi-none")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/media/$mediaId/like/")
            .post(formBody)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-IG-Connection-Type", CONNECTION_TYPE)
            .header("X-IG-Capabilities", CAPABILITIES)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val isOk = res.isSuccessful && json?.optString("status") == "ok"
                ActionResult(isOk, if (isOk) "Thả tim thành công" else json?.optString("message", "Lỗi Like"), body)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message, "")
        }
    }

    /**
     * Bình luận (Comment) (100% App API: POST /api/v1/media/{mediaId}/comment/)
     */
    fun comment(mediaId: String, text: String): ActionResult {
        val formBody = FormBody.Builder()
            .add("comment_text", text)
            .add("radio_type", "wifi-none")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/media/$mediaId/comment/")
            .post(formBody)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-IG-Connection-Type", CONNECTION_TYPE)
            .header("X-IG-Capabilities", CAPABILITIES)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val isOk = res.isSuccessful && json?.optString("status") == "ok"
                ActionResult(isOk, if (isOk) "Bình luận thành công" else json?.optString("message", "Lỗi Comment"), body)
            }
        } catch (e: Exception) {
            ActionResult(false, e.message, "")
        }
    }
}
