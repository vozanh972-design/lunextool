package com.cayxu.app.ui.screens.golike

import android.annotation.SuppressLint
import android.content.Context
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

          function getHeaderValue(headers, keyName) {
            if (!headers || !keyName) return '';
            let target = keyName.toLowerCase();
            try {
              if (typeof headers.get === 'function') {
                return headers.get(target) || headers.get(keyName) || '';
              }
            } catch(e) {}
            try {
              if (Array.isArray(headers)) {
                for (let i = 0; i < headers.length; i++) {
                  let h = headers[i] || [];
                  if (String(h[0]).toLowerCase() === target) return String(h[1] || '');
                }
              }
            } catch(e) {}
            try {
              if (typeof headers === 'object') {
                for (let k in headers) {
                  if (Object.prototype.hasOwnProperty.call(headers, k)) {
                    if (String(k).toLowerCase() === target) return String(headers[k] || '');
                  }
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

              // Trích xuất webVersion và webVersionText
              let webVersion = '3.0';
              if (state && (state.version || state.app_version)) {
                webVersion = String(state.version || state.app_version);
              }
              if (!webVersion || webVersion === 'null' || webVersion === 'undefined') {
                let localVer = localStorage.getItem('version') || localStorage.getItem('app_version');
                if (localVer) webVersion = localVer;
              }
              if (!webVersion || webVersion === 'null' || webVersion === 'undefined') webVersion = '3.0';

              let webVersionText = '';
              try {
                let match = document.body && document.body.innerText ? document.body.innerText.match(/(\d+\.\d+\.\d+\.\d+)/) : null;
                if (match) webVersionText = match[1];
              } catch(e) {}
              if (!webVersionText && (webVersion && webVersion !== '3.0')) {
                webVersionText = webVersion;
              }
              if (!webVersionText) webVersionText = '26.09.17.1';

              let version = webVersionText;

              // Quét localStorage tìm signing_key và user_id
              for (let i = 0; i < localStorage.length; i++) {
                let storageKey = localStorage.key(i);
                let raw = localStorage.getItem(storageKey);
                if (!raw) continue;
                if (!signingKey && storageKey === 'signing_key') signingKey = raw;
                if (!userId && storageKey === 'user_id') userId = raw;
                if (!version && (storageKey === 'version' || storageKey === 'app_version')) version = raw;
              }

              if (window.GoMaxApp && window.GoMaxApp.sendSessionStore) {
                GoMaxApp.sendSessionStore(signingKey || '', userId || '', webData || 'null');
              }
              if (window.GoMaxApp && window.GoMaxApp.sendGatewayHeaders && (deviceId || username)) {
                GoMaxApp.sendGatewayHeaders('', deviceId || '', username || '');
              }
              if (window.GoMaxApp && window.GoMaxApp.sendWebVersionInfo) {
                GoMaxApp.sendWebVersionInfo(webVersion || '3.0', webVersionText || '26.09.17.1');
              }
              if (window.GoMaxApp && window.GoMaxApp.sendVersionInfo && version) {
                GoMaxApp.sendVersionInfo(version, 'web', 'https');
              }

              // Kiểm tra thêm token trong localStorage nếu có
              let localAuth = localStorage.getItem('token') || localStorage.getItem('authorization') || localStorage.getItem('access_token');
              if (localAuth && window.GoMaxApp && window.GoMaxApp.sendAuthData) {
                let cleanAuth = localAuth.startsWith('Bearer ') ? localAuth : ('Bearer ' + localAuth);
                let tVal = localStorage.getItem('t') || '';
                window.GoMaxApp.sendAuthData(cleanAuth, tVal);
              }
            } catch(e) {}
          }

          window.goMaxCaptureSessionStore = captureSessionStore;

          function captureAllHeaders(headers) {
            captureSessionStore();
            let auth = getHeaderValue(headers, 'authorization');
            let tHeader = getHeaderValue(headers, 't');
            let gAuth = getHeaderValue(headers, 'g-auth');
            let gDeviceId = getHeaderValue(headers, 'g-device-id');
            let gUsername = getHeaderValue(headers, 'g-username');
            let gVersion = getHeaderValue(headers, 'g-version');
            let gClient = getHeaderValue(headers, 'g-client');
            let gScheme = getHeaderValue(headers, 'g-scheme');

            if (gAuth || gDeviceId || gUsername) {
              if (window.GoMaxApp && window.GoMaxApp.sendGatewayHeaders) {
                GoMaxApp.sendGatewayHeaders(gAuth || '', gDeviceId || '', gUsername || '');
              }
            }
            if (gVersion) {
              if (window.GoMaxApp && window.GoMaxApp.sendWebVersionInfo) {
                GoMaxApp.sendWebVersionInfo('3.0', gVersion);
              }
            }
            if (gVersion || gClient || gScheme) {
              if (window.GoMaxApp && window.GoMaxApp.sendVersionInfo) {
                GoMaxApp.sendVersionInfo(gVersion || '', gClient || '', gScheme || '');
              }
            }
            if (auth && auth !== 'null' && auth !== 'undefined' && auth !== 'Bearer null') {
              if (window.GoMaxApp && window.GoMaxApp.sendAuthData) {
                GoMaxApp.sendAuthData(auth, tHeader || '');
              }
            }
          }

          // Hook window.fetch
          let origFetch = window.fetch;
          window.fetch = function() {
            let args = arguments;
            if (args[1] && args[1].headers) {
              captureAllHeaders(args[1].headers);
            }
            return origFetch.apply(this, args);
          };

          // Hook XMLHttpRequest
          let origSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
          XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
            if (!this._headers) this._headers = {};
            this._headers[header] = value;
            return origSetRequestHeader.apply(this, arguments);
          };

          let origSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function() {
            if (this._headers) {
              captureAllHeaders(this._headers);
            }
            return origSend.apply(this, arguments);
          };

          // Chủ động trigger gọi API /api/users/me để bộ hook fetch/xhr tóm đủ headers
          try {
            if (window.fetch && (location.href.indexOf('app.golike.net') !== -1 || location.pathname !== '/login')) {
              window.fetch('/api/users/me').catch(function(){});
            }
          } catch(e) {}

          // Thực hiện quét session store định kỳ
          captureSessionStore();
          setInterval(captureSessionStore, 1000);
        })();
    """

    interface AuthCallback {
        fun onAuthCaptured(
            authToken: String,
            tToken: String,
            deviceId: String,
            username: String,
            gAuth: String,
            signingKey: String,
            userId: String,
            webData: String,
            version: String = "26.09.17.1",
            client: String = "web",
            scheme: String = "https"
        )
    }

    class JsBridge(
        private val context: Context?,
        private val callback: AuthCallback
    ) {
        constructor(callback: AuthCallback) : this(null, callback)

        private var savedAuth: String? = null
        private var savedT: String? = null
        private var savedDeviceId: String? = null
        private var savedUsername: String? = null
        private var savedGAuth: String? = null
        private var savedSigningKey: String? = null
        private var savedUserId: String? = null
        private var savedWebData: String? = null
        private var savedVersion: String = "26.09.17.1"
        private var savedClient: String = "web"
        private var savedScheme: String = "https"
        private var savedWebVersion: String = "3.0"
        private var savedWebVersionText: String = "26.09.17.1"

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
        fun sendVersionInfo(version: String, client: String, scheme: String) {
            if (version.isNotBlank()) {
                savedVersion = version
                savedWebVersionText = version
                context?.let { ctx ->
                    try {
                        GolikeAccountsStore.saveWebVersionInfo(ctx, savedWebVersion, version)
                        GolikeAccountsStore.saveVersionData(ctx, version = version, client = client, scheme = scheme)
                    } catch (e: Exception) {
                        Log.e("GolikeJsBridge", "Lỗi lưu version info: ${e.message}")
                    }
                }
            }
            if (client.isNotBlank()) savedClient = client
            if (scheme.isNotBlank()) savedScheme = scheme
            checkAndNotify()
        }

        @JavascriptInterface
        fun sendWebVersionInfo(webVersion: String, webVersionText: String) {
            val finalWebVer = webVersion.takeIf { it.isNotBlank() && it != "null" && it != "undefined" } ?: "3.0"
            val finalWebVerText = webVersionText.takeIf { it.isNotBlank() && it != "null" && it != "undefined" } ?: "26.09.17.1"
            savedWebVersion = finalWebVer
            savedWebVersionText = finalWebVerText
            savedVersion = finalWebVerText
            context?.let { ctx ->
                try {
                    GolikeAccountsStore.saveWebVersionInfo(ctx, finalWebVer, finalWebVerText)
                    GolikeAccountsStore.saveVersionData(ctx, version = finalWebVerText)
                } catch (e: Exception) {
                    Log.e("GolikeJsBridge", "Lỗi lưu webVersionInfo: ${e.message}")
                }
            }
            checkAndNotify()
        }

        @JavascriptInterface
        fun sendSessionStore(signingKey: String, userId: String, webData: String) {
            if (signingKey.isNotBlank()) savedSigningKey = signingKey
            if (userId.isNotBlank()) savedUserId = userId
            if (webData.isNotBlank() && webData != "null") savedWebData = webData
            checkAndNotify()
        }

        private fun checkAndNotify() {
            val auth = savedAuth
            val t = savedT.orEmpty()
            val devId = savedDeviceId.orEmpty()
            val uname = savedUsername.orEmpty()
            val gAuth = savedGAuth.orEmpty()

            // Điều kiện thành công chuẩn GoMax: Có auth (Bearer Token) và (username hoặc deviceId)
            if (!auth.isNullOrBlank() && auth != "Bearer null" && auth != "Bearer undefined" &&
                (uname.isNotBlank() || devId.isNotBlank())
            ) {
                // Đảm bảo luôn lưu webVersion và webVersionText vào Store khi login thành công
                context?.let { ctx ->
                    try {
                        GolikeAccountsStore.saveWebVersionInfo(ctx, savedWebVersion, savedWebVersionText)
                    } catch (_: Exception) {}
                }

                val effectiveVersion = savedWebVersionText.ifBlank { savedVersion }.ifBlank { "26.09.17.1" }
                callback.onAuthCaptured(
                    authToken = auth,
                    tToken = t,
                    deviceId = devId,
                    username = uname,
                    gAuth = gAuth,
                    signingKey = savedSigningKey.orEmpty(),
                    userId = savedUserId.orEmpty(),
                    webData = savedWebData.orEmpty(),
                    version = effectiveVersion,
                    client = savedClient.ifBlank { "web" },
                    scheme = savedScheme.ifBlank { "https" }
                )
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

        webView.addJavascriptInterface(JsBridge(webView.context, callback), "GoMaxApp")
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
