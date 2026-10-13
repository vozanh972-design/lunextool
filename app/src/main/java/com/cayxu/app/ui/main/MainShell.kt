package com.cayxu.app.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.animationsEnabled
import com.cayxu.app.ui.design.type

/**
 * Khung chính của app: nội dung của tab đang chọn + MỘT thanh dock duy nhất.
 * Mọi màn tab (Trang chủ, Nhiệm vụ, Tiện ích, Hồ sơ) chỉ vẽ nội dung và chừa [DockClearance] ở đáy,
 * KHÔNG tự vẽ dock.
 */
enum class MainTab(val label: String) {
    Home("Trang chủ"), Tasks("Nhiệm vụ"), Utilities("Tiện ích"), Profile("Hồ sơ");

    fun displayLabel(): String = when (this) {
        Home -> com.cayxu.app.ui.locale.Str.tabHome
        Tasks -> com.cayxu.app.ui.locale.Str.tabTasks
        Utilities -> com.cayxu.app.ui.locale.Str.tabUtilities
        Profile -> com.cayxu.app.ui.locale.Str.tabProfile
    }
}

/** Chiều cao cần chừa dưới cùng của mỗi màn tab: dock 66 + cách đáy 14 + khoảng thở 24. */
val DockClearance: Dp = 104.dp

/** Luồng 4 tab. Truyền nội dung từng tab; tab đang chọn được nhớ qua xoay màn hình. */
@Composable
fun MainRoute(
    home: @Composable () -> Unit,
    tasks: @Composable () -> Unit,
    utilities: @Composable () -> Unit,
    profile: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: MainTab = MainTab.Home,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    MainShell(tab, { tab = it }, modifier) {
        when (tab) {
            MainTab.Home -> home()
            MainTab.Tasks -> tasks()
            MainTab.Utilities -> utilities()
            MainTab.Profile -> profile()
        }
    }
}

@Composable
fun MainShell(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().background(Alx.Bg)) {
        content()
        Dock(
            selected, onSelect,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }
}

/**
 * Viên thuốc nổi cao 66dp (tab cao 52dp), bo 33dp, rộng trừ 16dp mỗi bên.
 * Có MỘT viên thuốc mực #0F172A trượt giữa các tab như dock của Apple:
 *  - [pos] là vị trí (số thực) của viên thuốc, chạy bằng lò xo ~0,6s, tắt dần ở cuối. Về tab xa thì trượt ngược lại, không nhảy.
 *  - Chỉ tab cũ (co lại) và tab mới (giãn ra 1 → 2,5 phần) đổi bề rộng và chữ; tab ở giữa đứng yên, viên thuốc chỉ lướt qua.
 *  - Icon đổi xám ↔ trắng theo khoảng cách tới viên thuốc.
 * Tắt animation hệ thống thì nhảy thẳng tới trạng thái cuối.
 */
@Composable
private fun Dock(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(33.dp)
    val animate = animationsEnabled()
    val target = selected.ordinal
    val pos = remember { Animatable(target.toFloat()) }
    var from by remember { mutableIntStateOf(target) }   // tab đang rời đi
    var to by remember { mutableIntStateOf(target) }     // tab đang tới
    LaunchedEffect(target, animate) {
        if (target != to) { from = to; to = target }
        if (animate) pos.animateTo(target.toFloat(), spring(dampingRatio = 0.85f, stiffness = 300f))
        else pos.snapTo(target.toFloat())
        from = to
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .shadow(14.dp, shape, ambientColor = Color(0x290F172A), spotColor = Color(0x470F172A))
            .background(Alx.White, shape)
            .border(1.dp, Alx.Line, shape)
            .padding(7.dp),
    ) {
        val gap = 4.dp
        val unit = (maxWidth - gap * 3) / 5.5f           // 1 phần = 1 tab thường; tab chọn = 2,5 phần
        val step = unit + gap

        // Viên thuốc: rộng 2,5 phần, vị trí = pos × (1 phần + khoảng cách)
        Box(
            Modifier
                .offset { IntOffset((step.toPx() * pos.value).roundToInt(), 0) }
                .width(unit * 2.5f)
                .height(52.dp)
                .background(Alx.Ink, RoundedCornerShape(26.dp)),
        )

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            MainTab.values().forEach { tab ->
                val i = tab.ordinal
                val near = (1f - abs(i - pos.value)).coerceIn(0f, 1f)          // 1 khi viên thuốc nằm đúng tab này
                val grow = if (i == from || i == to) near else 0f              // chỉ tab cũ/mới đổi bề rộng + chữ
                DockItem(
                    tab = tab,
                    weight = 1f + 1.5f * grow,
                    label = grow,
                    tint = lerp(Alx.Muted, Alx.White, (near * 1.6f).coerceIn(0f, 1f)),
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.DockItem(tab: MainTab, weight: Float, label: Float, tint: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "dockPress")
            val text = tab.displayLabel()
            Box(
                Modifier
                    .weight(weight)
                    .height(52.dp)
                    .graphicsLayer { scaleX = press; scaleY = press }
                    .clip(RoundedCornerShape(26.dp))
                    .semantics { contentDescription = text }
                    .clickable(interactionSource = source, indication = null, role = Role.Tab, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SvgIcon(tab.icon(), (22f - label).dp, tint, 1.9f)
                    BasicText(
                        text,
                        Modifier.padding(start = (8f * label).dp).reveal(label),
                        style = type(14.5f, FontWeight.Bold, Alx.White),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
}

/** Mở chữ theo p: bề rộng = rộng thật × p (cắt phần thừa), đồng thời mờ dần. */
private fun Modifier.reveal(p: Float): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val w = (placeable.width * p).roundToInt()
        layout(w, placeable.height) { placeable.place(0, 0) }
    }
    .clipToBounds()
    .graphicsLayer { alpha = p }

/* Icon khung 24×24, cờ cung viết cách ra. */
private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val x1 = x + w; val y1 = y + h
    return "M${x + r} $y H${x1 - r} A$r $r 0 0 1 $x1 ${y + r} V${y1 - r} A$r $r 0 0 1 ${x1 - r} $y1 " +
        "H${x + r} A$r $r 0 0 1 $x ${y1 - r} V${y + r} A$r $r 0 0 1 ${x + r} $y Z"
}

private val HomeIcon = listOf("M3 11 L12 3 L21 11", "M5 10 V20 H19 V10")
private val TasksIcon = listOf("M21 12 A9 9 0 1 0 3 12 A9 9 0 1 0 21 12 Z", "M8 12 L11 15 L16 9")
private val GridIcon = listOf(
    roundRect(3f, 3f, 7.5f, 7.5f, 1.8f), roundRect(13.5f, 3f, 7.5f, 7.5f, 1.8f),
    roundRect(3f, 13.5f, 7.5f, 7.5f, 1.8f), roundRect(13.5f, 13.5f, 7.5f, 7.5f, 1.8f),
)
private val ProfileIcon = listOf(
    "M16 8 A4 4 0 1 0 8 8 A4 4 0 1 0 16 8 Z",
    "M4 21 C5.5 16.5 9 15 12 15 S18.5 16.5 20 21",
)

private fun MainTab.icon() = when (this) {
    MainTab.Home -> HomeIcon
    MainTab.Tasks -> TasksIcon
    MainTab.Utilities -> GridIcon
    MainTab.Profile -> ProfileIcon
}
