package com.cayxu.app.ui.license

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseInOutCss
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.EaseScroll
import com.cayxu.app.ui.design.PrimaryButton
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.animationsEnabled
import com.cayxu.app.ui.design.keys
import com.cayxu.app.ui.design.rememberLoop
import com.cayxu.app.ui.design.rise
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.screens.login.LoginViewModel
import kotlin.math.roundToInt

/* ───────────────────────────── Icon path (khung 24×24, lấy từ SVG thiết kế) ───────────────────────────── */

private val ClipboardPaths = listOf(
    "M9 3h6a1 1 0 011 1v2a1 1 0 01-1 1H9a1 1 0 01-1-1V4a1 1 0 011-1z",
    "M16 5h2a2 2 0 012 2v12a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2h2",
)
private val KeyPaths = listOf(
    "M4 15a4 4 0 108 0a4 4 0 10-8 0",
    "M10.8 12.2L20 3M16 7l3 3",
)
private val ErrorPaths = listOf(
    "M3 12a9 9 0 1018 0a9 9 0 10-18 0",
    "M12 7.5v5M12 16h.01",
)
private val HeadsetPaths = listOf(
    "M4 14v-2a8 8 0 0116 0v2",
    "M4 14H5A1.5 1.5 0 016.5 15.5V18.5A1.5 1.5 0 015 20H4A1.5 1.5 0 012.5 18.5V15.5A1.5 1.5 0 014 14Z",
    "M19 14H20A1.5 1.5 0 0121.5 15.5V18.5A1.5 1.5 0 0120 20H19A1.5 1.5 0 0117.5 18.5V15.5A1.5 1.5 0 0119 14Z",
)

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * @param errorMessage khác null thì ô key chuyển sang trạng thái lỗi (viền đỏ + dòng báo lỗi).
 * @param errorVersion tăng 1 mỗi lần xác thực sai để ô key rung lại (kể cả khi cùng một lỗi).
 * @param loading true khi đang gọi server: khóa nút và đổi chữ thành "Đang xác thực…".
 */
@Composable
fun LicenseScreen(
    keyValue: String,
    onKeyChange: (String) -> Unit,
    onVerify: () -> Unit,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    errorVersion: Int = 0,
    loading: Boolean = false,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("AUTOLUNEX", style = type(16f, FontWeight.ExtraBold, Alx.Ink, spacing = 0.1f))
                BasicText(
                    "Bản quyền 2026",
                    Modifier
                        .background(Alx.BlueSoft, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = type(12f, FontWeight.Bold, Alx.Blue),
                )
            }

            BasicText(
                buildAnnotatedString {
                    append("Tự động hóa\n")
                    withStyle(SpanStyle(color = Alx.Blue)) { append("tác vụ bạn cần.") }
                },
                Modifier
                    .padding(top = 26.dp)
                    .rise(50),
                style = type(34f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.03f, lineHeight = 1.12f),
            )
            BasicText(
                "Nhập mã License Key để mở khóa toàn bộ tính năng cày xu đa kênh.",
                Modifier
                    .padding(top = 10.dp)
                    .widthIn(max = 320.dp)
                    .rise(50),
                style = type(14.5f, FontWeight.Medium, Alx.Ink2, lineHeight = 1.55f),
            )

            PhonePreview(Modifier.padding(top = 12.dp))

            KeyField(
                value = keyValue,
                onValueChange = onKeyChange,
                onDone = onVerify,
                errorMessage = errorMessage,
                errorVersion = errorVersion,
                modifier = Modifier.padding(top = 18.dp),
            )

            PrimaryButton(
                label = if (loading) "Đang xác thực…" else "Xác thực & Kích hoạt",
                onClick = onVerify,
                enabled = !loading,
                radius = 16.dp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .rise(440),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp)
                .rise(540),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row {
                BasicText("Bạn chưa có key? ", style = type(14f, FontWeight.Medium, Alx.Ink2))
                BasicText(
                    "Mua ngay",
                    Modifier.clickable(role = Role.Button, onClick = onBuy),
                    style = type(14f, FontWeight.ExtraBold, Alx.Blue),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SvgIcon(HeadsetPaths, 15.dp, Alx.Muted, 1.8f)
                BasicText("Hỗ trợ kỹ thuật 24/7", style = type(13f, FontWeight.Medium, Alx.Muted))
            }
        }
    }
}

