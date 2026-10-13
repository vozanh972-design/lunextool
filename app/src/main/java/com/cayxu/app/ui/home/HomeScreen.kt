package com.cayxu.app.ui.home

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.ui.screens.golike.GolikeAccountsStore
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.main.DockClearance
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.screens.home.HomeViewModel
import java.util.Calendar

/* ───────────────────────────── Dữ liệu ───────────────────────────── */

/** Một ô "Truy cập nhanh". [badgeColor] là màu ô logo, [blobColor] là vòng tròn mờ ở góc. */
data class HomeTile(
    val id: String,
    val badge: String,
    val badgeColor: Color,
    val blobColor: Color,
    val amount: String,
    val name: String,
    val subtitle: String,
    val badgeSizeSp: Float = 12f,
    val isSupported: Boolean = true,
)

data class HomeUiState(
    val username: String,
    val coins: String = "0",
    val servicesActive: Int = 4,
    val servicesTotal: Int = 5,
    val daysLeft: Int = 25,
    val today: DayEarnings = DayEarnings(0, 0),          // xu + tiền (đ) kiếm được hôm nay
    val expiryText: String = "Hạn dùng 05/11/2026 · 11:51",
    val hasUnread: Boolean = true,
    val tiles: List<HomeTile> = DefaultTiles,
    val wideTile: HomeTile? = DefaultWideTile,
    val wideStatus: String = "Chưa hỗ trợ",
)

/** Thu nhập của một ngày: [coins] là số xu, [money] là số tiền (đồng). */
data class DayEarnings(val coins: Long, val money: Long)

/** Quy đổi & mốc điểm. Chỉnh ở đây nếu tỉ giá hoặc mục tiêu đổi. */
object PerformanceScale {
    /** 1.000.000 xu = 10.000đ  →  100 xu = 1đ. */
    const val COINS_PER_DONG = 100.0
    /** Kiếm được giá trị tương đương từng này đồng trong ngày thì đạt 100% (10.000.000 xu = 100.000đ). */
    const val FULL_SCORE_DONG = 100_000.0
}

/**
 * Hiệu suất hôm nay (0..1), tính từ xu và tiền thật kiếm được hôm nay:
 *   giá trị (đ) = xu / 100 + tiền (đ);   hiệu suất = giá trị / 100.000đ, tối đa 100%.
 * Mức bình thường (vài trăm nghìn xu, vài nghìn đồng) chỉ ra vài %. Phải kiếm cỡ 10 triệu xu
 * (hoặc tương đương bằng tiền) mới chạm 100%.
 */
fun computePerformance(today: DayEarnings): Float {
    val dong = today.coins / PerformanceScale.COINS_PER_DONG + today.money
    return (dong / PerformanceScale.FULL_SCORE_DONG).coerceIn(0.0, 1.0).toFloat()
}

val HomeUiState.performance: Float get() = computePerformance(today)

val DefaultTiles = listOf(
    HomeTile("xsmm", "XS", Color(0xFFC9363B), Color(0xFFFDECEC), "0 đ", "XSMM", "Tăng tương tác"),
    HomeTile("golike", "GL", Alx.Blue, Alx.BlueSoft, "0 đ", "GoLike", "Kiếm tiền MXH", 13f),
    HomeTile("ttc", "TTC", Color(0xFFC2255C), Color(0xFFFDEAF2), "0 xu", "Tuongtaccheo", "Trao đổi like sub"),
    HomeTile("tiktok", "TikT", Color(0xFF334155), Color(0xFFEDF0F5), "0 acc", "TikTok TTC", "Nhận job tự động", 11f),
)
val DefaultWideTile = HomeTile("nvc", "NV", Color(0xFF94A3B8), Color(0xFFF1F5F9), "", "Nhiemvucheo", "Chưa hỗ trợ", 12f, isSupported = false)

/** "Chào buổi sáng / trưa / chiều / tối" theo giờ máy. */
fun greetingNow(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 11 -> "Chào buổi sáng,"
        h < 14 -> "Chào buổi trưa,"
        h < 18 -> "Chào buổi chiều,"
        else -> "Chào buổi tối,"
    }
}

private val Red = Color(0xFFE5484D)

/* ───────────────────────────── Icon (path khung 24×24, cờ cung viết cách ra) ───────────────────────────── */

