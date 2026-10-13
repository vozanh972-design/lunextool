package com.cayxu.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cayxu.app.data.CoinEntry
import com.cayxu.app.data.CoinEntryKind
import com.cayxu.app.data.CoinLedgerProvider
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.relativeTime
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.history.CoinEntryCard
import com.cayxu.app.ui.history.CoinEntryRow
import com.cayxu.app.ui.main.DockClearance
import com.cayxu.app.ui.main.MainShell
import com.cayxu.app.ui.main.MainTab
import com.cayxu.app.ui.navigation.Routes

/* ───────────────────────────── Dữ liệu ───────────────────────────── */

/** Các dòng trong "Tài khoản & hỗ trợ". Phân biệt bằng giá trị này ở [onAction]. */
enum class ProfileAction(val label: String) {
    EditProfile("Chỉnh sửa hồ sơ"),
    Settings("Cài đặt tài khoản"),
    Support("Hỗ trợ & điều khoản"),
    Logout("Đăng xuất"),
}

/**
 * [membershipProgress] là phần thanh đã dùng/còn lại, 0..1 (thiết kế: 0,58). Tính ở nơi giữ dữ liệu
 * (ví dụ daysLeft / tổng số ngày của gói) rồi truyền vào.
 */
data class ProfileUiState(
    val username: String,
    val handle: String,
    val planBadge: String,          // "" thì ẩn nhãn
    val planName: String,
    val daysLeft: Int,
    val expiryDate: String,         // đã định dạng, vd "05/11/2026"
    val membershipProgress: Float,
    /** Vài dòng mới nhất của sổ xu (thường 2), lấy từ CoinLedger.observeRecent(2). Rỗng thì ẩn cả mục. */
    val recent: List<CoinEntry> = emptyList(),
)

