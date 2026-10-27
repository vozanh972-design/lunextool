package com.cayxu.app.ui.screens.golike

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cayxu.app.automation.instagram.IgTaToolClient
import com.cayxu.app.automation.instagram.XsmmInstagramTaskRunner
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.TikTokCaptureBridge
import com.cayxu.app.automation.tiktok.TikTokCaptureState
import com.cayxu.app.automation.tiktok.XsmmTaskActionResult
import com.cayxu.app.automation.tiktok.XsmmTaskAutomationBridge
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.overlay.xsmm.XsmmJobRunnerOverlayService
import com.cayxu.app.ui.overlay.xsmm.XsmmJobStatusBridge
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private val GolikeBrandOrange = Color(0xFFF59E0B)
private val GolikeLightOrange = Color(0xFFFEF3C7)
private val TikTokBrandBlack = Color(0xFF0F172A)

/**
 * Trình quản lý tác vụ chạy nền độc lập của Golike
 */
object GolikeRunningManager {
    val runningAccounts = mutableStateMapOf<String, Boolean>()
    val statusMap = mutableStateMapOf<String, String>()
    val successCountMap = mutableStateMapOf<String, Int>()
    val errorCountMap = mutableStateMapOf<String, Int>()
    val lastErrorDetailMap = mutableStateMapOf<String, String>()

    fun isRunning(accountId: String): Boolean = runningAccounts[accountId] == true

    fun isAnyRunning(platformAccounts: List<GolikeAccount>): Boolean =
        platformAccounts.any { isRunning(it.id) }

    fun stop(accountId: String) {
        runningAccounts[accountId] = false
        statusMap[accountId] = "Đã tạm dừng"
    }

    fun stopAll(platformAccounts: List<GolikeAccount>) {
        platformAccounts.forEach { stop(it.id) }
    }

    fun clearAll() {
        runningAccounts.clear()
        statusMap.clear()
        successCountMap.clear()
        errorCountMap.clear()
        lastErrorDetailMap.clear()
    }
}

/**
 * Kiểm tra trạng thái liên kết Golike ngầm cho các tài khoản TikTok hiện có trong máy.
 * - Nếu ĐÃ LIÊN KẾT (trùng username trên Golike): Cập nhật golikeAccountId và trạng thái sẵn sàng (KHÔNG cảnh báo).
 * - Nếu CHƯA LIÊN KẾT: Đánh dấu isGolikeLinked = false để hiển thị tag "Cần liên kết Golike".
 */
suspend fun checkGolikeLinkedStatusForTikTokAccounts(
    context: Context,
    client: GolikeApiClient,
    uiTikTokAccounts: androidx.compose.runtime.snapshots.SnapshotStateList<GolikeAccount>? = null
) {
    if (!GolikeAccountsStore.isLoggedIn(context)) return
    try {
        val res = client.getTikTokAccounts()
        val dataArray = res?.optJSONArray("data")
            ?: res?.optJSONObject("data")?.optJSONArray("data")

        if (dataArray == null) {
            android.util.Log.w("GolikeCheck", "Không nhận được dataArray từ Golike API: $res")
            return
        }

        val golikeNickMap = mutableMapOf<String, Pair<String, String>>()
        val serverAccounts = mutableListOf<GolikeAccount>()
        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            val rawId = item.opt("id") ?: item.opt("account_id")
            val accountId = when (rawId) {
                is Number -> rawId.toLong().toString()
                is String -> rawId.trim()
                else -> item.optString("id").ifBlank { item.optString("account_id") }
            }
            val avatar = item.optString("avatar_thumb").ifBlank { item.optString("avatar") }
            val u1 = item.optString("unique_username").trim().removePrefix("@").lowercase()
            val u2 = item.optString("unique_id").trim().removePrefix("@").lowercase()
            val u3 = item.optString("nickname").trim().removePrefix("@").lowercase()
            val u4 = item.optString("tiktok_account").trim().removePrefix("@").lowercase()
            val displayHandle = u1.ifBlank { u4.ifBlank { u2.ifBlank { u3.ifBlank { "acc_$accountId" } } } }

            listOf(u1, u2, u3, u4).filter { it.isNotBlank() }.forEach { handle ->
                golikeNickMap[handle] = Pair(accountId, avatar)
                GolikeAccountsStore.saveTikTokMapping(context, handle, accountId)
            }

            if (displayHandle.isNotBlank()) {
                serverAccounts.add(
                    GolikeAccount(
                        id = displayHandle,
                        platform = "tiktok",
                        username = displayHandle,
                        avatar = avatar,
                        isLive = true,
                        isGolikeLinked = true,
                        golikeAccountId = accountId,
                        lastStatus = "Đã liên kết Golike • Sẵn sàng"
                    )
                )
            }
        }

        withContext(Dispatchers.Main) {
            if (uiTikTokAccounts != null) {
                // 1. Cập nhật các tài khoản hiện có trong UI
                uiTikTokAccounts.forEachIndexed { index, acc ->
                    val cleanHandle = acc.username.trim().removePrefix("@").lowercase()
                    val cleanId = acc.id.trim().removePrefix("@").lowercase()
                    val mapId = GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanHandle)
                        ?: GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanId)

                    val match = golikeNickMap[cleanHandle] ?: golikeNickMap[cleanId]
                    val golikeId = match?.first ?: mapId ?: acc.golikeAccountId
                    val serverAvatar = match?.second.orEmpty()

                    val isLinked = !golikeId.isNullOrBlank() || acc.isGolikeLinked
                    if (!golikeId.isNullOrBlank()) {
                        GolikeAccountsStore.saveTikTokMapping(context, cleanHandle, golikeId)
                        if (cleanId != cleanHandle) {
                            GolikeAccountsStore.saveTikTokMapping(context, cleanId, golikeId)
                        }
                    }

                    uiTikTokAccounts[index] = acc.copy(
                        isGolikeLinked = isLinked,
                        golikeAccountId = golikeId.orEmpty(),
                        avatar = if (acc.avatar.isBlank() && serverAvatar.isNotBlank()) serverAvatar else acc.avatar,
                        lastStatus = if (isLinked) "Đã liên kết Golike • Sẵn sàng" else acc.lastStatus
                    )
                }

                // 2. Tự động thêm các tài khoản từ server Golike chưa có trong UI
                serverAccounts.forEach { serverAcc ->
                    val exists = uiTikTokAccounts.any { local ->
                        local.id.equals(serverAcc.id, ignoreCase = true) ||
                                local.username.trim().removePrefix("@").equals(serverAcc.username, ignoreCase = true) ||
                                (local.golikeAccountId.isNotBlank() && local.golikeAccountId == serverAcc.golikeAccountId)
                    }
                    if (!exists) {
                        uiTikTokAccounts.add(serverAcc)
                    }
                }
                GolikeAccountsStore.saveTikTokAccounts(context, uiTikTokAccounts.toList())
            } else {
                val currentAccounts = GolikeAccountsStore.getTikTokAccounts(context).toMutableList()
                val updatedAccounts = currentAccounts.map { acc ->
                    val cleanHandle = acc.username.trim().removePrefix("@").lowercase()
                    val cleanId = acc.id.trim().removePrefix("@").lowercase()
                    val mapId = GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanHandle)
                        ?: GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanId)

                    val match = golikeNickMap[cleanHandle] ?: golikeNickMap[cleanId]
                    val golikeId = match?.first ?: mapId ?: acc.golikeAccountId
                    val serverAvatar = match?.second.orEmpty()

                    val isLinked = !golikeId.isNullOrBlank() || acc.isGolikeLinked
                    if (!golikeId.isNullOrBlank()) {
                        GolikeAccountsStore.saveTikTokMapping(context, cleanHandle, golikeId)
                        if (cleanId != cleanHandle) {
                            GolikeAccountsStore.saveTikTokMapping(context, cleanId, golikeId)
                        }
                    }

                    acc.copy(
                        isGolikeLinked = isLinked,
                        golikeAccountId = golikeId.orEmpty(),
                        avatar = if (acc.avatar.isBlank() && serverAvatar.isNotBlank()) serverAvatar else acc.avatar,
                        lastStatus = if (isLinked) "Đã liên kết Golike • Sẵn sàng" else acc.lastStatus
                    )
                }.toMutableList()

                serverAccounts.forEach { serverAcc ->
                    val exists = updatedAccounts.any { local ->
                        local.id.equals(serverAcc.id, ignoreCase = true) ||
                                local.username.trim().removePrefix("@").equals(serverAcc.username, ignoreCase = true) ||
                                (local.golikeAccountId.isNotBlank() && local.golikeAccountId == serverAcc.golikeAccountId)
                    }
                    if (!exists) {
                        updatedAccounts.add(serverAcc)
                    }
                }
                GolikeAccountsStore.saveTikTokAccounts(context, updatedAccounts)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("GolikeCheck", "Lỗi kiểm tra ngầm: ${e.message}")
    }
}

