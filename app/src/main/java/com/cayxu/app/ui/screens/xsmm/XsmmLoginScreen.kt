package com.cayxu.app.ui.screens.xsmm

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.repository.XsmmAuthRepository
import com.cayxu.app.data.repository.XsmmLoginResult
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.locale.Str
import com.cayxu.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/*
 * Màn "Đăng nhập XSMM" — CHỈ là lớp giao diện mới.
 * Không có gọi mạng tuỳ tiện, không lưu sai logic: toàn bộ kết nối do hệ thống XSMM sẵn có của bạn đảm nhiệm.
 */

private val XsmmRed = Color(0xFFC9363B)
private val XsmmRedSoft = Color(0xFFFDECEC)

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val CloseIcon = listOf("M18 6L6 18M6 6l12 12")
private val Lock = listOf("M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V7a4 4 0 0 1 8 0v4")
private val LoginArrow = listOf("M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3")
private val External = listOf("M7 17L17 7M8 7h9v9")

/**
 * Cổng vào kết nối với NavController và logic đăng nhập XSMM cũ.
 */
@Composable
fun XsmmLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tokenInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoggedIn = remember { XsmmAccountStore.isLoggedIn(context) }
    var showWebView by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (XsmmAccountStore.isLoggedIn(context)) {
            XsmmSession.restore(context)
            navController.navigate(Routes.XSMM_ACCOUNT) {
                popUpTo(Routes.XSMM_LOGIN) { inclusive = true }
            }
        }
    }

    fun submit() {
        if (tokenInput.isBlank()) {
            errorMessage = Str.xsmmEmptyTokenError
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            when (val result = XsmmAuthRepository.fetchUser(tokenInput)) {
                is XsmmLoginResult.Success -> {
                    XsmmSession.login(context, tokenInput.trim(), result.info.username, result.info.points)
                    isLoading = false
                    navController.navigate(Routes.XSMM_ACCOUNT) {
                        popUpTo(Routes.XSMM_LOGIN) { inclusive = true }
                    }
                }
                is XsmmLoginResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    XsmmLoginScreen(
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
        XsmmWebViewDialog(onClose = { showWebView = false })
    }
}

/**
 * @param token         nội dung ô nhập — giữ ở ViewModel/state rồi truyền vào.
 * @param onTokenChange gọi mỗi khi người dùng gõ hoặc bấm "Dán".
 * @param onLogin       bấm "Đăng nhập" (hoặc phím Xong) — nối vào hàm đăng nhập sẵn có.
 * @param onOpenSite    bấm "Mở xsmm.net để lấy token" — mở WebView xsmm.net trực tiếp trong app.
 * @param loading       đang gọi đăng nhập: khóa nút và đổi chữ thành "Đang đăng nhập…".
 * @param errorText     thông báo lỗi sẵn có của bạn (null = không hiện).
 * @param loggedIn      đã đăng nhập: thẻ trạng thái hiện "Đã đăng nhập XSMM" thay vì "Chưa đăng nhập".
 */
@Composable
fun XsmmLoginScreen(
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
            BasicText(Str.xsmmTitle, style = type(24f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
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
            Box(Modifier.size(48.dp).background(XsmmRedSoft, CircleShape), contentAlignment = Alignment.Center) {
                BasicText("XSMM", style = type(12.5f, FontWeight.ExtraBold, XsmmRed))
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    if (loggedIn) Str.xsmmLoggedIn else Str.xsmmNotLoggedIn,
                    style = type(14.5f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.25f),
                )
                BasicText(
                    if (loggedIn) Str.xsmmLoggedInSubtitle else Str.xsmmNotLoggedInSubtitle,
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
            BasicText(Str.xsmmAccessToken, style = type(15.5f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.2f))
            BasicText(
                Str.xsmmTokenDesc,
                Modifier.padding(top = 3.dp),
                style = type(12.5f, FontWeight.Medium, Alx.Muted, lineHeight = 1.4f),
            )

            TokenField(token, onTokenChange, enabled = !loading, hasError = errorText != null, onDone = onLogin)

            if (errorText != null) {
                BasicText(
                    errorText,
                    Modifier.padding(top = 10.dp, start = 4.dp),
                    style = type(12.5f, FontWeight.SemiBold, Alx.Danger, lineHeight = 1.4f),
                )
            }

            LoginButton(
                label = if (loading) Str.xsmmLoggingIn else Str.xsmmLogin,
                enabled = !loading,
                onClick = onLogin,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        // Mở trang lấy token
        PressBox(
            onClick = onOpenSite,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(18.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(Str.xsmmOpenSite, Modifier.weight(1f), style = type(14f, FontWeight.Bold, Alx.Ink))
                SvgIcon(External, 18.dp, Alx.Muted, 2f)
            }
        }

        BasicText(
            Str.xsmmNote,
            Modifier
                .padding(top = 16.dp, start = 6.dp, end = 6.dp, bottom = 40.dp)
                .fillMaxWidth(),
            style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f, align = TextAlign.Center),
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

/* ───────────────────────────── Thành phần ───────────────────────────── */

/**
 * Hộp thoại hiển thị WebView xsmm.net trực tiếp trong app.
 * Thuần túy là WebView thông thường, không đọc hay can thiệp ngầm bất kỳ dữ liệu nào.
 */
@Composable
private fun XsmmWebViewDialog(onClose: () -> Unit) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Alx.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(Modifier.fillMaxSize()) {
                // Thanh tiêu đề
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PressBox(
                        onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Alx.White, CircleShape)
                            .border(1.dp, Alx.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SvgIcon(Back, 18.dp, Alx.Ink, 2f)
                    }

                    Column(Modifier.weight(1f)) {
                        BasicText("xsmm.net", style = type(16f, FontWeight.ExtraBold, Alx.Ink))
                        BasicText("https://xsmm.net", style = type(12f, FontWeight.Medium, Alx.Muted))
                    }

                    PressBox(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Alx.White, CircleShape)
                            .border(1.dp, Alx.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SvgIcon(CloseIcon, 18.dp, Alx.Ink, 2f)
                    }
                }

                // Thanh tiến trình tải trang
                if (progress in 1..99) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Alx.Line)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(2.5.dp)
                                .background(Alx.Blue)
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Alx.Line))
                }

                // WebView hiển thị xsmm.net thuần túy
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                @Suppress("SetJavaScriptEnabled")
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            webViewClient = object : WebViewClient() {
                                // Thuần túy điều hướng web thông thường, không tiêm JS hay đọc trộm dữ liệu
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            loadUrl("https://xsmm.net")
                            webViewInstance = this
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TokenField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    hasError: Boolean,
    onDone: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val shape = RoundedCornerShape(16.dp)
    val border = when {
        hasError -> Alx.Danger
        focused -> Alx.Blue
        else -> Alx.InputBorder
    }

    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = source,
            textStyle = type(14.5f, FontWeight.SemiBold, Alx.Ink),
            cursorBrush = SolidColor(Alx.Blue),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone() }),
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (focused) {
                        val g = 5.dp.toPx()
                        drawRoundRect(
                            Alx.Blue.copy(alpha = 0.12f),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(16.dp.toPx() + g),
                        )
                    }
                }
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
                        BasicText(Str.xsmmTokenPlaceholder, style = type(14.5f, FontWeight.Medium, Alx.Placeholder))
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
                .background(Alx.BlueSoft, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(Str.xsmmPaste, style = type(13f, FontWeight.ExtraBold, Alx.Blue)) }
    }
}

@Composable
private fun LoginButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "xsmmBtn")
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.7f }
            .shadow(12.dp, shape, ambientColor = Color(0x4D1F4BD8), spotColor = Color(0xB31F4BD8))
            .background(Alx.Blue, shape)
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

/* ───────────────────────────── Preview (chỉ để xem bố cục) ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun XsmmLoginScreenPreview() {
    XsmmLoginScreen(token = "", onTokenChange = {}, onLogin = {}, onBack = {}, onOpenSite = {})
}