/* ───────────────────────────── Ô nhập key ───────────────────────────── */

@Composable
private fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    errorMessage: String?,
    errorVersion: Int,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    val hasError = errorMessage != null

    // Rung ngang 2 lần (±6dp, 220ms mỗi lần) mỗi khi errorVersion tăng.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorVersion) {
        if (errorVersion > 0) {
            repeat(2) {
                shake.animateTo(-6f, tween(55))
                shake.animateTo(6f, tween(110))
                shake.animateTo(0f, tween(55))
            }
        }
    }

    Column(modifier.rise(340)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Mã kích hoạt (License Key)", style = type(14f, FontWeight.Bold, Alx.Ink))
            Row(
                Modifier
                    .clickable(role = Role.Button) {
                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(onValueChange)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SvgIcon(ClipboardPaths, 15.dp, Alx.Blue, 2f)
                BasicText("Dán key", style = type(13f, FontWeight.Bold, Alx.Blue))
            }
        }

        val borderColor = when {
            focused -> Alx.Blue
            hasError -> Alx.Danger
            else -> Alx.InputBorder
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            interactionSource = source,
            textStyle = type(14.5f, FontWeight.Medium, Alx.Ink),
            cursorBrush = SolidColor(Alx.Blue),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrect = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
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
                .background(if (hasError && !focused) Alx.DangerBg else Alx.White, shape)
                .border(if (focused) 2.dp else 1.5.dp, borderColor, shape),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize().padding(start = 44.dp, end = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        BasicText(
                            "Dán hoặc nhập mã key tại đây...",
                            style = type(14.5f, FontWeight.Medium, Alx.Placeholder),
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                SvgIcon(
                    KeyPaths, 18.dp, if (hasError && !focused) Alx.Danger else Alx.Muted, 1.8f,
                    Modifier.padding(start = 16.dp, top = 19.dp),
                )
            },
        )

        if (hasError) {
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SvgIcon(ErrorPaths, 16.dp, Alx.Danger, 2f, Modifier.padding(top = 1.dp))
                BasicText(
                    errorMessage ?: "",
                    style = type(12.5f, FontWeight.SemiBold, Alx.Danger, lineHeight = 1.4f),
                )
            }
        }
    }
}

/* ───────────────────────────── Điện thoại mini = Trang chủ thật, trượt lên + tự cuộn ───────────────────────────── */

@Composable
private fun PhonePreview(modifier: Modifier = Modifier) {
    val on = animationsEnabled()
    // Vòng 10s: điện thoại trồi lên → màn hình cuộn xuống → thanh 95% chạy → hạ xuống → lặp
    val p by rememberLoop(10_000, on, rest = 0.5f)
    val phoneY = keys(p, 0f to 250f, 0.10f to 0f, 0.88f to 0f, 0.94f to 250f, 1f to 250f, ease = EaseOutSoft::transform)
    val scrollY = keys(p, 0f to 0f, 0.17f to 0f, 0.52f to -100f, 0.94f to -100f, 0.941f to 0f, 1f to 0f, ease = EaseScroll::transform)
    val bar = keys(p, 0f to 0f, 0.12f to 0f, 0.30f to 1f, 0.94f to 1f, 0.941f to 0f, 1f to 0f, ease = EaseInOutCss::transform)

    Box(
        modifier
            .fillMaxWidth()
            .height(244.dp)
            .clip(RoundedCornerShape(0.dp))
            .clearAndSetSemantics { },
    ) {
        // Khung điện thoại 252×340, bo 36, viền đen 6dp
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .graphicsLayer { translationY = phoneY.dp.toPx() }
                .size(252.dp, 340.dp)
                .shadow(
                    16.dp, RoundedCornerShape(36.dp),
                    ambientColor = Color(0x330F172A), spotColor = Color(0x800F172A),
                )
                .background(Alx.Ink, RoundedCornerShape(36.dp))
                .padding(6.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Alx.Bg),
        ) {
            Box(Modifier.graphicsLayer { translationY = scrollY.dp.toPx() }) {
                MiniHome(
                    barFraction = bar,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(390.dp, 700.dp)
                        .graphicsLayer {
                            scaleX = 0.6154f; scaleY = 0.6154f
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                )
            }
        }
        // Chân điện thoại mờ dần vào nền màn hình
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Alx.Bg.copy(alpha = 0f),
                        0.85f to Alx.Bg,
                        1f to Alx.Bg,
                    ),
                ),
        )
    }
}

