package com.cayxu.app.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit

object FacebookLiveChecker {

    private const val TAG = "FacebookLiveChecker"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Kiểm tra cookie có hợp lệ không bằng cách gửi request đến m.facebook.com
     * @param cookieString chuỗi cookie (định dạng: name1=value1; name2=value2; ...)
     * @param onResult (uid: String?, isLive: Boolean)
     */
    fun checkCookie(cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        try {
            // Parse cookie thành Map
            val cookieMap = mutableMapOf<String, String>()
            cookieString.split(';').forEach { pair ->
                val trimmed = pair.trim()
                val eqIndex = trimmed.indexOf('=')
                if (eqIndex > 0) {
                    val key = trimmed.substring(0, eqIndex).trim()
                    val value = trimmed.substring(eqIndex + 1).trim()
                    if (key.isNotEmpty()) {
                        cookieMap[key] = value
                    }
                }
            }

            // Kiểm tra có c_user không
            val cUser = cookieMap["c_user"]
            if (cUser.isNullOrEmpty()) {
                onResult(null, false)
                return
            }

            // Xây dựng cookie string theo định dạng của OkHttp
            val cookieBuilder = StringBuilder()
            cookieMap.forEach { (key, value) ->
                if (cookieBuilder.isNotEmpty()) cookieBuilder.append("; ")
                cookieBuilder.append(key).append("=").append(value)
            }

            val request = Request.Builder()
                .url("https://m.facebook.com/")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .addHeader("Cookie", cookieBuilder.toString())
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e(TAG, "Request failed", e)
                    onResult(null, false)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            // Thường là redirect hoặc lỗi
                            val effectiveUrl = it.request.url.toString()
                            // Kiểm tra xem có bị redirect đến login không
                            if (effectiveUrl.contains("login") || effectiveUrl.contains("home.php") == false) {
                                onResult(null, false)
                            } else {
                                // Có thể vẫn live dù response code không 200 (ví dụ redirect đến home.php)
                                val isLive = extractUidFromHtml(it.body?.string()) != null
                                onResult(if (isLive) extractUidFromHtml(it.body?.string()) else null, isLive)
                            }
                            return
                        }

                        val html = it.body?.string() ?: ""
                        val uid = extractUidFromHtml(html)
                        if (uid != null) {
                            Log.d(TAG, "✅ Cookie hợp lệ, UID: $uid")
                            onResult(uid, true)
                        } else {
                            // Thử lấy từ cookie manager (có thể response không chứa uid trực tiếp)
                            val cookiesFromResponse = it.headers("Set-Cookie")
                            var cUserFromResponse: String? = null
                            cookiesFromResponse.forEach { cookie ->
                                if (cookie.startsWith("c_user=")) {
                                    cUserFromResponse = cookie.substringAfter("c_user=").substringBefore(";").trim()
                                }
                            }
                            if (cUserFromResponse != null) {
                                onResult(cUserFromResponse, true)
                            } else {
                                Log.d(TAG, "❌ Cookie không hợp lệ hoặc hết hạn")
                                onResult(null, false)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cookie", e)
            onResult(null, false)
        }
    }

    private fun extractUidFromHtml(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        // Tìm c_user trong HTML (thường có trong script hoặc meta)
        val patterns = listOf(
            "\"c_user\":\"(\\d+)\"",
            "\"c_user\":\"(\\d+)\"".toRegex(),
            "c_user\\s*=\\s*['\"]?(\\d+)['\"]?",
            "window\\.__INITIAL_STATE__.*?\"c_user\":\"(\\d+)\"",
            "https://m\\.facebook\\.com/(\\d+)" // fallback
        )
        for (pattern in patterns) {
            val regex = when (pattern) {
                is Regex -> pattern
                else -> Regex(pattern)
            }
            val match = regex.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        // Nếu không tìm thấy, kiểm tra xem có phải trang chủ đã đăng nhập không (chứa "home.php" hoặc "messages")
        if (html.contains("home.php") || html.contains("messages") || html.contains("news_feed")) {
            // Nếu đã đăng nhập mà không lấy được uid, trả về "unknown" hoặc null
            return "unknown" // hoặc null tùy bạn
        }
        return null
    }
}
