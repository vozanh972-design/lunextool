package com.cayxu.app.ui.verify

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.animationsEnabled
import com.cayxu.app.ui.design.rememberLoop
import com.cayxu.app.ui.design.type
import kotlinx.coroutines.delay

/** Một màn, 3 trạng thái. */
enum class VerifyState { Checking, Success, Error }

private val DangerSoft = Color(0xFFFDECEC)

/**
 * Màn chờ kiểm tra key. CHỈ có: ổ khóa + một dòng chữ.
 *  - Checking: ổ khóa có cung xanh xoay, chữ "Đang kiểm tra…".
 *  - Success : quai khóa mở, ổ khóa thu nhỏ biến mất, dấu tích vẽ ra, chữ xanh "Xác thực thành công".
 *  - Error   : ổ khóa rung, lõi chuyển đỏ, dấu X trắng vẽ ra, chữ đỏ "Key không hợp lệ".
 */
@Composable
fun VerifyingScreen(
    state: VerifyState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText("AUTOLUNEX", style = type(16f, FontWeight.ExtraBold, Alx.Ink, spacing = 0.1f))
            BasicText(
                "Bản quyền 2026",
                Modifier.background(Alx.BlueSoft, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                style = type(12f, FontWeight.Bold, Alx.Blue),
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LockHero(state)

            // Khung chữ cao cố định 26dp để ổ khóa không nhảy khi đổi chữ.
            Box(Modifier.padding(top = 26.dp).width(260.dp).height(26.dp), contentAlignment = Alignment.Center) {
                Crossfade(state, animationSpec = tween(250), label = "label") { s ->
                    val (text, color) = when (s) {
                        VerifyState.Checking -> "Đang kiểm tra…" to Alx.Ink
                        VerifyState.Success -> "Xác thực thành công" to Alx.Blue
                        VerifyState.Error -> "Key không hợp lệ" to Alx.Danger
                    }
                    BasicText(text, style = type(18f, FontWeight.Bold, color, spacing = -0.01f, align = TextAlign.Center))
                }
            }
        }
    }
}

/**
 * Hero 144dp (vẽ trên hệ 176 đơn vị như bản thiết kế).
 * Trạng thái đổi thì chạy đúng một lần, không lặp (trừ cung xoay lúc đang kiểm tra).
 */
