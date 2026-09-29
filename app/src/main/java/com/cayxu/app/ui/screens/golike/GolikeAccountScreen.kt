package com.cayxu.app.ui.screens.golike

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
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
}

/**
 * Đồng bộ danh sách tài khoản đã liên kết từ API Golike về máy và quét tài khoản trên máy.
 */
suspend fun syncLinkedAccountsFromApi(context: Context, client: GolikeApiClient, platform: String) {
    try {
        if (platform.lowercase() == "tiktok") {
            // 1. Quét danh sách tài khoản TikTok đã lưu trong máy (copy cơ chế từ XSMM)
            val localTikTokAccounts = com.cayxu.app.data.local.TikTokAccountsStore.getAccounts(context).filter { it.enabled }

            // 2. Lấy danh sách nick TikTok đã liên kết trên Golike qua Gateway API
            val res = client.getTikTokAccounts()
            val dataArray = res?.optJSONArray("data")

            val serverMap = mutableMapOf<String, JSONObject>()
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    val uname = item.optString("unique_username").ifBlank {
                        item.optString("username").ifBlank {
                            item.optString("nickname")
                        }
                    }.trim().removePrefix("@").lowercase()
                    if (uname.isNotBlank()) {
                        serverMap[uname] = item
                    }
                }
            }

            val processedHandles = mutableSetOf<String>()

            // 3. Đối chiếu nick trên máy với danh sách từ Golike
            for (local in localTikTokAccounts) {
                val cleanHandle = local.handle.trim().removePrefix("@").lowercase()
                processedHandles.add(cleanHandle)
                val serverItem = serverMap[cleanHandle]

                if (serverItem != null) {
                    val accountId = serverItem.optString("id")
                    val serverAvatar = serverItem.optString("avatar")
                    val acc = GolikeAccount(
                        id = cleanHandle,
                        platform = "tiktok",
                        username = local.handle,
                        avatar = serverAvatar.ifBlank { local.avatarUrl },
                        isLive = local.isLive,
                        isGolikeLinked = true,
                        golikeAccountId = accountId,
                        lastStatus = "Đã liên kết Golike • Sẵn sàng"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, acc)
                } else {
                    // Nick trên máy chưa liên kết Golike
                    val existing = GolikeAccountsStore.getAccounts(context, "tiktok").firstOrNull { it.id.equals(cleanHandle, ignoreCase = true) }
                    val acc = GolikeAccount(
                        id = cleanHandle,
                        platform = "tiktok",
                        username = local.handle,
                        avatar = local.avatarUrl,
                        isLive = local.isLive,
                        isGolikeLinked = existing?.isGolikeLinked ?: false,
                        golikeAccountId = existing?.golikeAccountId.orEmpty(),
                        lastStatus = if (existing?.isGolikeLinked == true) existing.lastStatus else "Chưa liên kết Golike"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, acc)
                }
            }

            // 4. Bổ sung các nick đã có trên Golike nhưng chưa có trong máy
            for ((serverHandle, serverItem) in serverMap) {
                if (!processedHandles.contains(serverHandle)) {
                    val accountId = serverItem.optString("id")
                    val serverAvatar = serverItem.optString("avatar")
                    val displayUname = serverItem.optString("nickname").ifBlank {
                        serverItem.optString("unique_username").ifBlank {
                            serverItem.optString("username", serverHandle)
                        }
                    }
                    val acc = GolikeAccount(
                        id = serverHandle,
                        platform = "tiktok",
                        username = displayUname,
                        avatar = serverAvatar,
                        isLive = true,
                        isGolikeLinked = true,
                        golikeAccountId = accountId,
                        lastStatus = "Đã liên kết Golike • Sẵn sàng"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, acc)
                }
            }
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
    val isLoggedIn = !GolikeSession.token.value.isNullOrBlank()

    var isRefreshing by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf("tiktok") }
    var selectedForRunIds by remember(selectedPlatform) { mutableStateOf<Set<String>>(emptySet()) }

    var showConfigSheet by remember { mutableStateOf(false) }
    var showWebViewLoginDialog by remember { mutableStateOf(false) }
    var showAddAccountSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }
    var selectedErrorAccount by remember { mutableStateOf<GolikeAccount?>(null) }

    // Danh sách tài khoản theo nền tảng
    var currentAccounts by remember {
        mutableStateOf(GolikeAccountsStore.getAccounts(context, selectedPlatform))
    }

    // Đếm số lượng tài khoản theo từng tab
    val tiktokCount = remember(currentAccounts, selectedPlatform) {
        if (selectedPlatform == "tiktok") currentAccounts.size else GolikeAccountsStore.getAccounts(context, "tiktok").size
    }
    val facebookCount = remember(currentAccounts, selectedPlatform) {
        if (selectedPlatform == "facebook") currentAccounts.size else GolikeAccountsStore.getAccounts(context, "facebook").size
    }
    val instagramCount = remember(currentAccounts, selectedPlatform) {
        if (selectedPlatform == "instagram") currentAccounts.size else GolikeAccountsStore.getAccounts(context, "instagram").size
    }

    // Đồng bộ danh sách tài khoản khi đổi tab
    fun reloadAccounts() {
        currentAccounts = GolikeAccountsStore.getAccounts(context, selectedPlatform)
    }

    LaunchedEffect(selectedPlatform) {
        if (selectedPlatform.lowercase() == "tiktok") {
            // Nạp trước danh sách nick TikTok trên thiết bị nếu dữ liệu Golike đang trống
            val stored = GolikeAccountsStore.getAccounts(context, "tiktok")
            if (stored.isEmpty()) {
                val locals = com.cayxu.app.data.local.TikTokAccountsStore.getAccounts(context).filter { it.enabled }
                locals.forEach { local ->
                    val cleanHandle = local.handle.trim().removePrefix("@").lowercase()
                    val acc = GolikeAccount(
                        id = cleanHandle,
                        platform = "tiktok",
                        username = local.handle,
                        avatar = local.avatarUrl,
                        isLive = local.isLive,
                        isGolikeLinked = false,
                        golikeAccountId = "",
                        lastStatus = "Chưa liên kết Golike"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, acc)
                }
            }
            if (isLoggedIn) {
                scope.launch(Dispatchers.IO) {
                    val client = GolikeAccountsStore.getApiClient(context)
                    syncLinkedAccountsFromApi(context, client, "tiktok")
                    withContext(Dispatchers.Main) {
                        reloadAccounts()
                    }
                }
            }
        }
        reloadAccounts()
        selectedForRunIds = emptySet()
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

    // Modal BottomSheet Cấu hình
    if (showConfigSheet) {
        GolikeConfigBottomSheet(
            platform = selectedPlatform,
            onDismiss = { showConfigSheet = false },
            onConfigSaved = {
                // Đã lưu cấu hình thành công
            }
        )
    }

    // Dialog WebView Đăng nhập Golike (Vượt Cloudflare Turnstile)
    if (showWebViewLoginDialog) {
        GolikeWebViewLoginDialog(
            onDismiss = { showWebViewLoginDialog = false },
            onLoginSuccess = { uname ->
                reloadAccounts()
                // Tự động kéo danh sách tài khoản MXH từ Golike về
                scope.launch(Dispatchers.IO) {
                    val client = GolikeAccountsStore.getApiClient(context)
                    syncLinkedAccountsFromApi(context, client, selectedPlatform)
                    withContext(Dispatchers.Main) {
                        reloadAccounts()
                    }
                }
            }
        )
    }

    // Modal BottomSheet Thêm tài khoản
    if (showAddAccountSheet) {
        GolikeAddAccountBottomSheet(
            platform = selectedPlatform,
            onDismiss = { showAddAccountSheet = false },
            onAccountAdded = { reloadAccounts() }
        )
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
                var targetAcc = acc
                if (!acc.isGolikeLinked || acc.golikeAccountId.isBlank()) {
                    GolikeRunningManager.statusMap[acc.id] = "Đang tự động cấu hình xác minh vào Golike..."
                    val verifyRes = GolikeTikTokTaskRunner.verifyAndLinkTikTokAccount(
                        context = context,
                        client = client,
                        username = acc.username,
                        onProgress = { step ->
                            GolikeRunningManager.statusMap[acc.id] = step
                        }
                    )
                    if (verifyRes.isSuccess) {
                        targetAcc = verifyRes.getOrThrow()
                        withContext(Dispatchers.Main) {
                            reloadAccounts()
                            Toast.makeText(context, "Cấu hình thành công @${acc.username} vào Golike!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val err = verifyRes.exceptionOrNull()?.message ?: "Xác minh tài khoản thất bại"
                        GolikeRunningManager.statusMap[acc.id] = "Lỗi: $err"
                        GolikeRunningManager.lastErrorDetailMap[acc.id] = err
                        GolikeRunningManager.runningAccounts[acc.id] = false
                        GolikeAccountsStore.updateAccountProgress(context, "tiktok", acc.id, "Lỗi: $err", isSuccess = false, errorDetail = err)
                        return@launch
                    }
                }

                GolikeTikTokTaskRunner.runTikTokTaskLoop(
                    context = context,
                    account = targetAcc,
                    client = client,
                    onStatusChange = { newStatus ->
                        GolikeRunningManager.statusMap[acc.id] = newStatus
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
                    },
                    onJobFailed = { reason ->
                        val curErr = (GolikeRunningManager.errorCountMap[acc.id] ?: acc.errorCount) + 1
                        GolikeRunningManager.errorCountMap[acc.id] = curErr
                        GolikeRunningManager.lastErrorDetailMap[acc.id] = reason
                        GolikeAccountsStore.updateAccountProgress(context, "tiktok", acc.id, GolikeRunningManager.statusMap[acc.id].orEmpty(), isSuccess = false, errorDetail = reason)
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

                    // 2. Chờ thời gian đếm ngược an toàn
                    for (sec in countdown downTo 1) {
                        if (!GolikeRunningManager.isRunning(acc.id)) break
                        GolikeRunningManager.statusMap[acc.id] = "Làm job $jobType | Chờ nhận xu sau ${sec}s..."
                        delay(1000L)
                    }
                    if (!GolikeRunningManager.isRunning(acc.id)) break

                    // 3. Báo hoàn thành (Complete Job)
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

    // Layout chính
    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        // 1. TOP HEADER
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
            Text("Golike", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 2. THẺ THÔNG TIN TÀI KHOẢN GOLIKE (Chuẩn UX)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isLoggedIn) Modifier.clickable { showWebViewLoginDialog = true }
                        else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val initial = if (isLoggedIn && username.isNotBlank()) {
                        username.trim().first().uppercaseChar().toString()
                    } else "G"

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isLoggedIn) GolikeBrandOrange else Color(0xFF94A3B8)),
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
                            text = if (isLoggedIn && username.isNotBlank()) username else "Chưa đăng nhập Golike",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        if (isLoggedIn) {
                            val formattedBalance = try {
                                NumberFormat.getInstance(Locale("vi", "VN")).format(balance)
                            } catch (_: Exception) {
                                balance.toString()
                            }
                            Text(
                                text = "$formattedBalance đ",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GolikeBrandOrange
                            )
                        } else {
                            Text(
                                text = "Nhấn đăng nhập để bắt đầu",
                                fontSize = 12.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (!isLoggedIn) {
                        // CHƯA ĐĂNG NHẬP: ẨN HOÀN TOÀN reload/logout -> HIỂN THỊ nút "Đăng nhập"
                        Button(
                            onClick = { showWebViewLoginDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GolikeBrandOrange),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Filled.Login, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Đăng nhập", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // ĐÃ ĐĂNG NHẬP: HIỂN THỊ nút Làm mới số dư & Nút Đăng xuất màu đỏ
                        IconButton(
                            onClick = {
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
                                            Toast.makeText(context, "Đã cập nhật số dư: $newCoin đ", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Lỗi kết nối Golike: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isRefreshing = false
                                        }
                                    }
                                }
                            },
                            enabled = !isRefreshing,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(color = GolikeBrandOrange, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Làm mới", tint = GolikeBrandOrange, modifier = Modifier.size(20.dp))
                            }
                        }

                        IconButton(
                            onClick = {
                                GolikeSession.logout(context)
                                Toast.makeText(context, "Đã đăng xuất tài khoản Golike", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = "Đăng xuất", tint = DangerRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 3. THANH TAB CHỌN MẠNG XÃ HỘI (TikTok / Facebook / Instagram)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPlatform == "tiktok",
                    onClick = { selectedPlatform = "tiktok" },
                    label = { Text("TikTok ($tiktokCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TikTokBrandBlack.copy(alpha = 0.12f),
                        selectedLabelColor = TikTokBrandBlack
                    )
                )
                FilterChip(
                    selected = selectedPlatform == "facebook",
                    onClick = { selectedPlatform = "facebook" },
                    label = { Text("Facebook ($facebookCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1877F2).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFF1877F2)
                    )
                )
                FilterChip(
                    selected = selectedPlatform == "instagram",
                    onClick = { selectedPlatform = "instagram" },
                    label = { Text("Instagram ($instagramCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE1306C).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFFE1306C)
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            // 4. DANH SÁCH TÀI KHOẢN ĐANG CHẠY
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val platformName = when (selectedPlatform) {
                    "facebook" -> "Facebook"
                    "instagram" -> "Instagram"
                    else -> "TikTok"
                }
                Text("Tài khoản $platformName", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))

                // Nút Xóa thùng rác khi có nick được chọn
                if (selectedForRunIds.isNotEmpty()) {
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
                            Icon(Icons.Filled.Delete, contentDescription = "Xóa đã chọn", tint = DangerRed, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }

                // Nút Đồng bộ nick từ Golike & Thiết bị
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val client = GolikeAccountsStore.getApiClient(context)
                            syncLinkedAccountsFromApi(context, client, selectedPlatform)
                            withContext(Dispatchers.Main) {
                                reloadAccounts()
                                Toast.makeText(context, "Đã đồng bộ tài khoản $platformName!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GolikeBrandOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Đồng bộ", tint = GolikeBrandOrange, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.width(6.dp))

                // Nút Thêm tài khoản (+) ở góc phải
                IconButton(
                    onClick = { showAddAccountSheet = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GolikeBrandOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Thêm tài khoản", tint = GolikeBrandOrange, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (currentAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Chưa có tài khoản nào được thêm.", color = TextSecondary, fontSize = 13.5.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val client = GolikeAccountsStore.getApiClient(context)
                                    syncLinkedAccountsFromApi(context, client, selectedPlatform)
                                    withContext(Dispatchers.Main) {
                                        reloadAccounts()
                                        Toast.makeText(context, "Đã đồng bộ tài khoản từ Golike & thiết bị!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "⟳ Đồng bộ nick từ Golike",
                                color = GolikeBrandOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    currentAccounts.forEach { acc ->
                        val isChecked = acc.id in selectedForRunIds
                        val isRunning = GolikeRunningManager.isRunning(acc.id)
                        val status = GolikeRunningManager.statusMap[acc.id] ?: acc.lastStatus
                        val success = GolikeRunningManager.successCountMap[acc.id] ?: acc.successCount
                        val error = GolikeRunningManager.errorCountMap[acc.id] ?: acc.errorCount
                        val isError = status.contains("Lỗi", ignoreCase = true) || error > 0

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isChecked) 1.5.dp else 1.dp,
                                color = if (isChecked) GolikeBrandOrange else Color(0xFFEEF1F5)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // DÒNG 1: Checkbox, Avatar, Username, Badge Live, Badge Đã liên kết, Nút Cấu hình, Nút Play/Stop
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedForRunIds = if (checked) selectedForRunIds + acc.id else selectedForRunIds - acc.id
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = GolikeBrandOrange),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))

                                    // Avatar với AsyncImage hoặc chữ cái đầu
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (selectedPlatform) {
                                                    "facebook" -> Color(0xFF1877F2)
                                                    "instagram" -> Color(0xFFE1306C)
                                                    else -> TikTokBrandBlack
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (acc.avatar.isNotBlank()) {
                                            AsyncImage(
                                                model = acc.avatar,
                                                contentDescription = "Avatar",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = acc.username.trim().removePrefix("@").take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.username.ifBlank { acc.id },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Badge Live / Die
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (acc.isLive) SuccessGreen.copy(alpha = 0.12f) else DangerRed.copy(alpha = 0.12f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (acc.isLive) "Live" else "Die / Checkpoint",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (acc.isLive) SuccessGreen else DangerRed
                                                )
                                            }

                                            // Badge Đã liên kết Golike / Chưa liên kết
                                            if (acc.isGolikeLinked) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(GolikeBrandOrange.copy(alpha = 0.12f))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Đã liên kết Golike",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GolikeBrandOrange
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFFFEF3C7))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Chưa liên kết Golike",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFD97706)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Nút Cấu hình nhanh cho nick chưa liên kết
                                    if (!acc.isGolikeLinked && selectedPlatform == "tiktok") {
                                        OutlinedButton(
                                            onClick = {
                                                if (!isLoggedIn) {
                                                    Toast.makeText(context, "Vui lòng đăng nhập Golike trước", Toast.LENGTH_SHORT).show()
                                                    showWebViewLoginDialog = true
                                                    return@OutlinedButton
                                                }
                                                scope.launch(Dispatchers.IO) {
                                                    val client = GolikeAccountsStore.getApiClient(context)
                                                    GolikeRunningManager.statusMap[acc.id] = "Đang cấu hình nick @${acc.username}..."
                                                    val result = GolikeTikTokTaskRunner.verifyAndLinkTikTokAccount(
                                                        context = context,
                                                        client = client,
                                                        username = acc.username,
                                                        onProgress = { GolikeRunningManager.statusMap[acc.id] = it }
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        if (result.isSuccess) {
                                                            reloadAccounts()
                                                            Toast.makeText(context, "Đã liên kết @${acc.username} thành công!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            val msg = result.exceptionOrNull()?.message ?: "Lỗi xác minh"
                                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, tint = GolikeBrandOrange, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Cấu hình", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GolikeBrandOrange)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }

                                    // Nút Play / Stop độc lập từng nick
                                    IconButton(
                                        onClick = {
                                            if (isRunning) {
                                                GolikeRunningManager.stop(acc.id)
                                                Toast.makeText(context, "Đã dừng tài khoản ${acc.username}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                startAccountTask(acc)
                                                Toast.makeText(context, "Bắt đầu chạy Golike cho ${acc.username}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(if (isRunning) DangerRed else GolikeBrandOrange),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isRunning) {
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

                                Spacer(Modifier.height(8.dp))

                                // DÒNG 2: Text tiến trình realtime
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    color = if (isError) DangerRed else if (isRunning) Color(0xFF1E40AF) else TextSecondary,
                                    fontWeight = if (isRunning) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(6.dp))

                                // DÒNG 3: Thống kê hoàn thành, lỗi, proxy, icon cảnh báo đỏ (!)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Hoàn thành
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFE8F5E9))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "✓ Hoàn thành: $success",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }

                                        // Lỗi
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFFFEBEE))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "• Lỗi: $error",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DangerRed
                                            )
                                        }

                                        // Proxy nếu có
                                        if (acc.proxy.isNotBlank()) {
                                            val pDisplay = acc.proxy.take(12) + "..."
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFF5F5F5))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "Proxy: $pDisplay",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF616161)
                                                )
                                            }
                                        }
                                    }

                                    // Icon cảnh báo lỗi đỏ (!) -> mở BottomSheet chi tiết lỗi
                                    IconButton(
                                        onClick = { selectedErrorAccount = acc },
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
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(90.dp))
        }

        // 5. THANH ĐIỀU KHIỂN DƯỚI CÙNG (BOTTOM BAR)
        val allIds = remember(currentAccounts) { currentAccounts.map { it.id }.toSet() }
        val isAllSelected = currentAccounts.isNotEmpty() && allIds.isNotEmpty() && allIds.all { it in selectedForRunIds }
        val isAnyRunning = remember(currentAccounts) { GolikeRunningManager.isAnyRunning(currentAccounts) }

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
                // Nút "Cấu hình" (icon bánh răng)
                OutlinedButton(
                    onClick = { showConfigSheet = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GolikeBrandOrange),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GolikeBrandOrange.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = GolikeBrandOrange)
                    Spacer(Modifier.width(6.dp))
                    Text("Cấu hình", fontSize = 13.sp, color = GolikeBrandOrange)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Checkbox "Tất cả"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedForRunIds = if (isAllSelected) emptySet() else allIds
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Checkbox(
                            checked = isAllSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = GolikeBrandOrange),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Tất cả", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    // Nút Xóa thùng rác đỏ khi có nick được chọn
                    if (selectedForRunIds.isNotEmpty()) {
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
                                Icon(Icons.Filled.Delete, contentDescription = "Xóa tài khoản đã chọn", tint = DangerRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Nút Chạy tất cả / Dừng tất cả to tròn
                    IconButton(
                        onClick = {
                            if (isAnyRunning) {
                                GolikeRunningManager.stopAll(currentAccounts)
                                Toast.makeText(context, "Đã dừng tất cả tài khoản Golike", Toast.LENGTH_SHORT).show()
                            } else {
                                val targets = if (selectedForRunIds.isNotEmpty()) {
                                    currentAccounts.filter { it.id in selectedForRunIds }
                                } else {
                                    currentAccounts
                                }
                                if (targets.isEmpty()) {
                                    Toast.makeText(context, "Chưa có tài khoản nào để chạy!", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                targets.forEach { acc ->
                                    startAccountTask(acc)
                                }
                                Toast.makeText(context, "Bắt đầu chạy ${targets.size} tài khoản Golike", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isAnyRunning) DangerRed else GolikeBrandOrange),
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
}
