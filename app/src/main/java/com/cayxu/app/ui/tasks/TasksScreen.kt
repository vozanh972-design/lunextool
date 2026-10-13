package com.cayxu.app.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.home.CoinIcon
import com.cayxu.app.ui.home.DayEarnings
import com.cayxu.app.ui.home.computePerformance
import com.cayxu.app.ui.main.DockClearance
import com.cayxu.app.ui.main.MainShell
import com.cayxu.app.ui.main.MainTab
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.screens.golike.GolikeAccountsStore

/* ───────────────────────────── Dữ liệu ───────────────────────────── */

enum class TaskStatus { Running, Ready }

/** Một nền tảng kiếm xu. [color] là màu chữ logo + nhãn "Sẵn sàng", [tint] là nền ô logo. */
data class TaskService(
    val id: String,
    val badge: String,
    val color: Color,
    val tint: Color,
    val name: String,
    val subtitle: String,
    val amount: String,                 // "0 đ", "0 xu", "0 acc"
    val status: TaskStatus,
    val badgeSizeSp: Float = 13f,
)

data class TasksUiState(
    val coins: String = "0",
    val today: DayEarnings = DayEarnings(0, 0),   // cùng nguồn với Trang chủ để hai nơi luôn khớp
    val services: List<TaskService> = DefaultServices,
)

val DefaultServices = listOf(
    TaskService("xsmm", "XSMM", Color(0xFFC9363B), Color(0xFFFDECEC), "XSMM", "Tăng tương tác", "0 đ", TaskStatus.Running, 12.5f),
    TaskService("golike", "GL", Alx.Blue, Alx.BlueSoft, "GoLike", "Kiếm tiền MXH", "0 đ", TaskStatus.Running),
    TaskService("ttc", "TTC", Color(0xFFC2255C), Color(0xFFFDEAF2), "Tuongtaccheo", "Trao đổi like sub", "0 xu", TaskStatus.Running),
    TaskService("tiktok", "TikT", Color(0xFF334155), Color(0xFFEDF0F5), "TikTok TTC", "Nhận job tự động", "0 acc", TaskStatus.Running, 12.5f),
    TaskService("nvc", "NV", Color(0xFF0E7490), Color(0xFFE3F4F8), "Nhiemvucheo", "Tăng view, sub", "", TaskStatus.Ready),
)

private enum class Filter(val label: String) { All("Tất cả"), Running("Đang chạy"), Ready("Sẵn sàng") }

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Màn Nhiệm vụ. Khung 390 × 844dp, nội dung cuộn. KHÔNG vẽ thanh dock: dock dùng chung nằm ở MainShell,
 * màn chỉ chừa [DockClearance] ở đáy. Không có animation riêng.
 */
@Composable
fun TasksScreen(
    state: TasksUiState,
    modifier: Modifier = Modifier,
    onService: (TaskService) -> Unit = {},
) {
    var filter by rememberSaveable { mutableStateOf(Filter.All.name) }
    val current = Filter.valueOf(filter)
    val shown = state.services.filter {
        when (current) {
            Filter.All -> true
            Filter.Running -> it.status == TaskStatus.Running
            Filter.Ready -> it.status == TaskStatus.Ready
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp),
    ) {
        BasicText("Nhiệm vụ", style = type(30f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.03f))
        BasicText("${state.services.size} nền tảng kiếm xu đang kết nối", Modifier.padding(top = 4.dp), style = type(14f, FontWeight.Medium, Alx.Muted))

        Summary(state, Modifier.padding(top = 18.dp))

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.values().forEach { f ->
                FilterChip(f.label, f == current) { filter = f.name }
            }
        }

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { ServiceRow(it) { onService(it) } }
        }

        Spacer(Modifier.height(DockClearance))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * Overload kết nối NavController và dữ liệu thật các Store trong app.
 */
