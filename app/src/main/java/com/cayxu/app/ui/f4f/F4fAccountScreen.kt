package com.cayxu.app.ui.f4f

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.ui.navigation.Routes
import kotlinx.coroutines.launch

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
    var isAnyRunning by remember { mutableStateOf(false) }

    // Danh sách tài khoản remote từ F4F API
    var remoteTiktokAccounts by remember { mutableStateOf<List<F4fRemoteAccount>>(emptyList()) }
    var remoteFbAccounts by remember { mutableStateOf<List<F4fRemoteAccount>>(emptyList()) }
    var remoteIgAccounts by remember { mutableStateOf<List<F4fRemoteAccount>>(emptyList()) }

    // Danh sách tài khoản local trên máy
    val localTikTokAccounts = remember { TikTokAccountsStore.getAccounts(context) }
    val localFbAccounts = remember { FacebookAccountsStore.getAccounts(context) }
    val localIgAccounts = remember { InstagramAccountsStore.getAccounts(context) }

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

    // Đếm số lượng tài khoản theo từng nền tảng (ưu tiên số lượng từ server, nếu chưa có thì đếm local)
    val platformCounts = remember(remoteTiktokAccounts, remoteFbAccounts, remoteIgAccounts, localTikTokAccounts, localFbAccounts, localIgAccounts) {
        mapOf(
            F4fPlatform.TikTok to if (remoteTiktokAccounts.isNotEmpty()) remoteTiktokAccounts.size else localTikTokAccounts.size,
            F4fPlatform.Facebook to if (remoteFbAccounts.isNotEmpty()) remoteFbAccounts.size else localFbAccounts.size,
            F4fPlatform.Instagram to if (remoteIgAccounts.isNotEmpty()) remoteIgAccounts.size else localIgAccounts.size,
        )
    }

    val accounts = remember(selectedPlatform, remoteTiktokAccounts, remoteFbAccounts, remoteIgAccounts, localTikTokAccounts, localFbAccounts, localIgAccounts) {
        when (selectedPlatform) {
            F4fPlatform.TikTok -> {
                if (remoteTiktokAccounts.isNotEmpty()) {
                    remoteTiktokAccounts.map { acc ->
                        F4fAccount(
                            id = acc.id,
                            name = if (acc.username.startsWith("@")) acc.username else "@${acc.username}",
                            typeId = "tiktok",
                            statusLabel = if (acc.isLive) "Hoạt động" else "Chưa kích hoạt",
                            statusOk = acc.isLive,
                            avatarUrl = acc.avatarUrl,
                            handle = acc.username.removePrefix("@"),
                            isLive = acc.isLive,
                        )
                    }
                } else {
                    localTikTokAccounts.map { acc ->
                        val cleanHandle = acc.handle.trim().removePrefix("@")
                        F4fAccount(
                            id = acc.uid,
                            name = "@${cleanHandle.ifBlank { acc.uid }}",
                            typeId = "tiktok",
                            statusLabel = if (acc.isLive) "Hoạt động" else "Chưa đăng nhập",
                            statusOk = acc.isLive,
                            avatarUrl = acc.avatarUrl,
                            handle = cleanHandle,
                            followerCount = acc.followerCount,
                            heartCount = acc.heartCount,
                            createDateFormatted = acc.createDateFormatted,
                            isLive = acc.isLive,
                        )
                    }
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
                if (remoteIgAccounts.isNotEmpty()) {
                    remoteIgAccounts.map { acc ->
                        F4fAccount(
                            id = acc.id,
                            name = if (acc.username.startsWith("@")) acc.username else "@${acc.username}",
                            typeId = "instagram",
                            statusLabel = if (acc.isLive) "Hoạt động" else "Hết hạn",
                            statusOk = acc.isLive,
                            avatarUrl = acc.avatarUrl,
                            handle = "@${acc.username.removePrefix("@")}",
                            isLive = acc.isLive,
                        )
                    }
                } else {
                    localIgAccounts.map { acc ->
                        val displayName = acc.fullName.ifBlank { "@${acc.username.trim().removePrefix("@")}" }
                        F4fAccount(
                            id = acc.username,
                            name = displayName,
                            typeId = "instagram",
                            statusLabel = if (acc.isLive) "Hoạt động" else "Hết hạn",
                            statusOk = acc.isLive,
                            avatarUrl = acc.avatar,
                            handle = "@${acc.username.trim().removePrefix("@")}",
                            followerCount = acc.followersCount.toLong(),
                            isLive = acc.isLive,
                        )
                    }
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
                Toast.makeText(context, "Thêm tài khoản cho ${selectedPlatform.label}", Toast.LENGTH_SHORT).show()
            },
            onOpenConfig = {
                navController.navigate(Routes.F4F_RUN_CONFIG)
            },
            onRun = {
                if (isAnyRunning) {
                    isAnyRunning = false
                    Toast.makeText(context, "Đã dừng nhiệm vụ Follow4Follow", Toast.LENGTH_SHORT).show()
                } else {
                    if (selectedAccountIds.isEmpty() && accounts.isNotEmpty()) {
                        selectedAccountIds = accounts.map { it.id }.toSet()
                    }
                    if (selectedAccountIds.isEmpty()) {
                        Toast.makeText(context, "Vui lòng chọn tài khoản để chạy", Toast.LENGTH_SHORT).show()
                    } else {
                        isAnyRunning = true
                        Toast.makeText(context, "Đang chạy nhiệm vụ Follow4Follow...", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReloadAccount = { acc ->
                Toast.makeText(context, "Đang kiểm tra tài khoản ${acc.name}...", Toast.LENGTH_SHORT).show()
            },
            onDeleteSelected = {
                selectedAccountIds = emptySet()
            }
        )
    }
}
