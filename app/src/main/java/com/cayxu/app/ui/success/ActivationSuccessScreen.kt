package com.cayxu.app.ui.success

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.animationsEnabled
import com.cayxu.app.ui.design.keys
import com.cayxu.app.ui.design.type
import kotlinx.coroutines.delay

/* ───────────────────────────── Lịch chuyển động (ms) – khớp KeySuccess.dc.html ───────────────────────────── */

private const val T_END = 5_800f

private val EaseText = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val EasePop = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)
private val EaseZoom = CubicBezierEasing(0.65f, 0f, 0.2f, 1f)

/** Tiến độ 0..1 của đoạn [a, b] ms tại thời điểm [t], có easing. */
private fun prog(t: Float, a: Float, b: Float, e: Easing = LinearEasing): Float =
    e.transform(((t - a) / (b - a)).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p

/* ───────────────────────────── Màu / đường icon riêng của màn này ───────────────────────────── */

private val PinkBg = Color(0xFFFDEAF2)
private val PinkFg = Color(0xFFC2255C)
private val TealBg = Color(0xFFE3F4F8)
private val TealFg = Color(0xFF0E7490)
private val Red = Color(0xFFE5484D)

// Khung 24×24. Cờ cung (arc flags) luôn viết cách nhau để PathParser đọc đúng.
private const val LIKE_PATH =
    "M7 11v9H4a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1h3z M9 11l3.6-7.2A1.8 1.8 0 0 1 14.5 5v4h4.6a2 2 0 0 1 2 2.3l-1.1 7A2 2 0 0 1 18 20H9z"
private const val HEART_PATH =
    "M12 20.5C6 16 3 12.8 3 9.2C3 6.6 5 4.8 7.3 4.8c1.7 0 3.2 1 4.7 2.8c1.5-1.8 3-2.8 4.7-2.8C19 4.8 21 6.6 21 9.2c0 3.6-3 6.8-9 11.3z"
private const val HEX_PATH = "M12 3l7.8 4.5v9L12 21l-7.8-4.5v-9z"
private val UserPlusPaths = listOf(
    "M6.6 8 A3.4 3.4 0 1 0 13.4 8 A3.4 3.4 0 1 0 6.6 8 Z",
    "M3.5 19c.6-3.6 3.3-5.5 6.5-5.5s5.9 1.9 6.5 5.5M19 8v6M16 11h6",
)
private val CheckPaths = listOf("M5 12.5l4.5 4.5L19 7.5")

/** Icon tô đặc (SvgIcon trong AlxDesign chỉ vẽ nét viền). */
@Composable
private fun FilledIcon(path: String, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, Offset.Zero) {
            drawPath(PathParser().parsePathString(path).toPath(), color)
        }
    }
}

/* ───────────────────────────── Màn chính ───────────────────────────── */

/**
 * Màn "Kích hoạt thành công / Chào mừng", chạy MỘT lần rồi vào Trang chủ.
 *   0 → 3,8 s    lời chào + 5 phần tử trang trí
 *   3,4 → 4,3 s  điện thoại mini (= Trang chủ thật) trồi lên
 *   4,5 → 5,8 s  phóng to thành Trang chủ toàn màn hình, rồi gọi [onFinished]
 *
 * @param home Trang chủ thật của app (HomeScreen). Màn này không vẽ lại bản sao.
 * @param username tên hiển thị; avatar lấy 2 chữ cái đầu viết hoa.
 */
@Composable
fun ActivationSuccessScreen(
    username: String,
    daysLeft: Int,
    onFinished: () -> Unit,
    home: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = animationsEnabled()
    val time = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (on) {
            time.animateTo(T_END, tween(T_END.toInt(), easing = LinearEasing))
        } else {
            time.snapTo(1_000f)        // chỉ hiện tĩnh lời chào ~0,5 s
            delay(500)
            time.snapTo(T_END)
        }
        done = true
        onFinished()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Alx.Bg)) {
        val u = maxWidth.value / 390f                       // hệ số co theo bề rộng màn hình thật
        val stageHeight = (maxHeight.value / u).dp
        val t = { time.value }

        // Sân khấu thiết kế 390dp rộng, co đều theo [u]: lời chào + khung điện thoại mini.
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(390.dp, stageHeight)
                .graphicsLayer { scaleX = u; scaleY = u; transformOrigin = TransformOrigin(0f, 0f) },
        ) {
            Welcome(username, daysLeft, t)
            PhoneBezel(t)
        }

        // Trang chủ thật: tải sẵn từ đầu, ẩn cho đến 3,3 s; thu 61,54% rồi phóng to.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (done) Modifier else Modifier.graphicsLayer { applyHomeTransform(t(), u) }),
        ) { home() }

        // Chặn chạm trong lúc đang chuyển cảnh.
        if (!done) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown().consume() }
                },
            )
        }
    }
}

