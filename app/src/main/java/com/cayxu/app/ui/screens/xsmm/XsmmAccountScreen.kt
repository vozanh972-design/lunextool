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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import android.widget.Toast
import com.cayxu.app.tiktok.checker.TikTokProfileCheckerClient
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.repository.XsmmAccountsRepository
import com.cayxu.app.data.repository.XsmmAccountsResult
import com.cayxu.app.data.repository.XsmmAddAccountResult
import com.cayxu.app.data.repository.XsmmAuthRepository
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.InstagramAccount
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.automation.instagram.IgTaToolClient
import com.cayxu.app.instagram.InstagramApiClient
import com.cayxu.app.data.repository.XsmmLoginResult
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*
import com.cayxu.app.ui.xsmm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val XsmmAccentStart = Color(0xFF3B82F6)
private val XsmmAccentEnd = Color(0xFF1D4ED8)
private val TikTokBrandBlack = Color(0xFF0F172A)
private val TikTokDarkSurface = Color(0xFF1E293B)

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

    var currentPlatform by remember { mutableStateOf(XsmmPlatform.TikTok) }
    var selectedTypeId by remember { mutableStateOf<String?>("tiktok") }
    var selectedPlatform by remember { mutableStateOf("tiktok") }
    var selectedVariant by remember { mutableStateOf(TikTokAppVariant.STANDARD) }
    var linkedHandles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var linkedFbUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addingFbUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addingIgUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var linkedSyncTrigger by remember { mutableStateOf(0L) }
    var isCheckingLinked by remember { mutableStateOf(false) }
    var addingUid by remember { mutableStateOf<String?>(null) }
    var selectedAccountUid by remember(selectedPlatform, selectedVariant) { mutableStateOf<String?>(null) }
    var selectedForRunUids by remember(selectedPlatform, selectedVariant) { mutableStateOf<Set<String>>(emptySet()) }
    var showInstagramCookieSheet by remember { mutableStateOf(false) }
    var showFacebookLoginSheet by remember { mutableStateOf(false) }
    var showTikTokCheckSheet by remember { mutableStateOf(false) }
    var selectedFbDetailAccount by remember { mutableStateOf<FacebookAccount?>(null) }
    var selectedFbDetailPage by remember { mutableStateOf<Pair<FacebookAccount, com.cayxu.app.data.local.FacebookPageItem>?>(null) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }

    var allTikTokAccounts by remember {
        val saved = XsmmAccountStore.getSavedTikTokAccounts(context).filter { it.enabled }
        mutableStateOf(if (saved.isNotEmpty()) saved else TikTokAccountsStore.getAccounts(context).filter { it.enabled })
    }
    var reloadingTikTokUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var linkingTikTokUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    val accountsForVariant = allTikTokAccounts.filter { it.variant == selectedVariant }
    var facebookAccounts by remember { mutableStateOf(com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)) }
    var instagramAccounts by remember {
        mutableStateOf(
            com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
        )
    }
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }
    var liveFbAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageUids by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageCovers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Helper nạp tài khoản TikTok XSMM
    fun reloadXsmmTikTok() {
        val saved = XsmmAccountStore.getSavedTikTokAccounts(context).filter { it.enabled }
        val storeAccounts = TikTokAccountsStore.getAccounts(context).filter { it.enabled }
        val merged = if (saved.isEmpty()) {
            storeAccounts
        } else {
            val list = saved.toMutableList()
            storeAccounts.forEach { sa ->
                val sHandle = sa.handle.trim().removePrefix("@").lowercase()
                val idx = list.indexOfFirst { it.handle.trim().removePrefix("@").lowercase() == sHandle }
                if (idx >= 0) {
                    val existing = list[idx]
                    list[idx] = if (sa.avatarUrl.isNotBlank() || sa.createDateFormatted.isNotBlank() || sa.followerCount > 0) {
                        sa
                    } else if (existing.avatarUrl.isNotBlank() || existing.createDateFormatted.isNotBlank()) {
                        existing
                    } else {
                        sa
                    }
                } else {
                    list.add(sa)
                }
            }
            list
        }
        allTikTokAccounts = merged
        if (merged.isNotEmpty()) {
            XsmmAccountStore.saveSavedTikTokAccounts(context, merged)
        }
    }

    fun updateTikTokProfile(uid: String, prof: com.cayxu.app.tiktok.checker.TikTokFullProfile) {
        TikTokAccountsStore.updateFullProfile(context, uid, prof)
        val saved = XsmmAccountStore.getSavedTikTokAccounts(context).toMutableList()
        val clean = prof.username.trim().removePrefix("@").lowercase()
        val sIdx = saved.indexOfFirst {
            it.uid == uid || it.handle.trim().removePrefix("@").lowercase() == clean
        }
        val avatar = if (!prof.avatarHdUrl.isNullOrBlank()) prof.avatarHdUrl
            else if (!prof.avatarThumbUrl.isNullOrBlank()) prof.avatarThumbUrl
            else ""
        if (sIdx >= 0) {
            val old = saved[sIdx]
            saved[sIdx] = old.copy(
                handle = if (prof.username.isNotBlank()) prof.username else old.handle,
                displayName = if (prof.nickname.isNotBlank()) prof.nickname else old.displayName,
                avatarUrl = if (avatar.isNotBlank()) avatar else old.avatarUrl,
                followerCount = prof.followerCount,
                followingCount = prof.followingCount,
                heartCount = prof.totalFavorited,
                videoCount = prof.videoCount,
                bio = prof.biography.ifBlank { old.bio },
                isLive = prof.isLive,
                createdAt = if (prof.createTimestampSec > 0) prof.createTimestampSec * 1000L else old.createdAt,
                createDateFormatted = if (prof.formattedCreateDate.isNotBlank()) prof.formattedCreateDate else old.createDateFormatted
            )
            XsmmAccountStore.saveSavedTikTokAccounts(context, saved)
        }
        reloadXsmmTikTok()
    }

    LaunchedEffect(Unit) {
        reloadXsmmTikTok()
    }

    LaunchedEffect(selectedPlatform, showFacebookLoginSheet, showInstagramCookieSheet) {
        if (selectedPlatform == "facebook") {
            facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
        } else if (selectedPlatform == "instagram") {
            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
        } else {
            reloadXsmmTikTok()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (selectedPlatform == "tiktok") {
                    reloadXsmmTikTok()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedPlatform, facebookAccounts) {
        if (selectedPlatform == "facebook") {
            facebookAccounts.forEach { acc ->
                val token = acc.bio.trim()
                // Tự động kiểm tra và cập nhật Avatar thật cho nick cá nhân nếu chưa có hoặc đang dính ảnh silhouette
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
                                com.cayxu.app.data.local.FacebookAccountsStore.updateAccount(context, updated)
                                withContext(Dispatchers.Main) {
                                    liveFbAvatars = liveFbAvatars + (acc.uid to realAvatar)
                                    avatarVersion = System.currentTimeMillis()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                val proxyParts = acc.phone.ifBlank { null }?.split(":")
                val proxyHost = proxyParts?.getOrNull(0)
                val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                acc.pages.forEach { p ->
                    val curUid = livePageUids[p.pageId] ?: p.displayUid
                    if (!curUid.startsWith("615")) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val pageEngine = com.cayxu.app.facebook.FacebookPageEngine(
                                    accessToken = acc.bio,
                                    proxyHost = proxyHost,
                                    proxyPort = proxyPort
                                )
                                val uid615 = pageEngine.fetchProfilePlusIdForPage(
                                    pageId = p.pageId,
                                    tokenParam = acc.bio,
                                    pageTokenParam = p.pageToken
                                )
                                if (!uid615.isNullOrBlank() && uid615.startsWith("615")) {
                                    com.cayxu.app.data.local.FacebookAccountsStore.updatePageUid(context, acc.uid, p.pageId, uid615)
                                    withContext(Dispatchers.Main) {
                                        livePageUids = livePageUids + (p.pageId to uid615)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedPlatform, allTikTokAccounts.map { it.uid to (it.avatarUrl.isNotBlank() && it.createDateFormatted.isNotBlank()) }) {
        if (selectedPlatform == "tiktok") {
            // Tự động kiểm tra và làm mới API cho các tài khoản chưa có avatar, chưa có ngày tạo hoặc follow
            val needCheck = allTikTokAccounts.filter {
                (it.avatarUrl.isBlank() || it.createDateFormatted.isBlank() || it.followerCount <= 0L) &&
                it.handle.isNotBlank() && it.uid !in reloadingTikTokUids
            }
            if (needCheck.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    val client = TikTokProfileCheckerClient()
                    val jobs = needCheck.map { acc ->
                        launch(Dispatchers.IO) {
                            try {
                                val cleanHandle = acc.handle.trim().removePrefix("@")
                                val prof = client.fetchProfile(cleanHandle)
                                if (prof != null) {
                                    withContext(Dispatchers.Main) {
                                        updateTikTokProfile(acc.uid, prof)
                                    }
                                }
                            } catch (ignored: Exception) {}
                        }
                    }
                    jobs.forEach { it.join() }
                }
            }
        }
    }

    LaunchedEffect(selectedPlatform, instagramAccounts.size) {
        if (selectedPlatform == "instagram") {
            val needCheck = instagramAccounts.mapNotNull { username ->
                val acc = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, username)
                if (acc != null && (!acc.isLive || acc.fbDtsg.isNullOrBlank()) && acc.cookie.isNotBlank()) acc else null
            }
            if (needCheck.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    var hasUpdates = false
                    needCheck.forEach { acc ->
                        try {
                            val igAcc = com.cayxu.app.automation.instagram.IgXsmmAccount(
                                userId = acc.userId,
                                username = acc.username,
                                cookie = acc.cookie,
                                proxy = acc.proxy.takeIf { it.isNotBlank() }
                            )
                            val runner = com.cayxu.app.automation.instagram.XsmmInstagramTaskRunner("", igAcc)
                            val (isLive, tokens) = runner.syncSessionTokens()
                            val updated = acc.copy(
                                isLive = isLive,
                                fbDtsg = tokens["fb_dtsg"] ?: acc.fbDtsg,
                                lsd = tokens["lsd"] ?: acc.lsd,
                                userId = tokens["userId"] ?: acc.userId
                            )
                            com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, updated)
                            hasUpdates = true
                        } catch (_: Exception) {}
                    }
                    if (hasUpdates) {
                        withContext(Dispatchers.Main) {
                            avatarVersion = System.currentTimeMillis()
                            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedPlatform, facebookAccounts.size) {
        if (selectedPlatform == "facebook") {
            // Tự động kiểm tra và phục hồi tên/avatar thật của nick Profile mẹ nếu trước đó bị ghi đè nhầm tên Page
            val needRestoreProfile = facebookAccounts.filter { acc ->
                acc.pages.isNotEmpty() && acc.pages.any { p -> p.pageName.isNotBlank() && p.pageName.equals(acc.name, ignoreCase = true) } && acc.note.contains("c_user=")
            }
            if (needRestoreProfile.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    val mgr = com.cayxu.app.facebook.FacebookAccountManager()
                    var hasUpdates = false
                    needRestoreProfile.forEach { acc ->
                        try {
                            val direct = mgr.getTokenFromCookie(acc.note, acc.phone.ifBlank { null })
                            if (direct != null && direct.name.isNotBlank() && !acc.pages.any { p -> p.pageName.equals(direct.name, ignoreCase = true) }) {
                                val restored = acc.copy(
                                    name = direct.name,
                                    avatar = direct.avatar.ifBlank { acc.avatar },
                                    isLive = true
                                )
                                com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, restored)
                                hasUpdates = true
                            }
                        } catch (_: Exception) {}
                    }
                    if (hasUpdates) {
                        withContext(Dispatchers.Main) {
                            facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
                        }
                    }
                }
            }
        }
    }

    val runningIgAccounts = com.cayxu.app.automation.instagram.XsmmInstagramManager.runningAccounts
    val igStatusMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap
    val igSuccessCountMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.successCountMap
    val igErrorCountMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.errorCountMap
    val igErrorDetailMap = com.cayxu.app.automation.instagram.XsmmInstagramManager.lastErrorDetail

    val runningFbAccounts = com.cayxu.app.automation.facebook.XsmmFacebookManager.runningAccounts
    val fbStatusMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.statusMap
    val fbSuccessCountMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.successCountMap
    val fbErrorCountMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.errorCountMap
    val fbErrorDetailMap = com.cayxu.app.automation.facebook.XsmmFacebookManager.lastErrorDetail

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
                    val client = com.cayxu.app.instagram.InstagramApiClient(
                        cookie = acc.cookie,
                        proxyConfig = com.cayxu.app.instagram.InstagramApiClient.parseProxy(acc.proxy)
                    )
                    val newPicUrl = client.changeProfilePicture(bytes)
                    val freshDetails = try {
                        client.fetchAccountDetails(acc.username)
                    } catch (_: Exception) { null }
                    val finalAvatar = (if (newPicUrl?.startsWith("http") == true) newPicUrl else null)
                        ?: freshDetails?.profilePicUrl?.takeIf { it.startsWith("http") }
                        ?: acc.avatar
                    val updatedAcc = acc.copy(
                        avatar = finalAvatar,
                        fullName = freshDetails?.fullName?.ifBlank { acc.fullName } ?: acc.fullName,
                        followersCount = freshDetails?.followersCount?.takeIf { it > 0 } ?: acc.followersCount,
                        followingCount = freshDetails?.followingCount?.takeIf { it > 0 } ?: acc.followingCount,
                        postsCount = freshDetails?.postsCount?.takeIf { it > 0 } ?: acc.postsCount
                    )
                    com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, updatedAcc)
                    withContext(Dispatchers.Main) {
                        avatarVersion = System.currentTimeMillis()
                        instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                        if (newPicUrl != null || freshDetails?.profilePicUrl?.startsWith("http") == true) {
                            android.widget.Toast.makeText(context, "Đổi avatar Instagram thành công!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Đã gửi yêu cầu đổi avatar Instagram", android.widget.Toast.LENGTH_SHORT).show()
                        }
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
                    val result = mediaEngine.updateUserAvatar(
                        imageBytes = bytes,
                        tokenParam = token
                    )

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
                        com.cayxu.app.data.local.FacebookAccountsStore.updateAccount(context, updatedAcc)
                        withContext(Dispatchers.Main) {
                            liveFbAvatars = liveFbAvatars + (acc.uid to finalAvatar)
                            avatarVersion = System.currentTimeMillis()
                            facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
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

    if (selectedErrorDetailAccount != null) {
        val targetUser = selectedErrorDetailAccount ?: ""
        val cleanTarget = targetUser.trim().removePrefix("@").lowercase()
        val detail = fbErrorDetailMap[targetUser]
            ?: fbErrorDetailMap[cleanTarget]
            ?: igErrorDetailMap[targetUser]
            ?: igErrorDetailMap[cleanTarget]
            ?: fbStatusMap[targetUser]
            ?: fbStatusMap[cleanTarget]
            ?: igStatusMap[targetUser]
            ?: igStatusMap[cleanTarget]
            ?: "Không có thông tin lỗi chi tiết."
        val displayName = remember(targetUser, facebookAccounts, instagramAccounts) {
            val fb = facebookAccounts.firstOrNull { it.uid.equals(targetUser, ignoreCase = true) }
            if (fb != null) return@remember fb.name.ifBlank { targetUser }
            val page = facebookAccounts.flatMap { it.pages }.firstOrNull {
                it.pageId.equals(targetUser, ignoreCase = true) ||
                it.displayUid.equals(targetUser, ignoreCase = true) ||
                it.additionalProfileId.equals(targetUser, ignoreCase = true)
            }
            if (page != null) return@remember "Page: ${page.pageName.ifBlank { targetUser }}"
            val ig = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, targetUser)
                ?: com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, cleanTarget)
            if (ig != null) {
                val uname = ig.username.takeIf { !it.contains("người dùng", ignoreCase = true) && it.isNotBlank() }
                    ?: ig.userId.takeIf { !it.contains("người dùng", ignoreCase = true) && it.isNotBlank() }
                    ?: ig.fullName.takeIf { !it.contains("người dùng", ignoreCase = true) && it.isNotBlank() }
                    ?: ig.username
                return@remember uname
            }
            targetUser
        }
        ErrorDetailBottomSheet(
            accountName = displayName,
            errorMessage = detail,
            onDismiss = { selectedErrorDetailAccount = null }
        )
    }

    if (showInstagramCookieSheet) {
        XsmmIgAddSheet(
            onDismiss = { showInstagramCookieSheet = false },
            onSubmit = { data ->
                val rawInput = data.trim()
                if (rawInput.isBlank()) {
                    Toast.makeText(context, "Vui lòng nhập danh sách tài khoản", Toast.LENGTH_SHORT).show()
                    return@XsmmIgAddSheet
                }

                val lines = rawInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                val hasValidCookieLine = lines.any { line ->
                    val rawCookie = if (line.contains("|")) line.substring(0, line.lastIndexOf("|")).trim() else line
                    rawCookie.contains("sessionid") && rawCookie.contains("ds_user_id") && rawCookie.contains("csrftoken")
                }

                if (!hasValidCookieLine) {
                    Toast.makeText(context, "Cookie thiếu các trường bắt buộc (cần có sessionid, ds_user_id, csrftoken)", Toast.LENGTH_LONG).show()
                    return@XsmmIgAddSheet
                }

                Toast.makeText(context, "Đang kiểm tra tài khoản Instagram...", Toast.LENGTH_SHORT).show()
                showInstagramCookieSheet = false

                scope.launch {
                    val addedAccounts = mutableListOf<String>()
                    withContext(Dispatchers.IO) {
                        for (line in lines) {
                            var rawCookie = ""
                            var proxyStr: String? = null
                            if (line.contains("|")) {
                                val lastPipeIndex = line.lastIndexOf("|")
                                rawCookie = line.substring(0, lastPipeIndex).trim()
                                proxyStr = line.substring(lastPipeIndex + 1).trim().takeIf { it.isNotBlank() }
                            } else {
                                rawCookie = line
                            }

                            if (!rawCookie.contains("sessionid") || !rawCookie.contains("ds_user_id") || !rawCookie.contains("csrftoken")) {
                                continue
                            }

                            val client = IgTaToolClient.buildClient(proxyStr)
                            val info = IgTaToolClient.checkCookieIg(client, rawCookie)

                            if (info.isLive && info.username.isNotBlank()) {
                                val tokens = IgTaToolClient.extractPageTokens(
                                    client = client,
                                    cookie = rawCookie,
                                    targetUrl = "https://www.instagram.com/"
                                )
                                val dev = InstagramApiClient.getDeviceProfileFor(info.userId)
                                val pic = try {
                                    InstagramApiClient.fetchProfilePic(info.username, rawCookie, proxyStr)
                                } catch (_: Exception) { null }

                                withContext(Dispatchers.Main) {
                                    InstagramAccountsStore.addAccount(
                                        context,
                                        InstagramAccount(
                                            username = info.username,
                                            userId = info.userId,
                                            cookie = rawCookie,
                                            userAgent = dev.userAgent,
                                            proxy = proxyStr.orEmpty(),
                                            fullName = info.username,
                                            avatar = pic.orEmpty(),
                                            fbDtsg = tokens.dtsg,
                                            lsd = tokens.lsd,
                                            isLive = true
                                        )
                                    )
                                    LinkedAccountsStore.addAccount(context, "Instagram", info.username)
                                    addedAccounts.add(info.username)
                                }
                            }
                        }
                    }

                    if (addedAccounts.isNotEmpty()) {
                        val successMsg = if (addedAccounts.size == 1) {
                            "Thêm tài khoản @${addedAccounts.first()} thành công!"
                        } else {
                            "Thêm thành công ${addedAccounts.size} tài khoản Instagram!"
                        }
                        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không có tài khoản nào Live hoặc Cookie hết hạn", Toast.LENGTH_SHORT).show()
                    }
                    instagramAccounts = InstagramAccountsStore.getAccounts(context).map { it.username }
                }
            }
        )
    }

    if (showFacebookLoginSheet) {
        XsmmFbLoginSheet(
            onDismiss = { showFacebookLoginSheet = false },
            onSubmit = { format, data ->
                val raw = data.trim()
                if (raw.isBlank()) {
                    Toast.makeText(context, "Vui lòng dán dữ liệu tài khoản", Toast.LENGTH_SHORT).show()
                    return@XsmmFbLoginSheet
                }

                Toast.makeText(context, "Đang kiểm tra tài khoản Facebook...", Toast.LENGTH_SHORT).show()
                showFacebookLoginSheet = false

                scope.launch {
                    val checkedAccounts = withContext(Dispatchers.IO) {
                        raw.lines().mapNotNull { rawLine ->
                            val line = rawLine.trim()
                            if (line.isBlank()) return@mapNotNull null

                            async(Dispatchers.IO) {
                                val accountManager = FacebookAccountManager()
                                val parts = if (line.contains("|")) line.split("|").map { it.trim() } else listOf(line)

                                val directToken = when {
                                    line.startsWith("EAA") -> line
                                    format == XsmmFbFormat.Token -> {
                                        parts.find { it.startsWith("EAA") } ?: line
                                    }
                                    else -> parts.find { it.startsWith("EAA") }
                                }

                                val proxy = parts.find { it.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+.*""")) }
                                val cookiePart = parts.find { it.contains("c_user=") || it.contains("xs=") }

                                if (!directToken.isNullOrBlank() && (directToken.startsWith("EAA") || format == XsmmFbFormat.Token)) {
                                    try {
                                        val details = accountManager.fetchAccountDetailsWithToken(directToken, proxy)
                                        if (details.isLive) {
                                            return@async details.copy(
                                                bio = directToken,
                                                note = cookiePart ?: details.note,
                                                phone = proxy.orEmpty(),
                                                isLive = true
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }

                                // 2. Nếu là UID|PASS|2FA... -> Đăng nhập bằng Katana Native API (b-graph.facebook.com)
                                if (parts.size >= 2 && !parts[0].startsWith("EAA") && !parts[0].contains("c_user=")) {
                                    val uidInput = parts[0]
                                    val pwdInput = parts[1]
                                    val twofaInput = parts.getOrNull(2)?.takeIf { !it.contains("=") && !it.startsWith("EAA") }.orEmpty()

                                    try {
                                        val authRes = com.cayxu.app.facebook.FacebookAuthenticator().login(
                                            uid = uidInput,
                                            pass = pwdInput,
                                            twoFaSecret = twofaInput,
                                            proxyStr = proxy,
                                            rawCookie = cookiePart
                                        )
                                        if (authRes.isSuccess && authRes.account.isLive) {
                                            return@async authRes.account.copy(
                                                password = pwdInput,
                                                link = twofaInput,
                                                note = cookiePart ?: authRes.account.note,
                                                phone = proxy.orEmpty(),
                                                isLive = true
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }

                                // 3. Thử lấy token qua Cookie nếu có cookie
                                if (!cookiePart.isNullOrBlank()) {
                                    try {
                                        val cookieAcc = accountManager.getTokenFromCookie(cookiePart, proxy)
                                        if (cookieAcc != null && cookieAcc.isLive) {
                                            return@async cookieAcc.copy(
                                                note = cookiePart,
                                                phone = proxy.orEmpty(),
                                                isLive = true
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }

                                val fallbackUid = uidFromLine ?: cookie?.let { c ->
                                    Regex("""c_user=(\d+)""").find(c)?.groupValues?.get(1)
                                } ?: parts.getOrNull(0)?.takeIf { it.matches(Regex("""\d+""")) } ?: "N/A"

                                return@async FacebookAccount(
                                    uid = fallbackUid,
                                    name = fallbackUid,
                                    note = cookie.orEmpty(),
                                    bio = directToken ?: token.orEmpty(),
                                    phone = proxy.orEmpty(),
                                    isLive = false
                                )
                            }
                        }.awaitAll()
                    }

                    FacebookAccountsStore.addAccounts(context, checkedAccounts)
                    val liveCount = checkedAccounts.count { it.isLive }
                    if (liveCount > 0) {
                        Toast.makeText(context, "Đăng nhập thành công ($liveCount tài khoản)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Đăng nhập thất bại: Tài khoản DIE hoặc sai pass/2FA", Toast.LENGTH_SHORT).show()
                    }
                    facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                }
            }
        )
    }

    if (showTikTokCheckSheet) {
        XsmmTikTokCheckSheet(
            initialVariant = selectedVariant,
            onDismiss = {
                showTikTokCheckSheet = false
                reloadXsmmTikTok()
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
                facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
            }
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
                        scope.launch(Dispatchers.IO) {
                            val allKeysToRemove = mutableSetOf<String>()
                            targetUids.forEach { accKey ->
                                allKeysToRemove.addAll(
                                    listOfNotNull(
                                        accKey,
                                        accKey.trim().removePrefix("@"),
                                        accKey.trim().removePrefix("@").lowercase()
                                    )
                                )
                                val target = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, accKey)
                                if (target != null) {
                                    if (target.username.isNotBlank()) {
                                        allKeysToRemove.add(target.username)
                                        allKeysToRemove.add(target.username.trim().removePrefix("@"))
                                        allKeysToRemove.add(target.username.trim().removePrefix("@").lowercase())
                                    }
                                    if (target.userId.isNotBlank()) {
                                        allKeysToRemove.add(target.userId)
                                        allKeysToRemove.add("IG_" + target.userId)
                                        allKeysToRemove.add("ig_" + target.userId.lowercase())
                                    }
                                    if (target.fullName.isNotBlank()) {
                                        allKeysToRemove.add(target.fullName)
                                        allKeysToRemove.add(target.fullName.trim().removePrefix("@"))
                                    }
                                }
                            }
                            allKeysToRemove.forEach { k ->
                                com.cayxu.app.automation.instagram.XsmmInstagramManager.stop(k)
                            }
                            val updatedAccounts = com.cayxu.app.data.local.InstagramAccountsStore.deleteAccounts(context, allKeysToRemove)
                            com.cayxu.app.data.local.LinkedAccountsStore.removeAccounts(context, "Instagram", allKeysToRemove)
                            val cleanRemoveSet = allKeysToRemove.map { it.trim().removePrefix("@").lowercase() }.toSet()
                            val refreshedList = updatedAccounts.map { it.username }
                                .filterNot { cleanRemoveSet.contains(it.trim().removePrefix("@").lowercase()) }
                            withContext(Dispatchers.Main) {
                                instagramAccounts = refreshedList // Ép Compose recompose ngay lập tức
                                selectedForRunUids = emptySet()
                                showDeleteConfirmSheet = false
                                avatarVersion = System.currentTimeMillis()
                                android.widget.Toast.makeText(context, "Đã xóa vĩnh viễn tài khoản khỏi thiết bị", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@DeleteConfirmBottomSheet
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
                        val remaining = allTikTokAccounts.filterNot { it.uid in targetUids }
                        allTikTokAccounts = remaining
                        XsmmAccountStore.saveSavedTikTokAccounts(context, remaining)
                    }
                }
                selectedForRunUids = emptySet()
                showDeleteConfirmSheet = false
                android.widget.Toast.makeText(context, "Đã xóa thành công $count tài khoản", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    LaunchedEffect(selectedPlatform, selectedVariant, linkedSyncTrigger) {
        if (selectedPlatform == "facebook") {
            facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
        } else if (selectedPlatform == "instagram") {
            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
        } else {
            reloadXsmmTikTok()
        }

        val token = XsmmAccountStore.getToken(context) ?: return@LaunchedEffect
        isCheckingLinked = true
        if (selectedPlatform == "facebook") {
            val allFbOnXsmm = XsmmAccountsRepository.getFacebookAccounts(token)
            val xsmmUids = allFbOnXsmm
                .map { it.accountId.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            linkedFbUids = xsmmUids

            val accMap = allFbOnXsmm
                .filter { it.accountId.isNotBlank() }
                .associate { it.accountId.trim() to it.accountId.trim() }
            val internalMap = allFbOnXsmm
                .filter { it.accountId.isNotBlank() && it.id.isNotBlank() }
                .associate { it.accountId.trim() to it.id.trim() }
            XsmmAccountStore.saveAccountIdMap(context, accMap)
            XsmmAccountStore.saveInternalIdMap(context, internalMap)
        } else if (selectedPlatform == "instagram") {
            try {
                val allIgOnXsmm = XsmmAccountsRepository.getAllAccounts2(token, accountType = "instagram")
                val xsmmIgHandles = allIgOnXsmm.mapNotNull { acc ->
                    val raw = acc.linkAccount.substringAfterLast("/").trim().removePrefix("@").lowercase()
                    if (raw.isNotBlank()) raw else acc.accountId.takeIf { it.isNotBlank() }
                }.toSet()

                val deletedKeys = com.cayxu.app.data.local.InstagramAccountsStore.getDeletedKeys(context)
                val localAccs = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context)
                    .filterNot { deletedKeys.contains(it.username.trim().removePrefix("@").lowercase()) }
                var hasChanges = false
                localAccs.forEach { local ->
                    val cleanUname = local.username.trim().removePrefix("@").lowercase()
                    val cleanUid = local.userId.trim().lowercase()
                    if (deletedKeys.contains(cleanUname) || (cleanUid.isNotBlank() && deletedKeys.contains(cleanUid))) return@forEach
                    val isMatched = xsmmIgHandles.contains(cleanUname) ||
                            (cleanUid.isNotBlank() && xsmmIgHandles.contains(cleanUid)) ||
                            allIgOnXsmm.any { it.linkAccount.contains(cleanUname, ignoreCase = true) }
                    if (isMatched && !local.isXsmmLinked) {
                        com.cayxu.app.data.local.InstagramAccountsStore.setXsmmLinked(context, local.username, true)
                        hasChanges = true
                    }
                }
                if (hasChanges) {
                    avatarVersion = System.currentTimeMillis()
                }
            } catch (_: Exception) {}
        } else {
            val allPlatformOnXsmm = if (selectedPlatform == "tiktok") {
                XsmmAccountsRepository.getAllAccounts2(token, accountType = "tiktok")
            } else {
                XsmmAccountsRepository.getAllAccounts(token, accountType = selectedPlatform)
            }
            val accMap = mutableMapOf<String, String>()
            val internalMap = mutableMapOf<String, String>()
            allPlatformOnXsmm.forEach { acc ->
                val handle = acc.linkAccount.substringAfterLast("@").trim('/').lowercase()
                val accId = acc.accountId.ifBlank { acc.id }
                if (handle.isNotBlank()) {
                    if (accId.isNotBlank()) accMap[handle] = accId
                    if (acc.id.isNotBlank()) internalMap[handle] = acc.id
                }
            }
            XsmmAccountStore.saveAccountIdMap(context, accMap)
            XsmmAccountStore.saveInternalIdMap(context, internalMap)
            linkedHandles = accMap.keys
        }
        isCheckingLinked = false
    }

    // Tự động cập nhật số dư XSMM định kỳ mỗi 15 giây mà không cần bấm reload tay
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(15_000L)
            val token = XsmmAccountStore.getToken(context)
            if (!token.isNullOrBlank()) {
                try {
                    when (val result = XsmmAuthRepository.fetchUser(token)) {
                        is XsmmLoginResult.Success -> {
                            XsmmSession.login(context, token, result.info.username, result.info.points)
                        }
                        is XsmmLoginResult.Error -> Unit
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val currentAccounts = remember(currentPlatform, allTikTokAccounts, facebookAccounts, instagramAccounts, linkedHandles, linkedFbUids, liveFbAvatars, reloadingTikTokUids, linkingTikTokUids) {
        when (currentPlatform) {
            XsmmPlatform.TikTok -> {
                allTikTokAccounts.map { acc ->
                    val typeId = when (acc.variant) {
                        TikTokAppVariant.LITE -> "tiktok_lite"
                        TikTokAppVariant.STUDIO -> "tiktok_studio"
                        else -> "tiktok"
                    }
                    val isLinked = acc.handle.trim().removePrefix("@").lowercase() in linkedHandles
                    val isReloading = acc.uid in reloadingTikTokUids
                    val isLinking = acc.uid in linkingTikTokUids
                    val cleanHandle = acc.handle.trim().removePrefix("@")
                    XsmmAccount(
                        id = acc.uid,
                        name = "@${cleanHandle.ifBlank { acc.uid }}",
                        typeId = typeId,
                        statusLabel = if (!isLinked) "Chưa liên kết" else if (acc.isLive) "Hoạt động" else "Die",
                        statusOk = isLinked && acc.isLive,
                        avatarUrl = acc.avatarUrl,
                        handle = cleanHandle,
                        followerCount = acc.followerCount,
                        heartCount = acc.heartCount,
                        createDateFormatted = acc.createDateFormatted,
                        isLive = acc.isLive,
                        isReloading = isReloading,
                        isLinked = isLinked,
                        isLinking = isLinking,
                    )
                }
            }
            XsmmPlatform.Facebook -> {
                facebookAccounts.map { acc ->
                    val isLinked = linkedFbUids.contains(acc.uid.trim())
                    val curAv = liveFbAvatars[acc.uid] ?: acc.avatar
                    val isRunningThis = com.cayxu.app.automation.facebook.XsmmFacebookManager.isRunning(acc.uid)

                    val pageItems = acc.pages.map { page ->
                        val pageDisplayUid = livePageUids[page.pageId] ?: page.displayUid
                        val effectivePageUid = (page.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                            ?: pageDisplayUid.takeIf { it.isNotBlank() && it.startsWith("615") }
                            ?: page.additionalProfileId.takeIf { it.isNotBlank() }
                            ?: pageDisplayUid.takeIf { it.isNotBlank() }
                            ?: page.pageId).trim()

                        val isPageRunning = com.cayxu.app.automation.facebook.XsmmFacebookManager.isRunning(effectivePageUid) ||
                            com.cayxu.app.automation.facebook.XsmmFacebookManager.isRunning(page.pageId) ||
                            (page.additionalProfileId.isNotBlank() && com.cayxu.app.automation.facebook.XsmmFacebookManager.isRunning(page.additionalProfileId))
                        val isPageLinked = linkedFbUids.contains(effectivePageUid) || linkedFbUids.contains(page.pageId)
                        val pageAvatar = livePageAvatars[page.pageId] ?: page.avatar.ifBlank { "https://graph.facebook.com/v19.0/$effectivePageUid/picture?type=large" }

                        XsmmPageItem(
                            pageId = effectivePageUid,
                            pageName = page.pageName.ifBlank { "Page: $effectivePageUid" },
                            displayUid = pageDisplayUid.ifBlank { effectivePageUid },
                            avatarUrl = pageAvatar,
                            isLive = page.isLive,
                            isLinked = isPageLinked,
                            isRunning = isPageRunning
                        )
                    }

                    XsmmAccount(
                        id = acc.uid,
                        name = acc.name.ifBlank { "UID: ${acc.uid}" },
                        typeId = "facebook",
                        statusLabel = if (!isLinked) "Chưa thêm XSMM" else if (acc.isLive) "Hoạt động" else "Hết hạn",
                        statusOk = isLinked && acc.isLive,
                        avatarUrl = curAv,
                        isLive = acc.isLive,
                        isLinked = isLinked,
                        isRunning = isRunningThis,
                        pages = pageItems
                    )
                }
            }
            XsmmPlatform.Instagram -> {
                val localIgAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context)
                val igMap = localIgAccounts.associateBy { it.username.trim().removePrefix("@").lowercase() }
                instagramAccounts.map { username ->
                    val clean = username.trim().removePrefix("@").lowercase()
                    val acc = igMap[clean]
                    val isLinked = acc?.isXsmmLinked == true
                    val isRunningThis = com.cayxu.app.automation.instagram.XsmmInstagramManager.isRunning(clean) || (acc?.userId?.isNotBlank() == true && com.cayxu.app.automation.instagram.XsmmInstagramManager.isRunning(acc.userId))
                    val displayName = acc?.fullName?.takeIf { it.isNotBlank() && !it.contains("người dùng", ignoreCase = true) }
                        ?: "@${username.trim().removePrefix("@")}"
                    XsmmAccount(
                        id = username,
                        name = displayName,
                        typeId = "instagram",
                        statusLabel = if (!isLinked) "Chưa thêm XSMM" else if (acc?.isLive == true) "Hoạt động" else "Hết hạn",
                        statusOk = isLinked && (acc?.isLive != false),
                        avatarUrl = acc?.avatar.orEmpty(),
                        followerCount = acc?.followersCount?.toLong() ?: 0L,
                        isLive = acc?.isLive != false,
                        isLinked = isLinked,
                        isRunning = isRunningThis
                    )
                }
            }
        }
    }

    val platformCounts = mapOf(
        XsmmPlatform.TikTok to allTikTokAccounts.size,
        XsmmPlatform.Facebook to facebookAccounts.size,
        XsmmPlatform.Instagram to instagramAccounts.size
    )

    val isAnyRunning = when (currentPlatform) {
        XsmmPlatform.TikTok -> false
        XsmmPlatform.Facebook -> com.cayxu.app.automation.facebook.XsmmFacebookManager.isAnyRunning()
        XsmmPlatform.Instagram -> com.cayxu.app.automation.instagram.XsmmInstagramManager.isAnyRunning()
    }

    val uiState = XsmmAccountsUiState(
        username = username,
        coinBalance = points,
        platformCounts = platformCounts,
        selectedPlatform = currentPlatform,
        selectedTypeId = selectedTypeId,
        accounts = currentAccounts,
        selectedAccountIds = selectedForRunUids,
        refreshingBalance = isRefreshing,
        isAnyRunning = isAnyRunning
    )

    XsmmAccountsScreen(
        state = uiState,
        onBack = { navController.popBackStack() },
        onRefreshBalance = {
            scope.launch {
                isRefreshing = true
                val token = XsmmAccountStore.getToken(context)
                if (!token.isNullOrBlank()) {
                    try {
                        when (val result = XsmmAuthRepository.fetchUser(token)) {
                            is XsmmLoginResult.Success -> {
                                XsmmSession.login(context, token, result.info.username, result.info.points)
                                Toast.makeText(context, "Đã làm mới số dư xu", Toast.LENGTH_SHORT).show()
                            }
                            is XsmmLoginResult.Error -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                isRefreshing = false
            }
        },
        onLogout = {
            XsmmSession.logout(context)
            navController.navigate(Routes.XSMM_LOGIN) {
                popUpTo(Routes.XSMM_ACCOUNT) { inclusive = true }
            }
        },
        onSelectPlatform = { plat ->
            currentPlatform = plat
            selectedPlatform = when (plat) {
                XsmmPlatform.TikTok -> "tiktok"
                XsmmPlatform.Facebook -> "facebook"
                XsmmPlatform.Instagram -> "instagram"
            }
            selectedTypeId = when (plat) {
                XsmmPlatform.TikTok -> "tiktok"
                XsmmPlatform.Facebook -> "facebook"
                XsmmPlatform.Instagram -> "instagram"
            }
            selectedForRunUids = emptySet()
        },
        onSelectType = { typeId ->
            selectedTypeId = typeId
            if (currentPlatform == XsmmPlatform.TikTok) {
                selectedVariant = when (typeId) {
                    "tiktok_lite" -> TikTokAppVariant.LITE
                    "tiktok_studio" -> TikTokAppVariant.STUDIO
                    else -> TikTokAppVariant.STANDARD
                }
            }
        },
        onToggleSelectAll = {
            val visibleIds = when (currentPlatform) {
                XsmmPlatform.TikTok -> {
                    val targetVariant = when (selectedTypeId) {
                        "tiktok_lite" -> TikTokAppVariant.LITE
                        "tiktok_studio" -> TikTokAppVariant.STUDIO
                        else -> TikTokAppVariant.STANDARD
                    }
                    allTikTokAccounts.filter { it.variant == targetVariant }.map { it.uid }
                }
                XsmmPlatform.Facebook -> facebookAccounts.map { it.uid }
                XsmmPlatform.Instagram -> instagramAccounts
            }
            val allSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedForRunUids }
            selectedForRunUids = if (allSelected) {
                selectedForRunUids - visibleIds.toSet()
            } else {
                selectedForRunUids + visibleIds.toSet()
            }
        },
        onToggleAccount = { acc ->
            selectedForRunUids = if (selectedForRunUids.contains(acc.id)) {
                selectedForRunUids - acc.id
            } else {
                selectedForRunUids + acc.id
            }
        },
        onAddAccount = {
            when (currentPlatform) {
                XsmmPlatform.TikTok -> showTikTokCheckSheet = true
                XsmmPlatform.Facebook -> showFacebookLoginSheet = true
                XsmmPlatform.Instagram -> showInstagramCookieSheet = true
            }
        },
        onOpenConfig = {
            val platformKey = when (currentPlatform) {
                XsmmPlatform.TikTok -> "tiktok"
                XsmmPlatform.Facebook -> "facebook"
                XsmmPlatform.Instagram -> "instagram"
            }
            com.cayxu.app.data.local.XsmmRunConfigStore.setActivePlatform(context, platformKey)
            navController.navigate(Routes.XSMM_RUN_CONFIG)
        },
        onRun = {
            when (currentPlatform) {
                XsmmPlatform.TikTok -> {
                    val targetVariant = when (selectedTypeId) {
                        "tiktok_lite" -> TikTokAppVariant.LITE
                        "tiktok_studio" -> TikTokAppVariant.STUDIO
                        else -> TikTokAppVariant.STANDARD
                    }
                    val accountsForCurrentVariant = allTikTokAccounts.filter { it.variant == targetVariant }
                    val selected = if (selectedForRunUids.isNotEmpty()) {
                        accountsForCurrentVariant.filter { it.uid in selectedForRunUids }
                    } else if (selectedAccountUid != null) {
                        accountsForCurrentVariant.filter { it.uid == selectedAccountUid }
                    } else {
                        accountsForCurrentVariant
                    }
                    val handles = selected.map { it.handle.trim().removePrefix("@") }.filter { it.isNotBlank() }
                    if (handles.isEmpty()) {
                        Toast.makeText(context, "Chưa có tài khoản nào để chạy", Toast.LENGTH_SHORT).show()
                    } else {
                        com.cayxu.app.ui.overlay.xsmm.startXsmmJobRunnerOverlay(context, handles)
                    }
                }
                XsmmPlatform.Facebook -> {
                    val isAnyFbRunning = com.cayxu.app.automation.facebook.XsmmFacebookManager.isAnyRunning()
                    if (isAnyFbRunning) {
                        com.cayxu.app.automation.facebook.XsmmFacebookManager.stopAll()
                        Toast.makeText(context, "Đã dừng tất cả tác vụ Facebook", Toast.LENGTH_SHORT).show()
                    } else {
                        val accountsToRun = if (selectedForRunUids.isNotEmpty()) {
                            selectedForRunUids.toList()
                        } else {
                            facebookAccounts.map { it.uid }
                        }
                        if (accountsToRun.isEmpty()) {
                            Toast.makeText(context, "Vui lòng chọn ít nhất 1 tài khoản để chạy", Toast.LENGTH_SHORT).show()
                        } else {
                            com.cayxu.app.automation.facebook.XsmmFacebookManager.startAccounts(context, accountsToRun)
                            Toast.makeText(context, "Bắt đầu chạy ${accountsToRun.size} tài khoản Facebook", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                XsmmPlatform.Instagram -> {
                    val isAnyIgRunning = com.cayxu.app.automation.instagram.XsmmInstagramManager.isAnyRunning()
                    if (isAnyIgRunning) {
                        com.cayxu.app.automation.instagram.XsmmInstagramManager.stopAll()
                        Toast.makeText(context, "Đã dừng tất cả tác vụ Instagram", Toast.LENGTH_SHORT).show()
                    } else {
                        val allIgAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context)
                        val rawList = if (selectedForRunUids.isNotEmpty()) {
                            selectedForRunUids.toList()
                        } else {
                            instagramAccounts
                        }
                        val accountsToRun = rawList.mapNotNull { uname ->
                            val clean = uname.trim().removePrefix("@").lowercase()
                            val match = allIgAccounts.firstOrNull { acc ->
                                val accUname = acc.username.trim().removePrefix("@").lowercase()
                                val accUid = acc.userId.trim().lowercase()
                                (accUname == clean || accUid == clean || acc.username == uname || acc.userId == uname) && acc.isXsmmLinked
                            }
                            match?.let { acc ->
                                val candidate = acc.username.trim().removePrefix("@")
                                if (candidate.isNotBlank() && !candidate.contains("người dùng", ignoreCase = true)) candidate
                                else acc.userId.trim().takeIf { it.isNotBlank() && !it.contains("người dùng", ignoreCase = true) } ?: candidate
                            }
                        }.distinct()
                        if (accountsToRun.isEmpty()) {
                            Toast.makeText(context, "Chưa có tài khoản nào được liên kết XSMM! Hãy thêm nick vào XSMM trước.", Toast.LENGTH_LONG).show()
                        } else {
                            com.cayxu.app.automation.instagram.XsmmInstagramManager.startAccounts(context, accountsToRun)
                            Toast.makeText(context, "Bắt đầu chạy ${accountsToRun.size} tài khoản Instagram", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        },
        onLinkAccount = { acc ->
            if (currentPlatform == XsmmPlatform.Facebook) {
                scope.launch {
                    try {
                        val token = XsmmAccountStore.getToken(context)
                        if (token.isNullOrBlank()) {
                            Toast.makeText(context, "Vui lòng đăng nhập XSMM trước", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        Toast.makeText(context, "Đang thêm nick Facebook vào XSMM...", Toast.LENGTH_SHORT).show()
                        val res = XsmmAccountsRepository.ensureFacebookAccountLinked2(token, acc.id)
                        if (res.isSuccess) {
                            linkedFbUids = linkedFbUids + acc.id
                            Toast.makeText(context, "Đã thêm Facebook [${acc.name}] vào XSMM thành công!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lỗi thêm Facebook: ${res.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                return@XsmmAccountsScreen
            }

            val cleanHandle = acc.handle.trim().removePrefix("@")
            if (cleanHandle.isBlank()) {
                Toast.makeText(context, "Handle tài khoản không hợp lệ", Toast.LENGTH_SHORT).show()
                return@XsmmAccountsScreen
            }
            val handleLower = cleanHandle.lowercase()
            if (handleLower in linkedHandles) {
                Toast.makeText(context, "Tài khoản @$cleanHandle đã được liên kết XSMM rồi", Toast.LENGTH_SHORT).show()
                return@XsmmAccountsScreen
            }
            linkingTikTokUids = linkingTikTokUids + acc.id
            scope.launch {
                try {
                    val token = XsmmAccountStore.getToken(context)
                    if (token.isNullOrBlank()) {
                        Toast.makeText(context, "Vui lòng đăng nhập XSMM trước khi liên kết", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    when (val result = XsmmAccountsRepository.addTikTokAccount2(token, cleanHandle)) {
                        is XsmmAddAccountResult.Success -> {
                            linkedHandles = linkedHandles + handleLower
                            Toast.makeText(context, "Đã liên kết @$cleanHandle với XSMM thành công!", Toast.LENGTH_SHORT).show()
                            linkedSyncTrigger = System.currentTimeMillis()
                        }
                        is XsmmAddAccountResult.Error -> {
                            if (result.message.contains("đã tồn tại", ignoreCase = true) ||
                                result.message.contains("already", ignoreCase = true) ||
                                result.message.contains("exist", ignoreCase = true)
                            ) {
                                linkedHandles = linkedHandles + handleLower
                                Toast.makeText(context, "Tài khoản @$cleanHandle đã có trên XSMM!", Toast.LENGTH_SHORT).show()
                                linkedSyncTrigger = System.currentTimeMillis()
                            } else {
                                Toast.makeText(context, "Lỗi liên kết: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi liên kết: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    linkingTikTokUids = linkingTikTokUids - acc.id
                }
            }
        },
        onReloadAccount = { acc ->
            if (currentPlatform == XsmmPlatform.Facebook) {
                scope.launch(Dispatchers.IO) {
                    val targetAcc = facebookAccounts.firstOrNull { it.uid == acc.id }
                    if (targetAcc != null) {
                        try {
                            val mgr = com.cayxu.app.facebook.FacebookAccountManager()
                            val refreshed = if (targetAcc.bio.isNotBlank()) {
                                mgr.fetchAccountDetailsWithToken(targetAcc.bio, targetAcc.phone.ifBlank { null })
                            } else if (targetAcc.note.contains("c_user=")) {
                                mgr.getTokenFromCookie(targetAcc.note, targetAcc.phone.ifBlank { null })
                            } else null

                            withContext(Dispatchers.Main) {
                                if (refreshed != null && refreshed.isLive) {
                                    val merged = targetAcc.copy(
                                        name = refreshed.name.ifBlank { targetAcc.name },
                                        avatar = refreshed.avatar.ifBlank { targetAcc.avatar },
                                        bio = refreshed.bio.ifBlank { targetAcc.bio },
                                        pages = refreshed.pages.ifEmpty { targetAcc.pages },
                                        isLive = true
                                    )
                                    com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, merged)
                                    facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
                                    Toast.makeText(context, "Đã làm mới [${merged.name}] (Live)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Không thể kết nối hoặc nick đã hết hạn", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Lỗi làm mới: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                return@XsmmAccountsScreen
            }

            if (currentPlatform == XsmmPlatform.TikTok) {
                if (!reloadingTikTokUids.contains(acc.id)) {
                    reloadingTikTokUids = reloadingTikTokUids + acc.id
                    scope.launch(Dispatchers.IO) {
                        try {
                            val client = TikTokProfileCheckerClient()
                            val clean = acc.handle.ifBlank { acc.name }.trim().removePrefix("@").split(" ").firstOrNull()?.replace("(", "")?.replace(")", "") ?: ""
                            val prof = client.fetchProfile(clean)
                            if (prof != null) {
                                withContext(Dispatchers.Main) {
                                    updateTikTokProfile(acc.id, prof)
                                    Toast.makeText(context, "Đã làm mới @$clean", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Không lấy được thông tin @$clean", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Lỗi kiểm tra: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                reloadingTikTokUids = reloadingTikTokUids - acc.id
                            }
                        }
                    }
                }
            }
        },
        onTogglePlayAccount = { acc ->
            when (currentPlatform) {
                XsmmPlatform.Facebook -> {
                    if (com.cayxu.app.automation.facebook.XsmmFacebookManager.isRunning(acc.id)) {
                        com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(acc.id)
                        Toast.makeText(context, "Đã dừng chạy: ${acc.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        com.cayxu.app.automation.facebook.XsmmFacebookManager.start(context, acc.id)
                        Toast.makeText(context, "Bắt đầu chạy: ${acc.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                XsmmPlatform.Instagram -> {
                    val clean = acc.id.trim().removePrefix("@").lowercase()
                    if (com.cayxu.app.automation.instagram.XsmmInstagramManager.isRunning(clean)) {
                        com.cayxu.app.automation.instagram.XsmmInstagramManager.stop(clean)
                        Toast.makeText(context, "Đã dừng chạy: ${acc.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        com.cayxu.app.automation.instagram.XsmmInstagramManager.start(context, clean)
                        Toast.makeText(context, "Bắt đầu chạy: ${acc.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                XsmmPlatform.TikTok -> {
                    com.cayxu.app.ui.overlay.xsmm.startXsmmJobRunnerOverlay(context, listOf(acc.handle.ifBlank { acc.id }))
                }
            }
        },
        onTogglePage = { parentAcc, page ->
            selectedForRunUids = if (selectedForRunUids.contains(page.pageId)) {
                selectedForRunUids - page.pageId
            } else {
                selectedForRunUids + page.pageId
            }
        },
        onTogglePlayPage = { parentAcc, page ->
            if (com.cayxu.app.automation.facebook.XsmmFacebookManager.isRunning(page.pageId)) {
                com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(page.pageId)
                Toast.makeText(context, "Đã dừng chạy Page: ${page.pageName}", Toast.LENGTH_SHORT).show()
            } else {
                com.cayxu.app.automation.facebook.XsmmFacebookManager.start(context, page.pageId)
                Toast.makeText(context, "Bắt đầu chạy Page: ${page.pageName}", Toast.LENGTH_SHORT).show()
            }
        },
        onReloadPage = { parentAcc, page ->
            scope.launch(Dispatchers.IO) {
                try {
                    val targetAcc = facebookAccounts.firstOrNull { it.uid == parentAcc.id }
                    val parentToken = targetAcc?.bio.orEmpty()
                    if (parentToken.isNotBlank()) {
                        val service = com.cayxu.app.facebook.FacebookPageService()
                        val refreshed = service.getPages(parentToken)
                        val match = refreshed.firstOrNull { it.pageId == page.pageId || it.additionalProfileId == page.pageId }
                        withContext(Dispatchers.Main) {
                            if (match != null) {
                                val currentStored = com.cayxu.app.data.local.FacebookAccountsStore.getAccount(context, parentAcc.id) ?: targetAcc
                                if (currentStored != null) {
                                    val updatedPages = currentStored.pages.map { p ->
                                        if (p.pageId == match.pageId) match else p
                                    }
                                    com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, currentStored.copy(pages = updatedPages))
                                    facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
                                }
                                Toast.makeText(context, "Đã làm mới Page: ${match.pageName}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Không tìm thấy Page từ Facebook", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi làm mới: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onDeleteSelected = {
            if (selectedForRunUids.isNotEmpty()) {
                showDeleteConfirmSheet = true
            }
        }
    )

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
            .background(if (isSelected) TikTokBrandBlack else CardWhite, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) TikTokBrandBlack else Color(0xFFEEF1F5),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick(variant) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "$label ($count)",
            color = if (isSelected) Color.White else TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun formatTikTokCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
private fun XsmmTikTokAccountCard(
    account: TikTokAccount,
    isSelected: Boolean,
    isAdded: Boolean,
    isAdding: Boolean,
    isCheckedForRun: Boolean,
    isReloading: Boolean,
    onCheckedForRunChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onReloadProfile: () -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val title = account.displayName.ifBlank { account.handle.ifBlank { "TikTok User" } }
    val initialLetter = (title.firstOrNull { it.isLetterOrDigit() } ?: 'T').uppercase()

    val ttAvatarModel = remember(account.avatarUrl) {
        if (account.avatarUrl.isBlank()) null
        else coil.request.ImageRequest.Builder(context)
            .data(account.avatarUrl)
            .crossfade(true)
            .build()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = if (isCheckedForRun) androidx.compose.foundation.BorderStroke(1.5.dp, TikTokBrandBlack) else null,
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
                    colors = CheckboxDefaults.colors(checkedColor = TikTokBrandBlack),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }

            // Avatar TikTok hiển thị ảnh HD thực tế
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF111111).copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (ttAvatarModel != null) {
                    AsyncImage(
                        model = ttAvatarModel,
                        contentDescription = "Avatar TikTok",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF111111)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initialLetter,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // Nội dung thông tin tài khoản TikTok
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Nút trạng thái Live / Die
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (account.isLive) Color(0xFF22C55E).copy(alpha = 0.12f) else DangerRed.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (account.isLive) Color(0xFF16A34A) else DangerRed)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (account.isLive) "Live" else "Die",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (account.isLive) Color(0xFF16A34A) else DangerRed
                        )
                    }
                }

                Spacer(Modifier.height(1.dp))
                Text(
                    text = "@${account.handle.ifBlank { "chưa_rõ" }}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )

                if (account.bio.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = account.bio,
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dòng thống kê: Followers, Tim
                val statsList = buildList {
                    if (account.followerCount > 0) add("${formatTikTokCount(account.followerCount)} followers")
                    if (account.heartCount > 0) add("${formatTikTokCount(account.heartCount)} tim")
                }
                if (statsList.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statsList.joinToString(" • "),
                        color = Color(0xFFE1306C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Dòng ngày tạo riêng biệt
                if (account.createDateFormatted.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "Tạo: ${account.createDateFormatted}",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Nút làm mới (Reload) thông tin profile TikTok
            IconButton(
                onClick = onReloadProfile,
                enabled = !isReloading,
                modifier = Modifier.size(32.dp)
            ) {
                if (isReloading) {
                    CircularProgressIndicator(
                        color = TikTokBrandBlack,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Làm mới thông tin TikTok",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Nút Thêm / Đã thêm
            when {
                isAdding -> {
                    CircularProgressIndicator(color = TikTokBrandBlack, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
                isAdded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = TikTokBrandBlack, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đã thêm", color = TikTokBrandBlack, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                else -> {
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokBrandBlack),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Thêm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val realUsername = if (platformName.equals("Instagram", ignoreCase = true)) {
                            val targetAcc = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, acc)
                            val raw = targetAcc?.username?.takeIf { u -> 
                                u.isNotBlank() && !u.equals("Người dùng Instagram", ignoreCase = true) 
                            } ?: targetAcc?.fullName?.takeIf { u ->
                                u.isNotBlank() && !u.equals("Người dùng Instagram", ignoreCase = true) && !u.contains(" ")
                            } ?: targetAcc?.userId?.takeIf { it.isNotBlank() } ?: acc
                            com.cayxu.app.instagram.InstagramApiClient.unescapeUnicode(raw).trim().removePrefix("@")
                        } else {
                            acc
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = realUsername,
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

            Text("Chi tiết nguyên nhân phản hồi:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // Nội dung chi tiết lỗi
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    val displayError = if (errorMessage.length > 500) errorMessage.take(500) + "..." else errorMessage
                    Text(
                        displayError,
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

private data class XsmmTikTokTypeOption(
    val variant: TikTokAppVariant,
    val title: String,
    val subtitle: String
)

private val xsmmTikTokOptions = listOf(
    XsmmTikTokTypeOption(TikTokAppVariant.STANDARD, "TikTok", "Phiên bản tiêu chuẩn"),
    XsmmTikTokTypeOption(TikTokAppVariant.LITE, "TikTok Lite", "Phiên bản rút gọn, nhẹ hơn"),
    XsmmTikTokTypeOption(TikTokAppVariant.STUDIO, "TikTok Studio", "Dành cho nhà sáng tạo nội dung")
)

@Composable
fun XsmmTikTokCheckSheet(
    initialVariant: TikTokAppVariant = TikTokAppVariant.STANDARD,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedApp by remember {
        mutableStateOf(
            when (initialVariant) {
                TikTokAppVariant.LITE -> com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTokLite
                TikTokAppVariant.STUDIO -> com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTokStudio
                else -> com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTok
            }
        )
    }
    var overlayGranted by remember { mutableStateOf(com.cayxu.app.automation.tiktok.TikTokAppLauncher.isOverlayPermissionGranted(context)) }
    var accessibilityGranted by remember { mutableStateOf(com.cayxu.app.automation.tiktok.TikTokAppLauncher.isAccessibilityServiceEnabled(context)) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                overlayGranted = com.cayxu.app.automation.tiktok.TikTokAppLauncher.isOverlayPermissionGranted(context)
                accessibilityGranted = com.cayxu.app.automation.tiktok.TikTokAppLauncher.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installedApps = remember(context) {
        val set = mutableSetOf<com.cayxu.app.ui.xsmm.TiktokAppVariant>()
        if (com.cayxu.app.automation.tiktok.TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STANDARD)) {
            set.add(com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTok)
        }
        if (com.cayxu.app.automation.tiktok.TikTokAppLauncher.isInstalled(context, TikTokAppVariant.LITE)) {
            set.add(com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTokLite)
        }
        if (com.cayxu.app.automation.tiktok.TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STUDIO)) {
            set.add(com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTokStudio)
        }
        set
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        com.cayxu.app.ui.xsmm.XsmmTiktokCheckScreen(
            state = com.cayxu.app.ui.xsmm.XsmmTiktokCheckUiState(
                overlayGranted = overlayGranted,
                accessibilityGranted = accessibilityGranted,
                selectedApp = selectedApp,
                installedApps = installedApps,
            ),
            onDismiss = onDismiss,
            onGrantOverlay = {
                com.cayxu.app.automation.tiktok.TikTokAppLauncher.openOverlayPermissionSettings(context)
            },
            onGrantAccessibility = {
                com.cayxu.app.automation.tiktok.TikTokAppLauncher.openAccessibilitySettings(context)
            },
            onSelectApp = { selectedApp = it },
            onCheckAccount = {
                val variant = when (selectedApp) {
                    com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTokLite -> TikTokAppVariant.LITE
                    com.cayxu.app.ui.xsmm.TiktokAppVariant.TikTokStudio -> TikTokAppVariant.STUDIO
                    else -> TikTokAppVariant.STANDARD
                }
                if (!com.cayxu.app.automation.tiktok.TikTokAppLauncher.isInstalled(context, variant)) {
                    android.widget.Toast.makeText(context, "Chưa cài đặt ${selectedApp.title} trên máy này", android.widget.Toast.LENGTH_SHORT).show()
                    return@XsmmTiktokCheckScreen
                }
                if (!overlayGranted || !accessibilityGranted) {
                    android.widget.Toast.makeText(context, "Vui lòng cấp đủ 2 quyền ở trên trước khi kiểm tra", android.widget.Toast.LENGTH_SHORT).show()
                    return@XsmmTiktokCheckScreen
                }

                com.cayxu.app.ui.overlay.xsmm.startXsmmVerifyTikTokAccount(context, variant)
                onDismiss()
            }
        )
    }
}
