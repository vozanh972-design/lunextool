package com.cayxu.app.tiktok.checker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * CLIENT CHECK FULL THÔNG TIN TÀI KHOẢN TIKTOK (Tên, Avatar HD, Follow, Tim, Ngày Tạo, Trạng thái).
 *
 * Sử dụng kết hợp:
 * 1. Web Profile SSR & Universal Data API với Mobile User-Agent để vượt qua WAF (không cần Cookie/Token)
 * 2. Thuật toán giải mã Snowflake ID (dịch bit 64-bit) để tính chính xác ngày giờ tạo acc.
 */
class TikTokProfileCheckerClient(
    private var proxyStr: String? = null
) {
    companion object {
        const val BASE_URL = "https://www.tiktok.com"

        // Regex trích xuất JSON Data từ trang Profile HTML (hỗ trợ nhiều định dạng thẻ script)
        val UNIVERSAL_DATA_REGEX = Pattern.compile("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>", Pattern.DOTALL)
        val SIGI_STATE_REGEX = Pattern.compile("<script id=\"SIGI_STATE\"[^>]*>(.*?)</script>", Pattern.DOTALL)
        val USERNAME_REGEX = Pattern.compile("^(?=.{1,30}$)(?!.*\\.\\.)(?!\\.)[A-Za-z0-9](?:[A-Za-z0-9._]*[A-Za-z0-9])?$")

        // Mobile UA giúp bypass SlardarWAF / Cloudflare captcha của TikTok Web
        const val MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    }

    private var httpClient: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)

        if (!proxyStr.isNullOrEmpty()) {
            setupProxy(builder, proxyStr!!)
        }
        httpClient = builder.build()
    }

    private fun setupProxy(builder: OkHttpClient.Builder, proxyStr: String) {
        try {
            val parts = proxyStr.split(":")
            val host = parts[0].trim()
            val port = parts[1].trim().toInt()
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))

            if (parts.size >= 4) {
                val user = parts[2].trim()
                val pass = parts[3].trim()
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(user, pass)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Thuật toán giải mã Ngày Tạo Tài Khoản từ Snowflake UID của TikTok
     * Quy tắc ByteDance: 32 bit cao nhất của 64-bit UID chính là Unix Timestamp (giây) khi đăng ký.
     */
    fun calculateAccountCreateTimestamp(uidStr: String): Long {
        return try {
            val uid = uidStr.toULong()
            val ts = (uid shr 32).toLong()
            if (ts in 1400000000L..2500000000L) ts else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Tra cứu thông tin Profile TikTok bằng Username (chạy trên Dispatchers.IO)
     */
    suspend fun fetchProfile(username: String): TikTokFullProfile = withContext(Dispatchers.IO) {
        val cleanUser = username.removePrefix("@").trim()
        if (cleanUser.isBlank()) {
            return@withContext TikTokFullProfile(
                userId = "",
                username = cleanUser,
                nickname = cleanUser,
                isLive = false
            )
        }

        // Thử Mobile UA trước (tỷ lệ thành công cao nhất không bị WAF)
        val profile = tryFetch(cleanUser, MOBILE_USER_AGENT)
        if (profile != null) return@withContext profile

        // Fallback thử Desktop UA
        val fallback = tryFetch(cleanUser, DESKTOP_USER_AGENT)
        if (fallback != null) return@withContext fallback

        // Nếu cả 2 đều không ra json hoặc bị 404
        TikTokFullProfile(
            userId = "",
            username = cleanUser,
            nickname = cleanUser,
            isLive = false
        )
    }

    private fun tryFetch(cleanUser: String, userAgent: String): TikTokFullProfile? {
        val url = "$BASE_URL/@$cleanUser"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-Mode", "navigate")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""

                if (response.code == 404 || body.contains("user-not-found") || body.contains("Couldn't find this account")) {
                    return TikTokFullProfile(
                        userId = "",
                        username = cleanUser,
                        nickname = cleanUser,
                        isLive = false
                    )
                }

                val userJson = extractUserJson(body, cleanUser)
                val statsJson = extractStatsJson(body, cleanUser)

                if (userJson != null) {
                    val userId = userJson.optString("id", userJson.optString("uid", ""))
                    val secUid = userJson.optString("secUid", userJson.optString("sec_uid", null))
                    val nickname = userJson.optString("nickname", cleanUser)
                    val avatarHd = userJson.optString("avatarLarger", userJson.optString("avatarMedium", null))
                    val avatarThumb = userJson.optString("avatarThumb", null)
                    val bio = userJson.optString("signature", "")
                    val isPrivate = userJson.optBoolean("privateAccount", false)
                    val isVerified = userJson.optBoolean("verified", false)

                    val followers = statsJson?.optLong("followerCount", 0L) ?: 0L
                    val following = statsJson?.optLong("followingCount", 0L) ?: 0L
                    val totalHearts = statsJson?.optLong("heartCount", statsJson.optLong("heart", 0L)) ?: 0L
                    val videoCount = statsJson?.optLong("videoCount", 0L) ?: 0L

                    val createTimeSec = calculateAccountCreateTimestamp(userId)

                    TikTokFullProfile(
                        userId = userId,
                        secUid = secUid,
                        username = cleanUser,
                        nickname = nickname.ifBlank { cleanUser },
                        avatarHdUrl = avatarHd,
                        avatarThumbUrl = avatarThumb,
                        biography = bio,
                        followerCount = followers,
                        followingCount = following,
                        totalFavorited = totalHearts,
                        videoCount = videoCount,
                        isPrivate = isPrivate,
                        isVerified = isVerified,
                        createTimestampSec = createTimeSec,
                        isLive = true
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractUserJson(html: String, username: String): JSONObject? {
        try {
            val mUniversal = UNIVERSAL_DATA_REGEX.matcher(html)
            if (mUniversal.find()) {
                val root = JSONObject(mUniversal.group(1))
                val defaultScope = root.optJSONObject("__DEFAULT_SCOPE__")
                val userDetail = defaultScope?.optJSONObject("webapp.user-detail")?.optJSONObject("userInfo")
                if (userDetail != null && userDetail.has("user")) {
                    return userDetail.getJSONObject("user")
                }
            }

            val mSigi = SIGI_STATE_REGEX.matcher(html)
            if (mSigi.find()) {
                val root = JSONObject(mSigi.group(1))
                val userModule = root.optJSONObject("UserModule")?.optJSONObject("users")
                if (userModule != null && userModule.has(username)) {
                    return userModule.getJSONObject(username)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun extractStatsJson(html: String, username: String): JSONObject? {
        try {
            val mUniversal = UNIVERSAL_DATA_REGEX.matcher(html)
            if (mUniversal.find()) {
                val root = JSONObject(mUniversal.group(1))
                val defaultScope = root.optJSONObject("__DEFAULT_SCOPE__")
                val userDetail = defaultScope?.optJSONObject("webapp.user-detail")?.optJSONObject("userInfo")
                if (userDetail != null && userDetail.has("stats")) {
                    return userDetail.getJSONObject("stats")
                }
            }

            val mSigi = SIGI_STATE_REGEX.matcher(html)
            if (mSigi.find()) {
                val root = JSONObject(mSigi.group(1))
                val statsModule = root.optJSONObject("UserModule")?.optJSONObject("stats")
                if (statsModule != null && statsModule.has(username)) {
                    return statsModule.getJSONObject(username)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
