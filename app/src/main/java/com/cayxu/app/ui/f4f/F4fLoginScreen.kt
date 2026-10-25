package com.cayxu.app.ui.f4f

import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
private val Globe = listOf("M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-1-15.93a8.04 8.04 0 0 0-4.8 4.93h2.62a14.2 14.2 0 0 1 2.18-4.93zm2 0a14.2 14.2 0 0 1 2.18 4.93h2.62a8.04 8.04 0 0 0-4.8-4.93zM4.26 13h3.58a14.86 14.86 0 0 0 .36 4H4.55a8.08 8.08 0 0 1-.29-4zm0-2a8.08 8.08 0 0 1 .29-4h3.65a14.86 14.86 0 0 0-.36 4zm5.58 2a13 13 0 0 1-.16-2h4.64a13 13 0 0 1-.16 2zm0-4c.12-1.38.45-2.72.94-4h2.44c.49 1.28.82 2.62.94 4zm4.48 6a14.86 14.86 0 0 0 .36-4h3.58a8.08 8.08 0 0 1-.29 4zm.36-6a14.86 14.86 0 0 0-.36-4h3.65a8.08 8.08 0 0 1 .29 4z")

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

    fun submitToken(tokenToVerify: String) {
        val clean = tokenToVerify.trim().removePrefix("Bearer ").trim()
        if (clean.isBlank()) {
            errorMessage = "Vui lòng nhập Access Token"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            when (val result = F4fApiClient.fetchMe(clean)) {
                is F4fApiResult.Success -> {
                    F4fSession.login(
                        context = context,
                        token = clean,
                        userUsername = result.data.username,
                        userPoints = result.data.balancePoints
                    )
                    isLoading = false
                    Toast.makeText(context, "Đăng nhập Follow4Follow thành công!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Routes.F4F_ACCOUNT) {
                        popUpTo(Routes.F4F_LOGIN) { inclusive = true }
                    }
                }
                is F4fApiResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PressBox(
                onClick = { navController.popBackStack() },
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
                    if (isLoggedIn) "Đã đăng nhập Follow4Follow" else "Chưa đăng nhập Follow4Follow",
                    style = type(14.5f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.25f),
                )
                BasicText(
                    if (isLoggedIn) "Nhập token mới nếu muốn đổi tài khoản" else "Kết nối với tài khoản Follow4Follow của bạn",
                    Modifier.padding(top = 2.dp),
                    style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.35f),
                )
            }
        }

        // Thẻ nhập Token thủ công
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(22.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            val clipboard = LocalClipboardManager.current
            val focusManager = LocalFocusManager.current
            val source = remember { MutableInteractionSource() }
            val focused by source.collectIsFocusedAsState()

            BasicText("Access Token / API Key", style = type(15.5f, FontWeight.ExtraBold, Alx.Ink))
            BasicText(
                "Dán token phiên (định dạng ID|Token) hoặc bấm nút đăng nhập web bên dưới",
                Modifier.padding(top = 3.dp),
                style = type(12.5f, FontWeight.Medium, Alx.Muted, lineHeight = 1.4f),
            )

            Box(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                BasicTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it; errorMessage = null },
                    enabled = !isLoading,
                    singleLine = true,
                    interactionSource = source,
                    textStyle = type(14f, FontWeight.Bold, Alx.Ink),
                    cursorBrush = SolidColor(F4fViolet),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        submitToken(tokenInput)
                    }),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Alx.White, RoundedCornerShape(14.dp))
                        .border(
                            if (focused || errorMessage != null) 2.dp else 1.5.dp,
                            if (errorMessage != null) Alx.Danger else if (focused) F4fViolet else Alx.InputBorder,
                            RoundedCornerShape(14.dp)
                        ),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize().padding(start = 44.dp, end = 76.dp), contentAlignment = Alignment.CenterStart) {
                            if (tokenInput.isEmpty()) {
                                BasicText("Dán token vào đây…", style = type(14f, FontWeight.Medium, Alx.Placeholder))
                            }
                            inner()
                        }
                    }
                )
                SvgIcon(Lock, 20.dp, Alx.Muted, 2f, Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
                PressBox(
                    onClick = { clipboard.getText()?.text?.trim()?.let { tokenInput = it; errorMessage = null } },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .height(36.dp)
                        .background(F4fVioletSoft, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { BasicText("Dán", style = type(13f, FontWeight.ExtraBold, F4fViolet)) }
            }

            if (errorMessage != null) {
                BasicText(errorMessage!!, Modifier.padding(top = 10.dp), style = type(12f, FontWeight.SemiBold, Alx.Danger))
            }

            LoginButton(
                label = if (isLoading) "Đang kết nối…" else "Đăng nhập",
                enabled = !isLoading && tokenInput.isNotBlank(),
                onClick = { submitToken(tokenInput) },
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // Nút mở Web Đăng nhập tự động bắt Token
        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(18.dp))
                .border(1.5.dp, F4fViolet, RoundedCornerShape(18.dp))
                .clickable { showWebView = true }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(40.dp).background(F4fVioletSoft, CircleShape), contentAlignment = Alignment.Center) {
                SvgIcon(Globe, 22.dp, F4fViolet, 2f)
            }
            Column(Modifier.weight(1f)) {
                BasicText("Đăng nhập bằng Web (Tự động)", style = type(14.5f, FontWeight.Bold, F4fViolet))
                BasicText("Mở web đăng nhập, app tự động lấy token", Modifier.padding(top = 2.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
            }
            SvgIcon(LoginArrow, 18.dp, F4fViolet, 2f)
        }

        Spacer(Modifier.height(30.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    if (showWebView) {
        F4fWebViewDialog(
            onClose = { showWebView = false },
            onTokenCaptured = { capturedToken ->
                showWebView = false
                tokenInput = capturedToken
                submitToken(capturedToken)
            }
        )
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

class F4fJsBridge(private val onTokenCaptured: (String) -> Unit) {
    @JavascriptInterface
    fun postToken(token: String?) {
        val clean = token?.trim()?.trim('"', ' ', '\'').orEmpty()
        if (clean.isNotBlank() && clean != "null" && clean != "undefined" && clean.contains("|")) {
            onTokenCaptured(clean)
        }
    }
}

@Composable
fun F4fWebViewDialog(
    onClose: () -> Unit,
    onTokenCaptured: (String) -> Unit
) {
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
                    Column {
                        BasicText("Đăng nhập Follow4Follow", style = type(16f, FontWeight.Bold, Alx.Ink))
                        BasicText("Đăng nhập xong app sẽ tự động bắt token", style = type(12f, FontWeight.Medium, Alx.Muted))
                    }
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
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true

                            addJavascriptInterface(F4fJsBridge { token ->
                                post { onTokenCaptured(token) }
                            }, "AndroidF4f")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Chạy script tự động kiểm tra token trong localStorage
                                    val script = """
                                        (function() {
                                            function check() {
                                                try {
                                                    var t = localStorage.getItem('token');
                                                    if (t && t.length > 5 && t.indexOf('|') !== -1) {
                                                        if (window.AndroidF4f) {
                                                            window.AndroidF4f.postToken(t);
                                                        }
                                                    }
                                                } catch(e) {}
                                            }
                                            check();
                                            if (!window._f4f_interval) {
                                                window._f4f_interval = setInterval(check, 1000);
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(script, null)
                                }
                            }
                            webChromeClient = WebChromeClient()
                            loadUrl("https://follow4follow.net/login")
                            webView = this
                        }
                    }
                )
            }
        }
    }
}
