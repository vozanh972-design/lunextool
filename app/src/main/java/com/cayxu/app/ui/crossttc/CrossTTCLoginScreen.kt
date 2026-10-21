package com.cayxu.app.ui.crossttc

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.cayxu.app.data.local.TikTokAccountsStore
import kotlinx.coroutines.launch
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
import androidx.navigation.NavController
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/*
 * MÀN "ĐĂNG NHẬP TƯƠNG TÁC CHÉO TIKTOK" — CHỈ là lớp giao diện, dựng y hệt cấu trúc
 * `com.cayxu.app.ui.xsmm.XsmmLoginScreen` theo đúng yêu cầu "làm Tương tác chéo TikTok giống
 * XSMM" — nhưng là file RIÊNG, package riêng (`com.cayxu.app.ui.crossttc`), không đè lên
 * XsmmLoginScreen.kt. Khác biệt duy nhất là nội dung/nhãn (Tương tác chéo thay vì XSMM, miền
 * tuongtaccheo.com thay vì xsmm.net) và MÀU: Đen TikTok `TtcDark`/`TtcDarkSoft` (#0F172A/#F1F5F9).
 *
 * Không gọi mạng, không kiểm tra token, không lưu token ở đây — màn chỉ nhận giá trị và báo lại
 * sự kiện qua callback, bạn tự nối vào logic đăng nhập Tương tác chéo thật đang có (hoặc sắp viết).
 */

private val TtcDark = Color(0xFF0F172A)
private val TtcDarkSoft = Color(0xFFF1F5F9)

private val Back = listOf("M19 12H5M11 6l-6 6 6 6")
private val Lock = listOf("M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V7a4 4 0 0 1 8 0v4")
private val LoginArrow = listOf("M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3")
private val External = listOf("M7 17L17 7M8 7h9v9")

/**
 * Cổng vào kết nối với NavController.
 */
