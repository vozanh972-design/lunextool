package com.cayxu.app.ui.screens.linkaccount

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Chỉ nhập UID (định danh công khai) - KHÔNG có ô mật khẩu/cookie/token, không thu thập
 * thông tin đăng nhập của bất kỳ ai.
 */
@Composable
fun AddAccountScreen(navController: NavController, platform: String, iconRes: Int) {
    val context = LocalContext.current
    var uid by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Thêm tài khoản $platform",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uid,
                onValueChange = { uid = it },
                placeholder = { Text("Nhập UID tài khoản $platform") },
                leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "UID là mã định danh công khai của tài khoản, không phải mật khẩu.",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (uid.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập UID", Toast.LENGTH_SHORT).show()
                    } else {
                        LinkedAccountsStore.addAccount(context, platform, uid)
                        Toast.makeText(context, "Đã thêm tài khoản $platform", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Thêm tài khoản", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