private val SearchIcon = listOf("M18 11 A7 7 0 1 0 4 11 A7 7 0 1 0 18 11 Z", "M20 20 L16.5 16.5")
private val BellIcon = listOf(
    "M18 8 A6 6 0 1 0 6 8 C6 15 3 17 3 17 H21 S18 15 18 8",
    "M13.7 21 A2 2 0 0 1 10.3 21",
)
private val CompassIcon = listOf(
    "M21 12 A9 9 0 1 0 3 12 A9 9 0 1 0 21 12 Z",
    "M15.5 8.5 L13.5 13.5 L8.5 15.5 L10.5 10.5 Z",
)
private val ChevronIcon = listOf("M9 6 L15 12 L9 18")

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Trang chủ. Khung 390 × 844dp, nền #F3F5F9, nội dung cuộn. KHÔNG vẽ thanh dock: dock dùng chung nằm ở MainShell.
 * Màn này không có animation riêng (chuyển cảnh từ màn chào mừng do ActivationSuccessScreen lo).
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    greeting: String = greetingNow(),
    onSearch: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onExplore: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onTile: (HomeTile) -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(Alx.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        ) {
            Header(state, greeting, onSearch, onNotifications)
            StatsCard(state, Modifier.padding(top = 20.dp))
            ExploreRow(onExplore, Modifier.padding(top = 12.dp))

            Row(
                Modifier.padding(top = 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText("Truy cập nhanh", style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
                BasicText(
                    "Xem tất cả",
                    Modifier.clickable(role = Role.Button, onClick = onSeeAll),
                    style = type(13f, FontWeight.Bold, Alx.Blue),
                )
            }

            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { QuickTile(it, { onTile(it) }, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                state.wideTile?.let { WideTile(it, state.wideStatus) { onTile(it) } }
            }

            // chừa chỗ cho thanh dock dùng chung (MainShell vẽ), không vẽ dock ở đây
            Spacer(Modifier.height(DockClearance))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Overload kết nối NavController và ViewModel thật trong app.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(),
    onExplore: (() -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }

    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val effectiveName = remember(uiState.info, buyerUsername) {
        uiState.info?.effectiveUsername?.ifBlank { null }
            ?: buyerUsername?.ifBlank { null }
            ?: "username"
    }
    val daysLeft = remember(uiState.info) {
        uiState.info?.daysLeft ?: securePrefs.getDaysLeft()
    }
    val rawExpiresAt = remember(uiState.info) {
        uiState.info?.expiresAt ?: securePrefs.getExpiresAt() ?: ""
    }
    val expiryText = remember(rawExpiresAt) {
        if (rawExpiresAt.isNotBlank()) "Hạn dùng $rawExpiresAt" else "Hạn dùng 30 ngày"
    }

    val xsmmPoints = remember { XsmmAccountStore.getPoints(context) }
    val golikeBalance = remember { GolikeAccountsStore.getBalance(context) }
    val ttcAccounts = remember { TtcAccountsStore.getAccounts(context) }
    val ttcCoins = remember(ttcAccounts) { ttcAccounts.sumOf { it.coins } }
    val tiktokAccounts = remember { TikTokAccountsStore.getAccounts(context) }
    val totalCoins: Long = xsmmPoints + golikeBalance + ttcCoins

    val tiles = remember(xsmmPoints, golikeBalance, ttcCoins, tiktokAccounts.size) {
        listOf(
            HomeTile("xsmm", "XS", Color(0xFFC9363B), Color(0xFFFDECEC), "${xsmmPoints} đ", "XSMM", "Tăng tương tác"),
            HomeTile("golike", "GL", Alx.Blue, Alx.BlueSoft, "${golikeBalance} đ", "GoLike", "Kiếm tiền MXH", 13f),
            HomeTile("ttc", "TTC", Color(0xFFC2255C), Color(0xFFFDEAF2), "${ttcCoins} xu", "Tuongtaccheo", "Trao đổi like sub"),
            HomeTile("tiktok", "TikT", Color(0xFF334155), Color(0xFFEDF0F5), "${tiktokAccounts.size} acc", "TikTok TTC", "Nhận job tự động", 11f),
        )
    }

    val state = remember(effectiveName, totalCoins, daysLeft, expiryText, tiles) {
        HomeUiState(
            username = effectiveName,
            coins = totalCoins.toString(),
            servicesActive = 5,
            servicesTotal = 5,
            daysLeft = daysLeft,
            today = DayEarnings(coins = ttcCoins, money = xsmmPoints + golikeBalance),
            expiryText = expiryText,
            hasUnread = true,
            tiles = tiles,
            wideTile = DefaultWideTile,
            wideStatus = "Chưa hỗ trợ",
        )
    }

    HomeScreen(
        state = state,
        onSearch = { },
        onNotifications = { },
        onExplore = onExplore ?: { navController.navigate(Routes.TASKS) },
        onSeeAll = onSeeAll ?: { navController.navigate(Routes.TASKS) },
        onTile = { tile ->
            if (tile.isSupported) {
                when (tile.id) {
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
private fun Header(state: HomeUiState, greeting: String, onSearch: () -> Unit, onNotifications: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(Alx.BlueSoft, CircleShape), contentAlignment = Alignment.Center) {
                BasicText(initials(state.username), style = type(15f, FontWeight.ExtraBold, Alx.Blue))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText(greeting, style = type(12.5f, FontWeight.Medium, Alx.Muted))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    BasicText(state.username, style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f), maxLines = 1)
                    BasicText(
                        "PRO",
                        Modifier.background(Alx.Blue, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        style = type(10f, FontWeight.ExtraBold, Alx.White, spacing = 0.08f, lineHeight = 1.2f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton("Tìm kiếm", onSearch) { SvgIcon(SearchIcon, 20.dp, Alx.Ink, 1.8f) }
            RoundButton("Thông báo", onNotifications) {
                SvgIcon(BellIcon, 20.dp, Alx.Ink, 1.8f)
                if (state.hasUnread) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = (-11).dp, y = 10.dp).size(8.dp)
                            .background(Red, CircleShape).border(2.dp, Alx.White, CircleShape),
                    )
                }
            }
        }
    }
}

/** "benbenteo10" → "BE", "username" → "US". */
private fun initials(name: String) = name.trim().take(2).uppercase().ifEmpty { "?" }

@Composable
private fun RoundButton(label: String, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(Alx.White, CircleShape)
            .border(1.dp, Alx.Line, CircleShape)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun StatsCard(state: HomeUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 16.dp)) {
            StatCell("Tổng xu", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoinIcon(18.dp)
                    BasicText(state.coins, style = valueStyle())
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Alx.Divider))
            StatCell("Dịch vụ", Modifier.weight(1f)) {
                BasicText(
                    buildAnnotatedString {
                        append("${state.servicesActive}")
                        withStyle(SpanStyle(color = Alx.Placeholder, fontWeight = FontWeight.Bold)) { append("/${state.servicesTotal}") }
                    },
                    style = valueStyle(),
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Alx.Divider))
            StatCell("Còn lại", Modifier.weight(1f)) {
                BasicText(
                    buildAnnotatedString {
                        append("${state.daysLeft}")
                        withStyle(SpanStyle(color = Alx.Muted, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)) { append(" ngày") }
                    },
                    style = valueStyle(),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Alx.Divider))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                BasicText("Hiệu suất hôm nay", style = type(12f, FontWeight.SemiBold, Alx.Ink2))
                BasicText("${(state.performance * 100).toInt()}%", style = type(13f, FontWeight.ExtraBold, Alx.Blue))
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(Alx.BlueSoft, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(state.performance).height(6.dp).background(Alx.Blue, RoundedCornerShape(3.dp)))
            }
            BasicText(state.expiryText, style = type(11.5f, FontWeight.Medium, Alx.Muted))
        }
    }
}

private fun valueStyle() = type(20f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f)

@Composable
private fun StatCell(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = type(11.5f, FontWeight.Medium, Alx.Muted))
        value()
    }
}

