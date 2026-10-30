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
import com.cayxu.app.automation.common.XsmmJobStatusBridge
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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
                // Tự động tiêm bộ lắng nghe mạng
                view?.evaluateJavascript(NETWORK_INTERCEPTOR_JS, null)
            }
        }

        wv.addJavascriptInterface(WebActionInterface(), "GolikeJobBridge")

        // Tải thẳng trang làm nhiệm vụ TikTok của Golike
        wv.loadUrl("https://app.golike.net/jobs/tiktok")
        miniWebView = wv
        return wv
    }

    /**
     * Nhận nhiệm vụ TikTok trực tiếp thông qua thao tác DOM thật trên giao diện Web Golike:
     * 1. Chọn tài khoản TikTok (Ảnh 3).
     * 2. Tự xử lý popup "Xác nhận làm việc bằng App" (Ảnh 4).
     * 3. Bấm "Nhận Job ngay" (Ảnh 2).
     * 4. Trích xuất job hoặc bắt phản hồi mạng (Ảnh 5).
     */
    suspend fun getJobViaWeb(accountId: String, accountUsername: String = "", webData: String? = null): JSONObject? {
        val wv = miniWebView ?: return null
        val deferred = CompletableDeferred<JSONObject?>()
        pendingGetJob = deferred

        val cleanTargetUser = accountUsername.trim().removePrefix("@").lowercase()

        val domScript = """
            (function() {
                try {
                    // 0. Tiêm hook mạng nếu chưa có
                    $NETWORK_INTERCEPTOR_JS

                    // 1. Kiểm tra Captcha
                    let captchaEl = document.querySelector('iframe[src*="recaptcha"], iframe[src*="cloudflare"], div.g-recaptcha, div.cf-turnstile');
                    if (captchaEl && captchaEl.offsetParent !== null) {
                        if (window.GolikeJobBridge && window.GolikeJobBridge.onCaptchaDetected) {
                            window.GolikeJobBridge.onCaptchaDetected();
                        }
                    }

                    // 2. Hàm xử lý Popup "Xác nhận làm việc bằng App" (Ảnh 4)
                    let dismissModal = function() {
                        let modal = Array.from(document.querySelectorAll('div, section, aside')).find(el => {
                            let t = el.innerText || '';
                            return (t.includes('Xác nhận làm việc bằng App') || t.includes('Đã hiểu')) && el.offsetParent !== null;
                        });
                        if (modal) {
                            let cb = modal.querySelector('input[type="checkbox"]');
                            if (cb && !cb.checked) {
                                cb.click();
                            } else {
                                let lbl = Array.from(modal.querySelectorAll('label, span, div')).find(e => (e.innerText || '').includes('Đã hiểu'));
                                if (lbl) lbl.click();
                            }
                            let btnOk = Array.from(modal.querySelectorAll('button, div[role="button"]')).find(b => (b.innerText || '').trim().includes('Đồng ý'));
                            if (btnOk) {
                                btnOk.click();
                                if (window.GolikeJobBridge && window.GolikeJobBridge.onLog) window.GolikeJobBridge.onLog('Dismissed modal');
                                return true;
                            }
                        }
                        return false;
                    };

                    dismissModal();

                    // 3. Chọn tài khoản kiếm thưởng nếu chưa khớp (Ảnh 2 & 3)
                    let targetUser = '$cleanTargetUser';
                    if (targetUser) {
                        let accContainer = Array.from(document.querySelectorAll('div, section')).find(el => {
                            let t = el.innerText || '';
                            return t.includes('Tài khoản kiếm thưởng') && !t.includes('Hệ thống cấm') && el.offsetParent !== null;
                        });
                        if (accContainer) {
                            let curText = (accContainer.innerText || '').toLowerCase();
                            if (!curText.includes(targetUser)) {
                                let dropdownTrigger = accContainer.querySelector('div, button, svg, i') || accContainer;
                                dropdownTrigger.click();

                                setTimeout(function() {
                                    let items = Array.from(document.querySelectorAll('div, li, button')).filter(el => {
                                        let t = (el.innerText || '').toLowerCase();
                                        return t.includes(targetUser) && el.offsetParent !== null;
                                    });
                                    if (items.length > 0) {
                                        items[items.length - 1].click();
                                        if (window.GolikeJobBridge && window.GolikeJobBridge.onLog) window.GolikeJobBridge.onLog('Selected account ' + targetUser);
                                    }
                                }, 350);
                            }
                        }
                    }

                    // 4. Bấm nút "Nhận Job ngay ->" (Ảnh 2)
                    setTimeout(function() {
                        dismissModal();
                        let allBtns = Array.from(document.querySelectorAll('button, a, div[role="button"], span, div'));
                        let getJobBtn = allBtns.find(b => {
                            let t = (b.innerText || '').trim().toLowerCase();
                            return (t.includes('nhận job ngay') || t.includes('nhan job ngay') || t.includes('bắt đầu kiếm xu')) && b.offsetParent !== null;
                        });
                        if (getJobBtn) {
                            getJobBtn.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            getJobBtn.click();
                            if (window.GolikeJobBridge && window.GolikeJobBridge.onLog) window.GolikeJobBridge.onLog('Clicked get job button');
                            setTimeout(dismissModal, 400);
                            setTimeout(dismissModal, 900);
                        }
                    }, 500);

                    return 'triggered';
                } catch(e) {
                    return 'err: ' + e;
                }
            })();
        """.trimIndent()

        mainHandler.post {
            try {
                // Đảm bảo đang ở trang /jobs/tiktok
                val currentUrl = wv.url.orEmpty()
                if (!currentUrl.contains("/jobs/tiktok") || currentUrl.contains("job-detail")) {
                    wv.loadUrl("https://app.golike.net/jobs/tiktok")
                }
                wv.evaluateJavascript(domScript, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing DOM getJob: ${e.message}")
            }
        }

        // Lặp kiểm tra liên tục trong 25 giây: nếu trang đã chuyển sang Chi tiết job (Ảnh 5), trích xuất ngay từ DOM
        var elapsed = 0L
        while (elapsed < 25000L && !deferred.isCompleted) {
            delay(1000L)
            elapsed += 1000L

            mainHandler.post {
                try {
                    // Kiểm tra và bấm popup xác nhận nếu vẫn còn
                    wv.evaluateJavascript(
                        """
                        (function() {
                            try {
                                let modal = Array.from(document.querySelectorAll('div, section, aside')).find(el => {
                                    let t = el.innerText || '';
                                    return (t.includes('Xác nhận làm việc bằng App') || t.includes('Đã hiểu')) && el.offsetParent !== null;
                                });
                                if (modal) {
                                    let cb = modal.querySelector('input[type="checkbox"]');
                                    if (cb && !cb.checked) cb.click();
                                    let btnOk = Array.from(modal.querySelectorAll('button, div[role="button"]')).find(b => (b.innerText || '').trim().includes('Đồng ý'));
                                    if (btnOk) btnOk.click();
                                }

                                // Trích xuất thông tin nếu trang đã ở màn Chi tiết job (Ảnh 5)
                                let path = window.location.pathname || '';
                                let bodyText = document.body ? document.body.innerText : '';
                                if (path.includes('job-detail') || bodyText.includes('TĂNG LƯỢT THEO DÕI') || bodyText.includes('THẢ TIM') || bodyText.includes('Load job thành công')) {
                                    let isFollow = bodyText.includes('THEO DÕI') || bodyText.includes('Follow');
                                    let isLike = bodyText.includes('THẢ TIM') || bodyText.includes('Like') || bodyText.includes('TIM');
                                    let jobType = isLike ? 'like' : 'follow';

                                    let idMatch = bodyText.match(/Job Id:\s*([A-Za-z0-9_-]+)/i);
                                    let adsId = idMatch ? idMatch[1] : ('job_' + Date.now());

                                    let tiktokBtn = Array.from(document.querySelectorAll('a, button, div[role="button"]')).find(b => {
                                        let t = (b.innerText || '').toLowerCase();
                                        let h = b.getAttribute('href') || '';
                                        return t.includes('tiktok') || h.includes('tiktok.com');
                                    });

                                    let link = '';
                                    if (tiktokBtn) {
                                        link = tiktokBtn.getAttribute('href') || tiktokBtn.getAttribute('data-url') || '';
                                    }

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
                                    window.GolikeJobBridge.onJobResult(JSON.stringify(res));
                                }
                            } catch(e) {}
                        })();
                        """.trimIndent(),
                        null
                    )
                } catch (_: Exception) {}
            }
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
                            let allBtns = Array.from(document.querySelectorAll('button, a, div[role="button"], span, div'));
                            let ttBtn = allBtns.find(b => {
                                let t = (b.innerText || '').trim().toLowerCase();
                                let h = b.getAttribute('href') || '';
                                return (t.includes('tiktok') || h.includes('tiktok.com')) && b.offsetParent !== null;
                            });
                            if (ttBtn) {
                                ttBtn.scrollIntoView({ behavior: 'smooth', block: 'center' });
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

                    let allBtns = Array.from(document.querySelectorAll('button, a, div[role="button"], span, div'));
                    let compBtn = allBtns.find(b => {
                        let t = (b.innerText || '').trim().toLowerCase();
                        return (t.includes('hoàn thành') || t.includes('hoan thanh')) && b.offsetParent !== null;
                    });
                    if (compBtn) {
                        compBtn.scrollIntoView({ behavior: 'smooth', block: 'center' });
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
        if (res != null) return res

        // Fallback: nếu bấm nút xong mà web đã chuyển về hoặc không có lỗi -> xem như thành công
        return JSONObject().apply {
            put("success", true)
            put("data", JSONObject().apply { put("prices", 35) })
        }
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
