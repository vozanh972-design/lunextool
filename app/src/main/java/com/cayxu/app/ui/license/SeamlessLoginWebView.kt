package com.cayxu.app.ui.license

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hiển thị trang mua key / đăng nhập trực tiếp trong app một cách liền mạch (Seamless).
 * Tuyệt đối không để lộ bất kỳ thanh công cụ hay giao diện trình duyệt nào.
 * URL được giải mã động từ mảng byte nhị phân trong RAM (không chứa chuỗi tĩnh trong DEX).
 */
@Keep
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SeamlessLoginWebView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Xử lý phím Back / cử chỉ vuốt Back của Android
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onClose()
        }
    }

    // Giải phóng tài nguyên WebView khi đóng màn hình
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.WHITE)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            if (isExitUrl(url)) {
                                onClose()
                                return true
                            }
                            return false // Giữ mọi điều hướng bên trong WebView, không mở app ngoài
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val targetUrl = url.orEmpty()
                            if (isExitUrl(targetUrl)) {
                                onClose()
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            syncExitButton(view, url)
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            syncExitButton(view, url)
                        }
                    }

                    webChromeClient = WebChromeClient()

                    webViewInstance = this
                    loadUrl(getDecodedLoginUrl())
                }
            }
        )
    }
}

// Mảng 37-byte nhị phân mã hóa chuỗi https://lunex.io.vn/api/app/login.php
private val encLoginUrl = byteArrayOf(
    0x32.toByte(), 0x05.toByte(), 0xFC.toByte(), 0xEF.toByte(), 0xC5.toByte(), 0xF7.toByte(), 0xCB.toByte(), 0xD4.toByte(),
    0x7E.toByte(), 0x5C.toByte(), 0x2E.toByte(), 0x32.toByte(), 0x16.toByte(), 0xAB.toByte(), 0xF5.toByte(), 0xDC.toByte(),
    0xE4.toByte(), 0x97.toByte(), 0x96.toByte(), 0x20.toByte(), 0x47.toByte(), 0x4D.toByte(), 0x3D.toByte(), 0x44.toByte(),
    0xE3.toByte(), 0xE9.toByte(), 0xC0.toByte(), 0xE8.toByte(), 0xB2.toByte(), 0x9A.toByte(), 0x6B.toByte(), 0x4A.toByte(),
    0x54.toByte(), 0x7F.toByte(), 0x18.toByte(), 0x17.toByte(), 0xE6.toByte()
)

private fun getDecodedLoginUrl(): String {
    val baseKey = 0x5A
    val step = 0x17
    val out = CharArray(encLoginUrl.size)
    for (i in encLoginUrl.indices) {
        val k = (baseKey + i * step) and 0xFF
        val raw = encLoginUrl[i].toInt() and 0xFF
        val dec = (raw or k) - (raw and k)
        out[i] = dec.toChar()
    }
    return String(out)
}

// Mảng 12-byte nhị phân mã hóa chuỗi lunex://exit
private val encExitScheme = byteArrayOf(
    0x2B.toByte(), 0x11.toByte(), 0xEF.toByte(), 0xFB.toByte(), 0xC3.toByte(), 0xE2.toByte(),
    0xDA.toByte(), 0x3D.toByte(), 0x4A.toByte(), 0x34.toByte(), 0x00.toByte(), 0xF2.toByte()
)

private fun isExitUrl(url: String): Boolean {
    val baseKey = 0x47
    val step = 0x1D
    val out = CharArray(encExitScheme.size)
    for (i in encExitScheme.indices) {
        val k = (baseKey + i * step) and 0xFF
        val raw = encExitScheme[i].toInt() and 0xFF
        out[i] = (raw xor k).toChar()
    }
    val exitPrefix = String(out)
    return url.startsWith(exitPrefix, ignoreCase = true)
}

