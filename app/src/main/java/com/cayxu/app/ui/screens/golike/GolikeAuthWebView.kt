package com.cayxu.app.ui.screens.golike

import android.annotation.SuppressLint
import android.net.http.SslError
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Quản lý WebView tự động tiêm JS và bắt Session Golike chuẩn GoMax 1.2.2
 */
object GolikeAuthWebView {

    const val LOGIN_URL = "https://app.golike.net"

    val INJECT_JS = """
        (function() {
          if (window.hasInjectedAuthDetector) { 
            if (window.goMaxCaptureSessionStore) window.goMaxCaptureSessionStore(); 
            return; 
          }
          window.hasInjectedAuthDetector = true;

          function readHeader(headers, name) {
            if (!headers || !name) return '';
            try {
              if (typeof headers.get === 'function') 
                return headers.get(name) || headers.get(name.toLowerCase()) || headers.get(name.toUpperCase()) || '';
            } catch(e) {}
            try {
              if (Array.isArray(headers)) {
                for (let i = 0; i < headers.length; i++) {
                  let h = headers[i] || [];
                  if (String(h[0]).toLowerCase() === name.toLowerCase()) return h[1] || '';
                }
              }
            } catch(e) {}
            return '';
          }

          function captureSessionStore() {
            try {
              let signingKey = '';
              let userId = '';
              let webData = localStorage.getItem('__') || 'null';
              let deviceId = localStorage.getItem('device_id') || localStorage.getItem('deviceId') || '';
              let username = localStorage.getItem('username') || '';

              // Trích xuất từ Vue store
              let appRoot = document.querySelector('#app');
              let state = appRoot && appRoot.__vue__ && appRoot.__vue__['\u0024store'] ? appRoot.__vue__['\u0024store'].state : null;
              if (state) {
                signingKey = String(state.signing_key || '');
                userId = String(state.user_id || '');
                if (!deviceId) deviceId = String(state.device_id || state.deviceId || '');
                if (!username) username = String(state.username || state.user_name || '');
              }

              // Quét localStorage tìm signing_key và user_id
              for (let i = 0; i < localStorage.length; i++) {
                let storageKey = localStorage.key(i);
                let raw = localStorage.getItem(storageKey);
                if (!raw) continue;
                if (!signingKey && storageKey === 'signing_key') signingKey = raw;
                if (!userId && storageKey === 'user_id') userId = raw;
              }

              // Quét localStorage tìm auth token lưu sẵn
              let storedToken = localStorage.getItem('token') || localStorage.getItem('authorization') || '';
              if (!storedToken) {
                try {
                  let authObj = JSON.parse(localStorage.getItem('auth') || '{}');
                  if (authObj && authObj.token) storedToken = authObj.token;
                  else if (authObj && authObj.access_token) storedToken = authObj.access_token;
                } catch(e) {}
              }
              if (!storedToken) {
                try {
                  let userObj = JSON.parse(localStorage.getItem('user') || '{}');
                  if (userObj && userObj.token) storedToken = userObj.token;
                } catch(e) {}
              }

              if (window.GoMaxApp && window.GoMaxApp.sendGatewayHeaders && (deviceId || username)) {
                GoMaxApp.sendGatewayHeaders('', deviceId || '', username || '');
              }
              if (window.GoMaxApp && window.GoMaxApp.sendSessionStore) {
                GoMaxApp.sendSessionStore(signingKey || '', userId || '', webData || 'null');
              }
              if (storedToken && storedToken !== 'null' && storedToken !== 'undefined' && storedToken !== 'Bearer null') {
                if (window.GoMaxApp && window.GoMaxApp.sendAuthData) {
                  GoMaxApp.sendAuthData(storedToken, '');
                }
              }
            } catch(e) {}
          }

          window.goMaxCaptureSessionStore = captureSessionStore;

          function captureHeaders(headers) {
            captureSessionStore();
            let auth = readHeader(headers, 'authorization');
            let tHeader = readHeader(headers, 't');
            let gAuth = readHeader(headers, 'g-auth');
            let gDeviceId = readHeader(headers, 'g-device-id');
            let gUsername = readHeader(headers, 'g-username');

            if (gAuth || gDeviceId || gUsername) {
              if (window.GoMaxApp && window.GoMaxApp.sendGatewayHeaders) {
                GoMaxApp.sendGatewayHeaders(gAuth || '', gDeviceId || '', gUsername || '');
              }
            }
            if (auth && auth !== 'null' && auth !== 'undefined' && auth !== 'Bearer null') {
              if (window.GoMaxApp && window.GoMaxApp.sendAuthData) {
                GoMaxApp.sendAuthData(auth, tHeader || '');
              }
            }
          }

          let origFetch = window.fetch;
          window.fetch = function() {
            let args = arguments;
            if (args[1] && args[1].headers) captureHeaders(args[1].headers);
            return origFetch.apply(this, args);
          };

          let origSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
          XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
            if (!this._headers) this._headers = {};
            this._headers[header] = value;
            if (String(header).toLowerCase() === 'authorization' && value && value !== 'Bearer null') {
              if (window.GoMaxApp && window.GoMaxApp.sendAuthData) {
                GoMaxApp.sendAuthData(value, this._headers['t'] || '');
              }
            }
            return origSetRequestHeader.apply(this, arguments);
          };

          // Thực hiện quét ngay và lặp lại định kỳ
          captureSessionStore();
          setInterval(captureSessionStore, 1200);
        })();
    """

