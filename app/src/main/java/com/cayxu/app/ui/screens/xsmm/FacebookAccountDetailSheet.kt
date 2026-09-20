package com.cayxu.app.ui.screens.xsmm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.facebook.FacebookMediaEngine
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookAccountDetailSheet(
    account: FacebookAccount,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentAvatar by remember { mutableStateOf(account.avatar) }
    var currentCover by remember { mutableStateOf(account.cover) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    // Tự động load cover photo và avatar HD khi mở Sheet nếu có token
    androidx.compose.runtime.LaunchedEffect(account.uid, account.bio) {
        val token = account.bio.ifBlank { "" }
        if (token.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val proxyParts = account.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                    val mediaEngine = FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val mediaInfo = mediaEngine.getProfileMedia("me", tokenParam = token)
                    if (mediaInfo != null) {
                        val fetchedCover = mediaInfo.coverUrl.orEmpty()
                        val fetchedAvatar = mediaInfo.avatarUrl.orEmpty()
                        withContext(Dispatchers.Main) {
                            if (fetchedCover.isNotBlank() && fetchedCover != currentCover) {
                                currentCover = fetchedCover
                            }
                            if (fetchedAvatar.isNotBlank() && !fetchedAvatar.contains("84628273_176159830277856")) {
                                currentAvatar = fetchedAvatar
                            }
                            val updatedAcc = account.copy(avatar = currentAvatar, cover = currentCover)
                            FacebookAccountsStore.updateAccount(context, updatedAcc)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Launcher chọn ảnh đại diện (Avatar) qua 100% Graph API (Token)
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val token = account.bio.ifBlank { "" }
            if (token.isBlank()) {
                Toast.makeText(context, "Tài khoản cần có Access Token để đổi Avatar", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isUploadingAvatar = true
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Không thể đọc file ảnh", Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang tải lên Avatar mới...", Toast.LENGTH_SHORT).show()
                    }

                    val proxyParts = account.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                    val mediaEngine = FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val result = mediaEngine.updateUserAvatar(
                        imageBytes = bytes,
                        tokenParam = token
                    )

                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Đổi Avatar thành công!", Toast.LENGTH_SHORT).show()
                            // Lấy lại URL avatar mới trực tiếp từ photoId hoặc qua /me
                            var directUrl: String? = null
                            if (!result.mediaId.isNullOrBlank()) {
                                directUrl = mediaEngine.getPhotoDirectUrl(result.mediaId, tokenParam = token)
                            }
                            if (directUrl.isNullOrBlank()) {
                                val updatedMedia = mediaEngine.getUserMedia(tokenParam = token)
                                directUrl = updatedMedia?.avatarUrl?.takeIf { !it.contains("84628273_176159830277856") }
                            }
                            val newAvatarUrl = directUrl ?: "https://graph.facebook.com/v21.0/me/picture?type=large&access_token=$token&t=${System.currentTimeMillis()}"
                            currentAvatar = newAvatarUrl
                            val updatedAcc = account.copy(avatar = newAvatarUrl, cover = currentCover)
                            FacebookAccountsStore.updateAccount(context, updatedAcc)
                        } else {
                            Toast.makeText(context, "Lỗi đổi Avatar: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Launcher chọn ảnh bìa (Cover) qua 100% Graph API (Token)
    val pickCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val token = account.bio.ifBlank { "" }
            if (token.isBlank()) {
                Toast.makeText(context, "Tài khoản cần có Access Token để đổi Ảnh Bìa", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isUploadingCover = true
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Không thể đọc file ảnh", Toast.LENGTH_SHORT).show()
                            isUploadingCover = false
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang tải lên Ảnh Bìa mới...", Toast.LENGTH_SHORT).show()
                    }

                    val proxyParts = account.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                    val mediaEngine = FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val result = mediaEngine.updateCoverPhoto(
                        imageBytes = bytes,
                        targetId = account.uid,
                        tokenParam = token
                    )

                    withContext(Dispatchers.Main) {
                        isUploadingCover = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Đổi Ảnh Bìa thành công!", Toast.LENGTH_SHORT).show()
                            // Delay nhỏ để Facebook CDN cập nhật trước khi lấy URL mới
                            kotlinx.coroutines.delay(1500)
                            val updatedMedia = mediaEngine.getProfileMedia(account.uid, tokenParam = token)
                            val rawCover = updatedMedia?.coverUrl.orEmpty()
                            val newCoverUrl = if (rawCover.isNotBlank()) {
                                // Cache-bust để Coil không dùng ảnh cũ
                                if (rawCover.contains("?")) "$rawCover&t=${System.currentTimeMillis()}"
                                else "$rawCover?t=${System.currentTimeMillis()}"
                            } else if (result.mediaId != null) {
                                // Fallback: dùng trực tiếp URL ảnh từ photo_id vừa upload
                                "https://graph.facebook.com/v21.0/${result.mediaId}/picture?access_token=$token&t=${System.currentTimeMillis()}"
                            } else {
                                currentCover
                            }
                            if (newCoverUrl != currentCover) currentCover = newCoverUrl
                            val updatedAcc = account.copy(avatar = currentAvatar, cover = currentCover)
                            FacebookAccountsStore.updateAccount(context, updatedAcc)
                        } else {
                            Toast.makeText(context, "Lỗi đổi Ảnh Bìa: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isUploadingCover = false
                        Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun copyToClipboard(label: String, text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Đã sao chép $label", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ========================================================
            // BANNER ẢNH BÌA VÀ AVATAR PROFILE (Đúng chuẩn Facebook App)
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                // 1. Ảnh Bìa (Cover Photo)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1877F2), Color(0xFF0D53B8))
                            )
                        )
                ) {
                    if (currentCover.isNotBlank()) {
                        AsyncImage(
                            model = currentCover,
                            contentDescription = "Cover Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Nút đổi ảnh bìa góc trên phải
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { pickCoverLauncher.launch("image/*") }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Đổi bìa",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isUploadingCover) "Đang tải..." else "Đổi bìa",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 2. Avatar đặt đè lên góc dưới bên trái ảnh bìa
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp)
                        .size(68.dp)
                ) {
                    if (currentAvatar.isNotBlank()) {
                        AsyncImage(
                            model = currentAvatar,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.5.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF1877F2).copy(alpha = 0.15f))
                                .border(2.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color(0xFF1877F2),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Nút camera nhỏ để đổi Avatar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2))
                            .border(1.5.dp, Color.White, CircleShape)
                            .clickable { pickAvatarLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Đổi Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tên và Trạng thái Live/Die
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = account.name.ifBlank { account.uid },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isLive = account.isLive
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isLive) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isLive) "● Live" else "● Die / Checkpoint",
                                color = if (isLive) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(Modifier.height(12.dp))

            Text("Chi tiết tài khoản", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // 1. UID
            InfoRowItem("UID Facebook", account.uid) { copyToClipboard("UID", account.uid) }

            // 2. Email (nếu có)
            if (account.email.isNotBlank()) {
                InfoRowItem("Email", account.email) { copyToClipboard("Email", account.email) }
            }

            // 3. 2FA (nếu có)
            if (account.link.isNotBlank()) {
                InfoRowItem("2FA Secret", account.link) { copyToClipboard("2FA", account.link) }
            }

            // 4. Proxy (nếu có)
            if (account.phone.isNotBlank()) {
                InfoRowItem("Proxy", account.phone) { copyToClipboard("Proxy", account.phone) }
            }

            // 5. Cookie (Hộp text scrollable)
            if (account.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                InfoBoxItem("Cookie", account.note) { copyToClipboard("Cookie", account.note) }
            }

            // 6. Token (Hộp text scrollable)
            if (account.bio.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                InfoBoxItem("Access Token", account.bio) { copyToClipboard("Token", account.bio) }
            }

            // 7. Danh sách Page con — HIỂN THỊ CHÍNH XÁC PAGE UID 615, KHÔNG HIỆN ID PAGE
            Spacer(Modifier.height(16.dp))
            if (account.pages.isEmpty()) {
                Text(
                    text = "Tài khoản không có Page",
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontSize = 12.5.sp,
                    color = TextSecondary.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = "Danh sách Fanpage Pro5 (${account.pages.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))

                account.pages.forEach { page ->
                    // Lấy chính xác UID 615 thật của Page (ưu tiên additionalProfileId nếu là 615 hoặc pageId 615)
                    val uid615 = if (page.additionalProfileId.isNotBlank() && page.additionalProfileId.startsWith("615")) {
                        page.additionalProfileId
                    } else if (page.pageId.startsWith("615")) {
                        page.pageId
                    } else if (page.additionalProfileId.isNotBlank() && !page.additionalProfileId.equals(page.pageId, ignoreCase = true)) {
                        page.additionalProfileId
                    } else {
                        page.displayUid
                    }

                    PageDetailItem(pageName = page.pageName, uid615 = uid615) {
                        copyToClipboard("Page UID", uid615.ifBlank { page.pageId })
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("Đóng", color = CardWhite, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InfoRowItem(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextSecondary, fontSize = 11.5.sp)
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color(0xFF1877F2), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InfoBoxItem(label: String, value: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color(0xFF1877F2), modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

/**
 * Hiển thị Page: Hiển thị Page UID 615, KHÔNG HIỆN ID PAGE
 */
@Composable
private fun PageDetailItem(pageName: String, uid615: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F5F9))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Flag, contentDescription = null, tint = Color(0xFF1877F2), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Page: ${pageName.ifBlank { uid615 }}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = TextPrimary
            )
            // Hiển thị Page UID 615 thật, không hiện id page
            if (uid615.isNotBlank()) {
                Text(
                    text = "UID: $uid615",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1877F2)
                )
            } else {
                Text(
                    text = "UID: Đang quét UID 615...",
                    fontSize = 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = TextSecondary
                )
            }
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy UID", tint = Color(0xFF1877F2), modifier = Modifier.size(14.dp))
        }
    }
}
