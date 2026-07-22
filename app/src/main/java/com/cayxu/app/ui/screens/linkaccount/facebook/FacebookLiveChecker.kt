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

    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        checkCookieWithAvatar(cookieString) { uid, isLive, _ ->
            onResult(uid, isLive)
        }
    }
}
