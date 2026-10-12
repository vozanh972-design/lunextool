package com.cayxu.app.ui.screens.welcome

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import com.cayxu.app.ui.locale.LanguageState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ───────────────────────────── Design tokens ───────────────────────────── */

private object Alx {
    val Bg = Color(0xFFF3F5F9)
    val Ink = Color(0xFF0F172A)
    val Ink2 = Color(0xFF475569)
    val Muted = Color(0xFF64748B)
    val Blue = Color(0xFF1F4BD8)
    val BlueSoft = Color(0xFFE8EEFD)
    val BlueSelected = Color(0xFFF5F8FF)
    val PanelBorder = Color(0xFFDCE5FA)
    val Globe = Color(0xFFB9CBF3)
    val Line = Color(0xFFE4E8F0)
    val RadioOff = Color(0xFFCBD2DF)
    val Chip = Color(0xFFF1F5F9)
    val ChipText = Color(0xFF334155)
    val White = Color.White
}

/** Manrope: copy 4 file TTF vào res/font: manrope_medium, manrope_semibold, manrope_bold, manrope_extrabold. */
private val Manrope = FontFamily(
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun type(
    size: Float,
    weight: FontWeight,
    color: Color,
    spacing: Float = 0f,
    lineHeight: Float? = null,
    align: TextAlign = TextAlign.Unspecified,
) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    letterSpacing = spacing.em,
    lineHeight = if (lineHeight != null) lineHeight.em else TextStyle.Default.lineHeight,
    textAlign = align,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/* ───────────────────────────── Model ───────────────────────────── */

enum class AppLanguage(val tag: String) { VI("vi"), EN("en") }

private data class PreviewCopy(
    val tag: String,
    val greet: String,
    val welcome: String,
    val tabs: List<String>,
    val reply: String,
)

private fun AppLanguage.preview() = when (this) {
    AppLanguage.VI -> PreviewCopy(
        "NGÔN NGỮ GIAO DIỆN", "Xin chào", "Chào mừng đến với AUTOLUNEX",
        listOf("Trang chủ", "Nhiệm vụ", "Tiện ích"), "Bắt đầu thôi!",
    )
    AppLanguage.EN -> PreviewCopy(
        "INTERFACE LANGUAGE", "Hello", "Welcome to AUTOLUNEX",
        listOf("Home", "Tasks", "Tools"), "Let's start!",
    )
}

/* ───────────────────────────── Animation helpers ───────────────────────────── */

private fun smooth(x: Float) = x * x * (3f - 2f * x)

/** Nội suy theo các mốc (giống @keyframes), mỗi đoạn làm mượt kiểu ease. */
private fun keys(t: Float, vararg p: Pair<Float, Float>): Float {
    if (t <= p.first().first) return p.first().second
    for (i in 0 until p.size - 1) {
        val (t0, v0) = p[i]
        val (t1, v1) = p[i + 1]
        if (t <= t1) {
            val f = if (t1 == t0) 1f else (t - t0) / (t1 - t0)
            return v0 + (v1 - v0) * smooth(f)
        }
    }
    return p.last().second
}

/** Vòng lặp 0..1. Khi người dùng tắt animation hệ thống thì trả về [rest] cố định. */
@Composable
private fun rememberLoop(durationMs: Int, enabled: Boolean, rest: Float = 0f): State<Float> {
    if (!enabled) return remember { mutableStateOf(rest) }
    return rememberInfiniteTransition(label = "loop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "loop$durationMs",
    )
}

@Composable
private fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/* ───────────────────────────── Screen ───────────────────────────── */

@Composable
fun LanguageScreen(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Phần nội dung cuộn được (máy màn hình nhỏ), nút "Tiếp tục" luôn ghim phía dưới.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        ) {
            BasicText(
                "AUTOLUNEX",
                style = type(16f, FontWeight.ExtraBold, Alx.Ink, spacing = 0.1f),
            )

            PreviewPanel(selected.preview(), Modifier.padding(top = 22.dp))

            BasicText(
                "Chọn ngôn ngữ",
                Modifier.padding(top = 26.dp),
                style = type(30f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.03f, lineHeight = 1.1f),
            )
            BasicText(
                "Vui lòng chọn ngôn ngữ hiển thị bạn muốn sử dụng.",
                Modifier.padding(top = 6.dp),
                style = type(14.5f, FontWeight.Medium, Alx.Ink2, lineHeight = 1.5f),
            )

            Column(
                Modifier
                    .padding(top = 18.dp, bottom = 16.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LanguageOption(
                    badge = "VI", title = "Tiếng Việt", subtitle = "Giao diện Tiếng Việt",
                    solidBadge = true,
                    selected = selected == AppLanguage.VI,
                    onClick = { onSelect(AppLanguage.VI) },
                )
                LanguageOption(
                    badge = "EN", title = "English", subtitle = "English interface",
                    solidBadge = false,
                    selected = selected == AppLanguage.EN,
                    onClick = { onSelect(AppLanguage.EN) },
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                "Bạn có thể đổi lại trong Hồ sơ › Cài đặt tài khoản",
                style = type(12.5f, FontWeight.Medium, Alx.Muted, align = TextAlign.Center),
            )
            PrimaryButton("Tiếp tục", onContinue)
        }
    }
}

/* ───────────────────────────── Preview panel ───────────────────────────── */

@Composable
private fun PreviewPanel(copy: PreviewCopy, modifier: Modifier = Modifier) {
    val on = animationsEnabled()
    val shape = RoundedCornerShape(26.dp)

    val globe by rememberLoop(9000, on, rest = 0.1f)
    val orbit by rememberLoop(10000, on, rest = 0.62f)
    val bubbles by rememberLoop(7000, on, rest = 0.6f)
    val floatT by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f,
        targetValue = if (on) 1f else 0f,
        animationSpec = infiniteRepeatable(
            tween(2500, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
            RepeatMode.Reverse,
        ),
        label = "floatT",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(204.dp)
            .clip(shape)
            .background(Alx.BlueSoft)
            .border(1.dp, Alx.PanelBorder, shape),
    ) {
        Globe(Modifier.fillMaxSize(), globe, orbit)

        // Thẻ chào nổi lên xuống 5dp
        val cardShape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .padding(start = 16.dp, top = 20.dp)
                .width(236.dp)
                .graphicsLayer { translationY = -5.dp.toPx() * floatT }
                .shadow(
                    10.dp, cardShape,
                    ambientColor = Color(0x1A0F172A), spotColor = Color(0x4D0F172A),
                )
                .background(Alx.White, cardShape)
                .border(1.dp, Alx.Line, cardShape)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        ) {
            val six = with(LocalDensity.current) { 6.dp.roundToPx() }
            AnimatedContent(
                targetState = copy,
                transitionSpec = {
                    (fadeIn(tween(350)) + slideInVertically(tween(350)) { six }) togetherWith fadeOut(tween(1))
                },
                label = "previewCopy",
            ) { c ->
                Column {
                    BasicText(c.tag, style = type(10.5f, FontWeight.ExtraBold, Alx.Blue, spacing = 0.1f))
                    BasicText(
                        c.greet, Modifier.padding(top = 6.dp),
                        style = type(30f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.03f, lineHeight = 1.1f),
                    )
                    BasicText(
                        c.welcome, Modifier.padding(top = 4.dp),
                        style = type(12.5f, FontWeight.Medium, Alx.Muted),
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.tabs.forEach { tab ->
                            BasicText(
                                tab,
                                Modifier
                                    .background(Alx.Chip, CircleShape)
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                style = type(11f, FontWeight.Bold, Alx.ChipText),
                            )
                        }
                    }
                }
            }
        }

        // Ba chấm "đang gõ" rồi bong bóng trả lời bật ra (chu kỳ 7s)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 20.dp),
        ) {
            TypingBubble(bubbles)
            ReplyBubble(copy.reply, bubbles)
        }
    }
}

