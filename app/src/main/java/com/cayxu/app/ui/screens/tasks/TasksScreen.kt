package com.cayxu.app.ui.screens.tasks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.R
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.InfoBlueBg
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

private data class PlatformOption(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
)

private val platformOptions = listOf(
    PlatformOption(
        name = "Golike",
        subtitle = "Nhiệm vụ tăng like, view, follow cho mạng xã hội",
        icon = Icons.Filled.ThumbUp,
        accentColor = Color(0xFF7C3AED)
    ),
    PlatformOption(
        name = "Traodoisub",
        subtitle = "Nhiệm vụ đăng ký kênh, tăng sub, view YouTube",
        icon = Icons.Filled.SwapHoriz,
        accentColor = Color(0xFF2563EB)
    ),
    PlatformOption(
        name = "Tuongtaccheo",
        subtitle = "Nhiệm vụ tương tác chéo giữa các nền tảng",
        icon = Icons.Filled.FavoriteBorder,
        accentColor = Color(0xFFEC4899)
    )
)

@Composable
fun TasksScreen(navController: NavController) {
    var selectedPlatform by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nhiệm vụ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = { /* TODO: hướng dẫn làm nhiệm vụ */ }) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "Trợ giúp", tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(8.dp))

            TaskBannerCard()

            Spacer(Modifier.height(24.dp))

            Text("Chọn nền tảng", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                platformOptions.forEachIndexed { index, option ->
                    PlatformRow(
                        option = option,
                        selected = index == selectedPlatform,
                        onClick = { selectedPlatform = index }
                    )
                }
            }

            Spacer(Modifier.height(90.dp))
        }
}

@Composable
private fun TaskBannerCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.ic_mascot_coin),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(width = 130.dp, height = 110.dp)
            )
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth(0.72f)) {
                Text("Hoàn thành nhiệm vụ", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row {
                    Text("Nhận ", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("xu", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    Text(" mỗi ngày", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Làm nhiệm vụ đơn giản để nhận xu và tăng cấp nhanh hơn!",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PlatformRow(option: PlatformOption, selected: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) option.accentColor.copy(alpha = 0.08f) else CardWhite
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) option.accentColor else Color(0xFFEEF1F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(option.accentColor, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(option.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    option.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (selected) option.accentColor else TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(option.subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