private fun syncExitButton(view: WebView?, url: String?) {
    val currentUrl = url.orEmpty().lowercase()
    val isHome = currentUrl.contains("home") || currentUrl.endsWith("/app/") || currentUrl.endsWith("/app")
    if (isHome) {
        val script = decodeScript(encInjectScript)
        view?.evaluateJavascript(script, null)
        view?.postDelayed({
            view.evaluateJavascript(script, null)
        }, 200)
    } else {
        view?.evaluateJavascript(decodeScript(encRemoveScript), null)
    }
}

private fun decodeScript(enc: ByteArray): String {
    val baseKey = 0x53
    val step = 0x1B
    val out = CharArray(enc.size)
    for (i in enc.indices) {
        val k = (baseKey + i * step) and 0xFF
        val raw = enc[i].toInt() and 0xFF
        out[i] = (raw xor k).toChar()
    }
    return String(out)
}

// Injected Exit Button Script (tự động clone kích thước & style của nút tai nghe, đặt cạnh tai nghe)
private val encInjectScript = byteArrayOf(
    0x7B.toByte(), 0x08.toByte(), 0xFC.toByte(), 0xCA.toByte(), 0xDC.toByte(), 0xAE.toByte(), 0x9C.toByte(), 0x7F.toByte(), 0x45.toByte(), 0x6E.toByte(), 0x48.toByte(), 0x07.toByte(), 0x9D.toByte(), 0x92.toByte(), 0xED.toByte(), 0x81.toByte(), 0x65.toByte(), 0x36.toByte(), 0x5D.toByte(), 0x3B.toByte(), 0x0C.toByte(), 0xFF.toByte(), 0xC8.toByte(), 0xA5.toByte(), 0xB5.toByte(), 0x82.toByte(), 0x3F.toByte(), 0x4B.toByte(), 0x22.toByte(), 0x16.toByte(), 0x38.toByte(), 0xF4.toByte(), 0xD6.toByte(), 0xA3.toByte(), 0x8C.toByte(), 0x6A.toByte(), 0x6B.toByte(), 0x78.toByte(), 0x2C.toByte(), 0x39.toByte(), 0xEF.toByte(), 0x8E.toByte(), 0xE6.toByte(), 0x83.toByte(), 0xA8.toByte(), 0x7E.toByte(), 0x55.toByte(), 0x17.toByte(), 0x06.toByte(), 0x06.toByte(), 0xF0.toByte(), 0xC0.toByte(), 0xE8.toByte(), 0xC3.toByte(), 0x2C.toByte(), 0x52.toByte(), 0x5E.toByte(), 0x22.toByte(), 0x04.toByte(), 0xFE.toByte(), 0xC9.toByte(), 0xF9.toByte(), 0xD7.toByte(), 0xD8.toByte(), 0x33.toByte(), 0x58.toByte(), 0x28.toByte(), 0x16.toByte(), 0x5F.toByte(), 0xF2.toByte(), 0xC5.toByte(), 0xED.toByte(), 0x8F.toByte(), 0x69.toByte(), 0x42.toByte(), 0x49.toByte(), 0x3A.toByte(), 0x17.toByte(), 0xE3.toByte(), 0xDC.toByte(), 0xED.toByte(), 0xAF.toByte(), 0x8C.toByte(), 0x71.toByte(), 0x5D.toByte(), 0x33.toByte(), 0x36.toByte(), 0xE5.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xB2.toByte(), 0x98.toByte(), 0x68.toByte(), 0x50.toByte(), 0x15.toByte(), 0x7F.toByte(), 0x11.toByte(), 0xF8.toByte(), 0xDD.toByte(), 0xB0.toByte(), 0xB0.toByte(), 0x94.toByte(), 0x4E.toByte(), 0x5F.toByte(), 0x25.toByte(), 0x05.toByte(), 0xED.toByte(), 0xF5.toByte(), 0xD4.toByte(), 0xB9.toByte(), 0xC7.toByte(), 0x35.toByte(), 0x01.toByte(), 0x51.toByte(), 0x29.toByte(), 0x11.toByte(), 0xE1.toByte(), 0xF9.toByte(), 0xB0.toByte(), 0x90.toByte(), 0x8B.toByte(), 0x79.toByte(), 0x43.toByte(), 0x38.toByte(), 0x34.toByte(), 0xEA.toByte(), 0xF8.toByte(), 0xDD.toByte(), 0xA7.toByte(), 0xCC.toByte(), 0x54.toByte(), 0x03.toByte(), 0x16.toByte(), 0x26.toByte(), 0x09.toByte(), 0xF4.toByte(), 0xC4.toByte(), 0xA5.toByte(), 0x94.toByte(), 0x91.toByte(), 0x72.toByte(), 0x5C.toByte(), 0x39.toByte(), 0x46.toByte(), 0xF2.toByte(), 0xEB.toByte(), 0xDC.toByte(), 0xA6.toByte(), 0x96.toByte(), 0x59.toByte(), 0x40.toByte(), 0x2C.toByte(), 0x3E.toByte(), 0x15.toByte(), 0xE5.toByte(), 0xC3.toByte(), 0xB5.toByte(), 0xCA.toByte(), 0xDA.toByte(), 0x7A.toByte(), 0x46.toByte(), 0x3A.toByte(), 0x1D.toByte(), 0xEB.toByte(), 0xF1.toByte(), 0xE1.toByte(), 0xB4.toByte(), 0x82.toByte(), 0x62.toByte(), 0x47.toByte(), 0x6C.toByte(), 0x30.toByte(), 0x16.toByte(), 0xF0.toByte(), 0xC8.toByte(), 0xA4.toByte(), 0xC9.toByte(), 0xC3.toByte(), 0x3B.toByte(), 0x77.toByte(), 0x1C.toByte(), 0x21.toByte(), 0xCD.toByte(), 0x82.toByte(), 0xE6.toByte(), 0xF1.toByte(), 0xD8.toByte(), 0x37.toByte(), 0x2D.toByte(), 0x62.toByte(), 0x7D.toByte(), 0x11.toByte(), 0xF5.toByte(), 0x86.toByte(), 0xE8.toByte(), 0x8C.toByte(), 0x8F.toByte(), 0x66.toByte(), 0x49.toByte(), 0x71.toByte(), 0x03.toByte(), 0xF6.toByte(), 0x8F.toByte(), 0xCC.toByte(), 0xB6.toByte(), 0x80.toByte(), 0x68.toByte(), 0x46.toByte(), 0x37.toByte(), 0x10.toByte(), 0x16.toByte(), 0xF0.toByte(), 0xCA.toByte(), 0xE3.toByte(), 0x97.toByte(), 0x65.toByte(), 0x6F.toByte(), 0x43.toByte(), 0x23.toByte(), 0x02.toByte(), 0xBC.toByte(), 0xA8.toByte(), 0x9D.toByte(), 0xF8.toByte(), 0x85.toByte(), 0x6F.toByte(), 0x5B.toByte(), 0x64.toByte(), 0x3D.toByte(), 0x47.toByte(), 0xF1.toByte(), 0xDF.toByte(), 0xA8.toByte(), 0x93.toByte(), 0x6C.toByte(), 0x79.toByte(), 0x59.toByte(), 0x26.toByte(), 0x43.toByte(), 0xEB.toByte(), 0xD1.toByte(), 0xDB.toByte(), 0xB8.toByte(), 0x80.toByte(), 0x6A.toByte(), 0x6F.toByte(), 0x29.toByte(), 0x05.toByte(), 0x16.toByte(), 0xF3.toByte(), 0xDF.toByte(), 0xB8.toByte(), 0xCF.toByte(), 0x25.toByte(), 0x7F.toByte(), 0x4D.toByte(), 0x27.toByte(), 0x1A.toByte(), 0xE6.toByte(), 0xCA.toByte(), 0x98.toByte(), 0xF3.toByte(), 0xCE.toByte(), 0x1A.toByte(), 0x0B.toByte(), 0x66.toByte(), 0x03.toByte(), 0x52.toByte(), 0xFE.toByte(), 0xD6.toByte(), 0xF0.toByte(), 0xCF.toByte(), 0x5C.toByte(), 0x41.toByte(), 0x55.toByte(), 0x2C.toByte(), 0x30.toByte(), 0xEF.toByte(), 0xDD.toByte(), 0xA9.toByte(), 0xAF.toByte(), 0xD1.toByte(), 0x2A.toByte(), 0x26.toByte(), 0x67.toByte(), 0x42.toByte(), 0x1F.toByte(), 0xB6.toByte(), 0xC7.toByte(), 0xB7.toByte(), 0x99.toByte(), 0x61.toByte(), 0x22.toByte(), 0x1D.toByte(), 0x37.toByte(), 0x05.toByte(), 0xFF.toByte(), 0xD2.toByte(), 0xAE.toByte(), 0xB2.toByte(), 0xD0.toByte(), 0x29.toByte(), 0x27.toByte(), 0x68.toByte(), 0x43.toByte(), 0x1C.toByte(), 0xB7.toByte(), 0xD7.toByte(), 0xA3.toByte(), 0x8B.toByte(), 0x76.toByte(), 0x53.toByte(), 0x75.toByte(), 0x37.toByte(), 0x1C.toByte(), 0xE9.toByte(), 0x9A.toByte(), 0xAA.toByte(), 0xAD.toByte(), 0xD6.toByte(), 0x70.toByte(), 0x42.toByte(), 0x28.toByte(), 0x17.toByte(), 0x0C.toByte(), 0xD4.toByte(), 0xD4.toByte(), 0xBD.toByte(), 0x8E.toByte(), 0x3D.toByte(), 0x2B.toByte(), 0x1C.toByte(), 0x77.toByte(), 0x10.toByte(), 0xA3.toByte(), 0xDB.toByte(), 0xA6.toByte(), 0xAA.toByte(), 0xB8.toByte(), 0x60.toByte(), 0x5B.toByte(), 0x38.toByte(), 0x0C.toByte(), 0xE2.toByte(), 0xEE.toByte(), 0xC2.toByte(), 0xB4.toByte(), 0xC4.toByte(), 0x20.toByte(), 0x51.toByte(), 0x49.toByte(), 0x21.toByte(), 0x1F.toByte(), 0xEB.toByte(), 0x8E.toByte(), 0xE8.toByte(), 0xB7.toByte(), 0x8A.toByte(), 0x3B.toByte(), 0x57.toByte(), 0x2E.toByte(), 0x12.toByte(), 0xC0.toByte(), 0xE8.toByte(), 0xC3.toByte(), 0xA0.toByte(), 0x84.toByte(), 0x6A.toByte(), 0x56.toByte(), 0x4A.toByte(), 0x3C.toByte(), 0x5C.toByte(), 0xA8.toByte(), 0xD9.toByte(), 0xB1.toByte(), 0x99.toByte(), 0x97.toByte(), 0x73.toByte(), 0x16.toByte(), 0x65.toByte(), 0x1B.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0x9F.toByte(), 0xFA.toByte(), 0xD5.toByte(), 0x03.toByte(), 0x04.toByte(), 0x1F.toByte(), 0x38.toByte(), 0x5B.toByte(), 0xE3.toByte(), 0xDF.toByte(), 0xBF.toByte(), 0x8D.toByte(), 0x99.toByte(), 0x39.toByte(), 0x51.toByte(), 0x22.toByte(), 0x04.toByte(), 0xEC.toByte(), 0xEC.toByte(), 0x84.toByte(), 0xF3.toByte(), 0xCC.toByte(), 0x3C.toByte(), 0x11.toByte(), 0x77.toByte(), 0x6F.toByte(), 0x4E.toByte(), 0xD3.toByte(), 0x8B.toByte(), 0xFC.toByte(), 0xE8.toByte(), 0xDD.toByte(), 0x38.toByte(), 0x51.toByte(), 0x60.toByte(), 0x00.toByte(), 0xEA.toByte(), 0xF1.toByte(), 0xDF.toByte(), 0xA7.toByte(), 0xB8.toByte(), 0x5F.toByte(), 0x6B.toByte(), 0x0D.toByte(), 0x61.toByte(), 0x50.toByte(), 0xAE.toByte(), 0xDE.toByte(), 0xBE.toByte(), 0x84.toByte(), 0xDE.toByte(), 0x6E.toByte(), 0x5D.toByte(), 0x2B.toByte(), 0x1E.toByte(), 0xED.toByte(), 0x9D.toByte(), 0x99.toByte(), 0xE7.toByte(), 0xC7.toByte(), 0x2E.toByte(), 0x07.toByte(), 0x2A.toByte(), 0x38.toByte(), 0x11.toByte(), 0xF4.toByte(), 0xC6.toByte(), 0xBD.toByte(), 0xD9.toByte(), 0xDD.toByte(), 0x2B.toByte(), 0x03.toByte(), 0x72.toByte(), 0x4B.toByte(), 0xF0.toByte(), 0xC8.toByte(), 0xD9.toByte(), 0xA0.toByte(), 0xB0.toByte(), 0x62.toByte(), 0x50.toByte(), 0x7E.toByte(), 0x7C.toByte(), 0x49.toByte(), 0xB4.toByte(), 0x9F.toByte(), 0xEA.toByte(), 0xD7.toByte(), 0x34.toByte(), 0x3B.toByte(), 0x04.toByte(), 0x65.toByte(), 0x4E.toByte(), 0xA7.toByte(), 0xC4.toByte(), 0xD4.toByte(), 0xB4.toByte(), 0x9F.toByte(), 0x33.toByte(), 0x0B.toByte(), 0x2A.toByte(), 0x30.toByte(), 0x14.toByte(), 0xF0.toByte(), 0x92.toByte(), 0xEB.toByte(), 0x95.toByte(), 0x75.toByte(), 0x6E.toByte(), 0x58.toByte(), 0x39.toByte(), 0x08.toByte(), 0xB5.toByte(), 0x81.toByte(), 0xDD.toByte(), 0xAC.toByte(), 0x86.toByte(), 0x7D.toByte(), 0x4F.toByte(), 0x2B.toByte(), 0x14.toByte(), 0x38.toByte(), 0xF9.toByte(), 0xDD.toByte(), 0xA3.toByte(), 0x95.toByte(), 0x20.toByte(), 0x3D.toByte(), 0x4B.toByte(), 0x27.toByte(), 0x1C.toByte(), 0xE6.toByte(), 0xCF.toByte(), 0xDA.toByte(), 0xF7.toByte(), 0x82.toByte(), 0x79.toByte(), 0x4F.toByte(), 0x32.toByte(), 0x09.toByte(), 0x41.toByte(), 0xB5.toByte(), 0x80.toByte(), 0xE3.toByte(), 0xDA.toByte(), 0x21.toByte(), 0x3E.toByte(), 0x4A.toByte(), 0x20.toByte(), 0x1D.toByte(), 0xE5.toByte(), 0xCE.toByte(), 0xA5.toByte(), 0xF6.toByte(), 0x9A.toByte(), 0x78.toByte(), 0x42.toByte(), 0x22.toByte(), 0x01.toByte(), 0x1C.toByte(), 0xE8.toByte(), 0x8E.toByte(), 0xEC.toByte(), 0x9B.toByte(), 0x6B.toByte(), 0x6A.toByte(), 0x54.toByte(), 0x31.toByte(), 0x52.toByte(), 0xAB.toByte(), 0xD5.toByte(), 0xB5.toByte(), 0xAE.toByte(), 0x98.toByte(), 0x79.toByte(), 0x48.toByte(), 0x65.toByte(), 0x0F.toByte(), 0x17.toByte(), 0xF7.toByte(), 0xD1.toByte(), 0xA5.toByte(), 0x85.toByte(), 0x6C.toByte(), 0x4E.toByte(), 0x06.toByte(), 0x74.toByte(), 0x03.toByte(), 0xE3.toByte(), 0xD2.toByte(), 0xAC.toByte(), 0xB9.toByte(), 0xDA.toByte(), 0x2D.toByte(), 0x12.toByte(), 0x25.toByte(), 0x0D.toByte(), 0x11.toByte(), 0xFF.toByte(), 0x95.toByte(), 0xA8.toByte(), 0xDA.toByte(), 0x3B.toByte(), 0x03.toByte(), 0x0D.toByte(), 0x6F.toByte(), 0x50.toByte(), 0xAD.toByte(), 0xD1.toByte(), 0xF2.toByte(), 0xE3.toByte(), 0xDB.toByte(), 0x22.toByte(), 0x0D.toByte(), 0x6A.toByte(), 0x1D.toByte(), 0xB2.toByte(), 0xA6.toByte(), 0x94.toByte(), 0xE7.toByte(), 0xCE.toByte(), 0x27.toByte(), 0x5B.toByte(), 0x0F.toByte(), 0x65.toByte(), 0x51.toByte(), 0xBF.toByte(), 0x91.toByte(), 0xE6.toByte(), 0xE1.toByte(), 0xC6.toByte(), 0x3A.toByte(), 0x5C.toByte(), 0x22.toByte(), 0x08.toByte(), 0xE4.toByte(), 0xA2.toByte(), 0x8B.toByte(), 0xBE.toByte(), 0x84.toByte(), 0x66.toByte(), 0x46.toByte(), 0x1E.toByte(), 0x21.toByte(), 0x45.toByte(), 0xB2.toByte(), 0x88.toByte(), 0xF3.toByte(), 0xC2.toByte(), 0xDB.toByte(), 0x6F.toByte(), 0x00.toByte(), 0x71.toByte(), 0x45.toByte(), 0xB4.toByte(), 0xBF.toByte(), 0x98.toByte(), 0xAB.toByte(), 0xDC.toByte(), 0x34.toByte(), 0x06.toByte(), 0x0E.toByte(), 0x62.toByte(), 0x57.toByte(), 0xB0.toByte(), 0xD2.toByte(), 0xF4.toByte(), 0xDC.toByte(), 0xDE.toByte(), 0x26.toByte(), 0x0A.toByte(), 0x6F.toByte(), 0x56.toByte(), 0xBF.toByte(), 0xB1.toByte(), 0xD5.toByte(), 0xBD.toByte(), 0x81.toByte(), 0x6F.toByte(), 0x1B.toByte(), 0x7C.toByte(), 0x74.toByte(), 0x05.toByte(), 0xE7.toByte(), 0xCB.toByte(), 0xF9.toByte(), 0xC5.toByte(), 0xC6.toByte(), 0x12.toByte(), 0x13.toByte(), 0x6E.toByte(), 0x0B.toByte(), 0xAA.toByte(), 0xF0.toByte(), 0xD4.toByte(), 0xB6.toByte(), 0x9C.toByte(), 0x62.toByte(), 0x45.toByte(), 0x2A.toByte(), 0x61.toByte(), 0x11.toByte(), 0xE7.toByte(), 0xC3.toByte(), 0xAB.toByte(), 0x97.toByte(), 0x97.toByte(), 0x76.toByte(), 0x5A.toByte(), 0x67.toByte(), 0x0F.toByte(), 0xAC.toByte(), 0xDB.toByte(), 0xDE.toByte(), 0xF8.toByte(), 0x81.toByte(), 0x7E.toByte(), 0x42.toByte(), 0x34.toByte(), 0x38.toByte(), 0x16.toByte(), 0xE7.toByte(), 0xEA.toByte(), 0xAC.toByte(), 0x82.toByte(), 0x9E.toByte(), 0x6F.toByte(), 0x59.toByte(), 0x24.toByte(), 0x43.toByte(), 0xAF.toByte(), 0x9A.toByte(), 0xD9.toByte(), 0xF9.toByte(), 0x81.toByte(), 0x79.toByte(), 0x47.toByte(), 0x33.toByte(), 0x0E.toByte(), 0x0B.toByte(), 0xFB.toByte(), 0xDF.toByte(), 0xAB.toByte(), 0x82.toByte(), 0x61.toByte(), 0x6F.toByte(), 0x5F.toByte(), 0x3E.toByte(), 0x02.toByte(), 0xAF.toByte(), 0x8B.toByte(), 0x86.toByte(), 0xAF.toByte(), 0x9A.toByte(), 0x60.toByte(), 0x4D.toByte(), 0x2B.toByte(), 0x28.toByte(), 0x54.toByte(), 0xF9.toByte(), 0xDF.toByte(), 0xA8.toByte(), 0x87.toByte(), 0x75.toByte(), 0x75.toByte(), 0x58.toByte(), 0x3C.toByte(), 0x43.toByte(), 0xE0.toByte(), 0xD1.toByte(), 0xDB.toByte(), 0xBF.toByte(), 0xC9.toByte(), 0x28.toByte(), 0x46.toByte(), 0x30.toByte(), 0x0E.toByte(), 0x1E.toByte(), 0xEE.toByte(), 0x8B.toByte(), 0xE3.toByte(), 0xC8.toByte(), 0x67.toByte(), 0x65.toByte(), 0x51.toByte(), 0x27.toByte(), 0x49.toByte(), 0xB2.toByte(), 0xD9.toByte(), 0x84.toByte(), 0xD0.toByte(), 0xD5.toByte(), 0x30.toByte(), 0x43.toByte(), 0x36.toByte(), 0x4F.toByte(), 0x0C.toByte(), 0xF6.toByte(), 0xC0.toByte(), 0xA8.toByte(), 0x86.toByte(), 0x77.toByte(), 0x50.toByte(), 0x56.toByte(), 0x30.toByte(), 0x0A.toByte(), 0xA4.toByte(), 0xCC.toByte(), 0xAE.toByte(), 0xA8.toByte(), 0x93.toByte(), 0x63.toByte(), 0x58.toByte(), 0x05.toByte(), 0x07.toByte(), 0x1B.toByte(), 0xF7.toByte(), 0xC1.toByte(), 0xAB.toByte(), 0xC1.toByte(), 0x66.toByte(), 0x33.toByte(), 0x52.toByte(), 0x25.toByte(), 0x59.toByte(), 0xB0.toByte(), 0xAC.toByte(), 0xBC.toByte(), 0xF5.toByte(), 0xDF.toByte(), 0x3B.toByte(), 0x16.toByte()
)

