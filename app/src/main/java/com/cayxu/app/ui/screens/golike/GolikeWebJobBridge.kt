package com.cayxu.app.ui.screens.golike

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Cầu nối thực thi nhiệm vụ Golike trực tiếp thông qua WebView mini.
 * Giúp tránh 100% lỗi 403 Sig/WAF vì các request được gửi từ chính ngữ cảnh trình duyệt Web thật.
 */
object GolikeWebJobBridge {

    private const val TAG = "GolikeWebBridge"
    private var miniWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingGetJob: CompletableDeferred<JSONObject?>? = null
    private var pendingCompleteJob: CompletableDeferred<JSONObject?>? = null

    @androidx.annotation.Keep
    class WebActionInterface {
        @JavascriptInterface
        fun onJobResult(jsonStr: String?) {
            Log.d(TAG, "onJobResult from Web: $jsonStr")
            val result = try {
                if (!jsonStr.isNullOrBlank()) JSONObject(jsonStr) else null
            } catch (e: Exception) {
                null
            }
            pendingGetJob?.complete(result)
        }

        @JavascriptInterface
        fun onCompleteResult(jsonStr: String?) {
            Log.d(TAG, "onCompleteResult from Web: $jsonStr")
            val result = try {
                if (!jsonStr.isNullOrBlank()) JSONObject(jsonStr) else null
            } catch (e: Exception) {
                null
            }
            pendingCompleteJob?.complete(result)
        }
    }

    /**
     * Khởi tạo hoặc tái sử dụng Mini WebView gắn trên màn nổi hoặc chạy nền.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun attachMiniWebView(context: Context): WebView {
        if (miniWebView != null) return miniWebView!!

        val wv = WebView(context.applicationContext)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = false // Tiết kiệm RAM và mạng
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = GolikeApiClient.USER_AGENT
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Mini WebView loaded: $url")
            }
        }
        wv.addJavascriptInterface(WebActionInterface(), "GolikeJobBridge")
        
        // Tải trang web chính để sẵn sàng nhận cookie / session
        wv.loadUrl("https://app.golike.net")
        miniWebView = wv
        return wv
    }

    /**
     * Nhận nhiệm vụ TikTok trực tiếp thông qua Web context (Tránh 403 Sig)
     */
    suspend fun getJobViaWeb(accountId: String, webData: String?): JSONObject? {
        val wv = miniWebView ?: return null
        val deferred = CompletableDeferred<JSONObject?>()
        pendingGetJob = deferred

        val effectiveData = webData?.takeIf { it.isNotBlank() && it != "null" } ?: "null"
        val jsCode = """
            (function() {
                try {
                    let accId = '$accountId';
                    let dataParam = '$effectiveData';
                    let url = '/api/advertising/publishers/tiktok/jobs?account_id=' + accId + '&data=' + encodeURIComponent(dataParam);
                    
                    fetch(url, {
                        method: 'GET',
                        headers: {
                            'Accept': 'application/json, text/plain, */*'
                        }
                    })
                    .then(r => r.json())
                    .then(data => {
                        window.GolikeJobBridge.onJobResult(JSON.stringify(data));
                    })
                    .catch(err => {
                        window.GolikeJobBridge.onJobResult(JSON.stringify({ status: 500, message: err.toString(), success: false }));
                    });
                } catch(e) {
                    window.GolikeJobBridge.onJobResult(JSON.stringify({ status: 500, message: e.toString(), success: false }));
                }
            })();
        """.trimIndent()

        mainHandler.post {
            try {
                wv.evaluateJavascript(jsCode, null)
            } catch (e: Exception) {
                deferred.complete(null)
            }
        }

        return withTimeoutOrNull(20000L) { deferred.await() }
    }

    /**
     * Hoàn thành nhiệm vụ TikTok trực tiếp thông qua Web context
     */
    suspend fun completeJobViaWeb(
        accountId: String,
        adsId: String,
        objectId: String,
        accountType: String = "tiktok"
    ): JSONObject? {
        val wv = miniWebView ?: return null
        val deferred = CompletableDeferred<JSONObject?>()
        pendingCompleteJob = deferred

        val payload = JSONObject().apply {
            put("account_id", accountId)
            put("ads_id", adsId)
            put("object_id", objectId)
            put("type", accountType)
        }.toString()

        val jsCode = """
            (function() {
                try {
                    let payload = $payload;
                    fetch('/api/advertising/publishers/tiktok/complete-jobs', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Accept': 'application/json, text/plain, */*'
                        },
                        body: JSON.stringify(payload)
                    })
                    .then(r => r.json())
                    .then(data => {
                        window.GolikeJobBridge.onCompleteResult(JSON.stringify(data));
                    })
                    .catch(err => {
                        window.GolikeJobBridge.onCompleteResult(JSON.stringify({ status: 500, message: err.toString(), success: false }));
                    });
                } catch(e) {
                    window.GolikeJobBridge.onCompleteResult(JSON.stringify({ status: 500, message: e.toString(), success: false }));
                }
            })();
        """.trimIndent()

        mainHandler.post {
            try {
                wv.evaluateJavascript(jsCode, null)
            } catch (e: Exception) {
                deferred.complete(null)
            }
        }

        return withTimeoutOrNull(25000L) { deferred.await() }
    }

    /**
     * Dọn dẹp WebView khi tắt màn nổi
     */
    fun destroy() {
        mainHandler.post {
            try {
                miniWebView?.destroy()
                miniWebView = null
            } catch (_: Exception) {}
        }
    }
}
