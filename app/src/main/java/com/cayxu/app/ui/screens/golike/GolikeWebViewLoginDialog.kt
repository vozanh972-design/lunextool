package com.cayxu.app.ui.screens.golike

import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GolikeBrandOrange = Color(0xFFF59E0B)

@Composable
fun GolikeWebViewLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (username: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isCaptured by remember { mutableStateOf(false) }

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
                            onClick = { webViewInstance?.reload() },
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

                Divider(color = Color(0xFFEEF1F5), thickness = 1.dp)

                // AndroidView WebView
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                GolikeAuthWebView.setupWebView(this, object : GolikeAuthWebView.AuthCallback {
                                    override fun onAuthCaptured(
                                        authToken: String,
                                        tToken: String?,
                                        deviceId: String?,
                                        username: String?,
                                        gAuth: String?
                                    ) {
                                        if (isCaptured) return
                                        isCaptured = true

                                        scope.launch(Dispatchers.IO) {
                                            val client = GolikeApiClient(
                                                authToken = authToken,
                                                tToken = tToken,
                                                deviceId = deviceId,
                                                username = username,
                                                gAuth = gAuth
                                            )
                                            val meObj = client.getMe()
                                            val dataObj = meObj?.optJSONObject("data")
                                            val resolvedUsername = dataObj?.optString("username")?.takeIf { it.isNotBlank() }
                                                ?: username?.takeIf { it.isNotBlank() }
                                                ?: "Golike User"
                                            val resolvedCoin = dataObj?.optLong("coin") ?: 0L

                                            // Lưu session đầy đủ vào SharedPreferences
                                            GolikeAccountsStore.saveLogin(
                                                context = context,
                                                token = authToken,
                                                username = resolvedUsername,
                                                balance = resolvedCoin,
                                                tToken = tToken.orEmpty(),
                                                deviceId = deviceId.orEmpty(),
                                                gAuth = gAuth.orEmpty()
                                            )

                                            withContext(Dispatchers.Main) {
                                                GolikeSession.login(
                                                    context = context,
                                                    userToken = authToken,
                                                    userUsername = resolvedUsername,
                                                    userBalance = resolvedCoin,
                                                    tToken = tToken.orEmpty(),
                                                    deviceId = deviceId.orEmpty(),
                                                    gAuth = gAuth.orEmpty()
                                                )
                                                Toast.makeText(context, "Đăng nhập Golike thành công: $resolvedUsername", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess(resolvedUsername)
                                                onDismiss()
                                            }
                                        }
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
