package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.util.NativeSecurity
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ============================================================
// Mã hóa mật khẩu Facebook RSA + AES-256-GCM theo Meta pwd_key_fetch
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
// FacebookAuthEngine: Hỗ trợ Đăng nhập CAA Bloks GraphQL + Tự động 2FA + Đăng nhập Cookie
// Logic chuẩn xác thực tế từ GoMax (fr.java & ar.java)
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
        const val APP_ID_2FA = "com.bloks.www.two_step_verification.verify_code.async"
        const val CLIENT_DOC_ID_2FA = "11994080423986492941384902285"
        const val BLOKS_VERSION_2FA = "cb6ac324faea83da28649a4d5046c3a4f0486cb987f8ab769765e316b075a76c"
        const val BLOKS_VERSIONING_ID = "3469837656910fc29c9aa968ab33845cd52eb5253ae110610b944c8e9028d8f6"
    }

    @Keep
    data class LoginResult(
        val isSuccess: Boolean,
        val userId: String? = null,
        val userName: String? = null,
        val accessToken: String? = null,
        val cookies: String? = null,
        val avatarUrl: String? = null,
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

    /**
     * Phân tích lỗi chính xác chuẩn GoMax fr.a
     */
    fun parseErrorMessage(raw: String): String {
        val clean = raw.replace("\\\"", "\"").replace("&quot;", "\"")
        val m1 = Pattern.compile("bk\\.action\\.string\\.Make\\(\"([^\"]+)\"\\)").matcher(clean)
        if (m1.find()) {
            val g = m1.group(1) ?: ""
            if (g.isNotEmpty() && !g.startsWith("com.") && !g.startsWith("bk.")) {
                return g
            }
        }
        val m2 = Pattern.compile("\"error_message\":\"([^\"]+)\"").matcher(clean)
        if (m2.find()) return m2.group(1) ?: "Lỗi Facebook"
        val m3 = Pattern.compile("\"error_user_msg\":\"([^\"]+)\"").matcher(clean)
        if (m3.find()) return m3.group(1) ?: "Lỗi Facebook"

        return when {
            clean.contains("login_error") || clean.contains("Sai mật khẩu") || clean.contains("mật khẩu bạn đã nhập không chính xác") || clean.contains("Incorrect password") ->
                "Sai tài khoản hoặc mật khẩu Facebook"
            clean.contains("Mã đăng nhập không hợp lệ") || clean.contains("Invalid login code") || clean.contains("login_code_invalid") ->
                "Mã 2FA không đúng (kiểm tra lại khóa 2FA)"
            clean.contains("checkpoint") || clean.contains("login_approvals") || clean.contains("approvals_code") ->
                "Tài khoản bị bắt phê duyệt thiết bị (Checkpoint)"
            clean.contains("vô hiệu hóa") || clean.contains("account_disabled") ->
                "Tài khoản Facebook đã bị vô hiệu hóa"
            clean.contains("two_factor") || clean.contains("error_2fa") || clean.contains("challenge") ->
                "Tài khoản yêu cầu xác thực 2FA"
            else -> "Đăng nhập thất bại: Kiểm tra lại tài khoản hoặc 2FA!"
        }
    }

    /**
     * Bóc tách cookie từ raw response (session_cookies)
     */
    private fun extractCookies(raw: String): String {
        val replace2 = raw.replace("\\\"", "\"").replace("&quot;", "\"").replace("\\\\", "")
        val cookies = mutableListOf<String>()
        val matcher = Pattern.compile("\"session_cookies\":\\s*\\[([^\\]]+)\\]").matcher(replace2)
        if (matcher.find()) {
            val sub = matcher.group(1) ?: ""
            val mCookie = Pattern.compile("\"name\":\"([^\"]+)\",\"value\":\"([^\"]+)\"").matcher(sub)
            while (mCookie.find()) {
                val n = mCookie.group(1)
                val v = mCookie.group(2)
                if (!n.isNullOrBlank() && !v.isNullOrBlank()) {
                    cookies.add("$n=$v")
                }
            }
        }
        return cookies.joinToString("; ")
    }

    /**
     * Kiểm tra response có yêu cầu 2FA không (GoMax fr.f)
     */
    private fun is2FaRequired(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = raw.lowercase(Locale.ROOT)
        return lower.contains("error_2fa") ||
                lower.contains("\"challenge\":\"totp\"") ||
                lower.contains("authentication_app") ||
                lower.contains("authentication app") ||
                lower.contains("trình tạo mã") ||
                lower.contains("code generator") ||
                lower.contains("two_step_verification_context")
    }

    /**
     * Trích xuất two_step_verification_context (GoMax fr.e)
     */
    private fun extract2FaContext(raw: String): String {
        if (raw.isBlank()) return ""
        val replace = raw.replace("\\\"", "\"").replace("&quot;", "\"").replace("\\\\", "")
        val m1 = Pattern.compile("two_step_verification_context\"?\\s*[:=]\\s*\"([^\"]{20,})\"").matcher(replace)
        if (m1.find()) return m1.group(1) ?: ""
        val m2 = Pattern.compile("two_step_verification_context[^\"]*\"([A-Za-z0-9_\\-\\+\\/=\\.%]{20,})\"").matcher(replace)
        if (m2.find()) return m2.group(1) ?: ""
        return ""
    }

    /**
     * Gửi mã xác thực 2FA lên Meta (GoMax fr.g)
     */
    private fun submit2FaCode(
        deviceId: String,
        machineId: String,
        twoStepContext: String,
        totpCode: String
    ): String {
        val oauthToken = NativeSecurity.getFbOAuthToken()
        val katanaUa = NativeSecurity.getFbKatanaUA()

        val clientInputParams = JSONObject().apply {
            put("auth_secure_device_id", "")
            put("machine_id", machineId)
            put("code", totpCode)
            put("should_trust_device", 1)
            put("family_device_id", deviceId)
            put("device_id", deviceId)
        }

        val serverParams = JSONObject().apply {
            put("INTERNAL__latency_qpl_marker_id", 36707139)
            put("device_id", deviceId)
            put("challenge", "totp")
            put("machine_id", machineId)
            put("INTERNAL__latency_qpl_instance_id", ThreadLocalRandom.current().nextLong(100000000000000L, 1000000000000000L))
            put("two_step_verification_context", twoStepContext)
            put("flow_source", "two_factor_login")
        }

        val rootParams = JSONObject().apply {
            put("client_input_params", clientInputParams)
            put("server_params", serverParams)
        }

        val ntContext = JSONObject().apply {
            put("using_white_navbar", true)
            put("styles_id", "55d2af294359fa6bbdb8e045ff01fc5e")
            put("pixel_ratio", 1.5)
            put("is_push_on", true)
            put("debug_tooling_metadata_token", JSONObject.NULL)
            put("is_flipper_enabled", false)
            put("theme_params", JSONArray())
            put("bloks_version", BLOKS_VERSION_2FA)
        }

        val variables = JSONObject().apply {
            put("params", rootParams.toString())
            put("bloks_versioning_id", BLOKS_VERSION_2FA)
            put("app_id", APP_ID_2FA)
            put("using_white_navbar", true)
            put("styles_id", "55d2af294359fa6bbdb8e045ff01fc5e")
            put("pixel_ratio", 1.5)
            put("is_push_on", true)
            put("debug_tooling_metadata_token", JSONObject.NULL)
            put("is_flipper_enabled", false)
            put("theme_params", JSONArray())
            put("bloks_version", BLOKS_VERSION_2FA)
            put("scale", 1)
            put("nt_context", ntContext)
        }

        val formBody = FormBody.Builder()
            .add("method", "post")
            .add("pretty", "false")
            .add("format", "json")
            .add("server_timestamps", "true")
            .add("locale", "vi_VN")
            .add("purpose", "fetch")
            .add("fb_api_req_friendly_name", "FbBloksActionRootQuery-$APP_ID_2FA")
            .add("fb_api_caller_class", "graphservice")
            .add("client_doc_id", CLIENT_DOC_ID_2FA)
            .add("variables", variables.toString())
            .add("fb_api_analytics_tags", "[\"GraphServices\"]")
            .add("generate_session_cookies", "1")
            .add("client_trace_id", UUID.randomUUID().toString())
            .build()

        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .header("User-Agent", katanaUa)
            .header("Authorization", oauthToken)
            .header("X-Fb-Connection-Type", "WIFI")
            .header("X-Fb-Http-Engine", "Tigon/Liger")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .post(formBody)
            .build()

        return httpClient.newCall(request).execute().use { res ->
            res.body?.string() ?: ""
        }
    }

    /**
     * Đăng nhập hoàn chỉnh bằng UID|Pass|2FA theo chuẩn GoMax fr.java
     */
    fun loginCAA(contactPoint: String, passwordRaw: String, twoFaSecret: String = ""): LoginResult {
        val oauthToken = NativeSecurity.getFbOAuthToken()
        val katanaUa = NativeSecurity.getFbKatanaUA()
        val clientDocId = NativeSecurity.getFbBloksDocId()

        val encryptedPasswordB64 = FacebookPasswordEncryptor.encryptPassword(passwordRaw)
        val passwordEncrypted = if (!encryptedPasswordB64.isNullOrBlank()) {
            "#PWD_FB4A:2:${System.currentTimeMillis() / 1000}:$encryptedPasswordB64"
        } else {
            passwordRaw
        }

        val deviceId = UUID.randomUUID().toString()
        val familyDeviceId = UUID.randomUUID().toString()
        val waterfallId = UUID.randomUUID().toString()

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
            val rawRes = httpClient.newCall(request).execute().use { res ->
                res.body?.string() ?: ""
            }

            var finalBody = rawRes

            // Nếu Meta yêu cầu 2FA -> Tự động xử lý bằng TOTP từ GoMax fr.g
            if (is2FaRequired(rawRes)) {
                if (twoFaSecret.isNotBlank()) {
                    val totp = TotpGenerator.generateTotp(twoFaSecret)
                    val twoStepCtx = extract2FaContext(rawRes)
                    if (totp.isNotBlank() && twoStepCtx.isNotBlank()) {
                        val verifyRes = submit2FaCode(
                            deviceId = deviceId,
                            machineId = deviceId.substring(0, 24),
                            twoStepContext = twoStepCtx,
                            totpCode = totp
                        )
                        if (verifyRes.isNotBlank()) {
                            finalBody = verifyRes
                        }
                    }
                } else {
                    return LoginResult(
                        isSuccess = false,
                        message = "Tài khoản yêu cầu mã 2FA. Vui lòng cung cấp khóa 2FA!",
                        rawResponse = rawRes
                    )
                }
            }

            // Bóc tách token và cookies
            val tokenRegex = Regex("""["'](?:access_token|token)["']\s*:\s*["']([A-Za-z0-9\-_|]+)["']""")
            val uidRegex = Regex("""["'](?:uid|user_id|actor_id|c_user)["']\s*:\s*["']?(\d+)["']?""")

            val token = tokenRegex.find(finalBody)?.groupValues?.get(1)
            val uid = uidRegex.find(finalBody)?.groupValues?.get(1) ?: contactPoint
            val cookies = extractCookies(finalBody)

            val isOk = !token.isNullOrEmpty() || (cookies.contains("c_user=") && cookies.contains("xs="))

            if (isOk) {
                LoginResult(
                    isSuccess = true,
                    userId = uid,
                    accessToken = token,
                    cookies = cookies,
                    message = "Đăng nhập thành công",
                    rawResponse = finalBody
                )
            } else {
                LoginResult(
                    isSuccess = false,
                    message = parseErrorMessage(finalBody),
                    rawResponse = finalBody
                )
            }
        } catch (e: Exception) {
            LoginResult(false, null, null, null, null, null, e.message ?: "Lỗi kết nối", "")
        }
    }

    /**
     * Đăng nhập trực tiếp bằng Cookie theo chuẩn GoMax ar.java
     * Kiểm tra cookie còn LIVE qua mbasic.facebook.com/me và lấy UID, Tên người dùng
     */
    fun loginWithCookie(cookieStr: String): LoginResult {
        val cleanCookie = cookieStr.replace("\r", "").replace("\n", "").filter { it.code in 32..126 }.trim()
        val cUserMatcher = Pattern.compile("c_user=(\\d+)").matcher(cleanCookie)
        val uidFromCookie = if (cUserMatcher.find()) cUserMatcher.group(1) ?: "" else ""

        val req = Request.Builder()
            .url("https://mbasic.facebook.com/me")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Cookie", cleanCookie)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            .get()
            .build()

        return try {
            httpClient.newCall(req).execute().use { res ->
                val finalUrl = res.request.url.toString()
                val body = res.body?.string() ?: ""

                // Nếu bị redirect về /login/ hoặc checkpoint -> Cookie DIE
                if (finalUrl.contains("/login") || finalUrl.contains("checkpoint") || body.contains("login_form") || body.contains("checkpointSubmitButton")) {
                    return LoginResult(
                        isSuccess = false,
                        message = "Cookie đã hết hạn hoặc tài khoản bị Checkpoint!",
                        rawResponse = body
                    )
                }

                // Lấy UID thực tế
                val realUid = when {
                    uidFromCookie.isNotBlank() -> uidFromCookie
                    finalUrl.contains("profile.php?id=") -> finalUrl.substringAfter("id=").substringBefore("&")
                    finalUrl.contains("facebook.com/") -> finalUrl.substringAfter("facebook.com/").substringBefore("?").substringBefore("/")
                    else -> "N/A"
                }

                // Lấy tên từ thẻ <title> hoặc og:title
                var name = realUid
                val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(body)
                if (titleMatcher.find()) {
                    val rawTitle = titleMatcher.group(1)?.replace("| Facebook", "")?.replace("- Facebook", "")?.trim() ?: ""
                    if (rawTitle.isNotBlank() && !rawTitle.lowercase().contains("facebook") && !rawTitle.lowercase().contains("đăng nhập")) {
                        name = rawTitle
                    }
                }

                val avatar = if (realUid != "N/A") "https://graph.facebook.com/v21.0/$realUid/picture?type=large" else ""

                LoginResult(
                    isSuccess = true,
                    userId = realUid,
                    userName = name,
                    accessToken = null,
                    cookies = cleanCookie,
                    avatarUrl = avatar,
                    message = "Đăng nhập Cookie thành công",
                    rawResponse = body
                )
            }
        } catch (e: Exception) {
            LoginResult(false, null, null, null, null, null, e.message ?: "Lỗi kiểm tra Cookie", "")
        }
    }
}
