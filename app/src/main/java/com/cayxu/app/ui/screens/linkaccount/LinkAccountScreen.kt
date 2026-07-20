package com.cayxu.app.ui.screens.linkaccount

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.DangerRed
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Màn hình DÙNG CHUNG cho các nền tảng KHÔNG PHẢI Facebook (TikTok/Instagram/LinkedIn/...).
 * Facebook có màn hình riêng: xem FacebookLinkAccountScreen.
 *
 * Danh sách UID do người dùng tự nhập ở màn AddAccountScreen - không có mật khẩu/cookie/token.
 */
@Composable
fun LinkAccountScreen(navController: NavController, platform: String, iconRes: Int) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(LinkedAccountsStore.getAccounts(context, platform)) }

    // TODO: 2 dòng seed dưới đây chỉ để demo giao diện danh sách có sẵn vài mục, không phải
    // tài khoản thật. Xoá đoạn seed này khi có API backend quản lý tài khoản liên kết thật.
    LaunchedEffect(platform) {
        if (LinkedAccountsStore.getAccounts(context, platform).isEmpty()) {
            LinkedAccountsStore.addAccount(context, platform, "uid_mau_001 (mẫu)")
            LinkedAccountsStore.addAccount(context, platform, "uid_mau_002 (mẫu)")
            accounts = LinkedAccountsStore.getAccounts(context, platform)
        }
    }

    // Tự làm mới danh sách mỗi khi quay lại màn này (ví dụ sau khi thêm UID mới ở màn kế tiếp).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accounts = LinkedAccountsStore.getAccounts(context, platform)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Liên kết tài khoản",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "$platform (${accounts.size} tài khoản)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))

            if (accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Chưa có tài khoản nào được thêm", color = TextSecondary, fontSize = 13.sp)
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        accounts.forEachIndexed { index, uid ->
                            AccountRow(
                                iconRes = iconRes,
                                uid = uid,
                                onRemove = {
                                    LinkedAccountsStore.removeAccount(context, platform, uid)
                                    accounts = LinkedAccountsStore.getAccounts(context, platform)
                                }
                            )
                            if (index != accounts.lastIndex) {
                                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = { navController.navigate(Routes.addAccount(platform, iconRes)) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Thêm tài khoản", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun AccountRow(iconRes: Int, uid: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(TextSecondary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != 0) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(uid, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Xoá", tint = DangerRed, modifier = Modifier.size(16.dp))
        }
    }
}
