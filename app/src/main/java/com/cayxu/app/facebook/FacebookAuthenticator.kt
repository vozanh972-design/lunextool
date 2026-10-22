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
 * Xác thực và đăng nhập Facebook chuẩn xác 100% qua Katana Native API & Graph API.
 * KHÔNG sử dụng web scraping (mbasic, www, m.facebook.com).
 */
@Keep
class FacebookAuthenticator {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun login(
        uid: String,
        pass: String,
        twoFaSecret: String,
        proxyStr: String? = null,
        rawCookie: String? = null
    ): FacebookAuthResult {
        val client = if (!proxyStr.isNullOrBlank()) buildProxiedClient(proxyStr) else httpClient

        // TRƯỜNG HỢP 1: Đăng nhập bằng Cookie trực tiếp nếu có Cookie mà không có password
        val effectiveCookie = rawCookie?.trim()?.takeIf { it.contains("c_user=") || it.contains("xs=") }
            ?: pass.trim().takeIf { it.contains("c_user=") || it.contains("xs=") }
            ?: uid.trim().takeIf { it.contains("c_user=") || it.contains("xs=") }

        if (!effectiveCookie.isNullOrBlank() && (pass.isBlank() || pass == effectiveCookie || uid == effectiveCookie)) {
            val mgr = FacebookAccountManager()
            val cookieAcc = mgr.getTokenFromCookie(effectiveCookie, proxyStr)
            if (cookieAcc != null && cookieAcc.isLive) {
                return FacebookAuthResult(isSuccess = true, account = cookieAcc)
            }
        }

        // TRƯỜNG HỢP 2: Đăng nhập bằng Token EAA trực tiếp nếu có
        val directToken = uid.trim().takeIf { it.startsWith("EAA") }
            ?: pass.trim().takeIf { it.startsWith("EAA") }
            ?: twoFaSecret.trim().takeIf { it.startsWith("EAA") }

        if (!directToken.isNullOrBlank()) {
            val mgr = FacebookAccountManager()
            try {
                val details = mgr.fetchAccountDetailsWithToken(directToken, proxyStr)
                if (details.isLive) {
                    return FacebookAuthResult(isSuccess = true, account = details)
                }
            } catch (_: Exception) {}
        }

        // TRƯỜNG HỢP 3: Đăng nhập UID | PASS | 2FA chuẩn Katana Native API (b-graph.facebook.com)
        val appToken = NativeSecurity.getFbAppToken()
        val apiKey = NativeSecurity.getFbApiKey()
        val sig = NativeSecurity.getFbSig()
        val userAgent = NativeSecurity.getFbDalvikUA()

        val adid = UUID.randomUUID().toString()
        val jazoest = (10000..99999).random().toString()
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        val cleanDatr = if (!rawCookie.isNullOrBlank() && rawCookie.contains("datr=")) {
            rawCookie.substringAfter("datr=").substringBefore(";").trim()
        } else null

        val machineId = if (!cleanDatr.isNullOrBlank() && cleanDatr.length >= 24) {
            cleanDatr.substring(0, 24)
        } else {
            (1..24).map { chars.random() }.joinToString("")
        }

        val passwords = mutableListOf<String>()
        val encPass = FacebookPasswordEncryptor.encryptPassword(pass)
        if (!encPass.isNullOrBlank()) {
            passwords.add(encPass)
        }
        if (encPass != pass) {
            passwords.add(pass)
        }

        var lastResult: FacebookAuthResult? = null
        val cleanFullCookie = rawCookie?.replace("\r", "")?.replace("\n", "")?.filter { it.code in 32..126 }?.trim()

        for (pwd in passwords) {
            try {
                val totpCode = if (twoFaSecret.isNotBlank()) TotpGenerator.generateTotp(twoFaSecret) else ""

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
                    .header("Content-Type", "application/x-www-form-urlencoded")
                if (!cleanFullCookie.isNullOrBlank()) {
                    reqBuilder.header("Cookie", cleanFullCookie)
                }
                val request = reqBuilder.post(formBuilder.build()).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                val json = JSONObject(responseBody)

                // Kiểm tra nếu cần 2FA bước 2
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
                        .header("Content-Type", "application/x-www-form-urlencoded")
                    if (!cleanFullCookie.isNullOrBlank()) {
                        retryReqBuilder.header("Cookie", cleanFullCookie)
                    }
                    val retryReq = retryReqBuilder.post(retryForm).build()

                    val retryRes = client.newCall(retryReq).execute()
                    val retryBody = retryRes.body?.string() ?: ""
                    val retryJson = JSONObject(retryBody)
                    val res = handleAuthJsonResponse(uid, pass, twoFaSecret, proxyStr, cleanFullCookie, retryJson, retryBody)
                    if (res.isSuccess) return res
                    lastResult = res
                    continue
                }

                val res = handleAuthJsonResponse(uid, pass, twoFaSecret, proxyStr, cleanFullCookie, json, responseBody)
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
        rawCookie: String?,
        json: JSONObject,
        rawBody: String
    ): FacebookAuthResult {
        if (json.has("access_token")) {
            val rawToken = json.getString("access_token")
            val realUid = json.optString("uid", uid).ifBlank { uid }

            val mgr = FacebookAccountManager()
            val eaaaaToken = mgr.convertToken(rawToken, "350685531728", proxyStr) ?: rawToken

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
            val finalSavedCookie = if (!rawCookie.isNullOrBlank()) rawCookie else cookieStr

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
                note = finalSavedCookie,
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
