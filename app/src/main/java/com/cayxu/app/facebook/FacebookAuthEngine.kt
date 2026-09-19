package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.util.NativeSecurity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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

@Keep
class FacebookAuthEngine(
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val B_GRAPH_URL = "https://b-graph.facebook.com"
        const val GRAPHQL_ENDPOINT = "https://b-graph.facebook.com/graphql"
        const val KATANA_APP_ID = "350685531728"
        const val KATANA_APP_SECRET = "62f8ce9f74b12f84c123cc23437a4a32"
        const val BLOKS_VERSIONING_ID = "3469837656910fc29c9aa968ab33845cd52eb5253ae110610b944c8e9028d8f6"
        const val PREFIX_PWD_FB4A = "#PWD_FB4A:2:"
    }

    @Keep
    data class LoginResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val accessToken: String? = null,
        val machineId: String? = null,
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
     * Lấy public key để mã hoá password — dùng token đúng chuẩn từ NativeSecurity
     */
    fun fetchPasswordEncryptionKey(): Pair<Int, String>? {
        val keyFetchToken = NativeSecurity.getFbKeyFetchToken()
        val url = "$B_GRAPH_URL/pwd_key_fetch?version=2&flow=CONTROLLER_INITIALIZATION&access_token=$keyFetchToken"
        val ua = NativeSecurity.getFbKatanaUA()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", ua)
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Http-Engine", "Liger")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: return null
                val json = JSONObject(body)
                val keyId = json.optInt("key_id", -1)
                val pubKey = json.optString("public_key", "")
                if (keyId != -1 && pubKey.isNotEmpty()) Pair(keyId, pubKey) else null
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
            .replace("\r", "")
            .trim()
        val keyBytes = android.util.Base64.decode(cleanKey, android.util.Base64.DEFAULT)
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

        val b64Payload = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        return "$PREFIX_PWD_FB4A$timestampStr:$b64Payload"
    }

    /**
     * Đăng nhập qua CAA Bloks GraphQL — đúng chuẩn b-graph.facebook.com với flat params Bloks.
     * Dùng Authorization OAuth token từ NativeSecurity, bloks_versioning_id chuẩn, client_doc_id từ NativeSecurity.
     */
    fun loginCAA(contactPoint: String, passwordRaw: String): LoginResult {
        val oauthToken = NativeSecurity.getFbOAuthToken()       // "OAuth 350685531728|..."
        val katanaUa = NativeSecurity.getFbKatanaUA()
        val clientDocId = NativeSecurity.getFbBloksDocId()      // "119940804214876861379510865434"

        // Bước 1: Mã hoá password
        val keyInfo = fetchPasswordEncryptionKey()
        val encryptedPassword = if (keyInfo != null) {
            try {
                encryptPassword(passwordRaw, keyInfo.first, keyInfo.second)
            } catch (_: Exception) {
                passwordRaw
            }
        } else {
            passwordRaw
        }

        // Bước 2: Xây dựng params JSON chuẩn Bloks CAA login
        val clientInputParams = JSONObject().apply {
            put("contact_point", contactPoint)
            put("password", encryptedPassword)
            put("login_source", "Login")
            put("device_id", UUID.randomUUID().toString())
            put("family_device_id", UUID.randomUUID().toString())
            put("access_flow_version", "F2_FLOW")
            put("credential_type", "password")
            put("waterfall_id", UUID.randomUUID().toString())
            put("headers_flow_mode", "regular")
        }

        val serverParams = JSONObject().apply {
            put("server_login_source", "Login")
            put("credential_type", "password")
        }

        val innerParams = JSONObject().apply {
            put("client_input_params", clientInputParams)
            put("server_params", serverParams)
        }

        // Flat params chuẩn Bloks (KHÔNG lồng trong variables JSON)
        val paramsJson = JSONObject().apply {
            put("params", innerParams.toString())
        }

        val formBody = FormBody.Builder()
            .add("params", paramsJson.toString())
            .add("bloks_versioning_id", BLOKS_VERSIONING_ID)
            .add("app_id", "com.bloks.www.bloks.caa.login.async.send_login_request")
            .add("fb_api_req_friendly_name", "FbBloksActionRootQuery-com.bloks.www.bloks.caa.login.async.send_login_request")
            .add("fb_api_caller_class", "com.bloks.www.bloks.caa.login.async.send_login_request")
            .add("client_doc_id", clientDocId)
            .add("method", "post")
            .add("format", "json")
            .build()

        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .post(formBody)
            .header("Authorization", oauthToken)
            .header("User-Agent", katanaUa)
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Http-Engine", "Liger")
            .header("X-FB-Friendly-Name", "FbBloksActionRootQuery-com.bloks.www.bloks.caa.login.async.send_login_request")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""

                // Parse token từ response Bloks
                val tokenRegex = Regex("""["'](?:access_token|token)["']\s*:\s*["']([A-Za-z0-9\-_|]+)["']""")
                val uidRegex = Regex("""["'](?:uid|user_id|actor_id)["']\s*:\s*["']?(\d+)["']?""")

                val token = tokenRegex.find(body)?.groupValues?.get(1)
                val uid = uidRegex.find(body)?.groupValues?.get(1)

                val isOk = !token.isNullOrEmpty()

                val errorMsg: String = if (isOk) {
                    "Login Success"
                } else {
                    // Trích xuất lỗi CHÍNH XÁC từ response Instagram/Facebook trả về
                    when {
                        body.contains("checkpoint", ignoreCase = true) ->
                            "Tài khoản bị checkpoint – yêu cầu xác minh bảo mật"
                        body.contains("Invalid username or password", ignoreCase = true) ||
                        body.contains("invalid_credentials", ignoreCase = true) ->
                            "Sai tài khoản hoặc mật khẩu"
                        body.contains("two_factor", ignoreCase = true) ||
                        body.contains("2fa", ignoreCase = true) ->
                            "Tài khoản yêu cầu xác thực 2FA"
                        body.contains("account_disabled", ignoreCase = true) ||
                        body.contains("disabled", ignoreCase = true) ->
                            "Tài khoản đã bị vô hiệu hoá"
                        else -> {
                            // Thử lấy thông báo lỗi trực tiếp từ JSON Facebook trả về
                            val msgPattern = Regex("""["'](?:error_message|message|description|title)["']\s*:\s*["']([^"']+)["']""")
                            val foundMsg = msgPattern.find(body)?.groupValues?.get(1)
                            if (!foundMsg.isNullOrBlank() && foundMsg.length < 300) {
                                foundMsg
                            } else {
                                "Đăng nhập thất bại (HTTP ${res.code})"
                            }
                        }
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
            LoginResult(false, null, null, null, e.message ?: "Lỗi kết nối", "")
        }
    }
}
