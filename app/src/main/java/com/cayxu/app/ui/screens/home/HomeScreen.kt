package com.cayxu.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cayxu.app.ui.components.CayXuBottomBar
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

    Scaffold(
        containerColor = AppBackground,
        bottomBar = { CayXuBottomBar(navController) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        val info = uiState.info

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header: Mã máy
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(InfoBlueBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = Primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mã máy", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
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

            // Menu 4 nút
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MenuButton(Icons.Filled.SportsEsports, "Cày xu", Modifier.weight(1f)) { }
                MenuButton(Icons.Filled.EventAvailable, "Điểm danh", Modifier.weight(1f)) { }
                MenuButton(Icons.Filled.Assignment, "Nhiệm vụ", Modifier.weight(1f)) { navController.navigate("tasks") }
                MenuButton(Icons.Filled.AccountBalanceWallet, "Rút tiền", Modifier.weight(1f)) { navController.navigate("wallet") }
            }

            Spacer(Modifier.height(20.dp))

            IncomeCard()

            Spacer(Modifier.height(24.dp))

            SectionHeader("Nhiệm vụ nổi bật") { navController.navigate("tasks") }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TaskRow(Icons.Filled.PlayCircle, "Xem quảng cáo", "+500 Xu")
                TaskRow(Icons.Filled.EventAvailable, "Check-in", "+100 Xu")
                TaskRow(Icons.Filled.SportsEsports, "Chơi game", "+1.000 Xu")
                TaskRow(Icons.Filled.PersonAdd, "Mời bạn bè", "+10.000 Xu")
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader("Lịch sử gần đây") { navController.navigate("wallet") }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryRow(Icons.Filled.CheckCircle, SuccessGreen, "Hoàn thành nhiệm vụ", "Nhận thưởng nhiệm vụ", "+2.000 Xu", "Hôm nay, 09:30", true)
                HistoryRow(Icons.Filled.EventAvailable, SuccessGreen, "Điểm danh hàng ngày", "Điểm danh ngày 7", "+500 Xu", "Hôm nay, 08:15", true)
                HistoryRow(Icons.Filled.AccountBalanceWallet, DangerRed, "Rút tiền về ngân hàng", "Rút về MB Bank **** 1234", "-100.000 Xu", "Hôm qua, 21:45", false)
            }

            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun PremiumCard(info: com.cayxu.app.data.model.VerifyKeyResponse) {
    val packageLabel = if (info.packageName == "PRO") "Premium Pro" else "Premium Basic"
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))))
                .padding(20.dp)
        ) {
            Column {
                Text("GÓI PREMIUM", color = Color(0xFFBFDBFE), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(packageLabel, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                    "Hết hạn: ${info.expiresAt ?: "--"}  •  Còn ${info.daysLeft ?: 0} ngày",
                    color = Color(0xFFDCE7FF),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MenuButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .background(CardWhite, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Primary)
        }
        Text(label, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IncomeCard() {
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
            }
            Spacer(Modifier.height(4.dp))
            // Dữ liệu demo minh hoạ giao diện, sẽ được thay bằng dữ liệu thật từ API thu nhập trong tương lai
            Text("+128.500 Xu", color = SuccessGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("↑ 12.5% so với hôm qua", color = SuccessGreen, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
                val points = listOf(0.6f, 0.5f, 0.65f, 0.45f, 0.55f, 0.3f, 0.35f, 0.15f)
                val stepX = size.width / (points.size - 1)
                val path = Path()
                points.forEachIndexed { i, v ->
                    val x = stepX * i
                    val y = size.height * v
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = SuccessGreen, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
            }
        }
    }
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
