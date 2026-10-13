package com.cayxu.app.ui.utilities

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.main.DockClearance
import com.cayxu.app.ui.main.MainShell
import com.cayxu.app.ui.main.MainTab
import com.cayxu.app.ui.navigation.Routes

/* ───────────────────────────── Dữ liệu ───────────────────────────── */

/** Một công cụ nhỏ trong danh sách "Công cụ khác". [tag] là nhãn nhóm viết hoa, in nhỏ phía trên tên. */
data class UtilityTool(
    val id: String,
    val tag: String,
    val title: String,
    val description: String,
    val color: Color,
    val tint: Color,
    val iconPaths: List<String>,
)

/** Thẻ nổi bật ở đầu màn. Ba bước của dải quy trình là cố định theo thiết kế (Lướt feed → Cảm xúc → Xem story). */
data class FeaturedTool(
    val badge: String = "NỔI BẬT",
    val meta: String = "Đa luồng",
    val title: String = "Nuôi tài khoản tổng hợp",
    val description: String = "Tự động lướt feed, thả cảm xúc, xem story chăm sóc nick FB & TikTok.",
    val cta: String = "Cấu hình ngay",
)

data class UtilitiesUiState(
    val featured: FeaturedTool = FeaturedTool(),
    val tools: List<UtilityTool> = DefaultTools,
)

/* Icon: toàn bộ lưới 24×24, cờ cung (arc flag) luôn viết cách nhau vì PathParser không đọc dạng dính. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private val ArrowDown = listOf("M12 5v14M6 13l6 6 6-6")
private val Heart = listOf("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z")
private val PlayCircle = listOf(circle(12f, 12f, 9f), "M10 8.5l5 3.5-5 3.5z")
private val Flag = listOf("M4 22V4a1 1 0 0 1 1-1h12l-2 5 2 5H5")
private val Users = listOf(
    "M17 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
    circle(9.5f, 7f, 4f),
    "M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8",
)
private val Sliders = listOf("M4 7h10M18 7h2M4 17h2M10 17h10", circle(16f, 7f, 2.2f), circle(8f, 17f, 2.2f))
private val Chevron = listOf("M9 6l6 6-6 6")

/** Dữ liệu mẫu theo thiết kế. [UtilityTool.id] là khoá để bạn điều hướng trong onTool. */
val DefaultTools = listOf(
    UtilityTool("page_reg", "FANPAGE", "Reg & chuyển page", "Tạo trang và chuyển quyền quản trị", Alx.Blue, Alx.BlueSoft, Flag),
    UtilityTool("fb_care", "FACEBOOK", "Nuôi nick FB", "Lướt feed, kết bạn, tương tác nick chính", Color(0xFFC2255C), Color(0xFFFDEAF2), Users),
    UtilityTool("scenario", "CẤU HÌNH", "Thiết lập kịch bản nuôi", "Tùy chỉnh delay, số lượng hành động", Color(0xFF334155), Color(0xFFEDF0F5), Sliders),
)

/* ───────────────────────────── Màn hình ───────────────────────────── */

/**
 * Màn Tiện ích. Khung 390 × 844dp, nội dung cuộn. KHÔNG vẽ thanh dock: dock dùng chung nằm ở MainShell,
 * màn chỉ chừa [DockClearance] ở đáy. Không có animation riêng ngoài hiệu ứng chạm nhún 0.985 / 120 ms.
 *
 * @param onConfigure bấm "Cấu hình ngay" trên thẻ nổi bật (mở màn cấu hình nuôi tài khoản).
 * @param onTool      bấm một dòng trong "Công cụ khác"; phân biệt bằng [UtilityTool.id].
 */
