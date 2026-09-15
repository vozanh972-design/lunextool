package com.cayxu.app.ui.screens.utilities

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.ui.screens.xsmm.FacebookLoginBottomSheet
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardWhite = Color.White
private val DangerRed = Color(0xFFEF4444)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val CardBorderColor = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegAndTransferPageScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var facebookAccounts by remember {
        mutableStateOf(FacebookAccountsStore.getAccounts(context, forceReload = true))
    }
    var selectedForRunUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFacebookLoginSheet by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var targetFbAvatarChangeUid by remember { mutableStateOf<String?>(null) }
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }

    val pickFbAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val uid = targetFbAvatarChangeUid ?: return@rememberLauncherForActivityResult
        if (uri != null) {
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
                    val acc = facebookAccounts.find { it.uid == uid }
                    if (acc == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Không tìm thấy tài khoản Facebook $uid", Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang đổi ảnh đại diện Facebook...", Toast.LENGTH_SHORT).show()
                    }
                    val fbManager = FacebookAccountManager()
                    val token = acc.bio.ifBlank { null }
                    var newPicUrl: String? = null
                    if (!token.isNullOrBlank()) {
                        newPicUrl = fbManager.changeProfilePicture(token, bytes, acc.phone.ifBlank { null })
                    }
                    val fallbackPic = "https://graph.facebook.com/v19.0/$uid/picture?type=large"
                    val finalAvatar = newPicUrl ?: fallbackPic
                    val updatedAcc = acc.copy(avatar = finalAvatar)
                    FacebookAccountsStore.addAccount(context, updatedAcc)
                    withContext(Dispatchers.Main) {
                        avatarVersion = System.currentTimeMillis()
                        facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                        Toast.makeText(context, "Đổi avatar Facebook thành công!", Toast.LENGTH_SHORT).show()
                        isUploadingAvatar = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi đổi avatar: ${e.message}", Toast.LENGTH_LONG).show()
                        isUploadingAvatar = false
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        targetFbAvatarChangeUid = null
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, CardBorderColor, CircleShape)
                            .background(Color.White)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reg page & Chuyển page",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Danh sách tài khoản Facebook quản lý Page",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF3F5F8)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header section: Tài khoản Facebook + nút Thêm & Xóa
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tài khoản Facebook (${facebookAccounts.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedForRunUids.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        selectedForRunUids.forEach { uid ->
                                            FacebookAccountsStore.removeAccount(context, uid)
                                        }
                                        withContext(Dispatchers.Main) {
                                            selectedForRunUids = emptySet()
                                            facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                            Toast.makeText(context, "Đã xóa tài khoản đã chọn", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(DangerRed.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Xóa đã chọn",
                                        tint = DangerRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Nút thêm tài khoản (+)
                        IconButton(
                            onClick = { showFacebookLoginSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1877F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Thêm tài khoản",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (facebookAccounts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showFacebookLoginSheet = true }
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Chưa có tài khoản Facebook nào.", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Bấm vào đây hoặc nút dấu + để thêm tài khoản Facebook.",
                                color = Color(0xFF1877F2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                items(facebookAccounts, key = { it.uid }) { account ->
                    val isChecked = account.uid in selectedForRunUids
                    val fbAvatarModel = remember(account.avatar, avatarVersion) {
                        if (account.avatar.isBlank()) null
                        else coil.request.ImageRequest.Builder(context)
                            .data(account.avatar)
                            .crossfade(true)
                            .memoryCacheKey("${account.avatar}_$avatarVersion")
                            .diskCacheKey("${account.avatar}_$avatarVersion")
                            .build()
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1877F2)) else androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedForRunUids = if (isChecked) selectedForRunUids - account.uid
                                else selectedForRunUids + account.uid
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedForRunUids = if (checked) selectedForRunUids + account.uid
                                        else selectedForRunUids - account.uid
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1877F2))
                                )
                                Spacer(Modifier.width(6.dp))

                                // Avatar Facebook có nút đổi ảnh cây bút nhỏ nằm bên trong
                                val isThisFbUploading = isUploadingAvatar && targetFbAvatarChangeUid == account.uid
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Color(0xFF1877F2).copy(alpha = 0.6f), CircleShape)
                                        .clickable(enabled = !isUploadingAvatar) {
                                            targetFbAvatarChangeUid = account.uid
                                            pickFbAvatarLauncher.launch("image/*")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (account.avatar.isNotBlank()) {
                                        AsyncImage(
                                            model = fbAvatarModel,
                                            contentDescription = "Avatar Facebook",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF1877F2)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (account.name.firstOrNull() ?: 'F').uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    // Lớp phủ và icon bút sửa ảnh
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(16.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Đổi avatar",
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }

                                    if (isThisFbUploading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            account.name.ifBlank { account.uid },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Badge Live/Die
                                        val isLive = account.isLive
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isLive) Color(0xFF22C55E).copy(alpha = 0.12f) else DangerRed.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isLive) Color(0xFF16A34A) else DangerRed)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (isLive) "Live" else "Die",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLive) Color(0xFF16A34A) else DangerRed
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "UID: ${account.uid}",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Nút Reload (Làm mới)
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            val mgr = FacebookAccountManager()
                                            val token = account.bio.ifBlank { null }
                                            if (!token.isNullOrBlank()) {
                                                try {
                                                    val details = mgr.fetchAccountDetailsWithToken(token, account.phone.ifBlank { null })
                                                    val updated = account.copy(
                                                        name = details.name.ifBlank { account.name },
                                                        avatar = details.avatar.ifBlank { account.avatar },
                                                        email = details.email,
                                                        pages = details.pages,
                                                        isLive = true
                                                    )
                                                    FacebookAccountsStore.addAccount(context, updated)
                                                    withContext(Dispatchers.Main) {
                                                        avatarVersion = System.currentTimeMillis()
                                                        facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                        Toast.makeText(context, "Đã làm mới thông tin: ${updated.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Lỗi kiểm tra Facebook: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else if (account.note.contains("c_user=")) {
                                                try {
                                                    val directAcc = mgr.getTokenFromCookie(account.note, account.phone.ifBlank { null })
                                                    if (directAcc != null && directAcc.isLive) {
                                                        val updated = account.copy(
                                                            name = directAcc.name.ifBlank { account.name },
                                                            avatar = directAcc.avatar.ifBlank { account.avatar },
                                                            bio = directAcc.bio,
                                                            isLive = true
                                                        )
                                                        FacebookAccountsStore.addAccount(context, updated)
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                            Toast.makeText(context, "Đã làm mới thông tin: ${updated.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Lỗi kiểm tra Facebook: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1877F2).copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Làm mới",
                                            tint = Color(0xFF1877F2),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Trạng thái Page
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (account.pages.isEmpty()) {
                                    Text(
                                        "Tài khoản không có page",
                                        fontSize = 11.5.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = TextSecondary.copy(alpha = 0.8f)
                                    )
                                } else {
                                    Text(
                                        "Danh sách Page / Profile+ (${account.pages.size}):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }
                            }

                            if (account.pages.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    account.pages.forEach { page ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF8FAFC))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Flag,
                                                contentDescription = null,
                                                tint = Color(0xFF1877F2),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Page: ${page.pageName}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "ID: ${page.pageId}",
                                                fontSize = 10.5.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFacebookLoginSheet) {
        FacebookLoginBottomSheet(
            onDismiss = { showFacebookLoginSheet = false },
            onAccountSaved = {
                facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                showFacebookLoginSheet = false
            }
        )
    }
}
