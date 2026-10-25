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
                            return false // Giữ mọi điều hướng bên trong WebView, không mở app ngoài
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