@Composable
fun UtilitiesScreen(
    state: UtilitiesUiState,
    modifier: Modifier = Modifier,
    onConfigure: () -> Unit = {},
    onTool: (UtilityTool) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp),
    ) {
        BasicText("Tiện ích mở rộng", style = type(30f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.03f))
        BasicText(
            "Tối ưu và tự động hóa quy trình nuôi tài khoản mạng xã hội",
            Modifier.padding(top = 4.dp),
            style = type(14f, FontWeight.Medium, Alx.Muted, lineHeight = 1.45f),
        )

        FeaturedCard(state.featured, onConfigure, Modifier.padding(top = 20.dp))

        BasicText("Công cụ khác", Modifier.padding(top = 24.dp), style = type(17f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f))

        val listShape = RoundedCornerShape(20.dp)
        Column(
            Modifier.padding(top = 12.dp).fillMaxWidth()
                .background(Alx.White, listShape).border(1.dp, Alx.Line, listShape).clip(listShape)
                .padding(horizontal = 16.dp),
        ) {
            state.tools.forEachIndexed { i, tool ->
                ToolRow(tool, divider = i > 0) { onTool(tool) }
            }
        }

        Spacer(Modifier.height(DockClearance))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * Overload kết nối NavController và luồng điều hướng thật trong app.
 */
@Composable
fun UtilitiesScreen(
    navController: NavController,
    onConfigure: (() -> Unit)? = null,
    onTool: ((UtilityTool) -> Unit)? = null,
) {
    UtilitiesScreen(
        state = UtilitiesUiState(),
        onConfigure = onConfigure ?: {
            navController.navigate(Routes.NURTURE_SETUP) { launchSingleTop = true }
        },
        onTool = onTool ?: { tool ->
            when (tool.id) {
                "page_reg" -> navController.navigate(Routes.REG_AND_TRANSFER_PAGE) { launchSingleTop = true }
                "fb_care" -> navController.navigate(Routes.FB_NURTURE) { launchSingleTop = true }
                "scenario" -> navController.navigate(Routes.FB_NUOI_CONFIG) { launchSingleTop = true }
            }
        }
    )
}

@Composable
private fun FeaturedCard(f: FeaturedTool, onConfigure: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color(0x590F172A), spotColor = Color(0x590F172A))
            .background(Alx.White, shape).border(1.dp, Alx.Line, shape).clip(shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                f.badge,
                Modifier.background(Alx.BlueSoft, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                style = type(11f, FontWeight.ExtraBold, Alx.Blue, spacing = 0.1f),
            )
            BasicText(f.meta, style = type(13f, FontWeight.Bold, Alx.Muted))
        }
        BasicText(f.title, Modifier.padding(top = 12.dp), style = type(21f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.02f))
        BasicText(
            f.description,
            Modifier.padding(top = 6.dp),
            style = type(13.5f, FontWeight.Medium, Alx.Ink2, lineHeight = 1.55f),
        )

        FlowStrip(Modifier.padding(top = 16.dp))

        PressBox(
            onClick = onConfigure,
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(50.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Alx.Blue.copy(alpha = .4f), spotColor = Alx.Blue.copy(alpha = .4f))
                .background(Alx.Blue, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(f.cta, style = type(15f, FontWeight.Bold, Color.White))
        }
    }
}

/** Dải 3 bước: Lướt feed (đặc) — Cảm xúc (đặc) — Xem story (viền). Đường nối đầu liền, đường nối sau nét đứt. */
@Composable
private fun FlowStrip(modifier: Modifier) {
    val dashed = Color(0xFF9DB2EE)
    Row(
        modifier.fillMaxWidth().background(Color(0xFFF5F8FF), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FlowStep("Lướt feed", ArrowDown, filled = true)
        Connector(false, dashed)
        FlowStep("Cảm xúc", Heart, filled = true)
        Connector(true, dashed)
        FlowStep("Xem story", PlayCircle, filled = false)
    }
}

@Composable
private fun FlowStep(label: String, icon: List<String>, filled: Boolean) {
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val circle = Modifier.size(36.dp).let {
            if (filled) it.background(Alx.Blue, CircleShape)
            else it.background(Color.White, CircleShape).border(1.5.dp, Color(0xFF9DB2EE), CircleShape)
        }
        Box(circle, contentAlignment = Alignment.Center) {
            SvgIcon(icon, 17.dp, if (filled) Color.White else Alx.Blue, 2f)
        }
        BasicText(label, style = type(11f, FontWeight.Bold, Color(0xFF334155), align = androidx.compose.ui.text.style.TextAlign.Center))
    }
}

/** Đường nối nằm ngang cao 2dp, canh giữa vòng tròn 36dp (lệch 17dp từ đỉnh). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Connector(dashed: Boolean, dashColor: Color) {
    Box(
        Modifier.weight(1f).padding(top = 17.dp, start = 2.dp, end = 2.dp).height(2.dp).drawBehind {
            if (!dashed) {
                drawRect(Alx.Blue)
            } else {
                drawLine(
                    dashColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = size.height, cap = StrokeCap.Butt,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
        },
    )
}

@Composable
private fun ToolRow(t: UtilityTool, divider: Boolean, onClick: () -> Unit) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Alx.Divider))
        PressBox(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(t.tint, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    SvgIcon(t.iconPaths, 21.dp, t.color, 1.8f)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(t.tag, style = type(10.5f, FontWeight.ExtraBold, t.color, spacing = 0.1f))
                    BasicText(t.title, style = type(15f, FontWeight.Bold, Alx.Ink), maxLines = 1)
                    BasicText(t.description, style = type(12f, FontWeight.Medium, Alx.Muted), maxLines = 1)
                }
                SvgIcon(Chevron, 18.dp, Color(0xFF94A3B8), 2f)
            }
        }
    }
}

/** Vùng bấm có hiệu ứng nhún 0.985 / 120 ms, không gợn sóng. */
@Composable
private fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "press")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content() }
}

/* ───────────────────────────── Preview ───────────────────────────── */

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun UtilitiesScreenPreview() {
    MainShell(selected = MainTab.Utilities, onSelect = {}) {
        UtilitiesScreen(UtilitiesUiState())
    }
}
