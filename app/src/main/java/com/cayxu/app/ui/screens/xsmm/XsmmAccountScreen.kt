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
import android.widget.Toast
import com.cayxu.app.tiktok.checker.TikTokProfileCheckerClient
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val XsmmAccentStart = Color(0xFF34D399)
private val XsmmAccentEnd = Color(0xFF16A34A)
private val TikTokBrandBlack = Color(0xFF0F172A)
private val TikTokDarkSurface = Color(0xFF1E293B)

/**
 * MÃ n tÃ i khoáº£n XSMM - hiá»‡n username + sá»‘ dÆ° (points) dáº¡ng tháº» gradient á»Ÿ trÃªn, vÃ  NGAY BÃŠN
 * DÆ¯á»šI lÃ  danh sÃ¡ch acc TikTok (3 tab: TikTok / TikTok Lite / TikTok Studio, giá»‘ng bá»‘ cá»¥c mÃ n
 * TikTok cá»§a GoLike trÆ°á»›c Ä‘Ã¢y) - acc chÆ°a "ThÃªm" hiá»‡n nÃºt ThÃªm, acc Ä‘Ã£ thÃªm rá»“i thÃ¬ áº©n nÃºt Ä‘Ã³.
 * Cuá»‘i mÃ n cÃ³ 2 nÃºt cá»‘ Ä‘á»‹nh: "Cáº¥u hÃ¬nh cháº¡y" vÃ  "Cháº¡y".
 *
 * ÄÃ£ ná»‘i THáº¬T vá»›i API XSMM (/api/taskapi/accounts):
 *   - VÃ o mÃ n/Ä‘á»•i tab -> gá»i GET accounts?account_type=tiktok Ä‘á»ƒ biáº¿t @handle nÃ o ÄÃƒ cÃ³ trÃªn
 *     XSMM (so khá»›p theo link_account) -> tá»± áº©n nÃºt "ThÃªm" cho acc Ä‘Ã³.
 *   - Báº¥m "ThÃªm" -> gá»i THáº¬T POST accounts (type=tiktok, link_account, active=true) Ä‘á»ƒ thÃªm
 *     acc Ä‘Ã³ vÃ o XSMM (Ä‘áº·t luÃ´n lÃ m "nick cháº¡y").
 *
 * "Cáº¥u hÃ¬nh cháº¡y" vÃ  "Cháº¡y" HIá»†N VáºªN LÃ€ PLACEHOLDER - XSMM cÃ³ API GET tasks + POST
 * tasks/complete (xem XsmmTasksRepository, Ä‘Ã£ viáº¿t sáºµn sÃ ng ná»‘i) nhÆ°ng CHÆ¯A gáº¯n vÃ o Ä‘Ã¢y vÃ¬
 * "type" nhiá»‡m vá»¥ cÃ³ nhiá»u loáº¡i (tiktok_follow/tiktok_like/tiktok_comment...) vÃ  chÆ°a rÃµ mÃ n
 * nÃ y nÃªn Ä‘á»ƒ ngÆ°á»i dÃ¹ng tá»± chá»n loáº¡i nÃ o hay máº·c Ä‘á»‹nh loáº¡i nÃ o.
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
    var linkedFbUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addingFbUids by remember { mutableStateOf<Set<String>>(emptySet()) }
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

    var allTikTokAccounts by remember { mutableStateOf(TikTokAccountsStore.getAccounts(context).filter { it.enabled }) }
    var reloadingTikTokUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    val accountsForVariant = allTikTokAccounts.filter { it.variant == selectedVariant }
    var facebookAccounts by remember { mutableStateOf(com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)) }
    var instagramAccounts by remember {
        mutableStateOf(
            com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                .ifEmpty { com.cayxu.app.data.local.LinkedAccountsStore.getAccounts(context, "Instagram") }
        )
    }
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }
    var liveFbAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageUids by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageCovers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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

    LaunchedEffect(selectedPlatform, facebookAccounts) {
        if (selectedPlatform == "facebook") {
            facebookAccounts.forEach { acc ->
                val token = acc.bio.trim()
                // Tá»± Ä‘á»™ng kiá»ƒm tra vÃ  cáº­p nháº­t Avatar tháº­t cho nick cÃ¡ nhÃ¢n náº¿u chÆ°a cÃ³ hoáº·c Ä‘ang dÃ­nh áº£nh silhouette
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

    LaunchedEffect(selectedPlatform, selectedVariant, allTikTokAccounts.size) {
        if (selectedPlatform == "tiktok") {
            // Tá»± Ä‘á»™ng kiá»ƒm tra vÃ  lÃ m má»›i cho cÃ¡c tÃ i khoáº£n chÆ°a cÃ³ avatar, chÆ°a cÃ³ ngÃ y táº¡o chuáº©n, hoáº·c bá»‹ Ä‘Ã¡nh dáº¥u Die do lá»—i cÅ©
            val needCheck = accountsForVariant.filter {
                (it.avatarUrl.isBlank() || it.createDateFormatted.isBlank() || !it.isLive) &&
                it.handle.isNotBlank() && it.uid !in reloadingTikTokUids
            }
            if (needCheck.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    val client = TikTokProfileCheckerClient()
                    val jobs = needCheck.map { acc ->
                        launch(Dispatchers.IO) {
                            try {
                                val prof = client.fetchProfile(acc.handle)
                                if (prof != null) {
                                    TikTokAccountsStore.updateFullProfile(context, acc.uid, prof)
                                }
                            } catch (ignored: Exception) {}
                        }
                    }
                    jobs.forEach { it.join() }
                    withContext(Dispatchers.Main) {
                        allTikTokAccounts = TikTokAccountsStore.getAccounts(context).filter { it.enabled }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedPlatform, instagramAccounts.size) {
        if (selectedPlatform == "instagram") {
            val needCheck = instagramAccounts.mapNotNull { username ->
                val acc = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, username)
                if (acc != null && (acc.username.startsWith("IG_") || !acc.isLive || acc.avatar.isBlank() || !acc.avatar.startsWith("http") || acc.fullName.isBlank()) && acc.cookie.isNotBlank()) acc else null
            }
            if (needCheck.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    var hasUpdates = false
                    needCheck.forEach { acc ->
                        try {
                            val client = com.cayxu.app.instagram.InstagramApiClient(
                                cookie = acc.cookie,
                                proxyConfig = com.cayxu.app.instagram.InstagramApiClient.parseProxy(acc.proxy)
                            )
                            val info = client.fetchAccountDetails(acc.username)
                            val freshPic = info.profilePicUrl?.takeIf { it.startsWith("http") }
                            if (freshPic != null || info.fullName.isNotBlank() || (info.username.isNotBlank() && acc.username.startsWith("IG_")) || info.isLive != acc.isLive) {
                                val updated = acc.copy(
                                    username = if (info.username.isNotBlank() && !info.username.startsWith("IG_")) info.username else acc.username,
                                    fullName = info.fullName.ifBlank { acc.fullName },
                                    avatar = freshPic ?: acc.avatar,
                                    followersCount = if (info.followersCount > 0) info.followersCount else acc.followersCount,
                                    followingCount = if (info.followingCount > 0) info.followingCount else acc.followingCount,
                                    postsCount = if (info.postsCount > 0) info.postsCount else acc.postsCount,
                                    isLive = info.isLive
                                )
                                com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, updated)
                                hasUpdates = true
                            }
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
            // Tá»± Ä‘á»™ng kiá»ƒm tra vÃ  phá»¥c há»“i tÃªn/avatar tháº­t cá»§a nick Profile máº¹ náº¿u trÆ°á»›c Ä‘Ã³ bá»‹ ghi Ä‘Ã¨ nháº§m tÃªn Page
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
                            android.widget.Toast.makeText(context, "KhÃ´ng thá»ƒ Ä‘á»c file áº£nh", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    val acc = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, username)
                    if (acc == null || acc.cookie.isBlank()) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "KhÃ´ng tÃ¬m tháº¥y cookie cho $username", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Äang Ä‘á»•i áº£nh Ä‘áº¡i diá»‡n Instagram...", android.widget.Toast.LENGTH_SHORT).show()
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
                            android.widget.Toast.makeText(context, "Äá»•i avatar Instagram thÃ nh cÃ´ng!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "ÄÃ£ gá»­i yÃªu cáº§u Ä‘á»•i avatar Instagram", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        isUploadingAvatar = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Lá»—i Ä‘á»•i avatar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
                            android.widget.Toast.makeText(context, "KhÃ´ng thá»ƒ Ä‘á»c file áº£nh", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    val acc = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context).firstOrNull { it.uid == uid }
                    if (acc == null) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n Facebook $uid", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    val token = acc.bio.ifBlank { "" }
                    if (token.isBlank()) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "TÃ i khoáº£n cáº§n cÃ³ Access Token Ä‘á»ƒ Ä‘á»•i Avatar", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Äang Ä‘á»•i áº£nh Ä‘áº¡i diá»‡n Facebook...", android.widget.Toast.LENGTH_SHORT).show()
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
                            android.widget.Toast.makeText(context, "Äá»•i avatar Facebook thÃ nh cÃ´ng!", android.widget.Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Lá»—i Ä‘á»•i avatar: ${result.message}", android.widget.Toast.LENGTH_LONG).show()
                            isUploadingAvatar = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Lá»—i Ä‘á»•i avatar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        isUploadingAvatar = false
                    }
                }
            }
        }
    }

    if (selectedErrorDetailAccount != null) {
        val targetUser = selectedErrorDetailAccount ?: ""
        val detail = fbErrorDetailMap[targetUser]
            ?: igErrorDetailMap[targetUser]
            ?: fbStatusMap[targetUser]
            ?: igStatusMap[targetUser]
            ?: "KhÃ´ng cÃ³ thÃ´ng tin lá»—i chi tiáº¿t."
        val displayName = remember(targetUser, facebookAccounts, instagramAccounts) {
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
        ErrorDetailBottomSheet(
            accountName = displayName,
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

    if (showTikTokCheckSheet) {
        XsmmTikTokCheckSheet(
            initialVariant = selectedVariant,
            onDismiss = {
                showTikTokCheckSheet = false
                allTikTokAccounts = TikTokAccountsStore.getAccounts(context).filter { it.enabled }
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
                android.widget.Toast.makeText(context, "ÄÃ£ xÃ³a thÃ nh cÃ´ng $count tÃ i khoáº£n", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    LaunchedEffect(selectedPlatform, selectedVariant, linkedSyncTrigger) {
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
        } else {
            val allPlatformOnXsmm = XsmmAccountsRepository.getAllAccounts(token, accountType = selectedPlatform)
            val accMap = mutableMapOf<String, String>()
            val internalMap = mutableMapOf<String, String>()
            allPlatformOnXsmm.forEach { acc ->
                val handle = acc.linkAccount.substringAfterLast("@").trim('/').lowercase()
                if (handle.isNotBlank()) {
                    if (acc.accountId.isNotBlank()) accMap[handle] = acc.accountId
                    if (acc.id.isNotBlank()) internalMap[handle] = acc.id
                }
            }
            XsmmAccountStore.saveAccountIdMap(context, accMap)
            XsmmAccountStore.saveInternalIdMap(context, internalMap)
            linkedHandles = accMap.keys
        }
        isCheckingLinked = false
    }

    // Tá»± Ä‘á»™ng cáº­p nháº­t sá»‘ dÆ° XSMM Ä‘á»‹nh ká»³ má»—i 15 giÃ¢y mÃ  khÃ´ng cáº§n báº¥m reload tay
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

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay láº¡i", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text("XSMM", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- Tháº» tÃ i khoáº£n XSMM gá»n gÃ ng ----
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
                    val initial = (username.trim().firstOrNull()?.uppercaseChar() ?: 'X').toString()
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2)),
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
                            text = username.ifBlank { "XSMM User" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        val formattedPoints = try {
                            java.text.NumberFormat.getInstance(java.util.Locale("vi", "VN")).format(points)
                        } catch (_: Exception) {
                            points.toString()
                        }
                        Text(
                            text = "$formattedPoints xu",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1877F2)
                        )
                    }
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

                                        val count = xsmmUids.size
                                        android.widget.Toast.makeText(context, "ÄÃ£ Ä‘á»“ng bá»™ XSMM: $count tÃ i khoáº£n Facebook Ä‘Ã£ liÃªn káº¿t", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    isRefreshing = false
                                }
                            }
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "LÃ m má»›i", tint = XsmmAccentEnd, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(
                        onClick = {
                            XsmmSession.logout(context)
                            navController.navigate(Routes.XSMM_LOGIN) {
                                popUpTo(Routes.XSMM_ACCOUNT) { inclusive = true }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "ÄÄƒng xuáº¥t", tint = DangerRed, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Chá»n ná»n táº£ng (TikTok / Facebook / Instagram) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPlatform == "tiktok",
                    onClick = {
                        selectedPlatform = "tiktok"
                        com.cayxu.app.data.local.XsmmRunConfigStore.setActivePlatform(context, "tiktok")
                        selectedForRunUids = emptySet()
                    },
                    label = { Text("TikTok (${allTikTokAccounts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TikTokBrandBlack.copy(alpha = 0.12f),
                        selectedLabelColor = TikTokBrandBlack
                    )
                )
                FilterChip(
                    selected = selectedPlatform == "facebook",
                    onClick = {
                        selectedPlatform = "facebook"
                        com.cayxu.app.data.local.XsmmRunConfigStore.setActivePlatform(context, "facebook")
                        selectedForRunUids = emptySet()
                    },
                    label = { Text("Facebook (${facebookAccounts.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1877F2).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFF1877F2)
                    )
                )
                FilterChip(
                    selected = selectedPlatform == "instagram",
                    onClick = {
                        selectedPlatform = "instagram"
                        com.cayxu.app.data.local.XsmmRunConfigStore.setActivePlatform(context, "instagram")
                        selectedForRunUids = emptySet()
                    },
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
                    Text("TÃ i khoáº£n TikTok", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    
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
                                Icon(Icons.Filled.Delete, contentDescription = "XÃ³a tÃ i khoáº£n Ä‘Ã£ chá»n", tint = DangerRed, modifier = Modifier.size(18.dp))
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
                            colors = CheckboxDefaults.colors(checkedColor = TikTokBrandBlack),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Táº¥t cáº£", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = { showTikTokCheckSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(TikTokBrandBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "ThÃªm/Kiá»ƒm tra tÃ i khoáº£n TikTok", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
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
                    Text("TÃ i khoáº£n Facebook", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    
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
                                Icon(Icons.Filled.Delete, contentDescription = "XÃ³a tÃ i khoáº£n Ä‘Ã£ chá»n", tint = DangerRed, modifier = Modifier.size(18.dp))
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
                            Icon(Icons.Filled.Add, contentDescription = "ThÃªm tÃ i khoáº£n Facebook", tint = Color(0xFF1877F2), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("TÃ i khoáº£n Instagram", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    
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
                                Icon(Icons.Filled.Delete, contentDescription = "XÃ³a tÃ i khoáº£n Ä‘Ã£ chá»n", tint = DangerRed, modifier = Modifier.size(18.dp))
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
                            Icon(Icons.Filled.Add, contentDescription = "ThÃªm tÃ i khoáº£n Instagram", tint = Color(0xFFE1306C), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isCheckingLinked) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Äang kiá»ƒm tra tÃ i khoáº£n trÃªn XSMM...", color = TextSecondary, fontSize = 12.sp)
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
                            Text("ChÆ°a cÃ³ tÃ i khoáº£n nÃ o á»Ÿ loáº¡i nÃ y - thÃªm á»Ÿ pháº§n Quáº£n lÃ½ tÃ i khoáº£n TikTok trÆ°á»›c.", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accountsForVariant.forEach { account ->
                            val handleLower = account.handle.trim().removePrefix("@").lowercase()
                            val isLinked = handleLower in linkedHandles
                            val isThisReloading = account.uid in reloadingTikTokUids
                            XsmmTikTokAccountCard(
                                account = account,
                                isSelected = account.uid == selectedAccountUid,
                                isAdded = isLinked,
                                isAdding = addingUid == account.uid,
                                isCheckedForRun = account.uid in selectedForRunUids,
                                isReloading = isThisReloading,
                                onCheckedForRunChange = { checked ->
                                    if (!isLinked) return@XsmmTikTokAccountCard
                                    selectedForRunUids = if (checked) selectedForRunUids + account.uid
                                    else selectedForRunUids - account.uid
                                },
                                onClick = { selectedAccountUid = account.uid },
                                onReloadProfile = {
                                    if (isThisReloading || account.handle.isBlank()) return@XsmmTikTokAccountCard
                                    reloadingTikTokUids = reloadingTikTokUids + account.uid
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val client = TikTokProfileCheckerClient()
                                            val profile = client.fetchProfile(account.handle)
                                            if (profile != null) {
                                                TikTokAccountsStore.updateFullProfile(context, account.uid, profile)
                                                withContext(Dispatchers.Main) {
                                                    allTikTokAccounts = TikTokAccountsStore.getAccounts(context).filter { it.enabled }
                                                    val msg = if (profile.isLive) {
                                                        "ÄÃ£ cáº­p nháº­t @${profile.username}: Live (${formatTikTokCount(profile.followerCount)} follow)"
                                                    } else {
                                                        "TÃ i khoáº£n @${profile.username} khÃ´ng tá»“n táº¡i hoáº·c bá»‹ khÃ³a"
                                                    }
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    android.widget.Toast.makeText(context, "KhÃ´ng thá»ƒ káº¿t ná»‘i TikTok, vui lÃ²ng thá»­ láº¡i", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, "Lá»—i cáº­p nháº­t: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } finally {
                                            withContext(Dispatchers.Main) {
                                                reloadingTikTokUids = reloadingTikTokUids - account.uid
                                            }
                                        }
                                    }
                                },
                                onAddClick = {
                                    val token = XsmmAccountStore.getToken(context)
                                    if (token.isNullOrBlank()) {
                                        android.widget.Toast.makeText(context, "ChÆ°a Ä‘Äƒng nháº­p XSMM", android.widget.Toast.LENGTH_SHORT).show()
                                        return@XsmmTikTokAccountCard
                                    }
                                    addingUid = account.uid
                                    scope.launch {
                                        when (val result = XsmmAccountsRepository.addTikTokAccount(token, account.handle)) {
                                             is XsmmAddAccountResult.Success -> {
                                                linkedHandles = linkedHandles + handleLower
                                                android.widget.Toast.makeText(context, "ÄÃ£ thÃªm @${account.handle} vÃ o XSMM", android.widget.Toast.LENGTH_SHORT).show()
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
                            Text("ChÆ°a cÃ³ tÃ i khoáº£n Facebook nÃ o.", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Báº¥m vÃ o Ä‘Ã¢y hoáº·c nÃºt dáº¥u + Ä‘á»ƒ Ä‘Äƒng nháº­p tÃ i khoáº£n Facebook.", color = Color(0xFF1877F2), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        facebookAccounts.forEach { account ->
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

                                        // Avatar Facebook cÃ³ nÃºt Ä‘á»•i áº£nh cÃ¢y bÃºt nhá» náº±m bÃªn trong
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

                                            // Lá»›p phá»§ vÃ  icon bÃºt sá»­a áº£nh náº±m bÃªn trong Ä‘Ã¡y avatar
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
                                                    contentDescription = "Äá»•i avatar",
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

                                                // NÃºt Live/Die náº±m ngay cáº¡nh tÃªn acc
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

                                            Spacer(Modifier.height(4.dp))
                                            // Tráº¡ng thÃ¡i kiá»ƒm tra trÃªn XSMM / NÃºt ThÃªm vÃ o XSMM náº±m ngang hÃ ng riÃªng
                                            val isFbLinked = account.uid.trim() in linkedFbUids
                                            val isFbAdding = account.uid in addingFbUids

                                            if (isFbAdding) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF1877F2).copy(alpha = 0.08f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = Color(0xFF1877F2),
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(
                                                        "Äang thÃªm...",
                                                        color = Color(0xFF1877F2),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            } else if (isFbLinked) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF16A34A).copy(alpha = 0.12f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF16A34A),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "ÄÃ£ liÃªn káº¿t XSMM",
                                                        color = Color(0xFF16A34A),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF1877F2).copy(alpha = 0.12f))
                                                        .clickable {
                                                            val token = XsmmAccountStore.getToken(context)
                                                            if (token.isNullOrBlank()) {
                                                                android.widget.Toast.makeText(context, "ChÆ°a Ä‘Äƒng nháº­p XSMM", android.widget.Toast.LENGTH_SHORT).show()
                                                                return@clickable
                                                            }
                                                            addingFbUids = addingFbUids + account.uid
                                                            scope.launch {
                                                                when (val res = XsmmAccountsRepository.addFacebookAccount(token, account.uid)) {
                                                                    is XsmmAddAccountResult.Success -> {
                                                                        android.widget.Toast.makeText(context, "ÄÃ£ thÃªm Facebook [${account.name.ifBlank { account.uid }}] vÃ o XSMM, Ä‘ang Ä‘á»“ng bá»™...", android.widget.Toast.LENGTH_SHORT).show()
                                                                        linkedSyncTrigger = System.currentTimeMillis()
                                                                    }
                                                                    is XsmmAddAccountResult.Error -> {
                                                                        android.widget.Toast.makeText(context, "Lá»—i thÃªm XSMM: ${res.message}", android.widget.Toast.LENGTH_LONG).show()
                                                                    }
                                                                }
                                                                addingFbUids = addingFbUids - account.uid
                                                            }
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Add,
                                                        contentDescription = null,
                                                        tint = Color(0xFF1877F2),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        "ThÃªm vÃ o XSMM",
                                                        color = Color(0xFF1877F2),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }

                                        // NÃºt Reload (LÃ m má»›i) mÃ u xanh chá»§ Ä‘áº¡o Facebook
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
                                                                com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updated)
                                                            } else {
                                                                val updated = account.copy(isLive = false)
                                                                com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updated)
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
                                                                com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updated)
                                                            } else {
                                                                val updated = account.copy(isLive = false)
                                                                com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updated)
                                                            }
                                                        }
                                                    } catch (_: Exception) {
                                                        val updated = account.copy(isLive = false)
                                                        com.cayxu.app.data.local.FacebookAccountsStore.addAccount(context, updated)
                                                    }

                                                    // Kiá»ƒm tra thá»±c táº¿ tÃ i khoáº£n nÃ y vÃ  cÃ¡c Page cá»§a nÃ³ qua danh sÃ¡ch toÃ n bá»™ acc XSMM
                                                    val xsmmToken = XsmmAccountStore.getToken(context)
                                                    if (!xsmmToken.isNullOrBlank()) {
                                                        val allFbOnXsmm = XsmmAccountsRepository.getFacebookAccounts(xsmmToken)
                                                        val targetUids = mutableListOf(account.uid.trim())
                                                        account.pages.forEach { p ->
                                                            val pUid = p.additionalProfileId.ifBlank { p.pageId }.trim()
                                                            if (pUid.isNotBlank()) targetUids.add(pUid)
                                                        }
                                                        val accMap = XsmmAccountStore.getAccountIdMap(context).toMutableMap()
                                                        val internalMap = XsmmAccountStore.getInternalIdMap(context).toMutableMap()
                                                        val newlyLinked = mutableSetOf<String>()
                                                        val newlyUnlinked = mutableSetOf<String>()

                                                        targetUids.forEach { u ->
                                                            val matched = allFbOnXsmm.firstOrNull { it.accountId.trim() == u || it.linkAccount.contains(u) }
                                                            if (matched != null) {
                                                                newlyLinked.add(u)
                                                                accMap[u] = matched.accountId.trim()
                                                                if (matched.id.isNotBlank()) internalMap[u] = matched.id
                                                            } else {
                                                                newlyUnlinked.add(u)
                                                                accMap.remove(u)
                                                                internalMap.remove(u)
                                                            }
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            linkedFbUids = (linkedFbUids + newlyLinked) - newlyUnlinked
                                                            XsmmAccountStore.saveAccountIdMap(context, accMap)
                                                            XsmmAccountStore.saveInternalIdMap(context, internalMap)
                                                        }
                                                    }

                                                    withContext(Dispatchers.Main) {
                                                        avatarVersion = System.currentTimeMillis()
                                                        facebookAccounts = com.cayxu.app.data.local.FacebookAccountsStore.getAccounts(context, forceReload = true)
                                                        android.widget.Toast.makeText(context, "ÄÃ£ lÃ m má»›i thÃ´ng tin Facebook", android.widget.Toast.LENGTH_SHORT).show()
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
                                                    contentDescription = "LÃ m má»›i",
                                                    tint = Color(0xFF1877F2),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(6.dp))

                                        // NÃºt Cháº¡y (Play tam giÃ¡c mÃ u xanh Facebook) / Dá»«ng
                                        IconButton(
                                            onClick = {
                                                if (isRunningFbThis) {
                                                    com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(account.uid)
                                                    android.widget.Toast.makeText(context, "ÄÃ£ dá»«ng cháº¡y Facebook: ${account.name.ifBlank { account.uid }}", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    com.cayxu.app.automation.facebook.XsmmFacebookManager.start(context, account.uid)
                                                    android.widget.Toast.makeText(context, "Báº¯t Ä‘áº§u cháº¡y Facebook: ${account.name.ifBlank { account.uid }}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isRunningFbThis) DangerRed else Color(0xFF1877F2)),
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
                                                        contentDescription = "Cháº¡y",
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
                                                            color = Color(0xFF1877F2),
                                                            strokeWidth = 1.6.dp,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Spacer(Modifier.width(5.dp))
                                                    }
                                                    Text(
                                                        text = fbStatus ?: "Sáºµn sÃ ng",
                                                        fontSize = 11.5.sp,
                                                        color = if (isRunningFbThis) Color(0xFF1877F2) else TextSecondary,
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
                                                                        contentDescription = "Xem chi tiáº¿t lá»—i",
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

                                    // Tráº¡ng thÃ¡i Page: Náº¿u khÃ´ng cÃ³ page -> hiá»ƒn thá»‹ "TÃ i khoáº£n khÃ´ng cÃ³ page", náº¿u cÃ³ page -> hiá»ƒn thá»‹ danh sÃ¡ch vá»›i chá»¯ "Page: " á»Ÿ trÆ°á»›c tÃªn
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
                                                "TÃ i khoáº£n khÃ´ng cÃ³ page",
                                                fontSize = 11.5.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                color = TextSecondary.copy(alpha = 0.8f)
                                            )
                                        } else {
                                            Text(
                                                "Danh sÃ¡ch Page / Profile+ (${account.pages.size}):",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextSecondary
                                            )
                                        }

                                        // NÃºt cháº¥m than (i) xem Full Info xuá»‘ng cÃ¹ng hÃ ng vá»›i tráº¡ng thÃ¡i Page
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
                                                    contentDescription = "Xem thÃ´ng tin chi tiáº¿t",
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
                                                        // 1. Dáº¥u tÃ­ch chá»n (Checkbox) cá»§a Page nhÆ° Profile
                                                        Checkbox(
                                                            checked = isPageChecked,
                                                            onCheckedChange = { checked ->
                                                                selectedForRunUids = if (checked) {
                                                                    selectedForRunUids + effectivePageUid
                                                                } else {
                                                                    selectedForRunUids - effectivePageUid - page.pageId - page.additionalProfileId - pageDisplayUid
                                                                }
                                                            },
                                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1877F2)),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))

                                                        // 2. Avatar cá»§a Page
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
                                                                    .background(Color(0xFF1877F2).copy(alpha = 0.15f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Flag,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF1877F2),
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.width(8.dp))

                                                        // 3. TÃªn Page vÃ  Ã©p hiá»ƒn thá»‹ UID tháº­t (615), khÃ´ng hiá»ƒn thá»‹ ID page
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

                                                            Spacer(Modifier.height(3.dp))

                                                             // Tráº¡ng thÃ¡i liÃªn káº¿t Page: chá»‰ so sÃ¡nh UID Page vá»›i danh sÃ¡ch account_id tá»« XSMM
                                                             val pageUid = effectivePageUid
                                                             val isPageLinked = pageUid.isNotBlank() && pageUid in linkedFbUids
                                                             val isPageAdding = pageUid.isNotBlank() && pageUid in addingFbUids

                                                            if (isPageAdding) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(5.dp))
                                                                        .background(Color(0xFF1877F2).copy(alpha = 0.08f))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    CircularProgressIndicator(
                                                                        color = Color(0xFF1877F2),
                                                                        strokeWidth = 1.5.dp,
                                                                        modifier = Modifier.size(11.dp)
                                                                    )
                                                                    Spacer(Modifier.width(4.dp))
                                                                    Text(
                                                                        "Äang thÃªm...",
                                                                        color = Color(0xFF1877F2),
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            } else if (isPageLinked) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(5.dp))
                                                                        .background(Color(0xFF16A34A).copy(alpha = 0.12f))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Filled.Check,
                                                                        contentDescription = null,
                                                                        tint = Color(0xFF16A34A),
                                                                        modifier = Modifier.size(11.dp)
                                                                    )
                                                                    Spacer(Modifier.width(3.dp))
                                                                    Text(
                                                                        "ÄÃ£ liÃªn káº¿t XSMM",
                                                                        color = Color(0xFF16A34A),
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            } else {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(5.dp))
                                                                        .background(Color(0xFF1877F2).copy(alpha = 0.12f))
                                                                        .clickable {
                                                                            val token = XsmmAccountStore.getToken(context)
                                                                            if (token.isNullOrBlank()) {
                                                                                android.widget.Toast.makeText(context, "ChÆ°a Ä‘Äƒng nháº­p XSMM", android.widget.Toast.LENGTH_SHORT).show()
                                                                                return@clickable
                                                                            }
                                                                            val targetToAdd = pageUid
                                                                            if (targetToAdd.isBlank()) {
                                                                                android.widget.Toast.makeText(context, "ChÆ°a xÃ¡c Ä‘á»‹nh Ä‘Æ°á»£c UID cá»§a Page", android.widget.Toast.LENGTH_SHORT).show()
                                                                                return@clickable
                                                                            }
                                                                            addingFbUids = addingFbUids + targetToAdd
                                                                            scope.launch {
                                                                                when (val res = XsmmAccountsRepository.addFacebookAccount(token, targetToAdd)) {
                                                                                    is XsmmAddAccountResult.Success -> {
                                                                                        android.widget.Toast.makeText(context, "ÄÃ£ thÃªm Page [${page.pageName.ifBlank { targetToAdd }}] vÃ o XSMM, Ä‘ang Ä‘á»“ng bá»™...", android.widget.Toast.LENGTH_SHORT).show()
                                                                                        linkedSyncTrigger = System.currentTimeMillis()
                                                                                    }
                                                                                    is XsmmAddAccountResult.Error -> {
                                                                                        android.widget.Toast.makeText(context, "Lá»—i thÃªm XSMM: ${res.message}", android.widget.Toast.LENGTH_LONG).show()
                                                                                    }
                                                                                }
                                                                                addingFbUids = addingFbUids - targetToAdd
                                                                            }
                                                                        }
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Filled.Add,
                                                                        contentDescription = null,
                                                                        tint = Color(0xFF1877F2),
                                                                        modifier = Modifier.size(11.dp)
                                                                    )
                                                                    Spacer(Modifier.width(3.dp))
                                                                    Text(
                                                                        "ThÃªm vÃ o XSMM",
                                                                        color = Color(0xFF1877F2),
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // 4. Dáº¥u cháº¥m than xanh (i) xem info vÃ  Ä‘á»•i avatar bÃ¬a cá»§a page
                                                        IconButton(
                                                            onClick = { selectedFbDetailPage = Pair(account, page) },
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
                                                                    contentDescription = "Xem thÃ´ng tin vÃ  Ä‘á»•i avatar bÃ¬a cá»§a Page",
                                                                    tint = Color(0xFF1877F2),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }

                                                        Spacer(Modifier.width(4.dp))

                                                        // 5. NÃºt Play / Stop riÃªng cho Page
                                                        IconButton(
                                                            onClick = {
                                                                if (isPageRunning) {
                                                                    com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(effectivePageUid)
                                                                    com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(page.pageId)
                                                                    if (page.additionalProfileId.isNotBlank()) com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(page.additionalProfileId)
                                                                    if (pageDisplayUid.isNotBlank()) com.cayxu.app.automation.facebook.XsmmFacebookManager.stop(pageDisplayUid)
                                                                    android.widget.Toast.makeText(context, "ÄÃ£ dá»«ng cháº¡y Page: ${page.pageName.ifBlank { effectivePageUid }}", android.widget.Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    com.cayxu.app.automation.facebook.XsmmFacebookManager.start(context, effectivePageUid)
                                                                    android.widget.Toast.makeText(context, "Báº¯t Ä‘áº§u cháº¡y Page: ${page.pageName.ifBlank { effectivePageUid }}", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(26.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isPageRunning) DangerRed else Color(0xFF1877F2)),
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
                                                                        contentDescription = "Cháº¡y Page",
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(15.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 6. Tráº¡ng thÃ¡i cháº¡y & tiáº¿n Ä‘á»™ nháº­n nhiá»‡m vá»¥ cá»§a Page (Live status)
                                                    val pageStatus = fbStatusMap[effectivePageUid]
                                                        ?: fbStatusMap[page.pageId]
                                                        ?: fbStatusMap[page.additionalProfileId]
                                                        ?: (if (pageDisplayUid.isNotBlank()) fbStatusMap[pageDisplayUid] else null)

                                                    val pageSuccess = fbSuccessCountMap[effectivePageUid]
                                                        ?: fbSuccessCountMap[page.pageId]
                                                        ?: fbSuccessCountMap[page.additionalProfileId]
                                                        ?: (if (pageDisplayUid.isNotBlank()) fbSuccessCountMap[pageDisplayUid] else null)
                                                        ?: 0

                                                    val pageErrors = fbErrorCountMap[effectivePageUid]
                                                        ?: fbErrorCountMap[page.pageId]
                                                        ?: fbErrorCountMap[page.additionalProfileId]
                                                        ?: (if (pageDisplayUid.isNotBlank()) fbErrorCountMap[pageDisplayUid] else null)
                                                        ?: 0

                                                    val pageErrorDetail = fbErrorDetailMap[effectivePageUid]
                                                        ?: fbErrorDetailMap[page.pageId]
                                                        ?: fbErrorDetailMap[page.additionalProfileId]
                                                        ?: (if (pageDisplayUid.isNotBlank()) fbErrorDetailMap[pageDisplayUid] else null)

                                                    if (isPageRunning || !pageStatus.isNullOrBlank() || pageSuccess > 0 || pageErrors > 0 || !pageErrorDetail.isNullOrBlank()) {
                                                        Spacer(Modifier.height(5.dp))
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isPageRunning) Color(0xFF1877F2).copy(alpha = 0.08f) else Color(0xFFE2E8F0).copy(alpha = 0.4f))
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
                                                                            color = Color(0xFF1877F2),
                                                                            strokeWidth = 1.6.dp,
                                                                            modifier = Modifier.size(10.dp)
                                                                        )
                                                                        Spacer(Modifier.width(5.dp))
                                                                    }
                                                                    Text(
                                                                        text = pageStatus ?: if (isPageRunning) "Äang cháº¡y..." else "Sáºµn sÃ ng",
                                                                        fontSize = 11.sp,
                                                                        color = if (isPageRunning) Color(0xFF1877F2) else TextSecondary,
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
                                                                                        contentDescription = "Xem chi tiáº¿t lá»—i Page",
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
                            Text("ChÆ°a cÃ³ tÃ i khoáº£n Instagram nÃ o.", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Báº¥m dáº¥u + Ä‘á»ƒ Ä‘Äƒng nháº­p tÃ i khoáº£n Instagram.", color = TextSecondary.copy(alpha = 0.8f), fontSize = 12.sp)
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
                                val av = igAcc?.avatar?.trim().orEmpty()
                                if (av.isBlank() || !av.startsWith("http")) null
                                else coil.request.ImageRequest.Builder(context)
                                    .data(av)
                                    .crossfade(true)
                                    .memoryCacheKey("${av}_$avatarVersion")
                                    .diskCacheKey("${av}_$avatarVersion")
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

                                        // Avatar Instagram cÃ³ nÃºt camera Ä‘á»•i áº£nh náº±m gá»n BÃŠN TRONG avatar
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
                                            if (avatarModel != null) {
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

                                            // Lá»›p phá»§ vÃ  icon bÃºt/camera sá»­a áº£nh náº±m bÃªn trong Ä‘Ã¡y avatar
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
                                                    contentDescription = "Äá»•i avatar",
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

                                                // NÃºt Live/Die náº±m ngay cáº¡nh tÃªn acc (khÃ´ng láº·p láº¡i tÃªn)
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
                                                if ((igAcc?.followingCount ?: 0) > 0) add("${igAcc?.followingCount} Ä‘ang theo dÃµi")
                                                if ((igAcc?.postsCount ?: 0) > 0) add("${igAcc?.postsCount} bÃ i viáº¿t")
                                            }
                                            if (stats.isNotEmpty()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = stats.joinToString(" â€¢ "),
                                                    color = Color(0xFFE1306C),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        // NÃºt Reload (LÃ m má»›i)
                                        val isRunningThis = com.cayxu.app.automation.instagram.XsmmInstagramManager.isRunning(cleanIg)
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    val acc = com.cayxu.app.data.local.InstagramAccountsStore.getAccount(context, cleanIg)
                                                    if (acc == null || acc.cookie.isBlank()) {
                                                        withContext(Dispatchers.Main) {
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[cleanIg] = "ChÆ°a lÆ°u cookie"
                                                            android.widget.Toast.makeText(context, "KhÃ´ng tÃ¬m tháº¥y cookie cho $cleanIg", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        return@launch
                                                    }
                                                    try {
                                                        withContext(Dispatchers.Main) {
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[cleanIg] = "Äang láº¥y thÃ´ng tin..."
                                                        }
                                                        val proxyConfig = com.cayxu.app.instagram.InstagramApiClient.parseProxy(acc.proxy)
                                                        val client = com.cayxu.app.instagram.InstagramApiClient(
                                                            cookie = acc.cookie,
                                                            proxyConfig = proxyConfig
                                                        )
                                                        val info = client.fetchAccountDetails(acc.username)
                                                        val finalUsername = info.username.ifBlank { acc.username }
                                                        val updatedAcc = acc.copy(
                                                            username = finalUsername,
                                                            userId = info.userId.ifBlank { acc.userId },
                                                            fullName = info.fullName.ifBlank { acc.fullName },
                                                            avatar = info.profilePicUrl?.takeIf { it.startsWith("http") } ?: acc.avatar,
                                                            fbDtsg = info.fbDtsg ?: acc.fbDtsg,
                                                            lsd = info.lsd ?: acc.lsd,
                                                            biography = info.biography.ifBlank { acc.biography },
                                                            followersCount = if (info.followersCount > 0) info.followersCount else acc.followersCount,
                                                            followingCount = if (info.followingCount > 0) info.followingCount else acc.followingCount,
                                                            postsCount = if (info.postsCount > 0) info.postsCount else acc.postsCount,
                                                            isLive = info.isLive
                                                        )
                                                        com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, updatedAcc)
                                                        if (cleanIg != finalUsername) {
                                                            com.cayxu.app.data.local.LinkedAccountsStore.removeAccount(context, "Instagram", cleanIg)
                                                            com.cayxu.app.data.local.LinkedAccountsStore.addAccount(context, "Instagram", finalUsername)
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            val nameDisplay = if (updatedAcc.fullName.isNotBlank()) updatedAcc.fullName else updatedAcc.username
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[finalUsername] = if (info.isLive) "Sáºµn sÃ ng" else "Lá»—i: Checkpoint / DIE"
                                                            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                                                            val toastMsg = if (info.isLive) "ÄÃ£ cáº­p nháº­t: $nameDisplay" else "Cookie DIE hoáº·c bá»‹ Checkpoint: $nameDisplay"
                                                            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        val deadAcc = acc.copy(isLive = false)
                                                        com.cayxu.app.data.local.InstagramAccountsStore.updateAccount(context, deadAcc)
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            com.cayxu.app.automation.instagram.XsmmInstagramManager.statusMap[cleanIg] = "Lá»—i: Cookie DIE / Checkpoint"
                                                            instagramAccounts = com.cayxu.app.data.local.InstagramAccountsStore.getAccounts(context).map { it.username }
                                                            android.widget.Toast.makeText(context, "Lá»—i kiá»ƒm tra $cleanIg: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
                                                    contentDescription = "LÃ m má»›i",
                                                    tint = Color(0xFFE1306C),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(6.dp))

                                        // NÃºt Cháº¡y (Play tam giÃ¡c) / Dá»«ng (Stop Ã´ vuÃ´ng Ä‘á»)
                                        IconButton(
                                            onClick = {
                                                if (isRunningThis) {
                                                    com.cayxu.app.automation.instagram.XsmmInstagramManager.stop(cleanIg)
                                                    android.widget.Toast.makeText(context, "ÄÃ£ dá»«ng cháº¡y $cleanIg", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    com.cayxu.app.automation.instagram.XsmmInstagramManager.start(context, cleanIg)
                                                    android.widget.Toast.makeText(context, "Báº¯t Ä‘áº§u cháº¡y $cleanIg", android.widget.Toast.LENGTH_SHORT).show()
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
                                                        contentDescription = "Cháº¡y",
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

                                    // Khu vá»±c hiá»ƒn thá»‹ tráº¡ng thÃ¡i + thá»‘ng kÃª HoÃ n thÃ nh / Lá»—i + Proxy
                                    val rawStatus = igStatusMap[cleanIg] ?: "Tráº¡ng thÃ¡i: Sáºµn sÃ ng"
                                    val currentStatus = if (rawStatus.equals("Live", ignoreCase = true)) "Tráº¡ng thÃ¡i: Sáºµn sÃ ng" else rawStatus
                                    val successCount = igSuccessCountMap[cleanIg] ?: 0
                                    val errorCount = igErrorCountMap[cleanIg] ?: 0
                                    val isError = currentStatus.contains("Lá»—i", ignoreCase = true) || currentStatus.contains("DIE", ignoreCase = true) || currentStatus.contains("KhÃ´ng tÃ¬m tháº¥y", ignoreCase = true)
                                    val isRunningNow = com.cayxu.app.automation.instagram.XsmmInstagramManager.isRunning(cleanIg)

                                    // HÃ m rÃºt gá»n proxy: 128.0.0.1:3098:user:pass -> 128......pass hoáº·c 128...3098
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
                                        // DÃ²ng 1: Status text kÃ¨m cháº¥m trÃ²n tráº¡ng thÃ¡i (Ä‘áº¿m ngÆ°á»£c thá»i gian)
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

                                        // DÃ²ng 2: Hiá»ƒn thá»‹ Thá»‘ng kÃª HoÃ n thÃ nh / Lá»—i + Proxy bÃªn cáº¡nh
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // ThÃ nh cÃ´ng
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
                                                        "HoÃ n thÃ nh: $successCount",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF15803D)
                                                    )
                                                }

                                                // Tháº¥t báº¡i / Lá»—i
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
                                                        "Lá»—i: $errorCount",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DangerRed
                                                    )
                                                }

                                                // Proxy rÃºt gá»n bÃªn cáº¡nh nÃºt Lá»—i (náº¿u acc cÃ³ gÃ¡n proxy)
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
                                                            contentDescription = "Xem chi tiáº¿t lá»—i",
                                                            tint = DangerRed,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            } else if (isRunningNow) {
                                                Text(
                                                    "Äang cháº¡y...",
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

        // ---- Thanh Ä‘iá»u khiá»ƒn cá»‘ Ä‘á»‹nh dÆ°á»›i cÃ¹ng ----
        if (selectedPlatform == "tiktok") {
            // ---- TikTok: 2 nÃºt Cáº¥u hÃ¬nh cháº¡y + Cháº¡y to mÃ u Ä‘en TikTok ----
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TikTokBrandBlack),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TikTokBrandBlack.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = TikTokBrandBlack)
                    Spacer(Modifier.width(6.dp))
                    Text("Cáº¥u hÃ¬nh cháº¡y", maxLines = 1, color = TikTokBrandBlack)
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
                            android.widget.Toast.makeText(context, "ChÆ°a cÃ³ tÃ i khoáº£n nÃ o Ä‘á»ƒ cháº¡y", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        com.cayxu.app.ui.overlay.xsmm.startXsmmJobRunnerOverlay(context, handles)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TikTokBrandBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    val runCount = if (selectedForRunUids.isNotEmpty()) selectedForRunUids.size else accountsForVariant.size
                    Text(if (runCount > 1) "Cháº¡y ($runCount)" else "Cháº¡y", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // ---- Instagram & Facebook: Thanh cÃ´ng cá»¥ tiá»‡n Ã­ch (Cáº¥u hÃ¬nh, Táº¥t cáº£, XÃ³a, ThÃªm +) ----
            val isIg = selectedPlatform == "instagram"
            val platformColor = if (isIg) Color(0xFFE1306C) else Color(0xFF1877F2)
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
            val allSelected = if (isIg) {
                instagramAccounts.isNotEmpty() && instagramAccounts.all { it.trim() in selectedForRunUids }
            } else {
                allFbKeys.isNotEmpty() && allFbKeys.all { it in selectedForRunUids }
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
                    // NÃºt Cáº¥u hÃ¬nh cháº¡y
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
                        Text("Cáº¥u hÃ¬nh", fontSize = 13.sp, color = platformColor)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // NÃºt Táº¥t cáº£
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
                                        selectedForRunUids = if (allSelected) selectedForRunUids - allFbKeys
                                        else selectedForRunUids + allFbKeys
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
                            Text("Táº¥t cáº£", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        // NÃºt XÃ³a (thÃ¹ng rÃ¡c Ä‘á» khi cÃ³ acc Ä‘Æ°á»£c chá»n)
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
                                        contentDescription = "XÃ³a tÃ i khoáº£n Ä‘Ã£ chá»n",
                                        tint = DangerRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // NÃºt Cháº¡y táº¥t cáº£ / Dá»«ng táº¥t cáº£ (DÃ nh cho Instagram)
                        if (isIg && instagramAccounts.isNotEmpty()) {
                            val isAnyIgRunning = com.cayxu.app.automation.instagram.XsmmInstagramManager.isAnyRunning()
                            IconButton(
                                onClick = {
                                    if (isAnyIgRunning) {
                                        com.cayxu.app.automation.instagram.XsmmInstagramManager.stopAll()
                                        android.widget.Toast.makeText(context, "ÄÃ£ dá»«ng táº¥t cáº£ tÃ¡c vá»¥ Instagram", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val accountsToRun = if (selectedForRunUids.isNotEmpty()) {
                                            selectedForRunUids.toList()
                                        } else {
                                            instagramAccounts
                                        }
                                        com.cayxu.app.automation.instagram.XsmmInstagramManager.startAccounts(context, accountsToRun)
                                        android.widget.Toast.makeText(context, "Báº¯t Ä‘áº§u cháº¡y ${accountsToRun.size} tÃ i khoáº£n Instagram", android.widget.Toast.LENGTH_SHORT).show()
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
                                            contentDescription = "Cháº¡y táº¥t cáº£",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        } else if (!isIg && facebookAccounts.isNotEmpty()) {
                            // NÃºt Cháº¡y táº¥t cáº£ / Dá»«ng táº¥t cáº£ (DÃ nh cho Facebook)
                            val isAnyFbRunning = com.cayxu.app.automation.facebook.XsmmFacebookManager.isAnyRunning()
                            IconButton(
                                onClick = {
                                    if (isAnyFbRunning) {
                                        com.cayxu.app.automation.facebook.XsmmFacebookManager.stopAll()
                                        android.widget.Toast.makeText(context, "ÄÃ£ dá»«ng táº¥t cáº£ tÃ¡c vá»¥ Facebook", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val accountsToRun = if (selectedForRunUids.isNotEmpty()) {
                                            selectedForRunUids.toList()
                                        } else {
                                            facebookAccounts.map { it.uid }
                                        }
                                        if (accountsToRun.isEmpty()) {
                                            android.widget.Toast.makeText(context, "Vui lÃ²ng chá»n Ã­t nháº¥t 1 tÃ i khoáº£n Ä‘á»ƒ cháº¡y", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            com.cayxu.app.automation.facebook.XsmmFacebookManager.startAccounts(context, accountsToRun)
                                            android.widget.Toast.makeText(context, "Báº¯t Ä‘áº§u cháº¡y ${accountsToRun.size} tÃ i khoáº£n Facebook", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isAnyFbRunning) DangerRed else platformColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAnyFbRunning) {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Cháº¡y táº¥t cáº£",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // NÃºt ThÃªm acc (+)
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
                                    contentDescription = "ThÃªm tÃ i khoáº£n",
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

            // Avatar TikTok hiá»ƒn thá»‹ áº£nh HD thá»±c táº¿
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

            // Ná»™i dung thÃ´ng tin tÃ i khoáº£n TikTok
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

                    // NÃºt tráº¡ng thÃ¡i Live / Die
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
                    text = "@${account.handle.ifBlank { "chÆ°a_rÃµ" }}",
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

                // DÃ²ng thá»‘ng kÃª: Followers, Tim
                val statsList = buildList {
                    if (account.followerCount > 0) add("${formatTikTokCount(account.followerCount)} followers")
                    if (account.heartCount > 0) add("${formatTikTokCount(account.heartCount)} tim")
                }
                if (statsList.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statsList.joinToString(" â€¢ "),
                        color = Color(0xFFE1306C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // DÃ²ng ngÃ y táº¡o riÃªng biá»‡t
                if (account.createDateFormatted.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "Táº¡o: ${account.createDateFormatted}",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // NÃºt lÃ m má»›i (Reload) thÃ´ng tin profile TikTok
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
                        contentDescription = "LÃ m má»›i thÃ´ng tin TikTok",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // NÃºt ThÃªm / ÄÃ£ thÃªm
            when {
                isAdding -> {
                    CircularProgressIndicator(color = TikTokBrandBlack, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
                isAdded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = TikTokBrandBlack, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ÄÃ£ thÃªm", color = TikTokBrandBlack, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                else -> {
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = TikTokBrandBlack),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("ThÃªm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            // TiÃªu Ä‘á» BottomSheet
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
                        "XÃ¡c nháº­n xÃ³a tÃ i khoáº£n",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "XÃ³a ${accountList.size} tÃ i khoáº£n $platformName Ä‘Ã£ chá»n",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Ná»™i dung cáº£nh bÃ¡o
            Text(
                "Báº¡n cÃ³ cháº¯c cháº¯n muá»‘n xÃ³a ${accountList.size} tÃ i khoáº£n nÃ y khá»i thiáº¿t bá»‹? Má»i thÃ´ng tin tÃ i khoáº£n vÃ  cookie Ä‘Ã£ lÆ°u sáº½ bá»‹ xÃ³a vÄ©nh viá»…n.",
                fontSize = 13.5.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(14.dp))

            // Danh sÃ¡ch cÃ¡c tÃ i khoáº£n bá»‹ xÃ³a
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

            // 2 NÃºt: Há»§y / XÃ³a ngay
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
                    Text("Há»§y", color = TextSecondary, fontWeight = FontWeight.Medium)
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
                    Text("XÃ³a ngay", fontWeight = FontWeight.Bold, color = Color.White)
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
            // Header: Dáº¥u cháº¥m than cáº£nh bÃ¡o
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
                        "Chi tiáº¿t lá»—i",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "TÃ i khoáº£n: $accountName",
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Chi tiáº¿t nguyÃªn nhÃ¢n pháº£n há»“i:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // Ná»™i dung chi tiáº¿t lá»—i
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
                Text("ÄÃ£ hiá»ƒu & ÄÃ³ng", fontWeight = FontWeight.Bold, color = Color.White)
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
    XsmmTikTokTypeOption(TikTokAppVariant.STANDARD, "TikTok", "PhiÃªn báº£n tiÃªu chuáº©n"),
    XsmmTikTokTypeOption(TikTokAppVariant.LITE, "TikTok Lite", "PhiÃªn báº£n rÃºt gá»n, nháº¹ hÆ¡n"),
    XsmmTikTokTypeOption(TikTokAppVariant.STUDIO, "TikTok Studio", "DÃ nh cho nhÃ  sÃ¡ng táº¡o ná»™i dung")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XsmmTikTokCheckSheet(
    initialVariant: TikTokAppVariant = TikTokAppVariant.STANDARD,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedVariant by remember { mutableStateOf(initialVariant) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Kiá»ƒm tra tÃ i khoáº£n TikTok", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "MÃ n hÃ¬nh ná»•i & Trá»£ nÄƒng cáº§n Ä‘Æ°á»£c cáº¥p quyá»n Ä‘á»ƒ tá»± Ä‘á»™ng má»Ÿ TikTok vÃ  kiá»ƒm tra tráº¡ng thÃ¡i nick.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            // 1. Quyá»n hiá»ƒn thá»‹ trÃªn á»©ng dá»¥ng khÃ¡c
            XsmmTikTokPermissionCard(
                title = "Hiá»ƒn thá»‹ trÃªn á»©ng dá»¥ng khÃ¡c",
                desc = "Äá»ƒ hiá»‡n mÃ n ná»•i (overlay) kiá»ƒm tra vÃ  Ä‘iá»u khiá»ƒn trÃªn TikTok",
                granted = overlayGranted,
                onClick = { com.cayxu.app.automation.tiktok.TikTokAppLauncher.openOverlayPermissionSettings(context) }
            )

            Spacer(Modifier.height(10.dp))

            // 2. Quyá»n Trá»£ nÄƒng
            XsmmTikTokPermissionCard(
                title = "Dá»‹ch vá»¥ Trá»£ nÄƒng (Accessibility)",
                desc = "Äá»ƒ tá»± Ä‘á»™ng báº¥m tab \"TÃ´i\" vÃ  kiá»ƒm tra @username TikTok",
                granted = accessibilityGranted,
                onClick = { com.cayxu.app.automation.tiktok.TikTokAppLauncher.openAccessibilitySettings(context) }
            )

            Spacer(Modifier.height(18.dp))
            Text("Chá»n á»©ng dá»¥ng cáº§n kiá»ƒm tra:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // Variant selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                xsmmTikTokOptions.forEach { opt ->
                    val isSel = selectedVariant == opt.variant
                    val isInstalled = com.cayxu.app.automation.tiktok.TikTokAppLauncher.isInstalled(context, opt.variant)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) Color(0xFF1E293B) else AppBackground)
                            .clickable { selectedVariant = opt.variant }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                opt.title,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else TextPrimary
                            )
                            Text(
                                if (isInstalled) "ÄÃ£ cÃ i" else "ChÆ°a cÃ i",
                                fontSize = 10.sp,
                                color = if (isSel) Color(0xFF94A3B8) else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // NÃºt "Kiá»ƒm tra tÃ i khoáº£n"
            Button(
                onClick = {
                    if (!com.cayxu.app.automation.tiktok.TikTokAppLauncher.isInstalled(context, selectedVariant)) {
                        val variantName = xsmmTikTokOptions.first { it.variant == selectedVariant }.title
                        android.widget.Toast.makeText(context, "ChÆ°a cÃ i Ä‘áº·t $variantName trÃªn mÃ¡y nÃ y", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!overlayGranted || !accessibilityGranted) {
                        android.widget.Toast.makeText(context, "Vui lÃ²ng cáº¥p Ä‘á»§ 2 quyá»n á»Ÿ trÃªn trÆ°á»›c khi kiá»ƒm tra", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    com.cayxu.app.automation.tiktok.TikTokCaptureBridge.startWaiting(selectedVariant)
                    context.startService(
                        android.content.Intent(context, com.cayxu.app.automation.tiktok.TikTokCaptureOverlayService::class.java)
                            .putExtra(com.cayxu.app.automation.tiktok.TikTokCaptureOverlayService.EXTRA_VARIANT, selectedVariant.name)
                    )
                    val launched = com.cayxu.app.automation.tiktok.TikTokAppLauncher.launch(context, selectedVariant, forceStopFirst = true)
                    if (!launched) {
                        val variantName = xsmmTikTokOptions.first { it.variant == selectedVariant }.title
                        android.widget.Toast.makeText(context, "KhÃ´ng má»Ÿ Ä‘Æ°á»£c $variantName", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F172A),
                    disabledContainerColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kiá»ƒm tra tÃ i khoáº£n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun XsmmTikTokPermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppBackground)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SuccessGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("ÄÃ£ cáº¥p", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Cáº¥p quyá»n", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


