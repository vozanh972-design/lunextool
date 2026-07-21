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

    fun extractUidFromCookie(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        val pairs = cookie.split(';')
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.startsWith("c_user=")) {
                return trimmed.substringAfter("c_user=").trim()
            }
        }
        return null
    }

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

            val request = Request.Builder()
                .url("https://m.facebook.com/$uid")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Cookie", cookieString)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e(TAG, "Request failed: ${e.message}")
                    onResult(uid, false, null)
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
                                val hasProfileContent = html.contains("profile") || html.contains("_1dwg") || html.contains("profilePic") || html.contains("profile_pic")
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
                        Log.e(TAG, "Error processing response: ${e.message}", e)
                        onResult(uid, false, null)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            onResult(null, false, null)
        }
    }

    private fun extractAvatarUrl(html: String): String? {
        val patterns = listOf(
            """data-profile-pic-url="([^"]+)""".toRegex(),
            """<img[^>]*class="[^"]*profilePic[^"]*"[^>]*src="([^"]+)""".toRegex(),
            """<div[^>]*role="img"[^>]*style="background-image:\s*url\(['"]?([^'"]+)['"]?\)""".toRegex(),
            """https://scontent\.[^"]+\.fbcdn\.net/[^"]+_n\.(?:jpg|jpeg|png|gif|webp)""".toRegex()
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
        return null
    }

    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        checkCookieWithAvatar(cookieString) { uid, isLive, _ ->
            onResult(uid, isLive)
        }
    }
}
