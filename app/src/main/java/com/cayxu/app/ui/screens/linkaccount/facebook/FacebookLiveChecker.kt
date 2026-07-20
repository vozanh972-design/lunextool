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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        if (cookieString.isBlank()) {
            onResult(null, false)
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
                    Log.e(TAG, "Request failed", e)
                    onResult(null, false)
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
                                        Log.d(TAG, "✅ Cookie hợp lệ, UID: $uid")
                                        onResult(uid, true)
                                    } else {
                                        Log.w(TAG, "⚠️ Response 200 nhưng không có id: $body")
                                        onResult(null, false)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Lỗi parse JSON: $body", e)
                                    onResult(null, false)
                                }
                            }
                            401, 403 -> {
                                Log.w(TAG, "❌ Cookie không hợp lệ (HTTP ${it.code})")
                                onResult(null, false)
                            }
                            else -> {
                                Log.w(TAG, "⚠️ HTTP ${it.code}: $body")
                                onResult(null, false)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception khi kiểm tra cookie", e)
            onResult(null, false)
        }
    }
}
