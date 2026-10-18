package com.cayxu.app.ui.overlay

/*
 * CHỈ THAY GIAO DIỆN MÀN HÌNH NỔI (OVERLAY HIỆN ĐÈ LÊN APP KHÁC KHI ĐANG CHẠY NHIỆM VỤ)
 * — KHÔNG ĐỤNG VÀO LOGIC BÊN TRONG.
 *
 * File này chỉ vẽ lại UI đúng theo bản thiết kế canvas GolikeOverlay.dc.html (bản đầy đủ + bản thu
 * nhỏ dạng "chat-head" đậu sát mép, có dòng trạng thái kèm theo). KHÔNG có logic chạy nhiệm vụ nào ở
 * đây: không tự kiểm tra/lấy dữ liệu từ nick, không tự tạm dừng/tiếp tục thật, không tự lưu vị trí màn
 * nổi, không tự xin quyền SYSTEM_ALERT_WINDOW/overlay, không tự dùng WindowManager để nhúng cửa sổ hệ
 * thống. Mọi dữ liệu hiển thị vào qua [RunningOverlayUiState], mọi hành động đi ra qua các callback —
 * BẠN PHẢI TỰ NỐI LẠI FILE LOGIC THẬT (service đang chạy nhiệm vụ, nơi tạo/điều khiển overlay) VÀO
 * MÀN NỔI MỚI NÀY, file này không tự biết phải làm gì khi người dùng bấm nút.
 *
 * Cách dùng dự kiến: Service nền tạo MỘT system overlay window kiểu TYPE_APPLICATION_OVERLAY, kích
 * thước bằng cả màn hình thiết bị (MATCH_PARENT × MATCH_PARENT, FLAG_NOT_TOUCH_MODAL + nền trong suốt),
 * rồi đặt [RunningOverlayWindow] làm nội dung của nó. Vì khung bao bằng cả màn hình, việc kéo thả bản
 * thu nhỏ xử lý HOÀN TOÀN bằng Compose offset bên trong (không cần gọi WindowManager.updateViewLayout
 * theo từng khung hình) — đây là cách làm phổ biến cho overlay dạng "bong bóng nổi".
 *
 * HOẠT ẢNH: đã có theo đúng yêu cầu —
 *  - Mọi nút bấm (đổi vị trí / thu nhỏ / đóng / tạm dừng-tiếp tục / chạm vào bản thu nhỏ) đều có
 *    animation co nhẹ khi đè ngón tay vào (scale ~0.94, 110ms), giống các nút khác trong app.
 *  - Chuyển cảnh đầy đủ ⇄ thu nhỏ: fade + scale (180ms mở ra, 140ms thu lại).
 *  - Khi kéo bản thu nhỏ rồi thả tay: animate trượt mượt sang đúng mép đã chọn (260ms) rồi mới báo
 *    [onDockSideChange] — không bị "nhảy cóc" sang mép kia.
 *
 * KÉO SANG TRÁI / PHẢI: chỉ áp dụng cho BẢN THU NHỎ (đúng yêu cầu — bản đầy đủ không kéo được, có nút
 * "Đổi vị trí" riêng cho bản đầy đủ, hành vi kéo thật của nút đó là logic, không vẽ ở đây).
 *  - Đậu bên PHẢI: dòng trạng thái bên trái, logo bên phải (ló nửa ra mép phải) — ĐÚNG như canvas gốc.
 *  - Đậu bên TRÁI: chỉ đổi VỊ TRÍ sang trái, nội dung/thông tin hiển thị giữ nguyên y hệt (vẫn logo +
 *    dòng trạng thái + số NV đã làm) — nhưng để dòng trạng thái không bị khung màn hình cắt mất chữ,
 *    thứ tự trái-phải của logo và dòng trạng thái được LẬT GƯƠNG (logo ló ra mép trái, dòng trạng thái
 *    nằm bên phải logo, chỗ bo góc cũng đổi theo) — đúng như đã thống nhất khi chốt thiết kế.
 *
 * NGÔN NGỮ: nếu [RunningOverlayUiState.language] = [AppLanguage.EN] thì các NHÃN CỐ ĐỊNH của overlay
 * (Chế độ/Mode, Tài khoản/Account, Chưa chọn tài khoản/No account selected, Đã làm/Done, đơn vị NV/
 * tasks, Tạm dừng/Pause, Tiếp tục/Resume) tự đổi sang tiếng Anh — xem [OverlayStrings]. RIÊNG dòng
 * trạng thái [RunningOverlayUiState.statusText] (vd "Đang mở TikTok kiểm tra nick…") là NỘI DUNG ĐỘNG
 * từ logic thật, overlay KHÔNG tự dịch — bạn tự đưa đúng chữ theo ngôn ngữ hiện tại từ nơi gọi.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.locale.AppLanguage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* ───────────────────────────── Trạng thái ───────────────────────────── */