private val BubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)

@Composable
private fun TypingBubble(p: Float) {
    val alpha = keys(p, 0f to 0f, 0.02f to 0f, 0.08f to 1f, 0.26f to 1f, 0.32f to 0f, 1f to 0f)
    val y = keys(p, 0f to 8f, 0.02f to 8f, 0.08f to 0f, 0.26f to 0f, 0.32f to -4f, 1f to -4f)
    val s = keys(p, 0f to 0.9f, 0.02f to 0.9f, 0.08f to 1f, 0.26f to 1f, 0.32f to 0.96f, 1f to 0.96f)
    Row(
        Modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                this.alpha = alpha
                translationY = y.dp.toPx()
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(1f, 1f)
            }
            .background(Alx.White, BubbleShape)
            .border(1.dp, Alx.Line, BubbleShape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val dots by rememberLoop(1000, animationsEnabled(), rest = 0.3f)
        listOf(0f, 0.15f, 0.3f).forEach { delay ->
            val ph = ((dots - delay) % 1f + 1f) % 1f
            val dy = keys(ph, 0f to 0f, 0.3f to -3f, 0.6f to 0f, 1f to 0f)
            val da = keys(ph, 0f to 0.45f, 0.3f to 1f, 0.6f to 0.45f, 1f to 0.45f)
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { translationY = dy.dp.toPx(); this.alpha = da }
                    .background(Alx.Blue, CircleShape),
            )
        }
    }
}