// Remove Exit Button Script (tự động gỡ khi rời khỏi trang chủ)
private val encRemoveScript = byteArrayOf(
    0x25.toByte(), 0x0F.toByte(), 0xFB.toByte(), 0x84.toByte(), 0xDD.toByte(), 0xE7.toByte(), 0x91.toByte(), 0x7F.toByte(), 0x48.toByte(), 0x33.toByte(), 0x0C.toByte(), 0x19.toByte(), 0xF9.toByte(), 0xC6.toByte(), 0xE3.toByte(), 0x8F.toByte(), 0x66.toByte(), 0x6A.toByte(), 0x7C.toByte(), 0x38.toByte(), 0x0A.toByte(), 0xE7.toByte(), 0xC0.toByte(), 0xAE.toByte(), 0xAF.toByte(), 0xB4.toByte(), 0x68.toByte(), 0x65.toByte(), 0x23.toByte(), 0x4A.toByte(), 0x5A.toByte(), 0xC7.toByte(), 0xEC.toByte(), 0xA2.toByte(), 0x91.toByte(), 0x5B.toByte(), 0x7A.toByte(), 0x42.toByte(), 0x3C.toByte(), 0x04.toByte(), 0xAC.toByte(), 0x8F.toByte(), 0xFA.toByte(), 0xB5.toByte(), 0x91.toByte(), 0x3A.toByte(), 0x4F.toByte(), 0x61.toByte(), 0x01.toByte(), 0x50.toByte(), 0xEB.toByte(), 0xD1.toByte(), 0xA2.toByte(), 0x85.toByte(), 0x73.toByte(), 0x45.toByte(), 0x13.toByte(), 0x7F.toByte(), 0x4A.toByte()
)