enum class OverlaySide { Right, Left }

/**
 * [accountLabel] = null nghĩa là "chưa chọn tài khoản" (overlay tự hiện đúng nhãn theo [language]).
 * [statusText] là dòng trạng thái động NGUYÊN VĂN do logic thật đưa vào (không tự dịch).
 */
data class RunningOverlayUiState(
    val mode: String = "Golike",
    val accountLabel: String? = null,
    val doneTasks: Int = 0,
    val earnedCoins: Long = 0,
    val statusText: String = "",
    val paused: Boolean = false,
    val expanded: Boolean = true,
    val dockedSide: OverlaySide = OverlaySide.Right,
    val language: AppLanguage = AppLanguage.VI,
    val appVersion: String = "v1.0.5",
)

/* ───────────────────────────── Màn hình nổi ───────────────────────────── */

@Composable
fun RunningOverlayWindow(
    state: RunningOverlayUiState,
    onReposition: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onPauseResume: () -> Unit,
    onDockSideChange: (OverlaySide) -> Unit,
    onBubbleDragDelta: ((Float, Float) -> Unit)? = null,
    onBubbleDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = remember(state.language) { OverlayStrings.of(state.language) }
    val scope = rememberCoroutineScope()

    if (state.expanded) {
        val cardDragX = remember { Animatable(0f) }
        val cardDragY = remember { Animatable(0f) }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(cardDragX.value.roundToInt(), cardDragY.value.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            val dx = cardDragX.value
                            val dy = cardDragY.value
                            scope.launch {
                                if (kotlin.math.abs(dx) > 25f || kotlin.math.abs(dy) > 25f) {
                                    val goRight = dx >= 0
                                    onDockSideChange(if (goRight) OverlaySide.Right else OverlaySide.Left)
                                    onCollapse()
                                }
                                cardDragX.animateTo(0f, tween(200))
                                cardDragY.animateTo(0f, tween(200))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                cardDragX.animateTo(0f, tween(200))
                                cardDragY.animateTo(0f, tween(200))
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newX = (cardDragX.value + dragAmount.x).coerceIn(-300f, 300f)
                            val newY = (cardDragY.value + dragAmount.y).coerceIn(-130f, 250f)
                            cardDragX.snapTo(newX)
                            cardDragY.snapTo(newY)
                        }
                    }
                }
        ) {
            ExpandedCard(state, t, onReposition, onCollapse, onClose, onPauseResume)
        }
    } else {
        Box(modifier = modifier.wrapContentSize()) {
            CollapsedBubble(
                state = state,
                t = t,
                onExpand = onExpand,
                onDockSideChange = onDockSideChange,
                onDragDelta = onBubbleDragDelta,
                onDragEndSnap = onBubbleDragEnd
            )
        }
    }
}

/* ───────────────────────────── Bản đầy đủ ───────────────────────────── */

@Composable
private fun ExpandedCard(
    state: RunningOverlayUiState,
    t: OverlayStrings,
    onReposition: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onPauseResume: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().shadow(20.dp, shape, ambientColor = Color(0x8C000000), spotColor = Color(0x8C000000))
            .background(Alx.White, shape).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("AUTOLUNEX", style = type(15f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
            BasicText(state.appVersion, style = type(12f, FontWeight.SemiBold, Alx.Muted))
            Spacer(Modifier.weight(1f))
            PressCircleButton(onClick = onReposition, bg = Color(0xFFF1F5F9)) { SvgIcon(Reposition, 17.dp, Color(0xFF475569), 2f) }
            PressCircleButton(onClick = onCollapse, bg = Color(0xFFF1F5F9)) { SvgIcon(Minimize, 17.dp, Color(0xFF475569), 2.2f) }
            PressCircleButton(onClick = onClose, bg = Color(0xFFFDECEC)) { SvgIcon(Close, 17.dp, Alx.Danger, 2.2f) }
        }

        Column(Modifier.padding(top = 14.dp)) {
            InfoRow(t.modeLabel, state.mode, valueColor = Alx.Blue, divider = false)
            InfoRow(t.accountLabel, state.accountLabel ?: t.noAccount, valueColor = Color(0xFF475569), divider = true)
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t.doneLabel, Modifier.weight(1f), style = type(13f, FontWeight.Medium, Alx.Muted))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(t.doneCount(state.doneTasks), style = type(14f, FontWeight.ExtraBold, Alx.Ink))
                    Box(Modifier.width(1.dp).height(12.dp).background(Color(0xFFD5DBE6)))
                    BasicText(t.earnedCoins(state.earnedCoins), style = type(14f, FontWeight.ExtraBold, Alx.Blue))
                }
            }
        }

        if (state.statusText.isNotBlank()) {
            val isError = state.statusText.startsWith("Lỗi", ignoreCase = true) ||
                    state.statusText.startsWith("Golike:", ignoreCase = true)
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth()
                    .background(if (isError) Color(0xFFFDECEC) else Color(0xFFF5F8FF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PulsingDot(if (isError) Alx.Danger else Alx.Blue, 8.dp)
                BasicText(
                    state.statusText,
                    style = type(13f, FontWeight.SemiBold, if (isError) Alx.Danger else Color(0xFF334155), lineHeight = 1.35f)
                )
            }
        }

        PauseResumeButton(state.paused, t, onPauseResume, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color, divider: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = type(13f, FontWeight.Medium, Alx.Muted))
            BasicText(value, style = type(14f, FontWeight.ExtraBold, valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEF1F6)))
    }
}

