package com.instagram.engine

import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class InstagramEngine(
    private var sessionId: String? = null,
    private var userId: String? = null,
    private var csrfToken: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val BASE_URL = "https://i.instagram.com/api/v1"
        const val IG_APP_ID = "567067343352427"
        const val USER_AGENT =
            "Instagram 447.0.0.55.81 Android (36/15; 480dpi; 1080x2340; samsung; SM-S928B; e3q; qcom; vi_VN; 385311890)"
        const val PREFIX_PWD_INSTAGRAM = "#PWD_INSTAGRAM:4:"
    }

    data class LoginResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val username: String? = null,
        val sessionId: String? = null,
        val csrfToken: String? = null,
        val avatarUrl: String? = null,
        val isTwoFactorRequired: Boolean = false,
        val message: String? = null,
        val rawResponse: String = ""
    )

    data class AvatarInfo(
        val userId: String,
        val username: String? = null,
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
    }

    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            builder.proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
        }
        builder.build()
    }

    private val deviceId: String by lazy {
        "android-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }

    private val phoneId: String by lazy { UUID.randomUUID().toString() }
    private val guid: String by lazy { UUID.randomUUID().toString() }

    fun fetchPasswordEncryptionKey(): Pair<Int, String>? {
        val request = Request.Builder()
            .url("https://i.instagram.com/api/v2/qe/sync/")
            .get()
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val pubKey = res.header("ig-set-password-encryption-pub-key")
                val keyIdStr = res.header("ig-set-password-encryption-key-id")
                if (!pubKey.isNullOrEmpty() && !keyIdStr.isNullOrEmpty()) {
                    Pair(keyIdStr.toInt(), pubKey)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun encryptPassword(password: String, keyId: Int, publicKeyBase64: String): String {
        val timestampSeconds = System.currentTimeMillis() / 1000L
        val timestampStr = timestampSeconds.toString()

        val random = SecureRandom()
        val aesKeyBytes = ByteArray(32).also { random.nextBytes(it) }
        val ivBytes = ByteArray(12).also { random.nextBytes(it) }

        val cleanKey = publicKeyBase64
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .trim()
        val keyBytes = Base64.getDecoder().decode(cleanKey)
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

        val b64Payload = Base64.getEncoder().encodeToString(output.toByteArray())
        return "$PREFIX_PWD_INSTAGRAM$timestampStr:$b64Payload"
    }

    fun login(username: String, passwordRaw: String): LoginResult {
        val keyInfo = fetchPasswordEncryptionKey()
        val encPassword = if (keyInfo != null) {
            encryptPassword(passwordRaw, keyInfo.first, keyInfo.second)
        } else {
            val ts = System.currentTimeMillis() / 1000L
            "#PWD_INSTAGRAM:0:$ts:$passwordRaw"
        }

        val formBody = FormBody.Builder()
            .add("username", username)
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
            .header("X-IG-Connection-Type", "WIFI")
            .header("X-IG-Capabilities", "3brTvw==")
            .build()

        return try {
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
                        this.userId = uid

                        LoginResult(
                            isSuccess = true,
                            userId = uid,
                            username = uname,
                            sessionId = this.sessionId,
                            csrfToken = this.csrfToken,
                            avatarUrl = pUrl,
                            message = "Login successful",
                            rawResponse = body
                        )
                    } else if (isTwoFactor) {
                        LoginResult(
                            isSuccess = false,
                            isTwoFactorRequired = true,
                            message = "Two factor authentication required",
                            rawResponse = body
                        )
                    } else {
                        LoginResult(
                            isSuccess = false,
                            message = json.optString("message", "Login failed"),
                            rawResponse = body
                        )
                    }
                } else {
                    LoginResult(false, message = "Invalid response", rawResponse = body)
                }
            }
        } catch (e: Exception) {
            LoginResult(false, message = e.message, rawResponse = "")
        }
    }

    fun getAvatar(targetUserId: String? = null): AvatarInfo? {
        val target = targetUserId ?: userId ?: return null
        val request = Request.Builder()
            .url("$BASE_URL/users/$target/info/")
            .get()
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
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
                val isDefault = user.optBoolean("has_anonymous_profile_picture", false)

                AvatarInfo(target, uname, pUrl, hdUrl, isDefault)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String = "image/jpeg"): UploadAvatarResult {
        val mediaType = MediaType.parse(mimeType)
        val fileBody = RequestBody.create(mediaType, imageBytes)

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("profile_pic", "profile_pic.jpg", fileBody)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/accounts/change_profile_picture/")
            .post(multipart)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
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
                    message = if (isOk) "Avatar updated" else body,
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            UploadAvatarResult(false, null, e.message, "")
        }
    }

    fun removeAvatar(): Boolean {
        val formBody = FormBody.Builder().build()
        val request = Request.Builder()
            .url("$BASE_URL/accounts/remove_profile_picture/")
            .post(formBody)
            .header("User-Agent", USER_AGENT)
            .header("X-IG-App-ID", IG_APP_ID)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                res.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }
}
