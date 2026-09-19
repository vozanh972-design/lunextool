package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookPageItem
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

/**
 * Xác thực và đăng nhập Facebook chuẩn 100% Native Facebook Engine (CAA Bloks GraphQL).
 * Bỏ hoàn toàn cơ chế đăng nhập cũ, lấy đầy đủ Avatar HD, Ảnh Bìa và Page UID 615.
 */
@Keep
class FacebookAuthenticator {

    fun login(
        uid: String,
        pass: String,
        twoFaSecret: String,
        proxyStr: String? = null,
        rawCookie: String? = null
    ): FacebookAuthResult {
        val proxyParts = proxyStr?.split(":")
        val proxyHost = proxyParts?.getOrNull(0)
        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

        val authEngine = FacebookAuthEngine(proxyHost = proxyHost, proxyPort = proxyPort)
        val loginResult = authEngine.loginCAA(contactPoint = uid, passwordRaw = pass)

        if (loginResult.isSuccess && !loginResult.accessToken.isNullOrBlank()) {
            val token = loginResult.accessToken
            val realUid = loginResult.userId?.ifBlank { uid } ?: uid

            // Lấy Cookie nếu có trong raw response hoặc từ getSessionforApp
            val cookieBuilder = StringBuilder()
            try {
                val jsonRes = JSONObject(loginResult.rawResponse)
                if (jsonRes.has("session_cookies")) {
                    val cookiesArr = jsonRes.getJSONArray("session_cookies")
                    for (i in 0 until cookiesArr.length()) {
                        val c = cookiesArr.getJSONObject(i)
                        val name = c.optString("name")
                        val value = c.optString("value")
                        if (name.isNotBlank()) cookieBuilder.append("$name=$value; ")
                    }
                }
            } catch (_: Exception) {}

            var finalCookie = cookieBuilder.toString().trimEnd(' ', ';')
            if (finalCookie.isBlank() && !rawCookie.isNullOrBlank()) {
                finalCookie = rawCookie
            }

            // 1. Lấy thông tin Avatar HD & Ảnh bìa (Cover) từ FacebookMediaEngine
            val mediaEngine = FacebookMediaEngine(
                accessToken = token,
                userId = realUid,
                proxyHost = proxyHost,
                proxyPort = proxyPort
            )
            val profileMedia = mediaEngine.getProfileMedia(realUid)

            val fullName = profileMedia?.name?.ifBlank { realUid } ?: realUid
            val avatarUrl = profileMedia?.avatarUrl ?: "https://graph.facebook.com/v21.0/$realUid/picture?type=large"
            val coverUrl = profileMedia?.coverUrl.orEmpty()

            // 2. Lấy danh sách Fanpage chuẩn UID 615 từ FacebookPageEngine
            val pageEngine = FacebookPageEngine(
                accessToken = token,
                proxyHost = proxyHost,
                proxyPort = proxyPort
            )
            val pagesList = pageEngine.getAdminedPages(token)

            val finalAccount = FacebookAccount(
                uid = realUid,
                name = fullName,
                link = twoFaSecret,
                note = finalCookie,
                phone = proxyStr.orEmpty(),
                bio = token,
                isLive = true,
                avatar = avatarUrl,
                cover = coverUrl,
                pages = pagesList,
                password = pass
            )

            return FacebookAuthResult(
                isSuccess = true,
                account = finalAccount
            )
        } else {
            val errorMsg = loginResult.message?.ifBlank { "Đăng nhập thất bại: Sai tài khoản/mật khẩu hoặc Checkpoint" }
                ?: "Đăng nhập thất bại"

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
}
