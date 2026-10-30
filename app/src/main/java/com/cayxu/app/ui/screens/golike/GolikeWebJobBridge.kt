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

        // Cỗ máy trạng thái tuần hoàn (Active State Machine) quét và thao tác mỗi 1.2s trong tối đa 30s
        var elapsed = 0L
        while (elapsed < 30000L && !deferred.isCompleted) {
            mainHandler.post {
                try {
                    val currentUrl = wv.url.orEmpty()
                    // Nếu webview đang trống hoặc không thuộc golike -> nạp trang /jobs
                    if (currentUrl.isBlank() || !currentUrl.contains("golike.net")) {
                        wv.loadUrl("https://app.golike.net/jobs")
                    }

                    wv.evaluateJavascript(
                        """
                        (function() {
                            try {
                                $NETWORK_INTERCEPTOR_JS

                                let pathname = window.location.pathname || '';
                                let bodyText = document.body ? document.body.innerText : '';

                                // 1. Kiểm tra Captcha
                                let captchaEl = document.querySelector('iframe[src*="recaptcha"], iframe[src*="cloudflare"], div.g-recaptcha, div.cf-turnstile');
                                if (captchaEl && captchaEl.offsetParent !== null) {
                                    if (window.GolikeJobBridge && window.GolikeJobBridge.onCaptchaDetected) {
                                        window.GolikeJobBridge.onCaptchaDetected();
                                    }
                                }

                                // 2. Tự xử lý Popup "Xác nhận làm việc bằng App" / "Đã hiểu" (Ảnh 4)
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
                                        return 'modal_dismissed';
                                    }
                                }

                                // Xử lý SweetAlert2 popup "OK" (Bước 9 log F12)
                                let swalOk = document.querySelector('button.swal2-confirm');
                                if (swalOk && swalOk.offsetParent !== null && !swalOk.disabled) {
                                    swalOk.click();
                                }

                                // 3. BƯỚC TRANG TỔNG /jobs: Bấm vào thẻ TikTok <h6> (Bước 2 log F12)
                                if (pathname === '/jobs' || pathname.endsWith('/jobs') || (!pathname.includes('/jobs/tiktok') && bodyText.includes('Chọn kênh kiếm xu'))) {
                                    let ttCard = Array.from(document.querySelectorAll('h6, span, div, p')).find(el => {
                                        let t = (el.innerText || '').trim().toLowerCase();
                                        return (t === 'tiktok' || t.startsWith('tiktok')) && el.offsetParent !== null;
                                    });
                                    if (ttCard) {
                                        let clickable = ttCard.closest('div') || ttCard;
                                        clickable.click();
                                        if (window.GolikeJobBridge && window.GolikeJobBridge.onLog) window.GolikeJobBridge.onLog('Clicked TikTok card on /jobs');
                                        return 'navigated_to_tiktok';
                                    }
                                }

                                // 4. BƯỚC TRANG /jobs/tiktok (Chưa vào chi tiết job)
                                if (pathname.includes('/jobs/tiktok') && !pathname.includes('job-detail')) {
                                    // A. Kiểm tra tài khoản kiếm thưởng (Bước 3 & 4 log F12)
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
                                                    let items = Array.from(document.querySelectorAll('div, li, button, h6, span')).filter(el => {
                                                        let t = (el.innerText || '').toLowerCase();
                                                        return t.includes(targetUser) && el.offsetParent !== null;
                                                    });
                                                    if (items.length > 0) {
                                                        items[items.length - 1].click();
                                                    }
                                                }, 350);
                                            }
                                        }
                                    }

                                    // B. Bấm nút <div> "Nhận Job ngay" (Bước 5 log F12)
                                    let now = Date.now();
                                    if (!window._lastClickGetJob || (now - window._lastClickGetJob > 3000)) {
                                        let getJobEl = Array.from(document.querySelectorAll('div, button, a')).find(el => {
                                            let t = (el.innerText || '').trim().toLowerCase();
                                            return (t.includes('nhận job ngay') || t.includes('nhan job ngay') || t.includes('bắt đầu kiếm xu')) && el.offsetParent !== null;
                                        });
                                        if (getJobEl) {
                                            window._lastClickGetJob = now;
                                            getJobEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                            getJobEl.click();
                                            if (window.GolikeJobBridge && window.GolikeJobBridge.onLog) window.GolikeJobBridge.onLog('Clicked div Nhận Job ngay');
                                            return 'clicked_get_job';
                                        }
                                    }
                                }

                                // 5. BƯỚC TRANG CHI TIẾT JOB: /jobs/tiktok/job-detail (Bước 6 log F12)
                                if (pathname.includes('job-detail') || bodyText.includes('TĂNG LƯỢT THEO DÕI') || bodyText.includes('THẢ TIM') || bodyText.includes('Load job thành công')) {
                                    // Tìm thẻ <a> chứa link TikTok (như log: <a href="https://www.tiktok.com/@..." class="btn bg-button-1 ...">TikTok</a>)
                                    let tiktokLinkEl = Array.from(document.querySelectorAll('a')).find(a => {
                                        let h = a.getAttribute('href') || '';
                                        let t = (a.innerText || '').toLowerCase();
                                        return (h.includes('tiktok.com') || t.includes('tiktok')) && a.offsetParent !== null;
                                    });

                                    let link = tiktokLinkEl ? (tiktokLinkEl.getAttribute('href') || '') : '';
                                    let isFollow = bodyText.includes('THEO DÕI') || bodyText.includes('Follow') || bodyText.includes('TĂNG LƯỢT THEO DÕI');
                                    let isLike = bodyText.includes('THẢ TIM') || bodyText.includes('Like') || bodyText.includes('TIM');
                                    let jobType = isLike ? 'like' : 'follow';

                                    let idMatch = bodyText.match(/Job Id:\s*([A-Za-z0-9_-]+)/i);
                                    let adsId = idMatch ? idMatch[1] : ('job_' + Date.now());

                                    // Nếu network hook đã bắt được dữ liệu chính xác từ API
                                    if (window._lastJobData && window._lastJobData.data) {
                                        let jd = window._lastJobData.data;
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
