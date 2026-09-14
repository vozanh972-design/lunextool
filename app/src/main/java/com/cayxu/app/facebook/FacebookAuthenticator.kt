package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookPageItem
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

@Keep
data class FacebookAuthResult(
    val isSuccess: Boolean,
    val account: FacebookAccount,
    val errorMessage: String? = null
)

/**
 * Mã hóa mật khẩu Facebook bằng RSA + AES-256-GCM theo đúng giao thức Meta pwd_key_fetch.
 */
@Keep
object FacebookPasswordEncryptor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun encryptPassword(password: String): String? {
        return try {
            val keyFetchToken = NativeSecurity.getFbKeyFetchToken()
            val url = "https://b-graph.facebook.com/pwd_key_fetch?version=2&flow=CONTROLLER_INITIALIZATION&method=GET&fb_api_req_friendly_name=pwdKeyFetch&fb_api_caller_class=com.facebook.auth.login.AuthOperations&access_token=$keyFetchToken"
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

            // 1. Tạo ngẫu nhiên AES Key (32 bytes = 256 bits) và IV (12 bytes)
            val random = SecureRandom()
            val aesKeyBytes = ByteArray(32)
            random.nextBytes(aesKeyBytes)

            val ivBytes = ByteArray(12)
            random.nextBytes(ivBytes)

            // 2. Mã hóa AES Key bằng RSA (PKCS1Padding)
            val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
            val encryptedAesKey = rsaCipher.doFinal(aesKeyBytes)

            // 3. Mã hóa Password bằng AES-256-GCM với AAD = current timestamp
            val currentTimeSec = (System.currentTimeMillis() / 1000).toString()
            val aadBytes = currentTimeSec.toByteArray(Charsets.UTF_8)

            val gcmSpec = GCMParameterSpec(128, ivBytes) // 128 bit = 16 byte tag
            val secretKey = SecretKeySpec(aesKeyBytes, "AES")
            val gcmCipher = Cipher.getInstance("AES/GCM/NoPadding")
            gcmCipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            gcmCipher.updateAAD(aadBytes)
            val cipherTextWithTag = gcmCipher.doFinal(password.toByteArray(Charsets.UTF_8))

            // GCM trong Java nối tag 16 byte vào cuối ciphertext
            val tagLen = 16
            val encryptedPassLen = cipherTextWithTag.size - tagLen
            val encryptedPass = ByteArray(encryptedPassLen)
            val tag = ByteArray(tagLen)
            System.arraycopy(cipherTextWithTag, 0, encryptedPass, 0, encryptedPassLen)
            System.arraycopy(cipherTextWithTag, encryptedPassLen, tag, 0, tagLen)

            // 4. Pack buffer theo đúng định dạng nhị phân Facebook:
            // byte(1) + byte(keyId) + iv(12) + short_le(lenEncAesKey) + encAesKey + tag(16) + encPass
            val out = ByteArrayOutputStream()
            out.write(1) // version
            out.write(keyId) // keyId
            out.write(ivBytes) // iv 12 bytes

            val lenBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            lenBuf.putShort(encryptedAesKey.size.toShort())
            out.write(lenBuf.array()) // len Encrypted AES Key (2 bytes little-endian)

            out.write(encryptedAesKey)
            out.write(tag)
            out.write(encryptedPass)

            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}

@Keep
class FacebookAuthenticator {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Xác thực và đăng nhập Facebook bằng User/Pass/2FA thật sự.
     */
    fun login(
        uid: String,
        pass: String,
        twoFaSecret: String,
        proxyStr: String? = null,
        datrCookie: String? = null
    ): FacebookAuthResult {
        val client = if (!proxyStr.isNullOrBlank()) buildProxiedClient(proxyStr) else httpClient
        val oauthToken = NativeSecurity.getFbOAuthToken()
        val appToken = NativeSecurity.getFbAppToken()
        val apiKey = NativeSecurity.getFbApiKey()
        val sig = NativeSecurity.getFbSig()
        val userAgent = NativeSecurity.getFbDalvikUA()

        val adid = UUID.randomUUID().toString()
        val jazoest = (10000..99999).random().toString()
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val machineId = if (!datrCookie.isNullOrBlank() && datrCookie.length >= 24) {
            datrCookie.substring(0, 24)
        } else {
            (1..24).map { chars.random() }.joinToString("")
        }

        val passwords = mutableListOf<String>()
        val encPass = FacebookPasswordEncryptor.encryptPassword(pass)
        if (!encPass.isNullOrBlank()) {
            passwords.add(encPass)
        }
        passwords.add(pass)

        val cookieJar = mutableListOf<String>()
        if (!datrCookie.isNullOrBlank()) {
            cookieJar.add("datr=$datrCookie")
        }

        var lastResult: FacebookAuthResult? = null

        for (pwd in passwords) {
            try {
                val totpCode = if (twoFaSecret.isNotBlank()) TotpGenerator.generateTotp(twoFaSecret) else ""

                // Bước 1: Gửi request đăng nhập với tham số mã hóa từ tầng C++
                val formBuilder = FormBody.Builder()
                    .add("email", uid)
                    .add("password", pwd)
                    .add("generate_session_cookies", "1")
                    .add("locale", "vi_VN")
                    .add("client_country_code", "VN")
                    .add("access_token", appToken)
                    .add("api_key", apiKey)
                    .add("adid", adid)
                    .add("machine_id", machineId)
                    .add("jazoest", jazoest)
                    .add("fb_api_req_friendly_name", "authenticate")
                    .add("sig", sig)

                val reqBuilder = Request.Builder()
                    .url("https://b-graph.facebook.com/auth/login")
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                if (cookieJar.isNotEmpty()) {
                    reqBuilder.header("Cookie", cookieJar.joinToString("; "))
                }
                val request = reqBuilder.post(formBuilder.build()).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                val json = JSONObject(responseBody)

                // Kiểm tra nếu cần 2FA bước 2 (error_code 406 hoặc login_first_factor)
                val errorData = json.optJSONObject("error")?.optJSONObject("error_data")
                val is2FaRequired = (json.has("error_code") && json.optInt("error_code") == 406) || 
                                   (errorData != null && errorData.has("login_first_factor"))

                if (is2FaRequired && totpCode.isNotBlank()) {
                    val factor = errorData?.optString("login_first_factor", machineId) ?: machineId
                    val userId = errorData?.optString("uid", uid) ?: uid

                    val retryForm = FormBody.Builder()
                        .add("email", uid)
                        .add("access_token", appToken)
                        .add("twofactor_code", totpCode)
                        .add("password", pwd)
                        .add("userid", userId)
                        .add("machine_id", factor)
                        .add("generate_session_cookies", "1")
                        .build()

                    val retryReqBuilder = Request.Builder()
                        .url("https://b-graph.facebook.com/auth/login")
                        .header("User-Agent", userAgent)
                    if (cookieJar.isNotEmpty()) {
                        retryReqBuilder.header("Cookie", cookieJar.joinToString("; "))
                    }
                    val retryReq = retryReqBuilder.post(retryForm).build()

                    val retryRes = client.newCall(retryReq).execute()
                    val retryBody = retryRes.body?.string() ?: ""
                    val retryJson = JSONObject(retryBody)
                    val res = handleAuthJsonResponse(uid, pass, twoFaSecret, proxyStr, retryJson, retryBody)
                    if (res.isSuccess) return res
                    lastResult = res
                    continue
                }

                val res = handleAuthJsonResponse(uid, pass, twoFaSecret, proxyStr, json, responseBody)
                if (res.isSuccess) return res
                lastResult = res

                val errorMsg = if (json.has("error")) json.getJSONObject("error").optString("message", "") else ""
                if (errorMsg.contains("Invalid username or password") && pwd != passwords.last()) {
                    continue
                }
            } catch (e: Exception) {
                lastResult = FacebookAuthResult(
                    isSuccess = false,
                    account = FacebookAccount(
                        uid = uid,
                        name = uid,
                        link = twoFaSecret,
                        phone = proxyStr.orEmpty(),
                        isLive = false,
                        password = pass
                    ),
                    errorMessage = e.message ?: "Lỗi kết nối Facebook"
                )
            }
        }

        return lastResult ?: FacebookAuthResult(
            isSuccess = false,
            account = FacebookAccount(
                uid = uid,
                name = uid,
                link = twoFaSecret,
                phone = proxyStr.orEmpty(),
                isLive = false,
                password = pass
            ),
            errorMessage = "Đăng nhập thất bại"
        )
    }

