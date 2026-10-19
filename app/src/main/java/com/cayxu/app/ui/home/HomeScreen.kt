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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.ProAvatar
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.locale.Str
import com.cayxu.app.ui.main.DockClearance
import com.cayxu.app.ui.main.MainShell
import com.cayxu.app.ui.main.MainTab
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.profile.ContactChannel
import com.cayxu.app.ui.profile.ContactKind
import com.cayxu.app.ui.screens.golike.GolikeAccountsStore
import com.cayxu.app.ui.screens.home.HomeViewModel
import com.cayxu.app.ui.support.DefaultSupportContacts
import com.cayxu.app.ui.support.SupportSheet
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
    val tiles: List<HomeTile> = DefaultTiles,
    val wideTile: HomeTile? = DefaultWideTile,
    val wideStatus: String = "Sẵn sàng",
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
val DefaultWideTile = HomeTile("nvc", "NV", Color(0xFF0E7490), Color(0xFFE3F4F8), "", "Nhiemvucheo", "Tăng view, sub")

/** "Chào buổi sáng / trưa / chiều / tối" theo giờ máy. */
fun greetingNow(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return Str.greeting(h)
}

/* ───────────────────────────── Icon (path khung 24×24, cờ cung viết cách ra) ───────────────────────────── */

// Icon "hỗ trợ": người đeo tai nghe có mic. Vòng ngoài vẽ nét, hai chụp tai tô đặc (vẽ lớp riêng vì SvgIcon tô cả bộ path).
private val SupportOutline = listOf(
    "M15.4 11.5 A3.4 3.4 0 1 0 8.6 11.5 A3.4 3.4 0 1 0 15.4 11.5 Z",
    "M4.5 12 V11 A7.5 7.5 0 0 1 19.5 11 V12",
    "M19.7 16.5 C19.7 18.7 18.1 20 15.7 20 H13.5",
)
private val SupportCups = listOf(
    "M4.1 11.5 H4.5 A1.3 1.3 0 0 1 5.8 12.8 V15.2 A1.3 1.3 0 0 1 4.5 16.5 H4.1 A1.3 1.3 0 0 1 2.8 15.2 V12.8 A1.3 1.3 0 0 1 4.1 11.5 Z",
    "M19.5 11.5 H19.9 A1.3 1.3 0 0 1 21.2 12.8 V15.2 A1.3 1.3 0 0 1 19.9 16.5 H19.5 A1.3 1.3 0 0 1 18.2 15.2 V12.8 A1.3 1.3 0 0 1 19.5 11.5 Z",
)

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
    sponsors: List<SponsorBanner> = DefaultSponsorBanners,
    contacts: List<ContactChannel> = DefaultSupportContacts,
    onSponsorOpen: (String) -> Unit = {},
    onSeeAll: () -> Unit = {},
    onTile: (HomeTile) -> Unit = {},
) {
    var showSupport by remember { mutableStateOf(false) }
    if (showSupport) SupportSheet(onDismiss = { showSupport = false }, contacts = contacts)

    Box(modifier.fillMaxSize().background(Alx.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        ) {
            Header(state, greeting, onSupport = { showSupport = true })
            StatsCard(state, Modifier.padding(top = 20.dp))
            SponsorCarousel(sponsors, Modifier.padding(top = 12.dp), onOpen = onSponsorOpen)

            Row(
                Modifier.padding(top = 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText(Str.homeQuickAccess, style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
                BasicText(
                    Str.profileSeeAll,
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
 * Đọc dữ liệu thật từ các tài khoản, điểm XSMM, số dư Golike, tài khoản TTC, TikTok.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(),
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
            tiles = tiles,
            wideTile = DefaultWideTile,
            wideStatus = Str.homeNotSupported,
        )
    }

    HomeScreen(
        state = state,
        onSeeAll = onSeeAll ?: { navController.navigate(Routes.TASKS) },
        onTile = { tile ->
            if (tile.isSupported) {
                when (tile.id) {
                    "xsmm" -> navController.navigate(Routes.XSMM_LOGIN)
                    "golike" -> navController.navigate(Routes.GOLIKE_ACCOUNT)
                    "ttc" -> navController.navigate(Routes.TUONG_TAC_CHEO)
                    "tiktok" -> navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK)
                    "nvc" -> Unit
                }
            }
        }
    )
}

@Composable
private fun Header(state: HomeUiState, greeting: String, onSupport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header luôn hiện nhãn PRO nên avatar luôn có viền PRO (gradient xanh, tím, hồng).
            ProAvatar(initials(state.username), inner = 44.dp, ringWidth = 2.dp, gap = 2.dp, textSp = 15f, ringed = true)
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
        RoundButton("Liên hệ hỗ trợ", onSupport) {
            SvgIcon(SupportOutline, 20.dp, Alx.Ink, 1.8f)
            SvgIcon(SupportCups, 20.dp, Alx.Ink, 1.8f, fill = true)   // hai chụp tai tô đặc, chồng đúng lên lớp nét
        }
    }
}

/** "benbenteo10" → "BE", "username" → "US". */
private fun initials(name: String) = name.trim().take(2).uppercase().ifEmpty { "?" }

@Composable
private fun RoundButton(label: String, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "roundBtnPress")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Alx.White, CircleShape)
            .border(1.dp, Alx.Line, CircleShape)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun StatsCard(state: HomeUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 16.dp)) {
            StatCell(Str.homeTotalCoins, Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoinIcon(18.dp)
                    BasicText(state.coins, style = valueStyle())
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Alx.Divider))
            StatCell(Str.homeServices, Modifier.weight(1f)) {
                BasicText(
                    buildAnnotatedString {
                        append("${state.servicesActive}")
                        withStyle(SpanStyle(color = Alx.Placeholder, fontWeight = FontWeight.Bold)) { append("/${state.servicesTotal}") }
                    },
                    style = valueStyle(),
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Alx.Divider))
            StatCell(Str.homeRemaining, Modifier.weight(1f)) {
                BasicText(
                    buildAnnotatedString {
                        append("${state.daysLeft}")
                        withStyle(SpanStyle(color = Alx.Muted, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)) { append(Str.homeDaysUnit) }
                    },
                    style = valueStyle(),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Alx.Divider))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                BasicText(Str.homeTodayPerformance, style = type(12f, FontWeight.SemiBold, Alx.Ink2))
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
    PressBox(
        onClick,
        Modifier.fillMaxWidth().tileShadow().background(Alx.White, TileShape).border(1.dp, Alx.Line, TileShape).clip(TileShape),
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 34.dp, y = (-40).dp).size(110.dp).background(tile.blobColor, CircleShape))
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Badge(tile)
            Column(Modifier.weight(1f)) {
                BasicText(tile.name, style = type(15f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f), maxLines = 1)
                BasicText(tile.subtitle, Modifier.padding(top = 1.dp), style = type(12f, FontWeight.Medium, Alx.Muted), maxLines = 1)
            }
            BasicText(
                status,
                Modifier.background(Alx.White, CircleShape).border(1.dp, Color(0xFFBFE3EC), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                style = type(12f, FontWeight.ExtraBold, tile.badgeColor),
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
    MainShell(selected = MainTab.Home, onSelect = {}) {
        HomeScreen(
            HomeUiState(
                username = "username", coins = "1.250",
                today = DayEarnings(coins = 2_000_000, money = 30_000),   // 20.000đ + 30.000đ = 50%
            ),
            greeting = "Chào buổi trưa,"
        )
    }
}