/**
 * Đồng bộ danh sách tài khoản đã liên kết từ API Golike về máy và quét tài khoản trên máy.
 */
suspend fun syncLinkedAccountsFromApi(
    context: Context,
    client: GolikeApiClient,
    platform: String,
    uiTikTokAccounts: androidx.compose.runtime.snapshots.SnapshotStateList<GolikeAccount>? = null
) {
    try {
        if (platform.lowercase() == "tiktok") {
            checkGolikeLinkedStatusForTikTokAccounts(context, client, uiTikTokAccounts)
        } else {
            val res = when (platform.lowercase()) {
                "facebook" -> client.getFacebookAccounts()
                else -> client.getInstagramAccounts()
            }
            val dataArray = res?.optJSONArray("data") ?: return
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val accountId = item.optString("id")
                val uname = item.optString("nickname").ifBlank {
                    item.optString("unique_username").ifBlank {
                        item.optString("username").ifBlank {
                            item.optString("name", accountId)
                        }
                    }
                }
                val fbId = item.optString("fb_id")
                val cleanId = (if (fbId.isNotBlank()) fbId else if (uname.isNotBlank()) uname else accountId).removePrefix("@").trim()
                if (cleanId.isNotBlank()) {
                    val acc = GolikeAccount(
                        id = cleanId,
                        platform = platform.lowercase(),
                        username = uname,
                        avatar = item.optString("avatar"),
                        isLive = true,
                        isGolikeLinked = true,
                        golikeAccountId = accountId,
                        lastStatus = "Đã liên kết Golike • Sẵn sàng"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, acc)
                }
            }
        }
    } catch (_: Exception) {}
}

/**
 * Màn hình quản lý tài khoản & làm nhiệm vụ Golike (Nhân bản 100% giao diện từ XSMM).
 */