@Composable
private fun PauseResumeButton(paused: Boolean, t: OverlayStrings, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(110), label = "pauseBtn")
    Box(
        modifier.fillMaxWidth().height(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Alx.Ink, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(targetState = paused, label = "pauseIcon", transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) }) { isPaused ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SvgIcon(if (isPaused) Play else Pause, 16.dp, Alx.White, 2f, fill = true)
                BasicText(if (isPaused) t.resume else t.pause, style = type(15f, FontWeight.Bold, Alx.White))
            }
        }
    }
}

/* ───────────────────────────── Bản thu nhỏ (kéo-thả được) ───────────────────────────── */

private val BubbleOverhang = 0.dp   // Không đẩy lọt mép, toàn bộ icon và bong bóng nằm trọn 100% trong màn hình

@Composable
private fun CollapsedBubble(
    state: RunningOverlayUiState,
    t: OverlayStrings,
    onExpand: () -> Unit,
    onDockSideChange: (OverlaySide) -> Unit,
    onDragDelta: ((Float, Float) -> Unit)? = null,
    onDragEndSnap: (() -> Unit)? = null,
) {
    val right = state.dockedSide == OverlaySide.Right
    var dragging by remember { mutableStateOf(false) }

    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && !dragging) 0.96f else 1f, tween(110), label = "bubblePress")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .wrapContentSize()
            .scale(pressScale)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _: Offset -> dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDragEndSnap?.invoke()
                    },
                    onDragCancel = {
                        dragging = false
                    },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        change.consume()
                        onDragDelta?.invoke(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .clickable(interactionSource = pressSource, indication = null, role = Role.Button, onClick = onExpand),
    ) {
        if (right) {
            StatusLabel(state.statusText.ifBlank { t.running }, roundLeft = true, roundRight = false)
            LogoBadge(state.doneTasks)
        } else {
            LogoBadge(state.doneTasks)
            StatusLabel(state.statusText.ifBlank { t.running }, roundLeft = false, roundRight = true)
        }
    }
}

@Composable
private fun StatusLabel(text: String, roundLeft: Boolean, roundRight: Boolean) {
    val shape = RoundedCornerShape(
        topStart = if (roundLeft) 16.dp else 0.dp, bottomStart = if (roundLeft) 16.dp else 0.dp,
        topEnd = if (roundRight) 16.dp else 0.dp, bottomEnd = if (roundRight) 16.dp else 0.dp,
    )
    Row(
        Modifier.widthIn(max = 240.dp)
            .background(Color(0xFF10151F), shape)
            .padding(start = if (roundLeft) 12.dp else 16.dp, end = if (roundRight) 12.dp else 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PulsingDot(Color(0xFF22C55E), 7.dp)
        BasicText(text, style = type(11.5f, FontWeight.SemiBold, Alx.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LogoBadge(doneTasks: Int) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.size(52.dp).shadow(14.dp, shape, ambientColor = Color(0x8C000000), spotColor = Color(0x8C000000))
            .background(Color(0xFF0F172A), shape).border(2.dp, Color(0xFF22C55E), shape),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.cayxu.app.R.drawable.ic_app_logo),
            contentDescription = "App Icon",
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
        )
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-3).dp, y = 3.dp)
                .widthIn(min = 21.dp).height(21.dp).background(Alx.Blue, CircleShape).border(2.dp, Color(0xFF10151F), CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText("$doneTasks", style = type(10f, FontWeight.ExtraBold, Alx.White)) }
    }
}