/** Xu: đồng xu tròn xanh, vòng trắng mờ bên trong, hình thoi trắng ở giữa (khớp thiết kế). */
@Composable
internal fun CoinIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, Offset.Zero) {
            drawCircle(Alx.Blue, 11f, Offset(12f, 12f))
            drawCircle(Color.White.copy(alpha = 0.55f), 7f, Offset(12f, 12f), style = Stroke(1.5f))
            val gem = Path().apply { moveTo(12f, 8.2f); lineTo(15f, 12f); lineTo(12f, 15.8f); lineTo(9f, 12f); close() }
            drawPath(gem, Color.White)
        }
    }
}

@Composable
private fun ExploreRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    PressBox(onClick, modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(Alx.BlueSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                SvgIcon(CompassIcon, 20.dp, Alx.Blue, 1.8f)
            }
            Column(Modifier.weight(1f)) {
                BasicText("Khám phá nhiệm vụ", style = type(14.5f, FontWeight.Bold, Alx.Ink))
                BasicText("Chọn nền tảng để bắt đầu kiếm xu", Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Normal, Alx.Muted))
            }
            SvgIcon(ChevronIcon, 18.dp, Alx.Muted, 2f)
        }
    }
}

private val TileShape = RoundedCornerShape(22.dp)

@Composable
private fun QuickTile(tile: HomeTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressBox(
        onClick,
        modifier.height(132.dp).tileShadow().background(Alx.White, TileShape).border(1.dp, Alx.Line, TileShape).clip(TileShape),
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp).size(96.dp).background(tile.blobColor, CircleShape))
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Badge(tile)
                BasicText(tile.amount, Modifier.padding(top = 2.dp), style = type(19f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            BasicText(tile.name, style = type(15f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f), maxLines = 1)
            BasicText(tile.subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted), maxLines = 1)
        }
    }
}

