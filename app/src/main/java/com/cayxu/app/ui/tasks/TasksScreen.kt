package com.cayxu.app.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cayxu.app.R
import com.cayxu.app.data.local.SecurePrefs
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

/**
 * Mạng xã hội mà một nền tảng hỗ trợ. [icon] là logo đã cắt tròn 128×128 đặt trong res/drawable
 * (ic_platform_facebook.png, ic_platform_instagram.png, ic_platform_tiktok.png).
 * Thêm mạng mới: thêm một file logo cùng quy cách (vuông, cắt tròn, 128×128) và một giá trị ở đây.
 */
enum class Platform(val label: String, @DrawableRes val icon: Int) {
    Facebook("Facebook", R.drawable.ic_platform_facebook),
    Instagram("Instagram", R.drawable.ic_platform_instagram),
    TikTok("TikTok", R.drawable.ic_platform_tiktok),
}

/** Một nền tảng kiếm xu. [color] là màu chữ logo, [tint] là nền ô logo, [platforms] là các mạng nó hỗ trợ. [isSupported] xác định nền tảng đã sẵn sàng hay chưa. */
data class TaskService(
    val id: String,
    val badge: String,
    val color: Color,
    val tint: Color,
    val name: String,
    val platforms: List<Platform>,
    val badgeSizeSp: Float = 13f,
    val isSupported: Boolean = true,
)

data class TasksUiState(
    val coins: String = "0",
    val today: DayEarnings = DayEarnings(0, 0),   // cùng nguồn với Trang chủ để hai nơi luôn khớp
    val services: List<TaskService> = DefaultServices,
)

/** Danh sách mạng hỗ trợ ở đây là dữ liệu mẫu: sửa theo đúng từng nền tảng của bạn. */
val DefaultServices = listOf(
    TaskService("xsmm", "XSMM", Color(0xFFC9363B), Color(0xFFFDECEC), "XSMM", listOf(Platform.TikTok, Platform.Facebook, Platform.Instagram), 12.5f),
    TaskService("golike", "GL", Alx.Blue, Alx.BlueSoft, "GoLike", listOf(Platform.Facebook, Platform.Instagram, Platform.TikTok)),
    TaskService("ttc", "TTC", Color(0xFFC2255C), Color(0xFFFDEAF2), "Tuongtaccheo", listOf(Platform.Facebook)),
    TaskService("tiktok", "TikT", Color(0xFF334155), Color(0xFFEDF0F5), "TikTok TTC", listOf(Platform.TikTok), 12.5f),
    TaskService("nvc", "NV", Color(0xFF94A3B8), Color(0xFFF1F5F9), "Nhiemvucheo", listOf(Platform.Facebook), isSupported = false),
)

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Màn Nhiệm vụ: tiêu đề, thẻ tóm tắt và danh sách nền tảng (không có chip lọc). Khung 390 × 844dp, nội dung cuộn. KHÔNG vẽ thanh dock: dock dùng chung nằm ở MainShell,
 * màn chỉ chừa [DockClearance] ở đáy. Không có animation riêng.
 */
@Composable
fun TasksScreen(
    state: TasksUiState,
    modifier: Modifier = Modifier,
    onService: (TaskService) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp),
    ) {
        BasicText(com.cayxu.app.ui.locale.Str.tasksTitle, style = type(30f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.03f))
        BasicText(com.cayxu.app.ui.locale.Str.tasksSubtitle(state.services.size), Modifier.padding(top = 4.dp), style = type(14f, FontWeight.Medium, Alx.Muted))

        Summary(state, Modifier.padding(top = 18.dp))

        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.services.forEach { ServiceRow(it) { onService(it) } }
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
    val totalCoins: Long = xsmmPoints + golikeBalance + ttcCoins

    val state = remember(totalCoins) {
        TasksUiState(
            coins = totalCoins.toString(),
            today = DayEarnings(coins = ttcCoins, money = xsmmPoints + golikeBalance),
            services = DefaultServices,
        )
    }

    TasksScreen(
        state = state,
        onService = onService ?: { svc ->
            if (svc.isSupported) {
                when (svc.id) {
                    "xsmm" -> navController.navigate(Routes.XSMM_LOGIN)
                    "golike" -> navController.navigate(Routes.GOLIKE_ACCOUNT)
                    "ttc" -> navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK)
                    "tiktok" -> navController.navigate(Routes.LINK_ACCOUNT_TIKTOK)
                    "nvc" -> Unit
                }
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
            BasicText(com.cayxu.app.ui.locale.Str.tasksTotalCoins, style = type(12f, FontWeight.Medium, Alx.Muted))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CoinIcon(20.dp)
                BasicText(state.coins, style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Alx.Divider))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(com.cayxu.app.ui.locale.Str.tasksTodayPerformance, style = type(12f, FontWeight.Medium, Alx.Muted))
            BasicText(
                "${(computePerformance(state.today) * 100).toInt()}%",
                style = type(22f, FontWeight.ExtraBold, Alx.Blue, spacing = -0.02f),
            )
        }
    }
}

@Composable
private fun ServiceRow(s: TaskService, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (s.isSupported && pressed) 0.985f else 1f,
        tween(120),
        label = "press",
    )

    val rowModifier = Modifier
        .fillMaxWidth()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .background(if (s.isSupported) Alx.White else Color(0xFFF8FAFC), shape)
        .border(1.dp, if (s.isSupported) Alx.Line else Color(0xFFE2E8F0), shape)
        .clip(shape)
        .then(
            if (s.isSupported) {
                Modifier.clickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        )
        .padding(14.dp)

    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(46.dp).background(
                if (s.isSupported) s.tint else Color(0xFFF1F5F9),
                RoundedCornerShape(14.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                s.badge,
                style = type(
                    s.badgeSizeSp,
                    FontWeight.ExtraBold,
                    if (s.isSupported) s.color else Color(0xFF94A3B8),
                ),
            )
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                s.name,
                style = type(
                    15f,
                    FontWeight.Bold,
                    if (s.isSupported) Alx.Ink else Color(0xFF64748B),
                ),
                maxLines = 1,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.platforms.forEach { PlatformChip(it, isSupported = s.isSupported) }
            }
        }
        if (!s.isSupported) {
            Box(
                Modifier
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                BasicText(
                    com.cayxu.app.ui.locale.Str.tasksNotSupported,
                    style = type(11.5f, FontWeight.SemiBold, Color(0xFF94A3B8)),
                )
            }
        }
    }
}

/** Logo mạng xã hội 22dp, đã cắt tròn sẵn trong file; làm mờ khi nền tảng chưa hỗ trợ. */
@Composable
private fun PlatformChip(p: Platform, isSupported: Boolean = true) {
    Image(
        painter = painterResource(p.icon),
        contentDescription = p.label,
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(if (!isSupported) Modifier.graphicsLayer { alpha = 0.35f } else Modifier),
    )
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TasksScreenPreview() {
    MainShell(selected = MainTab.Tasks, onSelect = {}) {
        TasksScreen(TasksUiState(coins = "1.250", today = DayEarnings(2_000_000, 30_000)))
    }
}
