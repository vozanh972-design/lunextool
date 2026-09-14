package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.util.NativeSecurity
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Keep
data class FacebookAuthResult(
    val isSuccess: Boolean,
    val account: FacebookAccount,
    val errorMessage: String? = null
)

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
        proxyStr: String? = null
    ): FacebookAuthResult {
        val client = if (!proxyStr.isNullOrBlank()) buildProxiedClient(proxyStr) else httpClient
        val oauthToken = NativeSecurity.getFbOAuthToken()
        val userAgent = NativeSecurity.getFbKatanaUA()

        try {
            // Bước 1: Gửi request đăng nhập đầu tiên
            val formBuilder = FormBody.Builder()
                .add("email", uid)
                .add("password", pass)
                .add("credentials_type", "password")
                .add("generate_session_cookies", "1")
                .add("generate_machine_id", "1")
                .add("generate_analytics_claim", "1")
                .add("locale", "vi_VN")
                .add("client_country_code", "VN")
                .add("method", "auth.login")
                .add("format", "JSON")

            // Nếu có mã 2FA secret, sinh ngay mã 6 số TOTP
            val totpCode = if (twoFaSecret.isNotBlank()) TotpGenerator.generateTotp(twoFaSecret) else ""
            if (totpCode.isNotBlank()) {
                formBuilder.add("twofactor_code", totpCode)
            }

            val request = Request.Builder()
                .url("https://b-graph.facebook.com/auth/login")
                .header("User-Agent", userAgent)
                .header("Authorization", oauthToken)
                .post(formBuilder.build())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            val json = JSONObject(responseBody)

            // Kiểm tra nếu cần 2FA bước 2
            if (json.has("error_code") && json.optInt("error_code") == 406 && totpCode.isNotBlank()) {
                // Thử gửi lại kèm 2FA code và confirmation
                val retryForm = FormBody.Builder()
                    .add("email", uid)
                    .add("password", pass)
                    .add("twofactor_code", totpCode)
                    .add("credentials_type", "two_factor")
                    .add("generate_session_cookies", "1")
                    .add("generate_machine_id", "1")
                    .add("locale", "vi_VN")
                    .add("method", "auth.login")
                    .add("format", "JSON")
                    .build()

                val retryReq = Request.Builder()
                    .url("https://b-graph.facebook.com/auth/login")
                    .header("User-Agent", userAgent)
                    .header("Authorization", oauthToken)
                    .post(retryForm)
                    .build()

                val retryRes = client.newCall(retryReq).execute()
                val retryBody = retryRes.body?.string() ?: ""
                val retryJson = JSONObject(retryBody)
                return handleAuthJsonResponse(uid, pass, twoFaSecret, proxyStr, retryJson, retryBody)
            }

            return handleAuthJsonResponse(uid, pass, twoFaSecret, proxyStr, json, responseBody)
        } catch (e: Exception) {
            return FacebookAuthResult(
                isSuccess = false,
                account = FacebookAccount(
                    uid = uid,
                    name = pass,
                    link = twoFaSecret,
                    phone = proxyStr.orEmpty(),
                    isLive = false
                ),
                errorMessage = e.message ?: "Lỗi kết nối Facebook"
            )
        }
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
            val token = json.getString("access_token")
            val realUid = json.optString("uid", uid).ifBlank { uid }
            
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
            var avatarUrl = ""
            var email = ""
            var pages = emptyList<FacebookPageItem>()

            try {
                val mgr = FacebookAccountManager()
                val details = mgr.fetchAccountDetails(token, proxyStr)
                fullName = details.name.ifBlank { realUid }
                avatarUrl = details.avatarUrl ?: ""
                email = details.email ?: ""
                pages = details.pages.map { p ->
                    FacebookPageItem(
                        pageId = p.pageId,
                        pageName = p.pageName,
                        pageToken = p.pageToken ?: "",
                        additionalProfileId = p.additionalProfileId ?: "",
                        isLive = true
                    )
                }
            } catch (_: Exception) {}

            val finalAccount = FacebookAccount(
                uid = realUid,
                name = fullName,
                link = twoFaSecret,
                note = cookieStr,
                phone = proxyStr.orEmpty(),
                bio = token,
                isLive = true,
                avatar = avatarUrl,
                email = email,
                pages = pages
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
                    name = pass,
                    link = twoFaSecret,
                    phone = proxyStr.orEmpty(),
                    isLive = false
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
