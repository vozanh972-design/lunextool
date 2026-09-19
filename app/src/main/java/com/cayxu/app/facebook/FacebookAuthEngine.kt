package com.cayxu.app.facebook

import androidx.annotation.Keep
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

@Keep
class FacebookAuthEngine(
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val GRAPHQL_URL = "https://graph.facebook.com/graphql"
        const val B_GRAPH_URL = "https://b-graph.facebook.com"
        const val KATANA_APP_ID = "350685531728"
        const val KATANA_APP_SECRET = "62f8ce9f74b12f84c123cc23437a4a32"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
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

    fun fetchPasswordEncryptionKey(): Pair<Int, String>? {
        val url = "$B_GRAPH_URL/pwd_key_fetch?version=2&flow=CONTROLLER_INITIALIZATION&access_token=$KATANA_APP_ID|$KATANA_APP_SECRET"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", KATANA_USER_AGENT)
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

    fun loginCAA(contactPoint: String, passwordRaw: String): LoginResult {
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

        val level1 = JSONObject().apply {
            put("params", JSONObject().apply { put("params", innerParams.toString()) }.toString())
            put("bloks_versioning_id", "45a55ce624fec64fefb3d8756c602dc8992c3a37b12d5930263f35c6e8e8ce4a")
            put("app_id", "com.bloks.www.bloks.caa.login.async.send_login_request")
        }

        val variables = JSONObject().apply {
            put("params", level1)
            put("scale", "2")
            put("nt_context", JSONObject().apply {
                put("styles_id", "548.1.0.51.64")
                put("pixel_ratio", 2.0)
            })
        }

        val formBody = FormBody.Builder()
            .add("method", "post")
            .add("format", "json")
            .add("client_doc_id", "14886673641771928372657412")
            .add("variables", variables.toString())
            .build()

        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(formBody)
            .header("User-Agent", KATANA_USER_AGENT)
            .header("X-FB-Friendly-Name", "send_login_request")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val tokenRegex = Regex("""["'](?:access_token|token)["']\s*:\s*["']([^"']+)["']""")
                val uidRegex = Regex("""["'](?:uid|user_id|actor_id)["']\s*:\s*["']?(\d+)["']?""")

                val token = tokenRegex.find(body)?.groupValues?.get(1)
                val uid = uidRegex.find(body)?.groupValues?.get(1)

                val isOk = !token.isNullOrEmpty()
                
                var errorMsg = if (isOk) "Login Success" else "Login Failed"
                if (!isOk) {
                    if (body.contains("checkpoint")) {
                        errorMsg = "Tài khoản bị checkpoint yêu cầu xác minh bảo mật"
                    } else if (body.contains("Invalid username or password") || body.contains("invalid_credentials")) {
                        errorMsg = "Sai tài khoản hoặc mật khẩu"
                    } else {
                        val msgPattern = Regex("""["'](?:error_message|message|description)["']\s*:\s*["']([^"']+)["']""")
                        val foundMsg = msgPattern.find(body)?.groupValues?.get(1)
                        if (!foundMsg.isNullOrBlank()) {
                            errorMsg = foundMsg
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