    private fun handleAuthJsonResponse(
        uid: String,
        pass: String,
        twoFaSecret: String,
        proxyStr: String?,
        json: JSONObject,
        rawBody: String
    ): FacebookAuthResult {
        if (json.has("access_token")) {
            val rawToken = json.getString("access_token")
            val realUid = json.optString("uid", uid).ifBlank { uid }
            
            // Chuyển đổi token sang EAAAA (App ID 350685531728) theo đúng chuẩn getSessionforApp
            val mgr = FacebookAccountManager()
            val eaaaaToken = mgr.convertToken(rawToken, "350685531728", proxyStr) ?: rawToken

            // Xây dựng cookie từ session_cookies
            val cookieBuilder = StringBuilder()
            if (json.has("session_cookies")) {
                val cookiesArr = json.getJSONArray("session_cookies")
                for (i in 0 until cookiesArr.length()) {
                    val c = cookiesArr.getJSONObject(i)
                    val name = c.optString("name")
                    val value = c.optString("value")
                    if (name.isNotBlank()) {
                        cookieBuilder.append("$name=$value; ")
                    }
                }
            }
            val cookieStr = cookieBuilder.toString().trimEnd(' ', ';')

            // Lấy thêm thông tin Name, Avatar, Fanpage từ Graph API
            var fullName = realUid
            var avatarUrl = "https://graph.facebook.com/v19.0/$realUid/picture?type=large"
            var email = ""
            var pages = emptyList<FacebookPageItem>()

            try {
                val detailsAcc = mgr.fetchAccountDetailsWithToken(eaaaaToken, proxyStr)
                fullName = detailsAcc.name.ifBlank { realUid }
                avatarUrl = detailsAcc.avatar.ifBlank { avatarUrl }
                email = detailsAcc.email
                pages = detailsAcc.pages
            } catch (_: Exception) {}

            val finalAccount = FacebookAccount(
                uid = realUid,
                name = fullName,
                link = twoFaSecret,
                note = cookieStr,
                phone = proxyStr.orEmpty(),
                bio = eaaaaToken,
                isLive = true,
                avatar = avatarUrl,
                email = email,
                pages = pages,
                password = pass
            )

            return FacebookAuthResult(
                isSuccess = true,
                account = finalAccount
            )
        } else {
            val errorMsg = if (json.has("error_msg")) {
                json.getString("error_msg")
            } else if (json.has("error")) {
                json.getJSONObject("error").optString("message", "Đăng nhập thất bại")
            } else {
                "Tài khoản DIE, sai mật khẩu hoặc Checkpoint"
            }

            return FacebookAuthResult(
                isSuccess = false,
                account = FacebookAccount(
                    uid = uid,
                    name = uid,
                    link = twoFaSecret,
                    phone = proxyStr.orEmpty(),
                    isLive = false,
                    password = pass
                ),
                errorMessage = errorMsg
            )
        }
    }

    private fun buildProxiedClient(proxyStr: String): OkHttpClient {
        val parts = proxyStr.split(":")
        val host = parts[0]
        val port = parts[1].toInt()
        val builder = httpClient.newBuilder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))

        if (parts.size >= 4) {
            val user = parts[2]
            val pass = parts[3]
            builder.proxyAuthenticator { _, response ->
                val credential = Credentials.basic(user, pass)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        }
        return builder.build()
    }
}

