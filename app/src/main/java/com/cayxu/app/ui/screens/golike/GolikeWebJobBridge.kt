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
import com.cayxu.app.ui.overlay.xsmm.XsmmJobStatusBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Trình duyệt Web mini nhúng trực tiếp trang thật Golike (https://app.golike.net/jobs/tiktok) vào màn nổi.
 * Tự động hóa 100% bằng cách bấm trực tiếp trên giao diện DOM thật của Golike:
 * 1. Chọn đúng tài khoản kiếm thưởng trong menu dropdown (Ảnh 3).
 * 2. Tự động tick "Đã hiểu" và bấm "Đồng ý" trên popup "Xác nhận làm việc bằng App" (Ảnh 4).
 * 3. Bấm nút "Nhận Job ngay" (Ảnh 2).
 * 4. Chờ tải xong trang chi tiết công việc (Ảnh 5), trích xuất link đích hoặc click nút [ TikTok ].
 * 5. Bấm nút [ Hoàn thành ] trên web để Golike tự động gửi báo cáo nhận tiền.
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
            if (result != null) {
                pendingGetJob?.complete(result)
            }
        }

        @JavascriptInterface
        fun onCompleteResult(jsonStr: String?) {
            Log.d(TAG, "onCompleteResult from Web: $jsonStr")
            val result = try {
                if (!jsonStr.isNullOrBlank()) JSONObject(jsonStr) else null
            } catch (e: Exception) {
                null
            }
            if (result != null) {
                pendingCompleteJob?.complete(result)
            }
        }

        @JavascriptInterface
        fun onCaptchaDetected() {
            Log.w(TAG, "Captcha detected on Golike Web")
            XsmmJobStatusBridge.update("Golike: Vui lòng giải Captcha trên màn nổi...")
        }

        @JavascriptInterface
        fun onWebStatus(msg: String?) {
            if (!msg.isNullOrBlank()) {
                Log.d(TAG, "WebStatus: $msg")
                XsmmJobStatusBridge.update(msg)
            }
        }

        @JavascriptInterface
        fun onLog(msg: String?) {
            Log.d(TAG, "WebLog: $msg")
        }
    }

    /**
     * Đoạn mã Script chặn bắt ngầm các phản hồi mạng thật của chính trang Golike
     */
    private const val NETWORK_INTERCEPTOR_JS = """
        (function() {
            if (window._golikeHooked) return;
            window._golikeHooked = true;
            window._lastJobData = null;
            window._lastCompleteData = null;

            // Hook window.fetch
            const origFetch = window.fetch;
            window.fetch = async function(...args) {
                const response = await origFetch.apply(this, args);
                try {
                    const url = (args[0] && args[0].url) ? args[0].url : String(args[0] || '');
                    if (url.includes('/jobs') || url.includes('/tiktok/jobs')) {
                        const clone = response.clone();
                        clone.json().then(data => {
                            window._lastJobData = data;
                            if (window.GolikeJobBridge && window.GolikeJobBridge.onJobResult) {
                                window.GolikeJobBridge.onJobResult(JSON.stringify(data));
                            }
                        }).catch(() => {});
                    } else if (url.includes('/complete-jobs')) {
                        const clone = response.clone();
                        clone.json().then(data => {
                            window._lastCompleteData = data;
                            if (window.GolikeJobBridge && window.GolikeJobBridge.onCompleteResult) {
                                window.GolikeJobBridge.onCompleteResult(JSON.stringify(data));
                            }
                        }).catch(() => {});
                    }
                } catch(e) {}
                return response;
            };

            // Hook XMLHttpRequest
            const origOpen = XMLHttpRequest.prototype.open;
            const origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                this._reqUrl = url;
                return origOpen.apply(this, [method, url, ...rest]);
            };
            XMLHttpRequest.prototype.send = function(...args) {
                this.addEventListener('load', function() {
                    try {
                        const url = this._reqUrl ? String(this._reqUrl) : '';
                        if (url.includes('/jobs') || url.includes('/tiktok/jobs')) {
                            const data = JSON.parse(this.responseText);
                            window._lastJobData = data;
                            if (window.GolikeJobBridge && window.GolikeJobBridge.onJobResult) {
                                window.GolikeJobBridge.onJobResult(JSON.stringify(data));
                            }
                        } else if (url.includes('/complete-jobs')) {
                            const data = JSON.parse(this.responseText);
                            window._lastCompleteData = data;
                            if (window.GolikeJobBridge && window.GolikeJobBridge.onCompleteResult) {
                                window.GolikeJobBridge.onCompleteResult(JSON.stringify(data));
                            }
                        }
                    } catch(e) {}
                });
                return origSend.apply(this, args);
            };
        })();
    """

    const val AUTO_DISMISS_MODALS_JS = """
        (function() {
            function autoDismissGolikePopups() {
                try {
                    let allClickables = Array.from(document.querySelectorAll('button, a, div[role="button"], span, .btn, .van-button, .swal2-confirm'));

                    // 1. Quét tìm và click nút "Đã hiểu" / "Tôi đã hiểu"
                    for (let b of allClickables) {
                        let t = (b.innerText || b.textContent || '').trim().toLowerCase();
                        if ((t === 'đã hiểu' || t === 'tôi đã hiểu' || t.includes('đã hiểu') || t.includes('da hieu')) && b.offsetParent !== null) {
                            ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(evt => {
                                b.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window }));
                            });
                            b.click();
                            if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                window.GolikeJobBridge.onWebStatus('Golike: Đã bấm "Đã hiểu"');
                            }
                            return true;
                        }
                    }

                    // 2. Tự động tick checkbox xác nhận nếu có
                    let checkboxes = document.querySelectorAll('input[type="checkbox"]');
                    for (let cb of checkboxes) {
                        if (!cb.checked && cb.offsetParent !== null) {
                            cb.click();
                        }
                    }

                    // 3. Quét tìm và click nút "Đồng ý" (trong modal "Xác nhận làm việc bằng App")
                    for (let b of allClickables) {
                        let t = (b.innerText || b.textContent || '').trim().toLowerCase();
                        if ((t === 'đồng ý' || t === 'dong y' || t === 'chấp nhận') && b.offsetParent !== null) {
                            ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(evt => {
                                b.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window }));
                            });
                            b.click();
                            if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                window.GolikeJobBridge.onWebStatus('Golike: Đã bấm "Đồng ý" làm việc');
                            }
                            return true;
                        }
                    }

                    // 4. Modal #JobPolicyWarning nếu có
                    let policyModal = document.getElementById('JobPolicyWarning');
                    if (policyModal && (policyModal.classList.contains('show') || policyModal.style.display === 'block' || policyModal.offsetParent !== null)) {
                        let cb = document.getElementById('JobPolicyWarningAgree');
                        if (cb && !cb.checked) cb.click();
                        let btnOk = policyModal.querySelector('.btn-primary') || policyModal.querySelector('button');
                        if (btnOk && !btnOk.disabled) {
                            btnOk.click();
                            return true;
                        }
                    }

                    // 5. Tự động đóng popup cảnh báo CHPLAY / dừng hoạt động nếu có
                    let popups = document.querySelectorAll('.swal2-container, .modal, .ant-modal, .van-dialog, [role="dialog"], .modal-dialog');
                    for (let p of popups) {
                        let txt = (p.innerText || '').toLowerCase();
                        if (txt.includes('chplay') || txt.includes('dừng hoạt động') || txt.includes('bản web')) {
                            let closeBtn = p.querySelector('button, .btn, .swal2-confirm, .van-button, .close');
                            if (closeBtn && !closeBtn.disabled) {
                                closeBtn.click();
                            }
                        }
                    }

                    // 6. Đóng SweetAlert2 "OK"
                    let swalOk = document.querySelector('button.swal2-confirm');
                    if (swalOk && swalOk.offsetParent !== null && !swalOk.disabled) {
                        swalOk.click();
                    }
                } catch(e) {}
                return false;
            }

            window.autoDismissGolikePopups = autoDismissGolikePopups;
            autoDismissGolikePopups();
            if (!window._golikeAutoDismissInterval) {
                window._golikeAutoDismissInterval = setInterval(autoDismissGolikePopups, 350);
            }
        })();
    """

    /**
     * Tạo đoạn mã Javascript giả lập 100% môi trường iOS Safari (Apple WebKit)
     * và tự động đồng bộ trọn bộ LocalStorage từ phiên đăng nhập Golike đã lưu.
     */
    private fun getSpoofAndSessionScript(context: Context): String {
        val savedToken = GolikeAccountsStore.getToken(context).orEmpty()
        val authHeader = if (savedToken.startsWith("Bearer ", ignoreCase = true)) savedToken else "Bearer $savedToken"
        val savedUserId = GolikeAccountsStore.getUserId(context)
        val savedSigningKey = GolikeAccountsStore.getSigningKey(context)
        val savedDeviceId = GolikeAccountsStore.getDeviceId(context)
        val savedUsername = GolikeAccountsStore.getUsername(context)
        val savedWebData = GolikeAccountsStore.getWebData(context)
        val savedTToken = GolikeAccountsStore.getTToken(context)
        val savedVersion = GolikeAccountsStore.getWebVersionText(context)

        return """
            (function() {
                try {
                    // 1. Giả lập 100% môi trường iOS Safari (Apple WebKit)
                    Object.defineProperty(navigator, 'platform', { get: function() { return 'iPhone'; }, configurable: true });
                    Object.defineProperty(navigator, 'vendor', { get: function() { return 'Apple Computer, Inc.'; }, configurable: true });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 5; }, configurable: true });
                    if ('userAgentData' in navigator) {
                        Object.defineProperty(navigator, 'userAgentData', { get: function() { return undefined; }, configurable: true });
                    }
                    try { delete window.chrome; } catch(e) {}
                } catch(e) {}

                // 2. Đồng bộ LocalStorage từ tài khoản Golike đã đăng nhập
                try {
                    var token = ${JSONObject.quote(savedToken)};
                    var authHeader = ${JSONObject.quote(authHeader)};
                    var userId = ${JSONObject.quote(savedUserId)};
                    var signingKey = ${JSONObject.quote(savedSigningKey)};
                    var deviceId = ${JSONObject.quote(savedDeviceId)};
                    var username = ${JSONObject.quote(savedUsername)};
                    var webData = ${JSONObject.quote(savedWebData)};
                    var tToken = ${JSONObject.quote(savedTToken)};
                    var version = ${JSONObject.quote(savedVersion)};

                    if (token && !localStorage.getItem('token')) {
                        localStorage.setItem('token', token);
                    }
                    if (token && !localStorage.getItem('authorization')) {
                        localStorage.setItem('authorization', authHeader);
                    }
                    if (userId && !localStorage.getItem('user_id')) {
                        localStorage.setItem('user_id', userId);
                    }
                    if (signingKey && !localStorage.getItem('signing_key')) {
                        localStorage.setItem('signing_key', signingKey);
                    }
                    if (deviceId && !localStorage.getItem('device_id')) {
                        localStorage.setItem('device_id', deviceId);
                    }
                    if (username && !localStorage.getItem('username')) {
                        localStorage.setItem('username', username);
                    }
                    if (webData && webData !== 'null' && !localStorage.getItem('__')) {
                        localStorage.setItem('__', webData);
                    }
                    if (tToken && !localStorage.getItem('t')) {
                        localStorage.setItem('t', tToken);
                    }
                    if (version && !localStorage.getItem('g-version')) {
                        localStorage.setItem('g-version', version);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
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
        val targetUserAgent = GolikeAccountsStore.getUserAgent(context)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = targetUserAgent
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)

        // Đồng bộ cookies từ phiên đăng nhập Golike vào CookieManager
        val savedCookies = GolikeAccountsStore.getWebCookies(context)
        if (savedCookies.isNotBlank()) {
            listOf("https://app.golike.net", "https://gateway.golike.net", "https://api.golike.net").forEach { domain ->
                savedCookies.split(";").forEach { c ->
                    val cookie = c.trim()
                    if (cookie.isNotBlank()) {
                        cookieManager.setCookie(domain, cookie)
                    }
                }
            }
            cookieManager.flush()
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

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                // Tiêm script giả lập iOS Safari và nạp LocalStorage ngay khi trang bắt đầu render
                view?.evaluateJavascript(getSpoofAndSessionScript(context), null)
                view?.evaluateJavascript(NETWORK_INTERCEPTOR_JS, null)
                view?.evaluateJavascript(AUTO_DISMISS_MODALS_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Web Golike loaded: $url")
                // Nạp lại đảm bảo phiên và hook mạng luôn hoạt động
                view?.evaluateJavascript(getSpoofAndSessionScript(context), null)
                view?.evaluateJavascript(NETWORK_INTERCEPTOR_JS, null)
                view?.evaluateJavascript(AUTO_DISMISS_MODALS_JS, null)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                // Nếu là yêu cầu lấy nhiệm vụ TikTok từ gateway Golike
                // Can thiệp để loại bỏ hoàn toàn các header Client Hints Android (sec-ch-ua-platform)
                // Đảm bảo Golike Backend nhận diện 100% chuẩn iOS Safari và không bao giờ hiện cảnh báo CHPLAY!
                if (request.method.equals("GET", ignoreCase = true) &&
                    reqUrl.contains("gateway.golike.net/api/advertising/publishers/tiktok/jobs")
                ) {
                    try {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build()

                        val userAgent = GolikeAccountsStore.getUserAgent(context)
                        val reqBuilder = Request.Builder()
                            .url(reqUrl)
                            .get()
                            .header("User-Agent", userAgent)
                            .header("Origin", "https://app.golike.net")
                            .header("Referer", "https://app.golike.net/")
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")

                        // Copy các headers cần thiết (Authorization, t, cookie) nhưng BỎ QUA sec-ch-*
                        request.requestHeaders?.forEach { (k, v) ->
                            val lower = k.lowercase()
                            if (!lower.startsWith("sec-ch-") &&
                                !lower.equals("user-agent", ignoreCase = true) &&
                                !lower.equals("host", ignoreCase = true)
                            ) {
                                reqBuilder.header(k, v)
                            }
                        }

                        // Đảm bảo cookie được gắn đầy đủ
                        val cookies = CookieManager.getInstance().getCookie(reqUrl)
                        if (!cookies.isNullOrBlank()) {
                            reqBuilder.header("Cookie", cookies)
                        }

                        // Đảm bảo Authorization luôn có
                        val token = GolikeAccountsStore.getToken(context).orEmpty()
                        if (token.isNotBlank() && reqBuilder.build().header("Authorization").isNullOrBlank()) {
                            val auth = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                            reqBuilder.header("Authorization", auth)
                        }

                        val response = client.newCall(reqBuilder.build()).execute()
                        val responseBody = response.body?.bytes() ?: ByteArray(0)
                        val contentType = response.header("Content-Type") ?: "application/json; charset=utf-8"
                        val mimeType = contentType.substringBefore(";").trim()
                        val encoding = if (contentType.contains("charset=", ignoreCase = true)) {
                            contentType.substringAfter("charset=").substringBefore(";").trim()
                        } else "utf-8"

                        // Trả về luồng dữ liệu sạch cho WebView
                        val responseHeaders = mutableMapOf<String, String>()
                        response.headers.forEach { pair ->
                            responseHeaders[pair.first] = pair.second
                        }

                        // Nếu API trả về dữ liệu job -> cũng chủ động phân tích và hoàn thành pendingGetJob
                        val jsonString = String(responseBody, Charsets.UTF_8)
                        try {
                            val json = JSONObject(jsonString)
                            if (json.optBoolean("success", false) || json.optInt("status", 0) == 200) {
                                pendingGetJob?.complete(json)
                            }
                        } catch (_: Exception) {}

                        return WebResourceResponse(
                            mimeType,
                            encoding,
                            response.code,
                            response.message.ifBlank { "OK" },
                            responseHeaders,
                            ByteArrayInputStream(responseBody)
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Lỗi can thiệp getJob request: ${e.message}")
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        wv.addJavascriptInterface(WebActionInterface(), "GolikeJobBridge")

        // Chỉ tải trang gốc https://app.golike.net/jobs 1 lần duy nhất để Vue.js mount toàn bộ app
        // Mọi điều hướng tiếp theo (vào TikTok, nhận job, hoàn thành) 100% bằng click phần tử DOM
        wv.loadUrl("https://app.golike.net/jobs")
        miniWebView = wv
        return wv
    }

    /**
     * Nhận nhiệm vụ TikTok trực tiếp thông qua thao tác DOM thật chuẩn xác 100% theo HTML của Golike:
     * 1. Trang /jobs: Click .job-card có .job-name là "Tiktok" (Vue Router tự chuyển mượt, không trắng trang)
     * 2. Trang /jobs/tiktok: Xử lý modal #JobPolicyWarning, chọn nick trong .tk-account-select, bấm .tk-hero__cta
     * 3. Trang /jobs/tiktok/job-detail: Bắt a.btn[href*="tiktok.com"] và trích xuất Job Id
     */
    suspend fun getJobViaWeb(accountId: String, accountUsername: String = "", webData: String? = null): JSONObject? {
        val wv = miniWebView ?: return null
        val deferred = CompletableDeferred<JSONObject?>()
        pendingGetJob = deferred
        // Xóa sạch bộ nhớ đệm trước khi bắt đầu lấy job mới
        mainHandler.post {
            try {
                wv.evaluateJavascript("window._lastJobData = null; window._lastCompleteData = null;", null)
            } catch (_: Exception) {}
        }

        val cleanTargetUser = accountUsername.trim().removePrefix("@").lowercase()

        // Cỗ máy trạng thái tuần hoàn (Active State Machine) quét và thao tác mỗi 1.2s trong tối đa 30s
        var elapsed = 0L
        while (elapsed < 30000L && !deferred.isCompleted) {
            mainHandler.post {
                try {
                    val currentUrl = wv.url.orEmpty()
                    // Chỉ tải trang gốc /jobs khi WebView hoàn toàn rỗng lúc vừa mở app
                    if (currentUrl.isBlank() || !currentUrl.contains("golike.net")) {
                        wv.loadUrl("https://app.golike.net/jobs")
                    }

                    wv.evaluateJavascript(getSpoofAndSessionScript(wv.context), null)
                    wv.evaluateJavascript(
                        """
                        (function() {
                            try {
                                $NETWORK_INTERCEPTOR_JS
                                $AUTO_DISMISS_MODALS_JS

                                // Nếu bị trắng trang do router, tự back về trang trước trong lịch sử SPA
                                let appEl = document.getElementById('app');
                                if (appEl && appEl.children.length === 0) {
                                    window.history.back();
                                    return 'navigated_back_blank';
                                }

                                let pathname = window.location.pathname || '';
                                let bodyText = document.body ? document.body.innerText : '';

                                // 1. Kiểm tra Captcha (ID #captcha-golike hoặc reCAPTCHA iframe)
                                let captchaModal = document.getElementById('captcha-golike');
                                let recaptchaFrame = document.querySelector('iframe[src*="recaptcha"], iframe[src*="cloudflare"], div.g-recaptcha');
                                let isCaptchaVisible = (captchaModal && (captchaModal.classList.contains('show') || captchaModal.style.display === 'block')) ||
                                    (recaptchaFrame && recaptchaFrame.offsetParent !== null && recaptchaFrame.style.display !== 'none');
                                if (isCaptchaVisible) {
                                    if (window.GolikeJobBridge) {
                                        if (window.GolikeJobBridge.onCaptchaDetected) window.GolikeJobBridge.onCaptchaDetected();
                                        if (window.GolikeJobBridge.onWebStatus) window.GolikeJobBridge.onWebStatus('Golike: Vui lòng giải Captcha trên màn nổi...');
                                    }
                                    return 'waiting_captcha';
                                }

                                // 2. Tự động xử lý tất cả modal "Đã hiểu", "Đồng ý"
                                if (typeof autoDismissGolikePopups === 'function') {
                                    autoDismissGolikePopups();
                                }

                                // BƯỚC 0: Nếu mới mở đang ở Trang chủ (Home) -> Bấm nút "Kiếm xu" ở thanh menu dưới
                                if (pathname === '/home' || pathname.endsWith('/home') || (!document.querySelector('.job-grid') && !document.querySelector('.tk-hero') && !document.querySelector('.card-job-detail'))) {
                                    let walletTab = Array.from(document.querySelectorAll('.nav-bottom-container .col, .nav-bottom-container div, .nav-bottom-container span')).find(el => (el.innerText || '').trim() === 'Kiếm xu');
                                    if (walletTab) {
                                        let clickTarget = walletTab.closest('.col') || walletTab;
                                        clickTarget.scrollIntoView({ behavior: 'auto', block: 'center' });
                                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(evt => {
                                            clickTarget.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window }));
                                        });
                                        clickTarget.click();
                                        if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                            window.GolikeJobBridge.onWebStatus('Golike: Bấm Kiếm xu từ trang chủ...');
                                        }
                                        return 'navigated_to_jobs';
                                    }
                                }

                                // BƯỚC 1: TRANG /jobs (Chọn kênh kiếm xu -> Bấm chọn TikTok)
                                if (document.querySelector('.job-grid') || pathname === '/jobs' || pathname.endsWith('/jobs')) {
                                    let ttCard = Array.from(document.querySelectorAll('.job-card')).find(c => {
                                        let name = c.querySelector('.job-name')?.innerText?.trim()?.toLowerCase() || '';
                                        return name === 'tiktok';
                                    });
                                    if (ttCard) {
                                        let clickTarget = ttCard.querySelector('.job-card-inner') || ttCard;
                                        clickTarget.scrollIntoView({ behavior: 'auto', block: 'center' });
                                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(evt => {
                                            clickTarget.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window }));
                                        });
                                        clickTarget.click();
                                        if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                            window.GolikeJobBridge.onWebStatus('Golike: Đang vào kênh TikTok...');
                                        }
                                        return 'navigated_to_tiktok';
                                    }
                                }

                                // BƯỚC 2 & TRƯỜNG HỢP 2: TRANG /jobs/tiktok (Ở sẵn trang này các lần tiếp theo)
                                let heroCta = document.querySelector('.tk-hero__cta') || document.querySelector('.tk-hero');
                                if (heroCta || document.querySelector('.tk-tabs') || pathname.includes('/jobs/tiktok')) {
                                    if (!pathname.includes('job-detail')) {
                                        // A. Tự động cuộn trang xuống nút "Nhận Job ngay" để hiện rõ ngay giữa màn hình
                                        if (heroCta) {
                                            heroCta.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                        }

                                        // B. Kiểm tra và chọn đúng tài khoản kiếm thưởng
                                        let targetUser = '$cleanTargetUser';
                                        if (targetUser) {
                                            let curUser = document.querySelector('.tk-account-select__name')?.innerText?.trim()?.toLowerCase() || '';
                                            if (curUser && !curUser.includes(targetUser)) {
                                                let accSelect = document.querySelector('.tk-account-select');
                                                if (accSelect) {
                                                    accSelect.click();
                                                    setTimeout(function() {
                                                        let items = Array.from(document.querySelectorAll('.tk-account-item')).filter(it => {
                                                            let n = it.querySelector('.tk-account-item__name')?.innerText?.trim()?.toLowerCase() || '';
                                                            return n.includes(targetUser);
                                                        });
                                                        if (items.length > 0) {
                                                            items[0].scrollIntoView({ behavior: 'auto', block: 'center' });
                                                            items[0].click();
                                                            if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                                                window.GolikeJobBridge.onWebStatus('Golike: Đã chọn tài khoản @' + targetUser);
                                                            }
                                                        }
                                                    }, 350);
                                                }
                                            }
                                        }

                                        // C. Bấm nút Nhận Job ngay (.tk-hero__cta)
                                        if (heroCta) {
                                            let now = Date.now();
                                            if (!window._lastClickGetJob || (now - window._lastClickGetJob > 3500)) {
                                                window._lastClickGetJob = now;
                                                ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(evt => {
                                                    heroCta.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window }));
                                                });
                                                heroCta.click();
                                                if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                                    window.GolikeJobBridge.onWebStatus('Golike: Đã bấm Nhận Job ngay (Đang tải...)');
                                                }
                                                return 'clicked_get_job';
                                            }
                                        }
                                    }
                                }

                                // 5. TRANG 3: /jobs/tiktok/job-detail (Chi tiết nhiệm vụ)
                                let tiktokLink = document.querySelector('a.btn[href*="tiktok.com"]') || document.querySelector('a[href*="tiktok.com"]');
                                if (tiktokLink || document.querySelector('.card-job-detail') || pathname.includes('job-detail')) {
                                    let link = tiktokLink ? (tiktokLink.getAttribute('href') || '') : '';
                                    let isFollow = bodyText.includes('THEO DÕI') || bodyText.includes('Follow') || bodyText.includes('TĂNG LƯỢT THEO DÕI');
                                    let jobType = isFollow ? 'follow' : 'like';

                                    let idMatch = bodyText.match(/Job Id:\s*([A-Za-z0-9_-]+)/i);
                                    let adsId = idMatch ? idMatch[1] : ('job_' + Date.now());

                                    // Nếu network hook đã bắt được dữ liệu chính xác từ API
                                    if (window._lastJobData && window._lastJobData.data) {
                                        let jd = window._lastJobData.data;
                                        window._lastJobData = null; // Xóa cache ngay, tránh lặp lại job cũ
                                        let res = {
                                            success: true,
                                            status: 200,
                                            data: {
                                                id: jd.id || jd.ads_id || adsId,
                                                ads_id: jd.ads_id || jd.id || adsId,
                                                type: jd.type || jobType,
                                                link: jd.link || link,
                                                object_id: jd.object_id || jd.ads_id || adsId
                                            }
                                        };
                                        if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                            window.GolikeJobBridge.onWebStatus('Golike: Nhận job ' + adsId + ' thành công! Đang mở TikTok...');
                                        }
                                        window.GolikeJobBridge.onJobResult(JSON.stringify(res));
                                        return 'job_extracted_network';
                                    }

                                    // Nếu trích xuất được từ DOM
                                    if (link || adsId) {
                                        let res = {
                                            success: true,
                                            status: 200,
                                            data: {
                                                id: adsId,
                                                ads_id: adsId,
                                                type: jobType,
                                                link: link,
                                                object_id: adsId
                                            }
                                        };
                                        if (window.GolikeJobBridge && window.GolikeJobBridge.onWebStatus) {
                                            window.GolikeJobBridge.onWebStatus('Golike: Nhận job ' + adsId + ' thành công! Đang mở TikTok...');
                                        }
                                        window.GolikeJobBridge.onJobResult(JSON.stringify(res));
                                        return 'job_extracted_dom';
                                    }
                                }

                                return 'idle';
                            } catch(e) {
                                return 'err: ' + e;
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error in getJobViaWeb loop: ${e.message}")
                }
            }

            delay(1200L)
            elapsed += 1200L
        }

        return withTimeoutOrNull(5000L) { deferred.await() }
    }

    /**
     * Bấm nút [ TikTok ] trên trang Chi tiết job (Ảnh 5)
     */
    fun clickTikTokButtonOnWeb() {
        mainHandler.post {
            try {
                miniWebView?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            let ttBtn = document.querySelector('a.btn[href*="tiktok.com"]') || document.querySelector('a[href*="tiktok.com"]');
                            if (!ttBtn) {
                                let allLinks = Array.from(document.querySelectorAll('a, button, div[role="button"]'));
                                ttBtn = allLinks.find(b => {
                                    let t = (b.innerText || '').trim().toLowerCase();
                                    let h = b.getAttribute('href') || '';
                                    return (t.includes('tiktok') || h.includes('tiktok.com')) && b.offsetParent !== null;
                                });
                            }
                            if (ttBtn) {
                                ttBtn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(evt => {
                                    ttBtn.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window }));
                                });
                                ttBtn.click();
                                return 'clicked_tiktok';
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
     * Hoàn thành nhiệm vụ TikTok bằng cách bấm nút [ Hoàn thành ] màu vàng trên web (Ảnh 5)
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

        val domScript = """
            (function() {
                try {
                    $NETWORK_INTERCEPTOR_JS

                    let compBtn = Array.from(document.querySelectorAll('button, a, div[role="button"], .btn')).find(b => {
                        let t = (b.innerText || '').trim().toLowerCase();
                        return (t.includes('hoàn thành') || t.includes('hoan thanh')) && b.offsetParent !== null;
                    });
                    if (compBtn) {
                        compBtn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(evt => {
                            compBtn.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window }));
                        });
                        compBtn.click();
                        if (window.GolikeJobBridge && window.GolikeJobBridge.onLog) window.GolikeJobBridge.onLog('Clicked complete button');
                        return 'clicked_complete';
                    }
                    return 'not_found';
                } catch(e) {
                    return 'err: ' + e;
                }
            })();
        """.trimIndent()

        mainHandler.post {
            try {
                wv.evaluateJavascript(domScript, null)
            } catch (e: Exception) {
                deferred.complete(null)
            }
        }

        // Chờ kết quả phản hồi từ Web
        val res = withTimeoutOrNull(20000L) { deferred.await() }
        return res
    }

    /**
     * Tự động bấm nút "Báo lỗi" (màu đỏ) trên trang Golike nếu job không thực hiện được (Ảnh 5)
     */
    fun clickReportJobOnWeb() {
        mainHandler.post {
            try {
                miniWebView?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            let allBtns = Array.from(document.querySelectorAll('button, a, div[role="button"], span, div'));
                            let reportBtn = allBtns.find(b => {
                                let t = (b.innerText || '').trim().toLowerCase();
                                return (t.includes('báo lỗi') || t.includes('bao loi')) && b.offsetParent !== null;
                            });
                            if (reportBtn) {
                                reportBtn.click();
                                setTimeout(function() {
                                    let sendBtn = Array.from(document.querySelectorAll('button')).find(b => (b.innerText || '').includes('Gửi báo cáo'));
                                    if (sendBtn) sendBtn.click();
                                    setTimeout(function() {
                                        let swalOk = document.querySelector('button.swal2-confirm');
                                        if (swalOk) swalOk.click();
                                    }, 500);
                                }, 400);
                            }
                        } catch(e) {}
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
        mainHandler.post {
            try {
                miniWebView?.reload()
            } catch (_: Exception) {}
        }
    }

    /**
     * Dọn dẹp WebView khi đóng màn nổi
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
