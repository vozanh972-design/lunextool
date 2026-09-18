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

        val UNIVERSAL_DATA_REGEX = Pattern.compile("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>", Pattern.DOTALL)
        val SIGI_STATE_REGEX = Pattern.compile("<script id=\"SIGI_STATE\"[^>]*>(.*?)</script>", Pattern.DOTALL)
        val USERNAME_REGEX = Pattern.compile("^(?=.{1,30}$)(?!.*\\.\\.)(?!\\.)[A-Za-z0-9](?:[A-Za-z0-9._]*[A-Za-z0-9])?$")

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
     * Trả về null nếu lỗi mạng / không kết nối được (không tự tiện đánh dấu acc die)
     */
    suspend fun fetchProfile(username: String): TikTokFullProfile? = withContext(Dispatchers.IO) {
        val cleanUser = username.removePrefix("@").trim()
        if (cleanUser.isBlank()) return@withContext null

        // 1. Thử Mobile UA trước (tỷ lệ thành công cao nhất, không bị WAF)
        val profile = tryFetch(cleanUser, MOBILE_USER_AGENT)
        if (profile != null) return@withContext profile

        // 2. Fallback thử Desktop UA
        val fallback = tryFetch(cleanUser, DESKTOP_USER_AGENT)
        if (fallback != null) return@withContext fallback

        null
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
                if (response.code == 404) {
                    return TikTokFullProfile(
                        userId = "",
                        username = cleanUser,
                        nickname = cleanUser,
                        isLive = false
                    )
                }

                val body = response.body?.string() ?: ""

                // 1. Kiểm tra JSON dữ liệu từ __UNIVERSAL_DATA_FOR_REHYDRATION__
                val mUniversal = UNIVERSAL_DATA_REGEX.matcher(body)
                if (mUniversal.find()) {
                    val root = JSONObject(mUniversal.group(1))
                    val defaultScope = root.optJSONObject("__DEFAULT_SCOPE__")
                    val userDetail = defaultScope?.optJSONObject("webapp.user-detail")

                    val statusCode = userDetail?.optInt("statusCode", 0) ?: 0
                    if (statusCode == 10221) {
                        // User not found trên TikTok
                        return TikTokFullProfile(
                            userId = "",
                            username = cleanUser,
                            nickname = cleanUser,
                            isLive = false
                        )
                    }

                    val userInfo = userDetail?.optJSONObject("userInfo")
                    val userJson = userInfo?.optJSONObject("user")
                    val statsJson = userInfo?.optJSONObject("stats")

                    if (userJson != null) {
                        val userId = userJson.optString("id", userJson.optString("uid", ""))
                        if (userId.isNotBlank()) {
                            val secUid = userJson.optString("secUid", userJson.optString("sec_uid", null))
                            val nickname = userJson.optString("nickname", cleanUser)
                            val avatarLarger = userJson.optString("avatarLarger", "")
                            val avatarMedium = userJson.optString("avatarMedium", "")
                            val avatarThumb = userJson.optString("avatarThumb", null)
                            val finalAvatar = avatarLarger.ifBlank { avatarMedium }
                            val bio = userJson.optString("signature", "")
                            val isPrivate = userJson.optBoolean("privateAccount", false)
                            val isVerified = userJson.optBoolean("verified", false)

                            val followers = statsJson?.optLong("followerCount", 0L) ?: 0L
                            val following = statsJson?.optLong("followingCount", 0L) ?: 0L
                            val totalHearts = statsJson?.optLong("heartCount", statsJson.optLong("heart", 0L)) ?: 0L
                            val videoCount = statsJson?.optLong("videoCount", 0L) ?: 0L

                            // Thời gian tạo: ưu tiên lấy createTime có sẵn trong user JSON, nếu không có thì tính từ Snowflake UID
                            var createTimeSec = userJson.optLong("createTime", 0L)
                            if (createTimeSec <= 0L) {
                                createTimeSec = calculateAccountCreateTimestamp(userId)
                            }

                            val officialUsername = userJson.optString("uniqueId", cleanUser).ifBlank { cleanUser }

                            return TikTokFullProfile(
                                userId = userId,
                                secUid = secUid,
                                username = officialUsername,
                                nickname = nickname.ifBlank { cleanUser },
                                avatarHdUrl = finalAvatar,
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
                        }
                    }
                }

                // 2. Thử SIGI_STATE
                val mSigi = SIGI_STATE_REGEX.matcher(body)
                if (mSigi.find()) {
                    val root = JSONObject(mSigi.group(1))
                    val userModule = root.optJSONObject("UserModule")
                    val users = userModule?.optJSONObject("users")
                    val stats = userModule?.optJSONObject("stats")

                    val userJson = users?.optJSONObject(cleanUser)
                    val statsJson = stats?.optJSONObject(cleanUser)

                    if (userJson != null) {
                        val userId = userJson.optString("id", "")
                        if (userId.isNotBlank()) {
                            var createTimeSec = userJson.optLong("createTime", 0L)
                            if (createTimeSec <= 0L) {
                                createTimeSec = calculateAccountCreateTimestamp(userId)
                            }
                            val officialUsername = userJson.optString("uniqueId", cleanUser).ifBlank { cleanUser }

                            return TikTokFullProfile(
                                userId = userId,
                                secUid = userJson.optString("secUid", null),
                                username = officialUsername,
                                nickname = userJson.optString("nickname", cleanUser),
                                avatarHdUrl = userJson.optString("avatarLarger", userJson.optString("avatarMedium", null)),
                                avatarThumbUrl = userJson.optString("avatarThumb", null),
                                biography = userJson.optString("signature", ""),
                                followerCount = statsJson?.optLong("followerCount", 0L) ?: 0L,
                                followingCount = statsJson?.optLong("followingCount", 0L) ?: 0L,
                                totalFavorited = statsJson?.optLong("heartCount", 0L) ?: 0L,
                                videoCount = statsJson?.optLong("videoCount", 0L) ?: 0L,
                                createTimestampSec = createTimeSec,
                                isLive = true
                            )
                        }
                    }
                }

                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
