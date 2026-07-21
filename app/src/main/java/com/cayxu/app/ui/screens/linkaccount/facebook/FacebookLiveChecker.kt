package com.cayxu.app.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object FacebookLiveChecker {

    private const val TAG = "FacebookLiveChecker"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)   // tự xử lý redirect để bắt Location
        .build()

    /**
     * Trích xuất UID từ cookie (c_user)
     */
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

    /**
     * Kiểm tra cookie – trả về (uid, isLive, avatarUrl)
     * isLive = true nếu cookie còn hiệu lực
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

            // Dùng /me để lấy trang cá nhân (ổn định hơn dùng UID)
            val request = Request.Builder()
                .url("https://m.facebook.com/me")
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

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    try {
                        response.use {
                            val code = response.code
                            val finalUrl = response.request.url.toString()

                            // ⭐️ Kiểm tra redirect
                            if (code in 300..399) {
                                val location = response.header("Location")
                                if (location?.contains("login") == true) {
                                    Log.w(TAG, "❌ Redirect đến login: $location")
                                    onResult(uid, false, null)
                                    return
                                }
                            }

                            // ⭐️ Nếu code != 200 → coi như không hợp lệ
                            if (code != 200) {
                                Log.w(TAG, "❌ Response code = $code")
                                onResult(uid, false, null)
                                return
                            }

                            val html = response.body?.string() ?: ""

                            // ⭐️ Kiểm tra nội dung form login
                            if (html.contains("login") && html.contains("password")) {
                                Log.w(TAG, "❌ Trang chứa form login")
                                onResult(uid, false, null)
                                return
                            }

                            // ⭐️ Kiểm tra dấu hiệu profile (có thể thêm từ khoá)
                            val hasProfile = html.contains("profile") ||
                                    html.contains("_1dwg") ||
                                    html.contains("profilePic") ||
                                    html.contains("profile_pic") ||
                                    html.contains("data-profile-pic-url") ||
                                    html.contains("user") // trong JSON có thể có "user"

                            if (!hasProfile) {
                                Log.w(TAG, "⚠️ Không thấy nội dung profile rõ ràng, nhưng vẫn có thể login")
                            }

                            val avatarUrl = extractAvatarUrl(html)

                            Log.d(TAG, "✅ Cookie hợp lệ, UID: $uid, Avatar: $avatarUrl")
                            onResult(uid, true, avatarUrl)
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

    /**
     * Trích xuất avatar từ HTML – giữ nguyên logic
     */
    private fun extractAvatarUrl(html: String): String? {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "extractAvatarUrl error: ${e.message}", e)
            return null
        }
    }

    // ✅ Hàm tiện lợi – bỏ tham số context không dùng
    fun checkCookie(cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        checkCookieWithAvatar(cookieString) { uid, isLive, _ ->
            onResult(uid, isLive)
        }
    }
}
