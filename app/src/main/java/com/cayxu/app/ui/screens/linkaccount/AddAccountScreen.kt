package com.cayxu.app.ui.screens.linkaccount

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAccountScreen(navController: NavController, platform: String, iconRes: Int) {
    val context = LocalContext.current
    var isSingleAccountMode by remember { mutableStateOf(true) }

    // --- State: Nhập 1 tài khoản ---
    var uid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var twoFa by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var proxy by remember { mutableStateOf("") }

    // --- State: Nhập hàng loạt ---
    val availableFormats = listOf("UID", "Password", "2FA", "Cookie", "Token", "Proxy")
    var selectedFormats by remember { mutableStateOf(listOf("UID")) }
    var bulkText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // --- Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 20.dp)
        ) {
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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Banner thông báo ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AssignmentInd, contentDescription = null, tint = Primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Chỉ cần nhập UID (mã định danh công khai) của tài khoản $platform, không cần mật khẩu.",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // --- Switch Tab (1 Account / Bulk) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite, RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSingleAccountMode) Primary else Color.Transparent)
                        .clickable { isSingleAccountMode = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nhập UID",
                        color = if (isSingleAccountMode) CardWhite else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isSingleAccountMode) Primary else Color.Transparent)
                        .clickable { isSingleAccountMode = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nhập nhiều UID",
                        color = if (!isSingleAccountMode) CardWhite else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isSingleAccountMode) {
                // ==========================================
                // FORM: NHẬP 1 TÀI KHOẢN
                // ==========================================
                InputFieldWithLabel("UID", uid, "Nhập UID tài khoản $platform", "UID là mã định danh công khai của tài khoản, không phải mật khẩu.") { uid = it }
                InputFieldWithLabel("Password", password, "Nhập password (bắt buộc)", null) { password = it }
                InputFieldWithLabel("Link", twoFa, "Nhập 2FA (bắt buộc)", "Mã 2FA (Xác thực 2 yếu tố).") { twoFa = it }
                InputFieldWithLabel("Cookie", cookie, "Cookie (không bắt buộc)", null, isMultiline = true) { cookie = it }
                InputFieldWithLabel("Token", token, "Token (không bắt buộc, dùng nếu không có UID|PASS|2FA)", null, isMultiline = true) { token = it }
                InputFieldWithLabel("Proxy", proxy, "Nhập proxy (không bắt buộc)", null) { proxy = it }
            } else {
                // ==========================================
                // FORM: NHẬP HÀNG LOẠT
                // ==========================================
                Text("Danh sách UID", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Chọn các trường và thứ tự phân tách bằng dấu \"|\"", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableFormats.forEach { format ->
                        val isSelected = selectedFormats.contains(format)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Primary else CardWhite)
                                .clickable {
                                    if (isSelected) {
                                        if (selectedFormats.size > 1) {
                                            selectedFormats = selectedFormats.filter { it != format }
                                        }
                                    } else {
                                        selectedFormats = selectedFormats + format
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSelected) "${selectedFormats.indexOf(format) + 1}. $format" else format,
                                color = if (isSelected) CardWhite else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Định dạng hiện tại: ${selectedFormats.joinToString(" | ")}",
                    color = Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = bulkText,
                    onValueChange = { bulkText = it },
                    placeholder = { Text("Mỗi dòng phân tách bằng \"|\" theo đúng thứ tự đã chọn, ví dụ:\n100000001234567|Pass123|2FACODE") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    )
                )
                Spacer(Modifier.height(8.dp))
                val accountCount = bulkText.lines().filter { it.isNotBlank() }.size
                Text(
                    "Đã nhập $accountCount tài khoản. Mỗi dòng phân tách bằng dấu \"|\" theo đúng thứ tự trường đã chọn ở trên.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        // ==========================================
        // LOGIC XÁC NHẬN VÀ LƯU TÀI KHOẢN THẬT
        // ==========================================
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (isSingleAccountMode) {
                        // --- Logic Single Account ---
                        if (uid.isBlank() && token.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập UID hoặc Token!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val targetUid = uid.ifBlank { token.take(15) }
                        LinkedAccountsStore.addAccount(context, platform, targetUid)
                        
                        Toast.makeText(context, "Đã thêm tài khoản: $targetUid", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    } else {
                        // --- Logic Bulk Account ---
                        val lines = bulkText.lines().filter { it.isNotBlank() }
                        if (lines.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập danh sách tài khoản!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        var addedCount = 0
                        lines.forEach { line ->
                            val parts = line.split("|").map { it.trim() }
                            var parsedUid = ""

                            selectedFormats.forEachIndexed { index, format ->
                                val valAtPos = parts.getOrNull(index) ?: ""
                                if (format == "UID" && valAtPos.isNotBlank()) {
                                    parsedUid = valAtPos
                                } else if (format == "Token" && parsedUid.isBlank() && valAtPos.isNotBlank()) {
                                    parsedUid = valAtPos.take(15)
                                }
                            }

                            if (parsedUid.isNotBlank()) {
                                LinkedAccountsStore.addAccount(context, platform, parsedUid)
                                addedCount++
                            }
                        }

                        if (addedCount > 0) {
                            Toast.makeText(context, "Đã thêm thành công $addedCount tài khoản thật!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } else {
                            Toast.makeText(context, "Không định dạng được UID nào hợp lệ!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Xác nhận", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun InputFieldWithLabel(
    label: String,
    value: String,
    placeholder: String,
    helperText: String?,
    isMultiline: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            singleLine = !isMultiline,
            minLines = if (isMultiline) 3 else 1,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (helperText != null) {
            Spacer(Modifier.height(4.dp))
            Text(helperText, fontSize = 11.sp, color = TextSecondary)
        }
    }
}