@Composable
private fun ReplyBubble(text: String, p: Float) {
    val alpha = keys(p, 0f to 0f, 0.30f to 0f, 0.38f to 1f, 0.88f to 1f, 0.96f to 0f, 1f to 0f)
    val y = keys(p, 0f to 10f, 0.30f to 10f, 0.38f to 0f, 0.88f to 0f, 0.96f to -4f, 1f to -4f)
    val s = keys(p, 0f to 0.88f, 0.30f to 0.88f, 0.38f to 1.04f, 0.42f to 1f, 0.88f to 1f, 0.96f to 0.96f, 1f to 0.96f)
    BasicText(
        text,
        Modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = y.dp.toPx()
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(1f, 1f)
            }
            .shadow(
                8.dp, BubbleShape,
                ambientColor = Color(0x4D1F4BD8), spotColor = Color(0xB31F4BD8),
            )
            .background(Alx.Blue, BubbleShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        style = type(13f, FontWeight.Bold, Alx.White),
    )
}

/* ───────────────────────────── Globe (quay chậm + chấm chạy quanh) ───────────────────────────── */

@Composable
private fun Globe(modifier: Modifier, phase: Float, orbit: Float) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val cx = size.width - 58.dp.toPx()   // 292dp trên khung 350dp
        val cy = 108.dp.toPx()
        val r = 106.dp.toPx()
        val stroke = Stroke(width = 1.4.dp.toPx())

        drawCircle(Alx.Globe, r, Offset(cx, cy), style = stroke)

        // Kinh tuyến: 3 elip đổi bán kính ngang để tạo cảm giác quay
        val ease = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
        val tri = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
        val k = ease.transform(tri)
        val rxA = 8f + 92f * k
        val rxB = 100f - 92f * k
        val rxC = when {
            phase < 0.25f -> 54f + 46f * (phase / 0.25f)
            phase < 0.75f -> 100f - 92f * ((phase - 0.25f) / 0.5f)
            else -> 8f + 46f * ((phase - 0.75f) / 0.25f)
        }
        for (rxDp in floatArrayOf(rxA, rxB, rxC)) {
            val rx = rxDp.dp.toPx()
            drawOval(Alx.Globe, Offset(cx - rx, cy - r), Size(rx * 2, r * 2), style = stroke)
        }

        // Vĩ tuyến
        fun parallel(yDp: Float, halfDp: Float) {
            val y = yDp.dp.toPx(); val h = halfDp.dp.toPx()
            drawLine(Alx.Globe, Offset(cx - h, y), Offset(cx + h, y), strokeWidth = 1.4.dp.toPx())
        }
        parallel(108f, 106f)
        parallel(60f, 88f); parallel(156f, 88f)
        parallel(26f, 66f); parallel(190f, 66f)

        // Chấm xanh chạy quanh quả cầu (xuất phát bên trái, đi xuống dưới rồi sang phải)
        val a = (PI - 2.0 * PI * orbit).toFloat()
        val p = Offset(cx + r * cos(a), cy + r * sin(a))
        drawCircle(Alx.Blue.copy(alpha = 0.15f), 9.dp.toPx(), p)
        drawCircle(Alx.Blue, 4.5.dp.toPx(), p)
    }
}

/* ───────────────────────────── Language option card ───────────────────────────── */

