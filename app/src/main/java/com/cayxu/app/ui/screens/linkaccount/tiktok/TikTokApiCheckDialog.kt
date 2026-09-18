package com.cayxu.app.ui.screens.linkaccount.tiktok

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cayxu.app.tiktok.checker.TikTokFullProfile
import com.cayxu.app.ui.theme.*

data class ApiCheckLiveItem(
    val initialHandle: String,
    val initialDisplayName: String,
    val uid: String,
    var statusText: String = "Đang chờ...",
    var isRunning: Boolean = false,
    var isDone: Boolean = false,
    var isSuccess: Boolean = false,
    var profile: TikTokFullProfile? = null
)

@Composable
fun TikTokApiCheckLiveDialog(
    items: List<ApiCheckLiveItem>,
    isAllDone: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val total = items.size
    val completedCount = items.count { it.isDone }
    val progress = if (total > 0) completedCount.toFloat() / total.toFloat() else 0f

    Dialog(
        onDismissRequest = {
            if (isAllDone) onDismiss()
        },
        properties = DialogProperties(dismissOnBackPress = isAllDone, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A).copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Đồng bộ tài khoản",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Cập nhật thông tin hồ sơ và trạng thái",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Thanh tiến trình
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAllDone) "Đã hoàn tất đồng bộ" else "Đang kiểm tra hồ sơ...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAllDone) Color(0xFF16A34A) else Color(0xFF2563EB)
                        )
                        Text(
                            text = "$completedCount / $total",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isAllDone) Color(0xFF16A34A) else Color(0xFF2563EB),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Danh sách tài khoản đang được gọi API
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        val prof = item.profile
                        val isLive = prof?.isLive ?: false

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFFCBD5E1), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (prof != null && prof.avatarHdUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(prof.avatarHdUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = (item.initialDisplayName.firstOrNull() ?: 'T').uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            // Chi tiết kết quả trả về từ API
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (prof != null) prof.nickname else item.initialDisplayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (item.isDone) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isLive) Color(0xFF22C55E).copy(alpha = 0.15f)
                                                    else DangerRed.copy(alpha = 0.15f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isLive) "LIVE" else "DIE",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLive) Color(0xFF16A34A) else DangerRed
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "@" + (if (prof != null) prof.username else item.initialHandle),
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )

                                Spacer(Modifier.height(2.dp))

                                // Dòng chi tiết tiến trình API
                                Text(
                                    text = item.statusText,
                                    fontSize = 10.5.sp,
                                    color = when {
                                        item.isRunning -> Color(0xFF2563EB)
                                        item.isDone && isLive -> Color(0xFF16A34A)
                                        item.isDone && !isLive -> DangerRed
                                        else -> TextSecondary
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(6.dp))

                            // Icon trạng thái đang gọi / xong
                            if (item.isRunning) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            } else if (item.isDone) {
                                Icon(
                                    imageVector = if (isLive) Icons.Filled.Check else Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = if (isLive) Color(0xFF16A34A) else DangerRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Nút Đóng / Hoàn tất
                Button(
                    onClick = onDismiss,
                    enabled = isAllDone,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F172A),
                        disabledContainerColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = if (isAllDone) "Hoàn tất" else "Đang đồng bộ...",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