@Composable
fun GolikeAccountScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Khôi phục phiên Golike lúc mở màn hình
    LaunchedEffect(Unit) {
        GolikeSession.restore(context)
    }

    val username by GolikeSession.username
    val balance by GolikeSession.balance
    val isLoggedIn by GolikeSession.isLoggedIn

    var isRefreshing by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf("tiktok") }
    var selectedForRunIds by remember(selectedPlatform) { mutableStateOf<Set<String>>(emptySet()) }

    var showConfigSheet by remember { mutableStateOf(false) }
    var showWebViewLoginDialog by remember { mutableStateOf(false) }
    var showAddAccountSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }
    var selectedErrorAccount by remember { mutableStateOf<GolikeAccount?>(null) }

    // TikTok variants
    var selectedVariant by remember { mutableStateOf(TikTokAppVariant.STANDARD) }
    var reloadingTikTokUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var addingUid by remember { mutableStateOf<String?>(null) }

    // Danh sách tài khoản theo nền tảng
    val tiktokAccounts = remember { mutableStateListOf<GolikeAccount>() }
    var otherAccounts by remember(selectedPlatform, isLoggedIn) {
        mutableStateOf(if (selectedPlatform != "tiktok" && isLoggedIn) GolikeAccountsStore.getAccounts(context, selectedPlatform) else emptyList())
    }

    val displayAccounts: List<GolikeAccount> =
        if (selectedPlatform == "tiktok") tiktokAccounts else otherAccounts

    // Đếm số lượng tài khoản theo từng tab
    val tiktokCount = tiktokAccounts.size
    val facebookCount = remember(otherAccounts, selectedPlatform, isLoggedIn) {
        if (!isLoggedIn) 0 else if (selectedPlatform == "facebook") otherAccounts.size else GolikeAccountsStore.getAccounts(context, "facebook").size
    }
    val instagramCount = remember(otherAccounts, selectedPlatform, isLoggedIn) {
        if (!isLoggedIn) 0 else {
            val fromStore = InstagramAccountsStore.getAccounts(context).size
            val fromGolike = GolikeAccountsStore.getAccounts(context, "instagram").size
            maxOf(fromStore, fromGolike)
        }
    }

    // Đồng bộ danh sách tài khoản khi đổi tab hoặc sau khi đăng xuất/đăng nhập
    fun reloadAccounts() {
        if (selectedPlatform == "tiktok") {
            val list = GolikeAccountsStore.getTikTokAccounts(context).ifEmpty {
                GolikeAccountsStore.importScannedTikTokAccounts(context)
            }
            tiktokAccounts.clear()
            tiktokAccounts.addAll(list)
        } else if (selectedPlatform == "instagram") {
            // Tự động đồng bộ dùng chung toàn bộ tài khoản Instagram từ InstagramAccountsStore
            val igStoreAccounts = InstagramAccountsStore.getAccounts(context)
            val currentGolikeIg = GolikeAccountsStore.getAccounts(context, "instagram").associateBy { it.username.lowercase().trim().removePrefix("@") }
            igStoreAccounts.forEach { igAcc ->
                val cleanUname = igAcc.username.lowercase().trim().removePrefix("@")
                if (cleanUname.isNotBlank()) {
                    val existing = currentGolikeIg[cleanUname]
                    val updated = (existing ?: GolikeAccount(
                        id = igAcc.username,
                        platform = "instagram",
                        username = igAcc.username,
                        avatar = igAcc.avatar,
                        isLive = igAcc.isLive,
                        isGolikeLinked = false,
                        proxy = igAcc.proxy,
                        lastStatus = "Sẵn sàng làm job"
                    )).copy(
                        avatar = if (existing?.avatar.isNullOrBlank()) igAcc.avatar else existing!!.avatar,
                        isLive = igAcc.isLive,
                        proxy = if (existing?.proxy.isNullOrBlank()) igAcc.proxy else existing!!.proxy
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, updated)
                }
            }
            otherAccounts = if (!GolikeSession.isLoggedIn.value) emptyList() else GolikeAccountsStore.getAccounts(context, "instagram")
        } else {
            otherAccounts = if (!GolikeSession.isLoggedIn.value) emptyList() else GolikeAccountsStore.getAccounts(context, selectedPlatform)
        }
    }

    // Khởi tạo nạp danh sách TikTok ngay khi mở màn hình
    LaunchedEffect(Unit) {
        reloadAccounts()
    }

    // Tự động kiểm tra và đồng bộ trạng thái liên kết nick từ Golike API khi mở màn hình hoặc sau đăng nhập
    LaunchedEffect(isLoggedIn, selectedPlatform) {
        if (isLoggedIn) {
            scope.launch(Dispatchers.IO) {
                try {
                    val client = GolikeAccountsStore.getApiClient(context)
                    // 1. Cập nhật số dư và thông tin người dùng
                    val me = client.getMe()
                    val data = me?.optJSONObject("data")
                    val newCoin = data?.optLong("coin")
                    val newUname = data?.optString("username")?.takeIf { it.isNotBlank() }
                    if (newCoin != null) {
                        GolikeAccountsStore.updateBalance(context, newCoin)
                        withContext(Dispatchers.Main) {
                            GolikeSession.updateBalance(context, newCoin)
                            if (newUname != null) {
                                GolikeSession.username.value = newUname
                            }
                        }
                    }

                    // 2. Quét danh sách nick đã liên kết từ API Golike về và đối chiếu
                    syncLinkedAccountsFromApi(context, client, selectedPlatform, if (selectedPlatform == "tiktok") tiktokAccounts else null)
                    withContext(Dispatchers.Main) {
                        reloadAccounts()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                GolikeAccountsStore.setScanningTikTok(context, false)
                reloadAccounts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedPlatform) {
        reloadAccounts()
        selectedForRunIds = emptySet()
    }

    // Lắng nghe kết quả hoàn tất kiểm tra / quét từ màn nổi (Overlay)
    LaunchedEffect(Unit) {
        XsmmTaskAutomationBridge.result.collect { res ->
            if (res is XsmmTaskActionResult.Completed) {
                GolikeAccountsStore.setScanningTikTok(context, false)
                reloadAccounts()
            }
        }
    }

    // Lắng nghe kết quả quét tài khoản TikTok từ màn nổi (Overlay)
    LaunchedEffect(Unit) {
        TikTokCaptureBridge.state.collect { state ->
            when (state) {
                is TikTokCaptureState.Captured -> {
                    val clean = state.handle.trim().removePrefix("@")
                    if (clean.isNotBlank()) {
                        val gAcc = GolikeAccount(
                            id = clean,
                            platform = "tiktok",
                            username = clean,
                            avatar = state.avatarUrl,
                            isLive = true,
                            isGolikeLinked = false,
                            golikeAccountId = "",
                            lastStatus = "Cần liên kết Golike"
                        )
                        val idx = tiktokAccounts.indexOfFirst { it.id.equals(clean, ignoreCase = true) }
                        if (idx >= 0) {
                            tiktokAccounts[idx] = gAcc
                        } else {
                            tiktokAccounts.add(gAcc)
                        }
                        GolikeAccountsStore.saveTikTokAccounts(context, tiktokAccounts.toList())
                        Toast.makeText(context, "Đã quét thành công nick: @$clean", Toast.LENGTH_SHORT).show()
                    }
                    TikTokCaptureBridge.reset()
                }
                is TikTokCaptureState.CapturedBatch -> {
                    val active = state.accounts.firstOrNull { it.isActive } ?: state.accounts.firstOrNull()
                    val mainClean = active?.handle?.ifBlank { active.displayName }?.trim()?.removePrefix("@").orEmpty()
                    state.accounts.forEach { entry ->
                        val h = entry.handle.ifBlank { entry.displayName }.trim().removePrefix("@")
                        if (h.isNotBlank()) {
                            val gAcc = GolikeAccount(
                                id = h,
                                platform = "tiktok",
                                username = h,
                                avatar = "",
                                isLive = true,
                                isGolikeLinked = false,
                                golikeAccountId = "",
                                lastStatus = "Cần liên kết Golike"
                            )
                            val idx = tiktokAccounts.indexOfFirst { it.id.equals(h, ignoreCase = true) }
                            if (idx >= 0) {
                                tiktokAccounts[idx] = gAcc
                            } else {
                                tiktokAccounts.add(gAcc)
                            }
                        }
                    }
                    GolikeAccountsStore.saveTikTokAccounts(context, tiktokAccounts.toList())
                    if (mainClean.isNotBlank()) {
                        Toast.makeText(context, "Đã quét thành công nick: @$mainClean", Toast.LENGTH_SHORT).show()
                    }
                    TikTokCaptureBridge.reset()
                }
                is TikTokCaptureState.Failed -> {
                    Toast.makeText(context, state.reason, Toast.LENGTH_LONG).show()
                    TikTokCaptureBridge.reset()
                }
                else -> Unit
            }
        }
    }

    // Modal BottomSheet Chi tiết lỗi
    if (selectedErrorAccount != null) {
        val acc = selectedErrorAccount!!
        val detailMsg = GolikeRunningManager.lastErrorDetailMap[acc.id]
            ?: acc.lastErrorDetail
            ?: "Tài khoản bị giới hạn tương tác tạm thời hoặc không thể nhận thêm nhiệm vụ lúc này."
        GolikeErrorDetailBottomSheet(
            accountName = acc.username.ifBlank { acc.id },
            errorMessage = detailMsg,
            onDismiss = { selectedErrorAccount = null }
        )
    }

    // Modal BottomSheet Cấu hình nhiệm vụ Golike (Chuẩn thiết kế mới)
    // Modal BottomSheet Cấu hình nhiệm vụ Golike (Chuẩn thiết kế mới)
    if (showConfigSheet) {
        if (selectedPlatform == "instagram") {
            val savedCfg = remember(showConfigSheet) { GolikeRunConfigStore.get(context, "instagram") }
            var igConfigState by remember(showConfigSheet) {
                val mode = when (savedCfg.taskType) {
                    "instagram_follow" -> GolikeIgTaskMode.FollowOnly
                    "instagram_like" -> GolikeIgTaskMode.TymOnly
                    else -> GolikeIgTaskMode.Random
                }
                mutableStateOf(
                    GolikeRunConfigInstagramUiState(
                        mode = mode,
                        getTaskWaitSeconds = savedCfg.getJobMinSeconds.toString(),
                        workSeconds = savedCfg.doJobMinSeconds.toString(),
                        taskCount = if (savedCfg.taskCountTarget > 0) savedCfg.taskCountTarget.toString() else "",
                        stopAfterEmptyTimes = savedCfg.stopAfterNoTaskCount.toString(),
                        errorSwapAccountCount = savedCfg.failJobCountToSwitchAccount.toString(),
                        stopAfterDoneTimes = if (savedCfg.stopAfterCompletedCount > 0) savedCfg.stopAfterCompletedCount.toString() else "",
                        browseBeforeTask = savedCfg.swipeBeforeGetJob,
                        backHomeThenBrowse = savedCfg.returnHomeAndSwipe,
                    )
                )
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showConfigSheet = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                GolikeRunConfigInstagramScreen(
                    state = igConfigState,
                    onBack = { showConfigSheet = false },
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
                        val targetType = when (igConfigState.mode) {
                            GolikeIgTaskMode.FollowOnly -> "instagram_follow"
                            GolikeIgTaskMode.TymOnly -> "instagram_like"
                            GolikeIgTaskMode.Random -> "instagram_random"
                        }
                        val gWait = igConfigState.getTaskWaitSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 10
                        val wSec = igConfigState.workSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 10
                        val goal = igConfigState.taskCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val stopEmpty = igConfigState.stopAfterEmptyTimes.toIntOrNull()?.coerceAtLeast(1) ?: 100
                        val errSwap = igConfigState.errorSwapAccountCount.toIntOrNull()?.coerceAtLeast(1) ?: 50
                        val stopDone = igConfigState.stopAfterDoneTimes.toIntOrNull()?.coerceAtLeast(0) ?: 100

                        GolikeRunConfigStore.save(
                            context,
                            savedCfg.copy(
                                platform = "instagram",
                                taskType = targetType,
                                getJobMinSeconds = gWait,
                                getJobMaxSeconds = gWait,
                                doJobMinSeconds = wSec,
                                doJobMaxSeconds = wSec,
                                delayMinSeconds = gWait,
                                delayMaxSeconds = gWait,
                                taskCountTarget = goal,
                                stopAfterNoTaskCount = stopEmpty,
                                failJobCountToSwitchAccount = errSwap,
                                stopAfterCompletedCount = stopDone,
                                swipeBeforeGetJob = igConfigState.browseBeforeTask,
                                returnHomeAndSwipe = igConfigState.backHomeThenBrowse
                            )
                        )
                        Toast.makeText(context, "Đã lưu cấu hình Instagram", Toast.LENGTH_SHORT).show()
                        showConfigSheet = false
                    }
                )
            }
        } else {
            val currentConfig = remember(showConfigSheet, selectedPlatform) {
                GolikeRunConfigStore.get(context, selectedPlatform)
            }
            var configUiState by remember(showConfigSheet, selectedPlatform) {
                mutableStateOf(
                    GolikeTaskConfigUiState(
                        platformLabel = selectedPlatform.replaceFirstChar { it.uppercase() },
                        getJobMinSeconds = currentConfig.getJobMinSeconds.toString(),
                        getJobMaxSeconds = currentConfig.getJobMaxSeconds.toString(),
                        swipeBeforeGetJob = currentConfig.swipeBeforeGetJob,
                        doJobMinSeconds = currentConfig.doJobMinSeconds.toString(),
                        doJobMaxSeconds = currentConfig.doJobMaxSeconds.toString(),
                        swipeAfterFollow = currentConfig.swipeAfterFollow,
                        completeJobMinSeconds = currentConfig.completeJobMinSeconds.toString(),
                        completeJobMaxSeconds = currentConfig.completeJobMaxSeconds.toString(),
                        breakMinSeconds = currentConfig.delayMinSeconds.toString(),
                        breakMaxSeconds = currentConfig.delayMaxSeconds.toString(),
                        goalTaskCount = currentConfig.taskCountTarget.toString(),
                        autoSwapOnError = currentConfig.autoSwitchOnFail,
                        errorSwapCount = currentConfig.failJobCountToSwitchAccount.toString()
                    )
                )
            }

            GolikeTaskConfigSheet(
                state = configUiState,
                onDismiss = { showConfigSheet = false },
                onGetJobMinChange = { configUiState = configUiState.copy(getJobMinSeconds = it) },
                onGetJobMaxChange = { configUiState = configUiState.copy(getJobMaxSeconds = it) },
                onSwipeBeforeGetJobChange = { configUiState = configUiState.copy(swipeBeforeGetJob = it) },
                onDoJobMinChange = { configUiState = configUiState.copy(doJobMinSeconds = it) },
                onDoJobMaxChange = { configUiState = configUiState.copy(doJobMaxSeconds = it) },
                onSwipeAfterFollowChange = { configUiState = configUiState.copy(swipeAfterFollow = it) },
                onCompleteJobMinChange = { configUiState = configUiState.copy(completeJobMinSeconds = it) },
                onCompleteJobMaxChange = { configUiState = configUiState.copy(completeJobMaxSeconds = it) },
                onBreakMinChange = { configUiState = configUiState.copy(breakMinSeconds = it) },
                onBreakMaxChange = { configUiState = configUiState.copy(breakMaxSeconds = it) },
                onGoalTaskCountChange = { configUiState = configUiState.copy(goalTaskCount = it) },
                onAutoSwapOnErrorChange = { configUiState = configUiState.copy(autoSwapOnError = it) },
                onErrorSwapCountChange = { configUiState = configUiState.copy(errorSwapCount = it) },
                onSave = {
                    val gMin = configUiState.getJobMinSeconds.toIntOrNull() ?: 5
                    val gMax = configUiState.getJobMaxSeconds.toIntOrNull() ?: 12
                    val dMin = configUiState.doJobMinSeconds.toIntOrNull() ?: 8
                    val dMax = configUiState.doJobMaxSeconds.toIntOrNull() ?: 15
                    val cMin = configUiState.completeJobMinSeconds.toIntOrNull() ?: 20
                    val cMax = configUiState.completeJobMaxSeconds.toIntOrNull() ?: 40
                    val bMin = configUiState.breakMinSeconds.toIntOrNull() ?: 8
                    val bMax = configUiState.breakMaxSeconds.toIntOrNull() ?: 15
                    val goal = configUiState.goalTaskCount.toIntOrNull() ?: 0
                    val errCnt = configUiState.errorSwapCount.toIntOrNull() ?: 5

                    val newCfg = GolikeRunConfig(
                        platform = selectedPlatform,
                        getJobMinSeconds = minOf(gMin, gMax).coerceAtLeast(1),
                        getJobMaxSeconds = maxOf(gMin, gMax).coerceAtLeast(1),
                        swipeBeforeGetJob = configUiState.swipeBeforeGetJob,
                        doJobMinSeconds = minOf(dMin, dMax).coerceAtLeast(1),
                        doJobMaxSeconds = maxOf(dMin, dMax).coerceAtLeast(1),
                        swipeAfterFollow = configUiState.swipeAfterFollow,
                        completeJobMinSeconds = minOf(cMin, cMax).coerceAtLeast(1),
                        completeJobMaxSeconds = maxOf(cMin, cMax).coerceAtLeast(1),
                        delayMinSeconds = minOf(bMin, bMax).coerceAtLeast(1),
                        delayMaxSeconds = maxOf(bMin, bMax).coerceAtLeast(1),
                        taskCountTarget = goal.coerceAtLeast(0),
                        failJobCountToSwitchAccount = errCnt.coerceAtLeast(1),
                        autoSwitchOnFail = configUiState.autoSwapOnError
                    )
                    GolikeRunConfigStore.save(context, newCfg)
                    Toast.makeText(context, "Đã lưu cấu hình làm nhiệm vụ", Toast.LENGTH_SHORT).show()
                    showConfigSheet = false
                }
            )
        }
    }

    // Dialog WebView Đăng nhập Golike (Vượt Cloudflare Turnstile)
    if (showWebViewLoginDialog) {
        GolikeWebViewLoginDialog(
            onDismiss = { showWebViewLoginDialog = false },
            onLoginSuccess = { uname ->
                reloadAccounts()
                // Tự động kéo thông tin người dùng và danh sách tài khoản MXH từ Golike về
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = GolikeAccountsStore.getApiClient(context)
                        val me = client.getMe()
                        val data = me?.optJSONObject("data")
                        val newCoin = data?.optLong("coin")
                        val newUname = data?.optString("username")?.takeIf { it.isNotBlank() }
                        if (newCoin != null) {
                            GolikeAccountsStore.updateBalance(context, newCoin)
                            withContext(Dispatchers.Main) {
                                GolikeSession.updateBalance(context, newCoin)
                                if (newUname != null) {
                                    GolikeSession.username.value = newUname
                                }
                            }
                        }
                        syncLinkedAccountsFromApi(context, client, selectedPlatform)
                        withContext(Dispatchers.Main) {
                            reloadAccounts()
                        }
                    } catch (_: Exception) {}
                }
            }
        )
    }

    // Nếu chưa đăng nhập Golike: Hiển thị màn hình GolikeScreen mới (trạng thái chưa đăng nhập)
    if (!isLoggedIn) {
        GolikeScreen(
            onBack = { navController.popBackStack() },
            onLogin = { showWebViewLoginDialog = true }
        )
        return
    }

    if (showAddAccountSheet) {
        if (selectedPlatform == "instagram") {
            com.cayxu.app.ui.screens.xsmm.InstagramCookieBottomSheet(
                onDismiss = { showAddAccountSheet = false },
                onCookieSaved = {
                    showAddAccountSheet = false
                    reloadAccounts()
                }
            )
        } else {
            GolikeAddAccountBottomSheet(
                platform = selectedPlatform,
                onDismiss = { showAddAccountSheet = false },
                onAccountAdded = {
                    reloadAccounts()
                }
            )
        }
    }

    // Dialog xác nhận xóa tài khoản
    if (showDeleteConfirmSheet) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmSheet = false },
            title = {
                Text("Xác nhận xóa tài khoản", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "Bạn có chắc muốn xóa vĩnh viễn ${selectedForRunIds.size} tài khoản đã chọn khỏi Golike không?",
                    fontSize = 13.5.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = selectedForRunIds.size
                        selectedForRunIds.forEach { id -> GolikeRunningManager.stop(id) }
                        GolikeAccountsStore.removeAccounts(context, selectedPlatform, selectedForRunIds)
                        selectedForRunIds = emptySet()
                        showDeleteConfirmSheet = false
                        reloadAccounts()
                        Toast.makeText(context, "Đã xóa vĩnh viễn $count tài khoản", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Xóa vĩnh viễn", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmSheet = false }) {
                    Text("Hủy")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardWhite
        )
    }

    // Hàm thực thi luồng chạy nhiệm vụ theo nick qua GolikeApiClient
    fun startAccountTask(acc: GolikeAccount) {
        if (!isLoggedIn) {
            Toast.makeText(context, "Vui lòng đăng nhập tài khoản Golike trước khi chạy!", Toast.LENGTH_SHORT).show()
            showWebViewLoginDialog = true
            return
        }
        GolikeRunningManager.runningAccounts[acc.id] = true
        GolikeRunningManager.statusMap[acc.id] = "Đang kết nối API Gateway Golike..."

        if (selectedPlatform.lowercase() == "tiktok") {
            scope.launch(Dispatchers.IO) {
                val client = GolikeAccountsStore.getApiClient(context)
                val cleanHandle = acc.id.ifBlank { acc.username }.trim().removePrefix("@").lowercase()
                val cachedId = acc.golikeAccountId.ifBlank {
                    GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanHandle).orEmpty()
                }
                var effectiveAcc = if (cachedId.isNotBlank()) {
                    acc.copy(isGolikeLinked = true, golikeAccountId = cachedId)
                } else acc

                // Nếu chưa liên kết Golike: Tự động chạy quy trình liên kết nick trước
                if (!effectiveAcc.isGolikeLinked || effectiveAcc.golikeAccountId.isBlank()) {
                    GolikeRunningManager.statusMap[acc.id] = "Đang kiểm tra & liên kết nick với Golike..."
                    XsmmJobStatusBridge.update("Đang kiểm tra & liên kết nick với Golike...")
                    val linkResult = GolikeTikTokTaskRunner.verifyAndLinkTikTokAccount(
                        context = context,
                        client = client,
                        username = cleanHandle,
                        onProgress = { p ->
                            GolikeRunningManager.statusMap[acc.id] = p
                            XsmmJobStatusBridge.update(p)
                        }
                    )
                    if (linkResult.isSuccess) {
                        val linkedAcc = linkResult.getOrNull()
                        val newId = linkedAcc?.golikeAccountId?.ifBlank {
                            GolikeAccountsStore.getTikTokAccountIdFromMap(context, cleanHandle).orEmpty()
                        }.orEmpty()
                        effectiveAcc = effectiveAcc.copy(
                            isGolikeLinked = true,
                            golikeAccountId = newId,
                            lastStatus = "Đã liên kết Golike • Sẵn sàng"
                        )
                        GolikeAccountsStore.addOrUpdateAccount(context, effectiveAcc)
                        withContext(Dispatchers.Main) {
                            reloadAccounts()
                        }
                    } else {
                        val failMsg = linkResult.exceptionOrNull()?.message ?: "Không thể liên kết nick @$cleanHandle với Golike"
                        GolikeRunningManager.statusMap[acc.id] = "Lỗi: $failMsg"
                        XsmmJobStatusBridge.update("Lỗi: $failMsg")
                        GolikeRunningManager.runningAccounts[acc.id] = false
                        GolikeRunningManager.lastErrorDetailMap[acc.id] = failMsg
                        return@launch
                    }
                }

                GolikeTikTokTaskRunner.runTikTokTaskLoop(
                    context = context,
                    account = effectiveAcc,
                    client = client,
                    variant = selectedVariant,
                    onStatusChange = { newStatus ->
                        GolikeRunningManager.statusMap[acc.id] = newStatus
                        XsmmJobStatusBridge.update(newStatus)
                        GolikeAccountsStore.updateAccountProgress(context, "tiktok", acc.id, newStatus)
                    },
                    onJobSuccess = { earned ->
                        val curSucc = (GolikeRunningManager.successCountMap[acc.id] ?: acc.successCount) + 1
                        GolikeRunningManager.successCountMap[acc.id] = curSucc
                        val newBal = GolikeSession.balance.value + earned
                        GolikeAccountsStore.updateBalance(context, newBal)
                        scope.launch(Dispatchers.Main) {
                            GolikeSession.balance.value = newBal
                        }
                        GolikeAccountsStore.updateAccountProgress(context, "tiktok", acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = true)
                        XsmmJobStatusBridge.update("+$earned đ (Xong $curSucc NV)")
                    },
                    onJobFailed = { reason ->
                        val curErr = (GolikeRunningManager.errorCountMap[acc.id] ?: acc.errorCount) + 1
                        GolikeRunningManager.errorCountMap[acc.id] = curErr
                        GolikeRunningManager.lastErrorDetailMap[acc.id] = reason
                        GolikeAccountsStore.updateAccountProgress(context, "tiktok", acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = false, errorDetail = reason)
                        XsmmJobStatusBridge.update("Lỗi: $reason")
                    }
                )
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            val client = GolikeAccountsStore.getApiClient(context)
            val cfg = GolikeRunConfigStore.get(context, selectedPlatform)
            val golikeAccId = acc.golikeAccountId.ifBlank { acc.id }
            var consecutiveFails = 0

            while (GolikeRunningManager.isRunning(acc.id) && isActive) {
                try {
                    GolikeRunningManager.statusMap[acc.id] = "Đang lấy nhiệm vụ mới..."

                    // 1. Lấy nhiệm vụ (Get Job)
                    val jobRes = when (selectedPlatform.lowercase()) {
                        "facebook" -> client.getFacebookJob(acc.id)
                        "instagram" -> client.getInstagramJob(golikeAccId)
                        else -> client.getTikTokJob(golikeAccId)
                    }

                    if (jobRes == null || !jobRes.optBoolean("success", false)) {
                        consecutiveFails++
                        val errMsg = jobRes?.optString("message")?.takeIf { it.isNotBlank() } ?: "Chưa có nhiệm vụ phù hợp lúc này"
                        GolikeRunningManager.statusMap[acc.id] = "Chưa có job: $errMsg (Chờ ${cfg.delayMinSeconds}s...)"
                        GolikeRunningManager.lastErrorDetailMap[acc.id] = errMsg
                        GolikeAccountsStore.updateAccountProgress(context, selectedPlatform, acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = null, errorDetail = errMsg)

                        if (cfg.autoSwitchOnFail && consecutiveFails >= cfg.failJobCountToSwitchAccount) {
                            GolikeRunningManager.statusMap[acc.id] = "Tạm dừng: Gặp lỗi liên tiếp $consecutiveFails lần"
                            GolikeRunningManager.runningAccounts[acc.id] = false
                            break
                        }

                        delay(cfg.delayMinSeconds * 1000L)
                        continue
                    }

                    consecutiveFails = 0
                    val jobData = jobRes.optJSONObject("data")
                    if (jobData == null) {
                        delay(cfg.delayMinSeconds * 1000L)
                        continue
                    }

                    val adsId = jobData.optLong("ads_id", jobData.optLong("id", 0L))
                    val jobType = jobData.optString("type", "tương tác")
                    val countdown = jobRes.optInt("countdown", cfg.delayMinSeconds).coerceAtLeast(3)

                    // 2. Thực hiện tương tác thực tế nếu là Instagram (thừa hưởng từ IgTaToolClient)
                    if (selectedPlatform.lowercase() == "instagram") {
                        val igAcc = InstagramAccountsStore.getAccount(context, acc.username)
                            ?: InstagramAccountsStore.getAccount(context, acc.id)

                        if (igAcc == null || igAcc.cookie.isBlank()) {
                            consecutiveFails++
                            val errMsg = "Chưa có Cookie Instagram cho nick ${acc.username}"
                            val curErr = (GolikeRunningManager.errorCountMap[acc.id] ?: acc.errorCount) + 1
                            GolikeRunningManager.errorCountMap[acc.id] = curErr
                            GolikeRunningManager.lastErrorDetailMap[acc.id] = errMsg
                            GolikeRunningManager.statusMap[acc.id] = "Lỗi: $errMsg"
                            client.skipJob(adsId, jobData.optString("object_id"), golikeAccId, jobType)
                            GolikeAccountsStore.updateAccountProgress(context, selectedPlatform, acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = false, errorDetail = errMsg)
                            delay(cfg.delayMinSeconds * 1000L)
                            continue
                        }

                        val igProxy = igAcc.proxy.ifBlank { acc.proxy }.takeIf { it.isNotBlank() }
                        val igClient = IgTaToolClient.buildClient(igProxy)
                        val targetLink = jobData.optString("link")
                        val objectId = jobData.optString("object_id")
                        val pkgName = jobData.optString("package_name")

                        var actionSuccess = true
                        var actionErrMsg = ""

                        if (jobType.contains("follow", ignoreCase = true) || jobType.contains("sub", ignoreCase = true)) {
                            GolikeRunningManager.statusMap[acc.id] = "Đang thực hiện Follow Instagram..."
                            var uid = objectId.trim()
                            if (!uid.all { it.isDigit() } || uid.isBlank()) {
                                val extracted = if (targetLink.isNotBlank()) IgTaToolClient.extractTargetIdFromUrl(igClient, igAcc.cookie, targetLink) else null
                                uid = extracted ?: objectId
                            }
                            val targetUser = (if (pkgName.isNotBlank()) pkgName else objectId).trim().trim('/').substringAfterLast('/')
                            val profileUrl = if (targetLink.isNotBlank()) targetLink else if (targetUser.isNotBlank()) "https://www.instagram.com/$targetUser/" else "https://www.instagram.com/"
                            val followRes = IgTaToolClient.follow(
                                client = igClient,
                                rawCookie = igAcc.cookie,
                                targetId = uid,
                                profileUrl = profileUrl,
                                cachedDtsg = igAcc.fbDtsg.takeIf { it.isNotBlank() },
                                cachedLsd = igAcc.lsd.takeIf { it.isNotBlank() },
                                userId = igAcc.userId.takeIf { it.isNotBlank() }
                            )
                            if (followRes.contains("1357004") || followRes.contains("checkpoint_required") || followRes.contains("login_required")) {
                                actionSuccess = false
                                actionErrMsg = "Instagram yêu cầu xác minh / Checkpoint"
                            } else if (!followRes.contains("\"following\":true") && !followRes.contains("xdt_create_friendship") && !followRes.contains("\"status\":\"ok\"")) {
                                actionSuccess = false
                                actionErrMsg = "Instagram từ chối Follow: $followRes"
                            }
                        } else if (jobType.contains("like", ignoreCase = true) || jobType.contains("tym", ignoreCase = true)) {
                            GolikeRunningManager.statusMap[acc.id] = "Đang thực hiện Like / Tym Instagram..."
                            var mid = (if (objectId.isNotBlank()) objectId else jobData.optString("media_id")).trim()
                            if (!mid.all { it.isDigit() } || mid.isBlank()) {
                                val sc = XsmmInstagramTaskRunner.extractShortcode(targetLink)
                                    ?: XsmmInstagramTaskRunner.extractShortcode(mid)
                                if (!sc.isNullOrBlank()) mid = XsmmInstagramTaskRunner.shortcodeToMediaId(sc) ?: mid
                            }
                            val likeRes = IgTaToolClient.tym(
                                client = igClient,
                                rawCookie = igAcc.cookie,
                                mediaId = mid,
                                linkJob = targetLink,
                                cachedDtsg = igAcc.fbDtsg.takeIf { it.isNotBlank() },
                                cachedLsd = igAcc.lsd.takeIf { it.isNotBlank() },
                                userId = igAcc.userId.takeIf { it.isNotBlank() }
                            )
                            if (likeRes.contains("1357004") || likeRes.contains("checkpoint_required") || likeRes.contains("login_required")) {
                                actionSuccess = false
                                actionErrMsg = "Instagram yêu cầu xác minh / Checkpoint"
                            } else if (!likeRes.contains("\"status\":\"ok\"") && !likeRes.contains("xdt_like_media") && !likeRes.contains("\"viewer_has_liked\":true")) {
                                actionSuccess = false
                                actionErrMsg = "Instagram từ chối Like: $likeRes"
                            }
                        }

                        if (!actionSuccess) {
                            consecutiveFails++
                            val curErr = (GolikeRunningManager.errorCountMap[acc.id] ?: acc.errorCount) + 1
                            GolikeRunningManager.errorCountMap[acc.id] = curErr
                            GolikeRunningManager.lastErrorDetailMap[acc.id] = actionErrMsg
                            GolikeRunningManager.statusMap[acc.id] = "Thao tác thất bại: $actionErrMsg"
                            client.skipJob(adsId, objectId, golikeAccId, jobType)
                            GolikeAccountsStore.updateAccountProgress(context, selectedPlatform, acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = false, errorDetail = actionErrMsg)
                            delay(cfg.delayMinSeconds * 1000L)
                            continue
                        }
                    }

                    // 3. Chờ thời gian đếm ngược an toàn
                    for (sec in countdown downTo 1) {
                        if (!GolikeRunningManager.isRunning(acc.id)) break
                        GolikeRunningManager.statusMap[acc.id] = "Làm job $jobType | Chờ nhận xu sau ${sec}s..."
                        delay(1000L)
                    }
                    if (!GolikeRunningManager.isRunning(acc.id)) break

                    // 4. Báo hoàn thành (Complete Job)
                    GolikeRunningManager.statusMap[acc.id] = "Đang gửi báo cáo hoàn thành..."
                    val compRes = when (selectedPlatform.lowercase()) {
                        "facebook" -> client.completeFacebookJob(adsId, acc.id)
                        "instagram" -> client.completeInstagramJob(adsId, golikeAccId)
                        else -> client.completeTikTokJob(adsId, golikeAccId)
                    }

                    if (compRes != null && compRes.optBoolean("success", false)) {
                        val price = compRes.optJSONObject("data")?.optInt("prices", 35) ?: 35
                        val curSucc = (GolikeRunningManager.successCountMap[acc.id] ?: acc.successCount) + 1
                        GolikeRunningManager.successCountMap[acc.id] = curSucc
                        GolikeRunningManager.statusMap[acc.id] = "Hoàn thành +$price đ | Nghỉ ${cfg.delayMinSeconds}s"

                        // Cập nhật số dư realtime
                        val newBal = GolikeSession.balance.value + price
                        GolikeAccountsStore.updateBalance(context, newBal)
                        withContext(Dispatchers.Main) {
                            GolikeSession.balance.value = newBal
                        }

                        GolikeAccountsStore.updateAccountProgress(context, selectedPlatform, acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = true)
                    } else {
                        val failMsg = compRes?.optString("message")?.takeIf { it.isNotBlank() } ?: "Lỗi hoàn thành nhiệm vụ"
                        val curErr = (GolikeRunningManager.errorCountMap[acc.id] ?: acc.errorCount) + 1
                        GolikeRunningManager.errorCountMap[acc.id] = curErr
                        GolikeRunningManager.lastErrorDetailMap[acc.id] = failMsg
                        GolikeRunningManager.statusMap[acc.id] = "Lỗi: $failMsg"

                        // Gọi bỏ qua job (Skip Job)
                        client.skipJob(adsId, jobData.optString("object_id"), golikeAccId, jobType)
                        GolikeAccountsStore.updateAccountProgress(context, selectedPlatform, acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = false, errorDetail = failMsg)
                    }

                    // Nghỉ giữa các job theo cấu hình
                    val randomDelay = (cfg.delayMinSeconds..cfg.delayMaxSeconds).random()
                    delay(randomDelay * 1000L)

                } catch (e: Exception) {
                    GolikeRunningManager.statusMap[acc.id] = "Lỗi kết nối: ${e.message}"
                    delay(5000L)
                }
            }
        }
    }

    val currentPlatform = GolikePlatform.fromId(selectedPlatform)
    val platformCounts = mapOf(
        GolikePlatform.TikTok to tiktokCount,
        GolikePlatform.Facebook to facebookCount,
        GolikePlatform.Instagram to instagramCount
    )
    val selectedTypeId = when (selectedVariant) {
        TikTokAppVariant.STANDARD -> "standard"
        TikTokAppVariant.LITE -> "lite"
        TikTokAppVariant.STUDIO -> "studio"
    }

    val accountsUiState = GolikeAccountsUiState(
        username = username,
        balanceDong = balance,
        platformCounts = platformCounts,
        selectedPlatform = currentPlatform,
        selectedTypeId = selectedTypeId,
        accounts = displayAccounts,
        selectedAccountIds = selectedForRunIds,
        refreshingBalance = isRefreshing,
        toast = null,
        linkingAccountId = addingUid
    )

    GolikeAccountsScreen(
        state = accountsUiState,
        onBack = { navController.popBackStack() },
        onRefreshBalance = {
            if (isRefreshing) return@GolikeAccountsScreen
            isRefreshing = true
            scope.launch(Dispatchers.IO) {
                try {
                    val client = GolikeAccountsStore.getApiClient(context)
                    val me = client.getMe()
                    val data = me?.optJSONObject("data")
                    val newCoin = data?.optLong("coin") ?: balance
                    val newUname = data?.optString("username")?.takeIf { it.isNotBlank() } ?: username

                    GolikeAccountsStore.updateBalance(context, newCoin)
                    withContext(Dispatchers.Main) {
                        GolikeSession.updateBalance(context, newCoin)
                        GolikeSession.username.value = newUname
                    }

                    // Đồng bộ danh sách tài khoản liên kết từ Golike API
                    syncLinkedAccountsFromApi(context, client, selectedPlatform)

                    withContext(Dispatchers.Main) {
                        reloadAccounts()
                        Toast.makeText(context, "Đã cập nhật số dư: " + newCoin + " đ", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi kết nối Golike: " + e.message, Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isRefreshing = false
                    }
                }
            }
        },
        onLogout = {
            GolikeRunningManager.stopAll(displayAccounts)
            GolikeRunningManager.clearAll()
            GolikeSession.logout(context)
            selectedForRunIds = emptySet()
            reloadAccounts()
            Toast.makeText(context, "Đã đăng xuất tài khoản Golike", Toast.LENGTH_SHORT).show()
        },
        onSelectPlatform = { platform ->
            selectedPlatform = platform.id
            selectedForRunIds = emptySet()
            reloadAccounts()
        },
        onSelectType = { typeId ->
            selectedVariant = when (typeId) {
                "lite" -> TikTokAppVariant.LITE
                "studio" -> TikTokAppVariant.STUDIO
                else -> TikTokAppVariant.STANDARD
            }
        },
        onToggleSelectAll = {
            val allIds = displayAccounts.map { it.id }.toSet()
            val isAllSelected = allIds.isNotEmpty() && selectedForRunIds.containsAll(allIds)
            selectedForRunIds = if (isAllSelected) emptySet() else allIds
        },
        onToggleAccount = { acc ->
            selectedForRunIds = if (acc.id in selectedForRunIds) {
                selectedForRunIds - acc.id
            } else {
                selectedForRunIds + acc.id
            }
        },
        onLinkAccount = { acc ->
            if (selectedPlatform == "tiktok") {
                if (addingUid != null) return@GolikeAccountsScreen
                addingUid = acc.id
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = GolikeAccountsStore.getApiClient(context)
                        val cleanHandle = acc.id.ifBlank { acc.username }.trim().removePrefix("@").lowercase()
                        val linkResult = GolikeTikTokTaskRunner.verifyAndLinkTikTokAccount(
                            context = context,
                            client = client,
                            username = cleanHandle,
                            onProgress = { p ->
                                GolikeRunningManager.statusMap[acc.id] = p
                            }
                        )
                        withContext(Dispatchers.Main) {
                            if (linkResult.isSuccess) {
                                val linkedAcc = linkResult.getOrNull()
                                val updatedAcc = acc.copy(
                                    isGolikeLinked = true,
                                    golikeAccountId = linkedAcc?.golikeAccountId.orEmpty(),
                                    lastStatus = "Đã liên kết Golike • Sẵn sàng"
                                )
                                GolikeAccountsStore.addOrUpdateAccount(context, updatedAcc)
                                reloadAccounts()
                                Toast.makeText(context, "Đã liên kết @" + cleanHandle + " thành công!", Toast.LENGTH_SHORT).show()
                            } else {
                                val err = linkResult.exceptionOrNull()?.message ?: "Thất bại"
                                Toast.makeText(context, "Liên kết thất bại: " + err, Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Lỗi: " + e.message, Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            addingUid = null
                        }
                    }
                }
            } else {
                isRefreshing = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val client = GolikeAccountsStore.getApiClient(context)
                        syncLinkedAccountsFromApi(context, client, selectedPlatform)
                        withContext(Dispatchers.Main) {
                            reloadAccounts()
                            Toast.makeText(context, "Đã đồng bộ tài khoản từ Golike", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Lỗi đồng bộ: " + e.message, Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            isRefreshing = false
                        }
                    }
                }
            }
        },
        onAddAccount = {
            showAddAccountSheet = true
        },
        onOpenConfig = {
            showConfigSheet = true
        },
        onRun = {
            if (selectedPlatform == "tiktok") {
                val isAnyRunning = GolikeRunningManager.isAnyRunning(tiktokAccounts)
                if (isAnyRunning) {
                    GolikeRunningManager.stopAll(tiktokAccounts)
                    Toast.makeText(context, "Đã dừng tất cả tài khoản TikTok Golike", Toast.LENGTH_SHORT).show()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName)
                        )
                        context.startActivity(intent)
                        Toast.makeText(context, "Vui lòng cấp quyền 'Hiển thị trên ứng dụng khác' để mở popup!", Toast.LENGTH_LONG).show()
                        return@GolikeAccountsScreen
                    }

                    if (!TikTokAppLauncher.isAccessibilityServiceEnabled(context)) {
                        Toast.makeText(context, "Vui lòng bật quyền Trợ năng cho CayXu!", Toast.LENGTH_LONG).show()
                        TikTokAppLauncher.openAccessibilitySettings(context)
                        return@GolikeAccountsScreen
                    }

                    val targets = tiktokAccounts.filter { it.id in selectedForRunIds }
                    if (targets.isEmpty()) {
                        Toast.makeText(context, "Vui lòng chọn ít nhất 1 tài khoản để chạy!", Toast.LENGTH_SHORT).show()
                        return@GolikeAccountsScreen
                    }

                    val selectedHandles = targets.map { it.id.ifBlank { it.username }.trim().removePrefix("@") }

                    targets.forEach { acc ->
                        val cleanHandle = acc.id.ifBlank { acc.username }.trim().removePrefix("@").lowercase()
                        GolikeRunningManager.runningAccounts[cleanHandle] = true
                        GolikeRunningManager.runningAccounts[acc.id] = true
                    }

                    startGolikeJobRunnerOverlay(
                        context = context,
                        accountHandles = selectedHandles,
                        variant = selectedVariant
                    )

                    Toast.makeText(context, "Bắt đầu chạy " + targets.size + " tài khoản TikTok Golike", Toast.LENGTH_SHORT).show()
                }
            } else {
                val isAnyRunning = GolikeRunningManager.isAnyRunning(otherAccounts)
                if (isAnyRunning) {
                    GolikeRunningManager.stopAll(otherAccounts)
                    Toast.makeText(context, "Đã dừng tất cả tài khoản Golike", Toast.LENGTH_SHORT).show()
                } else {
                    val targets = if (selectedForRunIds.isNotEmpty()) {
                        otherAccounts.filter { it.id in selectedForRunIds }
                    } else {
                        otherAccounts
                    }
                    if (targets.isEmpty()) {
                        Toast.makeText(context, "Chưa có tài khoản nào để chạy!", Toast.LENGTH_SHORT).show()
                        return@GolikeAccountsScreen
                    }
                    targets.forEach { acc -> startAccountTask(acc) }
                    Toast.makeText(context, "Bắt đầu chạy " + targets.size + " tài khoản Golike", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onDeleteSelected = {
            if (selectedForRunIds.isNotEmpty()) {
                showDeleteConfirmSheet = true
            }
        },
        onTogglePlayAccount = { acc ->
            val isRunning = GolikeRunningManager.isRunning(acc.id) || GolikeRunningManager.isRunning(acc.username)
            if (isRunning) {
                GolikeRunningManager.runningAccounts[acc.id] = false
                GolikeRunningManager.runningAccounts[acc.username] = false
                Toast.makeText(context, "Đã dừng tài khoản @${acc.username}", Toast.LENGTH_SHORT).show()
            } else {
                startAccountTask(acc)
            }
        },
        onReloadAccount = { acc ->
            reloadAccounts()
            Toast.makeText(context, "Đã làm mới tài khoản @${acc.username}", Toast.LENGTH_SHORT).show()
        }
    )

}

@Composable
private fun VariantTabChip(
    label: String,
    variant: TikTokAppVariant,
    selectedVariant: TikTokAppVariant,
    onClick: (TikTokAppVariant) -> Unit
) {
    val isSelected = variant == selectedVariant
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
            label,
            color = if (isSelected) Color.White else TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun formatTikTokCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
private fun GolikeTikTokAccountCard(
    account: GolikeAccount,
    isCheckedForRun: Boolean,
    isReloading: Boolean,
    onCheckedForRunChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onReloadProfile: () -> Unit
) {
    val title = account.username.ifBlank { account.id.ifBlank { "TikTok User" } }
    val initialLetter = (title.firstOrNull { it.isLetterOrDigit() } ?: 'T').uppercase()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = if (isCheckedForRun) androidx.compose.foundation.BorderStroke(1.5.dp, TikTokBrandBlack) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEF1F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCheckedForRunChange(!isCheckedForRun)
            }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox tích chọn acc ở đầu mỗi card
            Checkbox(
                checked = isCheckedForRun,
                onCheckedChange = onCheckedForRunChange,
                colors = CheckboxDefaults.colors(checkedColor = TikTokBrandBlack),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))

            // Avatar TikTok
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF111111).copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (account.avatar.isNotBlank()) {
                    AsyncImage(
                        model = account.avatar,
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
                // Dòng 1: Tên hiển thị + Badge Live / Die
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

                    // Badge Trạng thái liên kết Golike
                    if (account.isGolikeLinked) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF22C55E).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Đã liên kết",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEF3C7))
                                .border(0.5.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD97706))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Chưa liên kết",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(1.dp))
                // Dòng 2: @handle
                val handleClean = account.id.ifBlank { account.username.trim().removePrefix("@") }
                Text(
                    text = "@$handleClean",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )

                // Dòng 3: Trạng thái chi tiết liên kết Golike
                if (!account.isGolikeLinked) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "⚠ Cần liên kết Golike",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD97706)
                    )
                } else if (account.golikeAccountId.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "ID: ${account.golikeAccountId}",
                        fontSize = 10.5.sp,
                        color = TextSecondary
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
        }
    }
}
