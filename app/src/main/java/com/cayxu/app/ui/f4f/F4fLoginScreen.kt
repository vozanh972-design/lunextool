package com.cayxu.app.ui.f4f

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.navigation.Routes
import kotlinx.coroutines.launch

private val F4fViolet = Color(0xFF7C3AED)
private val F4fVioletSoft = Color(0xFFF5F3FF)

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val CloseIcon = listOf("M18 6L6 18M6 6l12 12")
private val Lock = listOf("M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V7a4 4 0 0 1 8 0v4")
private val LoginArrow = listOf("M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3")
private val External = listOf("M7 17L17 7M8 7h9v9")

@Composable
fun F4fLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tokenInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoggedIn = remember { F4fAccountStore.isLoggedIn(context) }
    var showWebView by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (F4fAccountStore.isLoggedIn(context)) {
            F4fSession.restore(context)
            navController.navigate(Routes.F4F_ACCOUNT) {
                popUpTo(Routes.F4F_LOGIN) { inclusive = true }
            }
        }
    }

    fun submit() {
        if (tokenInput.isBlank()) {
            errorMessage = "Vui lòng nhập Access Token hoặc API Key"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            // Lưu thông tin đăng nhập F4F
            val trimmed = tokenInput.trim()
            val username = "f4f_user_${trimmed.take(6)}"
            F4fSession.login(context, trimmed, username, 0L)
            isLoading = false
            navController.navigate(Routes.F4F_ACCOUNT) {
                popUpTo(Routes.F4F_LOGIN) { inclusive = true }
            }
        }
    }

    F4fLoginContent(
        token = tokenInput,
        onTokenChange = {
            tokenInput = it
            errorMessage = null
        },
        onLogin = { submit() },
        onBack = { navController.popBackStack() },
        onOpenSite = { showWebView = true },
        loading = isLoading,
        errorText = errorMessage,
        loggedIn = isLoggedIn,
    )

    if (showWebView) {
        F4fWebViewDialog(onClose = { showWebView = false })
    }
}

@Composable
private fun F4fLoginContent(
    token: String,
    onTokenChange: (String) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    errorText: String? = null,
    loggedIn: Boolean = false,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PressBox(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Alx.White, CircleShape)
                    .border(1.dp, Alx.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
            BasicText("Đăng nhập Follow4Follow", style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
        }

        // Thẻ trạng thái
        Row(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(22.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).background(F4fVioletSoft, CircleShape), contentAlignment = Alignment.Center) {
                BasicText("F4F", style = type(12.5f, FontWeight.ExtraBold, F4fViolet))
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    if (loggedIn) "Đã đăng nhập Follow4Follow" else "Chưa đăng nhập Follow4Follow",
                    style = type(14.5f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.25f),
                )
                BasicText(
                    if (loggedIn) "Nhập token mới nếu bạn muốn đổi tài khoản" else "Dán Access Token để kết nối tài khoản",
                    Modifier.padding(top = 2.dp),
                    style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.35f),
                )
            }
        }

        // Thẻ Access Token
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(22.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
        ) {
            BasicText("Access Token / API Key", style = type(15.5f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.2f))
            BasicText(
                "Lấy token từ tài khoản Follow4Follow của bạn và dán vào bên dưới",
                Modifier.padding(top = 3.dp),
                style = type(12.5f, FontWeight.Medium, Alx.Muted, lineHeight = 1.4f),
            )

            TokenField(token, onTokenChange, enabled = !loading, hasError = errorText != null, onDone = onLogin)

            if (errorText != null) {
                BasicText(
                    errorText,
                    Modifier.padding(top = 8.dp),
                    style = type(12f, FontWeight.SemiBold, Alx.Danger, lineHeight = 1.35f),
                )
            }

            LoginButton(
                label = if (loading) "Đang đăng nhập…" else "Đăng nhập",
                enabled = !loading && token.isNotBlank(),
                onClick = onLogin,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        // Thẻ mở web
        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(18.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onOpenSite)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                "Mở trang web Follow4Follow để lấy token",
                Modifier.weight(1f),
                style = type(13.5f, FontWeight.Bold, F4fViolet),
            )
            SvgIcon(External, 18.dp, F4fViolet, 2f)
        }

        Spacer(Modifier.height(30.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TokenField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    hasError: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()

    val border = when {
        hasError -> Alx.Danger
        focused -> F4fViolet
        else -> Alx.InputBorder
    }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .height(52.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = source,
            textStyle = type(14.5f, FontWeight.Bold, Alx.Ink),
            cursorBrush = SolidColor(F4fViolet),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onDone()
            }),
            modifier = Modifier
                .fillMaxSize()
                .background(Alx.White, shape)
                .border(if (focused || hasError) 2.dp else 1.5.dp, border, shape),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 44.dp, end = 76.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        BasicText("Dán token vào đây…", style = type(14.5f, FontWeight.Medium, Alx.Placeholder))
                    }
                    inner()
                }
            },
        )
        SvgIcon(Lock, 20.dp, Alx.Muted, 2f, Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
        PressBox(
            onClick = { clipboard.getText()?.text?.trim()?.let(onValueChange) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .height(36.dp)
                .background(F4fVioletSoft, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText("Dán", style = type(13f, FontWeight.ExtraBold, F4fViolet)) }
    }
}

@Composable
private fun LoginButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "f4fBtn")
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.7f }
            .shadow(12.dp, shape, ambientColor = Color(0x4D7C3AED), spotColor = Color(0xB37C3AED))
            .background(F4fViolet, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SvgIcon(LoginArrow, 20.dp, Alx.White, 2f)
        BasicText(label, Modifier.padding(start = 8.dp), style = type(16f, FontWeight.ExtraBold, Alx.White))
    }
}

@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content() }
}

@Composable
fun F4fWebViewDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var webView: WebView? by remember { mutableStateOf(null) }

        BackHandler {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
            } else {
                onClose()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Alx.White)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText("Follow4Follow Web", style = type(16f, FontWeight.Bold, Alx.Ink))
                    PressBox(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Alx.Bg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        SvgIcon(CloseIcon, 18.dp, Alx.Ink, 2f)
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            loadUrl("https://follow4follow.com")
                            webView = this
                        }
                    }
                )
            }
        }
    }
}
