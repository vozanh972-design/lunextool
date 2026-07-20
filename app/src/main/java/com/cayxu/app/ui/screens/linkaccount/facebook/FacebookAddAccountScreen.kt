package com.cayxu.app.ui.screens.linkaccount.facebook

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.network.LoginRequest
import com.cayxu.app.network.RetrofitClient
import com.cayxu.app.ui.theme.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun FacebookAddAccountScreen(navController: NavController) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }

    // State cho nhập 1 tài khoản
    var singleUid by remember { mutableStateOf("") }
    var singlePassword by remember { mutableStateOf("") }
    var singleTwoFa by remember { mutableStateOf("") }

    // State cho nhập nhiều (giữ nguyên nếu cần, nhưng đơn giản hóa)
    var multiText by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Thêm tài khoản Facebook",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = Primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "UID là email hoặc số điện thoại. Password và 2FA (nếu có) sẽ được gửi lên server để xác thực.",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tab chọn chế độ (1 hoặc nhiều)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .padding(4.dp)
            ) {
                SegmentButton(
                    text = "Nhập UID",
                    selected = tabIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 0 }
                )
                Spacer(Modifier.width(4.dp))
                SegmentButton(
                    text = "Nhập nhiều UID",
                    selected = tabIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 1 }
                )
            }

            Spacer(Modifier.height(20.dp))

            if (tabIndex == 0) {
                // Nhập 1 tài khoản
                InputField(
                    label = "UID (Email)",
                    value = singleUid,
                    onValueChange = { singleUid = it },
                    placeholder = "Nhập email hoặc số điện thoại",
                    isRequired = true
                )
                InputField(
                    label = "Password",
                    value = singlePassword,
                    onValueChange = { singlePassword = it },
                    placeholder = "Nhập mật khẩu",
                    isRequired = true,
                    isPassword = true
                )
                InputField(
                    label = "2FA Secret (nếu có)",
                    value = singleTwoFa,
                    onValueChange = { singleTwoFa = it },
                    placeholder = "Nhập mã bí mật 2FA (không bắt buộc)",
                    isRequired = false
                )
            } else {
                // Nhập nhiều (đơn giản: mỗi dòng email|password|2fa)
                Text("Nhập nhiều tài khoản", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mỗi dòng: email|password|2fa (2fa có thể để trống)",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = multiText,
                    onValueChange = { multiText = it },
                    placeholder = { Text("example@email.com|pass123|2FASECRET") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }

        // Nút Xác nhận
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (isLoading) return@Button
                    if (tabIndex == 0) {
                        val email = singleUid.trim()
                        val password = singlePassword.trim()
                        if (email.isEmpty() || password.isEmpty()) {
                            Toast.makeText(context, "Email và Password là bắt buộc", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        val request = LoginRequest(
                            email = email,
                            password = password,
                            auth = singleTwoFa.trim().takeIf { it.isNotEmpty() }
                        )
                        RetrofitClient.apiService.login(request).enqueue(object : Callback<LoginResponse> {
                            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                                isLoading = false
                                if (response.isSuccessful && response.body()?.success == true) {
                                    val result = response.body()!!
                                    FacebookAccountsStore.addAccount(
                                        context,
                                        uid = result.uid ?: email,
                                        name = "User ${result.uid}",
                                        token = result.token ?: ""
                                    )
                                    Toast.makeText(context, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                } else {
                                    val msg = response.body()?.error ?: "Lỗi không xác định"
                                    Toast.makeText(context, "Lỗi: $msg", Toast.LENGTH_SHORT).show()
                                }
                            }
                            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                                isLoading = false
                                Toast.makeText(context, "Lỗi kết nối: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                    } else {
                        // Nhập nhiều
                        val lines = multiText.lines().filter { it.isNotBlank() }
                        if (lines.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập ít nhất một tài khoản", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val accounts = lines.mapNotNull { line ->
                            val parts = line.split("|").map { it.trim() }
                            if (parts.size >= 2) {
                                LoginRequest(
                                    email = parts[0],
                                    password = parts[1],
                                    auth = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
                                )
                            } else null
                        }
                        if (accounts.isEmpty()) {
                            Toast.makeText(context, "Định dạng không hợp lệ", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        val request = MultipleLoginRequest(accounts)
                        RetrofitClient.apiService.loginMultiple(request).enqueue(object : Callback<MultipleLoginResponse> {
                            override fun onResponse(call: Call<MultipleLoginResponse>, response: Response<MultipleLoginResponse>) {
                                isLoading = false
                                if (response.isSuccessful) {
                                    val results = response.body()?.results ?: emptyList()
                                    val successList = results.filter { it.success }
                                    successList.forEach {
                                        FacebookAccountsStore.addAccount(
                                            context,
                                            uid = it.uid ?: "",
                                            name = "User ${it.uid}",
                                            token = it.token ?: ""
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        "Thành công ${successList.size}/${results.size}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "Lỗi server", Toast.LENGTH_SHORT).show()
                                }
                            }
                            override fun onFailure(call: Call<MultipleLoginResponse>, t: Throwable) {
                                isLoading = false
                                Toast.makeText(context, "Lỗi kết nối: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CardWhite)
                } else {
                    Text("Xác nhận", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ========== Component hỗ trợ ==========
@Composable
private fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isRequired: Boolean = false,
    isPassword: Boolean = false
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Row {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (isRequired) {
                Spacer(Modifier.width(4.dp))
                Text("*", color = DangerRed, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else CardWhite)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) CardWhite else TextSecondary
        )
    }
}
