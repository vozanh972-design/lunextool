package com.cayxu.app.ui.screens.wallet

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.*

private data class WalletTransactionItem(
    val title: String,
    val time: String,
    val amount: String,
    val isPositive: Boolean,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color
)

private val sampleTransactions = listOf(
    WalletTransactionItem(
        title = "Theo dõi Instagram",
        time = "Hôm nay, 10:24",
        amount = "+40 điểm",
        isPositive = true,
        icon = Icons.Outlined.PersonAdd,
        iconBg = Color(0xFFEAF0FF),
        iconTint = Color(0xFF2F6BFF)
    ),
    WalletTransactionItem(
        title = "Thích video TikTok",
        time = "Hôm nay, 09:48",
        amount = "+30 điểm",
        isPositive = true,
        icon = Icons.Outlined.FavoriteBorder,
        iconBg = Color(0xFFEAF0FF),
        iconTint = Color(0xFF2F6BFF)
    ),
    WalletTransactionItem(
        title = "Đổi thẻ điện thoại",
        time = "Hôm qua, 18:12",
        amount = "-1.000 điểm",
        isPositive = false,
        icon = Icons.Outlined.CardGiftcard,
        iconBg = Color(0xFFEDE9FE),
        iconTint = Color(0xFF7C3AED)
    ),
    WalletTransactionItem(
        title = "Điểm mời bạn bè",
        time = "Hôm qua, 14:06",
        amount = "+100 điểm",
        isPositive = true,
        icon = Icons.Outlined.Group,
        iconBg = Color(0xFFEAF0FF),
        iconTint = Color(0xFF2F6BFF)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(navController: NavController) {
    val context = LocalContext.current
    var showWithdrawSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. TIÊU ĐỀ: VÍ ĐIỂM (FIGMA FRAME 22)
            Text(
                text = "Ví điểm",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. THẺ TỔNG ĐIỂM HIỆN CÓ (SAPPHIRE COBALT)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tổng điểm hiện có",
                            fontSize = 14.5.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )

                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "2.450",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. HAI NÚT HÀNH ĐỘNG: ĐỔI QUÀ & RÚT ĐIỂM (FIGMA FRAME 22)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = {
                        Toast.makeText(context, "Tính năng Đổi quà đang cập nhật", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CardGiftcard,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đổi quà",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = { showWithdrawSheet = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardWhite),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NorthEast,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rút điểm",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 4. TIÊU ĐỀ: GIAO DỊCH GẦN ĐÂY
            Text(
                text = "Giao dịch gần đây",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. DANH SÁCH GIAO DỊCH (FIGMA FRAME 22)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sampleTransactions.forEach { tx ->
                    FigmaTransactionItem(item = tx)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // BottomSheet rút tiền (Figma Frame 25: Rút tiền)
        if (showWithdrawSheet) {
            WithdrawBottomSheet(onDismiss = { showWithdrawSheet = false })
        }
    }
}

@Composable
private fun FigmaTransactionItem(item: WalletTransactionItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(item.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.time,
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = item.amount,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.isPositive) Primary else TextPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pointsToWithdraw by remember { mutableStateOf("2000") }
    var selectedMethod by remember { mutableStateOf(1) } // 0: Ví điện tử, 1: Ngân hàng
    var accountNumber by remember { mutableStateOf("") }

    val calculatedMoney = remember(pointsToWithdraw) {
        val points = pointsToWithdraw.toLongOrNull() ?: 0L
        val amount = points * 50
        "%,dđ".format(amount).replace(",", ".")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Rút tiền",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Số điểm muốn quy đổi",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = pointsToWithdraw,
                onValueChange = { pointsToWithdraw = it.filter { ch -> ch.isDigit() } },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Thẻ số tiền tương ứng (Figma Frame 25)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Số tiền tương ứng",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = calculatedMoney,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Chọn phương thức nhận",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Tùy chọn 1: Ví điện tử
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, if (selectedMethod == 0) Primary else BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedMethod = 0 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(InfoBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Ví điện tử", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("MoMo · ZaloPay", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    RadioButton(
                        selected = selectedMethod == 0,
                        onClick = { selectedMethod = 0 },
                        colors = RadioButtonDefaults.colors(selectedColor = Primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tùy chọn 2: Ngân hàng
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardWhite,
                border = BorderStroke(1.5.dp, if (selectedMethod == 1) Primary else BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedMethod = 1 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(InfoBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalance,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Ngân hàng", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Chuyển khoản nội địa", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    RadioButton(
                        selected = selectedMethod == 1,
                        onClick = { selectedMethod = 1 },
                        colors = RadioButtonDefaults.colors(selectedColor = Primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Số tài khoản",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it },
                placeholder = { Text("0123 4567 8928", color = TextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (accountNumber.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập số tài khoản", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Đã gửi yêu cầu rút điểm thành công", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Xác nhận rút",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
