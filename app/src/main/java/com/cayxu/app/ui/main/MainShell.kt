package com.cayxu.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

/**
 * Khung chính của app: nội dung của tab đang chọn + MỘT thanh dock duy nhất.
 * Mọi màn tab (Trang chủ, Nhiệm vụ, Tiện ích, Hồ sơ) chỉ vẽ nội dung và chừa [DockClearance] ở đáy,
 * KHÔNG tự vẽ dock.
 */
enum class MainTab(val label: String) {
    Home("Trang chủ"), Tasks("Nhiệm vụ"), Utilities("Tiện ích"), Profile("Hồ sơ"),
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
 * Viên thuốc nổi cao 66dp, bo 33dp. Tab đang chọn: nền mực #0F172A, icon + chữ trắng, chiếm 2,5 phần;
 * tab khác chỉ có icon xám, mỗi tab 1 phần.
 */
@Composable
private fun Dock(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(33.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .shadow(14.dp, shape, ambientColor = Color(0x290F172A), spotColor = Color(0x470F172A))
            .background(Alx.White, shape)
            .border(1.dp, Alx.Line, shape)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.values().forEach { tab ->
            val on = tab == selected
            val item = Modifier
                .weight(if (on) 2.5f else 1f)
                .height(52.dp)
                .let { if (on) it.background(Alx.Ink, RoundedCornerShape(26.dp)) else it }
                .clip(RoundedCornerShape(26.dp))
                .semantics { contentDescription = tab.label }
                .clickable(role = Role.Tab, onClick = { onSelect(tab) })
            Row(item, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                SvgIcon(tab.icon(), if (on) 21.dp else 22.dp, if (on) Alx.White else Alx.Muted, 1.9f)
                if (on) BasicText(tab.label, style = type(14.5f, FontWeight.Bold, Alx.White), maxLines = 1)
            }
        }
    }
}

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
