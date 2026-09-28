package com.cayxu.app.ui.screens.utilities

import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.screens.xsmm.FacebookAccountDetailSheet
import com.cayxu.app.ui.screens.xsmm.FacebookLoginBottomSheet
import com.cayxu.app.ui.screens.xsmm.FacebookPageDetailSheet
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BrandBlue = Color(0xFF1877F2)
private val ScreenBg = Color(0xFFF3F5F8)
private val CardBorderColor = Color(0x140F1E37)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookNurtureScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var facebookAccounts by remember {
        mutableStateOf(FacebookAccountsStore.getAccounts(context, forceReload = true))
    }
    var selectedForRunUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }

    var liveFbAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageUids by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageCovers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var showFacebookLoginSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }
    var selectedFbDetailAccount by remember { mutableStateOf<FacebookAccount?>(null) }
    var selectedFbDetailPage by remember { mutableStateOf<Pair<FacebookAccount, FacebookPageItem>?>(null) }
    var selectedErrorDetailAccount by remember { mutableStateOf<String?>(null) }

    var targetFbAvatarChangeUid by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val runningFbAccounts = com.cayxu.app.automation.facebook.XsmmFacebookManager.runningAccounts
    val fbStatusMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.statusMap
    val fbSuccessCountMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.successCountMap
    val fbErrorCountMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.errorCountMap
    val fbErrorDetailMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.lastErrorDetail

    val pickFbAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
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
                    val acc = FacebookAccountsStore.getAccount(context, uid)
                    if (acc == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Không tìm thấy tài khoản Facebook $uid", Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    val proxyParts = acc.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                    val mediaEngine = com.cayxu.app.facebook.FacebookMediaEngine(
                        accessToken = acc.bio.trim(),
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang tải ảnh lên Facebook...", Toast.LENGTH_SHORT).show()
                    }
                    val result = mediaEngine.updateAvatar(bytes)
                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        if (result.success) {
                            val newUrl = result.avatarUrl.ifBlank { "https://graph.facebook.com/v21.0/$uid/picture?type=large" }
                            liveFbAvatars = liveFbAvatars + (uid to newUrl)
                            val updated = acc.copy(avatar = newUrl)
                            FacebookAccountsStore.addAccount(context, updated)
                            facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                            avatarVersion = System.currentTimeMillis()
                            Toast.makeText(context, "Đổi avatar Facebook thành công!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lỗi đổi avatar: ${result.message}", Toast.LENGTH_LONG).show()
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

    LaunchedEffect(showFacebookLoginSheet) {
        if (!showFacebookLoginSheet) {
            facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky Top Bar
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
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nuôi tài khoản Facebook",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tự động tương tác, lướt feed & chăm sóc nick",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { showFacebookLoginSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(BrandBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Thêm Facebook",
                                tint = BrandBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Main List
            if (facebookAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFacebookLoginSheet = true }
                    ) {
                        Column(
                            Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(BrandBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Chưa có tài khoản Facebook nào",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Bấm vào đây để đăng nhập tài khoản Facebook",
                                color = BrandBlue,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(facebookAccounts, key = { it.uid }) { account ->
                        val isChecked = account.uid in selectedForRunUids
                        val isRunningFbThis = com.cayxu.app.automation.facebook.XsmmFacebookManager.isRunning(account.uid)
                        val currentFbAvatar = liveFbAvatars[account.uid] ?: account.avatar
                        val fbAvatarModel = remember(currentFbAvatar, avatarVersion) {
                            if (currentFbAvatar.isBlank()) null
                            else coil.request.ImageRequest.Builder(context)
                                .data(currentFbAvatar)
                                .crossfade(true)
                                .memoryCacheKey("${currentFbAvatar}_$avatarVersion")
                                .diskCacheKey("${currentFbAvatar}_$avatarVersion")
                                .build()
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, BrandBlue) else androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
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
                                        colors = CheckboxDefaults.colors(checkedColor = BrandBlue)
                                    )
                                    Spacer(Modifier.width(6.dp))

                                    // Avatar Facebook
                                    val isThisFbUploading = isUploadingAvatar && targetFbAvatarChangeUid == account.uid
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, BrandBlue.copy(alpha = 0.6f), CircleShape)
                                            .clickable(enabled = !isUploadingAvatar) {
                                                targetFbAvatarChangeUid = account.uid
                                                pickFbAvatarLauncher.launch("image/*")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentFbAvatar.isNotBlank()) {
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
                                                    .background(BrandBlue),
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

                                        // Icon bút sửa ảnh
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

                                    // Nút Reload
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val mgr = com.cayxu.app.facebook.FacebookAccountManager()
                                                try {
                                                    if (account.note.contains("c_user=")) {
                                                        val directAcc = mgr.getTokenFromCookie(account.note, account.phone.ifBlank { null })
                                                        if (directAcc != null && directAcc.isLive) {
                                                            val updated = account.copy(
                                                                name = directAcc.name.ifBlank { account.name },
                                                                avatar = directAcc.avatar.ifBlank { account.avatar },
                                                                bio = directAcc.bio.ifBlank { account.bio },
                                                                pages = directAcc.pages.ifEmpty { account.pages },
                                                                isLive = true
                                                            )
                                                            FacebookAccountsStore.addAccount(context, updated)
                                                        } else {
                                                            FacebookAccountsStore.addAccount(context, account.copy(isLive = false))
                                                        }
                                                    } else {
                                                        val token = account.bio.ifBlank { null }
                                                        if (!token.isNullOrBlank()) {
                                                            val details = mgr.fetchAccountDetailsWithToken(token, account.phone.ifBlank { null })
                                                            val updated = account.copy(
                                                                name = details.name.ifBlank { account.name },
                                                                avatar = details.avatar.ifBlank { account.avatar },
                                                                email = details.email,
                                                                pages = details.pages.ifEmpty { account.pages },
                                                                isLive = true
                                                            )
                                                            FacebookAccountsStore.addAccount(context, updated)
                                                        } else {
                                                            FacebookAccountsStore.addAccount(context, account.copy(isLive = false))
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                    FacebookAccountsStore.addAccount(context, account.copy(isLive = false))
                                                }

                                                withContext(Dispatchers.Main) {
                                                    avatarVersion = System.currentTimeMillis()
                                                    facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                                                    Toast.makeText(context, "Đã làm mới thông tin Facebook", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(BrandBlue.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = "Làm mới",
                                                tint = BrandBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Nút Chạy / Dừng
                                    IconButton(
                                        onClick = {
                                            if (isRunningFbThis) {
                                                com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(account.uid)
                                                Toast.makeText(context, "Đã dừng chạy: ${account.name.ifBlank { account.uid }}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                com.cayxu.app.automation.facebook.XsmmFacebookManager.start(context, account.uid)
                                                Toast.makeText(context, "Bắt đầu chạy: ${account.name.ifBlank { account.uid }}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (isRunningFbThis) DangerRed else BrandBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isRunningFbThis) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(Color.White)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Chạy",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                val fbStatus = fbStatusMap[account.uid]
                                val fbSuccess = fbSuccessCountMap[account.uid] ?: 0
                                val fbErrors = fbErrorCountMap[account.uid] ?: 0
                                val fbErrDetail = fbErrorDetailMap[account.uid]
                                if (!fbStatus.isNullOrBlank() || fbSuccess > 0 || fbErrors > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isRunningFbThis) {
                                                    CircularProgressIndicator(
                                                        color = BrandBlue,
                                                        strokeWidth = 1.6.dp,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                }
                                                Text(
                                                    text = fbStatus ?: "Sẵn sàng",
                                                    fontSize = 11.5.sp,
                                                    color = if (isRunningFbThis) BrandBlue else TextSecondary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (fbSuccess > 0 || fbErrors > 0 || !fbErrDetail.isNullOrBlank()) {
                                                Spacer(Modifier.width(6.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (fbSuccess > 0) {
                                                        Text("+$fbSuccess", color = Color(0xFF16A34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    if (fbErrors > 0) {
                                                        Text("-$fbErrors", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    if (fbErrors > 0 || !fbErrDetail.isNullOrBlank()) {
                                                        IconButton(
                                                            onClick = { selectedErrorDetailAccount = account.uid },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(20.dp)
                                                                    .clip(CircleShape)
                                                                    .background(DangerRed.copy(alpha = 0.15f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Warning,
                                                                    contentDescription = "Xem chi tiết lỗi",
                                                                    tint = DangerRed,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
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

                                    IconButton(
                                        onClick = { selectedFbDetailAccount = account },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(BrandBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Info,
                                                contentDescription = "Xem thông tin chi tiết",
                                                tint = BrandBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (account.pages.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        account.pages.forEach { page ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFF8FAFC))
                                                    .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                val pageDisplayUid = livePageUids[page.pageId] ?: page.displayUid
                                                val effectivePageUid = (page.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                                                    ?: pageDisplayUid.takeIf { it.isNotBlank() && it.startsWith("615") }
                                                    ?: page.additionalProfileId.takeIf { it.isNotBlank() }
                                                    ?: pageDisplayUid.takeIf { it.isNotBlank() }
                                                    ?: page.pageId).trim()

                                                val isPageRunning = runningFbAccounts.any {
                                                    it.equals(effectivePageUid, ignoreCase = true) ||
                                                    it.equals(page.pageId, ignoreCase = true) ||
                                                    (page.additionalProfileId.isNotBlank() && it.equals(page.additionalProfileId, ignoreCase = true)) ||
                                                    (pageDisplayUid.isNotBlank() && it.equals(pageDisplayUid, ignoreCase = true))
                                                }

                                                val isPageChecked = effectivePageUid in selectedForRunUids ||
                                                    page.pageId in selectedForRunUids ||
                                                    (page.additionalProfileId.isNotBlank() && page.additionalProfileId in selectedForRunUids) ||
                                                    (pageDisplayUid.isNotBlank() && pageDisplayUid in selectedForRunUids)

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isPageChecked,
                                                        onCheckedChange = { checked ->
                                                            selectedForRunUids = if (checked) {
                                                                selectedForRunUids + effectivePageUid
                                                            } else {
                                                                selectedForRunUids - effectivePageUid - page.pageId - page.additionalProfileId - pageDisplayUid
                                                            }
                                                        },
                                                        colors = CheckboxDefaults.colors(checkedColor = BrandBlue),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))

                                                    val avatarToDisplay = livePageAvatars[page.pageId] ?: (
                                                        if (page.avatar.isNotBlank() && !page.avatar.contains("silhouette") && !page.avatar.endsWith(".gif") && !page.avatar.contains(page.displayUid)) page.avatar
                                                        else "https://graph.facebook.com/v21.0/${page.pageId}/picture?type=large"
                                                    )
                                                    if (avatarToDisplay.isNotBlank()) {
                                                        AsyncImage(
                                                            model = avatarToDisplay,
                                                            contentDescription = "Page Avatar",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clip(CircleShape)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clip(CircleShape)
                                                                .background(BrandBlue.copy(alpha = 0.15f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Flag,
                                                                contentDescription = null,
                                                                tint = BrandBlue,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(8.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        val uid615 = effectivePageUid
                                                        Text(
                                                            "Page: ${page.pageName.ifBlank { uid615.ifBlank { page.pageId } }}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (uid615.isNotBlank()) {
                                                            Text(
                                                                "UID: $uid615",
                                                                fontSize = 10.sp,
                                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                                color = TextSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = { selectedFbDetailPage = Pair(account, page) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(CircleShape)
                                                                .background(BrandBlue.copy(alpha = 0.12f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Info,
                                                                contentDescription = "Xem thông tin Page",
                                                                tint = BrandBlue,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(Modifier.width(4.dp))

                                                    IconButton(
                                                        onClick = {
                                                            if (isPageRunning) {
                                                                com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(effectivePageUid)
                                                                com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(page.pageId)
                                                                if (page.additionalProfileId.isNotBlank()) com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(page.additionalProfileId)
                                                                Toast.makeText(context, "Đã dừng Page: ${page.pageName.ifBlank { effectivePageUid }}", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                com.cayxu.app.automation.facebook.XsmmFacebookManager.start(context, effectivePageUid)
                                                                Toast.makeText(context, "Bắt đầu chạy Page: ${page.pageName.ifBlank { effectivePageUid }}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isPageRunning) DangerRed else BrandBlue),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isPageRunning) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(8.dp)
                                                                        .clip(RoundedCornerShape(2.dp))
                                                                        .background(Color.White)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Filled.PlayArrow,
                                                                    contentDescription = "Chạy Page",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Log trạng thái Page
                                                val pageStatus = fbStatusMap[effectivePageUid]
                                                    ?: fbStatusMap[page.pageId]
                                                    ?: (if (page.additionalProfileId.isNotBlank()) fbStatusMap[page.additionalProfileId] else null)
                                                val pageSuccess = fbSuccessCountMap[effectivePageUid]
                                                    ?: fbSuccessCountMap[page.pageId]
                                                    ?: (if (page.additionalProfileId.isNotBlank()) fbSuccessCountMap[page.additionalProfileId] else null)
                                                    ?: 0
                                                val pageErrors = fbErrorCountMap[effectivePageUid]
                                                    ?: fbErrorCountMap[page.pageId]
                                                    ?: (if (page.additionalProfileId.isNotBlank()) fbErrorCountMap[page.additionalProfileId] else null)
                                                    ?: 0
                                                val pageErrorDetail = fbErrorDetailMap[effectivePageUid]
                                                    ?: fbErrorDetailMap[page.pageId]
                                                    ?: (if (page.additionalProfileId.isNotBlank()) fbErrorDetailMap[page.additionalProfileId] else null)

                                                if (!pageStatus.isNullOrBlank() || pageSuccess > 0 || pageErrors > 0) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (isPageRunning) {
                                                                CircularProgressIndicator(
                                                                    color = BrandBlue,
                                                                    strokeWidth = 1.6.dp,
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                                Spacer(Modifier.width(5.dp))
                                                            }
                                                            Text(
                                                                text = pageStatus ?: if (isPageRunning) "Đang chạy..." else "Sẵn sàng",
                                                                fontSize = 11.sp,
                                                                color = if (isPageRunning) BrandBlue else TextSecondary,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        if (pageSuccess > 0 || pageErrors > 0 || !pageErrorDetail.isNullOrBlank()) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                if (pageSuccess > 0) {
                                                                    Text("+$pageSuccess", color = Color(0xFF16A34A), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                                if (pageErrors > 0) {
                                                                    Text("-$pageErrors", color = DangerRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                                if (pageErrors > 0 || !pageErrorDetail.isNullOrBlank()) {
                                                                    IconButton(
                                                                        onClick = { selectedErrorDetailAccount = effectivePageUid },
                                                                        modifier = Modifier.size(22.dp)
                                                                    ) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(18.dp)
                                                                                .clip(CircleShape)
                                                                                .background(DangerRed.copy(alpha = 0.15f)),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Filled.Warning,
                                                                                contentDescription = "Xem chi tiết lỗi Page",
                                                                                tint = DangerRed,
                                                                                modifier = Modifier.size(11.dp)
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
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }

        // Bottom Bar Cố Định
        Surface(
            color = CardWhite,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Nút Cấu hình chạy
                OutlinedButton(
                    onClick = { navController.navigate(Routes.XSMM_RUN_CONFIG) },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Cấu hình",
                        tint = BrandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Cấu hình", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Checkbox Chọn tất cả
                    val allUids = facebookAccounts.flatMap { acc ->
                        listOf(acc.uid) + acc.pages.map { p ->
                            p.additionalProfileId.takeIf { it.isNotBlank() } ?: p.displayUid.takeIf { it.isNotBlank() } ?: p.pageId
                        }
                    }.toSet()
                    val isAllChecked = allUids.isNotEmpty() && allUids.all { it in selectedForRunUids }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                selectedForRunUids = if (isAllChecked) emptySet() else allUids
                            }
                            .padding(end = 10.dp)
                    ) {
                        Checkbox(
                            checked = isAllChecked,
                            onCheckedChange = { checked ->
                                selectedForRunUids = if (checked) allUids else emptySet()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BrandBlue)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Tất cả", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    // Nút Xóa (thùng rác đỏ)
                    if (selectedForRunUids.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteConfirmSheet = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Xóa tài khoản đã chọn",
                                    tint = DangerRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Nút Chạy tất cả / Dừng tất cả
                    val isAnyRunning = com.cayxu.app.automation.facebook.XsmmFacebookManager.isAnyRunning()
                    IconButton(
                        onClick = {
                            if (isAnyRunning) {
                                com.cayxu.app.automation.facebook.XsmmFacebookManager.stopAll()
                                Toast.makeText(context, "Đã dừng tất cả tác vụ Facebook", Toast.LENGTH_SHORT).show()
                            } else {
                                val targetToRun = if (selectedForRunUids.isNotEmpty()) {
                                    selectedForRunUids.toList()
                                } else {
                                    allUids.toList()
                                }
                                if (targetToRun.isEmpty()) {
                                    Toast.makeText(context, "Chưa có tài khoản Facebook nào!", Toast.LENGTH_SHORT).show()
                                } else {
                                    com.cayxu.app.automation.facebook.XsmmFacebookManager.startAccounts(context, targetToRun)
                                    Toast.makeText(context, "Bắt đầu chạy ${targetToRun.size} tài khoản Facebook", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isAnyRunning) DangerRed else BrandBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAnyRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Chạy tất cả",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Sheet đăng nhập Facebook
    if (showFacebookLoginSheet) {
        FacebookLoginBottomSheet(
            onDismiss = {
                showFacebookLoginSheet = false
                facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
            },
            onAccountSaved = {
                facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
            }
        )
    }

    // Sheet xem chi tiết tài khoản
    if (selectedFbDetailAccount != null) {
        FacebookAccountDetailSheet(
            account = selectedFbDetailAccount!!,
            onDismiss = { selectedFbDetailAccount = null }
        )
    }

    // Sheet xem chi tiết Page
    if (selectedFbDetailPage != null) {
        val (parentAcc, pageItem) = selectedFbDetailPage!!
        FacebookPageDetailSheet(
            parentAccount = parentAcc,
            page = pageItem,
            onUidResolved = { uid615 ->
                livePageUids = livePageUids + (pageItem.pageId to uid615)
            },
            onMediaUpdated = { avatar, cover ->
                if (avatar.isNotBlank()) livePageAvatars = livePageAvatars + (pageItem.pageId to avatar)
                if (cover.isNotBlank()) livePageCovers = livePageCovers + (pageItem.pageId to cover)
            },
            onDismiss = { selectedFbDetailPage = null }
        )
    }

    // Sheet xác nhận xóa
    if (showDeleteConfirmSheet) {
        val targetUids = selectedForRunUids.toList()
        AlertDialog(
            onDismissRequest = { showDeleteConfirmSheet = false },
            title = { Text("Xác nhận xóa tài khoản", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Bạn có chắc chắn muốn xóa ${targetUids.size} tài khoản Facebook đã chọn khỏi thiết bị?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        FacebookAccountsStore.removeAccounts(context, targetUids)
                        facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                        selectedForRunUids = emptySet()
                        showDeleteConfirmSheet = false
                        Toast.makeText(context, "Đã xóa thành công ${targetUids.size} tài khoản", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Xóa ngay", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmSheet = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }

    // Dialog xem chi tiết lỗi
    if (selectedErrorDetailAccount != null) {
        val targetUid = selectedErrorDetailAccount!!
        val errorText = fbErrorDetailMap[targetUid] ?: fbStatusMap[targetUid] ?: "Không có thông tin lỗi chi tiết"
        AlertDialog(
            onDismissRequest = { selectedErrorDetailAccount = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Chi tiết lỗi [$targetUid]", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = errorText,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedErrorDetailAccount = null }) {
                    Text("Đóng", color = BrandBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
