package com.cayxu.app.ui.screens.utilities

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Save
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
import com.cayxu.app.automation.facebook.nuoi.FbNuoiConfig
import com.cayxu.app.data.local.FbNuoiConfigStore
import com.cayxu.app.ui.theme.*

private val BrandBlue = Color(0xFF1877F2)
private val ScreenBg = Color(0xFFF3F5F8)
private val CardBorderColor = Color(0x140F1E37)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FbNuoiConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val savedConfig = remember { FbNuoiConfigStore.getConfig(context) }

    // Nhóm 1: Tương tác dạo
    var isInteractEnabled by remember { mutableStateOf(savedConfig.isInteractEnabled) }
    var selectedReactions by remember { mutableStateOf(savedConfig.selectedReactions.toSet()) }
    var interactCount by remember { mutableStateOf(savedConfig.interactCount.toString()) }
    var interactDelayMin by remember { mutableStateOf(savedConfig.interactDelayMinSec.toString()) }
    var interactDelayMax by remember { mutableStateOf(savedConfig.interactDelayMaxSec.toString()) }

    // Nhóm 2: Comment dạo
    var isCommentEnabled by remember { mutableStateOf(savedConfig.isCommentEnabled) }
    var commentsText by remember { mutableStateOf(savedConfig.commentList.joinToString("\n")) }
    var commentCount by remember { mutableStateOf(savedConfig.commentCount.toString()) }
    var commentDelayMin by remember { mutableStateOf(savedConfig.commentDelayMinSec.toString()) }
    var commentDelayMax by remember { mutableStateOf(savedConfig.commentDelayMaxSec.toString()) }

    // Nhóm 3: Follow dạo
    var isFollowEnabled by remember { mutableStateOf(savedConfig.isFriendEnabled) }
    var followCount by remember { mutableStateOf(savedConfig.friendCount.toString()) }
    var followDelayMin by remember { mutableStateOf(savedConfig.friendDelayMinSec.toString()) }
    var followDelayMax by remember { mutableStateOf(savedConfig.friendDelayMaxSec.toString()) }

    val reactionOptions = listOf(
        "LIKE" to ("Like" to "👍"),
        "LOVE" to ("Love" to "❤️"),
        "CARE" to ("Care" to "🥰"),
        "HAHA" to ("Haha" to "😆"),
        "WOW" to ("Wow" to "😮")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cấu hình nuôi Facebook",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tương tác dạo, comment dạo & follow dạo",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = ScreenBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================== CARD 1: TƯƠNG TÁC DẠO ==================
            CardSection(
                title = "Tương tác dạo (Lướt Feed & Thả cảm xúc)",
                subtitle = "Tự động lướt bài viết và thả cảm xúc ngẫu nhiên",
                icon = Icons.Filled.FavoriteBorder,
                iconColor = Color(0xFFEF4444),
                isEnabled = isInteractEnabled,
                onToggle = { isInteractEnabled = it }
            ) {
                if (isInteractEnabled) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Chọn các cảm xúc ngẫu nhiên:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reactionOptions.forEach { (code, labelPair) ->
                            val isSelected = code in selectedReactions
                            val (name, emoji) = labelPair
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) BrandBlue.copy(alpha = 0.12f)
                                        else Color(0xFFF1F5F9)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) BrandBlue else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedReactions = if (isSelected) {
                                            if (selectedReactions.size > 1) selectedReactions - code else selectedReactions
                                        } else {
                                            selectedReactions + code
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = emoji, fontSize = 20.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) BrandBlue else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    NumberInputField(
                        label = "Số lượng bài viết tương tác",
                        value = interactCount,
                        onValueChange = { interactCount = it },
                        placeholder = "10",
                        unit = "bài"
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            NumberInputField(
                                label = "Delay tối thiểu",
                                value = interactDelayMin,
                                onValueChange = { interactDelayMin = it },
                                placeholder = "3",
                                unit = "giây"
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            NumberInputField(
                                label = "Delay tối đa",
                                value = interactDelayMax,
                                onValueChange = { interactDelayMax = it },
                                placeholder = "8",
                                unit = "giây"
                            )
                        }
                    }
                }
            }

            // ================== CARD 2: COMMENT DẠO ==================
            CardSection(
                title = "Comment dạo (Tự nhập nội dung tùy ý)",
                subtitle = "Tự động bình luận các bài viết trên Feed",
                icon = Icons.Filled.ChatBubbleOutline,
                iconColor = Color(0xFF10B981),
                isEnabled = isCommentEnabled,
                onToggle = { isCommentEnabled = it }
            ) {
                if (isCommentEnabled) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Danh sách nội dung comment (Mỗi dòng là 1 câu riêng):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = commentsText,
                        onValueChange = { commentsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = {
                            Text(
                                "Chào bạn, chúc ngày mới tốt lành!\nTương tác lại với mình nhé ❤️\nBài viết tuyệt vời quá bạn ơi\nTuyệt vời!",
                                fontSize = 12.sp,
                                color = TextSecondary.copy(alpha = 0.6f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedContainerColor = Color(0xFFFAFAFC),
                            unfocusedContainerColor = Color(0xFFFAFAFC)
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, lineHeight = 18.sp)
                    )

                    Spacer(Modifier.height(14.dp))
                    NumberInputField(
                        label = "Số lượng bài viết comment",
                        value = commentCount,
                        onValueChange = { commentCount = it },
                        placeholder = "3",
                        unit = "bài"
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            NumberInputField(
                                label = "Nghỉ giữa lần cmt (Min)",
                                value = commentDelayMin,
                                onValueChange = { commentDelayMin = it },
                                placeholder = "15",
                                unit = "giây"
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            NumberInputField(
                                label = "Nghỉ giữa lần cmt (Max)",
                                value = commentDelayMax,
                                onValueChange = { commentDelayMax = it },
                                placeholder = "30",
                                unit = "giây"
                            )
                        }
                    }
                }
            }

            // ================== CARD 3: FOLLOW DẠO ==================
            CardSection(
                title = "Follow dạo (Theo dõi & Kết bạn)",
                subtitle = "Tự động bấm theo dõi tác giả bài viết",
                icon = Icons.Filled.PersonAddAlt1,
                iconColor = Color(0xFF6366F1),
                isEnabled = isFollowEnabled,
                onToggle = { isFollowEnabled = it }
            ) {
                if (isFollowEnabled) {
                    Spacer(Modifier.height(14.dp))
                    NumberInputField(
                        label = "Số lượng người follow",
                        value = followCount,
                        onValueChange = { followCount = it },
                        placeholder = "5",
                        unit = "người"
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            NumberInputField(
                                label = "Nghỉ giữa lần fl (Min)",
                                value = followDelayMin,
                                onValueChange = { followDelayMin = it },
                                placeholder = "15",
                                unit = "giây"
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            NumberInputField(
                                label = "Nghỉ giữa lần fl (Max)",
                                value = followDelayMax,
                                onValueChange = { followDelayMax = it },
                                placeholder = "30",
                                unit = "giây"
                            )
                        }
                    }
                }
            }

            // ================== CARD 4: NÚT LƯU CẤU HÌNH ==================
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val commentsList = commentsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val finalReactions = if (selectedReactions.isNotEmpty()) selectedReactions else setOf("LIKE", "LOVE")

                    val newConfig = FbNuoiConfig(
                        isInteractEnabled = isInteractEnabled,
                        selectedReactions = finalReactions,
                        interactCount = interactCount.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                        interactDelayMinSec = interactDelayMin.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                        interactDelayMaxSec = interactDelayMax.toIntOrNull()?.coerceAtLeast(1) ?: 8,

                        isCommentEnabled = isCommentEnabled,
                        commentList = commentsList.ifEmpty {
                            listOf(
                                "Chào bạn, chúc ngày mới tốt lành!",
                                "Tương tác lại với mình nhé ❤️",
                                "Bài viết tuyệt vời quá bạn ơi",
                                "Tuyệt vời!"
                            )
                        },
                        commentCount = commentCount.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                        commentDelayMinSec = commentDelayMin.toIntOrNull()?.coerceAtLeast(1) ?: 15,
                        commentDelayMaxSec = commentDelayMax.toIntOrNull()?.coerceAtLeast(1) ?: 30,

                        isFriendEnabled = isFollowEnabled,
                        friendCount = followCount.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                        friendDelayMinSec = followDelayMin.toIntOrNull()?.coerceAtLeast(1) ?: 15,
                        friendDelayMaxSec = followDelayMax.toIntOrNull()?.coerceAtLeast(1) ?: 30,
                        maxFeedPages = 4
                    )

                    FbNuoiConfigStore.saveConfig(context, newConfig)
                    Toast.makeText(context, "Đã lưu cấu hình nuôi Facebook thành công!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lưu cấu hình",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandBlue
                    )
                )
            }
            content()
        }
    }
}

@Composable
private fun NumberInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    unit: String
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { str ->
                if (str.all { it.isDigit() }) onValueChange(str)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.5f)) },
            trailingIcon = {
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedContainerColor = Color(0xFFFAFAFC),
                unfocusedContainerColor = Color(0xFFFAFAFC)
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}
