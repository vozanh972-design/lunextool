package com.cayxu.app.utils

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object FacebookLiveChecker {

    private const val TAG = "FacebookLiveChecker"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Kiểm tra cookie bằng cách tải trang profile và kiểm tra avatar.
     * @param cookieString chuỗi cookie (có thể rỗng)
     * @param onResult (uid: String?, isLive: Boolean, avatarUrl: String?)
     */
    fun checkCookieWithAvatar(
        cookieString: String,
        onResult: (uid: String?, isLive: Boolean, avatarUrl: String?) -> Unit
    ) {
        try {
            val uid = extractUidFromCookie(cookieString)
            if (uid == null) {
                Log.w(TAG, "❌ Không tìm thấy c_user trong cookie")
                onResult(null, false, null)
                return
            }

            val builder = Request.Builder()
                .url("https://m.facebook.com/$uid")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")

            if (cookieString.isNotBlank()) {
                builder.addHeader("Cookie", cookieString)
            }

            val request = builder.build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e(TAG, "Request failed: ${e.message}")
                    try { onResult(uid, false, null) } catch (_: Exception) {}
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        response.use {
                            val html = it.body?.string() ?: ""
                            val finalUrl = it.request.url.toString()

                            if (finalUrl.contains("login") || html.contains("login") && html.contains("password")) {
                                Log.w(TAG, "❌ Bị redirect về login")
                                onResult(uid, false, null)
                                return
                            }

                            val avatarUrl = extractAvatarUrl(html)
                            if (avatarUrl != null) {
                                Log.d(TAG, "✅ Cookie hợp lệ, UID: $uid, Avatar: $avatarUrl")
                                onResult(uid, true, avatarUrl)
                            } else {
                                val hasProfileContent = html.contains("profile") || html.contains("_1dwg") || html.contains("profilePic")
                                if (hasProfileContent) {
                                    Log.d(TAG, "✅ Cookie hợp lệ (profile content), UID: $uid")
                                    onResult(uid, true, null)
                                } else {
                                    Log.w(TAG, "❌ Không tìm thấy avatar hoặc nội dung profile")
                                    onResult(uid, false, null)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response: ${e.message}")
                        try { onResult(uid, false, null) } catch (_: Exception) {}
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            try { onResult(null, false, null) } catch (_: Exception) {}
        }
    }

    /**
     * Kiểm tra cookie + lấy avatar thật + tên hiển thị của tài khoản.
     * @param onResult (uid, isLive, avatarUrl, fullName)
     */
    fun checkCookieWithAvatarAndName(
        cookieString: String,
        onResult: (uid: String?, isLive: Boolean, avatarUrl: String?, fullName: String?) -> Unit
    ) {
        try {
            val uid = extractUidFromCookie(cookieString)
            if (uid == null) {
                Log.w(TAG, "❌ Không tìm thấy c_user trong cookie")
                onResult(null, false, null, null)
                return
            }

            val builder = Request.Builder()
                .url("https://m.facebook.com/$uid")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")

            if (cookieString.isNotBlank()) {
                builder.addHeader("Cookie", cookieString)
            }

            val request = builder.build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e(TAG, "Request failed: ${e.message}")
                    try { onResult(uid, false, null, null) } catch (_: Exception) {}
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        response.use {
                            val html = it.body?.string() ?: ""
                            val finalUrl = it.request.url.toString()

                            if (finalUrl.contains("login") || (html.contains("login") && html.contains("password"))) {
                                Log.w(TAG, "❌ Bị redirect về login")
                                onResult(uid, false, null, null)
                                return
                            }

                            val avatarUrl = extractAvatarUrlV2(html) ?: extractAvatarUrl(html)
                            val fullName = extractFullName(html)

                            val hasProfileContent = avatarUrl != null || fullName != null ||
                                    html.contains("profile") || html.contains("_1dwg") || html.contains("profilePic")

                            if (hasProfileContent) {
                                Log.d(TAG, "✅ Cookie hợp lệ, UID: $uid, Avatar: $avatarUrl, Tên: $fullName")
                                onResult(uid, true, avatarUrl, fullName)
                            } else {
                                Log.w(TAG, "❌ Không tìm thấy avatar/tên hoặc nội dung profile")
                                onResult(uid, false, avatarUrl, fullName)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response: ${e.message}")
                        try { onResult(uid, false, null, null) } catch (_: Exception) {}
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            try { onResult(null, false, null, null) } catch (_: Exception) {}
        }
    }

    private fun extractUidFromCookie(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        try {
            val pairs = cookie.split(';')
            for (pair in pairs) {
                val trimmed = pair.trim()
                if (trimmed.startsWith("c_user=")) {
                    return trimmed.substringAfter("c_user=").trim()
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun extractAvatarUrl(html: String): String? {
        try {
            val patterns = listOf(
                """data-profile-pic-url="([^"]+)""".toRegex(),
                """<img[^>]*class="[^"]*profilePic[^"]*"[^>]*src="([^"]+)""".toRegex(),
                """<div[^>]*role="img"[^>]*style="background-image:\s*url\(['"]?([^'"]+)['"]?\)""".toRegex(),
                """https://scontent\.[^"]+\.fbcdn\.net/[^"]+_n\.(?:jpg|png|gif|webp)""".toRegex()
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val url = match.groupValues[1]
                    if (!url.contains("silhouette") && !url.contains("default_avatar")) {
                        return url
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // Avatar thật ở layout mới (m.facebook.com dạng MSite) luôn có class "rounded gray-border",
    // khác với icon overlay (chỉ có class "img contain")
    private fun extractAvatarUrlV2(html: String): String? {
        try {
            val pattern = """<img[^>]+src="([^"]+)"[^>]*class="[^"]*rounded gray-border[^"]*"""".toRegex()
            val match = pattern.find(html)
            if (match != null) {
                val url = match.groupValues[1].replace("&amp;", "&")
                if (!url.contains("silhouette") && !url.contains("default_avatar")) {
                    return url
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun extractFullName(html: String): String? {
        try {
            // 1) Cách ổn định nhất: thẻ <title> luôn chứa tên hiển thị của trang cá nhân
            val titleMatch = """<title[^>]*>([^<]+)</title>""".toRegex(RegexOption.IGNORE_CASE).find(html)
            if (titleMatch != null) {
                var title = unescapeText(titleMatch.groupValues[1])
                title = title.substringBefore(" | ").substringBefore(" - ").trim()
                if (title.isNotBlank() &&
                    !title.equals("Facebook", ignoreCase = true) &&
                    !title.equals("Log in", ignoreCase = true) &&
                    !title.equals("Đăng nhập", ignoreCase = true)
                ) {
                    return title
                }
            }

            // 2) Fallback: layout MSite, tên nằm trong <span class="f4"> ngay trong nút
            // role="button" mà aria-label trùng khớp nội dung span đó
            val pattern = """aria-label="([^"]+)"[^>]{0,400}?class="f4"[^>]*>\s*([^<]+?)\s*(?:&nbsp;)?\s*</span>""".toRegex()
            val match = pattern.find(html)
            if (match != null) {
                val ariaLabel = unescapeText(match.groupValues[1])
                val spanText = unescapeText(match.groupValues[2])
                if (ariaLabel.isNotBlank() && ariaLabel == spanText) {
                    return spanText
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun unescapeText(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        checkCookieWithAvatar(cookieString) { uid, isLive, _ ->
            onResult(uid, isLive)
        }
    }
}