/** Cắt đáy + bo góc theo tiến độ phóng to (inset(0 0 cut 0 round r) của bản HTML). */
private class HomeClip(private val cutPx: Float, private val radiusPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(0f, 0f, size.width, (size.height - cutPx).coerceAtLeast(1f), CornerRadius(radiusPx)),
        )
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyHomeTransform(t: Float, u: Float) {
    if (t < 3_300f) { alpha = 0f; return }
    val rise = prog(t, 3_400f, 4_300f, EaseOutSoft)
    val zoom = prog(t, 4_500f, 5_800f, EaseZoom)
    val k = lerp(0.6154f, 1f, zoom)
    scaleX = k; scaleY = k
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = lerp(75.dp.toPx() * u, 0f, zoom)
    translationY = lerp(150.dp.toPx() * u, 0f, zoom) + (1f - rise) * 760.dp.toPx() * u
    // Cửa sổ nhìn thấy cao 533dp (cố định); phần còn lại của Home bị cắt ở đáy.
    val cut = (size.height - 533.dp.toPx()).coerceAtLeast(0f) * (1f - zoom)
    shape = HomeClip(cut, lerp(49.dp.toPx(), 0f, zoom))
    clip = true
}

/** Khung đen của điện thoại mini: trồi lên cùng Trang chủ, mờ đi lúc phóng to. */
@Composable
private fun PhoneBezel(t: () -> Float) {
    Box(
        Modifier
            .offset(69.dp, 144.dp)
            .size(252.dp, 340.dp)
            .graphicsLayer {
                val now = t()
                if (now < 3_300f) { alpha = 0f; return@graphicsLayer }
                translationY = (1f - prog(now, 3_400f, 4_300f, EaseOutSoft)) * 760.dp.toPx()
                alpha = 1f - prog(now, 4_500f, 5_100f)
            }
            .shadow(
                16.dp, RoundedCornerShape(36.dp),
                ambientColor = Color(0x330F172A), spotColor = Color(0x800F172A),
            )
            .background(Alx.Ink, RoundedCornerShape(36.dp)),
    )
}

/* ───────────────────────────── Pha 1: lời chào ───────────────────────────── */

@Composable
private fun Welcome(username: String, daysLeft: Int, t: () -> Float) {
    val topInset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val now = t()
                val inP = prog(now, 0f, 400f, EaseText)
                val outP = prog(now, 3_240f, 3_840f, EaseText)
                alpha = inP * (1f - outP)
                val s = lerp(0.92f, 1f, inP) * lerp(1f, 0.98f, outP)
                scaleX = s; scaleY = s
                translationY = -24.dp.toPx() * outP
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            Box(Modifier.padding(top = topInset)) {
                BasicText("AUTOLUNEX", style = type(16f, FontWeight.ExtraBold, Alx.Ink, spacing = 0.1f))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Hero(username, t)
                ProPill(daysLeft, Modifier.padding(top = 22.dp).textIn(t, 0f))
                Greeting(username, Modifier.padding(top = 14.dp).textIn(t, 120f))
                BasicText(
                    "Mọi nhiệm vụ follow, like và nuôi tài khoản đã sẵn sàng cho bạn.",
                    Modifier.padding(top = 8.dp).widthIn(max = 270.dp).textIn(t, 240f),
                    style = type(13.5f, FontWeight.Medium, Alx.Muted, lineHeight = 1.5f, align = TextAlign.Center),
                )
            }
        }
        Decorations(t)
    }
}

/** Hiện dần + trượt lên 12dp, từ 0,6 s (+ [delayMs]) trong 0,72 s. */
private fun Modifier.textIn(t: () -> Float, delayMs: Float): Modifier = graphicsLayer {
    val p = prog(t(), 600f + delayMs, 1_320f + delayMs, EaseText)
    alpha = p
    translationY = 12.dp.toPx() * (1f - p)
}

@Composable
private fun Greeting(username: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Alx.Blue)) { append(username) }
                append(" ơi,")
            },
            style = type(18f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f, lineHeight = 1.35f, align = TextAlign.Center),
            maxLines = 1, softWrap = false,
        )
        BasicText(
            "chào mừng bạn đến với AUTOLUNEX",
            style = type(18f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f, lineHeight = 1.35f, align = TextAlign.Center),
            maxLines = 1, softWrap = false,
        )
    }
}

@Composable
private fun ProPill(daysLeft: Int, modifier: Modifier) {
    Row(
        modifier
            .background(Alx.White, RoundedCornerShape(999.dp))
            .border(1.dp, Alx.PanelBorder, RoundedCornerShape(999.dp))
            .padding(start = 9.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(18.dp).background(Alx.Blue, CircleShape), contentAlignment = Alignment.Center) {
            SvgIcon(CheckPaths, 11.dp, Color.White, 3.4f)
        }
        BasicText(
            "Bản quyền PRO đã kích hoạt · còn $daysLeft ngày",
            style = type(12.5f, FontWeight.Bold, Alx.Blue),
            maxLines = 1, softWrap = false,
        )
    }
}