@Composable
fun TasksScreen(
    navController: NavController,
    onService: ((TaskService) -> Unit)? = null,
) {
    val context = LocalContext.current
    val xsmmPoints = remember { XsmmAccountStore.getPoints(context) }
    val golikeBalance = remember { GolikeAccountsStore.getBalance(context) }
    val ttcAccounts = remember { TtcAccountsStore.getAccounts(context) }
    val ttcCoins = remember(ttcAccounts) { ttcAccounts.sumOf { it.coins } }
    val tiktokAccounts = remember { TikTokAccountsStore.getAccounts(context) }
    val totalCoins: Long = xsmmPoints + golikeBalance + ttcCoins

    val services = remember(xsmmPoints, golikeBalance, ttcCoins, tiktokAccounts.size) {
        listOf(
            TaskService("xsmm", "XSMM", Color(0xFFC9363B), Color(0xFFFDECEC), "XSMM", "Tăng tương tác", "${xsmmPoints} đ", TaskStatus.Running, 12.5f),
            TaskService("golike", "GL", Alx.Blue, Alx.BlueSoft, "GoLike", "Kiếm tiền MXH", "${golikeBalance} đ", TaskStatus.Running),
            TaskService("ttc", "TTC", Color(0xFFC2255C), Color(0xFFFDEAF2), "Tuongtaccheo", "Trao đổi like sub", "${ttcCoins} xu", TaskStatus.Running),
            TaskService("tiktok", "TikT", Color(0xFF334155), Color(0xFFEDF0F5), "TikTok TTC", "Nhận job tự động", "${tiktokAccounts.size} acc", TaskStatus.Running, 12.5f),
            TaskService("nvc", "NV", Color(0xFF0E7490), Color(0xFFE3F4F8), "Nhiemvucheo", "Tăng view, sub", "", TaskStatus.Ready),
        )
    }

    val state = remember(totalCoins, services) {
        TasksUiState(
            coins = totalCoins.toString(),
            today = DayEarnings(coins = ttcCoins, money = xsmmPoints + golikeBalance),
            services = services,
        )
    }

    TasksScreen(
        state = state,
        onService = onService ?: { svc ->
            when (svc.id) {
                "xsmm" -> navController.navigate(Routes.XSMM_LOGIN)
                "golike" -> navController.navigate(Routes.GOLIKE_ACCOUNT)
                "ttc" -> navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK)
                "tiktok" -> navController.navigate(Routes.LINK_ACCOUNT_TIKTOK)
                "nvc" -> navController.navigate(Routes.simpleTaskPlatform("Nhiemvucheo"))
            }
        }
    )
}

@Composable
private fun Summary(state: TasksUiState, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape)
            .height(IntrinsicSize.Min).padding(vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText("Tổng xu", style = type(12f, FontWeight.Medium, Alx.Muted))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CoinIcon(20.dp)
                BasicText(state.coins, style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Alx.Divider))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText("Hiệu suất hôm nay", style = type(12f, FontWeight.Medium, Alx.Muted))
            BasicText(
                "${(computePerformance(state.today) * 100).toInt()}%",
                style = type(22f, FontWeight.ExtraBold, Alx.Blue, spacing = -0.02f),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier
            .height(36.dp)
            .let { if (selected) it.background(Alx.Ink, shape) else it.background(Alx.White, shape).border(1.dp, Alx.Line, shape) }
            .clip(shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = if (selected) type(13f, FontWeight.Bold, Alx.White) else type(13f, FontWeight.SemiBold, Alx.Ink2),
        )
    }
}

@Composable
private fun ServiceRow(s: TaskService, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Alx.White, shape)
            .border(1.dp, Alx.Line, shape)
            .clip(shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).background(s.tint, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            BasicText(s.badge, style = type(s.badgeSizeSp, FontWeight.ExtraBold, s.color))
        }
        Column(Modifier.weight(1f)) {
            BasicText(s.name, style = type(15f, FontWeight.Bold, Alx.Ink), maxLines = 1)
            BasicText(s.subtitle, Modifier.padding(top = 1.dp), style = type(12.5f, FontWeight.Normal, Alx.Muted), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (s.status == TaskStatus.Ready) {
                BasicText("Sẵn sàng", style = type(14f, FontWeight.ExtraBold, s.color, align = TextAlign.End))
            } else {
                BasicText(s.amount, style = type(16f, FontWeight.ExtraBold, Alx.Ink, align = TextAlign.End), maxLines = 1)
            }
            BasicText("Check ›", Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Bold, Alx.Blue))
        }
    }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TasksScreenPreview() {
    MainShell(selected = MainTab.Tasks, onSelect = {}) {
        TasksScreen(TasksUiState(coins = "1.250", today = DayEarnings(2_000_000, 30_000)))
    }
}
