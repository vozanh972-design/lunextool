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
import com.cayxu.app.facebook.FacebookPageEngine
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookPageDetailSheet(
    parentAccount: FacebookAccount,
    page: FacebookPageItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentAvatar by remember { mutableStateOf(page.avatar) }
    var currentCover by remember { mutableStateOf(page.cover) }
    var resolvedUid by remember { mutableStateOf(if (page.displayUid.startsWith("615")) page.displayUid else "") }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    val effectiveToken = page.pageToken.ifBlank { parentAccount.bio }.trim()

    // Tự động quét UID 615 và load cover/avatar HD của Page khi mở Sheet
    LaunchedEffect(page.pageId, page.pageToken, parentAccount.bio) {
        withContext(Dispatchers.IO) {
            try {
                val proxyParts = parentAccount.phone.ifBlank { null }?.split(":")
                val proxyHost = proxyParts?.getOrNull(0)
                val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                // 1. Quét UID 615 thật nếu hiện tại chưa có
                if (resolvedUid.isBlank() || !resolvedUid.startsWith("615")) {
                    val pageEngine = FacebookPageEngine(
                        accessToken = parentAccount.bio,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val uid615 = pageEngine.fetchProfilePlusIdForPage(
                        pageId = page.pageId,
                        tokenParam = parentAccount.bio,
                        pageTokenParam = page.pageToken
                    )
                    if (!uid615.isNullOrBlank() && uid615.startsWith("615")) {
                        withContext(Dispatchers.Main) {
                            resolvedUid = uid615
                        }
                        FacebookAccountsStore.updatePageUid(context, parentAccount.uid, page.pageId, uid615)
                    }
                }

                // 2. Load cover photo và avatar HD của Page
                if (effectiveToken.isNotBlank()) {
                    val mediaEngine = FacebookMediaEngine(
                        accessToken = effectiveToken,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val targetEndpoint = if (page.pageToken.isNotBlank()) "me" else (resolvedUid.ifBlank { page.pageId })
                    val mediaInfo = mediaEngine.getProfileMedia(targetEndpoint, tokenParam = effectiveToken)
                    if (mediaInfo != null) {
                        val fetchedCover = mediaInfo.coverUrl.orEmpty()
                        val fetchedAvatar = mediaInfo.avatarUrl.orEmpty()
                        withContext(Dispatchers.Main) {
                            if (fetchedCover.isNotBlank()) {
                                currentCover = fetchedCover
                            }
                            if (fetchedAvatar.isNotBlank() && (currentAvatar.isBlank() || currentAvatar.contains("silhouette"))) {
                                currentAvatar = fetchedAvatar
                            }
                        }
                        FacebookAccountsStore.updatePageMedia(
                            context = context,
                            parentUid = parentAccount.uid,
                            pageId = page.pageId,
                            avatar = currentAvatar,
                            cover = currentCover,
                            additionalProfileId = resolvedUid.ifBlank { null }
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Launcher chọn ảnh đại diện (Avatar) cho Page
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (effectiveToken.isBlank()) {
                Toast.makeText(context, "Cần có Token để đổi Avatar cho Page", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Đang tải lên Avatar mới cho Page...", Toast.LENGTH_SHORT).show()
                    }

                    val proxyParts = parentAccount.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                    val mediaEngine = FacebookMediaEngine(
                        accessToken = effectiveToken,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val targetEndpoint = if (page.pageToken.isNotBlank()) "me" else (resolvedUid.ifBlank { page.pageId })
                    val result = mediaEngine.updateAvatar(
                        imageBytes = bytes,
                        targetId = targetEndpoint,
                        tokenParam = effectiveToken
                    )

                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Đổi Avatar Page thành công!", Toast.LENGTH_SHORT).show()
                            kotlinx.coroutines.delay(1500)
                            val updatedMedia = mediaEngine.getProfileMedia(targetEndpoint, tokenParam = effectiveToken)
                            val rawAvatar = updatedMedia?.avatarUrl ?: "https://graph.facebook.com/v21.0/${resolvedUid.ifBlank { page.pageId }}/picture?type=large&access_token=$effectiveToken"
                            val newAvatarUrl = if (rawAvatar.contains("?")) "$rawAvatar&t=${System.currentTimeMillis()}" else "$rawAvatar?t=${System.currentTimeMillis()}"
                            currentAvatar = newAvatarUrl
                            FacebookAccountsStore.updatePageMedia(
                                context = context,
                                parentUid = parentAccount.uid,
                                pageId = page.pageId,
                                avatar = newAvatarUrl,
                                cover = currentCover,
                                additionalProfileId = resolvedUid.ifBlank { null }
                            )
                        } else {
                            Toast.makeText(context, "Lỗi đổi Avatar Page: ${result.message}", Toast.LENGTH_LONG).show()
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

    // Launcher chọn ảnh bìa (Cover) cho Page
    val pickCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (effectiveToken.isBlank()) {
                Toast.makeText(context, "Cần có Token để đổi Ảnh Bìa cho Page", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Đang tải lên Ảnh Bìa mới cho Page...", Toast.LENGTH_SHORT).show()
                    }

                    val proxyParts = parentAccount.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                    val mediaEngine = FacebookMediaEngine(
                        accessToken = effectiveToken,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val targetEndpoint = if (page.pageToken.isNotBlank()) "me" else (resolvedUid.ifBlank { page.pageId })
                    val result = mediaEngine.updateCoverPhoto(
                        imageBytes = bytes,
                        targetId = targetEndpoint,
                        tokenParam = effectiveToken
                    )

                    withContext(Dispatchers.Main) {
                        isUploadingCover = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Đổi Ảnh Bìa Page thành công!", Toast.LENGTH_SHORT).show()
                            kotlinx.coroutines.delay(1500)
                            val updatedMedia = mediaEngine.getProfileMedia(targetEndpoint, tokenParam = effectiveToken)
                            val rawCover = updatedMedia?.coverUrl.orEmpty()
                            val newCoverUrl = if (rawCover.isNotBlank()) {
                                if (rawCover.contains("?")) "$rawCover&t=${System.currentTimeMillis()}"
                                else "$rawCover?t=${System.currentTimeMillis()}"
                            } else if (result.mediaId != null) {
                                "https://graph.facebook.com/v21.0/${result.mediaId}/picture?access_token=$effectiveToken&t=${System.currentTimeMillis()}"
                            } else {
                                currentCover
                            }
                            if (newCoverUrl != currentCover) currentCover = newCoverUrl
                            FacebookAccountsStore.updatePageMedia(
                                context = context,
                                parentUid = parentAccount.uid,
                                pageId = page.pageId,
                                avatar = currentAvatar,
                                cover = currentCover,
                                additionalProfileId = resolvedUid.ifBlank { null }
                            )
                        } else {
                            Toast.makeText(context, "Lỗi đổi Ảnh Bìa Page: ${result.message}", Toast.LENGTH_LONG).show()
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
            // BANNER ẢNH BÌA VÀ AVATAR PAGE (Đúng chuẩn Facebook Page Pro5)
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                // 1. Ảnh Bìa của Page
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
                            contentDescription = "Cover Page",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Nút đổi ảnh bìa Page
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(enabled = !isUploadingCover) { pickCoverLauncher.launch("image/*") }
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

                // 2. Avatar của Page đặt đè lên góc dưới bên trái ảnh bìa
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp)
                        .size(68.dp)
                ) {
                    if (currentAvatar.isNotBlank()) {
                        AsyncImage(
                            model = currentAvatar,
                            contentDescription = "Avatar Page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(3.dp, CardWhite, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF1877F2))
                                .border(3.dp, CardWhite, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Flag,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Nút máy ảnh đổi Avatar Page
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2))
                            .border(1.5.dp, CardWhite, CircleShape)
                            .clickable(enabled = !isUploadingAvatar) { pickAvatarLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploadingAvatar) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Đổi avatar",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tên Page và trạng thái Live
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = page.pageName.ifBlank { "Fanpage Facebook" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF22C55E).copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Live", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ========================================================
            // THÔNG TIN CHI TIẾT PAGE (Ép hiện UID thật 615)
            // ========================================================
            Text(
                text = "Chi tiết Page / Profile+",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))

            val display615 = resolvedUid.ifBlank { if (page.displayUid.startsWith("615")) page.displayUid else "" }
            if (display615.isNotBlank()) {
                InfoRowItem("UID Facebook (UID 615)", display615) { copyToClipboard("UID Page", display615) }
            } else {
                InfoRowItem("UID Facebook (UID 615)", "Đang quét UID 615...") { copyToClipboard("Page ID", page.pageId) }
            }

            if (page.pageId.isNotBlank() && page.pageId != display615) {
                InfoRowItem("ID Page (Gốc)", page.pageId) { copyToClipboard("ID Page", page.pageId) }
            }

            InfoRowItem("Tên Page", page.pageName) { copyToClipboard("Tên Page", page.pageName) }

            InfoRowItem(
                "Tài khoản mẹ (Profile)",
                "${parentAccount.name.ifBlank { parentAccount.uid }} (${parentAccount.uid})"
            ) { copyToClipboard("UID Mẹ", parentAccount.uid) }

            if (page.pageToken.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                InfoBoxItem("Page Access Token", page.pageToken) { copyToClipboard("Page Token", page.pageToken) }
            } else if (parentAccount.bio.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                InfoBoxItem("User Access Token (Dùng chung)", parentAccount.bio) { copyToClipboard("Token", parentAccount.bio) }
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
