package com.cayxu.app.facebook

import androidx.annotation.Keep
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Keep
class FacebookRegisterEngine(
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP
) {

    companion object {
        const val B_GRAPH_URL = "https://b-graph.facebook.com"
        const val KATANA_APP_ID = "350685531728"
        const val KATANA_APP_SECRET = "62f8ce9f74b12f84c123cc23437a4a32"
        const val KATANA_USER_AGENT =
            "[FBAN/FB4A;FBAV/548.1.0.51.64;FBBV/474618929;FBDM/{density=3.0,width=1080,height=2340};FBLC/vi_VN;FBRV/0;FBCR/Viettel;FBMF/samsung;FBBD/samsung;FBPN/com.facebook.katana;FBDV/SM-S928B;FBSV/14;FBOP/1;FBCA/arm64-v8a;]"
        const val PREFIX_PWD_FB4A = "#PWD_FB4A:2:"
    }

    @Keep
    data class RegistrationResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val accessToken: String? = null,
        val sessionKey: String? = null,
        val isConfirmationRequired: Boolean = false,
        val message: String? = null,
        val rawResponse: String = ""
    )

    @Keep
    data class ConfirmationResult(
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

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    private fun generateSig(params: Map<String, String>): String {
        val sorted = params.toSortedMap()
        val sb = StringBuilder()
        for ((k, v) in sorted) {
            sb.append("$k=$v")
        }
        sb.append(KATANA_APP_SECRET)
        return md5(sb.toString())
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

    fun register(
        firstName: String,
        lastName: String,
        contactPoint: String,
        passwordRaw: String,
        birthday: String = "2000-01-01",
        gender: String = "M"
    ): RegistrationResult {
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

        val deviceId = UUID.randomUUID().toString()
        val familyDeviceId = UUID.randomUUID().toString()

        val params = mutableMapOf(
            "firstname" to firstName,
            "lastname" to lastName,
            "reg_instance" to UUID.randomUUID().toString(),
            "contact_point" to contactPoint,
            "password" to encryptedPassword,
            "birthday" to birthday,
            "gender" to gender,
            "device_id" to deviceId,
            "family_device_id" to familyDeviceId,
            "format" to "json",
            "locale" to "vi_VN",
            "client_country_code" to "VN",
            "method" to "user.register",
            "return_multiple_errors" to "true"
        )

        val sig = generateSig(params)
        params["sig"] = sig

        val formBuilder = FormBody.Builder()
        for ((k, v) in params) {
            formBuilder.add(k, v)
        }

        val request = Request.Builder()
            .url("$B_GRAPH_URL/method/user.register")
            .post(formBuilder.build())
            .header("User-Agent", KATANA_USER_AGENT)
            .header("X-FB-Friendly-Name", "user.register")
            .build()

        return try {
            httpClient.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                val json = try { JSONObject(body) } catch (_: Exception) { null }

                val token = json?.optString("session_info")?.let {
                    try { JSONObject(it).optString("access_token") } catch (_: Exception) { null }
                } ?: json?.optString("access_token")

                val uid = json?.optString("new_user_id") ?: json?.optString("uid")
                val isConfirmation = body.contains("confirmation_code") || body.contains("need_confirm")

                val isOk = res.isSuccessful && (!token.isNullOrEmpty() || isConfirmation)
                RegistrationResult(
                    isSuccess = isOk,
                    userId = uid,
                    accessToken = token,
                    isConfirmationRequired = isConfirmation,
                    message = if (isOk) "Đăng ký thành công" else body,
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            RegistrationResult(false, null, null, null, false, e.message, "")
        }
    }
}
