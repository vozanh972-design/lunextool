package com.cayxu.app.utils

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object FacebookLiveChecker {

    private const val TAG = "FacebookLiveChecker"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Kiểm tra cookie và trả về uid, isLive, avatarUrl
     */
    fun checkCookieWithAvatar(
        cookieString: String,
        onResult: (uid: String?, isLive: Boolean, avatarUrl: String?) -> Unit
    ) {
        if (cookieString.isBlank()) {
            onResult(null, false, null)
            return
        }

        try {
            val request = Request.Builder()
                .url("https://graph.facebook.com/me?fields=id")
                .addHeader("Cookie", cookieString)
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e(TAG, "Check cookie failed: ${e.message}")
                    onResult(null, false, null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        val body = it.body?.string() ?: "{}"
                        when (it.code) {
                            200 -> {
                                try {
                                    val json = JSONObject(body)
                                    val uid = json.optString("id", null)
                                    if (!uid.isNullOrEmpty()) {
                                        // Lấy avatar URL
                                        getAvatarUrl(uid) { avatarUrl ->
                                            Log.d(TAG, "✅ Cookie hợp lệ, UID: $uid, Avatar: $avatarUrl")
                                            onResult(uid, true, avatarUrl)
                                        }
                                    } else {
                                        Log.w(TAG, "⚠️ Response 200 nhưng không có id: $body")
                                        onResult(null, false, null)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Lỗi parse JSON: $body", e)
                                    onResult(null, false, null)
                                }
                            }
                            401, 403 -> {
                                Log.w(TAG, "❌ Cookie không hợp lệ (HTTP ${it.code})")
                                onResult(null, false, null)
                            }
                            else -> {
                                Log.w(TAG, "⚠️ HTTP ${it.code}: $body")
                                onResult(null, false, null)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception khi kiểm tra cookie", e)
            onResult(null, false, null)
        }
    }

    /**
     * Lấy avatar URL từ UID
     */
    private fun getAvatarUrl(uid: String, onResult: (String?) -> Unit) {
        try {
            val request = Request.Builder()
                .url("https://graph.facebook.com/$uid/picture?type=normal&redirect=false")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    onResult(null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        when (it.code) {
                            200 -> {
                                try {
                                    val json = JSONObject(it.body?.string() ?: "{}")
                                    val data = json.optJSONObject("data")
                                    val url = data?.optString("url", null)
                                    val isSilhouette = data?.optBoolean("is_silhouette", true) ?: true
                                    if (!isSilhouette && url != null) {
                                        onResult(url)
                                    } else {
                                        onResult(null)
                                    }
                                } catch (e: Exception) {
                                    onResult(null)
                                }
                            }
                            else -> onResult(null)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            onResult(null)
        }
    }

    /**
     * Kiểm tra cookie cũ (chỉ trả về uid, isLive) – giữ lại để tương thích
     */
    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        checkCookieWithAvatar(cookieString) { uid, isLive, _ ->
            onResult(uid, isLive)
        }
    }
}
