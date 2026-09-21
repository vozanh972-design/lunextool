package com.cayxu.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import coil.request.ImageRequest
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.NhiemVuCheoStore
import com.cayxu.app.nhiemvucheo.NhiemVuCheoApiClient
import com.cayxu.app.nhiemvucheo.NvcProfileResult
import com.cayxu.app.ui.screens.xsmm.FacebookAccountDetailSheet
import com.cayxu.app.ui.screens.xsmm.FacebookLoginBottomSheet
import com.cayxu.app.ui.screens.xsmm.FacebookPageDetailSheet
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NvcAccent = Color(0xFF2563EB)   // Blue chủ đạo NhiemVuCheo
private val NvcDangerRed = Color(0xFFEF4444)
private val NvcFbBlue = Color(0xFF1877F2)

/**
 * Màn hình Nhiệm Vụ Chéo (NVC) - chỉ có tab Facebook.
 * Giao diện sao chép từ XSMM Facebook tab.
 * Chưa có API thật - nút Chạy là placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NhiemVuCheoScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var facebookAccounts by remember { mutableStateOf(FacebookAccountsStore.getAccounts(context, forceReload = true)) }
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }
    var liveFbAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageUids by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageCovers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var selectedForRunUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFacebookLoginSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }
    var selectedFbDetailAccount by remember { mutableStateOf<FacebookAccount?>(null) }
    var selectedFbDetailPage by remember { mutableStateOf<Pair<FacebookAccount, com.cayxu.app.data.local.FacebookPageItem>?>(null) }
    var selectedErrorDetailAccount by remember { mutableStateOf<String?>(null) }
    var targetFbAvatarChangeUid by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    // Tài khoản Nhiệm Vụ Chéo
    var nvcToken by remember { mutableStateOf(NhiemVuCheoStore.getToken(context)) }
    var nvcUser by remember { mutableStateOf(NhiemVuCheoStore.getUser(context)) }
    var isRefreshingNvc by remember { mutableStateOf(false) }
    var showNvcLoginDialog by remember { mutableStateOf(false) }
    var showNvcLogoutConfirm by remember { mutableStateOf(false) }
    var inputTokenText by remember { mutableStateOf("") }
    var isLoggingInNvc by remember { mutableStateOf(false) }
    var nvcLoginError by remember { mutableStateOf<String?>(null) }

    // Tự động đồng bộ profile NVC mới nhất khi mở màn hình
    LaunchedEffect(nvcToken) {
        val token = nvcToken
        if (!token.isNullOrBlank()) {
            val res = NhiemVuCheoApiClient.fetchProfile(token)
            if (res is NvcProfileResult.Success) {
                NhiemVuCheoStore.saveLogin(
                    context = context,
                    token = token,
                    id = res.user.id,
                    username = res.user.username,
                    displayName = res.user.displayName,
                    coinBalance = res.user.coinBalance,
                    status = res.user.status
                )
                nvcUser = NhiemVuCheoStore.getUser(context)
            }
        }
    }

    // State map giả - sẽ nối API sau
    // Hiện tại dùng mutableStateOf empty để giữ giao diện đúng cấu trúc
    val fbStatusMap = remember { mutableStateMapOf<String, String>() }
    val fbSuccessCountMap = remember { mutableStateMapOf<String, Int>() }
    val fbErrorCountMap = remember { mutableStateMapOf<String, Int>() }
    val fbErrorDetailMap = remember { mutableStateMapOf<String, String>() }
    val runningFbAccounts = remember { mutableStateListOf<String>() }

    // Reload accounts khi dismiss login sheet
    LaunchedEffect(showFacebookLoginSheet) {
        if (!showFacebookLoginSheet) {
            facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
        }
    }

    // Tự động load avatar live cho các nick FB
    LaunchedEffect(facebookAccounts) {
        facebookAccounts.forEach { acc ->
            val token = acc.bio.trim()
            val curAv = liveFbAvatars[acc.uid] ?: acc.avatar
            val needAvatarFix = curAv.isBlank() || curAv.contains("picture?type=large") || curAv.contains("84628273_176159830277856")
            if (needAvatarFix && token.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val proxyParts = acc.phone.ifBlank { null }?.split(":")
                        val proxyHost = proxyParts?.getOrNull(0)
                        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                        val mediaEngine = com.cayxu.app.facebook.FacebookMediaEngine(
                            accessToken = token,
                            proxyHost = proxyHost,
                            proxyPort = proxyPort
                        )
                        val media = mediaEngine.getUserMedia(tokenParam = token)
                        val realAvatar = media?.avatarUrl?.takeIf { !it.contains("84628273_176159830277856") }
                        if (!realAvatar.isNullOrBlank() && realAvatar != acc.avatar) {
                            val updated = acc.copy(avatar = realAvatar)
                            FacebookAccountsStore.updateAccount(context, updated)
                            withContext(Dispatchers.Main) {
                                liveFbAvatars = liveFbAvatars + (acc.uid to realAvatar)
                                avatarVersion = System.currentTimeMillis()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Pickavatar launcher cho Facebook
    val pickFbAvatarLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val uid = targetFbAvatarChangeUid ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            isUploadingAvatar = true
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Không thể đọc file ảnh", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    val acc = FacebookAccountsStore.getAccounts(context).firstOrNull { it.uid == uid }
                    if (acc == null) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Không tìm thấy tài khoản Facebook $uid", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    val token = acc.bio.ifBlank { "" }
                    if (token.isBlank()) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Tài khoản cần có Access Token để đổi Avatar", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đang đổi ảnh đại diện Facebook...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    val proxy = acc.phone.ifBlank { null }
                    val proxyParts = proxy?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                    val mediaEngine = com.cayxu.app.facebook.FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val result = mediaEngine.updateUserAvatar(imageBytes = bytes, tokenParam = token)
                    if (result.isSuccess) {
                        var directUrl: String? = null
                        if (!result.mediaId.isNullOrBlank()) {
                            directUrl = mediaEngine.getPhotoDirectUrl(result.mediaId, tokenParam = token)
                        }
                        if (directUrl.isNullOrBlank()) {
                            val updatedMedia = mediaEngine.getUserMedia(tokenParam = token)
                            directUrl = updatedMedia?.avatarUrl?.takeIf { !it.contains("84628273_176159830277856") }
                        }
                        val finalAvatar = directUrl ?: "https://graph.facebook.com/v21.0/me/picture?type=large&access_token=$token&t=${System.currentTimeMillis()}"
                        val updatedAcc = acc.copy(avatar = finalAvatar)
                        FacebookAccountsStore.updateAccount(context, updatedAcc)
                        withContext(Dispatchers.Main) {
                            liveFbAvatars = liveFbAvatars + (acc.uid to finalAvatar)
                            avatarVersion = System.currentTimeMillis()
                            facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                            android.widget.Toast.makeText(context, "Đổi avatar Facebook thành công!", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Lỗi đổi avatar: ${result.message}", android.widget.Toast.LENGTH_LONG).show()
                            isUploadingAvatar = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Lỗi đổi avatar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        isUploadingAvatar = false
                    }
                }
            }
        }
    }

    // Bottom sheets
    if (selectedErrorDetailAccount != null) {
        val targetUser = selectedErrorDetailAccount ?: ""
        val detail = fbErrorDetailMap[targetUser]
            ?: fbStatusMap[targetUser]
            ?: "Không có thông tin lỗi chi tiết."
        val displayName = remember(targetUser, facebookAccounts) {
            val fb = facebookAccounts.firstOrNull { it.uid.equals(targetUser, ignoreCase = true) }
            if (fb != null) return@remember fb.name.ifBlank { targetUser }
            val page = facebookAccounts.flatMap { it.pages }.firstOrNull {
                it.pageId.equals(targetUser, ignoreCase = true) ||
                it.displayUid.equals(targetUser, ignoreCase = true) ||
                it.additionalProfileId.equals(targetUser, ignoreCase = true)
            }
            if (page != null) return@remember "Page: ${page.pageName.ifBlank { targetUser }}"
            targetUser
        }
        NvcErrorDetailBottomSheet(
            accountName = displayName,
            errorMessage = detail,
            onDismiss = { selectedErrorDetailAccount = null }
        )
    }

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

    if (selectedFbDetailAccount != null) {
        FacebookAccountDetailSheet(
            account = selectedFbDetailAccount!!,
            onDismiss = { selectedFbDetailAccount = null }
        )
    }

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
            onDismiss = {
                selectedFbDetailPage = null
                facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
            }
        )
    }

    if (showDeleteConfirmSheet) {
        val targetUids = selectedForRunUids.toList()
        NvcDeleteConfirmBottomSheet(
            accountCount = targetUids.size,
            onDismiss = { showDeleteConfirmSheet = false },
            onConfirmDelete = {
                FacebookAccountsStore.removeAccounts(context, targetUids)
                facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                selectedForRunUids = emptySet()
                showDeleteConfirmSheet = false
                android.widget.Toast.makeText(context, "Đã xóa ${targetUids.size} tài khoản", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog đăng nhập / nhập API Token Nhiệm Vụ Chéo
    if (showNvcLoginDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoggingInNvc) {
                    showNvcLoginDialog = false
                    nvcLoginError = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = NvcAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng nhập Nhiệm Vụ Chéo", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        "Nhập API Token của bạn (bắt đầu bằng nvc_sk_...). Tạo hoặc lấy token tại website nhiemvucheo.com (yêu cầu quyền account:read, account:write):",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = inputTokenText,
                        onValueChange = {
                            inputTokenText = it
                            nvcLoginError = null
                        },
                        placeholder = { Text("nvc_sk_...", fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.6f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        isError = nvcLoginError != null
                    )
                    if (nvcLoginError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = nvcLoginError.orEmpty(),
                            color = NvcDangerRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val token = inputTokenText.trim()
                        if (token.isBlank()) {
                            nvcLoginError = "Vui lòng nhập API Token"
                            return@Button
                        }
                        isLoggingInNvc = true
                        nvcLoginError = null
                        scope.launch {
                            when (val res = NhiemVuCheoApiClient.fetchProfile(token)) {
                                is NvcProfileResult.Success -> {
                                    NhiemVuCheoStore.saveLogin(
                                        context = context,
                                        token = token,
                                        id = res.user.id,
                                        username = res.user.username,
                                        displayName = res.user.displayName,
                                        coinBalance = res.user.coinBalance,
                                        status = res.user.status
                                    )
                                    nvcToken = token
                                    nvcUser = NhiemVuCheoStore.getUser(context)
                                    showNvcLoginDialog = false
                                    android.widget.Toast.makeText(
                                        context,
                                        "Đăng nhập thành công: ${res.user.displayName.ifBlank { res.user.username }}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is NvcProfileResult.Error -> {
                                    nvcLoginError = res.message
                                }
                            }
                            isLoggingInNvc = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NvcAccent),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoggingInNvc && inputTokenText.isNotBlank()
                ) {
                    if (isLoggingInNvc) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Xác nhận", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNvcLoginDialog = false
                        nvcLoginError = null
                    },
                    enabled = !isLoggingInNvc
                ) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }

    // Dialog xác nhận đăng xuất NVC
    if (showNvcLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showNvcLogoutConfirm = false },
            title = { Text("Đăng xuất Nhiệm Vụ Chéo", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Bạn có chắc muốn đăng xuất tài khoản Nhiệm Vụ Chéo? Token đã lưu trên máy sẽ bị xóa.") },
            confirmButton = {
                TextButton(onClick = {
                    NhiemVuCheoStore.clear(context)
                    nvcToken = null
                    nvcUser = NhiemVuCheoStore.getUser(context)
                    showNvcLogoutConfirm = false
                    android.widget.Toast.makeText(context, "Đã đăng xuất Nhiệm Vụ Chéo", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("Đăng xuất", color = NvcDangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNvcLogoutConfirm = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        // ---- Header ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text("Nhiệm vụ chéo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ================================================================
            // ---- THẺ TÀI KHOẢN NHIỆM VỤ CHÉO (CHƯA ĐĂNG NHẬP / ĐÃ ĐĂNG NHẬP) ----
            // ================================================================
            if (nvcToken.isNullOrBlank()) {
                // ---- CHƯA ĐĂNG NHẬP ----
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NvcAccent.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inputTokenText = ""
                            nvcLoginError = null
                            showNvcLoginDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NvcAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = NvcAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tài khoản Nhiệm Vụ Chéo",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Chưa kết nối API Token • Chạm để đăng nhập",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = {
                                inputTokenText = ""
                                nvcLoginError = null
                                showNvcLoginDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NvcAccent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                "Kết nối",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // ---- ĐÃ ĐĂNG NHẬP ----
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.15f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val initial = (nvcUser.displayName.ifBlank { nvcUser.username }.trim().firstOrNull()?.uppercaseChar() ?: 'N').toString()
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NvcAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nvcUser.displayName.ifBlank { nvcUser.username }.ifBlank { "Thành viên NVC" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            val formattedCoins = formatNvcCoins(nvcUser.coinBalance)
                            Text(
                                text = "$formattedCoins xu",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NvcAccent
                            )
                        }
                        // Nút Làm mới số dư
                        IconButton(
                            onClick = {
                                val token = nvcToken ?: return@IconButton
                                isRefreshingNvc = true
                                scope.launch {
                                    when (val res = NhiemVuCheoApiClient.fetchProfile(token)) {
                                        is NvcProfileResult.Success -> {
                                            NhiemVuCheoStore.saveLogin(
                                                context = context,
                                                token = token,
                                                id = res.user.id,
                                                username = res.user.username,
                                                displayName = res.user.displayName,
                                                coinBalance = res.user.coinBalance,
                                                status = res.user.status
                                            )
                                            nvcUser = NhiemVuCheoStore.getUser(context)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Đã cập nhật: ${formatNvcCoins(res.user.coinBalance)} xu",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        is NvcProfileResult.Error -> {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Lỗi làm mới: ${res.message}",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    isRefreshingNvc = false
                                }
                            },
                            enabled = !isRefreshingNvc,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isRefreshingNvc) {
                                CircularProgressIndicator(color = NvcAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Làm mới", tint = NvcAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        // Nút Đăng xuất
                        IconButton(
                            onClick = { showNvcLogoutConfirm = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = "Đăng xuất", tint = NvcDangerRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Header tài khoản FB ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Tài khoản Facebook",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (selectedForRunUids.isNotEmpty()) {
                    IconButton(
                        onClick = { showDeleteConfirmSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NvcDangerRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Xóa tài khoản đã chọn", tint = NvcDangerRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }

                IconButton(
                    onClick = { showFacebookLoginSheet = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NvcFbBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Thêm tài khoản Facebook", tint = NvcFbBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Danh sách nick Facebook ----
            if (facebookAccounts.isEmpty()) {
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
                        Text("Chưa có tài khoản Facebook nào.", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Bấm vào đây hoặc nút dấu + để đăng nhập tài khoản Facebook.",
                            color = NvcFbBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    facebookAccounts.forEach { account ->
                        val isChecked = account.uid in selectedForRunUids
                        val isRunningThis = runningFbAccounts.contains(account.uid)
                        val currentFbAvatar = liveFbAvatars[account.uid] ?: account.avatar
                        val fbAvatarModel = remember(currentFbAvatar, avatarVersion) {
                            if (currentFbAvatar.isBlank()) null
                            else ImageRequest.Builder(context)
                                .data(currentFbAvatar)
                                .crossfade(true)
                                .memoryCacheKey("${currentFbAvatar}_$avatarVersion")
                                .diskCacheKey("${currentFbAvatar}_$avatarVersion")
                                .build()
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, NvcFbBlue) else null,
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
                                        colors = CheckboxDefaults.colors(checkedColor = NvcFbBlue)
                                    )
                                    Spacer(Modifier.width(6.dp))

                                    // Avatar FB có nút đổi ảnh
                                    val isThisFbUploading = isUploadingAvatar && targetFbAvatarChangeUid == account.uid
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, NvcFbBlue.copy(alpha = 0.6f), CircleShape)
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
                                                    .background(NvcFbBlue),
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

                                        // Lớp phủ icon bút đổi ảnh
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
                                                    .background(
                                                        if (isLive) Color(0xFF22C55E).copy(alpha = 0.12f)
                                                        else NvcDangerRed.copy(alpha = 0.12f)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isLive) Color(0xFF16A34A) else NvcDangerRed)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    if (isLive) "Live" else "Die",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLive) Color(0xFF16A34A) else NvcDangerRed
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
                                                            FacebookAccountsStore.addAccount(context, account.copy(
                                                                name = details.name.ifBlank { account.name },
                                                                avatar = details.avatar.ifBlank { account.avatar },
                                                                email = details.email,
                                                                pages = details.pages.ifEmpty { account.pages },
                                                                isLive = true
                                                            ))
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
                                                    android.widget.Toast.makeText(context, "Đã làm mới thông tin Facebook", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(NvcFbBlue.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = "Làm mới",
                                                tint = NvcFbBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Nút Chạy / Dừng (placeholder - chưa có API)
                                    IconButton(
                                        onClick = {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Nhiệm vụ chéo chưa có API - tính năng đang chờ backend",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (isRunningThis) NvcDangerRed else NvcFbBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isRunningThis) {
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

                                // Trạng thái chạy / số xu / lỗi
                                val fbStatus = fbStatusMap[account.uid]
                                val fbSuccess = fbSuccessCountMap[account.uid] ?: 0
                                val fbErrors = fbErrorCountMap[account.uid] ?: 0
                                val fbErrDetail = fbErrorDetailMap[account.uid]
                                if (!fbStatus.isNullOrBlank() || fbSuccess > 0 || fbErrors > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isRunningThis) {
                                                    CircularProgressIndicator(
                                                        color = NvcFbBlue,
                                                        strokeWidth = 1.6.dp,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                }
                                                Text(
                                                    text = fbStatus ?: "Sẵn sàng",
                                                    fontSize = 11.5.sp,
                                                    color = if (isRunningThis) NvcFbBlue else TextSecondary,
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
                                                        Text("-$fbErrors", color = NvcDangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                                                    .background(NvcDangerRed.copy(alpha = 0.15f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Warning,
                                                                    contentDescription = "Xem chi tiết lỗi",
                                                                    tint = NvcDangerRed,
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

                                // Phần Pages
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

                                    // Nút Info (i) xem chi tiết
                                    IconButton(
                                        onClick = { selectedFbDetailAccount = account },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(NvcFbBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Info,
                                                contentDescription = "Xem thông tin chi tiết",
                                                tint = NvcFbBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Danh sách Pages
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
                                                    it.equals(page.pageId, ignoreCase = true)
                                                }

                                                val isPageChecked = effectivePageUid in selectedForRunUids ||
                                                    page.pageId in selectedForRunUids

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Checkbox Page
                                                    Checkbox(
                                                        checked = isPageChecked,
                                                        onCheckedChange = { checked ->
                                                            selectedForRunUids = if (checked) {
                                                                selectedForRunUids + effectivePageUid
                                                            } else {
                                                                selectedForRunUids - effectivePageUid - page.pageId - page.additionalProfileId - pageDisplayUid
                                                            }
                                                        },
                                                        colors = CheckboxDefaults.colors(checkedColor = NvcFbBlue),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))

                                                    // Avatar Page
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
                                                                .background(NvcFbBlue.copy(alpha = 0.15f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Flag,
                                                                contentDescription = null,
                                                                tint = NvcFbBlue,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(8.dp))

                                                    // Tên Page + UID
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            "Page: ${page.pageName.ifBlank { effectivePageUid.ifBlank { page.pageId } }}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (effectivePageUid.isNotBlank()) {
                                                            Text(
                                                                "UID: $effectivePageUid",
                                                                fontSize = 10.sp,
                                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                                color = TextSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    // Nút Info Page
                                                    IconButton(
                                                        onClick = { selectedFbDetailPage = Pair(account, page) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(CircleShape)
                                                                .background(NvcFbBlue.copy(alpha = 0.12f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Info,
                                                                contentDescription = "Xem thông tin Page",
                                                                tint = NvcFbBlue,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(Modifier.width(4.dp))

                                                    // Nút Play/Stop Page (placeholder)
                                                    IconButton(
                                                        onClick = {
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "Nhiệm vụ chéo chưa có API - tính năng đang chờ backend",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isPageRunning) NvcDangerRed else NvcFbBlue),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isPageRunning) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(9.dp)
                                                                        .clip(RoundedCornerShape(2.dp))
                                                                        .background(Color.White)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Filled.PlayArrow,
                                                                    contentDescription = "Chạy Page",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Trạng thái Page
                                                val pageStatus = fbStatusMap[effectivePageUid] ?: fbStatusMap[page.pageId]
                                                val pageSuccess = fbSuccessCountMap[effectivePageUid] ?: fbSuccessCountMap[page.pageId] ?: 0
                                                val pageErrors = fbErrorCountMap[effectivePageUid] ?: fbErrorCountMap[page.pageId] ?: 0
                                                val pageErrorDetail = fbErrorDetailMap[effectivePageUid] ?: fbErrorDetailMap[page.pageId]

                                                if (isPageRunning || !pageStatus.isNullOrBlank() || pageSuccess > 0 || pageErrors > 0 || !pageErrorDetail.isNullOrBlank()) {
                                                    Spacer(Modifier.height(5.dp))
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isPageRunning) NvcFbBlue.copy(alpha = 0.08f) else Color(0xFFE2E8F0).copy(alpha = 0.4f))
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
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
                                                                if (isPageRunning) {
                                                                    CircularProgressIndicator(
                                                                        color = NvcFbBlue,
                                                                        strokeWidth = 1.6.dp,
                                                                        modifier = Modifier.size(10.dp)
                                                                    )
                                                                    Spacer(Modifier.width(5.dp))
                                                                }
                                                                Text(
                                                                    text = pageStatus ?: if (isPageRunning) "Đang chạy..." else "Sẵn sàng",
                                                                    fontSize = 11.sp,
                                                                    color = if (isPageRunning) NvcFbBlue else TextSecondary,
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
                                                                        Text("-$pageErrors", color = NvcDangerRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
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
                                                                                    .background(NvcDangerRed.copy(alpha = 0.15f)),
                                                                                contentAlignment = Alignment.Center
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Filled.Warning,
                                                                                    contentDescription = "Xem chi tiết lỗi Page",
                                                                                    tint = NvcDangerRed,
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
                    }
                }
            }

            Spacer(Modifier.height(90.dp))
        }

        // ---- Footer toolbar (giống XSMM FB) ----
        val allFbKeys = remember(facebookAccounts, livePageUids) {
            facebookAccounts.flatMap { acc ->
                listOf(acc.uid) + acc.pages.map { p ->
                    val u = livePageUids[p.pageId] ?: p.displayUid
                    (p.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                        ?: u.takeIf { it.isNotBlank() && it.startsWith("615") }
                        ?: p.additionalProfileId.takeIf { it.isNotBlank() }
                        ?: u.takeIf { it.isNotBlank() }
                        ?: p.pageId).trim()
                }
            }.toSet()
        }
        val allSelected = allFbKeys.isNotEmpty() && allFbKeys.all { it in selectedForRunUids }

        Surface(
            color = CardWhite,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút Tất cả
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            selectedForRunUids = if (allSelected) selectedForRunUids - allFbKeys
                            else selectedForRunUids + allFbKeys
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = NvcFbBlue),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Tất cả", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Nút Xóa (khi có chọn)
                    if (selectedForRunUids.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteConfirmSheet = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NvcDangerRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Xóa tài khoản đã chọn",
                                    tint = NvcDangerRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Nút Chạy tất cả (placeholder)
                    if (facebookAccounts.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                android.widget.Toast.makeText(
                                    context,
                                    "Nhiệm vụ chéo chưa có API - tính năng đang chờ backend",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NvcFbBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Chạy tất cả",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Nút Thêm tài khoản
                    IconButton(
                        onClick = { showFacebookLoginSheet = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NvcFbBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Thêm tài khoản",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NvcErrorDetailBottomSheet(
    accountName: String,
    errorMessage: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NvcDangerRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = NvcDangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Chi tiết lỗi", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                    Text("Tài khoản: $accountName", fontSize = 12.5.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Chi tiết nguyên nhân phản hồi:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        errorMessage,
                        color = Color(0xFF991B1B),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NvcDangerRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Đã hiểu & Đóng", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NvcDeleteConfirmBottomSheet(
    accountCount: Int,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "Xác nhận xóa",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Bạn sắp xóa $accountCount tài khoản Facebook. Hành động này không thể hoàn tác.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hủy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = NvcDangerRed),
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Xóa ngay", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatNvcCoins(coinStr: String): String {
    val clean = coinStr.trim()
    val num = clean.toLongOrNull() ?: clean.toDoubleOrNull()?.toLong()
    return if (num != null) {
        try {
            java.text.NumberFormat.getInstance(java.util.Locale("vi", "VN")).format(num)
        } catch (_: Exception) {
            clean
        }
    } else {
        clean.ifBlank { "0" }
    }
}

