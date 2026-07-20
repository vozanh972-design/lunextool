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
        try {
            if (cookieString.isBlank()) {
                onResult(null, false)
                return
            }

            val request = Request.Builder()
                .url("https://graph.facebook.com/me?fields=id")
                .addHeader("Cookie", cookieString)
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    try {
                        onResult(null, false)
                    } catch (ignore: Exception) { }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        response.use {
                            val body = it.body?.string() ?: "{}"
                            when (it.code) {
                                200 -> {
                                    try {
                                        val json = JSONObject(body)
                                        val uid = json.optString("id", null)
                                        if (!uid.isNullOrEmpty()) {
                                            onResult(uid, true)
                                        } else {
                                            onResult(null, false)
                                        }
                                    } catch (e: Exception) {
                                        onResult(null, false)
                                    }
                                }
                                401, 403 -> onResult(null, false)
                                else -> onResult(null, false)
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            onResult(null, false)
                        } catch (ignore: Exception) { }
                    }
                }
            })
        } catch (e: Exception) {
            try {
                onResult(null, false)
            } catch (ignore: Exception) { }
        }
    }
}