@Composable
fun CrossTTCLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(CrossTTCStore.getToken(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(false) }
    var showWebView by remember { mutableStateOf(false) }

    var accountsState by remember {
        mutableStateOf(
            CrossTTCAccountsUiState(
                username = CrossTTCStore.getUsername(context),
                coins = CrossTTCStore.getCoins(context),
                accounts = emptyList(),
                selectedAccountIds = emptySet(),
            )
        )
    }
    var toastText by remember { mutableStateOf<String?>(null) }

    fun formatStatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    fun buildAccountsList(ttcAccounts: List<CrossTTCTikTokAccountInfo>): List<CrossTTCAccount> {
        val deviceTikToks = TikTokAccountsStore.getAccounts(context)
        val list = mutableListOf<CrossTTCAccount>()

        // 1. Nạp danh sách tài khoản TikTok trên máy (độc lập trong app)
        deviceTikToks.forEach { tk ->
            val cleanHandle = tk.handle.removePrefix("@")
            val isLinked = ttcAccounts.any { it.username.removePrefix("@").equals(cleanHandle, ignoreCase = true) }
            val h = if (tk.handle.startsWith("@")) tk.handle else "@${tk.handle}"
            val statList = mutableListOf<String>()
            if (tk.followerCount > 0) statList.add("${formatStatCount(tk.followerCount)} followers")
            if (tk.heartCount > 0) statList.add("${formatStatCount(tk.heartCount)} tim")
            val statLine = if (statList.isNotEmpty()) statList.joinToString(" • ") else ""

            list.add(
                CrossTTCAccount(
                    id = tk.uid,
                    handle = h,
                    avatarUrl = tk.avatarUrl,
                    statLine = statLine,
                    createdAt = tk.createDateFormatted.ifBlank { "Thiết bị" },
                    linked = isLinked,
                )
            )
        }

        // 2. Thêm các tài khoản TikTok đã cấu hình trên TTC web nếu có
        ttcAccounts.forEach { tt ->
            val cleanName = tt.username.removePrefix("@")
            val already = list.any { it.handle.removePrefix("@").equals(cleanName, ignoreCase = true) }
            if (!already) {
                list.add(
                    CrossTTCAccount(
                        id = tt.id,
                        handle = "@$cleanName",
                        avatarUrl = "",
                        statLine = if (tt.isActive) "Đang đặt làm nick chạy" else "",
                        createdAt = "TTC Web",
                        linked = true,
                    )
                )
            }
        }

        return list
    }

    fun performLogin(tokenToUse: String) {
        val trimmed = tokenToUse.trim()
        if (trimmed.isBlank()) {
            errorText = "Vui lòng nhập hoặc dán Access Token"
            return
        }
        isLoading = true
        errorText = null

        scope.launch {
            val res = CrossTTCApi.loginWithToken(trimmed)
            if (res.isSuccess) {
                CrossTTCStore.saveToken(context, trimmed)
                CrossTTCStore.saveSession(context, res.username, res.coins)

                val ttcTikToks = CrossTTCApi.getTtcTikTokAccounts(res.sessionCookie)
                val finalAccounts = buildAccountsList(ttcTikToks)

                accountsState = CrossTTCAccountsUiState(
                    username = res.username,
                    coins = res.coins,
                    accounts = finalAccounts,
                    selectedAccountIds = finalAccounts.map { it.id }.toSet(),
                )
                loggedIn = true
            } else {
                errorText = res.errorMessage.ifBlank { "Đăng nhập thất bại" }
            }
            isLoading = false
        }
    }

    // Tự động khôi phục phiên nếu đã có token từ trước
    LaunchedEffect(Unit) {
        val savedToken = CrossTTCStore.getToken(context)
        if (savedToken.isNotBlank()) {
            performLogin(savedToken)
        }
    }

    if (showWebView) {
        CrossTTCWebViewDialog(onDismiss = { showWebView = false })
    }

    if (loggedIn) {
        CrossTTCAccountsScreen(
            state = accountsState,
            onBack = { navController.popBackStack() },
            onRefreshSummary = {
                scope.launch {
                    val res = CrossTTCApi.loginWithToken(token)
                    if (res.isSuccess) {
                        CrossTTCStore.saveSession(context, res.username, res.coins)
                        val ttcTikToks = CrossTTCApi.getTtcTikTokAccounts(res.sessionCookie)
                        val updatedAccounts = buildAccountsList(ttcTikToks)
                        accountsState = accountsState.copy(
                            username = res.username,
                            coins = res.coins,
                            accounts = updatedAccounts,
                        )
                        toastText = "Đã làm mới số dư: ${res.coins} xu"
                    } else {
                        toastText = "Không thể làm mới: ${res.errorMessage}"
                    }
                }
            },
            onLogout = {
                CrossTTCStore.clearToken(context)
                loggedIn = false
                token = ""
            },
            onToggleSelectAll = {
                val allSelected = accountsState.accounts.isNotEmpty() &&
                    accountsState.accounts.all { it.id in accountsState.selectedAccountIds }
                accountsState = accountsState.copy(
                    selectedAccountIds = if (allSelected) emptySet() else accountsState.accounts.map { it.id }.toSet()
                )
            },
            onToggleAccount = { acc ->
                val current = accountsState.selectedAccountIds.toMutableSet()
                if (acc.id in current) current.remove(acc.id) else current.add(acc.id)
                accountsState = accountsState.copy(selectedAccountIds = current)
            },
            onRefreshAccount = { acc ->
                toastText = "Đang làm mới ${acc.handle}..."
            },
            onAddAccount = {
                loggedIn = false
            },
            onOpenConfig = {
                toastText = "Mở cấu hình tương tác chéo"
            },
            onRun = {
                val count = accountsState.selectedAccountIds.size
                if (count > 0) {
                    toastText = "Đang chạy $count tài khoản TikTok TTC"
                } else {
                    toastText = "Vui lòng chọn ít nhất 1 tài khoản"
                }
            },
            onDeleteSelected = {
                val toDelete = accountsState.selectedAccountIds
                if (toDelete.isNotEmpty()) {
                    toDelete.forEach { uid ->
                        TikTokAccountsStore.removeAccount(context, uid)
                    }
                    val remaining = accountsState.accounts.filterNot { it.id in toDelete }
                    accountsState = accountsState.copy(
                        accounts = remaining,
                        selectedAccountIds = emptySet()
                    )
                    toastText = "Đã xoá ${toDelete.size} tài khoản đã chọn"
                }
            },
            toastText = toastText,
        )
    } else {
        CrossTTCLoginScreen(
            token = token,
            onTokenChange = { token = it; errorText = null },
            onLogin = {
                performLogin(token)
            },
            onBack = { navController.popBackStack() },
            onOpenSite = {
                showWebView = true
            },
            loading = isLoading,
            errorText = errorText,
            loggedIn = loggedIn,
        )
    }
}

