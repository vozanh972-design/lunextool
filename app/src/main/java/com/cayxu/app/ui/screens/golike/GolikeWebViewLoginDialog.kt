package com.cayxu.app.ui.screens.golike

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private val GolikeBrandOrange = Color(0xFFF59E0B)

@Composable
fun GolikeWebViewLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (username: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val isCaptured = remember { AtomicBoolean(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // BƯỚC 2: Polling định kỳ mỗi 1.2s từ Android ép web gọi /api/users/me và quét session
    LaunchedEffect(webViewInstance) {
        val wv = webViewInstance ?: return@LaunchedEffect
        while (isActive && !isCaptured.get()) {
            delay(1200L)
            if (!isCaptured.get()) {
                withContext(Dispatchers.Main) {
                    try {
                        wv.evaluateJavascript(
                            "if(window.goMaxCaptureSessionStore) window.goMaxCaptureSessionStore();",
                            null
                        )
                        wv.evaluateJavascript(
                            """
                            (function() {
                              try {
                                if (window.fetch && (location.href.indexOf('app.golike.net') !== -1 || location.pathname !== '/login')) {
                                  window.fetch('/api/users/me').catch(function(){});
                                }
                              } catch(e) {}
                            })();
                            """.trimIndent(),
                            null
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = CardWhite
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Đăng nhập Golike",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GolikeBrandOrange.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Tự động bắt Token",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GolikeBrandOrange
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                loadError = null
                                webViewInstance?.reload()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Tải lại", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = TextSecondary, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                // Progress Bar khi đang tải trang
                if (loadingProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = GolikeBrandOrange,
                        trackColor = GolikeBrandOrange.copy(alpha = 0.2f)
                    )
                } else {
                    Divider(color = Color(0xFFEEF1F5), thickness = 1.dp)
                }

                // Thanh thông báo nếu có lỗi mạng / lỗi nạp trang
                loadError?.let { err ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEF2F2))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable {
                                loadError = null
                                webViewInstance?.reload()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Lỗi tải trang: $err. Nhấn để thử lại.",
                            fontSize = 12.sp,
                            color = Color(0xFFB91C1C),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // AndroidView WebView chiếm trọn diện tích còn lại
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewInstance = this
                                // Dọn sạch cookie & storage web cũ nếu chưa đăng nhập để trang hiện form login mới
                                if (!GolikeSession.isLoggedIn.value) {
                                    try {
                                        val cookieManager = android.webkit.CookieManager.getInstance()
                                        cookieManager.removeAllCookies(null)
                                        cookieManager.flush()
                                        android.webkit.WebStorage.getInstance().deleteAllData()
                                        clearCache(true)
                                        clearHistory()
                                    } catch (_: Exception) {}
                                }
                                GolikeAuthWebView.setupWebView(
                                    webView = this,
                                    callback = object : GolikeAuthWebView.AuthCallback {
                                        override fun onAuthCaptured(
                                            authToken: String,
                                            tToken: String,
                                            deviceId: String,
                                            username: String,
                                            gAuth: String,
                                            signingKey: String,
                                            userId: String,
                                            webData: String,
                                            version: String,
                                            client: String,
                                            scheme: String
                                        ) {
                                            if (!isCaptured.compareAndSet(false, true)) return

                                            val resolvedUsername = username.ifBlank { "Golike User" }

                                            // 1. Lưu phiên ban đầu vào Store ngay lập tức (.commit())
                                            GolikeAccountsStore.saveLogin(
                                                context = context,
                                                token = authToken,
                                                username = resolvedUsername,
                                                balance = 0L,
                                                tToken = tToken,
                                                deviceId = deviceId,
                                                gAuth = gAuth,
                                                signingKey = signingKey,
                                                userId = userId,
                                                webData = webData
                                            )
                                            GolikeAccountsStore.saveVersionData(
                                                context = context,
                                                version = version,
                                                client = client,
                                                scheme = scheme
                                            )

                                            // BƯỚC 3: CẬP NHẬT GOLIKE SESSION, ĐÓNG WEBVIEW & THÔNG BÁO TRÊN MAIN THREAD
                                            scope.launch(Dispatchers.Main) {
                                                GolikeSession.login(
                                                    context = context,
                                                    userToken = authToken,
                                                    userUsername = resolvedUsername,
                                                    userBalance = 0L,
                                                    tToken = tToken,
                                                    deviceId = deviceId,
                                                    gAuth = gAuth,
                                                    signingKey = signingKey,
                                                    userId = userId,
                                                    webData = webData
                                                )
                                                Toast.makeText(context, "Đã liên kết Golike: @$resolvedUsername", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess(resolvedUsername)
                                                onDismiss()
                                            }

                                            // 2. Chạy ngầm trong IO: Sync protocol, lấy số dư thật và danh sách nick
                                            scope.launch(Dispatchers.IO) {
                                                val client = GolikeApiClient(
                                                    authToken = authToken,
                                                    tToken = tToken,
                                                    deviceId = deviceId,
                                                    username = resolvedUsername,
                                                    gAuth = gAuth,
                                                    version = version,
                                                    client = client,
                                                    scheme = scheme
                                                )

                                                // 2. Đồng bộ protocol Golike (Chuẩn GoMax)
                                                try {
                                                    val protoRes = client.syncProtocol()
                                                    val protoData = protoRes?.optJSONObject("data") ?: protoRes
                                                    val syncScheme = protoData?.optString("scheme").orEmpty().ifBlank { scheme }
                                                    val protocol = protoData?.optString("protocol").orEmpty()
                                                    val gauthVer = protoData?.optString("gauth_version").orEmpty()
                                                    val appVer = protoData?.optString("version_app").orEmpty().ifBlank { version }
                                                    GolikeAccountsStore.saveProtocolData(
                                                        context = context,
                                                        scheme = syncScheme,
                                                        protocol = protocol,
                                                        gauthVersion = gauthVer,
                                                        versionApp = appVer
                                                    )
                                                    client.updateSession(
                                                        authToken = authToken,
                                                        tToken = tToken,
                                                        deviceId = deviceId,
                                                        username = resolvedUsername,
                                                        gAuth = gAuth,
                                                        version = appVer,
                                                        client = client,
                                                        scheme = syncScheme
                                                    )
                                                } catch (e: Exception) {
                                                    android.util.Log.w("GolikeProtocol", "Lỗi sync protocol: ${e.message}")
                                                }

                                                // 3. Lấy thông tin user và số dư thật
                                                try {
                                                    val meObj = client.getMe()
                                                    val dataObj = meObj?.optJSONObject("data")
                                                    val realUsername = dataObj?.optString("username")?.takeIf { it.isNotBlank() } ?: resolvedUsername
                                                    val realCoin = dataObj?.optLong("coin") ?: 0L

                                                    GolikeAccountsStore.saveLogin(
                                                        context = context,
                                                        token = authToken,
                                                        username = realUsername,
                                                        balance = realCoin,
                                                        tToken = tToken,
                                                        deviceId = deviceId,
                                                        gAuth = gAuth,
                                                        signingKey = signingKey,
                                                        userId = userId,
                                                        webData = webData
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        GolikeSession.updateBalance(context, realCoin)
                                                        GolikeSession.username.value = realUsername
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    onProgressChanged = { progress ->
                                        loadingProgress = progress
                                        if (progress >= 100) {
                                            loadError = null
                                        }
                                    },
                                    onErrorOccurred = { err ->
                                        loadError = err
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