@Composable
private fun LockHero(state: VerifyState, modifier: Modifier = Modifier) {
    val on = animationsEnabled()
    val spin by rememberLoop(1_400, on && state == VerifyState.Checking, rest = 0f)

    val arc = remember { Animatable(1f) }      // cung xanh
    val lift = remember { Animatable(0f) }     // quai khóa mở
    val lockOut = remember { Animatable(0f) }  // ổ khóa thu nhỏ + mờ
    val tick = remember { Animatable(0f) }     // 0..1 vẽ dấu tích
    val red = remember { Animatable(0f) }      // nền chuyển đỏ
    val xa = remember { Animatable(0f) }
    val xb = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }    // -5..5 (đơn vị thiết kế)

    LaunchedEffect(state, on) {
        when (state) {
            VerifyState.Checking -> {
                arc.snapTo(1f); lift.snapTo(0f); lockOut.snapTo(0f); tick.snapTo(0f)
                red.snapTo(0f); xa.snapTo(0f); xb.snapTo(0f); shake.snapTo(0f)
            }
            VerifyState.Success -> {
                if (!on) { arc.snapTo(0f); lockOut.snapTo(1f); tick.snapTo(1f); return@LaunchedEffect }
                arc.animateTo(0f, tween(250))
                lift.animateTo(1f, tween(480, easing = EaseOutSoft))
                lockOut.animateTo(1f, tween(450))
                tick.animateTo(1f, tween(700, easing = EaseOutSoft))
            }
            VerifyState.Error -> {
                if (!on) { arc.snapTo(0f); lockOut.snapTo(1f); red.snapTo(1f); xa.snapTo(1f); xb.snapTo(1f); return@LaunchedEffect }
                arc.animateTo(0f, tween(250))
                for (v in floatArrayOf(-5f, 5f, -4f, 3f, 0f)) shake.animateTo(v, tween(70))
                red.animateTo(1f, tween(450))
                lockOut.animateTo(1f, tween(450))
                xa.animateTo(1f, tween(450, easing = EaseOutSoft))
                xb.animateTo(1f, tween(450, easing = EaseOutSoft))
            }
        }
    }

    val desc = when (state) {
        VerifyState.Checking -> "Đang kiểm tra"
        VerifyState.Success -> "Xác thực thành công"
        VerifyState.Error -> "Key không hợp lệ"
    }
    Canvas(modifier.size(144.dp).semantics { contentDescription = desc }) {
        val k = size.width / 176f
        val c = Offset(88f * k, 88f * k)

        drawCircle(Alx.PanelBorder, 86f * k, c, style = Stroke(1.5f * k))
        drawCircle(Color(0xFFC9D6F6), 70f * k, c, style = Stroke(1.5f * k))
        drawCircle(Alx.BlueSoft, 58f * k, c)
        drawCircle(DangerSoft, 58f * k, c, alpha = red.value)

        if (arc.value > 0.01f) {
            rotate(spin * 360f, c) {
                drawArc(
                    Alx.Blue.copy(alpha = arc.value), startAngle = -90f, sweepAngle = 76f / 364.4f * 360f, useCenter = false,
                    topLeft = Offset(c.x - 58f * k, c.y - 58f * k), size = Size(116f * k, 116f * k),
                    style = Stroke(3f * k, cap = StrokeCap.Round),
                )
            }
        }

        drawCircle(Alx.Blue, 42f * k, c)
        drawCircle(Alx.Danger, 42f * k, c, alpha = red.value)

        // Ổ khóa (dịch lên 4 đơn vị cho cân trong lõi), thu nhỏ quanh (88, 92)
        val lo = lockOut.value
        if (lo < 0.99f) {
            val s = 1f - 0.45f * lo
            translate(left = shake.value * k, top = -4f * k) {
                scale(s, s, Offset(88f * k, 92f * k)) {
                    drawLock(k, lift.value, 1f - lo)
                }
            }
        }

        // Dấu tích (xanh): M72 89 l11 11 l21 -23
        if (tick.value > 0.001f) {
            val p = Path().apply {
                moveTo(72f * k, 89f * k); lineTo(83f * k, 100f * k); lineTo(104f * k, 77f * k)
            }
            drawTrimmed(p, tick.value, Stroke(7f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Dấu X (đỏ): hai nét trắng
        if (xa.value > 0.001f) {
            val p = Path().apply { moveTo(77f * k, 77f * k); lineTo(99f * k, 99f * k) }
            drawTrimmed(p, xa.value, Stroke(7f * k, cap = StrokeCap.Round))
        }
        if (xb.value > 0.001f) {
            val p = Path().apply { moveTo(99f * k, 77f * k); lineTo(77f * k, 99f * k) }
            drawTrimmed(p, xb.value, Stroke(7f * k, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawLock(k: Float, lift: Float, alpha: Float) {
    // Quai: bật lên 8 rồi lật -26° quanh chân phải (100, 89)
    translate(top = -8f * k * lift) {
        rotate(-26f * lift, Offset(100f * k, 89f * k)) {
            val shackle = Path().apply {
                moveTo(76f * k, 89f * k)
                lineTo(76f * k, 81f * k)
                arcTo(Rect(76f * k, 69f * k, 100f * k, 93f * k), 180f, 180f, false)
                lineTo(100f * k, 89f * k)
            }
            drawPath(shackle, Color.White.copy(alpha = alpha), style = Stroke(5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    drawRoundRect(
        Color.White.copy(alpha = alpha), Offset(67f * k, 87f * k), Size(42f * k, 28f * k), CornerRadius(7f * k),
    )
    drawCircle(Alx.Blue.copy(alpha = alpha), 3.6f * k, Offset(88f * k, 99f * k))
    drawLine(Alx.Blue.copy(alpha = alpha), Offset(88f * k, 101f * k), Offset(88f * k, 108f * k), 3.2f * k, StrokeCap.Round)
}

/** Vẽ phần đầu path theo tỉ lệ [fraction] (hiệu ứng nét được vẽ ra dần). */
private fun DrawScope.drawTrimmed(path: Path, fraction: Float, stroke: Stroke) {
    val pm = PathMeasure().apply { setPath(path, false) }
    val seg = Path()
    pm.getSegment(0f, pm.length * fraction.coerceIn(0f, 1f), seg, true)
    drawPath(seg, Color.White, style = stroke)
}

/* ───────────────────────────── Route mẫu ───────────────────────────── */

/**
 * Nối vào app. Thay thế cả 2 màn "đang kiểm tra" và kết quả: một màn dùng chung.
 * [validate] gọi server (suspend), trả true nếu key đúng.
 * Đúng: hiện "Xác thực thành công" rồi gọi [onSuccess] (đi tới ActivationSuccessScreen / Trang chủ).
 * Sai: hiện dấu X đỏ rồi gọi [onFailure] (quay lại màn nhập key để báo lỗi).
 */
@Composable
fun VerifyingRoute(
    key: String,
    validate: suspend (String) -> Boolean,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
) {
    var state by remember { mutableStateOf(VerifyState.Checking) }
    LaunchedEffect(key) {
        state = VerifyState.Checking
        val t0 = System.currentTimeMillis()
        val ok = key.trim().length >= 8 && validate(key.trim())
        // Đợi tối thiểu ~1,2 s để người dùng kịp thấy ổ khóa
        (1_200 - (System.currentTimeMillis() - t0)).takeIf { it > 0 }?.let { delay(it) }
        if (ok) {
            state = VerifyState.Success
            delay(1_900)   // quai mở + thu nhỏ + vẽ tích + nghỉ một nhịp
            onSuccess()
        } else {
            state = VerifyState.Error
            delay(1_700)   // rung + đỏ + vẽ X + nghỉ một nhịp
            onFailure()
        }
    }
    VerifyingScreen(state)
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun VerifyingCheckingPreview() = VerifyingScreen(VerifyState.Checking)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun VerifyingSuccessPreview() = VerifyingScreen(VerifyState.Success)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun VerifyingErrorPreview() = VerifyingScreen(VerifyState.Error)
