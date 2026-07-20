package com.cayxu.app.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object FacebookLiveChecker {

    private const val TAG = "FacebookLiveChecker"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun checkUidLiveWithAvatar(uid: String, onResult: (isLive: Boolean, avatarUrl: String?) -> Unit) {
        if (uid.isBlank()) {
            mainHandler.post { onResult(false, null) }
            return
        }

        try {
            val request = Request.Builder()
                .url("https://graph.facebook.com/$uid/picture?type=normal&redirect=false")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Check UID failed: ${e.message}")
                    mainHandler.post { onResult(false, null) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            when (it.code) {
                                200 -> {
                                    val json = JSONObject(it.body?.string() ?: "{}")
                                    val data = json.optJSONObject("data")
                                    val isSilhouette = data?.optBoolean("is_silhouette", true) ?: true
                                    val url = data?.optString("url", null)

                                    if (!isSilhouette && url != null) {
                                        mainHandler.post { onResult(true, url) }
                                    } else {
                                        // Fallback
                                        checkProfilePage(uid) { live, avatar ->
                                            mainHandler.post { onResult(live, avatar) }
                                        }
                                    }
                                }
                                else -> {
                                    // Fallback
                                    checkProfilePage(uid) { live, avatar ->
                                        mainHandler.post { onResult(live, avatar) }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Parse error", e)
                            mainHandler.post { onResult(false, null) }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception check UID", e)
            mainHandler.post { onResult(false, null) }
        }
    }

    private fun checkProfilePage(uid: String, onResult: (isLive: Boolean, avatarUrl: String?) -> Unit) {
        try {
            val request = Request.Builder()
                .url("https://m.facebook.com/$uid")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mainHandler.post { onResult(false, null) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            mainHandler.post { onResult(false, null) }
                            return
                        }
                        val html = it.body?.string() ?: ""
                        val avatarPattern = Regex("""(https?://[^\s"']+\.(?:jpg|jpeg|png|gif|webp)[^\s"']*)""")
                        val match = avatarPattern.find(html)
                        val isLoginPage = html.contains("login") && html.contains("password")

                        if (match != null && !isLoginPage) {
                            val avatarUrl = match.groupValues[1]
                            if (avatarUrl.contains("profile") || avatarUrl.contains("avatar") || avatarUrl.contains("pic")) {
                                mainHandler.post { onResult(true, avatarUrl) }
                            } else {
                                mainHandler.post { onResult(false, null) }
                            }
                        } else {
                            mainHandler.post { onResult(false, null) }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            mainHandler.post { onResult(false, null) }
        }
    }

    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        if (cookieString.isBlank()) {
            mainHandler.post { onResult(null, false) }
            return
        }

        try {
            val request = Request.Builder()
                .url("https://graph.facebook.com/me?fields=id")
                .addHeader("Cookie", cookieString)
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mainHandler.post { onResult(null, false) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            when (it.code) {
                                200 -> {
                                    val json = JSONObject(it.body?.string() ?: "{}")
                                    val uid = json.optString("id", null)
                                    if (!uid.isNullOrEmpty()) {
                                        mainHandler.post { onResult(uid, true) }
                                    } else {
                                        mainHandler.post { onResult(null, false) }
                                    }
                                }
                                else -> {
                                    mainHandler.post { onResult(null, false) }
                                }
                            }
                        } catch (e: Exception) {
                            mainHandler.post { onResult(null, false) }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            mainHandler.post { onResult(null, false) }
        }
    }
}