@Composable
private fun LanguageOption(
    badge: String,
    title: String,
    subtitle: String,
    solidBadge: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(150), label = "ring")
    val bg by animateColorAsState(if (selected) Alx.BlueSelected else Alx.White, tween(150), label = "bg")
    val border by animateColorAsState(if (selected) Alx.Blue else Alx.Line, tween(150), label = "border")

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (ring > 0f) {
                    val g = 4.dp.toPx()
                    drawRoundRect(
                        Alx.Blue.copy(alpha = 0.08f * ring),
                        topLeft = Offset(-g, -g),
                        size = Size(size.width + 2 * g, size.height + 2 * g),
                        cornerRadius = CornerRadius(20.dp.toPx() + g),
                    )
                }
            }
            .background(bg, shape)
            .border(1.5.dp, border, shape)
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(if (solidBadge) Alx.Blue else Alx.BlueSoft, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                badge,
                style = type(14f, FontWeight.ExtraBold, if (solidBadge) Alx.White else Alx.Blue, spacing = 0.02f),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(title, style = type(16f, FontWeight.Bold, Alx.Ink))
            BasicText(subtitle, style = type(12.5f, FontWeight.Medium, Alx.Muted))
        }
        RadioCheck(selected)
    }
}

@Composable
private fun RadioCheck(selected: Boolean) {
    val fill by animateColorAsState(if (selected) Alx.Blue else Alx.White, tween(150), label = "fill")
    val edge by animateColorAsState(if (selected) Alx.Blue else Alx.RadioOff, tween(150), label = "edge")
    val tick by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tick",
    )
    Box(
        Modifier
            .size(24.dp)
            .background(fill, CircleShape)
            .border(1.5.dp, edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(14.dp)
                .graphicsLayer { scaleX = tick; scaleY = tick; alpha = tick.coerceIn(0f, 1f) },
        ) {
            val s = size.width / 24f
            val path = Path().apply {
                moveTo(5f * s, 12.5f * s); lineTo(9.5f * s, 17f * s); lineTo(19f * s, 7.5f * s)
            }
            drawPath(
                path, Alx.White,
                style = Stroke(width = 3.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/* ───────────────────────────── Primary button ───────────────────────────── */

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "btn")
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                12.dp, shape,
                ambientColor = Color(0x4D1F4BD8), spotColor = Color(0xB31F4BD8),
            )
            .background(Alx.Blue, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = type(16f, FontWeight.Bold, Alx.White))
    }
}

/* ───────────────────────────── Route + Preview ───────────────────────────── */

/**
 * Màn hình Chọn ngôn ngữ chính được gọi từ CayXuNavGraph.
 * Đồng bộ với LanguageState và chuyển màn khi bấm Tiếp tục.
 */
@Composable
fun LanguageSelectionScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var lang by rememberSaveable { 
        mutableStateOf(
            if (LanguageState.language == com.cayxu.app.ui.locale.AppLanguage.EN) AppLanguage.EN else AppLanguage.VI
        ) 
    }
    LanguageScreen(
        selected = lang,
        onSelect = {
            lang = it
            LanguageState.setLanguage(
                context,
                if (it == AppLanguage.EN) com.cayxu.app.ui.locale.AppLanguage.EN else com.cayxu.app.ui.locale.AppLanguage.VI
            )
        },
        onContinue = {
            LanguageState.setLanguage(
                context,
                if (lang == AppLanguage.EN) com.cayxu.app.ui.locale.AppLanguage.EN else com.cayxu.app.ui.locale.AppLanguage.VI
            )
            onFinished()
        }
    )
}

/**
 * Ví dụ nối vào app. Khi bấm "Tiếp tục", lưu ngôn ngữ rồi sang màn nhập key:
 *   AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
 */
@Composable
fun LanguageRoute(onDone: (AppLanguage) -> Unit) {
    var lang by rememberSaveable { mutableStateOf(AppLanguage.VI) }
    LanguageScreen(selected = lang, onSelect = { lang = it }, onContinue = { onDone(lang) })
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LanguageScreenPreview() {
    var lang by remember { mutableStateOf(AppLanguage.VI) }
    LanguageScreen(selected = lang, onSelect = { lang = it }, onContinue = {})
}
