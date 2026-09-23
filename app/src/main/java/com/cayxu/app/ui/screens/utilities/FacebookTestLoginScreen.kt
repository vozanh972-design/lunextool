package com.cayxu.app.ui.screens.utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.AppBackground
import com.example.facebooktoken.FacebookToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ScreenPrimary = Color(0xFF0B1730)
private val CardBorderColor = Color(0x140F1E37)
private val GreenColor = Color(0xFF16A34A)
private val RedColor = Color(0xFFDC2626)
private val BlueAccent = Color(0xFF2563EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookTestLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FacebookToken.ProcessedResult>>(emptyList()) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Đã sao chép $label", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "test",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScreenPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = ScreenPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Đăng nhập Facebook (API Direct)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ScreenPrimary
                        )

                        Text(
                            text = "Định dạng: UID|PASS|2FA|DATR hoặc Cookie (mỗi tài khoản 1 dòng)",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "Nhập danh sách tài khoản hoặc cookie...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 220.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isProcessing
                        )

                        Button(
                            onClick = {
                                if (inputText.isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập tài khoản hoặc cookie", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        FacebookToken.process(inputText)
                                    }
                                    results = res
                                    isProcessing = false
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Đang xử lý...", color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Bắt đầu đăng nhập", color = Color.White)
                            }
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    Text(
                        text = "Kết quả (${results.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScreenPrimary
                    )
                }

                items(results) { res ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = res.account,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScreenPrimary
                                )
                                Text(
                                    text = if (res.success) "Thành công" else "Thất bại",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (res.success) GreenColor else RedColor
                                )
                            }

                            if (res.success) {
                                if (!res.uid.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "UID: ${res.uid}",
                                            fontSize = 12.sp,
                                            color = ScreenPrimary
                                        )
                                        IconButton(
                                            onClick = { copyToClipboard("UID", res.uid) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy UID",
                                                tint = BlueAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (!res.token.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Token: ${res.token.take(20)}...",
                                            fontSize = 12.sp,
                                            color = ScreenPrimary
                                        )
                                        IconButton(
                                            onClick = { copyToClipboard("Token", res.token) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy Token",
                                                tint = BlueAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (!res.cookie.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Cookie: ${res.cookie.take(25)}...",
                                            fontSize = 12.sp,
                                            color = ScreenPrimary
                                        )
                                        IconButton(
                                            onClick = { copyToClipboard("Cookie", res.cookie) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy Cookie",
                                                tint = BlueAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Lỗi: ${res.error ?: "Không xác định"}",
                                    fontSize = 12.sp,
                                    color = RedColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
