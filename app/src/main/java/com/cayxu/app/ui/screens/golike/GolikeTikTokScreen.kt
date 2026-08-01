package com.cayxu.app.ui.screens.golike

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.theme.*

/**
 * Màn Golike riêng cho TikTok.
 *
 * Bố cục: 3 loại app TikTok (TikTok / TikTok Lite / TikTok Studio) nằm CÙNG 1 HÀNG dạng tab.
 * Chọn tab nào thì phần giữa hiện danh sách tài khoản ĐÃ BẬT (enabled = true) của đúng loại
 * đó - chỉ đọc lại từ TikTokAccountsStore, KHÔNG cho tick/chọn lại ở đây (tài khoản được bật
 * từ màn Quản lý tài khoản TikTok). Dưới cùng là 2 nút dùng chung "Cấu hình chạy" và "Chạy" -
 * CHƯA gắn logic chạy gì cả, chỉ là 2 nút hiển thị.
 *
 * Không đụng tới TikTokLinkAccountScreen/TikTokAccountsStore hay các màn khác.
 */
private enum class TikTokTab(val label: String, val variant: TikTokAppVariant) {
    STANDARD("TikTok", TikTokAppVariant.STANDARD),
    LITE("TikTok Lite", TikTokAppVariant.LITE),
    STUDIO("TikTok Studio", TikTokAppVariant.STUDIO)
}

@Composable
fun GolikeTikTokScreen(navController: NavController) {
    val context = LocalContext.current
    val allAccounts = remember { mutableStateOf(TikTokAccountsStore.getAccounts(context)) }
    val enabledAccounts = allAccounts.value.filter { it.enabled }

    var selectedTab by remember { mutableStateOf(TikTokTab.STANDARD) }
    val accountsForSelectedTab = enabledAccounts.filter { it.variant == selectedTab.variant }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("TikTok", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // Phần nội dung cuộn riêng - chiều cao co giãn (weight), để 2 nút bên dưới
        // LUÔN cố định ở cuối màn hình, không phụ thuộc nội dung dài/ngắn hay có
        // tài khoản hay không (kể cả "Chưa có tài khoản nào được bật cho loại này").
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            GolikeStatusCard(navController)

            Spacer(Modifier.height(20.dp))
            Text("Loại TikTok", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tài khoản hiển thị bên dưới là tài khoản đã bật ở phần Quản lý tài khoản TikTok, không cần chọn lại ở đây.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            // 3 loại TikTok cùng 1 hàng.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TikTokTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val countForTab = enabledAccounts.count { it.variant == tab.variant }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Primary else CardWhite)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            tab.label,
                            color = if (isSelected) Color.White else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$countForTab tài khoản",
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Phần giữa: danh sách tài khoản đã check của loại đang chọn.
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Tài khoản ${selectedTab.label}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (accountsForSelectedTab.isEmpty()) {
                        Text(
                            "Chưa có tài khoản nào được bật cho loại này.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            accountsForSelectedTab.forEach { account ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(SuccessGreen, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        account.displayName.ifBlank { account.handle.ifBlank { "Chưa xác định" } },
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 2 nút CỐ ĐỊNH ở cuối màn hình - luôn hiện dù không có tài khoản TikTok
        // nào được bật, không nằm trong phần cuộn ở trên nữa. CHƯA gắn logic.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    navController.navigate(
                        com.cayxu.app.ui.navigation.Routes.golikeTikTokConfig(selectedTab.name)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cấu hình chạy")
            }
            Button(
                onClick = { /* Chưa gắn logic - chỉ hiển thị nút */ },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chạy")
            }
        }
    }
}