/** Hero 176dp: 2 vòng mờ cố định; đĩa avatar + huy hiệu tick bật lên, tick vẽ dần. */
@Composable
private fun Hero(username: String, t: () -> Float) {
    val initials = username.trim().take(2).uppercase().ifEmpty { "US" }
    Box(Modifier.size(176.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(176.dp)) {
            val k = size.width / 176f
            drawCircle(Alx.PanelBorder, 86f * k, center, style = Stroke(1.5f * k))
            drawCircle(Color(0xFFC9D6F6), 70f * k, center, style = Stroke(1.5f * k))
        }
        Box(
            Modifier
                .size(176.dp)
                .graphicsLayer {
                    val now = t()
                    val s = keys(now, 0f to 0.7f, 720f to 1.06f, 1_080f to 1f, ease = EaseOutSoft::transform)
                    scaleX = s; scaleY = s
                    alpha = prog(now, 0f, 720f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(176.dp)) {
                val k = size.width / 176f
                drawCircle(Alx.BlueSoft, 58f * k, center)
                drawCircle(Color.White, 46f * k, center)
                drawCircle(Alx.PanelBorder, 46f * k, center, style = Stroke(1.5f * k))
                drawCircle(Alx.Blue, 40f * k, center)
                // huy hiệu tick ở (124,124)
                val bc = Offset(124f * k, 124f * k)
                drawCircle(Color.White, 15f * k, bc)
                drawCircle(Alx.Blue, 11.5f * k, bc)
                val tick = Path().apply {
                    moveTo(118.5f * k, 124.5f * k); lineTo(122.5f * k, 128.5f * k); lineTo(130.5f * k, 119.5f * k)
                }
                val m = PathMeasure().apply { setPath(tick, false) }
                val seg = Path()
                m.getSegment(0f, m.length * prog(t(), 360f, 1_200f, EaseOutSoft), seg, true)
                drawPath(seg, Color.White, style = Stroke(3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            BasicText(initials, style = type(30f, FontWeight.ExtraBold, Color.White, spacing = -0.02f))
        }
    }
}

/* ───────────────────────────── 5 phần tử trang trí (trên 2 · giữa 1 · dưới 2) ───────────────────────────── */

@Composable
private fun Decorations(t: () -> Float) {
    // (x, y) = góc trên-trái trên khung thiết kế 390×844; thứ tự hiện: 0,6 · 0,96 · 1,14 · 1,32 · 1,68 s
    Deco(17f, 87f, 600f, t) { AvatarBubble(58.dp, PinkBg, PinkFg, "TH", LIKE_PATH, Alx.Blue) }
    Deco(317f, 169f, 960f, t) { CircleBadge(42.dp, Alx.Blue) { FilledIcon(LIKE_PATH, 23.dp, Color.White) } }
    Deco(10f, 300f, 1_140f, t) { InfoChip("+25 xu") { FilledIcon(HEX_PATH, 15.dp, Alx.Blue) } }
    Deco(226f, 748f, 1_320f, t) { InfoChip("+ Follow") { SvgIcon(UserPlusPaths, 15.dp, Alx.Blue, 2f) } }
    Deco(66f, 674f, 1_680f, t) { AvatarBubble(52.dp, TealBg, TealFg, "MK", HEART_PATH, Red) }
}

/** Phóng to 0,2 → 1 và hiện dần trong 0,6 s rồi ĐỨNG YÊN (không lơ lửng, không nảy). */
@Composable
private fun Deco(x: Float, y: Float, startMs: Float, t: () -> Float, content: @Composable () -> Unit) {
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .graphicsLayer {
                val p = prog(t(), startMs, startMs + 600f, EasePop)
                val s = lerp(0.2f, 1f, p)
                scaleX = s; scaleY = s; alpha = p
            },
    ) { content() }
}

private val DecoShadow = Color(0x4D0F172A)

@Composable
private fun CircleBadge(size: Dp, bg: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .shadow(10.dp, CircleShape, ambientColor = Color(0x1F0F172A), spotColor = DecoShadow)
            .background(Color.White, CircleShape)
            .padding(3.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AvatarBubble(size: Dp, bg: Color, fg: Color, text: String, badgePath: String, badgeBg: Color) {
    Box(Modifier.size(size)) {
        CircleBadge(size, bg) {
            BasicText(text, style = type(size.value * 0.3f, FontWeight.ExtraBold, fg))
        }
        Box(Modifier.align(Alignment.BottomEnd).offset(6.dp, 4.dp)) {
            Box(
                Modifier.size(26.dp).background(Color.White, CircleShape).padding(3.dp).background(badgeBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) { FilledIcon(badgePath, 13.dp, Color.White) }
        }
    }
}

@Composable
private fun InfoChip(label: String, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .shadow(10.dp, shape, ambientColor = Color(0x1A0F172A), spotColor = DecoShadow)
            .background(Color.White, shape)
            .border(1.dp, Alx.Line, shape)
            .padding(start = 9.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        BasicText(label, style = type(12f, FontWeight.Bold, Alx.ChipText), maxLines = 1, softWrap = false)
    }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ActivationSuccessPreview() {
    ActivationSuccessScreen(
        username = "username", daysLeft = 25, onFinished = {},
        home = { Box(Modifier.fillMaxSize().background(Alx.Bg)) },
    )
}