/**
 * @param token         nội dung ô nhập — bạn giữ ở ViewModel/state cũ rồi truyền vào.
 * @param onTokenChange gọi mỗi khi người dùng gõ hoặc bấm "Dán".
 * @param onLogin       bấm "Đăng nhập" (hoặc phím Xong) — nối vào hàm đăng nhập Tương tác chéo thật.
 * @param onOpenSite    bấm "Mở tuongtaccheo.com để lấy token" — mở trình duyệt tới trang thật.
 * @param loading       đang gọi đăng nhập: khóa nút và đổi chữ thành "Đang đăng nhập…".
 * @param errorText     thông báo lỗi sẵn có của bạn (null = không hiện).
 * @param loggedIn      trạng thái đăng nhập (giữ tương thích).
 */
@Composable
fun CrossTTCLoginScreen(
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
                modifier = Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
            BasicText(
                "Đăng nhập Tương tác chéo TikTok",
                style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f, lineHeight = 1.2f),
            )
        }

        // Thẻ Access Token
        Column(
            Modifier.padding(top = 20.dp).fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(22.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
        ) {
            BasicText("Access Token", style = type(15.5f, FontWeight.ExtraBold, Alx.Ink, lineHeight = 1.2f))
            BasicText(
                "Lấy token trong tài khoản trên tuongtaccheo.com rồi dán vào đây",
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
                label = if (loading) "Đang đăng nhập…" else "Đăng nhập",
                enabled = !loading,
                onClick = onLogin,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        // Mở trang lấy token
        PressBox(
            onClick = onOpenSite,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(18.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Mở tuongtaccheo.com để lấy token", Modifier.weight(1f), style = type(14f, FontWeight.Bold, Alx.Ink))
                SvgIcon(External, 18.dp, Alx.Muted, 2f)
            }
        }

        BasicText(
            "Token chỉ được lưu trên thiết bị này và dùng để kết nối tài khoản Tương tác chéo TikTok của bạn.",
            Modifier.padding(top = 16.dp, start = 6.dp, end = 6.dp, bottom = 40.dp).fillMaxWidth(),
            style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f, align = TextAlign.Center),
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

/* ───────────────────────────── Thành phần ───────────────────────────── */

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
        focused -> TtcDark
        else -> Alx.InputBorder
    }

    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(56.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = source,
            textStyle = type(14.5f, FontWeight.SemiBold, Alx.Ink),
            cursorBrush = SolidColor(TtcDark),
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
                            TtcDark.copy(alpha = 0.12f),
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
                    Modifier.fillMaxSize().padding(start = 44.dp, end = 76.dp),
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
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).height(36.dp)
                .background(TtcDarkSoft, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText("Dán", style = type(13f, FontWeight.ExtraBold, TtcDark)) }
    }
}

@Composable
private fun LoginButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "ttcLoginBtn")
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.7f }
            .shadow(12.dp, shape, ambientColor = Color(0x330F172A), spotColor = Color(0x800F172A))
            .background(TtcDark, shape)
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
private fun CrossTTCWebViewDialog(
    onDismiss: () -> Unit,
    url: String = "https://tuongtaccheo.com",
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var pageTitle by remember { mutableStateOf("tuongtaccheo.com") }

        Column(
            Modifier
                .fillMaxSize()
                .background(Alx.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Thanh điều hướng trên
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Alx.White)
                    .border(1.dp, Alx.Line)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PressBox(
                    onClick = onDismiss,
                    modifier = Modifier.size(38.dp).background(Alx.Bg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon(Back, 18.dp, Alx.Ink, 2f)
                }
                Column(Modifier.weight(1f)) {
                    BasicText(
                        pageTitle,
                        style = type(15f, FontWeight.Bold, Alx.Ink),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    BasicText(
                        "Đăng nhập & copy Access Token",
                        style = type(12f, FontWeight.Medium, Alx.Muted)
                    )
                }
                PressBox(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(38.dp).background(Alx.Bg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("Tải lại", style = type(12.5f, FontWeight.Bold, TtcDark))
                }
                PressBox(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(TtcDark, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("Đóng", style = type(12.5f, FontWeight.Bold, Alx.White))
                }
            }

            // WebView hiển thị trực tiếp trang web
            AndroidView(
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
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-A556B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val t = view?.title
                                if (!t.isNullOrBlank()) pageTitle = t
                            }
                        }
                        loadUrl(url)
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize().weight(1f)
            )
        }
    }
}

/* ───────────────────────────── Preview (chỉ để xem bố cục) ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CrossTTCLoginScreenPreview() {
    CrossTTCLoginScreen(token = "", onTokenChange = {}, onLogin = {}, onBack = {}, onOpenSite = {})
}