@Composable
private fun WideTile(tile: HomeTile, status: String, onClick: () -> Unit) {
    val shape = TileShape
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (tile.isSupported && pressed) 0.985f else 1f,
        tween(120),
        label = "press",
    )
    val boxModifier = Modifier
        .fillMaxWidth()
        .tileShadow()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .background(if (tile.isSupported) Alx.White else Color(0xFFF8FAFC), shape)
        .border(1.dp, if (tile.isSupported) Alx.Line else Color(0xFFE2E8F0), shape)
        .clip(shape)
        .then(
            if (tile.isSupported) {
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

    Box(boxModifier) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 34.dp, y = (-40).dp).size(110.dp).background(tile.blobColor, CircleShape))
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Badge(tile)
            Column(Modifier.weight(1f)) {
                BasicText(
                    tile.name,
                    style = type(
                        15f,
                        FontWeight.ExtraBold,
                        if (tile.isSupported) Alx.Ink else Color(0xFF64748B),
                        spacing = -0.01f,
                    ),
                    maxLines = 1,
                )
                BasicText(
                    tile.subtitle,
                    Modifier.padding(top = 1.dp),
                    style = type(12f, FontWeight.Medium, Alx.Muted),
                    maxLines = 1,
                )
            }
            BasicText(
                status,
                Modifier
                    .background(Alx.White, CircleShape)
                    .border(
                        1.dp,
                        if (tile.isSupported) Color(0xFFBFE3EC) else Color(0xFFE2E8F0),
                        CircleShape,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = type(
                    12f,
                    FontWeight.ExtraBold,
                    if (tile.isSupported) tile.badgeColor else Color(0xFF94A3B8),
                ),
            )
        }
    }
}

@Composable
private fun Badge(tile: HomeTile) {
    Box(Modifier.size(42.dp).background(tile.badgeColor, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        BasicText(tile.badge, style = type(tile.badgeSizeSp, FontWeight.ExtraBold, Alx.White))
    }
}

/** Bóng nhẹ dưới ô: tương đương `0 12px 22px -18px rgba(15,23,42,.3)` (Compose không có spread âm nên chỉ gần đúng). */
private fun Modifier.tileShadow() = shadow(
    elevation = 6.dp, shape = TileShape, clip = false,
    ambientColor = Color(0x140F172A), spotColor = Color(0x260F172A),
)

/** Vùng bấm nhún 0.985 trong 120ms, cùng kiểu mọi màn khác. */
@Composable
private fun PressBox(onClick: () -> Unit, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(HomeUiState(
        username = "username", coins = "1.250",
        today = DayEarnings(coins = 2_000_000, money = 30_000),   // 20.000đ + 30.000đ = 50%
    ), greeting = "Chào buổi trưa,")
}
