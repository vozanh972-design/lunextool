package com.cayxu.app.ui.screens.golike

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Trình duyệt Web mini nhúng trực tiếp trang thật Golike (https://app.golike.net/jobs/tiktok) vào màn nổi.
 * Chạy trực tiếp 100% trên giao diện Web thật của Golike, không gọi API OkHttp bên ngoài, tránh triệt để lỗi 403 sig.
 */
object GolikeWebJobBridge {

    private const val TAG = "GolikeWebBridge"
    private var miniWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingGetJob: CompletableDeferred<JSONObject?>? = null
    private var pendingCompleteJob: CompletableDeferred<JSONObject?>? = null

    // Callback khi Web mở link làm job TikTok
    var onTikTokJobOpened: ((url: String) -> Unit)? = null

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
     * Tạo hoặc tái sử dụng WebView thật hiển thị trực tiếp trang Golike
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateMiniWebView(context: Context): WebView {
        miniWebView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        val wv = WebView(context.applicationContext)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = GolikeApiClient.USER_AGENT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUrl = request?.url?.toString() ?: return false
                Log.d(TAG, "Navigating to: $targetUrl")

                // Nếu là link TikTok (nhận việc) -> Mở thẳng app TikTok để làm job
                if (targetUrl.contains("tiktok.com", ignoreCase = true) ||
                    targetUrl.startsWith("snssdk1128://", ignoreCase = true) ||
                    targetUrl.contains("vt.tiktok.com", ignoreCase = true)
                ) {
                    onTikTokJobOpened?.invoke(targetUrl)
                    try {
                        TikTokAppLauncher.openUserProfile(context, targetUrl)
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Web Golike loaded: $url")
            }
        }

        wv.addJavascriptInterface(WebActionInterface(), "GolikeJobBridge")

        // Tải thẳng trang làm nhiệm vụ TikTok của Golike
        wv.loadUrl("https://app.golike.net/jobs/tiktok")
        miniWebView = wv
        return wv
    }

    /**
     * Nhận nhiệm vụ TikTok trực tiếp thông qua chính ngữ cảnh trình duyệt thật của Golike
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
                    
                    // Lấy token tự động từ chính localStorage của trang Golike
                    let token = localStorage.getItem('token') || '';
                    let tVal = localStorage.getItem('t') || '';
                    
                    let headers = {
                        'Accept': 'application/json, text/plain, */*'
                    };
                    if (token) {
                        headers['Authorization'] = token.startsWith('Bearer ') ? token : ('Bearer ' + token);
                    }
                    if (tVal) {
                        headers['t'] = tVal;
                    }

                    fetch(url, {
                        method: 'GET',
                        headers: headers
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

        return withTimeoutOrNull(25000L) { deferred.await() }
    }

    /**
     * Hoàn thành nhiệm vụ TikTok trực tiếp thông qua chính ngữ cảnh trình duyệt thật của Golike
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
                    let token = localStorage.getItem('token') || '';
                    let tVal = localStorage.getItem('t') || '';

                    let headers = {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json, text/plain, */*'
                    };
                    if (token) {
                        headers['Authorization'] = token.startsWith('Bearer ') ? token : ('Bearer ' + token);
                    }
                    if (tVal) {
                        headers['t'] = tVal;
                    }

                    fetch('/api/advertising/publishers/tiktok/complete-jobs', {
                        method: 'POST',
                        headers: headers,
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
     * Tự động bấm nút "Nhận việc ngay" trên trang Golike nếu có
     */
    fun clickGetJobOnWeb() {
        miniWebView?.post {
            try {
                miniWebView?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            let buttons = document.querySelectorAll('button, a, div[role="button"]');
                            for (let b of buttons) {
                                let txt = (b.innerText || '').toLowerCase();
                                if (txt.includes('nhận việc') || txt.includes('nhan viec') || txt.includes('lấy việc')) {
                                    b.click();
                                    return 'clicked_get_job';
                                }
                            }
                        } catch(e) {}
                        return 'not_found';
                    })();
                    """.trimIndent(),
                    null
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Tự động bấm nút "Hoàn thành" trên trang Golike sau khi làm xong trên TikTok
     */
    fun clickCompleteJobOnWeb() {
        miniWebView?.post {
            try {
                miniWebView?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            let buttons = document.querySelectorAll('button, a, div[role="button"]');
                            for (let b of buttons) {
                                let txt = (b.innerText || '').toLowerCase();
                                if (txt.includes('hoàn thành') || txt.includes('hoan thanh') || txt.includes('gửi')) {
                                    b.click();
                                    return 'clicked_complete';
                                }
                            }
                        } catch(e) {}
                        return 'not_found';
                    })();
                    """.trimIndent(),
                    null
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Tải lại trang Web Golike khi cần
     */
    fun reloadWeb() {
        miniWebView?.post {
            try {
                miniWebView?.reload()
            } catch (_: Exception) {}
        }
    }

    /**
     * Dọn dẹp WebView khi đóng màn nổi
     */
    fun destroy() {
        miniWebView?.post {
            try {
                miniWebView?.destroy()
                miniWebView = null
            } catch (_: Exception) {}
        }
    }
}
