package com.cayxu.app.ui.design

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import kotlinx.coroutines.delay

/*
 * Thiết kế dùng chung cho mọi màn của AUTOLUNEX: màu, MỘT font duy nhất (Manrope), kiểu chữ,
 * hàm animation, nút chính, icon vẽ từ path SVG.
 */

/* ───────────────────────────── Màu ───────────────────────────── */

internal object Alx {
    val Bg = Color(0xFFF3F5F9)
    val Ink = Color(0xFF0F172A)
    val Ink2 = Color(0xFF475569)
    val Muted = Color(0xFF64748B)
    val Placeholder = Color(0xFF94A3B8)
    val Blue = Color(0xFF1F4BD8)
    val BlueSoft = Color(0xFFE8EEFD)
    val BlueSelected = Color(0xFFF5F8FF)
    val PanelBorder = Color(0xFFDCE5FA)
    val Globe = Color(0xFFB9CBF3)
    val Line = Color(0xFFE4E8F0)
    val Divider = Color(0xFFEAEEF4)
    val InputBorder = Color(0xFFD5DBE7)
    val RadioOff = Color(0xFFCBD2DF)
    val Chip = Color(0xFFF1F5F9)
    val ChipText = Color(0xFF334155)
    val Danger = Color(0xFFC9363B)
    val DangerBg = Color(0xFFFFF8F8)
    val White = Color.White
}

/* ───────────────────────────── Font: Manrope (dùng chung toàn app) ───────────────────────────── */

/**
 * Manrope dạng VARIABLE FONT: chỉ cần 1 file `res/font/manrope.ttf`
 * (tải "Manrope[wght].ttf" từ Google Fonts rồi đổi tên thành manrope.ttf).
 * Mỗi độ đậm được chọn bằng trục wght. (Android 8.0 / API 26 trở lên.)
 */
@OptIn(ExperimentalTextApi::class)
private fun manropeWeight(w: FontWeight) = Font(
    resId = R.font.manrope,
    weight = w,
    variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
)

internal val Manrope = FontFamily(
    manropeWeight(FontWeight.Medium),
    manropeWeight(FontWeight.SemiBold),
    manropeWeight(FontWeight.Bold),
    manropeWeight(FontWeight.ExtraBold),
)

/** Kiểu chữ theo số đo thiết kế: size (sp), letterSpacing và lineHeight tính theo em. */
internal fun type(
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

/* ───────────────────────────── Animation ───────────────────────────── */

internal fun smooth(x: Float) = x * x * (3f - 2f * x)

/** Nội suy theo các mốc (giống @keyframes). Mỗi đoạn dùng [ease] (mặc định smoothstep). */
internal fun keys(
    t: Float,
    vararg p: Pair<Float, Float>,
    ease: (Float) -> Float = ::smooth,
): Float {
    if (t <= p.first().first) return p.first().second
    for (i in 0 until p.size - 1) {
        val (t0, v0) = p[i]
        val (t1, v1) = p[i + 1]
        if (t <= t1) {
            val f = if (t1 == t0) 1f else (t - t0) / (t1 - t0)
            return v0 + (v1 - v0) * ease(f)
        }
    }
    return p.last().second
}

internal val EaseOutSoft = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
internal val EaseInOutCss = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
internal val EaseScroll = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/** Vòng lặp 0..1. Khi người dùng tắt animation hệ thống thì trả về [rest] cố định. */
@Composable
internal fun rememberLoop(durationMs: Int, enabled: Boolean, rest: Float = 0f): State<Float> {
    if (!enabled) return remember { mutableStateOf(rest) }
    return rememberInfiniteTransition(label = "loop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "loop$durationMs",
    )
}

@Composable
internal fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** Hiện dần + trượt lên 14dp một lần khi vào màn (cùng nhịp với bản HTML). */
@Composable
internal fun Modifier.rise(delayMs: Int): Modifier {
    val on = animationsEnabled()
    val a = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (on) {
            delay(delayMs.toLong())
            a.animateTo(1f, tween(500, easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)))
        }
    }
    return graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 14.dp.toPx()
    }
}

/* ───────────────────────────── Icon từ path SVG (khung 24×24) ───────────────────────────── */

@Composable
internal fun SvgIcon(
    paths: List<String>,
    size: Dp,
    color: Color,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        scale(k, Offset.Zero) {
            paths.forEach { d ->
                drawPath(
                    PathParser().parsePathString(d).toPath(),
                    color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/* ───────────────────────────── Nút chính ───────────────────────────── */

@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(radius)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "btn")
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = if (enabled) 1f else 0.7f
            }
            .shadow(
                12.dp, shape,
                ambientColor = Color(0x4D1F4BD8), spotColor = Color(0xB31F4BD8),
            )
            .background(Alx.Blue, shape)
            .clip(shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = type(16f, FontWeight.Bold, Alx.White))
    }
}
