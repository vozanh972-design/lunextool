package com.cayxu.app.tiktok.checker

import com.cayxu.app.util.NativeSecurity
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
 * TẤT CẢ URL, USER-AGENT, REGEX SCRIPT VÀ THUẬT TOÁN SNOWFLAKE ĐÃ ĐƯỢC BẢO MẬT Ở TẦNG C++ (libsqlitejni.so).
 * Chống dịch ngược và phân tích tĩnh/động bằng OLLVM Control Flow Flattening (CFF) + Mixed Boolean-Arithmetic (MBA).
 */
class TikTokProfileCheckerClient(
    private var proxyStr: String? = null
) {
    companion object {
        val BASE_URL: String get() = NativeSecurity.getTtBaseUrl()
        val MOBILE_USER_AGENT: String get() = NativeSecurity.getTtMobileUa()
        val DESKTOP_USER_AGENT: String get() = NativeSecurity.getTtDesktopUa()

        val USERNAME_REGEX = Pattern.compile("^(?=.{1,30}$)(?!.*\\.\\.)(?!\\.)[A-Za-z0-9](?:[A-Za-z0-9._]*[A-Za-z0-9])?$")
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
     * Giải mã ngày tạo tài khoản TikTok từ Snowflake UID 64-bit ở tầng Native C++
     */
    fun calculateAccountCreateTimestamp(uidStr: String): Long {
        return NativeSecurity.calculateTtAccountTimestamp(uidStr)
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
        if (profile != null && profile.userId.isNotBlank()) return@withContext profile

        // 2. Fallback thử Desktop UA
        val fallback = tryFetch(cleanUser, DESKTOP_USER_AGENT)
        if (fallback != null && fallback.userId.isNotBlank()) return@withContext fallback

        // 3. Nếu username có dấu tiếng Việt hoặc khoảng trắng (do nhận nhầm từ display name ở sheet chuyển đổi)
        // -> Tự động chuẩn hóa bỏ dấu & khoảng trắng để gọi API lấy đúng thông tin chính xác nhất
        val normalized = normalizeUsername(cleanUser)
        if (normalized.isNotBlank() && normalized != cleanUser.lowercase()) {
            val profNormalized = tryFetch(normalized, MOBILE_USER_AGENT) ?: tryFetch(normalized, DESKTOP_USER_AGENT)
            if (profNormalized != null && profNormalized.userId.isNotBlank()) return@withContext profNormalized
        }

        profile ?: fallback
    }

    private fun normalizeUsername(input: String): String {
        return try {
            val nfd = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
            val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
            pattern.matcher(nfd)
                .replaceAll("")
                .replace("đ", "d")
                .replace("Đ", "d")
                .replace("[^A-Za-z0-9._]".toRegex(), "")
                .lowercase()
        } catch (_: Exception) {
            input.replace("[^A-Za-z0-9._]".toRegex(), "").lowercase()
        }
    }

    private fun tryFetch(cleanUser: String, userAgent: String): TikTokFullProfile? {
        val url = "$BASE_URL/@$cleanUser"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", NativeSecurity.getTtAccept())
            .header("Accept-Language", NativeSecurity.getTtAcceptLang())
            .header("Sec-Fetch-Site", NativeSecurity.getTtSecFetchSite())
            .header("Sec-Fetch-Mode", NativeSecurity.getTtSecFetchMode())
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

                // 1. ƯU TIÊN SỐ 1: Phân tích & trích xuất bảo mật ở tầng Native C++ (libsqlitejni.so)
                val nativeParsedJson = NativeSecurity.extractTikTokProfileFromHtml(body, cleanUser)
                if (!nativeParsedJson.isNullOrBlank()) {
                    val root = JSONObject(nativeParsedJson)
                    val isLive = root.optBoolean("isLive", true)
                    val uid = root.optString("userId", "")
                    if (uid.isBlank() && !isLive) {
                        return TikTokFullProfile(
                            userId = "",
                            username = cleanUser,
                            nickname = cleanUser,
                            isLive = false
                        )
                    }
                    if (uid.isNotBlank()) {
                        val secUid = root.optString("secUid").takeIf { it.isNotBlank() }
                        val officialUser = root.optString("username", cleanUser).ifBlank { cleanUser }
                        val nick = root.optString("nickname", cleanUser).ifBlank { cleanUser }
                        val avatarHd = root.optString("avatarHdUrl").takeIf { it.isNotBlank() }
                        val avatarThumb = root.optString("avatarThumbUrl").takeIf { it.isNotBlank() }
                        val bio = root.optString("biography", "")
                        val followers = root.optLong("followerCount", 0L)
                        val following = root.optLong("followingCount", 0L)
                        val hearts = root.optLong("totalFavorited", 0L)
                        val videos = root.optLong("videoCount", 0L)
                        val isPriv = root.optBoolean("isPrivate", false)
                        val isVer = root.optBoolean("isVerified", false)
                        val createTs = root.optLong("createTimestampSec", 0L)

                        return TikTokFullProfile(
                            userId = uid,
                            secUid = secUid,
                            username = officialUser,
                            nickname = nick,
                            avatarHdUrl = avatarHd,
                            avatarThumbUrl = avatarThumb,
                            biography = bio,
                            followerCount = followers,
                            followingCount = following,
                            totalFavorited = hearts,
                            videoCount = videos,
                            isPrivate = isPriv,
                            isVerified = isVer,
                            createTimestampSec = createTs,
                            isLive = true
                        )
                    }
                }

                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
