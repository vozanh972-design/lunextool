package com.cayxu.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.*

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            Toast.makeText(context, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    val info = uiState.info

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
            // Header: Mã máy
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(com.cayxu.app.R.drawable.ic_app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("CAYXU", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(uiState.androidId, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("android_id", uiState.androidId))
                    Toast.makeText(context, "Đã sao chép mã máy", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Sao chép", tint = Primary)
                }
                IconButton(onClick = { /* TODO: thông báo */ }) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (info != null) {
                PremiumCard(info)
            }

            Spacer(Modifier.height(20.dp))

            // Menu 4 nút, xếp dạng lưới 2x2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuButton(
                        icon = Icons.Filled.Widgets,
                        label = "Tiện ích",
                        subtitle = "Khám phá công cụ hữu ích",
                        accentColor = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f)
                    ) { }
                    MenuButton(
                        icon = Icons.Filled.Settings,
                        label = "Cài đặt",
                        subtitle = "Cấu hình ứng dụng",
                        accentColor = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate(com.cayxu.app.ui.navigation.Routes.SETTINGS) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuButton(
                        icon = Icons.Filled.Assignment,
                        label = "Nhiệm vụ",
                        subtitle = "Hoàn thành nhiệm vụ để nhận xu",
                        accentColor = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate("tasks") { launchSingleTop = true } }
                    MenuButton(
                        icon = Icons.Filled.AccountBalanceWallet,
                        label = "Ví",
                        subtitle = "Quản lý xu và giao dịch",
                        accentColor = Color(0xFFF97316),
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate("wallet") { launchSingleTop = true } }
                }
            }

            Spacer(Modifier.height(20.dp))

            IncomeCard()

            Spacer(Modifier.height(24.dp))

            SectionHeader("Lịch sử gần đây") { navController.navigate("wallet") { launchSingleTop = true } }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryRow(Icons.Filled.CheckCircle, SuccessGreen, "Hoàn thành nhiệm vụ", "Nhận thưởng nhiệm vụ", "+2.000 Xu", "Hôm nay, 09:30", true)
                HistoryRow(Icons.Filled.EventAvailable, SuccessGreen, "Điểm danh hàng ngày", "Điểm danh ngày 7", "+500 Xu", "Hôm nay, 08:15", true)
                HistoryRow(Icons.Filled.AccountBalanceWallet, DangerRed, "Rút tiền về ngân hàng", "Rút về MB Bank **** 1234", "-100.000 Xu", "Hôm qua, 21:45", false)
            }

            Spacer(Modifier.height(90.dp))
        }
}

@Composable
private fun PremiumCard(info: com.cayxu.app.data.model.VerifyKeyResponse) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF418DFC), Color(0xFF7950F6))))
        ) {
            Image(
                painter = painterResource(com.cayxu.app.R.drawable.ic_crown_premium),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 22.dp, y = (-18).dp)
                    .size(width = 150.dp, height = 130.dp)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Text("GÓI", color = Color(0xFFE0D4FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text("Premium", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF22C55E).copy(alpha = 0.2f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Còn hạn", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${info.daysLeft ?: 0} ngày", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hết hạn: ${info.expiresAt ?: "--"}",
                    color = Color(0xFFE5DCFF),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .heightIn(min = 128.dp)
            .background(CardWhite, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(accentColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            fontSize = 10.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            lineHeight = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.heightIn(min = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(3.dp)
                .background(accentColor, RoundedCornerShape(50))
        )
    }
}

@Composable
private fun IncomeCard() {
    // TODO: các giá trị Xu bên dưới vẫn là dữ liệu mẫu vì backend hiện chỉ có
    // endpoint verify_key.php, chưa có API trả về thu nhập thật theo ngày.
    // Khi có API thu nhập, thay `points` bằng dữ liệu lấy từ ViewModel/API.
    val points = remember { listOf(52000f, 91000f, 38000f, 70000f, 55000f, 92000f, 128500f) }
    val dayLabels = remember { last7DayLabels() }

    var rangeMenuExpanded by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf("7 ngày") }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Thu nhập hôm nay", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(AppBackground, RoundedCornerShape(8.dp))
                            .clickable { rangeMenuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(selectedRange, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = rangeMenuExpanded, onDismissRequest = { rangeMenuExpanded = false }) {
                        listOf("7 ngày", "14 ngày", "30 ngày").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                selectedRange = option
                                rangeMenuExpanded = false
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("+128.500 Xu", color = SuccessGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("↑ 12.5% so với hôm qua", color = SuccessGreen, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            val maxValue = ((points.max() / 50000f).let { kotlin.math.ceil(it) } * 50000f).coerceAtLeast(50000f)

            Row(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.width(34.dp).height(110.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatCompactXu(maxValue), color = TextSecondary, fontSize = 9.sp)
                    Text(formatCompactXu(maxValue * 2 / 3), color = TextSecondary, fontSize = 9.sp)
                    Text(formatCompactXu(maxValue / 3), color = TextSecondary, fontSize = 9.sp)
                    Text("0", color = TextSecondary, fontSize = 9.sp)
                }
                Canvas(modifier = Modifier.weight(1f).height(110.dp)) {
                    val stepX = size.width / (points.size - 1)
                    val gridColor = androidx.compose.ui.graphics.Color(0xFFE5E9F0)
                    // Lưới ngang tại 0%, 33%, 66%, 100%
                    for (i in 0..3) {
                        val y = size.height * (1f - i / 3f)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                    }

                    val linePath = Path()
                    val fillPath = Path()
                    val coords = points.mapIndexed { i, v ->
                        Offset(stepX * i, size.height * (1f - v / maxValue))
                    }
                    coords.forEachIndexed { i, p ->
                        if (i == 0) {
                            linePath.moveTo(p.x, p.y)
                            fillPath.moveTo(p.x, size.height)
                            fillPath.lineTo(p.x, p.y)
                        } else {
                            linePath.lineTo(p.x, p.y)
                            fillPath.lineTo(p.x, p.y)
                        }
                    }
                    fillPath.lineTo(coords.last().x, size.height)
                    fillPath.close()

                    drawPath(
                        fillPath,
                        brush = Brush.verticalGradient(
                            listOf(SuccessGreen.copy(alpha = 0.28f), SuccessGreen.copy(alpha = 0f))
                        )
                    )
                    drawPath(linePath, color = SuccessGreen, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
                    coords.forEach { p ->
                        drawCircle(color = CardWhite, radius = 7f, center = p)
                        drawCircle(color = SuccessGreen, radius = 4.5f, center = p)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 34.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEach { label ->
                    Text(label, color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun last7DayLabels(): List<String> {
    val fmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
    val cal = java.util.Calendar.getInstance()
    val labels = mutableListOf<String>()
    repeat(7) {
        labels.add(0, fmt.format(cal.time))
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    return labels
}

private fun formatCompactXu(value: Float): String {
    if (value <= 0f) return "0"
    val k = value / 1000f
    return if (k == k.toLong().toFloat()) "${k.toLong()}K" else String.format(java.util.Locale.getDefault(), "%.1fK", k)
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Row(
            modifier = Modifier.clickable { onSeeAll() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Xem tất cả", color = Primary, fontSize = 13.sp)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TaskRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, reward: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).background(InfoBlueBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Text(reward, color = SuccessGreen, fontSize = 12.sp)
            }
            Button(
                onClick = { /* TODO: thực hiện nhiệm vụ */ },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InfoBlueBg, contentColor = Primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Làm ngay", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HistoryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, title: String, subtitle: String, amount: String, time: String, isPositive: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = if (isPositive) SuccessGreen else DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(time, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
