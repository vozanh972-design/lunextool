package com.cayxu.app.ui.f4f

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.InstagramAccount
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.instagram.InstagramApiClient
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.overlay.xsmm.startXsmmVerifyTikTokAccount
import com.cayxu.app.automation.instagram.IgTaToolClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình tài khoản Follow4Follow (F4F) độc lập kết nối API thật:
 * - Gọi /user/auth/me để lấy số dư xu, username thực tế từ F4F
 * - Gọi /user/account/list để đồng bộ tài khoản MXH thật từ server
 * - Hỗ trợ làm mới số dư, chuyển tab, đăng xuất
 */
@Composable
fun F4fAccountScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { F4fAccountStore.getToken(context).orEmpty() }

    var username by remember { mutableStateOf(F4fAccountStore.getUsername(context).ifBlank { "f4f_user" }) }
    var coinBalance by remember { mutableStateOf(F4fAccountStore.getPoints(context)) }
    var selectedPlatform by remember { mutableStateOf(F4fPlatform.TikTok) }
    var selectedTypeId by remember { mutableStateOf<String?>("tiktok") }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshingBalance by remember { mutableStateOf(false) }
    val isOverlayRunning by F4fJobRunnerOverlayService.isRunning.collectAsState()
    val isRunnerRunning by F4fTikTokTaskRunner.isRunning.collectAsState()
    val igRunningAccounts = F4fInstagramManager.runningAccounts
    val igStatusMap = F4fInstagramManager.statusMap
    val igSuccessCountMap = F4fInstagramManager.successCountMap
    val igErrorCountMap = F4fInstagramManager.errorCountMap
    val igLastErrorDetail = F4fInstagramManager.lastErrorDetail
    var isAnyRunningState by remember { mutableStateOf(false) }
    val isAnyRunning = isAnyRunningState || isOverlayRunning || isRunnerRunning || igRunningAccounts.isNotEmpty() || F4fInstagramManager.isAnyRunning()

    // Quản lý kiểm tra & liên kết tài khoản TikTok
    var showTikTokCheckSheet by remember { mutableStateOf(false) }
    var selectedVariant by remember { mutableStateOf(TikTokAppVariant.STANDARD) }
    var reloadingTikTokUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var linkingTikTokUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var locallyLinkedHandles by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Quản lý tài khoản Instagram
    var reloadingIgUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showInstagramAddSheet by remember { mutableStateOf(false) }
    var showInstagramConfigSheet by remember { mutableStateOf(false) }
    var selectedErrorDetailAccount by remember { mutableStateOf<F4fAccount?>(null) }

    // Danh sách tài khoản remote từ F4F API
    var remoteTiktokAccounts by remember { mutableStateOf<List<F4fRemoteAccount>>(emptyList()) }
    var remoteFbAccounts by remember { mutableStateOf<List<F4fRemoteAccount>>(emptyList()) }
    var remoteIgAccounts by remember { mutableStateOf<List<F4fRemoteAccount>>(emptyList()) }

    // Danh sách tài khoản local trên máy
    var localTikTokAccounts by remember { mutableStateOf(TikTokAccountsStore.getAccounts(context)) }
    val localFbAccounts = remember { FacebookAccountsStore.getAccounts(context) }
    var localIgAccounts by remember { mutableStateOf(InstagramAccountsStore.getAccounts(context)) }

    fun reloadTikTok() {
        localTikTokAccounts = TikTokAccountsStore.getAccounts(context)
    }

    fun reloadIg() {
        localIgAccounts = InstagramAccountsStore.getAccounts(context)
    }

    // Hàm gọi API đồng bộ thông tin tài khoản & số dư từ server Follow4Follow
    fun refreshProfileAndAccounts() {
        if (token.isBlank()) return
        scope.launch {
            refreshingBalance = true
            when (val userResult = F4fApiClient.fetchMe(token)) {
                is F4fApiResult.Success -> {
                    username = userResult.data.username
                    coinBalance = userResult.data.balancePoints
                    F4fAccountStore.updatePoints(context, coinBalance)
                    F4fSession.points.value = coinBalance
                    F4fSession.username.value = username
                }
                is F4fApiResult.Error -> {
                    // Giữ giá trị cache nếu lỗi mạng
                }
            }

            // Tải danh sách tài khoản theo từng nền tảng
            val tiktokRes = F4fApiClient.fetchAccountList(token, "tiktok")
            if (tiktokRes is F4fApiResult.Success) remoteTiktokAccounts = tiktokRes.data

            val fbRes = F4fApiClient.fetchAccountList(token, "facebook")
            if (fbRes is F4fApiResult.Success) remoteFbAccounts = fbRes.data

            val igRes = F4fApiClient.fetchAccountList(token, "instagram")
            if (igRes is F4fApiResult.Success) remoteIgAccounts = igRes.data

            refreshingBalance = false
        }
    }

    LaunchedEffect(Unit) {
        refreshProfileAndAccounts()
    }

    // Đếm số lượng tài khoản theo từng nền tảng
    val platformCounts = remember(remoteTiktokAccounts, remoteFbAccounts, remoteIgAccounts, localTikTokAccounts, localFbAccounts, localIgAccounts) {
        mapOf(
            F4fPlatform.TikTok to localTikTokAccounts.size,
            F4fPlatform.Facebook to if (remoteFbAccounts.isNotEmpty()) remoteFbAccounts.size else localFbAccounts.size,
            F4fPlatform.Instagram to if (remoteIgAccounts.isNotEmpty()) remoteIgAccounts.size else localIgAccounts.size,
        )
    }

    val accounts = remember(
        selectedPlatform,
        remoteTiktokAccounts,
        remoteFbAccounts,
        remoteIgAccounts,
        localTikTokAccounts,
        localFbAccounts,
        localIgAccounts,
        reloadingTikTokUids,
        linkingTikTokUids,
        locallyLinkedHandles,
        igRunningAccounts.toList(),
        igStatusMap.toMap(),
        igSuccessCountMap.toMap(),
        igErrorCountMap.toMap(),
        igLastErrorDetail.toMap(),
        reloadingIgUsernames,
    ) {
        when (selectedPlatform) {
            F4fPlatform.TikTok -> {
                val remoteHandles = remoteTiktokAccounts.map { it.username.trim().removePrefix("@").lowercase() }.toSet()
                localTikTokAccounts.map { acc ->
                    val cleanHandle = acc.handle.trim().removePrefix("@")
                    val typeId = when (acc.variant) {
                        TikTokAppVariant.LITE -> "tiktok_lite"
                        TikTokAppVariant.STUDIO -> "tiktok_studio"
                        else -> "tiktok"
                    }
                    val isLinked = cleanHandle.lowercase() in remoteHandles || cleanHandle.lowercase() in locallyLinkedHandles
                    val isReloading = acc.uid in reloadingTikTokUids
                    val isLinking = acc.uid in linkingTikTokUids
                    F4fAccount(
                        id = acc.uid,
                        name = "@${cleanHandle.ifBlank { acc.uid }}",
                        typeId = typeId,
                        statusLabel = if (!isLinked) "Chưa liên kết" else if (acc.isLive) "Hoạt động" else "Chưa đăng nhập",
                        statusOk = isLinked && acc.isLive,
                        avatarUrl = acc.avatarUrl,
                        handle = cleanHandle,
                        followerCount = acc.followerCount,
                        heartCount = acc.heartCount,
                        createDateFormatted = acc.createDateFormatted,
                        isLive = acc.isLive,
                        isLinked = isLinked,
                        isReloading = isReloading,
                        isLinking = isLinking,
                    )
                }
            }
            F4fPlatform.Facebook -> {
                if (remoteFbAccounts.isNotEmpty()) {
                    remoteFbAccounts.map { acc ->
                        F4fAccount(
                            id = acc.id,
                            name = acc.username,
                            typeId = "facebook",
                            statusLabel = if (acc.isLive) "Hoạt động" else "Hết hạn",
                            statusOk = acc.isLive,
                            avatarUrl = acc.avatarUrl,
                            handle = acc.id,
                            isLive = acc.isLive,
                        )
                    }
                } else {
                    localFbAccounts.map { acc ->
                        F4fAccount(
                            id = acc.uid,
                            name = acc.name.ifBlank { acc.uid },
                            typeId = "facebook",
                            statusLabel = if (acc.isLive) "Hoạt động" else "Hết hạn",
                            statusOk = acc.isLive,
                            avatarUrl = acc.avatar,
                            handle = acc.uid,
                            isLive = acc.isLive,
                        )
                    }
                }
            }
            F4fPlatform.Instagram -> {
                val remoteHandles = remoteIgAccounts.map { it.username.trim().removePrefix("@").lowercase() }.toSet()
                localIgAccounts.map { acc ->
                    val cleanUser = acc.username.trim().lowercase().removePrefix("@")
                    val isLinked = cleanUser in remoteHandles || cleanUser in locallyLinkedHandles
                    val isRunning = cleanUser in igRunningAccounts || F4fInstagramManager.isRunning(cleanUser)
                    val runStatus = igStatusMap[cleanUser].orEmpty()
                    val successCount = igSuccessCountMap[cleanUser] ?: 0
                    val errorCount = igErrorCountMap[cleanUser] ?: 0
                    val errorDetail = igLastErrorDetail[cleanUser].orEmpty()
                    val displayName = acc.fullName.ifBlank { "@$cleanUser" }
                    val isReloading = cleanUser in reloadingIgUsernames
                    F4fAccount(
                        id = acc.username,
                        name = displayName,
                        typeId = "instagram",
                        statusLabel = if (!isLinked) "Chưa liên kết" else if (acc.isLive) "Hoạt động" else "Die / Hết hạn",
                        statusOk = isLinked && acc.isLive,
                        avatarUrl = acc.avatar,
                        handle = "@$cleanUser",
                        followerCount = acc.followersCount.toLong(),
                        isLive = acc.isLive,
                        isLinked = isLinked,
                        isRunning = isRunning,
                        isReloading = isReloading,
                        runStatus = runStatus,
                        successCount = successCount,
                        errorCount = errorCount,
                        errorDetail = errorDetail,
                    )
                }
            }
        }
    }

    val state = F4fAccountsUiState(
        username = username,
        coinBalance = coinBalance,
        platformCounts = platformCounts,
        selectedPlatform = selectedPlatform,
        selectedTypeId = selectedTypeId,
        accounts = accounts,
        selectedAccountIds = selectedAccountIds,
        refreshingBalance = refreshingBalance,
        isAnyRunning = isAnyRunning,
    )

    Box(Modifier.fillMaxSize()) {
        F4fAccountsScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onRefreshBalance = {
                scope.launch {
                    refreshingBalance = true
                    when (val userResult = F4fApiClient.fetchMe(token)) {
                        is F4fApiResult.Success -> {
                            username = userResult.data.username
                            coinBalance = userResult.data.balancePoints
                            F4fAccountStore.updatePoints(context, coinBalance)
                            F4fSession.points.value = coinBalance
                            F4fSession.username.value = username
                            Toast.makeText(context, "Đã cập nhật: ${userResult.data.balanceText}", Toast.LENGTH_SHORT).show()
                        }
                        is F4fApiResult.Error -> {
                            Toast.makeText(context, userResult.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshingBalance = false
                }
            },
            onLogout = {
                F4fSession.logout(context)
                navController.navigate(Routes.F4F_LOGIN) {
                    popUpTo(Routes.F4F_ACCOUNT) { inclusive = true }
                }
            },
            onSelectPlatform = { p ->
                selectedPlatform = p
                selectedTypeId = when (p) {
                    F4fPlatform.TikTok -> "tiktok"
                    F4fPlatform.Facebook -> "facebook"
                    F4fPlatform.Instagram -> "instagram"
                }
                selectedAccountIds = emptySet()
            },
            onSelectType = { typeId ->
                selectedTypeId = typeId
                selectedVariant = when (typeId) {
                    "tiktok_lite" -> TikTokAppVariant.LITE
                    "tiktok_studio" -> TikTokAppVariant.STUDIO
                    else -> TikTokAppVariant.STANDARD
                }
            },
            onToggleSelectAll = {
                val currentVisible = state.accounts.filter { selectedTypeId == null || it.typeId == selectedTypeId }
                selectedAccountIds = if (currentVisible.isNotEmpty() && currentVisible.all { it.id in selectedAccountIds }) {
                    emptySet()
                } else {
                    currentVisible.map { it.id }.toSet()
                }
            },
            onToggleAccount = { acc ->
                selectedAccountIds = if (acc.id in selectedAccountIds) {
                    selectedAccountIds - acc.id
                } else {
                    selectedAccountIds + acc.id
                }
            },
            onAddAccount = {
                when (selectedPlatform) {
                    F4fPlatform.TikTok -> {
                        showTikTokCheckSheet = true
                    }
                    F4fPlatform.Facebook -> {
                        Toast.makeText(context, "Thêm tài khoản Facebook đang phát triển", Toast.LENGTH_SHORT).show()
                    }
                    F4fPlatform.Instagram -> {
                        showInstagramAddSheet = true
                    }
                }
            },
            onOpenConfig = {
                if (selectedPlatform == F4fPlatform.Instagram) {
                    showInstagramConfigSheet = true
                } else {
                    navController.navigate(Routes.F4F_RUN_CONFIG)
                }
            },
            onRun = {
                if (selectedPlatform == F4fPlatform.TikTok) {
                    if (isAnyRunning || F4fJobRunnerOverlayService.isRunning.value || F4fTikTokTaskRunner.isRunning()) {
                        isAnyRunningState = false
                        stopF4fJobRunnerOverlay(context)
                        F4fTikTokTaskRunner.stop()
                        Toast.makeText(context, "Đã dừng nhiệm vụ Follow4Follow", Toast.LENGTH_SHORT).show()
                        refreshProfileAndAccounts()
                    } else {
                        val accountsToRun = if (selectedAccountIds.isNotEmpty()) {
                            accounts.filter { it.id in selectedAccountIds }
                        } else {
                            accounts.filter { it.isLinked }
                        }

                        if (accountsToRun.isEmpty()) {
                            Toast.makeText(context, "Vui lòng chọn tài khoản TikTok đã liên kết để chạy", Toast.LENGTH_SHORT).show()
                            return@F4fAccountsScreen
                        }

                        if (selectedAccountIds.isEmpty()) {
                            selectedAccountIds = accountsToRun.map { it.id }.toSet()
                        }

                        val handles = accountsToRun.map { it.handle.ifBlank { it.name.removePrefix("@") } }
                        isAnyRunningState = true
                        Toast.makeText(context, "Mở màn nổi chạy ${handles.size} nick TikTok Follow4Follow...", Toast.LENGTH_SHORT).show()
                        startF4fJobRunnerOverlay(
                            context = context,
                            accountHandles = handles,
                            variant = selectedVariant
                        )
                    }
                } else if (selectedPlatform == F4fPlatform.Instagram) {
                    if (F4fInstagramManager.isAnyRunning()) {
                        F4fInstagramManager.stopAll()
                        Toast.makeText(context, "Đã dừng tất cả tác vụ Follow4Follow Instagram", Toast.LENGTH_SHORT).show()
                    } else {
                        val accountsToRun = if (selectedAccountIds.isNotEmpty()) {
                            accounts.filter { it.id in selectedAccountIds }
                        } else {
                            accounts
                        }

                        if (accountsToRun.isEmpty()) {
                            Toast.makeText(context, "Vui lòng thêm hoặc chọn tài khoản Instagram để chạy", Toast.LENGTH_SHORT).show()
                            return@F4fAccountsScreen
                        }

                        if (selectedAccountIds.isEmpty()) {
                            selectedAccountIds = accountsToRun.map { it.id }.toSet()
                        }

                        accountsToRun.forEach { acc ->
                            val clean = acc.handle.removePrefix("@").ifBlank { acc.name.removePrefix("@") }.trim().lowercase()
                            F4fInstagramManager.start(context, clean)
                        }
                        Toast.makeText(context, "Bắt đầu chạy ${accountsToRun.size} tài khoản Instagram...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Nền tảng ${selectedPlatform.label} đang được phát triển", Toast.LENGTH_SHORT).show()
                }
            },
            onReloadAccount = { acc ->
                when (selectedPlatform) {
                    F4fPlatform.TikTok -> {
                        val variant = when (acc.typeId) {
                            "tiktok_lite" -> TikTokAppVariant.LITE
                            "tiktok_studio" -> TikTokAppVariant.STUDIO
                            else -> TikTokAppVariant.STANDARD
                        }
                        reloadingTikTokUids = reloadingTikTokUids + acc.id
                        startXsmmVerifyTikTokAccount(context, variant)
                        scope.launch {
                            delay(4000)
                            reloadTikTok()
                            reloadingTikTokUids = reloadingTikTokUids - acc.id
                        }
                    }
                    F4fPlatform.Instagram -> {
                        val clean = acc.handle.removePrefix("@").ifBlank { acc.name.removePrefix("@") }
                        reloadingIgUsernames = reloadingIgUsernames + clean
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val igAcc = InstagramAccountsStore.getAccount(context, clean)
                                if (igAcc != null && igAcc.cookie.isNotBlank()) {
                                    val client = IgTaToolClient.buildClient(igAcc.proxy.ifBlank { null })
                                    val check = IgTaToolClient.checkCookieIg(client, igAcc.cookie)
                                    InstagramAccountsStore.updateAccount(context, igAcc.copy(isLive = check.isLive))
                                }
                            }
                            reloadingIgUsernames = reloadingIgUsernames - clean
                            reloadIg()
                        }
                    }
                    else -> {
                        Toast.makeText(context, "Đang kiểm tra tài khoản ${acc.name}...", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onLinkAccount = { acc ->
                val cleanHandle = acc.handle.trim().removePrefix("@")
                if (cleanHandle.isBlank()) {
                    Toast.makeText(context, "Handle tài khoản không hợp lệ", Toast.LENGTH_SHORT).show()
                    return@F4fAccountsScreen
                }
                if (token.isBlank()) {
                    Toast.makeText(context, "Vui lòng đăng nhập lại Follow4Follow", Toast.LENGTH_SHORT).show()
                    return@F4fAccountsScreen
                }

                linkingTikTokUids = linkingTikTokUids + acc.id
                scope.launch {
                    val type = when (selectedPlatform) {
                        F4fPlatform.TikTok -> "tiktok"
                        F4fPlatform.Facebook -> "facebook"
                        F4fPlatform.Instagram -> "instagram"
                    }
                    when (val res = F4fApiClient.addAccount(token, cleanHandle, type)) {
                        is F4fApiResult.Success -> {
                            locallyLinkedHandles = locallyLinkedHandles + cleanHandle.lowercase()
                            Toast.makeText(context, "Đã liên kết @$cleanHandle thành công!", Toast.LENGTH_SHORT).show()
                            refreshProfileAndAccounts()
                        }
                        is F4fApiResult.Error -> {
                            val msg = res.message
                            if (msg.contains("đã có", ignoreCase = true) ||
                                msg.contains("tồn tại", ignoreCase = true) ||
                                msg.contains("already", ignoreCase = true) ||
                                msg.contains("exist", ignoreCase = true) ||
                                msg.contains("tài khoản của bạn", ignoreCase = true)
                            ) {
                                locallyLinkedHandles = locallyLinkedHandles + cleanHandle.lowercase()
                                Toast.makeText(context, "Tài khoản @$cleanHandle đã được liên kết với Follow4Follow!", Toast.LENGTH_SHORT).show()
                                refreshProfileAndAccounts()
                            } else {
                                Toast.makeText(context, "Lỗi liên kết: $msg", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    linkingTikTokUids = linkingTikTokUids - acc.id
                }
            },
            onTogglePlayAccount = { acc ->
                when (selectedPlatform) {
                    F4fPlatform.TikTok -> {
                        Toast.makeText(context, "Chạy tài khoản TikTok: @${acc.handle.ifBlank { acc.name }}", Toast.LENGTH_SHORT).show()
                    }
                    F4fPlatform.Facebook -> {
                        Toast.makeText(context, "Chạy tài khoản Facebook: ${acc.name}", Toast.LENGTH_SHORT).show()
                    }
                    F4fPlatform.Instagram -> {
                        val clean = acc.handle.removePrefix("@").ifBlank { acc.name.removePrefix("@") }.trim().lowercase()
                        if (F4fInstagramManager.isRunning(clean)) {
                            F4fInstagramManager.stop(clean)
                            Toast.makeText(context, "Đã dừng @$clean", Toast.LENGTH_SHORT).show()
                        } else {
                            F4fInstagramManager.start(context, clean)
                            Toast.makeText(context, "Bắt đầu chạy @$clean...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onOpenInfoAccount = { acc ->
                selectedErrorDetailAccount = acc
            },
            onDeleteSelected = {
                selectedAccountIds = emptySet()
            }
        )

        if (showTikTokCheckSheet) {
            F4fTikTokCheckSheet(
                initialVariant = selectedVariant,
                onDismiss = {
                    showTikTokCheckSheet = false
                    reloadTikTok()
                    refreshProfileAndAccounts()
                }
            )
        }

        if (showInstagramAddSheet) {
            F4fIgAddSheet(
                onDismiss = { showInstagramAddSheet = false },
                onSubmit = { data ->
                    val rawInput = data.trim()
                    if (rawInput.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập cookie tài khoản", Toast.LENGTH_SHORT).show()
                        return@F4fIgAddSheet
                    }

                    val lines = rawInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val hasValid = lines.any { line ->
                        val c = if (line.contains("|")) line.substring(0, line.lastIndexOf("|")).trim() else line
                        c.contains("sessionid") && c.contains("ds_user_id") && c.contains("csrftoken")
                    }

                    if (!hasValid) {
                        Toast.makeText(context, "Cookie thiếu các trường bắt buộc (cần có sessionid, ds_user_id, csrftoken)", Toast.LENGTH_LONG).show()
                        return@F4fIgAddSheet
                    }

                    Toast.makeText(context, "Đang kiểm tra tài khoản Instagram...", Toast.LENGTH_SHORT).show()
                    showInstagramAddSheet = false

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
                                        addedAccounts.add(info.username)
                                    }
                                }
                            }
                        }

                        if (addedAccounts.isNotEmpty()) {
                            val msg = if (addedAccounts.size == 1) "Thêm @${addedAccounts.first()} thành công!" else "Thêm thành công ${addedAccounts.size} tài khoản Instagram!"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            reloadIg()
                            refreshProfileAndAccounts()
                        } else {
                            Toast.makeText(context, "Không có tài khoản nào Live hoặc Cookie hết hạn", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (showInstagramConfigSheet) {
            var igConfigState by remember { mutableStateOf(F4fRunConfigInstagramUiState()) }
            F4fRunConfigInstagramScreen(
                state = igConfigState,
                onBack = { showInstagramConfigSheet = false },
                onModeChange = { igConfigState = igConfigState.copy(mode = it) },
                onGetTaskWaitChange = { igConfigState = igConfigState.copy(getTaskWaitSeconds = it) },
                onWorkSecondsChange = { igConfigState = igConfigState.copy(workSeconds = it) },
                onTaskCountChange = { igConfigState = igConfigState.copy(taskCount = it) },
                onStopAfterEmptyChange = { igConfigState = igConfigState.copy(stopAfterEmptyTimes = it) },
                onErrorSwapChange = { igConfigState = igConfigState.copy(errorSwapAccountCount = it) },
                onStopAfterDoneChange = { igConfigState = igConfigState.copy(stopAfterDoneTimes = it) },
                onBrowseBeforeTaskChange = { igConfigState = igConfigState.copy(browseBeforeTask = it) },
                onBackHomeThenBrowseChange = { igConfigState = igConfigState.copy(backHomeThenBrowse = it) },
                onSave = {
                    showInstagramConfigSheet = false
                    Toast.makeText(context, "Đã lưu cấu hình Follow4Follow Instagram", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (selectedErrorDetailAccount != null) {
            val acc = selectedErrorDetailAccount!!
            val errorMsg = acc.errorDetail.ifBlank { acc.runStatus.ifBlank { "Không có nội dung lỗi chi tiết." } }
            F4fErrorDetailBottomSheet(
                accountName = acc.name,
                errorMessage = errorMsg,
                onDismiss = { selectedErrorDetailAccount = null }
            )
        }
    }
}