    interface AuthCallback {
        fun onAuthCaptured(authToken: String, tToken: String?, deviceId: String?, username: String?, gAuth: String?)
    }

    class JsBridge(private val callback: AuthCallback) {
        private var savedAuth: String? = null
        private var savedT: String? = null
        private var savedDeviceId: String? = null
        private var savedUsername: String? = null
        private var savedGAuth: String? = null

        @JavascriptInterface
        fun sendAuthData(auth: String, t: String) {
            val normalized = if (auth.startsWith("Bearer ", ignoreCase = true)) auth else "Bearer $auth"
            if (normalized.isNotBlank() && normalized != "Bearer null" && normalized != "Bearer undefined") {
                savedAuth = normalized
                if (t.isNotBlank()) savedT = t
                checkAndNotify()
            }
        }

        @JavascriptInterface
        fun sendGatewayHeaders(gAuth: String, deviceId: String, username: String) {
            if (gAuth.isNotBlank()) savedGAuth = gAuth
            if (deviceId.isNotBlank()) savedDeviceId = deviceId
            if (username.isNotBlank()) savedUsername = username
            checkAndNotify()
        }

        @JavascriptInterface
        fun sendSessionStore(signingKey: String, userId: String, webData: String) {
            checkAndNotify()
        }

        private fun checkAndNotify() {
            val auth = savedAuth
            if (!auth.isNullOrBlank() && auth != "Bearer null" && auth != "Bearer undefined") {
                callback.onAuthCaptured(auth, savedT, savedDeviceId, savedUsername, savedGAuth)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(
        webView: WebView,
        callback: AuthCallback,
        onProgressChanged: ((Int) -> Unit)? = null,
        onErrorOccurred: ((String) -> Unit)? = null
    ) {
        // Cho phép nhận Cookie và 3rd-party cookie (Cần thiết cho Cloudflare Turnstile & SPA)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = GolikeApiClient.USER_AGENT
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged?.invoke(newProgress)
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                Log.d("GolikeConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.addJavascriptInterface(JsBridge(callback), "GoMaxApp")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                view?.evaluateJavascript(INJECT_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(INJECT_JS, null)
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("GolikeWebView", "Lỗi tải trang: $description ($errorCode) tại $failingUrl")
                onErrorOccurred?.invoke("$description ($errorCode)")
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.w("GolikeWebView", "SSL Error: ${error?.primaryError}, proceeding...")
                handler?.proceed()
            }
        }

        webView.loadUrl(LOGIN_URL)
    }
}