/** Trang chủ ở kích thước thiết kế 390dp (sau đó được thu 61.54% để nằm trong điện thoại mini). */
@Composable
private fun MiniHome(barFraction: Float, modifier: Modifier = Modifier) {
    val card = RoundedCornerShape(22.dp)
    Column(
        modifier.padding(start = 20.dp, end = 20.dp, top = 34.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Alx.BlueSoft, CircleShape), contentAlignment = Alignment.Center) {
                    BasicText("US", style = type(15f, FontWeight.ExtraBold, Alx.Blue))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    BasicText("Chào buổi trưa,", style = type(12.5f, FontWeight.Medium, Alx.Muted))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        BasicText("username", style = type(17f, FontWeight.ExtraBold, Alx.Ink))
                        BasicText(
                            "PRO",
                            Modifier.background(Alx.Blue, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                            style = type(10f, FontWeight.ExtraBold, Alx.White, spacing = 0.08f, lineHeight = 1.2f),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { Box(Modifier.size(44.dp).background(Alx.White, CircleShape).border(1.dp, Alx.Line, CircleShape)) }
            }
        }

        // Thẻ thống kê
        Column(Modifier.fillMaxWidth().background(Alx.White, card).border(1.dp, Alx.Line, card)) {
            Row(Modifier.padding(vertical = 16.dp)) {
                Stat("Tổng xu", "0", false, Modifier.weight(1f))
                Stat("Dịch vụ", "5/5", true, Modifier.weight(1f))
                Stat("Còn lại", "25 ngày", true, Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Alx.Divider))
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("Hiệu suất hôm nay", style = type(12f, FontWeight.SemiBold, Alx.Ink2))
                    BasicText("${(95 * barFraction).roundToInt()}%", style = type(13f, FontWeight.ExtraBold, Alx.Blue))
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(Alx.BlueSoft, RoundedCornerShape(3.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.95f)
                            .height(6.dp)
                            .graphicsLayer { scaleX = barFraction; transformOrigin = TransformOrigin(0f, 0.5f) }
                            .background(Alx.Blue, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }

        BasicText(
            "Truy cập nhanh",
            Modifier.padding(top = 6.dp),
            style = type(17f, FontWeight.ExtraBold, Alx.Ink),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("XS", 12f, Color(0xFFC9363B), Color(0xFFFDECEC), "0 đ", "XSMM", "Tăng tương tác", Modifier.weight(1f))
                Tile("GL", 13f, Alx.Blue, Alx.BlueSoft, "0 đ", "GoLike", "Kiếm tiền MXH", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("TTC", 12f, Color(0xFFC2255C), Color(0xFFFDEAF2), "0 xu", "Tuongtaccheo", "Trao đổi like sub", Modifier.weight(1f))
                Tile("TK", 12f, Alx.ChipText, Color(0xFFEDF0F5), "0 xu", "TikTok TTC", "Tương tác TikTok", Modifier.weight(1f))
            }
        }
        WideTile()
    }
}

@Composable
private fun Stat(label: String, value: String, divider: Boolean, modifier: Modifier) {
    Column(
        modifier
            .then(
                if (divider) Modifier.drawBehind {
                    drawRect(Alx.Divider, Offset.Zero, Size(1.dp.toPx(), size.height))
                } else Modifier,
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(label, style = type(11.5f, FontWeight.Medium, Alx.Muted))
        BasicText(value, style = type(20f, FontWeight.ExtraBold, Alx.Ink))
    }
}

@Composable
private fun Tile(
    badge: String,
    badgeSp: Float,
    badgeColor: Color,
    cornerColor: Color,
    value: String,
    title: String,
    subtitle: String,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(132.dp)
            .clip(shape)
            .background(Alx.White)
            .border(1.dp, Alx.Line, shape)
            .drawBehind { drawCircle(cornerColor, 48.dp.toPx(), Offset(size.width - 18.dp.toPx(), 18.dp.toPx())) }
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(42.dp).background(badgeColor, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                BasicText(badge, style = type(badgeSp, FontWeight.ExtraBold, Alx.White))
            }
            BasicText(value, Modifier.padding(top = 2.dp), style = type(19f, FontWeight.ExtraBold, Alx.Ink))
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            BasicText(title, style = type(15f, FontWeight.ExtraBold, Alx.Ink))
            BasicText(subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
        }
    }
}

@Composable
private fun WideTile() {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(Alx.White)
            .border(1.dp, Alx.Line, shape)
            .drawBehind { drawCircle(Color(0xFFE3F4F8), 48.dp.toPx(), Offset(size.width - 0.dp.toPx(), 8.dp.toPx())) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).background(Color(0xFF0E7490), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            BasicText("NV", style = type(12f, FontWeight.ExtraBold, Alx.White))
        }
        Column(Modifier.weight(1f)) {
            BasicText("Nhiemvucheo", style = type(15f, FontWeight.ExtraBold, Alx.Ink))
            BasicText("Làm nhiệm vụ nhận thưởng", Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted))
        }
        BasicText("0 đ", style = type(19f, FontWeight.ExtraBold, Alx.Ink))
    }
}