/* Icon: lưới 24×24, cờ cung luôn viết cách nhau vì PathParser không đọc dạng dính. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val Shield = listOf("M12 22s8-4.5 8-11V5l-8-3-8 3v6c0 6.5 8 11 8 11z", "M8.5 12l2.5 2.5 4.5-5")
private val User = listOf(circle(12f, 8f, 4f), "M4 21c1.5-4.5 5-6 8-6s6.5 1.5 8 6")
private val Sliders = listOf("M4 7h10M18 7h2M4 17h2M10 17h10", circle(16f, 7f, 2.2f), circle(8f, 17f, 2.2f))
private val Help = listOf(circle(12f, 12f, 9f), "M9.5 9.5a2.5 2.5 0 1 1 4 2c-.9.6-1.5 1-1.5 2M12 17h.01")
private val Logout = listOf("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9")
private val Chevron = listOf("M9 6l6 6-6 6")

private val RowLine = Color(0xFFEEF1F6)

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Màn Hồ sơ. Khung 390 × 844dp, nội dung cuộn. KHÔNG vẽ thanh dock: dock dùng chung nằm ở MainShell,
 * màn chỉ chừa [DockClearance] ở đáy. Không có animation riêng ngoài hiệu ứng chạm nhún 0.985 / 120 ms.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    onAction: (ProfileAction) -> Unit = {},
    onSeeAllHistory: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp),
    ) {
        Identity(state)
        Membership(state, Modifier.padding(top = 20.dp))

        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Lịch sử xu", Modifier.weight(1f), style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
            SeeAll(onSeeAllHistory)
        }
        if (state.recent.isNotEmpty()) {
            CoinEntryCard(Modifier.padding(top = 12.dp)) {
                val today = remember { java.time.LocalDate.now() }
                state.recent.forEachIndexed { i, e -> CoinEntryRow(e, relativeTime(e.atMillis, today), divider = i > 0) }
            }
        } else {
            Card(Modifier.padding(top = 12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Chưa có lịch sử nhận xu", style = type(13.5f, FontWeight.Medium, Alx.Muted))
                }
            }
        }

        SectionTitle("Tài khoản & hỗ trợ")
        Card(Modifier.padding(top = 12.dp)) {
            val rows = listOf(
                Triple(ProfileAction.EditProfile, User, false),
                Triple(ProfileAction.Settings, Sliders, false),
                Triple(ProfileAction.Support, Help, false),
                Triple(ProfileAction.Logout, Logout, true),
            )
            rows.forEachIndexed { i, (action, icon, danger) ->
                SettingRow(action.label, icon, danger, divider = i > 0) { onAction(action) }
            }
        }

        Spacer(Modifier.height(DockClearance))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * Overload kết nối NavController, CoinLedger thực tế và thông tin bản quyền từ SecurePrefs.
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    onAction: ((ProfileAction) -> Unit)? = null,
) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val ledger = remember { CoinLedgerProvider.get(context) }
    val recentCoins by ledger.observeRecent(2).collectAsStateWithLifecycle(initialValue = emptyList())
    var showLogoutDialog by remember { mutableStateOf(false) }

    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val daysLeft = remember { securePrefs.getDaysLeft() }
    val rawExpiresAt = remember { securePrefs.getExpiresAt() }

    val username = remember(buyerUsername) {
        if (!buyerUsername.isNullOrBlank()) buyerUsername.trim() else "username"
    }
    val handle = remember(username) { "@$username" }

    val expiryDate = remember(rawExpiresAt) {
        if (!rawExpiresAt.isNullOrBlank()) {
            rawExpiresAt.split(" ").firstOrNull() ?: rawExpiresAt
        } else {
            "05/11/2026"
        }
    }

    val progress = remember(daysLeft) {
        (daysLeft.toFloat() / 30f).coerceIn(0f, 1f)
    }

    val state = remember(username, handle, daysLeft, expiryDate, progress, recentCoins) {
        ProfileUiState(
            username = username,
            handle = handle,
            planBadge = "PRO",
            planName = "Thành viên PRO",
            daysLeft = daysLeft,
            expiryDate = expiryDate,
            membershipProgress = progress,
            recent = recentCoins,
        )
    }

    ProfileScreen(
        state = state,
        onAction = onAction ?: { action ->
            when (action) {
                ProfileAction.EditProfile -> {
                    Toast.makeText(context, "Chỉnh sửa hồ sơ", Toast.LENGTH_SHORT).show()
                }
                ProfileAction.Settings -> {
                    navController.navigate(Routes.SETTINGS)
                }
                ProfileAction.Support -> {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/cayxu_admin")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Liên hệ hỗ trợ: https://t.me/cayxu_admin", Toast.LENGTH_SHORT).show()
                    }
                }
                ProfileAction.Logout -> {
                    showLogoutDialog = true
                }
            }
        },
        onSeeAllHistory = {
            navController.navigate(Routes.COIN_HISTORY)
        }
    )

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                BasicText("Đăng xuất", style = type(18f, FontWeight.Bold, Alx.Ink))
            },
            text = {
                BasicText("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?", style = type(14f, FontWeight.Medium, Alx.Ink))
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .background(Alx.Danger, RoundedCornerShape(12.dp))
                        .clickable {
                            showLogoutDialog = false
                            securePrefs.clearKey()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicText("Đăng xuất", style = type(14f, FontWeight.Bold, Color.White))
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable { showLogoutDialog = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicText("Hủy", style = type(14f, FontWeight.SemiBold, Alx.Muted))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun Identity(s: ProfileUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(68.dp).background(Alx.BlueSoft, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(
                s.username.take(1).uppercase(),
                style = type(28f, FontWeight.ExtraBold, Alx.Blue),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(s.username, Modifier.weight(1f, fill = false), style = type(22f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f), maxLines = 1)
                if (s.planBadge.isNotEmpty()) {
                    BasicText(
                        s.planBadge,
                        Modifier.background(Alx.Blue, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        style = type(10f, FontWeight.ExtraBold, Color.White, spacing = 0.08f, lineHeight = 1.2f),
                    )
                }
            }
            BasicText(s.handle, style = type(13.5f, FontWeight.Medium, Alx.Muted), maxLines = 1)
        }
    }
}

@Composable
private fun Membership(s: ProfileUiState, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Shield, Alx.Blue, Alx.BlueSoft, 40, 12, 20)
            Column(Modifier.weight(1f)) {
                BasicText(s.planName, style = type(14.5f, FontWeight.Bold, Alx.Ink))
                BasicText(
                    "Còn ${s.daysLeft} ngày · Hết hạn ${s.expiryDate}",
                    Modifier.padding(top = 1.dp),
                    style = type(12f, FontWeight.Medium, Alx.Muted),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(Alx.BlueSoft, RoundedCornerShape(3.dp))) {
            Box(
                Modifier.fillMaxWidth(s.membershipProgress.coerceIn(0f, 1f)).height(5.dp)
                    .background(Alx.Blue, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(text, Modifier.padding(top = 24.dp), style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))
}

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape)
            .padding(horizontal = 16.dp),
    ) { content() }
}

@Composable
private fun IconTile(paths: List<String>, color: Color, tint: Color, tile: Int, radius: Int, icon: Int) {
    Box(Modifier.size(tile.dp).background(tint, RoundedCornerShape(radius.dp)), contentAlignment = Alignment.Center) {
        SvgIcon(paths, icon.dp, color, 1.8f)
    }
}

/** "Xem tất cả ›" xanh, cạnh tiêu đề Lịch sử xu. Vùng chạm cao 44dp. */
@Composable
private fun SeeAll(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Row(
        Modifier.height(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText("Xem tất cả", style = type(13.5f, FontWeight.Bold, Alx.Blue))
        SvgIcon(Chevron, 16.dp, Alx.Blue, 2.2f)
    }
}

@Composable
private fun SettingRow(label: String, icon: List<String>, danger: Boolean, divider: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(RowLine))
        Row(
            Modifier.fillMaxWidth().height(54.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SvgIcon(icon, 20.dp, if (danger) Alx.Danger else Alx.Blue, 1.8f)
            BasicText(
                label,
                Modifier.weight(1f),
                style = type(14.5f, if (danger) FontWeight.Bold else FontWeight.SemiBold, if (danger) Alx.Danger else Alx.Ink),
            )
            if (!danger) SvgIcon(Chevron, 16.dp, Color(0xFF94A3B8), 2f)
        }
    }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    val now = System.currentTimeMillis()
    MainShell(selected = MainTab.Profile, onSelect = {}) {
        ProfileScreen(
            ProfileUiState(
                username = "username", handle = "@username", planBadge = "PRO", planName = "Thành viên PRO",
                daysLeft = 25, expiryDate = "05/11/2026", membershipProgress = 0.58f,
                recent = listOf(
                    CoinEntry(1, CoinEntryKind.Follow, "Theo dõi @linh.daily", 40, now, "preview"),
                    CoinEntry(2, CoinEntryKind.Redeem, "Đổi thẻ điện thoại", -1000, now - 86_400_000, "preview"),
                ),
            ),
        )
    }
}
