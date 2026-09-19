package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.util.NativeSecurity
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ============================================================
// NGUỒN: FacebookAuthenticator.kt gốc (git f0ba884)
// Class: FacebookPasswordEncryptor
// ============================================================
@Keep
object FacebookPasswordEncryptor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun encryptPassword(password: String): String? {
        return try {
            val keyFetchToken = NativeSecurity.getFbKeyFetchToken()
            val encodedToken = java.net.URLEncoder.encode(keyFetchToken, "UTF-8")
            val url = "https://b-graph.facebook.com/pwd_key_fetch?version=2&flow=CONTROLLER_INITIALIZATION&method=GET&fb_api_req_friendly_name=pwdKeyFetch&fb_api_caller_class=com.facebook.auth.login.AuthOperations&access_token=$encodedToken"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val pubKey = json.optString("public_key", "")
            val keyId = json.optInt("key_id", 25)
            if (pubKey.isBlank()) return null

            val cleanPem = pubKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyBytes = android.util.Base64.decode(cleanPem, android.util.Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val rsaPublicKey = keyFactory.generatePublic(spec)

            val random = SecureRandom()
            val aesKeyBytes = ByteArray(32)
            random.nextBytes(aesKeyBytes)

            val ivBytes = ByteArray(12)
            random.nextBytes(ivBytes)

            val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
            val encryptedAesKey = rsaCipher.doFinal(aesKeyBytes)

            val currentTimeSec = (System.currentTimeMillis() / 1000).toString()
            val aadBytes = currentTimeSec.toByteArray(Charsets.UTF_8)

            val gcmSpec = GCMParameterSpec(128, ivBytes)
            val secretKey = SecretKeySpec(aesKeyBytes, "AES")
            val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
            gcmCipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            gcmCipher.updateAAD(aadBytes)
            val cipherTextWithTag = gcmCipher.doFinal(password.toByteArray(Charsets.UTF_8))

            val tagLen = 16
            val encryptedPassLen = cipherTextWithTag.size - tagLen
            val encryptedPass = ByteArray(encryptedPassLen)
            val tag = ByteArray(tagLen)
            System.arraycopy(cipherTextWithTag, 0, encryptedPass, 0, encryptedPassLen)
            System.arraycopy(cipherTextWithTag, encryptedPassLen, tag, 0, tagLen)

            val out = ByteArrayOutputStream()
            out.write(1)
            out.write(keyId and 0xFF)
            out.write(ivBytes)
            val lenBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            lenBuf.putShort(encryptedAesKey.size.toShort())
            out.write(lenBuf.array())
            out.write(encryptedAesKey)
            out.write(tag)
            out.write(encryptedPass)

            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}

// ============================================================
// NGUỒN: FacebookBloksLogin.kt gốc (git f0ba884)
// Class: FacebookBloksLogin — CAA Bloks GraphQL login
// ============================================================
@Keep
class FacebookAuthEngine(
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPHQL_ENDPOINT = "https://b-graph.facebook.com/graphql"
        const val APP_ID_LOGIN = "com.bloks.www.bloks.caa.login.async.send_login_request"
        const val BLOKS_VERSIONING_ID = "3469837656910fc29c9aa968ab33845cd52eb5253ae110610b944c8e9028d8f6"
    }

    @Keep
    data class LoginResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val accessToken: String? = null,
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

    /**
     * Đăng nhập CAA Bloks — logic 100% từ FacebookBloksLogin.executeLogin() gốc (git f0ba884).
     * Mã hoá password 100% từ FacebookPasswordEncryptor (FacebookAuthenticator.kt gốc git f0ba884).
     */
    fun loginCAA(contactPoint: String, passwordRaw: String): LoginResult {
        val oauthToken = NativeSecurity.getFbOAuthToken()
        val katanaUa = NativeSecurity.getFbKatanaUA()
        val clientDocId = NativeSecurity.getFbBloksDocId()

        // Mã hoá password — 100% từ FacebookPasswordEncryptor (git f0ba884)
        val encryptedPasswordB64 = FacebookPasswordEncryptor.encryptPassword(passwordRaw)
        val passwordEncrypted = if (!encryptedPasswordB64.isNullOrBlank()) {
            "#PWD_FB4A:2:${System.currentTimeMillis() / 1000}:$encryptedPasswordB64"
        } else {
            passwordRaw
        }

        val deviceId = UUID.randomUUID().toString()
        val familyDeviceId = UUID.randomUUID().toString()
        val waterfallId = UUID.randomUUID().toString()

        // client_input_params — 100% từ FacebookBloksLogin.executeLogin() gốc (git f0ba884)
        val clientInputParams = JSONObject().apply {
            put("contact_point", contactPoint)
            put("password", passwordEncrypted)
            put("device_id", deviceId)
            put("family_device_id", familyDeviceId)
            put("login_source", "Login")
            put("waterfall_id", waterfallId)
            put("credential_type", "password")
            put("event_flow", "login_manual")
            put("login_attempt_count", "1")
            put("access_flow_version", "F2_FLOW")
            put("is_caa_perf_enabled", true)
        }

        val serverParams = JSONObject().apply {
            put("credential_type", "password")
            put("server_login_source", "login")
        }

        val rootParams = JSONObject().apply {
            put("client_input_params", clientInputParams)
            put("server_params", serverParams)
        }

        // FormBody — 100% từ FacebookBloksLogin.executeLogin() gốc (git f0ba884)
        val formBody = FormBody.Builder()
            .add("params", rootParams.toString())
            .add("bloks_versioning_id", BLOKS_VERSIONING_ID)
            .add("app_id", APP_ID_LOGIN)
            .add("fb_api_req_friendly_name", "FbBloksActionRootQuery-$APP_ID_LOGIN")
            .add("fb_api_caller_class", "graphservice")
            .add("client_doc_id", clientDocId)
            .add("method", "post")
            .add("format", "json")
            .build()

        // Headers — 100% từ FacebookBloksLogin.executeLogin() gốc (git f0ba884)
        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .header("User-Agent", katanaUa)
            .header("Authorization", oauthToken)
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Http-Engine", "Tigon/Liger")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .post(formBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""

                // Parse token từ raw response (FacebookBloksLogin.kt gốc trả raw string, parse ở caller)
                val tokenRegex = Regex("""["'](?:access_token|token)["']\s*:\s*["']([A-Za-z0-9\-_|]+)["']""")
                val uidRegex = Regex("""["'](?:uid|user_id|actor_id)["']\s*:\s*["']?(\d+)["']?""")

                val token = tokenRegex.find(body)?.groupValues?.get(1)
                val uid = uidRegex.find(body)?.groupValues?.get(1)
                val isOk = !token.isNullOrEmpty()

                // Lỗi chính xác từ Facebook — 100% từ handleAuthJsonResponse() gốc (git f0ba884)
                val errorMsg: String = if (isOk) {
                    "Login Success"
                } else {
                    try {
                        val json = JSONObject(body)
                        when {
                            json.has("error_msg") -> json.getString("error_msg")
                            json.has("error") -> json.getJSONObject("error").optString("message", "")
                                .ifBlank { "Tài khoản DIE, sai mật khẩu hoặc Checkpoint" }
                            body.contains("checkpoint", ignoreCase = true) ->
                                "Tài khoản bị checkpoint – yêu cầu xác minh bảo mật"
                            body.contains("Invalid username or password", ignoreCase = true) ||
                            body.contains("invalid_credentials", ignoreCase = true) ->
                                "Sai tài khoản hoặc mật khẩu"
                            body.contains("two_factor", ignoreCase = true) ->
                                "Tài khoản yêu cầu xác thực 2FA"
                            else -> "Tài khoản DIE, sai mật khẩu hoặc Checkpoint"
                        }
                    } catch (_: Exception) {
                        "Đăng nhập thất bại (HTTP ${res.code})"
                    }
                }

                LoginResult(
                    isSuccess = isOk,
                    userId = uid,
                    accessToken = token,
                    message = errorMsg,
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            LoginResult(false, null, null, e.message ?: "Lỗi kết nối", "")
        }
    }
}
