package com.cayxu.app.ui.f4f

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * Màn hình tài khoản Follow4Follow (F4F) độc lập:
 * - Hiển thị thông tin đăng nhập, số xu F4F
 * - 3 Tab: TikTok, Facebook, Instagram
 * - Nút Cấu hình, Tất cả, Chạy / Dừng, Thêm tài khoản
 */
@Composable
fun F4fAccountScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(F4fAccountStore.getUsername(context).ifBlank { "f4f_user" }) }
    var coinBalance by remember { mutableStateOf(F4fAccountStore.getPoints(context)) }
    var selectedPlatform by remember { mutableStateOf(F4fPlatform.TikTok) }
    var selectedTypeId by remember { mutableStateOf<String?>("tiktok") }
    var selectedAccountIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshingBalance by remember { mutableStateOf(false) }
    var isAnyRunning by remember { mutableStateOf(false) }

    // Đọc danh sách tài khoản độc lập từ store nội bộ
    val tiktokAccounts = remember { TikTokAccountsStore.getAccounts(context) }
    val fbAccounts = remember { FacebookAccountsStore.getAccounts(context) }
    val igAccounts = remember { InstagramAccountsStore.getAccounts(context) }

    val platformCounts = remember(tiktokAccounts, fbAccounts, igAccounts) {
        mapOf(
            F4fPlatform.TikTok to tiktokAccounts.size,
            F4fPlatform.Facebook to fbAccounts.size,
            F4fPlatform.Instagram to igAccounts.size,
        )
    }

    val accounts = remember(selectedPlatform, tiktokAccounts, fbAccounts, igAccounts) {
        when (selectedPlatform) {
            F4fPlatform.TikTok -> tiktokAccounts.map { acc ->
                F4fAccount(
                    id = acc.id,
                    name = acc.username,
                    typeId = "tiktok",
                    statusLabel = if (acc.isLoggedIn) "Hoạt động" else "Chưa đăng nhập",
                    statusOk = acc.isLoggedIn,
                    avatarUrl = acc.avatarUrl,
                    handle = "@${acc.username}",
                    followerCount = 0L,
                    heartCount = 0L,
                    isLive = acc.isLoggedIn,
                )
            }
            F4fPlatform.Facebook -> fbAccounts.map { acc ->
                F4fAccount(
                    id = acc.id,
                    name = acc.name.ifBlank { acc.uid },
                    typeId = "facebook",
                    statusLabel = if (acc.isLive) "Hoạt động" else "Die",
                    statusOk = acc.isLive,
                    avatarUrl = acc.avatarUrl,
                    handle = acc.uid,
                    isLive = acc.isLive,
                )
            }
            F4fPlatform.Instagram -> igAccounts.map { acc ->
                F4fAccount(
                    id = acc.id,
                    name = acc.username,
                    typeId = "instagram",
                    statusLabel = if (acc.isLive) "Hoạt động" else "Die",
                    statusOk = acc.isLive,
                    avatarUrl = acc.avatarUrl,
                    handle = "@${acc.username}",
                    isLive = acc.isLive,
                )
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
                    kotlinx.coroutines.delay(800)
                    coinBalance = F4fAccountStore.getPoints(context)
                    refreshingBalance = false
                    Toast.makeText(context, "Đã làm mới số dư F4F", Toast.LENGTH_SHORT).show()
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