/* ───────────────────────────── Route mẫu + Preview ───────────────────────────── */

/**
 * LicenseRoute kết nối API xác thực key qua LoginViewModel.
 * Khi xác thực thành công -> gọi onActivated.
 */
@Composable
fun LicenseRoute(
    onActivated: (String) -> Unit,
    onBuy: () -> Unit = {},
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var errorVersion by rememberSaveable { mutableStateOf(0) }

    // Rung khi có lỗi từ server
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            errorVersion++
        }
    }

    // Tự động chuyển tiếp nếu key đã lưu hợp lệ
    LaunchedEffect(uiState.isCheckingSavedKey) {
        if (!uiState.isCheckingSavedKey && viewModel.consumeAutoLoginSuccess()) {
            onActivated(uiState.keyInput)
        }
    }

    val buyAction = onBuy.takeIf { onBuy != {} } ?: {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/theanh39_bot"))
        context.startActivity(intent)
    }

    LicenseScreen(
        keyValue = uiState.keyInput,
        onKeyChange = { viewModel.onKeyInputChange(it) },
        onVerify = {
            viewModel.login(onSuccess = { onActivated(uiState.keyInput.trim()) })
        },
        onBuy = buyAction,
        errorMessage = uiState.errorMessage,
        errorVersion = errorVersion,
        loading = uiState.isLoading
    )
}

private fun isKeyValid(key: String) = key.trim().length >= 8 && !key.trim().endsWith("0000")

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LicenseScreenPreview() {
    LicenseScreen(keyValue = "", onKeyChange = {}, onVerify = {}, onBuy = {})
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LicenseScreenErrorPreview() {
    LicenseScreen(
        keyValue = "ALX-4F2K-91XZ-0000", onKeyChange = {}, onVerify = {}, onBuy = {},
        errorMessage = "Mã key không đúng hoặc đã bị khóa. Vui lòng kiểm tra lại.", errorVersion = 1,
    )
}
