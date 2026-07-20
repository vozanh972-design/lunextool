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
        .build()

    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        try {
            if (cookieString.isBlank()) {
                Log.w(TAG, "Cookie rỗng")
                onResult(null, false)
                return
            }

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

            val cUser = cookieMap["c_user"]
            if (cUser.isNullOrEmpty()) {
                Log.w(TAG, "❌ Không tìm thấy c_user trong cookie")
                onResult(null, false)
                return
            }

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
                    try {
                        onResult(null, false)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Lỗi khi gọi onResult failure", ex)
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) {
                                val effectiveUrl = it.request.url.toString()
                                if (effectiveUrl.contains("login") || !effectiveUrl.contains("home.php")) {
                                    onResult(null, false)
                                } else {
                                    val html = it.body?.string() ?: ""
                                    val uid = extractUidFromHtml(html)
                                    onResult(uid, uid != null)
                                }
                                return
                            }

                            val html = it.body?.string() ?: ""
                            val uid = extractUidFromHtml(html)
                            if (uid != null) {
                                Log.d(TAG, "✅ Cookie hợp lệ, UID: $uid")
                                onResult(uid, true)
                            } else {
                                // Thử lấy từ Set-Cookie
                                var cUserFromResponse: String? = null
                                it.headers("Set-Cookie").forEach { cookie ->
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi xử lý response", e)
                        try {
                            onResult(null, false)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Lỗi khi gọi onResult từ catch", ex)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trong checkCookie", e)
            try {
                onResult(null, false)
            } catch (ex: Exception) {
                Log.e(TAG, "Lỗi khi gọi onResult từ try-catch ngoài", ex)
            }
        }
    }

    private fun extractUidFromHtml(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        return try {
            val patterns = listOf(
                "\"c_user\":\"(\\d+)\"",
                "c_user\\s*=\\s*['\"]?(\\d+)['\"]?",
                "window\\.__INITIAL_STATE__.*?\"c_user\":\"(\\d+)\"",
                "https://m\\.facebook\\.com/(\\d+)"
            )
            for (pattern in patterns) {
                val regex = Regex(pattern)
                val match = regex.find(html)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
            if (html.contains("home.php") || html.contains("messages") || html.contains("news_feed")) {
                "unknown"
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi extractUidFromHtml", e)
            null
        }
    }
}
