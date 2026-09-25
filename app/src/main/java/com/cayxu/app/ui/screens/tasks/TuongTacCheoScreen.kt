package com.cayxu.app.ui.screens.tasks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.data.local.TtcAccount
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.data.local.TtcRunConfig
import com.cayxu.app.data.local.TtcRunConfigStore
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.facebook.FacebookMediaEngine
import com.cayxu.app.facebook.FacebookPageEngine
import com.cayxu.app.tuongtaccheo.TuongTacCheoApiClient
import com.cayxu.app.ui.screens.xsmm.FacebookAccountDetailSheet
import com.cayxu.app.ui.screens.xsmm.FacebookLoginBottomSheet
import com.cayxu.app.ui.screens.xsmm.FacebookPageDetailSheet
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Màu chủ đạo của TTC: Màu xám thanh lịch hiện đại
private val TtcPrimary = Color(0xFF475569) // Slate 600
private val TtcDark = Color(0xFF334155)    // Slate 700
private val TtcLight = Color(0xFFF1F5F9)   // Slate 100
private val DangerRed = Color(0xFFEF4444)
private val FbBlue = Color(0xFF1877F2)

/**
 * Màn hình Tương Tác Chéo (TTC):
 *   - Màu chủ đạo: Màu xám (TtcPrimary)
 *   - 2 Thẻ ở trên: Acc TTC / Facebook
 *   - Footer ở dưới: 2 nút "Cấu hình" (trượt từ dưới lên) và "Chạy"
 *   - Tab Facebook: Nút "+" mở FacebookLoginBottomSheet, sao chép toàn bộ logic FB từ XSMM (Mẹ + Page 615, avatar, Live/Die, Info, Warning, Run/Stop)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuongTacCheoScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tab đang chọn: 0 = Acc TTC, 1 = Facebook
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dữ liệu tài khoản
    var ttcAccounts by remember { mutableStateOf(TtcAccountsStore.getAccounts(context)) }
    var fbAccounts by remember { mutableStateOf(FacebookAccountsStore.getAccounts(context, forceReload = true)) }

    // Quản lý selection
    var selectedTtcUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFbUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Cấu hình TTC
    var ttcConfig by remember { mutableStateOf(TtcRunConfigStore.getConfig(context)) }
    var showConfigSheet by remember { mutableStateOf(false) }

    // BottomSheet thêm acc TTC (trượt từ dưới lên)
    var showAddTtcSheet by remember { mutableStateOf(false) }

    // BottomSheet thêm Facebook (+)
    var showFacebookLoginSheet by remember { mutableStateOf(false) }

    // Detail & Error BottomSheets
    var selectedFbDetailAccount by remember { mutableStateOf<FacebookAccount?>(null) }
    var selectedFbDetailPage by remember { mutableStateOf<Pair<FacebookAccount, FacebookPageItem>?>(null) }
    var selectedErrorDetailAccount by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }

    // Avatar cache & 615 dynamic mapping
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }
    var liveFbAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageUids by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var livePageAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Quản lý trạng thái chạy TTC Facebook
    val runningTtcUids = remember { mutableStateListOf<String>() }
    val ttcStatusMap = remember { mutableStateMapOf<String, String>() }
    val ttcSuccessCountMap = remember { mutableStateMapOf<String, Int>() }
    val ttcErrorCountMap = remember { mutableStateMapOf<String, Int>() }
    val ttcErrorDetailMap = remember { mutableStateMapOf<String, String>() }
    val activeRunJobs = remember { mutableStateMapOf<String, Job>() }

    // Quản lý trạng thái hiển thị và chạy cho từng nick TTC
    val runningTtcAccounts = remember { mutableStateListOf<String>() }
    val ttcAccountStatusMap = remember { mutableStateMapOf<String, String>() }

    var targetFbAvatarChangeUid by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    fun reloadData() {
        ttcAccounts = TtcAccountsStore.getAccounts(context)
        fbAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
    }

    // Tự động kiểm tra và cập nhật Avatar thật & UID 615 cho Facebook Page
    LaunchedEffect(fbAccounts.size) {
        fbAccounts.forEach { acc ->
            val token = acc.bio.trim()
            val curAv = liveFbAvatars[acc.uid] ?: acc.avatar
            val needAvatarFix = curAv.isBlank() || curAv.contains("picture?type=large") || curAv.contains("84628273_176159830277856")
            if (needAvatarFix && token.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val proxyParts = acc.phone.ifBlank { null }?.split(":")
                        val proxyHost = proxyParts?.getOrNull(0)
                        val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                        val mediaEngine = FacebookMediaEngine(
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

            val proxyParts = acc.phone.ifBlank { null }?.split(":")
            val proxyHost = proxyParts?.getOrNull(0)
            val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
            acc.pages.forEach { p ->
                val curUid = livePageUids[p.pageId] ?: p.displayUid
                if (!curUid.startsWith("615")) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val pageEngine = FacebookPageEngine(
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
                                FacebookAccountsStore.updatePageUid(context, acc.uid, p.pageId, uid615)
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

    // Bộ chọn ảnh đổi avatar cho FB
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
                    val acc = FacebookAccountsStore.getAccounts(context).firstOrNull { it.uid == uid }
                    if (acc == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Không tìm thấy tài khoản Facebook $uid", Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    val token = acc.bio.ifBlank { "" }
                    if (token.isBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Tài khoản cần có Access Token để đổi Avatar", Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang đổi ảnh đại diện Facebook...", Toast.LENGTH_SHORT).show()
                    }
                    val proxy = acc.phone.ifBlank { null }
                    val proxyParts = proxy?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                    val mediaEngine = FacebookMediaEngine(
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
                        FacebookAccountsStore.updateAccount(context, updatedAcc)
                        withContext(Dispatchers.Main) {
                            liveFbAvatars = liveFbAvatars + (acc.uid to finalAvatar)
                            avatarVersion = System.currentTimeMillis()
                            fbAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                            Toast.makeText(context, "Đổi avatar Facebook thành công!", Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Lỗi đổi avatar: ${result.message}", Toast.LENGTH_LONG).show()
                            isUploadingAvatar = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi đổi avatar FB: ${e.message}", Toast.LENGTH_LONG).show()
                        isUploadingAvatar = false
                    }
                }
            }
        }
    }

    // Logic chạy TTC cho từng nick FB hoặc acc TTC
    fun stopTtcAccount(key: String) {
        val job = activeRunJobs[key]
        job?.cancel()
        activeRunJobs.remove(key)
        runningTtcUids.remove(key)
        runningTtcAccounts.remove(key)
        ttcAccountStatusMap[key] = "Đã dừng"
        ttcStatusMap[key] = "Đã dừng"

        // Đồng bộ dọn dẹp các key khác chạy chung job này (Page UID, Nick mẹ UID, TTC username)
        if (job != null) {
            val siblingKeys = activeRunJobs.filter { it.value == job }.keys.toList()
            siblingKeys.forEach { k ->
                activeRunJobs.remove(k)
                runningTtcUids.remove(k)
                runningTtcAccounts.remove(k)
                ttcAccountStatusMap[k] = "Đã dừng"
                ttcStatusMap[k] = "Đã dừng"
            }
        }

        if (runningTtcAccounts.isEmpty() && runningTtcUids.isEmpty()) {
            activeRunJobs.clear()
            runningTtcAccounts.clear()
            runningTtcUids.clear()
        }
    }

    fun startTtcAccount(inputUid: String? = null, targetTtcUsername: String? = null) {
        if (ttcAccounts.isEmpty()) {
            Toast.makeText(context, "Chưa có tài khoản TTC nào để lấy nhiệm vụ!", Toast.LENGTH_SHORT).show()
            return
        }
        if (fbAccounts.isEmpty()) {
            Toast.makeText(context, "Chưa có tài khoản Facebook nào để tương tác!", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Xác định tài khoản TTC
        val activeTtcAccount = if (!targetTtcUsername.isNullOrBlank()) {
            ttcAccounts.firstOrNull { it.username == targetTtcUsername } ?: ttcAccounts.firstOrNull()
        } else if (selectedTtcUsernames.isNotEmpty()) {
            ttcAccounts.firstOrNull { it.username in selectedTtcUsernames && it.username !in runningTtcAccounts }
                ?: ttcAccounts.firstOrNull { it.username in selectedTtcUsernames }
                ?: ttcAccounts.firstOrNull()
        } else {
            ttcAccounts.firstOrNull { it.isLive && it.username !in runningTtcAccounts }
                ?: ttcAccounts.firstOrNull { it.username !in runningTtcAccounts }
                ?: ttcAccounts.firstOrNull()
        }

        if (activeTtcAccount == null || activeTtcAccount.token.isBlank()) {
            Toast.makeText(context, "Tài khoản TTC chưa có token hợp lệ!", Toast.LENGTH_SHORT).show()
            return
        }
        val ttcUser = activeTtcAccount.username
        if (ttcUser in runningTtcAccounts) return

        // 2. Tự động ghép nối Facebook (không bắt buộc người dùng phải tích chọn checkbox)
        val ttcIndex = ttcAccounts.indexOfFirst { it.username == ttcUser }.coerceAtLeast(0)

        // Tìm nick Facebook mẹ tương ứng (hoặc nick chứa Page có UID = inputUid)
        val parentFbAccount = if (!inputUid.isNullOrBlank()) {
            fbAccounts.firstOrNull { it.uid == inputUid }
                ?: fbAccounts.firstOrNull { acc ->
                    acc.pages.any { p ->
                        val pDisplay = livePageUids[p.pageId] ?: p.displayUid
                        val effUid = (p.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                            ?: pDisplay.takeIf { it.isNotBlank() && it.startsWith("615") }
                            ?: p.additionalProfileId.takeIf { it.isNotBlank() }
                            ?: pDisplay.takeIf { it.isNotBlank() }
                            ?: p.pageId).trim()
                        effUid.equals(inputUid, ignoreCase = true) || p.pageId.equals(inputUid, ignoreCase = true)
                    }
                }
                ?: fbAccounts.firstOrNull { it.isLive }
                ?: fbAccounts.firstOrNull()
        } else if (selectedFbUids.isNotEmpty()) {
            val selected = fbAccounts.filter { it.uid in selectedFbUids }
            selected.getOrNull(ttcIndex % selected.size) ?: selected.first()
        } else {
            val liveFbs = fbAccounts.filter { it.isLive }.ifEmpty { fbAccounts }
            liveFbs.getOrNull(ttcIndex % liveFbs.size) ?: liveFbs.first()
        }

        if (parentFbAccount == null) {
            Toast.makeText(context, "Chưa có tài khoản Facebook sẵn sàng!", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. Tự động đọc Cấu hình người dùng (Profile / Page)
        // Nếu inputUid trỏ đích danh tới 1 Page cụ thể thì ưu tiên chạy bằng Page đó
        val matchedPageInParent = if (!inputUid.isNullOrBlank()) {
            parentFbAccount.pages.firstOrNull { p ->
                val pDisplay = livePageUids[p.pageId] ?: p.displayUid
                val effUid = (p.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                    ?: pDisplay.takeIf { it.isNotBlank() && it.startsWith("615") }
                    ?: p.additionalProfileId.takeIf { it.isNotBlank() }
                    ?: pDisplay.takeIf { it.isNotBlank() }
                    ?: p.pageId).trim()
                effUid.equals(inputUid, ignoreCase = true) || p.pageId.equals(inputUid, ignoreCase = true)
            }
        } else null

        val usePage = matchedPageInParent != null || ttcConfig.pairTargetType.equals("page", ignoreCase = true)

        val targetPageItem: FacebookPageItem? = if (usePage) {
            matchedPageInParent
                ?: (if (parentFbAccount.pages.isNotEmpty()) {
                    parentFbAccount.pages.getOrNull(ttcIndex % parentFbAccount.pages.size) ?: parentFbAccount.pages.firstOrNull()
                } else {
                    val allPages = fbAccounts.flatMap { it.pages }
                    allPages.getOrNull(ttcIndex % allPages.size) ?: allPages.firstOrNull()
                })
        } else {
            null
        }

        if (usePage && targetPageItem == null) {
            Toast.makeText(context, "Tài khoản ${parentFbAccount.name} không có Page nào! Vui lòng thêm Page hoặc chọn [Dùng Profile]", Toast.LENGTH_LONG).show()
            return
        }

        // Xác định chính xác runUid và Token tương ứng:
        // - Page: lấy UID Page con (615...) và Page Token
        // - Profile: lấy UID Nick cá nhân mẹ và User Token
        val runUid: String
        val runToken: String
        val targetTypeStr: String
        val targetLabel: String

        if (usePage && targetPageItem != null) {
            val pageDisplayUid = livePageUids[targetPageItem.pageId] ?: targetPageItem.displayUid
            val effectivePageUid = (targetPageItem.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                ?: pageDisplayUid.takeIf { it.isNotBlank() && it.startsWith("615") }
                ?: targetPageItem.additionalProfileId.takeIf { it.isNotBlank() }
                ?: pageDisplayUid.takeIf { it.isNotBlank() }
                ?: targetPageItem.pageId).trim()

            runUid = effectivePageUid
            runToken = targetPageItem.pageToken.ifBlank { parentFbAccount.bio.orEmpty() }
            targetTypeStr = "page"
            targetLabel = "Page: ${targetPageItem.pageName.ifBlank { runUid }}"
        } else {
            runUid = parentFbAccount.uid.trim()
            runToken = (parentFbAccount.bio ?: "").trim()
            targetTypeStr = "nick"
            targetLabel = "Nick: ${parentFbAccount.name.ifBlank { runUid }}"
        }

        val cleanToken = runToken.removePrefix("OAuth ").removePrefix("Bearer ").trim()

        val proxyParts = (parentFbAccount.phone ?: "").trim().split(":")
        val proxyHost = proxyParts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val proxyPort = proxyParts.getOrNull(1)?.toIntOrNull()

        // Danh sách các key đại diện cho luồng chạy này trên UI (Nếu chạy Page thì CHỈ HIỂN THỊ TRÊN PAGE CON, TUYỆT ĐỐI KHÔNG KHỞI ĐỘNG PROFILE MẸ)
        val runningKeys = if (usePage && targetPageItem != null) {
            mutableListOf(runUid, targetPageItem.pageId)
        } else {
            mutableListOf(runUid)
        }
        val distinctRunningKeys = runningKeys.distinct()

        // Kiểm tra xem luồng này đã đang chạy chưa
        if (distinctRunningKeys.any { it in runningTtcUids }) return

        // 4. Kích hoạt trạng thái chạy ngay lập tức (Hiển thị ngay trên UI)
        if (ttcUser !in runningTtcAccounts) {
            runningTtcAccounts.add(ttcUser)
        }
        distinctRunningKeys.forEach { k ->
            if (k !in runningTtcUids) runningTtcUids.add(k)
        }

        // Bắt đầu cập nhật trạng thái ngay lập tức khi bấm Chạy
        val initStatus = "Đang kết nối Facebook ($targetLabel)..."
        ttcAccountStatusMap[ttcUser] = initStatus
        distinctRunningKeys.forEach { k -> ttcStatusMap[k] = initStatus }

        val job = scope.launch(Dispatchers.IO) {
            try {
                val ttcClient = TuongTacCheoApiClient(
                    tokenTTC = activeTtcAccount.token,
                    sessionCookie = activeTtcAccount.cookie.orEmpty(),
                    proxyStr = activeTtcAccount.proxy.ifBlank { null }
                )

                // Luôn làm mới phiên đăng nhập TTC bằng Token để CookieJar có session mới nhất
                if (activeTtcAccount.token.isNotBlank()) {
                    try {
                        val logged = ttcClient.loginWithToken(activeTtcAccount.token)
                        val freshCookie = logged.cookie
                        if (!freshCookie.isNullOrBlank() && freshCookie != activeTtcAccount.cookie) {
                            val updatedAcc = activeTtcAccount.copy(cookie = freshCookie, coins = logged.sodu)
                            TtcAccountsStore.addAccount(context, updatedAcc)
                        }
                    } catch (_: Exception) {}
                    delay(1200L)
                }

                // BƯỚC 1 & BƯỚC 2: TỰ ĐỘNG THÊM NICK/PAGE VÀO TTC (NẾU CHƯA CÓ) VÀ ĐẶT NICK CHẠY
                val setNickRes = try {
                    ttcClient.autoPrepareAndSetNick(runUid, "fb") { stepMsg ->
                        scope.launch(Dispatchers.Main) {
                            ttcAccountStatusMap[ttcUser] = stepMsg
                            distinctRunningKeys.forEach { k -> ttcStatusMap[k] = stepMsg }
                        }
                    }
                } catch (e: Exception) {
                    com.cayxu.app.tuongtaccheo.TTCDatNickResult(false, 0, "Lỗi kết nối TTC: ${e.message}", "")
                }

                // Nếu cấu hình chưa xong hoặc lỗi thì PHẢI DỪNG LẠI, TUYỆT ĐỐI KHÔNG tự động chuyển sang nhận job!
                if (!setNickRes.isSuccess) {
                    val err = setNickRes.message.ifBlank { "Lỗi TTC: Đặt $targetTypeStr [$runUid] thất bại" }
                    val rawResp = setNickRes.rawResponse.ifBlank { err }
                    withContext(Dispatchers.Main) {
                        ttcAccountStatusMap[ttcUser] = err
                        distinctRunningKeys.forEach { k ->
                            ttcStatusMap[k] = err
                            ttcErrorDetailMap[k] = rawResp
                            ttcErrorCountMap[k] = (ttcErrorCountMap[k] ?: 0) + 1
                        }
                        ttcErrorDetailMap[ttcUser] = rawResp

                        // Dừng ngay lập tức trạng thái chạy
                        runningTtcAccounts.remove(ttcUser)
                        distinctRunningKeys.forEach { runningTtcUids.remove(it) }
                        activeRunJobs.remove(ttcUser)
                        distinctRunningKeys.forEach { activeRunJobs.remove(it) }
                    }
                    return@launch
                }

                // BƯỚC 3: Nhận được phản hồi thành công (status = 1) -> CHUYỂN SANG BƯỚC NHẬN JOB
                withContext(Dispatchers.Main) {
                    val okText = "Đặt $targetTypeStr [$runUid] thành công! Đang nhận job..."
                    ttcAccountStatusMap[ttcUser] = okText
                    distinctRunningKeys.forEach { k -> ttcStatusMap[k] = okText }
                }
                delay(600L)

                var successCount = ttcSuccessCountMap[runUid] ?: 0
                var errorCount = ttcErrorCountMap[runUid] ?: 0
                var consecutiveErrors = 0

                val activeTypes = ttcConfig.taskTypes.filter { it.isNotBlank() }.ifEmpty { listOf("like") }
                var typeIndex = 0

                withContext(Dispatchers.Main) {
                    val readyMsg = "Sẵn sàng nhận job..."
                    ttcAccountStatusMap[ttcUser] = readyMsg
                    distinctRunningKeys.forEach { k -> ttcStatusMap[k] = readyMsg }
                }

                while (isActive && distinctRunningKeys.any { it in runningTtcUids } && ttcUser in runningTtcAccounts) {
                    val delaySec = ttcConfig.delaySeconds.coerceAtLeast(3)
                    val delayTime = delaySec * 1000L
                    for (sec in delaySec downTo 1) {
                        if (!isActive || distinctRunningKeys.none { it in runningTtcUids } || ttcUser !in runningTtcAccounts) break
                        withContext(Dispatchers.Main) {
                            ttcAccountStatusMap[ttcUser] = "Delay nghỉ ${sec}s..."
                            distinctRunningKeys.forEach { k -> ttcStatusMap[k] = "Delay nghỉ ${sec}s..." }
                        }
                        delay(1000L)
                    }
                    if (!isActive || distinctRunningKeys.none { it in runningTtcUids } || ttcUser !in runningTtcAccounts) break

                    val currentKey = activeTypes[typeIndex % activeTypes.size]
                    val currentJobType = com.cayxu.app.tuongtaccheo.TTCJobType.fromKey(currentKey)

                    withContext(Dispatchers.Main) {
                        val fetchMsg = "Đang nhận job ${currentJobType.displayName}..."
                        ttcAccountStatusMap[ttcUser] = fetchMsg
                        distinctRunningKeys.forEach { k -> ttcStatusMap[k] = fetchMsg }
                    }
                    // Lấy job từ TTC
                    val jobs = try {
                        ttcClient.getJobs(currentJobType)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    if (jobs.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            val emptyMsg = "Hết job ${currentJobType.displayName}, đổi..."
                            ttcAccountStatusMap[ttcUser] = emptyMsg
                            distinctRunningKeys.forEach { k -> ttcStatusMap[k] = emptyMsg }
                        }
                        typeIndex++
                        delay(3000L)
                        continue
                    }

                    for (j in jobs) {
                        if (!isActive || distinctRunningKeys.none { it in runningTtcUids } || ttcUser !in runningTtcAccounts) break
                        val target = j.idpost?.takeIf { it.isNotBlank() } ?: j.idfb?.takeIf { it.isNotBlank() } ?: j.link.orEmpty()
                        withContext(Dispatchers.Main) {
                            val doingMsg = "Đang làm nhiệm vụ..."
                            ttcAccountStatusMap[ttcUser] = doingMsg
                            distinctRunningKeys.forEach { k -> ttcStatusMap[k] = "Làm [${currentJobType.displayName}]: ${target.take(12)}..." }
                        }

                        // Thao tác tương tác bằng Facebook Engine (Page615 hoặc Profile)
                        var fbOk = true
                        var fbErr: String? = null

                        if (usePage && targetPageItem != null) {
                            val pageEngine = com.cayxu.app.facebook.Page615TuongTacEngine(
                                pageToken = cleanToken,
                                pageId615 = runUid,
                                proxyHost = proxyHost,
                                proxyPort = proxyPort
                            )
                            val res = when (currentJobType) {
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_COMMENT -> {
                                    val cmtText = j.cmt.orEmpty()
                                    if (cmtText.isNotBlank()) pageEngine.commentPost(target, cmtText)
                                    else com.cayxu.app.facebook.Page615TuongTacEngine.InteractionResult(false, target, "COMMENT", null, "Nội dung comment trống")
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_FOLLOW,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SUB_VIP -> {
                                    pageEngine.followTarget(target)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_PAGE -> {
                                    pageEngine.likeOtherPage(target)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_MEMBER,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_JOIN_GROUP -> {
                                    pageEngine.joinGroup(target)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SHARE -> {
                                    pageEngine.sharePost(target, message = null)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SHARE_ND,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SHARE_CONTENT -> {
                                    val shareMsg = j.cmt?.takeIf { it.isNotBlank() } ?: "Hay quá!"
                                    pageEngine.sharePost(target, message = shareMsg)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_REVIEW,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_REVIEW_PAGE -> {
                                    val reviewText = j.cmt?.takeIf { it.isNotBlank() } ?: "Dịch vụ rất tuyệt vời!"
                                    pageEngine.reviewOtherPage(target, reviewText = reviewText, recommendationType = "positive")
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_LIKE,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_LIKE_VIP -> {
                                    pageEngine.reactPost(target, com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.LIKE)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX_VIP,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_REACTION,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX_CMT,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX_COMMENT -> {
                                    val rxType = when (j.loaicx?.uppercase()) {
                                        "LOVE", "TYM" -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.LOVE
                                        "CARE", "THUONGTHUONG" -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.CARE
                                        "HAHA" -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.HAHA
                                        "WOW" -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.WOW
                                        "SAD" -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.SAD
                                        "ANGRY" -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.ANGRY
                                        else -> com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.LIKE
                                    }
                                    pageEngine.reactPost(target, rxType)
                                }
                                else -> {
                                    pageEngine.reactPost(target, com.cayxu.app.facebook.Page615TuongTacEngine.ReactionType.LIKE)
                                }
                            }
                            fbOk = res.isSuccess
                            if (!res.isSuccess) fbErr = res.message ?: res.rawResponse
                        } else {
                            val engine = com.cayxu.app.facebook.FacebookTuongTacEngine(
                                accessToken = cleanToken,
                                userId = runUid,
                                proxyHost = proxyHost,
                                proxyPort = proxyPort
                            )
                            val res = when (currentJobType) {
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_COMMENT -> {
                                    val cmtText = j.cmt.orEmpty()
                                    if (cmtText.isNotBlank()) engine.comment(target, cmtText)
                                    else com.cayxu.app.facebook.FacebookTuongTacEngine.EngineResult(isSuccess = false, action = "COMMENT", targetId = target, message = "Nội dung comment trống")
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_FOLLOW,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SUB_VIP -> {
                                    engine.follow(target)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_PAGE -> {
                                    engine.likePage(target)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_MEMBER,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_JOIN_GROUP -> {
                                    engine.joinGroup(target)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SHARE -> {
                                    engine.share(target, message = null)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SHARE_ND,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_SHARE_CONTENT -> {
                                    val shareMsg = j.cmt?.takeIf { it.isNotBlank() } ?: "Hay quá!"
                                    engine.share(target, message = shareMsg)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_REVIEW,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_REVIEW_PAGE -> {
                                    val reviewText = j.cmt?.takeIf { it.isNotBlank() } ?: "Dịch vụ rất tuyệt vời!"
                                    engine.reviewPage(target, isPositive = true, reviewText = reviewText)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_LIKE,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_LIKE_VIP -> {
                                    engine.react(target, com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LIKE)
                                }
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX_VIP,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_REACTION,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX_CMT,
                                com.cayxu.app.tuongtaccheo.TTCJobType.FB_CX_COMMENT -> {
                                    val rxType = when (j.loaicx?.uppercase()) {
                                        "LOVE", "TYM" -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LOVE
                                        "CARE", "THUONGTHUONG" -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.CARE
                                        "HAHA" -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.HAHA
                                        "WOW" -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.WOW
                                        "SAD" -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.SAD
                                        "ANGRY" -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.ANGRY
                                        else -> com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LIKE
                                    }
                                    engine.react(target, rxType)
                                }
                                else -> {
                                    engine.react(target, com.cayxu.app.facebook.FacebookTuongTacEngine.ReactionType.LIKE)
                                }
                            }
                            fbOk = res.isSuccess
                            if (!res.isSuccess) fbErr = res.message ?: res.rawResponse
                        }

                        if (!fbOk) {
                            errorCount++
                            consecutiveErrors++
                            val err = fbErr ?: "Tương tác Facebook thất bại"
                            val limitStr = if (ttcConfig.failJobCountLimit <= 0 || ttcConfig.failJobCountLimit == -1) "∞" else "${ttcConfig.failJobCountLimit}"
                            withContext(Dispatchers.Main) {
                                distinctRunningKeys.forEach { k ->
                                    ttcErrorCountMap[k] = errorCount
                                    ttcErrorDetailMap[k] = err
                                    ttcStatusMap[k] = "Lỗi FB ($consecutiveErrors/$limitStr)"
                                }
                                ttcAccountStatusMap[ttcUser] = "Lỗi FB ($consecutiveErrors/$limitStr)"
                                ttcErrorDetailMap[ttcUser] = err
                            }
                            if (consecutiveErrors >= 3) {
                                val accName = if (usePage && targetPageItem != null) "Page: ${targetPageItem.pageName.ifBlank { runUid }}" else parentFbAccount.name.ifBlank { runUid }
                                com.cayxu.app.worker.AppAlertNotifier.notifyAccountError(
                                    context = context,
                                    platform = "Tương Tác Chéo",
                                    accountName = accName,
                                    accountUid = runUid,
                                    consecutiveErrors = consecutiveErrors,
                                    errorDetail = err
                                )
                            }
                            if (ttcConfig.failJobCountLimit > 0 && consecutiveErrors >= ttcConfig.failJobCountLimit) {
                                val stopMsg = "Dừng do lỗi FB liên tiếp $consecutiveErrors lần"
                                withContext(Dispatchers.Main) {
                                    distinctRunningKeys.forEach { k -> ttcStatusMap[k] = stopMsg }
                                    ttcAccountStatusMap[ttcUser] = stopMsg
                                }
                                val accName = if (usePage && targetPageItem != null) "Page: ${targetPageItem.pageName.ifBlank { runUid }}" else parentFbAccount.name.ifBlank { runUid }
                                com.cayxu.app.worker.AppAlertNotifier.notifyAccountError(
                                    context = context,
                                    platform = "Tương Tác Chéo",
                                    accountName = accName,
                                    accountUid = runUid,
                                    consecutiveErrors = consecutiveErrors,
                                    errorDetail = "Đã dừng chạy: Đạt giới hạn $consecutiveErrors job lỗi liên tiếp"
                                )
                                break
                            }
                            continue
                        }

                        // Đợi trước khi nhận xu
                        delay(3000L)
                        val claimRes = try {
                            ttcClient.claimReward(j.id, currentJobType)
                        } catch (e: Exception) {
                            null
                        }

                        if (claimRes != null && claimRes.isSuccess) {
                            successCount++
                            consecutiveErrors = 0
                            withContext(Dispatchers.Main) {
                                val doneMsg = "Đã xong ${currentJobType.displayName} (+${claimRes.xuThem} xu)"
                                ttcAccountStatusMap[ttcUser] = doneMsg
                                distinctRunningKeys.forEach { k ->
                                    ttcSuccessCountMap[k] = successCount
                                    ttcStatusMap[k] = "$doneMsg (Tổng $successCount)"
                                }
                                val idx = ttcAccounts.indexOfFirst { it.username == ttcUser }
                                if (idx >= 0 && claimRes.sodu > 0) {
                                    val updatedAcc = ttcAccounts[idx].copy(coins = claimRes.sodu)
                                    TtcAccountsStore.addAccount(context, updatedAcc)
                                    reloadData()
                                }
                            }
                        } else {
                            errorCount++
                            consecutiveErrors++
                            val err = claimRes?.message ?: "Lỗi nhận xu từ TTC"
                            val limitStr = if (ttcConfig.failJobCountLimit <= 0 || ttcConfig.failJobCountLimit == -1) "∞" else "${ttcConfig.failJobCountLimit}"
                            withContext(Dispatchers.Main) {
                                val errStatus = "Lỗi nhận xu ($consecutiveErrors/$limitStr)"
                                distinctRunningKeys.forEach { k ->
                                    ttcErrorCountMap[k] = errorCount
                                    ttcErrorDetailMap[k] = err
                                    ttcStatusMap[k] = errStatus
                                }
                                ttcAccountStatusMap[ttcUser] = errStatus
                                ttcErrorDetailMap[ttcUser] = err
                            }
                            if (consecutiveErrors >= 3) {
                                val accName = if (usePage && targetPageItem != null) "Page: ${targetPageItem.pageName.ifBlank { runUid }}" else parentFbAccount.name.ifBlank { runUid }
                                com.cayxu.app.worker.AppAlertNotifier.notifyAccountError(
                                    context = context,
                                    platform = "Tương Tác Chéo",
                                    accountName = accName,
                                    accountUid = runUid,
                                    consecutiveErrors = consecutiveErrors,
                                    errorDetail = "Lỗi nhận xu: $err"
                                )
                            }
                            if (ttcConfig.failJobCountLimit > 0 && consecutiveErrors >= ttcConfig.failJobCountLimit) {
                                val stopMsg = "Dừng do lỗi nhận xu liên tiếp $consecutiveErrors lần"
                                withContext(Dispatchers.Main) {
                                    distinctRunningKeys.forEach { k -> ttcStatusMap[k] = stopMsg }
                                    ttcAccountStatusMap[ttcUser] = stopMsg
                                }
                                val accName = if (usePage && targetPageItem != null) "Page: ${targetPageItem.pageName.ifBlank { runUid }}" else parentFbAccount.name.ifBlank { runUid }
                                com.cayxu.app.worker.AppAlertNotifier.notifyAccountError(
                                    context = context,
                                    platform = "Tương Tác Chéo",
                                    accountName = accName,
                                    accountUid = runUid,
                                    consecutiveErrors = consecutiveErrors,
                                    errorDetail = "Đã dừng chạy: Đạt giới hạn $consecutiveErrors job lỗi liên tiếp"
                                )
                                break
                            }
                        }

                        if (ttcConfig.taskCountTarget > 0 && successCount >= ttcConfig.taskCountTarget) {
                            withContext(Dispatchers.Main) {
                                val doneAll = "Hoàn thành $successCount nhiệm vụ!"
                                ttcAccountStatusMap[ttcUser] = doneAll
                                distinctRunningKeys.forEach { k -> ttcStatusMap[k] = doneAll }
                            }
                            break
                        }

                        delay(delayTime)
                    }

                    // Chuyển sang loại nhiệm vụ tiếp theo sau mỗi đợt lấy
                    typeIndex++
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val err = "Lỗi luồng chạy: ${e.message}"
                    ttcAccountStatusMap[ttcUser] = err
                    ttcErrorDetailMap[ttcUser] = err
                    distinctRunningKeys.forEach { k ->
                        ttcStatusMap[k] = err
                        ttcErrorDetailMap[k] = err
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    distinctRunningKeys.forEach { k ->
                        runningTtcUids.remove(k)
                        activeRunJobs.remove(k)
                        if (ttcStatusMap[k]?.startsWith("Đang") == true || ttcStatusMap[k]?.startsWith("Delay") == true) {
                            ttcStatusMap[k] = "Đã dừng"
                        }
                    }
                    runningTtcAccounts.remove(ttcUser)
                    activeRunJobs.remove(ttcUser)
                    if (ttcAccountStatusMap[ttcUser]?.startsWith("Đang") == true || ttcAccountStatusMap[ttcUser]?.startsWith("Delay") == true) {
                        ttcAccountStatusMap[ttcUser] = "Đã dừng"
                    }
                }
            }
        }
        distinctRunningKeys.forEach { k -> activeRunJobs[k] = job }
        activeRunJobs[ttcUser] = job
    }

    fun toggleRunTtcSingle(username: String) {
        if (username in runningTtcAccounts) {
            stopTtcAccount(username)
        } else {
            startTtcAccount(targetTtcUsername = username)
        }
    }

    // Modal BottomSheet thêm acc TTC
    if (showAddTtcSheet) {
        AddTtcBottomSheet(
            onDismiss = { showAddTtcSheet = false },
            onLogin = { lines, isToken, isProxy ->
                var successCount = 0
                var failCount = 0
                withContext(Dispatchers.IO) {
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank()) {
                            val token: String
                            val proxy: String
                            if (isToken && isProxy) {
                                val parts = trimmed.split("|")
                                token = parts.getOrNull(0)?.trim().orEmpty()
                                proxy = parts.getOrNull(1)?.trim().orEmpty()
                            } else if (isToken) {
                                val parts = trimmed.split("|")
                                token = parts.getOrNull(0)?.trim().orEmpty()
                                proxy = if (parts.size > 1) parts[1].trim() else ""
                            } else {
                                token = ""
                                proxy = trimmed
                            }

                            if (token.isNotBlank()) {
                                try {
                                    val client = TuongTacCheoApiClient(
                                        proxyStr = proxy.ifBlank { null }
                                    )
                                    val loggedAcc = client.loginWithToken(token)
                                    TtcAccountsStore.addAccount(
                                        context,
                                        TtcAccount(
                                            username = loggedAcc.username,
                                            token = token,
                                            cookie = loggedAcc.cookie.orEmpty(),
                                            proxy = proxy,
                                            coins = loggedAcc.sodu,
                                            isLive = true
                                        )
                                    )
                                    successCount++
                                } catch (e: Exception) {
                                    failCount++
                                    val fallbackUser = if (token.length > 12) "TTC_${token.take(8)}" else token
                                    TtcAccountsStore.addAccount(
                                        context,
                                        TtcAccount(
                                            username = fallbackUser,
                                            token = token,
                                            cookie = "",
                                            proxy = proxy,
                                            coins = 0L,
                                            isLive = false
                                        )
                                    )
                                }
                            } else if (proxy.isNotBlank()) {
                                val fallbackUser = "Proxy_${proxy.substringBefore(":").takeLast(6)}"
                                TtcAccountsStore.addAccount(
                                    context,
                                    TtcAccount(
                                        username = fallbackUser,
                                        token = "",
                                        cookie = "",
                                        proxy = proxy,
                                        coins = 0L,
                                        isLive = true
                                    )
                                )
                                successCount++
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    reloadData()
                    showAddTtcSheet = false
                    if (failCount > 0) {
                        Toast.makeText(context, "TTC: $successCount thành công, $failCount thất bại", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Đã thêm thành công $successCount tài khoản TTC", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Modal BottomSheet thêm tài khoản Facebook (+)
    if (showFacebookLoginSheet) {
        FacebookLoginBottomSheet(
            onDismiss = {
                showFacebookLoginSheet = false
                reloadData()
            },
            onAccountSaved = {
                reloadData()
            }
        )
    }

    // Modal BottomSheet cấu hình chạy TTC (trượt từ dưới lên)
    if (showConfigSheet) {
        TtcRunConfigBottomSheet(
            config = ttcConfig,
            onDismiss = { showConfigSheet = false },
            onSaveConfig = { newCfg ->
                ttcConfig = newCfg
                TtcRunConfigStore.saveConfig(context, newCfg)
                showConfigSheet = false
                Toast.makeText(context, "Đã lưu cấu hình chạy TTC", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Sheet thông tin chi tiết Account mẹ
    selectedFbDetailAccount?.let { acc ->
        FacebookAccountDetailSheet(
            account = acc,
            onDismiss = {
                selectedFbDetailAccount = null
                reloadData()
            }
        )
    }

    // Sheet thông tin chi tiết Page
    selectedFbDetailPage?.let { pair ->
        FacebookPageDetailSheet(
            parentAccount = pair.first,
            page = pair.second,
            onUidResolved = { uid615 ->
                livePageUids = livePageUids + (pair.second.pageId to uid615)
                reloadData()
            },
            onMediaUpdated = { av, cov ->
                if (av.isNotBlank()) livePageAvatars = livePageAvatars + (pair.second.pageId to av)
                reloadData()
            },
            onDismiss = {
                selectedFbDetailPage = null
                reloadData()
            }
        )
    }

    // Sheet chi tiết lỗi
    selectedErrorDetailAccount?.let { accUid ->
        val errMsg = ttcErrorDetailMap[accUid]
            ?: ttcAccountStatusMap[accUid]
            ?: ttcStatusMap[accUid]
            ?: "Không có thông tin chi tiết lỗi"
        TtcErrorDetailBottomSheet(
            accountName = accUid,
            errorMessage = errMsg,
            onDismiss = { selectedErrorDetailAccount = null }
        )
    }

    // Sheet xác nhận xóa tài khoản FB đã chọn
    if (showDeleteConfirmSheet) {
        TtcDeleteConfirmBottomSheet(
            accountList = selectedFbUids.toList(),
            onDismiss = { showDeleteConfirmSheet = false },
            onConfirmDelete = {
                FacebookAccountsStore.removeAccounts(context, selectedFbUids.toList())
                selectedFbUids = emptySet()
                showDeleteConfirmSheet = false
                reloadData()
                Toast.makeText(context, "Đã xóa các tài khoản Facebook đã chọn", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // ==================== TOP BAR ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Tương tác chéo",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                reloadData()
                scope.launch(Dispatchers.IO) {
                    val accs = TtcAccountsStore.getAccounts(context)
                    var updated = false
                    accs.forEach { acc ->
                        if (acc.token.isNotBlank()) {
                            try {
                                val client = TuongTacCheoApiClient(proxyStr = acc.proxy.ifBlank { null })
                                val res = client.loginWithToken(acc.token)
                                if (res.sodu != acc.coins || res.username != acc.username) {
                                    TtcAccountsStore.addAccount(
                                        context,
                                        acc.copy(username = res.username, coins = res.sodu, isLive = true)
                                    )
                                    updated = true
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    if (updated) {
                        withContext(Dispatchers.Main) { reloadData() }
                    }
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Tải lại", tint = TextSecondary)
            }
        }

        // ==================== 2 THẺ (TABS) Ở TRÊN ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TtcTabButton(
                label = "Acc TTC",
                count = ttcAccounts.size,
                isSelected = selectedTab == 0,
                selectedColor = TtcPrimary,
                icon = Icons.Filled.SwapHoriz,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 0 }
            )

            // Tính tổng số account Facebook (cả nick mẹ + Page con)
            val allFbCount = remember(fbAccounts) {
                fbAccounts.sumOf { 1 + it.pages.size }
            }
            TtcTabButton(
                label = "Facebook",
                count = allFbCount,
                isSelected = selectedTab == 1,
                selectedColor = FbBlue,
                icon = Icons.Filled.Facebook,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 1 }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ==================== NỘI DUNG BÊN DƯỚI ====================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (selectedTab == 0) {
                // ---------- TAB ACC TTC ----------
                TtcAccountsTabContent(
                    accounts = ttcAccounts,
                    selectedUsernames = selectedTtcUsernames,
                    runningAccounts = runningTtcAccounts,
                    accountStatusMap = ttcAccountStatusMap,
                    isAnyFbRunning = runningTtcUids.isNotEmpty(),
                    onRunSingle = { username -> toggleRunTtcSingle(username) },
                    onErrorDetailClick = { selectedErrorDetailAccount = it },
                    onToggle = { username ->
                        selectedTtcUsernames = if (username in selectedTtcUsernames) {
                            selectedTtcUsernames - username
                        } else {
                            selectedTtcUsernames + username
                        }
                    },
                    onSelectAll = { checkAll ->
                        selectedTtcUsernames = if (checkAll) ttcAccounts.map { it.username }.toSet() else emptySet()
                    },
                    onAddNew = { showAddTtcSheet = true },
                    onDeleteSelected = {
                        selectedTtcUsernames.forEach { username ->
                            TtcAccountsStore.removeAccount(context, username)
                        }
                        selectedTtcUsernames = emptySet()
                        reloadData()
                    }
                )
            } else {
                // ---------- TAB FACEBOOK (ĐÃ SAO CHÉP TOÀN BỘ LOGIC TỪ XSMM) ----------
                val allFbKeys = remember(fbAccounts, livePageUids) {
                    fbAccounts.flatMap { acc ->
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

                FbAccountsTabContent(
                    accounts = fbAccounts,
                    selectedUids = selectedFbUids,
                    allFbKeys = allFbKeys,
                    liveFbAvatars = liveFbAvatars,
                    livePageUids = livePageUids,
                    livePageAvatars = livePageAvatars,
                    avatarVersion = avatarVersion,
                    runningUids = runningTtcUids,
                    statusMap = ttcStatusMap,
                    successCountMap = ttcSuccessCountMap,
                    errorCountMap = ttcErrorCountMap,
                    errorDetailMap = ttcErrorDetailMap,
                    isUploadingAvatar = isUploadingAvatar,
                    onToggle = { uid ->
                        selectedFbUids = if (uid in selectedFbUids) selectedFbUids - uid else selectedFbUids + uid
                    },
                    onSelectAll = { checkAll ->
                        selectedFbUids = if (checkAll) allFbKeys else emptySet()
                    },
                    onAddNew = { showFacebookLoginSheet = true },
                    onDeleteSelected = { showDeleteConfirmSheet = true },
                    onAccountDetailClick = { selectedFbDetailAccount = it },
                    onPageDetailClick = { parent, page -> selectedFbDetailPage = Pair(parent, page) },
                    onErrorDetailClick = { selectedErrorDetailAccount = it },
                    onAvatarChangeClick = { uid ->
                        targetFbAvatarChangeUid = uid
                        pickFbAvatarLauncher.launch("image/*")
                    },
                    onReloadAccount = { account ->
                        scope.launch(Dispatchers.IO) {
                            val mgr = FacebookAccountManager()
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
                                        val updated = account.copy(isLive = false)
                                        FacebookAccountsStore.addAccount(context, updated)
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
                                        val updated = account.copy(isLive = false)
                                        FacebookAccountsStore.addAccount(context, updated)
                                    }
                                }
                            } catch (_: Exception) {
                                val updated = account.copy(isLive = false)
                                FacebookAccountsStore.addAccount(context, updated)
                            }
                            withContext(Dispatchers.Main) {
                                avatarVersion = System.currentTimeMillis()
                                reloadData()
                                Toast.makeText(context, "Đã làm mới thông tin Facebook", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onToggleRun = { uid ->
                        if (uid in runningTtcUids) {
                            stopTtcAccount(uid)
                        } else {
                            startTtcAccount(uid)
                        }
                    }
                )
            }
        }

        // ==================== FOOTER: 2 NÚT (CẤU HÌNH + CHẠY) MÀU XÁM CHỦ ĐẠO ====================
        Surface(
            color = CardWhite,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Đã chọn: ${selectedTtcUsernames.size} acc TTC  •  ${selectedFbUids.size} nick/page FB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // NÚT 1: Cấu hình (bấm vào trượt BottomSheet từ dưới lên)
                    OutlinedButton(
                        onClick = { showConfigSheet = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TtcPrimary),
                        border = BorderStroke(1.2.dp, TtcPrimary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = TtcPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Cấu hình",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TtcPrimary
                        )
                    }

                    // NÚT 2: Chạy màu xám chủ đạo TTC
                    val isAnyRunning = runningTtcAccounts.isNotEmpty() || runningTtcUids.isNotEmpty()
                    Button(
                        onClick = {
                            if (isAnyRunning) {
                                val targets = activeRunJobs.keys.toList()
                                targets.forEach { stopTtcAccount(it) }
                                activeRunJobs.clear()
                                runningTtcUids.clear()
                                runningTtcAccounts.clear()
                                Toast.makeText(context, "Đã dừng tất cả tác vụ TTC", Toast.LENGTH_SHORT).show()
                            } else {
                                if (ttcAccounts.isEmpty()) {
                                    Toast.makeText(context, "Vui lòng thêm ít nhất 1 tài khoản TTC", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (fbAccounts.isEmpty()) {
                                    Toast.makeText(context, "Vui lòng thêm ít nhất 1 tài khoản Facebook", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Tự động ghép nối: Chạy các tài khoản TTC được chọn, nếu chưa chọn thì tự động chạy tất cả acc
                                val ttcToRun = if (selectedTtcUsernames.isNotEmpty()) {
                                    ttcAccounts.filter { it.username in selectedTtcUsernames }
                                } else {
                                    ttcAccounts.filter { it.isLive }.ifEmpty { ttcAccounts }
                                }

                                val isUsePage = ttcConfig.pairTargetType.equals("page", ignoreCase = true)

                                if (isUsePage) {
                                    val allAvailablePages = fbAccounts.flatMap { acc ->
                                        acc.pages.map { p ->
                                            val pDisplay = livePageUids[p.pageId] ?: p.displayUid
                                            val effUid = (p.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                                                ?: pDisplay.takeIf { it.isNotBlank() && it.startsWith("615") }
                                                ?: p.additionalProfileId.takeIf { it.isNotBlank() }
                                                ?: pDisplay.takeIf { it.isNotBlank() }
                                                ?: p.pageId).trim()
                                            Pair(p, effUid)
                                        }
                                    }

                                    val pagesToPair = if (selectedFbUids.isNotEmpty()) {
                                        allAvailablePages.filter { it.second in selectedFbUids || it.first.pageId in selectedFbUids }
                                    } else {
                                        allAvailablePages
                                    }

                                    if (pagesToPair.isEmpty()) {
                                        Toast.makeText(context, "Không có Page Facebook nào! Vui lòng chọn [Dùng Profile] hoặc thêm Page", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }

                                    pagesToPair.forEachIndexed { index, (pageItem, effUid) ->
                                        if (index < ttcToRun.size) {
                                            val ttcAcc = ttcToRun[index]
                                            startTtcAccount(inputUid = effUid, targetTtcUsername = ttcAcc.username)
                                        } else {
                                            ttcStatusMap[effUid] = "Thiếu acc TTC để ghép"
                                            ttcStatusMap[pageItem.pageId] = "Thiếu acc TTC để ghép"
                                        }
                                    }
                                    Toast.makeText(context, "Bắt đầu ghép ${minOf(ttcToRun.size, pagesToPair.size)} Page với TTC", Toast.LENGTH_SHORT).show()
                                } else {
                                    val fbsToPair = if (selectedFbUids.isNotEmpty()) {
                                        fbAccounts.filter { it.uid in selectedFbUids }
                                    } else {
                                        fbAccounts.filter { it.isLive }.ifEmpty { fbAccounts }
                                    }

                                    fbsToPair.forEachIndexed { index, fbAcc ->
                                        if (index < ttcToRun.size) {
                                            val ttcAcc = ttcToRun[index]
                                            startTtcAccount(inputUid = fbAcc.uid, targetTtcUsername = ttcAcc.username)
                                        } else {
                                            ttcStatusMap[fbAcc.uid] = "Thiếu acc TTC để ghép"
                                        }
                                    }
                                    Toast.makeText(context, "Bắt đầu ghép ${minOf(ttcToRun.size, fbsToPair.size)} Profile với TTC", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAnyRunning) DangerRed else TtcPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        if (isAnyRunning) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Dừng chạy", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            val runCount = selectedFbUids.size
                            Text(
                                if (runCount > 0) "Chạy ($runCount)" else "Chạy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Thẻ Tab chuyển đổi ở trên */