/** Chấm nháy báo đang chạy — cùng nhịp animation với canvas (1.4s, box-shadow lan ra rồi tắt). */
@Composable
private fun PulsingDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val ring by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = PulseEase), RepeatMode.Restart),
        label = "ring",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).graphicsLayer { scaleX = 1f + ring * 1.4f; scaleY = 1f + ring * 1.4f; alpha = (1f - ring) * 0.5f }.background(color, CircleShape))
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

private val PulseEase = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

@Composable
private fun PressCircleButton(onClick: () -> Unit, bg: Color, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(110), label = "iconBtn")
    Box(
        Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(bg, CircleShape).clip(CircleShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/* ───────────────────────────── Icon (path 24×24) ───────────────────────────── */

private val Reposition = listOf("M7 7h12l-3-3M17 17H5l3 3")
private val Minimize = listOf("M6 12h12")
private val Close = listOf("M6 6l12 12M18 6L6 18")
private val Pause = listOf("M7 5h3.5v14H7zM13.5 5H17v14h-3.5z")
private val Play = listOf("M7 4.5v15l12-7.5z")

/* ───────────────────────────── Chuỗi theo ngôn ngữ ───────────────────────────── */

/**
 * Chỉ dịch các NHÃN CỐ ĐỊNH của overlay. [statusText] trong [RunningOverlayUiState] KHÔNG đi qua đây —
 * đó là nội dung động do logic thật tạo ra, tự chọn đúng ngôn ngữ trước khi đưa vào state.
 */
class OverlayStrings private constructor(
    val modeLabel: String, val accountLabel: String, val noAccount: String, val doneLabel: String,
    val pause: String, val resume: String, val running: String,
    private val doneUnit: (Int) -> String, private val coinUnit: (Long) -> String,
) {
    fun doneCount(n: Int) = doneUnit(n)
    fun earnedCoins(n: Long) = coinUnit(n)

    companion object {
        fun of(language: AppLanguage): OverlayStrings = when (language) {
            AppLanguage.VI -> OverlayStrings(
                modeLabel = "Chế độ", accountLabel = "Tài khoản", noAccount = "Chưa chọn tài khoản", doneLabel = "Đã làm",
                pause = "Tạm dừng", resume = "Tiếp tục", running = "Đang chạy…",
                doneUnit = { n -> "$n NV" }, coinUnit = { n -> "+$n đ" },
            )
            AppLanguage.EN -> OverlayStrings(
                modeLabel = "Mode", accountLabel = "Account", noAccount = "No account selected", doneLabel = "Done",
                pause = "Pause", resume = "Resume", running = "Running…",
                doneUnit = { n -> "$n tasks" }, coinUnit = { n -> "+$n coins" },
            )
        }
    }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(name = "Đầy đủ", widthDp = 390, heightDp = 844)
@Composable
private fun RunningOverlayWindowExpandedPreview() {
    Box(Modifier.fillMaxSize().background(Color(0xFF10151F))) {
        RunningOverlayWindow(
            state = RunningOverlayUiState(mode = "Golike", statusText = "Đang mở TikTok kiểm tra nick…", expanded = true),
            onReposition = {}, onCollapse = {}, onExpand = {}, onClose = {}, onPauseResume = {}, onDockSideChange = {},
        )
    }
}

@Preview(name = "Thu nhỏ — phải", widthDp = 390, heightDp = 844)
@Composable
private fun RunningOverlayWindowCollapsedRightPreview() {
    Box(Modifier.fillMaxSize().background(Color(0xFF10151F))) {
        RunningOverlayWindow(
            state = RunningOverlayUiState(statusText = "Đang mở TikTok kiểm tra nick…", expanded = false, dockedSide = OverlaySide.Right),
            onReposition = {}, onCollapse = {}, onExpand = {}, onClose = {}, onPauseResume = {}, onDockSideChange = {},
        )
    }
}

@Preview(name = "Thu nhỏ — trái (English)", widthDp = 390, heightDp = 844)
@Composable
private fun RunningOverlayWindowCollapsedLeftPreview() {
    Box(Modifier.fillMaxSize().background(Color(0xFF10151F))) {
        RunningOverlayWindow(
            state = RunningOverlayUiState(
                statusText = "Checking account…", expanded = false, dockedSide = OverlaySide.Left, language = AppLanguage.EN,
            ),
            onReposition = {}, onCollapse = {}, onExpand = {}, onClose = {}, onPauseResume = {}, onDockSideChange = {},
        )
    }
}
