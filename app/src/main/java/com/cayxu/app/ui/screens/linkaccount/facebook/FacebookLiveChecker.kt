package com.cayxu.app.utils

import android.content.Context
import android.webkit.*
import kotlinx.coroutines.*

object FacebookLiveChecker {

    private const val TAG = "FacebookLiveChecker"

    fun checkCookie(context: Context, cookieString: String, onResult: (uid: String?, isLive: Boolean) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                performCheck(context, cookieString)
            }
            onResult(result.first, result.second)
        }
    }

    private suspend fun performCheck(context: Context, cookieString: String): Pair<String?, Boolean> {
        return suspendCancellableCoroutine { continuation ->
            val webView = WebView(context)
            var isCompleted = false

            webView.apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!isCompleted) {
                            val cookies = CookieManager.getInstance().getCookie("https://m.facebook.com")
                            val uid = extractUidFromCookie(cookies)
                            isCompleted = true
                            continuation.resume(Pair(uid, uid != null))
                            webView.destroy()
                        }
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resume(Pair(null, false))
                            webView.destroy()
                        }
                    }
                }
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setCookie("https://m.facebook.com", cookieString)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush()
            }

            webView.loadUrl("https://m.facebook.com")
        }
    }

    private fun extractUidFromCookie(cookieString: String?): String? {
        if (cookieString.isNullOrEmpty()) return null
        val pairs = cookieString.split(';')
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.startsWith("c_user=")) {
                return trimmed.substringAfter("c_user=").trim()
            }
        }
        return null
    }
}
