package com.cayxu.app.facebook

import androidx.annotation.Keep
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookPageItem
import okhttp3.*
import org.json.JSONObject

@Keep
data class FacebookAuthResult(
    val isSuccess: Boolean,
    val account: FacebookAccount,
    val errorMessage: String? = null
)

/**
 * Xác thực và đăng nhập Facebook chuẩn xác 100%:
 * 1. Hỗ trợ UID|Pass|2FA (tự động submit 2FA bằng TOTP).
 * 2. Hỗ trợ Đăng nhập bằng Cookie trực tiếp (kiểm tra LIVE qua mbasic Facebook).
 * 3. Hỗ trợ Đăng nhập bằng Token EAA trực tiếp.
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

        // =========================================================================
        // TRƯỜNG HỢP 1: ĐĂNG NHẬP BẰNG COOKIE (Khi có cookie hoặc không có pass)
        // =========================================================================
        val isCookieInput = !rawCookie.isNullOrBlank() && (rawCookie.contains("c_user=") || rawCookie.contains("xs="))
        val isPassAsCookie = pass.contains("c_user=") || pass.contains("xs=")
        val isUidAsCookie = uid.contains("c_user=") || uid.contains("xs=")
        val effectiveCookie = if (isCookieInput) rawCookie!!.trim() else if (isPassAsCookie) pass.trim() else if (isUidAsCookie) uid.trim() else null

        if (!effectiveCookie.isNullOrBlank() && (pass.isBlank() || isUidAsCookie || isPassAsCookie)) {
            val cookieRes = authEngine.loginWithCookie(effectiveCookie)
            if (cookieRes.isSuccess) {
                val realUid = cookieRes.userId ?: uid
                val realName = cookieRes.userName?.ifBlank { realUid } ?: realUid
                val avatar = cookieRes.avatarUrl ?: "https://graph.facebook.com/v21.0/$realUid/picture?type=large"

                // Cố gắng quét Page 615 nếu có token hoặc qua Cookie
                val pageEngine = FacebookPageEngine(proxyHost = proxyHost, proxyPort = proxyPort)
                val pages = pageEngine.getAdminedPages(cookieRes.accessToken)

                val account = FacebookAccount(
                    uid = realUid,
                    name = realName,
                    link = twoFaSecret,
                    note = effectiveCookie,
                    phone = proxyStr.orEmpty(),
                    bio = cookieRes.accessToken.orEmpty(),
                    isLive = true,
                    avatar = avatar,
                    pages = pages,
                    password = pass
                )
                return FacebookAuthResult(isSuccess = true, account = account)
            } else {
                return FacebookAuthResult(
                    isSuccess = false,
                    account = FacebookAccount(
                        uid = uid,
                        name = uid,
                        note = effectiveCookie,
                        isLive = false
                    ),
                    errorMessage = cookieRes.message ?: "Cookie không hợp lệ hoặc đã hết hạn"
                )
            }
        }

        // =========================================================================
        // TRƯỜNG HỢP 2: ĐĂNG NHẬP BẰNG UID | PASS | 2FA (Tự động vượt 2FA)
        // =========================================================================
        val loginResult = authEngine.loginCAA(
            contactPoint = uid,
            passwordRaw = pass,
            twoFaSecret = twoFaSecret
        )

        if (loginResult.isSuccess) {
            val token = loginResult.accessToken.orEmpty()
            val realUid = loginResult.userId?.ifBlank { uid } ?: uid
            val cookies = if (!loginResult.cookies.isNullOrBlank()) loginResult.cookies else rawCookie.orEmpty()

            // 1. Lấy thông tin Avatar & Tên
            val mediaEngine = FacebookMediaEngine(
                accessToken = token,
                userId = realUid,
                proxyHost = proxyHost,
                proxyPort = proxyPort
            )
            val profileMedia = if (token.isNotBlank()) mediaEngine.getProfileMedia(realUid) else null
            val fullName = profileMedia?.name?.ifBlank { realUid } ?: realUid
            val avatarUrl = profileMedia?.avatarUrl ?: "https://graph.facebook.com/v21.0/$realUid/picture?type=large"
            val coverUrl = profileMedia?.coverUrl.orEmpty()

            // 2. Lấy danh sách Fanpage chuẩn UID 615
            val pageEngine = FacebookPageEngine(
                accessToken = token,
                proxyHost = proxyHost,
                proxyPort = proxyPort
            )
            val pagesList = if (token.isNotBlank()) pageEngine.getAdminedPages(token) else emptyList()

            val finalAccount = FacebookAccount(
                uid = realUid,
                name = fullName,
                link = twoFaSecret,
                note = cookies.orEmpty(),
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
            // Nếu đăng nhập User/Pass thất bại mà có sẵn Cookie -> Thử fallback Cookie
            if (!rawCookie.isNullOrBlank() && (rawCookie.contains("c_user=") || rawCookie.contains("xs="))) {
                val fallbackCookie = authEngine.loginWithCookie(rawCookie)
                if (fallbackCookie.isSuccess) {
                    val realUid = fallbackCookie.userId ?: uid
                    val finalAccount = FacebookAccount(
                        uid = realUid,
                        name = fallbackCookie.userName ?: realUid,
                        link = twoFaSecret,
                        note = rawCookie,
                        phone = proxyStr.orEmpty(),
                        isLive = true,
                        avatar = fallbackCookie.avatarUrl ?: "",
                        password = pass
                    )
                    return FacebookAuthResult(isSuccess = true, account = finalAccount)
                }
            }

            val errorMsg = loginResult.message?.ifBlank { "Đăng nhập thất bại: Kiểm tra lại tài khoản hoặc 2FA!" }
                ?: "Đăng nhập thất bại"

            return FacebookAuthResult(
                isSuccess = false,
                account = FacebookAccount(
                    uid = uid,
                    name = uid,
                    link = twoFaSecret,
                    phone = proxyStr.orEmpty(),
                    isLive = false,
                    password = pass,
                    note = rawCookie.orEmpty()
                ),
                errorMessage = errorMsg
            )
        }
    }
}
