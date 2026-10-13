package com.cayxu.app.ui.license

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.derivedStateOf
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
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseInOutCss
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.EaseScroll
import com.cayxu.app.ui.design.PrimaryButton
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.animationsEnabled
import com.cayxu.app.ui.design.keys
import com.cayxu.app.ui.design.rise
import com.cayxu.app.ui.design.type
import kotlin.math.roundToInt
import kotlin.random.Random

/* ───────────────────────────── Icon path (khung 24×24, lấy từ SVG thiết kế) ───────────────────────────── */

private val KeyPaths = listOf(
    "M4 15 A4 4 0 1 0 12 15 A4 4 0 1 0 4 15 Z",
    "M10.8 12.2L20 3M16 7l3 3",
)
private val ErrorPaths = listOf(
    "M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 Z",
    "M12 7.5v5M12 16h.01",
)
private val HeadsetPaths = listOf(
    "M4 14 L4 12 A8 8 0 0 1 20 12 L20 14",
    "M4 14 L5 14 A1.5 1.5 0 0 1 6.5 15.5 L6.5 18.5 A1.5 1.5 0 0 1 5 20 L4 20 A1.5 1.5 0 0 1 2.5 18.5 L2.5 15.5 A1.5 1.5 0 0 1 4 14 Z",
    "M19 14 L20 14 A1.5 1.5 0 0 1 21.5 15.5 L21.5 18.5 A1.5 1.5 0 0 1 20 20 L19 20 A1.5 1.5 0 0 1 17.5 18.5 L17.5 15.5 A1.5 1.5 0 0 1 19 14 Z",
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

            PhonePreview(replay = errorVersion * 2 + (if (keyValue.isEmpty()) 0 else 1), modifier = Modifier.padding(top = 12.dp))

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
            BasicText("Mã kích hoạt (License Key)", Modifier.padding(vertical = 8.dp), style = type(14f, FontWeight.Bold, Alx.Ink))
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
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().padding(start = 44.dp, end = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
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
                        Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                    )
                }
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
private fun PhonePreview(replay: Int, modifier: Modifier = Modifier) {
    val on = animationsEnabled()
    // CHỈ CHẠY 1 LẦN (6s) rồi dừng ở trạng thái cuối. Chạy lại khi vào màn hoặc khi `replay` đổi
    // (người dùng nhập key / bấm xác thực).
    //   0 → 1s    nội dung trượt từ dưới lên trong khung (khung điện thoại đứng yên)
    //   1.7 → 5.1s nội dung cuộn xuống 100dp
    //   1.2 → 3s   thanh 95% chạy, các số đếm từ 0 lên
    val t = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(replay, on) {
        if (on) {
            t.snapTo(0f)
            t.animateTo(1f, tween(6_000, easing = LinearEasing))
        } else {
            t.snapTo(1f)
        }
    }
    val slideY = { keys(t.value, 0f to 330f, 0.17f to 0f, ease = EaseOutSoft::transform) }
    val scrollY = { keys(t.value, 0f to 0f, 0.28f to 0f, 0.85f to -100f, ease = EaseScroll::transform) }
    val bar = { keys(t.value, 0f to 0f, 0.20f to 0f, 0.50f to 1f, ease = EaseInOutCss::transform) }

    // Mỗi lần chạy lại là một bộ số mới: các ô đếm từ 0 lên vài trăm nghìn đến vài triệu.
    val amounts = remember(replay) { randomAmounts((System.nanoTime() % 100_000L).toInt() + replay) }

    Box(
        modifier
            .fillMaxWidth()
            .height(244.dp)
            .clip(RoundedCornerShape(0.dp))
            .clearAndSetSemantics { },
    ) {
        // Khung điện thoại 252×340, bo 36, viền đen 6dp. Đứng yên; đáy bị panel cắt thẳng.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
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
            Box(Modifier.graphicsLayer { translationY = (slideY() + scrollY()).dp.toPx() }) {
                MiniHome(
                    bar = bar,
                    amounts = amounts,
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
private fun MiniHome(bar: () -> Float, amounts: List<Long>, modifier: Modifier = Modifier) {
    // Chữ chỉ cần cập nhật theo từng 1% để đỡ tốn recomposition; thanh chạy mượt ở pha vẽ.
    val step by remember(bar) { derivedStateOf { (bar() * 100).toInt() } }
    val barFraction = step / 100f
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
                Stat("Tổng xu", money((amounts.sum() * barFraction).toLong()), false, Modifier.weight(1f))
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
                            .graphicsLayer { scaleX = bar(); transformOrigin = TransformOrigin(0f, 0.5f) }
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
                Tile("XS", 12f, Color(0xFFC9363B), Color(0xFFFDECEC), "${money((amounts[0] * barFraction).toLong())} đ", "XSMM", "Tăng tương tác", Modifier.weight(1f))
                Tile("GL", 13f, Alx.Blue, Alx.BlueSoft, "${money((amounts[1] * barFraction).toLong())} đ", "GoLike", "Kiếm tiền MXH", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("TTC", 12f, Color(0xFFC2255C), Color(0xFFFDEAF2), "${money((amounts[2] * barFraction).toLong())} xu", "Tuongtaccheo", "Trao đổi like sub", Modifier.weight(1f))
                Tile("TK", 12f, Alx.ChipText, Color(0xFFEDF0F5), "${money((amounts[3] * barFraction).toLong())} xu", "TikTok TTC", "Tương tác TikTok", Modifier.weight(1f))
            }
        }
        WideTile("${money((amounts[4] * barFraction).toLong())} đ")
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
private fun WideTile(value: String) {
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
        BasicText(value, style = type(19f, FontWeight.ExtraBold, Alx.Ink))
    }
}

/** 485000 → "485k", 1250000 → "1,25tr". */
private fun money(v: Long): String = when {
    v >= 1_000_000L ->
        java.lang.String.format(java.util.Locale.US, "%.2f", v / 10_000L / 100.0)
            .trimEnd('0').trimEnd('.').replace('.', ',') + "tr"
    v >= 1_000L -> "${v / 1_000L}k"
    else -> "$v"
}

/** Bộ số giả cho 5 ô (XSMM, GoLike, TTC, TikTok TTC, Nhiemvucheo), đổi theo từng vòng lặp. */
private fun randomAmounts(seed: Int): List<Long> {
    val r = Random(seed * 7919 + 13)
    fun pick(lo: Int, hi: Int) = (r.nextInt(lo, hi) / 5 * 5) * 1_000L
    return listOf(pick(180, 900), pick(600, 2400), pick(120, 700), pick(90, 500), pick(150, 800))
}

/* ───────────────────────────── Route mẫu + Preview ───────────────────────────── */

/**
 * Nối vào app. Màn này KHÔNG tự kiểm tra key với server: bấm "Xác thực" thì gọi [onActivated],
 * rồi AppFlow chuyển sang màn kiểm tra (VerifyingRoute). Key sai thì VerifyingRoute quay về đây,
 * truyền lại [initialKey] và [initialError] để ô key hiện đỏ + rung + thông báo.
 */
@Composable
fun LicenseRoute(
    onActivated: (String) -> Unit,
    onBuy: () -> Unit,
    initialKey: String = "",
    initialError: String? = null,
) {
    var key by rememberSaveable { mutableStateOf(initialKey) }
    var error by rememberSaveable { mutableStateOf(initialError) }
    var errorVersion by rememberSaveable { mutableStateOf(if (initialError != null) 1 else 0) }
    LicenseScreen(
        keyValue = key,
        onKeyChange = { key = it; error = null },
        onVerify = {
            val k = key.trim()
            if (k.length >= 8) onActivated(k)
            else {
                error = "Mã key chưa đúng định dạng. Vui lòng kiểm tra lại."
                errorVersion++
            }
        },
        onBuy = onBuy,
        errorMessage = error,
        errorVersion = errorVersion,
    )
}

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
