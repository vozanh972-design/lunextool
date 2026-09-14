package com.cayxu.app.ui.screens.xsmm

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import com.cayxu.app.data.local.FacebookAccount
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.repository.XsmmAccountsRepository
import com.cayxu.app.data.repository.XsmmAccountsResult
import com.cayxu.app.data.repository.XsmmAddAccountResult
import com.cayxu.app.data.repository.XsmmAuthRepository
import com.cayxu.app.data.repository.XsmmLoginResult
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val XsmmAccentStart = Color(0xFF34D399)
private val XsmmAccentEnd = Color(0xFF16A34A)

/**
 * Màn tài khoản XSMM - hiện username + số dư (points) dạng thẻ gradient ở trên, và NGAY BÊN
 * DƯỚI là danh sách acc TikTok (3 tab: TikTok / TikTok Lite / TikTok Studio, giống bố cục màn
 * TikTok của GoLike trước đây) - acc chưa "Thêm" hiện nút Thêm, acc đã thêm rồi thì ẩn nút đó.
 * Cuối màn có 2 nút cố định: "Cấu hình chạy" và "Chạy".
 *
 * Đã nối THẬT với API XSMM (/api/taskapi/accounts):
 *   - Vào màn/đổi tab -> gọi GET accounts?account_type=tiktok để biết @handle nào ĐÃ có trên
 *     XSMM (so khớp theo link_account) -> tự ẩn nút "Thêm" cho acc đó.
 *   - Bấm "Thêm" -> gọi THẬT POST accounts (type=tiktok, link_account, active=true) để thêm
 *     acc đó vào XSMM (đặt luôn làm "nick chạy").
 *
 * "Cấu hình chạy" và "Chạy" HIỆN VẪN LÀ PLACEHOLDER - XSMM có API GET tasks + POST
 * tasks/complete (xem XsmmTasksRepository, đã viết sẵn sàng nối) nhưng CHƯA gắn vào đây vì
 * "type" nhiệm vụ có nhiều loại (tiktok_follow/tiktok_like/tiktok_comment...) và chưa rõ màn
 * này nên để người dùng tự chọn loại nào hay mặc định loại nào.
 */