@Composable
private fun TtcTabButton(
    label: String,
    count: Int,
    isSelected: Boolean,
    selectedColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.12f) else CardWhite,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) selectedColor else Color(0xFFE5E7EB)
        ),
        shadowElevation = if (isSelected) 0.dp else 0.5.dp,
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$label ($count)",
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedColor else TextPrimary
            )
        }
    }
}

/** Nội dung danh sách tài khoản TTC */
@Composable
private fun TtcAccountsTabContent(
    accounts: List<TtcAccount>,
    selectedUsernames: Set<String>,
    runningAccounts: List<String>,
    accountStatusMap: Map<String, String>,
    isAnyFbRunning: Boolean,
    onRunSingle: (String) -> Unit,
    onErrorDetailClick: (String) -> Unit = {},
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAddNew: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAllSelected = accounts.isNotEmpty() && selectedUsernames.size == accounts.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelectAll(!isAllSelected) }
            ) {
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onSelectAll(it) },
                    colors = CheckboxDefaults.colors(checkedColor = TtcPrimary)
                )
                Text(
                    "Tất cả (${selectedUsernames.size}/${accounts.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nút thùng rác xóa các acc đã chọn
                if (selectedUsernames.isNotEmpty()) {
                    IconButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DangerRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Xóa đã chọn",
                                tint = DangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Nút dấu cộng "+"
                FilledIconButton(
                    onClick = onAddNew,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = TtcPrimary),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Thêm acc TTC", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Chưa có tài khoản Tương tác chéo", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    FilledIconButton(
                        onClick = onAddNew,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = TtcPrimary),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Thêm acc TTC", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.username }) { acc ->
                    val isSelected = acc.username in selectedUsernames
                    val isAccRunning = acc.username in runningAccounts
                    val statusText = accountStatusMap[acc.username]

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) TtcPrimary.copy(alpha = 0.05f) else CardWhite
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) TtcPrimary else Color(0xFFE5E7EB)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(acc.username) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggle(acc.username) },
                                colors = CheckboxDefaults.colors(checkedColor = TtcPrimary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    acc.username.ifBlank { "TTC Account" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (acc.coins > 0) "${acc.coins} xu" else "Sẵn sàng",
                                        fontSize = 12.sp,
                                        color = if (acc.isLive) Color(0xFF16A34A) else DangerRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (acc.token.isNotBlank()) {
                                        Text(
                                            text = " • Token",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    if (acc.proxy.isNotBlank()) {
                                        Text(
                                            text = " • Proxy",
                                            fontSize = 11.sp,
                                            color = Color(0xFF0284C7),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Trạng thái hiển thị (Status Visibility):
                                // Khi chưa bấm chạy: Ẩn hoàn toàn (View.GONE)
                                // Khi bấm chạy hoặc gặp lỗi: Hiện lên (View.VISIBLE) và cập nhật liên tục tiến trình
                                if (!statusText.isNullOrBlank()) {
                                    val isError = statusText.startsWith("Lỗi") ||
                                        statusText.contains("Lỗi", ignoreCase = true) ||
                                        statusText.contains("thất bại", ignoreCase = true) ||
                                        statusText.contains("hết hạn", ignoreCase = true) ||
                                        statusText.contains("sai Cookie", ignoreCase = true) ||
                                        statusText.contains("Dừng do", ignoreCase = true)
                                    val isWaiting = statusText.contains("chậm lại", ignoreCase = true)
                                    val isStopped = statusText == "Đã dừng"
                                    val statusColor = when {
                                        isError -> DangerRed
                                        isWaiting -> Color(0xFFF59E0B)
                                        isStopped -> TextSecondary
                                        else -> Color(0xFF16A34A)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isAccRunning && !isWaiting) {
                                            CircularProgressIndicator(
                                                color = statusColor,
                                                strokeWidth = 1.6.dp,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor)
                                            )
                                        }
                                        Text(
                                            text = statusText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = statusColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .then(if (isError) Modifier.clickable { onErrorDetailClick(acc.username) } else Modifier)
                                        )
                                        if (isError) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(DangerRed.copy(alpha = 0.12f))
                                                    .border(1.dp, DangerRed.copy(alpha = 0.35f), CircleShape)
                                                    .clickable { onErrorDetailClick(acc.username) },
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

                            // Nút chạy hình tam giác (▶) / Dừng (⏹) cho từng tài khoản TTC
                            IconButton(
                                onClick = { onRunSingle(acc.username) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isAccRunning) DangerRed.copy(alpha = 0.12f) else TtcPrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAccRunning) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(DangerRed)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Chạy tài khoản",
                                            tint = TtcPrimary,
                                            modifier = Modifier.size(18.dp)
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

/**
 * Nội dung danh sách tài khoản Facebook - Sao chép toàn bộ logic Facebook từ XSMM
 */
@Composable
private fun FbAccountsTabContent(
    accounts: List<FacebookAccount>,
    selectedUids: Set<String>,
    allFbKeys: Set<String>,
    liveFbAvatars: Map<String, String>,
    livePageUids: Map<String, String>,
    livePageAvatars: Map<String, String>,
    avatarVersion: Long,
    runningUids: List<String>,
    statusMap: Map<String, String>,
    successCountMap: Map<String, Int>,
    errorCountMap: Map<String, Int>,
    errorDetailMap: Map<String, String>,
    isUploadingAvatar: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAddNew: () -> Unit,
    onDeleteSelected: () -> Unit,
    onAccountDetailClick: (FacebookAccount) -> Unit,
    onPageDetailClick: (FacebookAccount, FacebookPageItem) -> Unit,
    onErrorDetailClick: (String) -> Unit,
    onAvatarChangeClick: (String) -> Unit,
    onReloadAccount: (FacebookAccount) -> Unit,
    onToggleRun: (String) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Thanh công cụ FB: Checkbox "Tất cả" + Thùng rác + Dấu cộng "+" (thay vì nút Quản lý FB)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAllSelected = allFbKeys.isNotEmpty() && selectedUids.size == allFbKeys.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelectAll(!isAllSelected) }
            ) {
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onSelectAll(it) },
                    colors = CheckboxDefaults.colors(checkedColor = FbBlue)
                )
                Text(
                    "Tất cả (${selectedUids.size}/${allFbKeys.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nút xóa (thùng rác đỏ khi có account/page được chọn)
                if (selectedUids.isNotEmpty()) {
                    IconButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DangerRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Xóa tài khoản đã chọn",
                                tint = DangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // NÚT DẤU "+" THAY CHO NÚT "QUẢN LÝ FB"
                FilledIconButton(
                    onClick = onAddNew,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = FbBlue),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Thêm tài khoản Facebook",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Facebook,
                        contentDescription = null,
                        tint = FbBlue.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Chưa có tài khoản Facebook nào", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onAddNew,
                        colors = ButtonDefaults.buttonColors(containerColor = FbBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Thêm tài khoản Facebook", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(accounts, key = { it.uid }) { account ->
                    val isChecked = account.uid in selectedUids
                    val isRunningThis = account.uid in runningUids
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
                        border = if (isChecked) BorderStroke(1.5.dp, FbBlue) else BorderStroke(0.8.dp, Color(0xFFE5E7EB)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header nick Mẹ
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggle(account.uid) },
                                    colors = CheckboxDefaults.colors(checkedColor = FbBlue)
                                )
                                Spacer(Modifier.width(6.dp))

                                // Avatar Facebook có nút đổi ảnh cây bút nhỏ
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, FbBlue.copy(alpha = 0.6f), CircleShape)
                                        .clickable(enabled = !isUploadingAvatar) {
                                            onAvatarChangeClick(account.uid)
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
                                                .background(FbBlue),
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

                                    // Icon bút đổi avatar
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

                                        // Badge Live / Die
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
                                    onClick = { onReloadAccount(account) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(FbBlue.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Làm mới",
                                            tint = FbBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(4.dp))

                                // Nút Chạy / Dừng riêng
                                IconButton(
                                    onClick = { onToggleRun(account.uid) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isRunningThis) DangerRed else FbBlue),
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

                            // Trạng thái và tiến độ của nick mẹ
                            val fbStatus = statusMap[account.uid]
                            val fbSuccess = successCountMap[account.uid] ?: 0
                            val fbErrors = errorCountMap[account.uid] ?: 0
                            val fbErrDetail = errorDetailMap[account.uid]
                            if (!fbStatus.isNullOrBlank() || fbSuccess > 0 || fbErrors > 0) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = fbStatus ?: "Sẵn sàng",
                                        fontSize = 11.5.sp,
                                        color = if (isRunningThis) FbBlue else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
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
                                                    onClick = { onErrorDetailClick(account.uid) },
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

                            // Header danh sách Page con
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

                                // Nút chấm than (i) xem Full Info mẹ
                                IconButton(
                                    onClick = { onAccountDetailClick(account) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(FbBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Info,
                                            contentDescription = "Xem thông tin chi tiết",
                                            tint = FbBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Danh sách Page con
                            if (account.pages.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    account.pages.forEach { page ->
                                        val pageDisplayUid = livePageUids[page.pageId] ?: page.displayUid
                                        val effectivePageUid = (page.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                                            ?: pageDisplayUid.takeIf { it.isNotBlank() && it.startsWith("615") }
                                            ?: page.additionalProfileId.takeIf { it.isNotBlank() }
                                            ?: pageDisplayUid.takeIf { it.isNotBlank() }
                                            ?: page.pageId).trim()

                                        val isPageRunning = runningUids.any {
                                            it.equals(effectivePageUid, ignoreCase = true) ||
                                            it.equals(page.pageId, ignoreCase = true)
                                        }

                                        val isPageChecked = effectivePageUid in selectedUids || page.pageId in selectedUids

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFFF8FAFC))
                                                .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 1. Checkbox chọn Page
                                                Checkbox(
                                                    checked = isPageChecked,
                                                    onCheckedChange = { onToggle(effectivePageUid) },
                                                    colors = CheckboxDefaults.colors(checkedColor = FbBlue),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))

                                                // 2. Avatar của Page
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
                                                            .background(FbBlue.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Flag,
                                                            contentDescription = null,
                                                            tint = FbBlue,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))

                                                // 3. Tên Page và UID 615
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "Page: ${page.pageName.ifBlank { effectivePageUid }}",
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

                                                // 4. Nút Info (i) cho Page
                                                IconButton(
                                                    onClick = { onPageDetailClick(account, page) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(FbBlue.copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Info,
                                                            contentDescription = "Xem thông tin Page",
                                                            tint = FbBlue,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(Modifier.width(4.dp))

                                                // 5. Nút Chạy / Dừng Page
                                                IconButton(
                                                    onClick = { onToggleRun(effectivePageUid) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isPageRunning) DangerRed else FbBlue),
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

                                            // 6. Trạng thái chạy & tiến độ Page
                                            val pageStatus = statusMap[effectivePageUid] ?: statusMap[page.pageId]
                                            val pageSuccess = successCountMap[effectivePageUid] ?: successCountMap[page.pageId] ?: 0
                                            val pageErrors = errorCountMap[effectivePageUid] ?: errorCountMap[page.pageId] ?: 0
                                            val pageErrDetail = errorDetailMap[effectivePageUid] ?: errorDetailMap[page.pageId]

                                            if (isPageRunning || !pageStatus.isNullOrBlank() || pageSuccess > 0 || pageErrors > 0) {
                                                Spacer(Modifier.height(5.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isPageRunning) FbBlue.copy(alpha = 0.08f) else Color(0xFFE2E8F0).copy(alpha = 0.4f))
                                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (isPageRunning) {
                                                            CircularProgressIndicator(
                                                                color = FbBlue,
                                                                strokeWidth = 1.6.dp,
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                            Spacer(Modifier.width(5.dp))
                                                        }
                                                        Text(
                                                            text = pageStatus ?: if (isPageRunning) "Đang chạy..." else "Sẵn sàng",
                                                            fontSize = 10.5.sp,
                                                            color = if (isPageRunning) FbBlue else TextSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (pageSuccess > 0 || pageErrors > 0 || !pageErrDetail.isNullOrBlank()) {
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
                                                            if (pageErrors > 0 || !pageErrDetail.isNullOrBlank()) {
                                                                IconButton(
                                                                    onClick = { onErrorDetailClick(effectivePageUid) },
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
            }
        }
    }
}

/** BottomSheet cấu hình chạy TTC trượt từ dưới lên */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtcRunConfigBottomSheet(
    config: TtcRunConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (TtcRunConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTypes by remember { mutableStateOf(config.taskTypes.toSet()) }
    var delaySec by remember { mutableIntStateOf(config.delaySeconds) }
    var targetCount by remember { mutableIntStateOf(config.taskCountTarget) }
    var failLimit by remember { mutableIntStateOf(config.failJobCountLimit) }
    var pairModeEnabled by remember { mutableStateOf(config.pairModeEnabled) }
    var pairTargetType by remember { mutableStateOf(config.pairTargetType) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(TtcPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = TtcPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Cấu hình chạy Tương Tác Chéo", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                    Text("Tùy chỉnh loại nhiệm vụ, độ trễ và số lượng cần chạy", fontSize = 12.sp, color = TextSecondary)
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // 1. Loại nhiệm vụ (Switch gạt bật tắt)
            Text("Loại nhiệm vụ thực hiện:", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TtcRunConfigStore.fbTaskTypes.forEach { (typeKey, typeLabel) ->
                    val isChecked = typeKey in selectedTypes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedTypes = if (isChecked) {
                                    if (selectedTypes.size > 1) selectedTypes - typeKey else selectedTypes
                                } else {
                                    selectedTypes + typeKey
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(typeLabel, fontSize = 13.5.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { chk ->
                                selectedTypes = if (chk) selectedTypes + typeKey
                                else if (selectedTypes.size > 1) selectedTypes - typeKey else selectedTypes
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TtcPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // 2. Chế độ chạy ghép 1 TTC ↔ 1 Page hoặc Profile
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chạy ghép 1 TTC ↔ 1 Nick FB", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Mỗi tài khoản TTC sẽ ghép cặp cùng 1 nick FB tương ứng", fontSize = 11.5.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = pairModeEnabled,
                        onCheckedChange = { pairModeEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TtcPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }

                if (pairModeEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isPage = pairTargetType.equals("page", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPage) TtcPrimary else Color.Transparent)
                                .clickable { pairTargetType = "page" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Dùng Page (Profile+)",
                                fontSize = 12.5.sp,
                                fontWeight = if (isPage) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPage) Color.White else TextPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isPage) TtcPrimary else Color.Transparent)
                                .clickable { pairTargetType = "profile" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Dùng Profile (Nick mẹ)",
                                fontSize = 12.5.sp,
                                fontWeight = if (!isPage) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isPage) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // 3. Độ trễ (delay)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Thời gian nghỉ giữa các nhiệm vụ:", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("$delaySec giây", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TtcPrimary)
                }
                Slider(
                    value = delaySec.toFloat(),
                    onValueChange = { delaySec = it.toInt() },
                    valueRange = 3f..60f,
                    steps = 57,
                    colors = SliderDefaults.colors(
                        thumbColor = TtcPrimary,
                        activeTrackColor = TtcPrimary,
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    )
                )
            }

            // 4. Số lượng nhiệm vụ cần chạy (Target)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Giới hạn số nhiệm vụ:", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(if (targetCount == 0) "Không giới hạn" else "$targetCount nhiệm vụ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TtcPrimary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20, 50, 100, 0).forEach { count ->
                        val isSel = targetCount == count
                        val lbl = if (count == 0) "Không giới hạn" else "$count"
                        FilterChip(
                            selected = isSel,
                            onClick = { targetCount = count },
                            label = { Text(lbl, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TtcPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = TtcPrimary
                            )
                        )
                    }
                }
            }

            // 5. Số lần lỗi liên tiếp thì dừng
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Số lần lỗi liên tiếp thì dừng:", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    val limitDisplay = when (failLimit) {
                        -1, 0 -> "Không giới hạn"
                        1000 -> "1000+"
                        else -> "$failLimit lần"
                    }
                    Text(limitDisplay, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val failOptions = listOf(
                        5 to "5 lần",
                        100 to "100 lần",
                        1000 to "1000+",
                        -1 to "Không giới hạn"
                    )
                    failOptions.forEach { (limit, label) ->
                        val isSel = (limit == -1 && (failLimit <= 0 || failLimit == -1)) || (failLimit == limit)
                        FilterChip(
                            selected = isSel,
                            onClick = { failLimit = limit },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DangerRed.copy(alpha = 0.12f),
                                selectedLabelColor = DangerRed
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 2 Nút: Đóng & Lưu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Đóng", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val newCfg = TtcRunConfig(
                            taskTypes = selectedTypes.toList(),
                            delaySeconds = delaySec,
                            taskCountTarget = targetCount,
                            failJobCountLimit = failLimit,
                            pairModeEnabled = pairModeEnabled,
                            pairTargetType = pairTargetType
                        )
                        onSaveConfig(newCfg)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TtcPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Lưu cấu hình", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** BottomSheet thêm tài khoản TTC trượt từ dưới lên */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTtcBottomSheet(
    onDismiss: () -> Unit,
    onLogin: suspend (lines: List<String>, isToken: Boolean, isProxy: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isTokenSelected by remember { mutableStateOf(true) }
    var isProxySelected by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Thêm tài khoản Tương tác chéo",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
            )

            // 2 ô để chọn: Token và Proxy (chọn được 1 hoặc cả 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = isTokenSelected,
                    onClick = {
                        if (isTokenSelected && !isProxySelected) return@FilterChip
                        isTokenSelected = !isTokenSelected
                    },
                    leadingIcon = if (isTokenSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Token", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TtcPrimary.copy(alpha = 0.15f),
                        selectedLabelColor = TtcPrimary,
                        selectedLeadingIconColor = TtcPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = isProxySelected,
                    onClick = {
                        if (!isTokenSelected && isProxySelected) return@FilterChip
                        isProxySelected = !isProxySelected
                    },
                    leadingIcon = if (isProxySelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Proxy", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TtcPrimary.copy(alpha = 0.15f),
                        selectedLabelColor = TtcPrimary,
                        selectedLeadingIconColor = TtcPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            val formatLabel = when {
                isTokenSelected && isProxySelected -> "Dán danh sách (định dạng token|proxy - mỗi dòng 1 acc):"
                isTokenSelected -> "Dán danh sách token (mỗi dòng 1 tài khoản):"
                else -> "Dán danh sách proxy (mỗi dòng 1 proxy):"
            }
            val placeholderText = when {
                isTokenSelected && isProxySelected -> "Dán token|proxy tại đây...\ntoken_1|1.2.3.4:8080\ntoken_2|1.2.3.4:8080:user:pass"
                isTokenSelected -> "Dán token tại đây...\ntoken_acc_1\ntoken_acc_2\ntoken_acc_3"
                else -> "Dán proxy tại đây...\n1.2.3.4:8080\n1.2.3.4:8080:user:pass"
            }

            Column {
                Text(
                    formatLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            placeholderText,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    },
                    minLines = 5,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TtcPrimary,
                        cursorColor = TtcPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("Hủy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val lines = inputText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        if (lines.isEmpty() || isLoggingIn) return@Button
                        isLoggingIn = true
                        scope.launch {
                            try {
                                onLogin(lines, isTokenSelected, isProxySelected)
                            } finally {
                                isLoggingIn = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TtcPrimary),
                    shape = RoundedCornerShape(10.dp),
                    enabled = inputText.isNotBlank() && !isLoggingIn,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Đang xử lý...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Đăng nhập", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** BottomSheet xem chi tiết lỗi */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtcErrorDetailBottomSheet(
    accountName: String,
    errorMessage: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

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
                        "Chi tiết lỗi TTC",
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

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = errorMessage,
                            color = Color(0xFF991B1B),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("TTC Error", errorMessage)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Đã sao chép nội dung lỗi", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DangerRed),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("Sao chép lỗi", fontWeight = FontWeight.Bold, color = DangerRed)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("Đã hiểu & Đóng", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** BottomSheet xác nhận xóa tài khoản */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtcDeleteConfirmBottomSheet(
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
                        "Xóa ${accountList.size} tài khoản Facebook đã chọn",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Bạn có chắc chắn muốn xóa ${accountList.size} tài khoản Facebook này khỏi thiết bị? Mọi thông tin tài khoản và cookie đã lưu sẽ bị xóa vĩnh viễn.",
                fontSize = 13.5.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
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