@Composable
fun XsmmAccountScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val username by XsmmSession.username
    val points by XsmmSession.points
    var isRefreshing by remember { mutableStateOf(false) }

    var selectedPlatform by remember { mutableStateOf("tiktok") }
    var selectedVariant by remember { mutableStateOf(TikTokAppVariant.STANDARD) }
    var linkedHandles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isCheckingLinked by remember { mutableStateOf(false) }
    var addingUid by remember { mutableStateOf<String?>(null) }
    var selectedAccountUid by remember(selectedPlatform, selectedVariant) { mutableStateOf<String?>(null) }
    var selectedForRunUids by remember(selectedPlatform, selectedVariant) { mutableStateOf<Set<String>>(emptySet()) }
    var showInstagramCookieSheet by remember { mutableStateOf(false) }
    var showFacebookLoginSheet by remember { mutableStateOf(false) }
    var selectedFbDetailAccount by remember { mutableStateOf<FacebookAccount?>(null) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }

    var allTikTokAccounts by remember { mutableStateOf(TikTokAccountsStore.getAccounts(context).filter { it.enabled }) }
    val accountsForVariant = allTikTokAccounts.filter { it.variant == selectedVariant }
    var facebookAccounts by remember { mutableStateOf(com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)) }
    var instagramAccounts by remember {
        mutableStateOf(
            com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                .ifEmpty { com.cayxu.app.data.local.LinkedAccountsStore.getAccounts(context, "Instagram") }
        )
    }

    LaunchedEffect(selectedPlatform, showFacebookLoginSheet, showInstagramCookieSheet) {
        if (selectedPlatform == "facebook") {
            facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
        } else if (selectedPlatform == "instagram") {
            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                .ifEmpty { com.cayxu.app.data.local.LinkedAccountsStore.getAccounts(context, "Instagram") }
        } else {
            allTikTokAccounts = TikTokAccountsStore.getAccounts(context).filter { it.enabled }
        }
    }
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }
    val runningIgAccounts = com.cayxu.app.automation.instagram.XsmmInstagramManager.runningAccounts
    val igStatusMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap
    val igSuccessCountMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.successCountMap
    val igErrorCountMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.errorCountMap
    val igErrorDetailMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.lastErrorDetail

    var selectedErrorDetailAccount by remember { mutableStateOf<String?>(null) }
    var targetAvatarChangeUsername by remember { mutableStateOf<String?>(null) }
    var targetFbAvatarChangeUid by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val pickAvatarLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val username = targetAvatarChangeUsername ?: return@rememberLauncherForActivityResult
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
                    val acc = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, username)
                    if (acc == null || acc.cookie.isBlank()) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Không tìm thấy cookie cho $username", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đang đổi ảnh đại diện Instagram...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    val client = com.cayxu.app.instagram.InstagramApiClient(cookie = acc.cookie)
                    val newPicUrl = client.changeProfilePicture(bytes)
                    val freshDetails = try {
                        client.fetchAccountDetails(acc.username)
                    } catch (_: Exception) { null }
                    val finalAvatar = newPicUrl ?: freshDetails?.profilePicUrl ?: acc.avatar
                    val updatedAcc = acc.copy(
                        avatar = finalAvatar,
                        fullName = freshDetails?.fullName ?: acc.fullName,
                        followersCount = freshDetails?.followersCount ?: acc.followersCount,
                        followingCount = freshDetails?.followingCount ?: acc.followingCount,
                        postsCount = freshDetails?.postsCount ?: acc.postsCount
                    )
                    com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, updatedAcc)
                    withContext(Dispatchers.Main) {
                        avatarVersion = System.currentTimeMillis()
                        instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                        android.widget.Toast.makeText(context, "Đổi avatar Instagram thành công!", android.widget.Toast.LENGTH_SHORT).show()
                        isUploadingAvatar = false
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
                    val acc = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context).firstOrNull { it.uid == uid }
                    if (acc == null) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Không tìm thấy tài khoản Facebook $uid", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đang đổi ảnh đại diện Facebook...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    val fbManager = com.cayxu.app.facebook.FacebookAccountManager()
                    val token = acc.bio.ifBlank { null }
                    var newPicUrl: String? = null
                    if (!token.isNullOrBlank()) {
                        newPicUrl = fbManager.changeProfilePicture(token, bytes, acc.phone.ifBlank { null })
                    }
                    val fallbackPic = "https://graph.facebook.com/v19.0/$uid/picture?type=large"
                    val finalAvatar = newPicUrl ?: fallbackPic
                    val updatedAcc = acc.copy(avatar = finalAvatar)
                    com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updatedAcc)
                    withContext(Dispatchers.Main) {
                        avatarVersion = System.currentTimeMillis()
                        facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
                        android.widget.Toast.makeText(context, "Đổi avatar Facebook thành công!", android.widget.Toast.LENGTH_SHORT).show()
                        isUploadingAvatar = false
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

    if (selectedErrorDetailAccount != null) {
        val targetUser = selectedErrorDetailAccount ?: ""
        val detail = igErrorDetailMap[targetUser] ?: igStatusMap[targetUser] ?: "Không có thông tin lỗi chi tiết."
        ErrorDetailBottomSheet(
            accountName = targetUser,
            errorMessage = detail,
            onDismiss = { selectedErrorDetailAccount = null }
        )
    }

    if (showInstagramCookieSheet) {
        InstagramCookieBottomSheet(
            onDismiss = { showInstagramCookieSheet = false },
            onCookieSaved = {
                instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
            }
        )
    }

    if (showFacebookLoginSheet) {
        FacebookLoginBottomSheet(
            onDismiss = {
                showFacebookLoginSheet = false
                facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
            },
            onAccountSaved = {
                facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
            }
        )
    }

    if (selectedFbDetailAccount != null) {
        FacebookAccountDetailSheet(
            account = selectedFbDetailAccount!!,
            onDismiss = { selectedFbDetailAccount = null }
        )
    }

    if (showDeleteConfirmSheet) {
        val platformLabel = when (selectedPlatform) {
            "facebook" -> "Facebook"
            "instagram" -> "Instagram"
            else -> "TikTok"
        }
        val targetUids = selectedForRunUids.toList()
        DeleteConfirmBottomSheet(
            platformName = platformLabel,
            accountList = targetUids,
            onDismiss = { showDeleteConfirmSheet = false },
            onConfirmDelete = {
                val count = targetUids.size
                when (selectedPlatform) {
                    "instagram" -> {
                        targetUids.forEach { uid ->
                            com.cayxu.app.data.local.InstagramAccountsStore.removeAccount(context, uid)
                            com.cayxu.app.data.local.LinkedAccountsStore.removeAccount(context, "Instagram", uid)
                        }
                        instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                            .ifEmpty { com.cayxu.app.data.local.LinkedAccountsStore.getAccounts(context, "Instagram") }
                    }
                    "facebook" -> {
                        com.cayxu.app.data.local.FacebookAccountsStore.removeAccounts(context, targetUids)
                        facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
                    }
                    "tiktok" -> {
                        TikTokAccountsStore.removeAccounts(context, targetUids)
                        targetUids.forEach { uid ->
                            com.cayxu.app.data.local.LinkedAccountsStore.removeAccount(context, "TikTok", uid)
                        }
                        allTikTokAccounts = TikTokAccountsStore.getAccounts(context).filter { it.enabled }
                    }
                }
                selectedForRunUids = emptySet()
                showDeleteConfirmSheet = false
                android.widget.Toast.makeText(context, "Đã xóa thành công $count tài khoản", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    LaunchedEffect(selectedPlatform, selectedVariant) {
        if (selectedPlatform == "facebook") {
            facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
        } else if (selectedPlatform == "instagram") {
            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                .ifEmpty { com.cayxu.app.data.local.LinkedAccountsStore.getAccounts(context, "Instagram") }
        } else {
            allTikTokAccounts = TikTokAccountsStore.getAccounts(context).filter { it.enabled }
        }

        val token = XsmmAccountStore.getToken(context) ?: return@LaunchedEffect
        isCheckingLinked = true
        when (val result = XsmmAccountsRepository.getAccounts(token, accountType = selectedPlatform)) {
            is XsmmAccountsResult.Success -> {
                val accMap = mutableMapOf<String, String>()
                val internalMap = mutableMapOf<String, String>()
                result.accounts.forEach { acc ->
                    val handle = acc.linkAccount.substringAfterLast("@").trim('/').lowercase()
                    if (handle.isNotBlank()) {
                        if (!acc.accountId.isNullOrBlank()) accMap[handle] = acc.accountId
                        if (!acc.id.isNullOrBlank()) internalMap[handle] = acc.id
                    }
                }
                XsmmAccountStore.saveAccountIdMap(context, accMap)
                XsmmAccountStore.saveInternalIdMap(context, internalMap)
                linkedHandles = accMap.keys + result.accounts.mapNotNull { acc ->
                    acc.linkAccount.substringAfterLast("@").trim('/').lowercase().takeIf { it.isNotBlank() }
                }.toSet()
            }
            is XsmmAccountsResult.Error -> Unit
        }
        isCheckingLinked = false
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("XSMM", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    isRefreshing = true
                    val token = XsmmAccountStore.getToken(context)
                    if (token.isNullOrBlank()) {
                        isRefreshing = false
                    } else {
                        scope.launch {
                            when (val result = XsmmAuthRepository.fetchUser(token)) {
                                is XsmmLoginResult.Success -> {
                                    XsmmSession.login(context, token, result.info.username, result.info.points)
                                }
                                is XsmmLoginResult.Error -> Unit
                            }
                            isRefreshing = false
                        }
                    }
                },
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Làm mới", tint = XsmmAccentEnd)
                }
            }
            IconButton(
                onClick = {
                    XsmmSession.logout(context)
                    navController.navigate(Routes.XSMM_LOGIN) {
                        popUpTo(Routes.XSMM_ACCOUNT) { inclusive = true }
                    }
                }
            ) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Đăng xuất", tint = DangerRed)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- Thẻ số dư ----
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(XsmmAccentStart, XsmmAccentEnd)))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                username.ifBlank { "Đang tải..." },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Số dư", color = Color(0xFFDCFCE7), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$points", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text("điểm", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Chọn nền tảng (TikTok / Facebook / Instagram) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPlatform == "tiktok",
                    onClick = { selectedPlatform = "tiktok" },
                    label = { Text("TikTok (${allTikTokAccounts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = XsmmAccentEnd.copy(alpha = 0.15f),
                        selectedLabelColor = XsmmAccentEnd
                    )
                )
                FilterChip(
                    selected = selectedPlatform == "facebook",
                    onClick = { selectedPlatform = "facebook" },
                    label = { Text("Facebook (${facebookAccounts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1877F2).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFF1877F2)
                    )
                )
                FilterChip(
                    selected = selectedPlatform == "instagram",
                    onClick = { selectedPlatform = "instagram" },
                    label = { Text("Instagram (${instagramAccounts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE1306C).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFFE1306C)
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            if (selectedPlatform == "tiktok") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Tài khoản TikTok", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    
                    if (selectedForRunUids.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteConfirmSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Xóa tài khoản đã chọn", tint = DangerRed, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    val allUidsInTab = accountsForVariant.filter { it.handle.trim().removePrefix("@").lowercase() in linkedHandles }.map { it.uid }
                    val allSelected = allUidsInTab.isNotEmpty() && allUidsInTab.all { it in selectedForRunUids }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedForRunUids = if (allSelected) selectedForRunUids - allUidsInTab.toSet()
                                else selectedForRunUids + allUidsInTab.toSet()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = XsmmAccentEnd),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Tất cả", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(10.dp))

                // ---- 3 tab: TikTok / TikTok Lite / TikTok Studio ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VariantTabChip("TikTok", TikTokAppVariant.STANDARD, selectedVariant, allTikTokAccounts) { selectedVariant = it }
                    VariantTabChip("TikTok Lite", TikTokAppVariant.LITE, selectedVariant, allTikTokAccounts) { selectedVariant = it }
                    VariantTabChip("TikTok Studio", TikTokAppVariant.STUDIO, selectedVariant, allTikTokAccounts) { selectedVariant = it }
                }
            } else if (selectedPlatform == "facebook") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Tài khoản Facebook", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    
                    if (selectedForRunUids.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteConfirmSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Xóa tài khoản đã chọn", tint = DangerRed, modifier = Modifier.size(18.dp))
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
                                .background(Color(0xFF1877F2).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Thêm tài khoản Facebook", tint = Color(0xFF1877F2), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Tài khoản Instagram", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    
                    if (selectedForRunUids.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteConfirmSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Xóa tài khoản đã chọn", tint = DangerRed, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = { showInstagramCookieSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE1306C).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Thêm tài khoản Instagram", tint = Color(0xFFE1306C), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isCheckingLinked) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đang kiểm tra tài khoản trên XSMM...", color = TextSecondary, fontSize = 12.sp)
                }
            }

            if (selectedPlatform == "tiktok") {
                if (accountsForVariant.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Chưa có tài khoản nào ở loại này - thêm ở phần Quản lý tài khoản TikTok trước.", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accountsForVariant.forEach { account ->
                            val handleLower = account.handle.trim().removePrefix("@").lowercase()
                            val isLinked = handleLower in linkedHandles
                            XsmmTikTokAccountCard(
                                account = account,
                                isSelected = account.uid == selectedAccountUid,
                                isAdded = isLinked,
                                isAdding = addingUid == account.uid,
                                isCheckedForRun = account.uid in selectedForRunUids,
                                onCheckedForRunChange = { checked ->
                                    if (!isLinked) return@XsmmTikTokAccountCard
                                    selectedForRunUids = if (checked) selectedForRunUids + account.uid
                                    else selectedForRunUids - account.uid
                                },
                                onClick = { selectedAccountUid = account.uid },
                                onAddClick = {
                                    val token = XsmmAccountStore.getToken(context)
                                    if (token.isNullOrBlank()) {
                                        android.widget.Toast.makeText(context, "Chưa đăng nhập XSMM", android.widget.Toast.LENGTH_SHORT).show()
                                        return@XsmmTikTokAccountCard
                                    }
                                    addingUid = account.uid
                                    scope.launch {
                                        when (val result = XsmmAccountsRepository.addTikTokAccount(token, account.handle)) {
                                             is XsmmAddAccountResult.Success -> {
                                                linkedHandles = linkedHandles + handleLower
                                                android.widget.Toast.makeText(context, "Đã thêm @${account.handle} vào XSMM", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            is XsmmAddAccountResult.Error -> {
                                                android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        addingUid = null
                                    }
                                }
                            )
                        }
                    }
                }
            } else if (selectedPlatform == "facebook") {
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
                            Text("Bấm vào đây hoặc nút dấu + để đăng nhập tài khoản Facebook.", color = Color(0xFF1877F2), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        facebookAccounts.forEach { account ->
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
                                border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1877F2)) else null,
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

                                            // Lớp phủ và icon bút sửa ảnh nằm bên trong đáy avatar
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

                                                // Nút Live/Die nằm ngay cạnh tên acc
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

                                        // Nút Reload (Làm mới) màu xanh chủ đạo Facebook
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    val mgr = com.cayxu.app.facebook.FacebookAccountManager()
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
                                                            com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updated)
                                                            withContext(Dispatchers.Main) {
                                                                avatarVersion = System.currentTimeMillis()
                                                                facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context)
                                                                android.widget.Toast.makeText(context, "Đã làm mới thông tin: ${updated.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            withContext(Dispatchers.Main) {
                                                                android.widget.Toast.makeText(context, "Lỗi kiểm tra Facebook: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
                                                                com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updated)
                                                                withContext(Dispatchers.Main) {
                                                                    avatarVersion = System.currentTimeMillis()
                                                                    facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context)
                                                                    android.widget.Toast.makeText(context, "Đã làm mới thông tin: ${updated.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            withContext(Dispatchers.Main) {
                                                                android.widget.Toast.makeText(context, "Lỗi kiểm tra Facebook: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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

                                        Spacer(Modifier.width(6.dp))

                                        // Nút Chạy (Play tam giác màu xanh Facebook)
                                        IconButton(
                                            onClick = {
                                                android.widget.Toast.makeText(context, "Sẵn sàng chạy Facebook: ${account.name.ifBlank { account.uid }}", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1877F2)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Chạy",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Trạng thái Page: Nếu không có page -> hiển thị "Tài khoản không có page", nếu có page -> hiển thị danh sách với chữ "Page: " ở trước tên
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

                                        // Nút chấm than (i) xem Full Info xuống cùng hàng với trạng thái Page
                                        IconButton(
                                            onClick = { selectedFbDetailAccount = account },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1877F2).copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.Info,
                                                    contentDescription = "Xem thông tin chi tiết",
                                                    tint = Color(0xFF1877F2),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (account.pages.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            account.pages.forEach { page ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFFF8FAFC))
                                                        .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (page.avatar.isNotBlank()) {
                                                        AsyncImage(
                                                            model = page.avatar,
                                                            contentDescription = "Page Avatar",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(CircleShape)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF1877F2).copy(alpha = 0.15f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Flag,
                                                                contentDescription = null,
                                                                tint = Color(0xFF1877F2),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            "Page: ${page.pageName.ifBlank { page.pageId }}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            "ID: ${page.pageId}${if (page.additionalProfileId.isNotBlank()) " • Profile: ${page.additionalProfileId}" else ""}",
                                                            fontSize = 10.sp,
                                                            color = TextSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
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
            } else {
                if (instagramAccounts.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Chưa có tài khoản Instagram nào.", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Bấm dấu + để đăng nhập tài khoản Instagram.", color = TextSecondary.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        instagramAccounts.forEach { igUid ->
                            val cleanIg = igUid.trim()
                            val isChecked = cleanIg in selectedForRunUids
                            val igAcc = remember(cleanIg, instagramAccounts, avatarVersion) {
                                com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, cleanIg)
                            }
                            val avatarModel = remember(igAcc?.avatar, avatarVersion) {
                                if (igAcc?.avatar.isNullOrBlank()) null
                                else coil.request.ImageRequest.Builder(context)
                                    .data(igAcc?.avatar)
                                    .crossfade(true)
                                    .memoryCacheKey("${igAcc?.avatar}_$avatarVersion")
                                    .diskCacheKey("${igAcc?.avatar}_$avatarVersion")
                                    .build()
                            }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardWhite),
                                border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE1306C)) else null,
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedForRunUids = if (isChecked) selectedForRunUids - cleanIg
                                        else selectedForRunUids + cleanIg
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedForRunUids = if (checked) selectedForRunUids + cleanIg
                                                else selectedForRunUids - cleanIg
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE1306C))
                                        )
                                        Spacer(Modifier.width(6.dp))

                                        // Avatar Instagram có nút camera đổi ảnh nằm gọn BÊN TRONG avatar
                                        val isThisUploading = isUploadingAvatar && targetAvatarChangeUsername == cleanIg
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, Color(0xFFE1306C).copy(alpha = 0.6f), CircleShape)
                                                .clickable(enabled = !isUploadingAvatar) {
                                                    targetAvatarChangeUsername = cleanIg
                                                    pickAvatarLauncher.launch("image/*")
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!igAcc?.avatar.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = avatarModel,
                                                    contentDescription = "Avatar",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(
                                                                    Color(0xFF833AB4),
                                                                    Color(0xFFFD1D1D),
                                                                    Color(0xFFF77737)
                                                                )
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Person,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            // Lớp phủ và icon bút/camera sửa ảnh nằm bên trong đáy avatar
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

                                            if (isThisUploading) {
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
                                            val rawDisplayName = igAcc?.fullName?.takeIf { it.isNotBlank() } ?: cleanIg
                                            val displayName = com.cayxu.app.instagram.InstagramApiClient.unescapeUnicode(rawDisplayName)
                                            val cleanUname = com.cayxu.app.instagram.InstagramApiClient.unescapeUnicode(igAcc?.username ?: cleanIg)
                                            val isLive = igAcc?.isLive ?: true

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.5.sp,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                // Nút Live/Die nằm ngay cạnh tên acc (không lặp lại tên)
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

                                            Text(
                                                text = "@$cleanUname",
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                            if (!igAcc?.biography.isNullOrBlank()) {
                                                val cleanBio = com.cayxu.app.instagram.InstagramApiClient.unescapeUnicode(igAcc!!.biography)
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = cleanBio,
                                                    color = TextSecondary.copy(alpha = 0.9f),
                                                    fontSize = 11.5.sp,
                                                    maxLines = 2,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                            val stats = buildList {
                                                if ((igAcc?.followersCount ?: 0) > 0) add("${igAcc?.followersCount} follower")
                                                if ((igAcc?.followingCount ?: 0) > 0) add("${igAcc?.followingCount} đang theo dõi")
                                                if ((igAcc?.postsCount ?: 0) > 0) add("${igAcc?.postsCount} bài viết")
                                            }
                                            if (stats.isNotEmpty()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = stats.joinToString(" • "),
                                                    color = Color(0xFFE1306C),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        // Nút Reload (Làm mới)
                                        val isRunningThis = com.cayxu.app.automation.instagram.XsmmInstagramManager.isRunning(cleanIg)
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    val acc = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, cleanIg)
                                                    if (acc == null || acc.cookie.isBlank()) {
                                                        withContext(Dispatchers.Main) {
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[cleanIg] = "Chưa lưu cookie"
                                                            android.widget.Toast.makeText(context, "Không tìm thấy cookie cho $cleanIg", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        return@launch
                                                    }
                                                    try {
                                                        withContext(Dispatchers.Main) {
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[cleanIg] = "Đang lấy thông tin..."
                                                        }
                                                        val proxyConfig = com.cayxu.app.instagram.InstagramApiClient.parseProxy(acc.proxy)
                                                        val client = com.cayxu.app.instagram.InstagramApiClient(
                                                            cookie = acc.cookie,
                                                            proxyConfig = proxyConfig
                                                        )
                                                        val info = client.fetchAccountDetails(acc.username)
                                                        val updatedAcc = acc.copy(
                                                            username = info.username.ifBlank { acc.username },
                                                            fullName = info.fullName,
                                                            avatar = info.profilePicUrl ?: acc.avatar,
                                                            fbDtsg = info.fbDtsg ?: acc.fbDtsg,
                                                            lsd = info.lsd ?: acc.lsd,
                                                            biography = info.biography,
                                                            followersCount = info.followersCount,
                                                            followingCount = info.followingCount,
                                                            postsCount = info.postsCount,
                                                            isLive = true
                                                        )
                                                        com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, updatedAcc)
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            val nameDisplay = if (info.fullName.isNotBlank()) info.fullName else info.username
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[cleanIg] = "Sẵn sàng"
                                                            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                                                            android.widget.Toast.makeText(context, "Đã cập nhật thông tin: $nameDisplay", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        val deadAcc = acc.copy(isLive = false)
                                                        com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, deadAcc)
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[cleanIg] = "Lỗi: Cookie DIE / Checkpoint"
                                                            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                                                            android.widget.Toast.makeText(context, "Lỗi kiểm tra $cleanIg: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isRunningThis,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE1306C).copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = "Làm mới",
                                                    tint = Color(0xFFE1306C),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(6.dp))

                                        // Nút Chạy (Play tam giác) / Dừng (Stop ô vuông đỏ)
                                        IconButton(
                                            onClick = {
                                                if (isRunningThis) {
                                                    com.cayxu.app.automation.instagram.XsmmInstagramManager.stop(cleanIg)
                                                    android.widget.Toast.makeText(context, "Đã dừng chạy $cleanIg", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    com.cayxu.app.automation.instagram.XsmmInstagramManager.start(context, cleanIg)
                                                    android.widget.Toast.makeText(context, "Bắt đầu chạy $cleanIg", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isRunningThis) DangerRed else Color(0xFFE1306C)),
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

                                    HorizontalDivider(
                                        color = Color(0xFFF3F4F6),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )

                                    // Khu vực hiển thị trạng thái + thống kê Hoàn thành / Lỗi + Proxy
                                    val rawStatus = igStatusMap[cleanIg] ?: "Trạng thái: Sẵn sàng"
                                    val currentStatus = if (rawStatus.equals("Live", ignoreCase = true)) "Trạng thái: Sẵn sàng" else rawStatus
                                    val successCount = igSuccessCountMap[cleanIg] ?: 0
                                    val errorCount = igErrorCountMap[cleanIg] ?: 0
                                    val isError = currentStatus.contains("Lỗi", ignoreCase = true) || currentStatus.contains("DIE", ignoreCase = true) || currentStatus.contains("Không tìm thấy", ignoreCase = true)
                                    val isRunningNow = com.cayxu.app.automation.instagram.XsmmInstagramManager.isRunning(cleanIg)

                                    // Hàm rút gọn proxy: 128.0.0.1:3098:user:pass -> 128......pass hoặc 128...3098
                                    val proxyDisplay = remember(igAcc?.proxy) {
                                        val p = igAcc?.proxy?.trim().orEmpty()
                                        if (p.isBlank()) null
                                        else {
                                            val parts = p.split(":")
                                            val first = parts.getOrNull(0)?.take(3) ?: "prx"
                                            val last = parts.lastOrNull()?.takeLast(4) ?: "..."
                                            "$first......$last"
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Dòng 1: Status text kèm chấm tròn trạng thái (đếm ngược thời gian)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isRunningNow -> Color(0xFF3B82F6)
                                                            isError -> DangerRed
                                                            else -> Color(0xFF16A34A)
                                                        }
                                                    )
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                currentStatus,
                                                fontSize = 12.sp,
                                                color = if (isError) DangerRed else if (isRunningNow) Color(0xFF1E40AF) else TextSecondary,
                                                fontWeight = if (isRunningNow) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 2,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Dòng 2: Hiển thị Thống kê Hoàn thành / Lỗi + Proxy bên cạnh
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Thành công
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .background(Color(0xFF16A34A).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF16A34A),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "Hoàn thành: $successCount",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF15803D)
                                                    )
                                                }

                                                // Thất bại / Lỗi
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .background(DangerRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(DangerRed)
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(
                                                        "Lỗi: $errorCount",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DangerRed
                                                    )
                                                }

                                                // Proxy rút gọn bên cạnh nút Lỗi (nếu acc có gán proxy)
                                                if (proxyDisplay != null) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .background(Color(0xFF6B7280).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            proxyDisplay,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color(0xFF4B5563)
                                                        )
                                                    }
                                                }
                                            }

                                            val errorDetail = igErrorDetailMap[cleanIg] ?: (if (errorCount > 0) currentStatus else null)
                                            if (errorDetail != null || errorCount > 0) {
                                                IconButton(
                                                    onClick = { selectedErrorDetailAccount = cleanIg },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(DangerRed.copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Warning,
                                                            contentDescription = "Xem chi tiết lỗi",
                                                            tint = DangerRed,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            } else if (isRunningNow) {
                                                Text(
                                                    "Đang chạy...",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE1306C)
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

            Spacer(Modifier.height(90.dp))
        }

        // ---- Thanh điều khiển cố định dưới cùng ----
        if (selectedPlatform == "tiktok") {
            // ---- TikTok: 2 nút Cấu hình chạy + Chạy to màu xanh ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        com.cayxu.app.data.local.XsmmRunConfigStore.setActivePlatform(context, "tiktok")
                        navController.navigate(Routes.XSMM_RUN_CONFIG)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = XsmmAccentEnd),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cấu hình chạy", maxLines = 1)
                }
                Button(
                    onClick = {
                        val selected = if (selectedForRunUids.isNotEmpty()) {
                            accountsForVariant.filter { it.uid in selectedForRunUids }
                        } else if (selectedAccountUid != null) {
                            accountsForVariant.filter { it.uid == selectedAccountUid }
                        } else {
                            accountsForVariant
                        }
                        val handles = selected.map { it.handle.trim().removePrefix("@") }.filter { it.isNotBlank() }
                        if (handles.isEmpty()) {
                            android.widget.Toast.makeText(context, "Chưa có tài khoản nào để chạy", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        com.cayxu.app.ui.overlay.xsmm.startXsmmJobRunnerOverlay(context, handles)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = XsmmAccentEnd),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    val runCount = if (selectedForRunUids.isNotEmpty()) selectedForRunUids.size else accountsForVariant.size
                    Text(if (runCount > 1) "Chạy ($runCount)" else "Chạy")
                }
            }
        } else {
            // ---- Instagram & Facebook: Thanh công cụ tiện ích (Cấu hình, Tất cả, Xóa, Thêm +) ----
            val isIg = selectedPlatform == "instagram"
            val platformColor = if (isIg) Color(0xFFE1306C) else Color(0xFF1877F2)
            val allSelected = if (isIg) {
                instagramAccounts.isNotEmpty() && instagramAccounts.all { it.trim() in selectedForRunUids }
            } else {
                facebookAccounts.isNotEmpty() && facebookAccounts.all { it.uid in selectedForRunUids }
            }

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
                    // Nút Cấu hình chạy
                    OutlinedButton(
                        onClick = {
                            com.cayxu.app.data.local.XsmmRunConfigStore.setActivePlatform(context, selectedPlatform)
                            navController.navigate(Routes.XSMM_RUN_CONFIG)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = platformColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, platformColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = platformColor)
                        Spacer(Modifier.width(6.dp))
                        Text("Cấu hình", fontSize = 13.sp, color = platformColor)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Nút Tất cả
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isIg) {
                                        val cleanAccounts = instagramAccounts.map { it.trim() }.toSet()
                                        selectedForRunUids = if (allSelected) selectedForRunUids - cleanAccounts
                                        else selectedForRunUids + cleanAccounts
                                    } else {
                                        val allUids = facebookAccounts.map { it.uid }.toSet()
                                        selectedForRunUids = if (allSelected) selectedForRunUids - allUids
                                        else selectedForRunUids + allUids
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = platformColor),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Tất cả", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        // Nút Xóa (thùng rác đỏ khi có acc được chọn)
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
                        }

                        // Nút Chạy tất cả / Dừng tất cả (Dành cho Instagram)
                        if (isIg && instagramAccounts.isNotEmpty()) {
                            val isAnyIgRunning = com.cayxu.app.automation.instagram.XsmmInstagramManager.isAnyRunning()
                            IconButton(
                                onClick = {
                                    if (isAnyIgRunning) {
                                        com.cayxu.app.automation.instagram.XsmmInstagramManager.stopAll()
                                        android.widget.Toast.makeText(context, "Đã dừng tất cả tác vụ", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val accountsToRun = if (selectedForRunUids.isNotEmpty()) {
                                            selectedForRunUids.toList()
                                        } else {
                                            instagramAccounts
                                        }
                                        com.cayxu.app.automation.instagram.XsmmInstagramManager.startAccounts(context, accountsToRun)
                                        android.widget.Toast.makeText(context, "Bắt đầu chạy ${accountsToRun.size} tài khoản Instagram", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isAnyIgRunning) DangerRed else platformColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAnyIgRunning) {
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

                        // Nút Thêm acc (+)
                        IconButton(
                            onClick = {
                                if (isIg) showInstagramCookieSheet = true
                                else showFacebookLoginSheet = true
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(platformColor),
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

}

@Composable
private fun VariantTabChip(
    label: String,
    variant: TikTokAppVariant,
    selectedVariant: TikTokAppVariant,
    allAccounts: List<TikTokAccount>,
    onClick: (TikTokAppVariant) -> Unit
) {
    val isSelected = variant == selectedVariant
    val count = allAccounts.count { it.variant == variant }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (isSelected) XsmmAccentEnd.copy(alpha = 0.12f) else CardWhite, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) XsmmAccentEnd else Color(0xFFEEF1F5),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick(variant) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "$label ($count)",
            color = if (isSelected) XsmmAccentEnd else TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun XsmmTikTokAccountCard(
    account: TikTokAccount,
    isSelected: Boolean,
    isAdded: Boolean,
    isAdding: Boolean,
    isCheckedForRun: Boolean,
    onCheckedForRunChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = if (isCheckedForRun) androidx.compose.foundation.BorderStroke(1.5.dp, XsmmAccentEnd) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isAdded) {
                    onCheckedForRunChange(!isCheckedForRun)
                } else {
                    onClick()
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAdded) {
                Checkbox(
                    checked = isCheckedForRun,
                    onCheckedChange = onCheckedForRunChange,
                    colors = CheckboxDefaults.colors(checkedColor = XsmmAccentEnd),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("@${account.handle.ifBlank { "chưa_rõ" }}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(account.displayName.ifBlank { "Chưa có tên hiển thị" }, color = TextSecondary, fontSize = 12.sp)
            }
            when {
                isAdding -> {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
                isAdded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = XsmmAccentEnd, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đã thêm", color = XsmmAccentEnd, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                else -> {
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = XsmmAccentEnd),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Thêm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmBottomSheet(
    platformName: String,
    accountList: List<String>,
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Tiêu đề BottomSheet
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DangerRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Xác nhận xóa tài khoản",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "Xóa ${accountList.size} tài khoản $platformName đã chọn",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Nội dung cảnh báo
            Text(
                "Bạn có chắc chắn muốn xóa ${accountList.size} tài khoản này khỏi thiết bị? Mọi thông tin tài khoản và cookie đã lưu sẽ bị xóa vĩnh viễn.",
                fontSize = 13.5.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(14.dp))

            // Danh sách các tài khoản bị xóa
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accountList.forEach { acc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                acc,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 2 Nút: Hủy / Xóa ngay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hủy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorDetailBottomSheet(
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
            // Header: Dấu chấm than cảnh báo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DangerRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Chi tiết lỗi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "Tài khoản: $accountName",
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Nguyên nhân Instagram trả về:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // Nội dung chi tiết lỗi
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
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Đã hiểu & Đóng", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

