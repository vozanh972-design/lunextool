package com.cayxu.app.ui.screens.utilities

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.facebook.FacebookPageService
import com.cayxu.app.facebook.QuanLyPageEngine
import com.cayxu.app.ui.screens.xsmm.FacebookAccountDetailSheet
import com.cayxu.app.ui.screens.xsmm.FacebookLoginBottomSheet
import com.cayxu.app.ui.screens.xsmm.FacebookPageDetailSheet
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardWhite = Color.White
private val DangerRed = Color(0xFFEF4444)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val CardBorderColor = Color(0xFFE2E8F0)
private val Cobalt600 = Color(0xFF1D4ED8)
private val Cobalt500 = Color(0xFF2E6BF2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegAndTransferPageScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tab chức năng: 0: Reg Page, 1: Chuyển Page
    var activeTab by remember { mutableStateOf(0) }

    var facebookAccounts by remember {
        mutableStateOf(FacebookAccountsStore.getAccounts(context, forceReload = true))
    }
    var selectedForRunUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFacebookLoginSheet by remember { mutableStateOf(false) }
    var selectedFbDetailAccount by remember { mutableStateOf<FacebookAccount?>(null) }
    var selectedPageDetail by remember { mutableStateOf<Pair<FacebookAccount, FacebookPageItem>?>(null) }
    val livePageAvatars = remember { mutableStateMapOf<String, String>() }
    val livePageUids = remember { mutableStateMapOf<String, String>() }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var targetFbAvatarChangeUid by remember { mutableStateOf<String?>(null) }
    var avatarVersion by remember { mutableStateOf(System.currentTimeMillis()) }

    // State cấu hình Reg Page
    var showConfigSheet by remember { mutableStateOf(false) }
    var nameTypeOption by remember { mutableStateOf("Tên Việt") } // "Tên Việt" hoặc "Tên Tây"
    var regCountInput by remember { mutableStateOf("15") }
    var delaySecondsInput by remember { mutableStateOf("2000") }

    // State cho tab Chuyển Page
    var transferReceiverUid by remember { mutableStateOf("") }
    var showTransferConfigSheet by remember { mutableStateOf(false) }
    var isFullPermission by remember { mutableStateOf(true) } // true = Full quyền, false = No full (không full quyền)
    var selectedPageKeys by remember { mutableStateOf<Set<String>>(emptySet()) } // Set các key "${accountUid}_${pageId}" đã tích chọn

    // Trạng thái đang chạy và Job điều khiển
    var isRunning by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    val runningJobs = remember { mutableStateMapOf<String, Job>() }
    var checkpointUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var runningAccountUid by remember { mutableStateOf<String?>(null) }
    val accountStatusMap = remember { mutableStateMapOf<String, String>() }
    var selectedErrorDetail by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(AccountName, ErrorMessage)

    val pageService = remember { FacebookPageService() }

    val pickFbAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
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
                    val acc = facebookAccounts.find { it.uid == uid }
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

                    val mediaEngine = com.cayxu.app.facebook.FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val result = mediaEngine.updateAvatar(
                        imageBytes = bytes,
                        targetId = acc.uid,
                        tokenParam = token
                    )

                    if (result.isSuccess) {
                        val updatedMedia = mediaEngine.getProfileMedia(acc.uid, tokenParam = token)
                        val rawAvatar = updatedMedia?.avatarUrl ?: "https://graph.facebook.com/v21.0/${acc.uid}/picture?type=large&access_token=$token"
                        val finalAvatar = if (rawAvatar.contains("?")) "$rawAvatar&t=${System.currentTimeMillis()}" else "$rawAvatar?t=${System.currentTimeMillis()}"
                        val updatedAcc = acc.copy(avatar = finalAvatar)
                        FacebookAccountsStore.addAccount(context, updatedAcc)
                        withContext(Dispatchers.Main) {
                            avatarVersion = System.currentTimeMillis()
                            facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
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
                        Toast.makeText(context, "Lỗi đổi avatar: ${e.message}", Toast.LENGTH_LONG).show()
                        isUploadingAvatar = false
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        targetFbAvatarChangeUid = null
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, CardBorderColor, CircleShape)
                            .background(Color.White)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeTab == 0) "Reg Page Tự Động" else "Chuyển Quyền Page",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (activeTab == 0) "Tạo Fanpage Facebook tự động theo cấu hình" else "Gán quyền quản trị viên Fanpage sang UID mới",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorderColor, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Cấu hình (hiện ở cả 2 tab)
                    OutlinedButton(
                        onClick = {
                            if (activeTab == 0) showConfigSheet = true
                            else showTransferConfigSheet = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Cobalt600),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cobalt600),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (activeTab == 0) "Cấu hình" else "Cấu hình (${if (isFullPermission) "Full quyền" else "No full"})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Nút tam giác nhỏ Bắt đầu chạy (Icon Play, không có chữ)
                    Button(
                        onClick = {
                            if (activeTab == 0) {
                                if (runningJobs.isNotEmpty()) return@Button

                                val targetAccounts = facebookAccounts.filter { it.uid in selectedForRunUids }
                                if (targetAccounts.isEmpty()) {
                                    Toast.makeText(context, "Vui lòng tick chọn ít nhất 1 tài khoản Facebook!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                // Chạy Reg Page ĐA LUỒNG SONG SONG
                                val count = regCountInput.toIntOrNull() ?: 15
                                val delaySec = delaySecondsInput.toIntOrNull() ?: 2000

                                isRunning = true
                                targetAccounts.forEach { account ->
                                    runningJobs[account.uid]?.cancel()
                                    val job = scope.launch(Dispatchers.IO) {
                                        try {
                                            // 1. Kiểm tra avatar - Nếu không có avatar hoặc avatar mặc định -> Báo Checkpoint ngay
                                            val hasNoAvatar = account.avatar.isBlank() ||
                                                    account.avatar.contains("silhouette") ||
                                                    account.avatar.contains("blank_avatar")
                                            if (hasNoAvatar) {
                                                checkpointUids = checkpointUids + account.uid
                                                try {
                                                    val updatedAcc = account.copy(isLive = false)
                                                    FacebookAccountsStore.addAccount(context, updatedAcc)
                                                } catch (_: Throwable) {}
                                                withContext(Dispatchers.Main) {
                                                    accountStatusMap[account.uid] = "Checkpoint: Nick chưa có Avatar"
                                                    facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                                                }
                                                return@launch
                                            }

                                            // 2. Kiểm tra token
                                            var token = account.bio.ifBlank { null }
                                            withContext(Dispatchers.Main) {
                                                accountStatusMap[account.uid] = "Đang kiểm tra Token..."
                                            }

                                            // Tự động khôi phục Token từ Cookie nếu Token rỗng
                                            if (token.isNullOrBlank() && account.note.contains("c_user=")) {
                                                try {
                                                    val mgr = FacebookAccountManager()
                                                    val directAcc = mgr.getTokenFromCookie(account.note, account.phone.ifBlank { null })
                                                    if (directAcc != null && directAcc.bio.isNotBlank()) {
                                                        token = directAcc.bio
                                                        val updated = account.copy(bio = directAcc.bio, isLive = true)
                                                        FacebookAccountsStore.addAccount(context, updated)
                                                    }
                                                } catch (_: Throwable) {}
                                            }

                                            if (token.isNullOrBlank()) {
                                                checkpointUids = checkpointUids + account.uid
                                                try {
                                                    val updatedAcc = account.copy(isLive = false)
                                                    FacebookAccountsStore.addAccount(context, updatedAcc)
                                                } catch (_: Throwable) {}
                                                withContext(Dispatchers.Main) {
                                                    accountStatusMap[account.uid] = "Lỗi: Thiếu Token EAAA (Checkpoint)"
                                                    facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                                                }
                                                return@launch
                                            }

                                            var createdSuccessCount = 0
                                            var lastErrorMsg: String? = null

                                            for (idx in 1..count) {
                                                if (!isActive) break
                                                val pageName = try {
                                                    pageService.generateRandomName(nameTypeOption)
                                                } catch (_: Throwable) {
                                                    "Shop Online ${System.currentTimeMillis() % 10000}"
                                                }

                                                withContext(Dispatchers.Main) {
                                                    accountStatusMap[account.uid] = "Đang tạo ($idx/$count): $pageName"
                                                }

                                                var isCreated = false
                                                try {
                                                    val res = pageService.createFacebookPage(pageName, token)
                                                    isCreated = true
                                                    createdSuccessCount++
                                                    try {
                                                        val updatedPages = pageService.getPages(token)
                                                        if (updatedPages.isNotEmpty()) {
                                                            val updatedAcc = account.copy(pages = updatedPages)
                                                            FacebookAccountsStore.addAccount(context, updatedAcc)
                                                        }
                                                    } catch (_: Throwable) {}
                                                    withContext(Dispatchers.Main) {
                                                        accountStatusMap[account.uid] = "Đã tạo: $pageName | [Thành công: $createdSuccessCount] / [$count]"
                                                    }
                                                } catch (e: Throwable) {
                                                    val errRaw = e.message ?: "Thất bại"
                                                    val shortErr = errRaw.substringBefore("\n\n[Raw Facebook Response]")
                                                    lastErrorMsg = shortErr
                                                    val failCount = (idx - createdSuccessCount)

                                                    // Nhận diện lỗi Checkpoint (#1675030, checkpoint, Session expired, ...)
                                                    val isCheckpointErr = errRaw.contains("1675030") ||
                                                            errRaw.contains("checkpoint", ignoreCase = true) ||
                                                            errRaw.contains("Session expired", ignoreCase = true) ||
                                                            errRaw.contains("Error validating access token", ignoreCase = true)

                                                    if (isCheckpointErr) {
                                                        checkpointUids = checkpointUids + account.uid
                                                        try {
                                                            val updatedAcc = account.copy(isLive = false)
                                                            FacebookAccountsStore.addAccount(context, updatedAcc)
                                                        } catch (_: Throwable) {}
                                                        withContext(Dispatchers.Main) {
                                                            accountStatusMap[account.uid] = "Checkpoint: $shortErr\n[Lỗi: $failCount | Thành công: $createdSuccessCount] / [$count]"
                                                            facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                                                        }
                                                        break
                                                    }

                                                    withContext(Dispatchers.Main) {
                                                        accountStatusMap[account.uid] = "Trạng thái: $shortErr\n[Lỗi: $failCount | Thành công: $createdSuccessCount] / [$count]"
                                                    }
                                                }

                                                // Đếm ngược thời gian nếu tạo THÀNH CÔNG và còn lần tạo tiếp
                                                if (isCreated && idx < count && isActive) {
                                                    for (s in delaySec downTo 1) {
                                                        if (!isActive) break
                                                        withContext(Dispatchers.Main) {
                                                            accountStatusMap[account.uid] = "Chờ ${s}s để tiếp tục...\n[Lỗi: ${idx - createdSuccessCount} | Thành công: $createdSuccessCount] / [$count]"
                                                        }
                                                        delay(1000L)
                                                    }
                                                } else if (!isCreated) {
                                                    // Nếu tạo lỗi do Facebook chặn, dừng vòng lặp tài khoản này
                                                    break
                                                }
                                            }

                                            if (isActive) {
                                                withContext(Dispatchers.Main) {
                                                    val failCount = (count - createdSuccessCount)
                                                    if (createdSuccessCount == count) {
                                                        accountStatusMap[account.uid] = "Hoàn tất: [Thành công: $count/$count Page]"
                                                    } else if (account.uid in checkpointUids) {
                                                        accountStatusMap[account.uid] = "Checkpoint: ${lastErrorMsg ?: "Bị hạn chế"} | [Lỗi: $failCount | Thành công: $createdSuccessCount] / [$count]"
                                                    } else if (createdSuccessCount > 0) {
                                                        accountStatusMap[account.uid] = "Dừng: ${lastErrorMsg ?: "Đã dừng"} | [Lỗi: $failCount | Thành công: $createdSuccessCount] / [$count]"
                                                    } else {
                                                        accountStatusMap[account.uid] = "Trạng thái: ${lastErrorMsg ?: "Không thể tạo Trang"} | [Lỗi: $failCount | Thành công: 0] / [$count]"
                                                    }
                                                }
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            withContext(Dispatchers.Main) {
                                                accountStatusMap[account.uid] = "Đã dừng"
                                            }
                                        } catch (e: Throwable) {
                                            val errMsg = e.localizedMessage ?: "Lỗi xử lý"
                                            withContext(Dispatchers.Main) {
                                                accountStatusMap[account.uid] = "Lỗi: $errMsg"
                                            }
                                        } finally {
                                            withContext(Dispatchers.Main) {
                                                runningJobs.remove(account.uid)
                                                if (runningJobs.isEmpty()) {
                                                    isRunning = false
                                                    facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                                                }
                                            }
                                        }
                                    }
                                    runningJobs[account.uid] = job
                                }
                            } else {
                                if (isRunning) return@Button
                                // Chuyển Page với Full quyền hoặc No full
                                val receiverUid = transferReceiverUid.trim()
                                if (receiverUid.isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập UID người nhận quyền admin!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Lọc ra các Acc Chuyển: acc != receiverUid và có ít nhất 1 page được chọn
                                val sourceAccounts = facebookAccounts.filter { acc ->
                                    acc.uid != receiverUid && acc.pages.any { p -> "${acc.uid}_${p.pageId}" in selectedPageKeys }
                                }

                                if (sourceAccounts.isEmpty()) {
                                    Toast.makeText(context, "Vui lòng tick chọn ít nhất 1 Fanpage cần chuyển!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isRunning = true
                                val permissionLabel = if (isFullPermission) "Full quyền" else "No full"
                                runJob = scope.launch(Dispatchers.IO) {
                                    try {
                                        var transferredCount = 0
                                        var failCount = 0

                                        for (account in sourceAccounts) {
                                            if (!isActive) break
                                            runningAccountUid = account.uid

                                            withContext(Dispatchers.Main) {
                                                accountStatusMap[account.uid] = "Đang chuẩn bị chuyển Page ($permissionLabel)..."
                                            }

                                            val proxyParts = account.phone.ifBlank { null }?.split(":")
                                            val proxyHost = proxyParts?.getOrNull(0)
                                            val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                                            val engine = QuanLyPageEngine(
                                                accessToken = account.bio.ifBlank { null },
                                                proxyHost = proxyHost,
                                                proxyPort = proxyPort
                                            )

                                            // CHỈ DUYỆT CÁC PAGE ĐƯỢC TÍCH CHỌN
                                            val pagesToTransfer = account.pages.filter { p ->
                                                "${account.uid}_${p.pageId}" in selectedPageKeys
                                            }

                                            if (pagesToTransfer.isEmpty()) {
                                                continue
                                            }

                                            for (page in pagesToTransfer) {
                                                if (!isActive) break
                                                val pageKey = "${account.uid}_${page.pageId}"
                                                runningAccountUid = pageKey

                                                val pName = page.pageName.ifBlank { page.displayUid.ifBlank { page.pageId } }
                                                withContext(Dispatchers.Main) {
                                                    accountStatusMap[account.uid] = "Đang chuyển: $pName ($permissionLabel)..."
                                                    accountStatusMap[pageKey] = "Đang chuyển ($permissionLabel)..."
                                                }

                                                var tokenToUse = page.pageToken.ifBlank { account.bio }.trim()
                                                if (tokenToUse.isBlank() && account.note.contains("c_user=")) {
                                                    try {
                                                        val mgr = FacebookAccountManager()
                                                        val direct = mgr.getTokenFromCookie(account.note, account.phone.ifBlank { null })
                                                        if (direct != null && direct.bio.isNotBlank()) {
                                                            tokenToUse = direct.bio
                                                        }
                                                    } catch (_: Exception) {}
                                                }

                                                if (tokenToUse.isBlank()) {
                                                    failCount++
                                                    val msg = "Thiếu Token thực thi"
                                                    withContext(Dispatchers.Main) {
                                                        accountStatusMap[pageKey] = "Lỗi: $msg"
                                                        accountStatusMap[account.uid] = "Lỗi ($pName): $msg"
                                                    }
                                                    continue
                                                }

                                                val res = if (isFullPermission) {
                                                    engine.chuyenPageFullQuyen(
                                                        pageId = page.pageId,
                                                        targetUserId = receiverUid,
                                                        pageToken = tokenToUse
                                                    )
                                                } else {
                                                    engine.chuyenPageKhongFullQuyen(
                                                        pageId = page.pageId,
                                                        targetUserId = receiverUid,
                                                        pageToken = tokenToUse
                                                    )
                                                }

                                                if (res.isSuccess) {
                                                    transferredCount++
                                                    withContext(Dispatchers.Main) {
                                                        accountStatusMap[pageKey] = "Thành công ($permissionLabel)"
                                                    }
                                                } else {
                                                    failCount++
                                                    val msg = res.message ?: "Chuyển thất bại"
                                                    withContext(Dispatchers.Main) {
                                                        accountStatusMap[pageKey] = "Lỗi: $msg"
                                                        accountStatusMap[account.uid] = "Lỗi ($pName): $msg"
                                                    }
                                                }
                                                delay(1200L)
                                            }

                                            withContext(Dispatchers.Main) {
                                                accountStatusMap[account.uid] = "Hoàn tất: [Thành công: $transferredCount | Lỗi: $failCount]"
                                            }
                                        }

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Hoàn tất chuyển: $transferredCount Page thành công ($permissionLabel) sang UID $receiverUid!", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (_: kotlinx.coroutines.CancellationException) {
                                        withContext(Dispatchers.Main) {
                                            runningAccountUid?.let { accountStatusMap[it] = "Đã dừng" }
                                        }
                                    } catch (e: Throwable) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isRunning = false
                                            runningAccountUid = null
                                            runJob = null
                                        }
                                    }
                                }
                            }
                        },
                        enabled = (if (activeTab == 0) runningJobs.isEmpty() else !isRunning) && (
                            if (activeTab == 0) selectedForRunUids.isNotEmpty()
                            else selectedPageKeys.isNotEmpty() && transferReceiverUid.trim().isNotBlank()
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cobalt600,
                            disabledContainerColor = Cobalt600.copy(alpha = 0.35f)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Bắt đầu",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Nút ô vuông đỏ nhỏ Dừng chạy (Cạnh bên, không có chữ)
                    Button(
                        onClick = {
                            if (activeTab == 0) {
                                if (runningJobs.isEmpty()) return@Button
                                try {
                                    runningJobs.forEach { (uid, job) ->
                                        job.cancel()
                                        accountStatusMap[uid] = "Đã dừng"
                                    }
                                    runningJobs.clear()
                                    isRunning = false
                                    Toast.makeText(context.applicationContext, "Đã dừng tất cả tiến trình!", Toast.LENGTH_SHORT).show()
                                } catch (_: Throwable) {}
                            } else {
                                if (!isRunning) return@Button
                                try {
                                    runJob?.cancel()
                                    runJob = null
                                    runningAccountUid?.let { uid ->
                                        accountStatusMap[uid] = "Đã dừng"
                                    }
                                    isRunning = false
                                    runningAccountUid = null
                                    Toast.makeText(context.applicationContext, "Đã dừng tiến trình!", Toast.LENGTH_SHORT).show()
                                } catch (_: Throwable) {}
                            }
                        },
                        enabled = if (activeTab == 0) runningJobs.isNotEmpty() else isRunning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            disabledContainerColor = DangerRed.copy(alpha = 0.35f)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Dừng chạy",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF3F5F8)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hàng 2 Tabs: 1 là Reg Page, 2 là Chuyển Page
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE2E8F0).copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 1: Reg Page
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeTab == 0) Color.White else Color.Transparent)
                            .clickable { activeTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AddCircleOutline,
                                contentDescription = null,
                                tint = if (activeTab == 0) Cobalt600 else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Reg Page",
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (activeTab == 0) Cobalt600 else TextSecondary
                            )
                        }
                    }

                    // Tab 2: Chuyển Page
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeTab == 1) Color.White else Color.Transparent)
                            .clickable { activeTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                tint = if (activeTab == 1) Cobalt600 else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Chuyển Page",
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (activeTab == 1) Cobalt600 else TextSecondary
                            )
                        }
                    }
                }
            }

            // Nếu ở tab Chuyển Page -> hiện ô nhập UID người nhận
            if (activeTab == 1) {
                item {
                    val receiverInList = facebookAccounts.find { it.uid == transferReceiverUid.trim() }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (receiverInList != null) Color(0xFF16A34A) else CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("UID người nhận quyền quản trị Admin", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = TextPrimary)
                                if (transferReceiverUid.isNotBlank()) {
                                    Text(
                                        "Xóa",
                                        color = DangerRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { transferReceiverUid = "" }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = transferReceiverUid,
                                onValueChange = { input ->
                                    transferReceiverUid = input
                                    val trimmed = input.trim()
                                    if (trimmed in selectedForRunUids) {
                                        selectedForRunUids = selectedForRunUids - trimmed
                                        val toRemove = facebookAccounts.find { it.uid == trimmed }?.pages?.map { "${trimmed}_${it.pageId}" }.orEmpty().toSet()
                                        selectedPageKeys = selectedPageKeys - toRemove
                                    }
                                },
                                placeholder = { Text("Nhập UID Facebook người nhận...", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (receiverInList != null) Color(0xFF16A34A) else Cobalt600,
                                    unfocusedBorderColor = if (receiverInList != null) Color(0xFF16A34A).copy(alpha = 0.5f) else CardBorderColor
                                )
                            )

                            // Nhận diện trạng thái người nhận
                            if (transferReceiverUid.trim().isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                if (receiverInList != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            "Tài khoản trong danh sách: ${receiverInList.name.ifBlank { receiverInList.uid }} (${receiverInList.uid})",
                                            color = Color(0xFF16A34A),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            "(Người nhận ngoài: ${transferReceiverUid.trim()})",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Header: Tài khoản Facebook (được dời xuống một chút dưới 2 Tab)
            item {
                val validSourceAccounts = if (activeTab == 1) {
                    facebookAccounts.filter { it.uid != transferReceiverUid.trim() && it.pages.isNotEmpty() }
                } else {
                    facebookAccounts
                }
                val allValidUids = validSourceAccounts.map { it.uid }
                val isAllSelected = if (activeTab == 1) {
                    allValidUids.isNotEmpty() && validSourceAccounts.all { acc ->
                        acc.uid in selectedForRunUids && acc.pages.all { p -> "${acc.uid}_${p.pageId}" in selectedPageKeys }
                    }
                } else {
                    allValidUids.isNotEmpty() && allValidUids.all { it in selectedForRunUids }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tài khoản Facebook (${facebookAccounts.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Nút Tất cả
                        if (facebookAccounts.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (activeTab == 1) {
                                            if (isAllSelected) {
                                                selectedForRunUids = selectedForRunUids - allValidUids.toSet()
                                                val toRemove = validSourceAccounts.flatMap { acc -> acc.pages.map { "${acc.uid}_${it.pageId}" } }.toSet()
                                                selectedPageKeys = selectedPageKeys - toRemove
                                            } else {
                                                selectedForRunUids = selectedForRunUids + allValidUids.toSet()
                                                val toAdd = validSourceAccounts.flatMap { acc -> acc.pages.map { "${acc.uid}_${it.pageId}" } }.toSet()
                                                selectedPageKeys = selectedPageKeys + toAdd
                                            }
                                        } else {
                                            selectedForRunUids = if (isAllSelected) selectedForRunUids - allValidUids.toSet()
                                            else selectedForRunUids + allValidUids.toSet()
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isAllSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1877F2)),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Tất cả", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (selectedForRunUids.isNotEmpty() && activeTab == 0) {
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        selectedForRunUids.forEach { uid ->
                                            FacebookAccountsStore.removeAccount(context, uid)
                                        }
                                        withContext(Dispatchers.Main) {
                                            selectedForRunUids = emptySet()
                                            selectedPageKeys = emptySet()
                                            facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                            Toast.makeText(context, "Đã xóa tài khoản đã chọn", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(DangerRed.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Xóa đã chọn",
                                        tint = DangerRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Nút thêm tài khoản (+)
                        IconButton(
                            onClick = { showFacebookLoginSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1877F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Thêm tài khoản",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Danh sách tài khoản
            if (facebookAccounts.isEmpty()) {
                item {
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
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Chưa có tài khoản Facebook nào.", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Bấm vào đây hoặc nút dấu + để thêm tài khoản Facebook.",
                                color = Color(0xFF1877F2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                items(facebookAccounts, key = { it.uid }) { account ->
                    val isReceiver = activeTab == 1 && transferReceiverUid.isNotBlank() && account.uid.trim() == transferReceiverUid.trim()
                    val selectedPagesCount = if (activeTab == 1) account.pages.count { "${account.uid}_${it.pageId}" in selectedPageKeys } else 0
                    val isChecked = if (activeTab == 1) (if (isReceiver) false else selectedPagesCount > 0) else account.uid in selectedForRunUids
                    val isSourceAccount = activeTab == 1 && !isReceiver && selectedPagesCount > 0

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
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isReceiver -> Color(0xFFF0FDF4)
                                isSourceAccount -> Color(0xFFF8FAFF)
                                isChecked && activeTab == 0 -> Color(0xFFFAFCFF)
                                else -> CardWhite
                            }
                        ),
                        border = when {
                            isReceiver -> androidx.compose.foundation.BorderStroke(1.8.dp, Color(0xFF16A34A))
                            isSourceAccount -> androidx.compose.foundation.BorderStroke(1.8.dp, Cobalt600)
                            isChecked && activeTab == 0 -> androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1877F2))
                            else -> androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor)
                        },
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                if (activeTab == 0) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedForRunUids = if (account.uid in selectedForRunUids) selectedForRunUids - account.uid else selectedForRunUids + account.uid
                                    }
                                } else Modifier
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Hàng Header Profile mẹ: Chia làm 2 cụm rõ ràng (Trái: Info; Phải: Nút Chức năng)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 1. CỤM BÊN TRÁI: (Checkbox chỉ ở Tab 0) + Avatar + Tên + UID + Trạng thái Live
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Checkbox chỉ hiển thị khi ở Tab 0 Reg Page, TUYỆT ĐỐI KHÔNG HIỂN THỊ Ở TAB 1 CHUYỂN PAGE
                                    if (activeTab == 0) {
                                        Checkbox(
                                            checked = account.uid in selectedForRunUids,
                                            onCheckedChange = { checked ->
                                                selectedForRunUids = if (checked) selectedForRunUids + account.uid else selectedForRunUids - account.uid
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1877F2)),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    // Avatar Facebook
                                    val isThisFbUploading = isUploadingAvatar && targetFbAvatarChangeUid == account.uid
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
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
                                                    fontSize = 15.sp
                                                )
                                            }
                                        }

                                        // Lớp phủ và icon bút sửa ảnh
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Đổi avatar",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
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
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        val hasNoAvatar = account.avatar.isBlank() ||
                                                account.avatar.contains("silhouette") ||
                                                account.avatar.contains("blank_avatar")
                                        val isCheckpoint = account.uid in checkpointUids || hasNoAvatar || !account.isLive
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = account.name.ifBlank { account.uid },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            // Badge Live / Checkpoint
                                            Text(
                                                text = if (isCheckpoint) "• Checkpoint" else "• Live",
                                                color = if (isCheckpoint) Color(0xFFD32F2F) else Color(0xFF16A34A),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(
                                                        color = if (isCheckpoint) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "UID: ${account.uid}",
                                            color = TextSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // 2. CỤM BÊN PHẢI: Nút "Chọn nhận / Hủy nhận" + Nút Reload (Nằm ngang cạnh nhau, KHÔNG BAO GIỜ bị ép rớt dòng)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (activeTab == 1) {
                                        if (isReceiver) {
                                            // Nút Hủy nhận: Chiều ngang thoải mái, 1 hàng duy nhất
                                            Surface(
                                                onClick = { transferReceiverUid = "" },
                                                color = Color(0xFFFFEBEE),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2).copy(alpha = 0.8f))
                                            ) {
                                                Text(
                                                    text = "✕ Hủy nhận",
                                                    color = Color(0xFFD32F2F),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                                )
                                            }
                                        } else {
                                            // Nút Chọn nhận: Rộng rãi, chữ nằm ngang 1 dòng
                                            Surface(
                                                onClick = {
                                                    transferReceiverUid = account.uid
                                                    val childKeys = account.pages.map { "${account.uid}_${it.pageId}" }.toSet()
                                                    selectedPageKeys = selectedPageKeys - childKeys
                                                    selectedForRunUids = selectedForRunUids - account.uid
                                                },
                                                color = Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text(
                                                    text = "🎯 Chọn nhận",
                                                    color = TextPrimary,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }

                                    // Nút Reload (Làm mới)
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val mgr = FacebookAccountManager()
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
                                                        FacebookAccountsStore.addAccount(context, updated)
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                            Toast.makeText(context, "Đã làm mới thông tin: ${updated.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        val updated = account.copy(isLive = false)
                                                        FacebookAccountsStore.addAccount(context, updated)
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                            Toast.makeText(context, "Lỗi kiểm tra Facebook: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                                            FacebookAccountsStore.addAccount(context, updated)
                                                            withContext(Dispatchers.Main) {
                                                                avatarVersion = System.currentTimeMillis()
                                                                facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                                Toast.makeText(context, "Đã làm mới thông tin: ${updated.name}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            val updated = account.copy(isLive = false)
                                                            FacebookAccountsStore.addAccount(context, updated)
                                                            withContext(Dispatchers.Main) {
                                                                avatarVersion = System.currentTimeMillis()
                                                                facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                                Toast.makeText(context, "Lỗi kiểm tra Facebook: Cookie/Token DIE", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        val updated = account.copy(isLive = false)
                                                        FacebookAccountsStore.addAccount(context, updated)
                                                        withContext(Dispatchers.Main) {
                                                            avatarVersion = System.currentTimeMillis()
                                                            facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                            Toast.makeText(context, "Lỗi kiểm tra Facebook: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } else {
                                                    val updated = account.copy(isLive = false)
                                                    FacebookAccountsStore.addAccount(context, updated)
                                                    withContext(Dispatchers.Main) {
                                                        avatarVersion = System.currentTimeMillis()
                                                        facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                        Toast.makeText(context, "Lỗi: Tài khoản thiếu Token và Cookie", Toast.LENGTH_SHORT).show()
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
                                }
                            }

                            // 3. Vị trí đặt Badge trạng thái "ĐANG CHUYỂN" hoặc "ACC NHẬN" trên dải băng riêng biệt
                            if (activeTab == 1) {
                                if (isReceiver) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF22C55E).copy(alpha = 0.12f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🎯 TÀI KHOẢN NHẬN ADMIN",
                                                color = Color(0xFF16A34A),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else if (isSourceAccount && selectedPagesCount > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1877F2).copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1877F2).copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📤 ĐANG CHUYỂN ($selectedPagesCount page đã chọn)",
                                                color = Cobalt600,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Khu vực hiển thị trạng thái chạy trực tiếp của tài khoản này
                            val liveStatus = accountStatusMap[account.uid]
                            val isRunningThis = (activeTab == 0 && runningJobs.containsKey(account.uid)) || (activeTab == 1 && isRunning && runningAccountUid == account.uid)
                            val isAccError = liveStatus != null && (liveStatus.contains("Lỗi", ignoreCase = true) || liveStatus.contains("Thất bại", ignoreCase = true) || liveStatus.contains("Checkpoint", ignoreCase = true))
                            val isAccStopped = liveStatus != null && liveStatus.contains("Đã dừng", ignoreCase = true)

                            if (liveStatus != null || isRunningThis) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isAccError -> DangerRed.copy(alpha = 0.08f)
                                                isAccStopped -> Color(0xFFF59E0B).copy(alpha = 0.08f)
                                                else -> Cobalt600.copy(alpha = 0.08f)
                                            }
                                        )
                                        .clickable(enabled = isAccError) {
                                            selectedErrorDetail = Pair(account.name.ifBlank { account.uid }, liveStatus ?: "Lỗi từ Facebook")
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isRunningThis) {
                                            CircularProgressIndicator(
                                                strokeWidth = 1.8.dp,
                                                modifier = Modifier.size(12.dp),
                                                color = Cobalt600
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isAccError -> DangerRed
                                                            isAccStopped -> Color(0xFFF59E0B)
                                                            else -> Color(0xFF16A34A)
                                                        }
                                                    )
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }

                                        Text(
                                            text = liveStatus ?: "Đang xử lý...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                isAccError -> DangerRed
                                                isAccStopped -> Color(0xFFD97706)
                                                else -> Cobalt600
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        // Nút chấm than màu đỏ để xem chi tiết lỗi chính xác từ Facebook
                                        if (isAccError) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(DangerRed.copy(alpha = 0.15f))
                                                    .clickable {
                                                        selectedErrorDetail = Pair(account.name.ifBlank { account.uid }, liveStatus ?: "Lỗi từ Facebook")
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.Info,
                                                    contentDescription = "Xem chi tiết lỗi",
                                                    tint = DangerRed,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isRunningThis) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DangerRed.copy(alpha = 0.15f))
                                                .clickable {
                                                    try {
                                                        if (activeTab == 0) {
                                                            runningJobs[account.uid]?.cancel()
                                                            runningJobs.remove(account.uid)
                                                            accountStatusMap[account.uid] = "Đã dừng"
                                                            if (runningJobs.isEmpty()) {
                                                                isRunning = false
                                                            }
                                                        } else {
                                                            runJob?.cancel()
                                                            runJob = null
                                                            accountStatusMap[account.uid] = "Đã dừng"
                                                            isRunning = false
                                                            runningAccountUid = null
                                                        }
                                                        Toast.makeText(context.applicationContext, "Đã dừng tài khoản ${account.name.ifBlank { account.uid }}!", Toast.LENGTH_SHORT).show()
                                                    } catch (_: Throwable) {}
                                                }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Stop,
                                                    contentDescription = "Dừng",
                                                    tint = DangerRed,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "Dừng",
                                                    color = DangerRed,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Trạng thái Page
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            if (activeTab == 1) "Danh sách Fanpage (Đã chọn: $selectedPagesCount/${account.pages.size} page):"
                                            else "Danh sách Fanpage (${account.pages.size}):",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (activeTab == 1 && selectedPagesCount > 0) Cobalt600 else TextSecondary
                                        )
                                        if (activeTab == 1 && !isReceiver && account.pages.isNotEmpty()) {
                                            Spacer(Modifier.width(8.dp))
                                            val isAllThisPagesSelected = account.pages.all { "${account.uid}_${it.pageId}" in selectedPageKeys }
                                            Text(
                                                text = if (isAllThisPagesSelected) "Bỏ chọn tất cả" else "Chọn tất cả",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Cobalt600,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        val myKeys = account.pages.map { "${account.uid}_${it.pageId}" }.toSet()
                                                        if (isAllThisPagesSelected) {
                                                            selectedPageKeys = selectedPageKeys - myKeys
                                                            selectedForRunUids = selectedForRunUids - account.uid
                                                        } else {
                                                            selectedPageKeys = selectedPageKeys + myKeys
                                                            selectedForRunUids = selectedForRunUids + account.uid
                                                        }
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Nút chấm than (i) xem chi tiết Info của account Facebook
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
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    account.pages.forEach { page ->
                                        val pageDisplayUid = livePageUids[page.pageId] ?: page.displayUid
                                        val effectivePageUid = (page.additionalProfileId.takeIf { it.isNotBlank() && it.startsWith("615") }
                                            ?: pageDisplayUid.takeIf { it.isNotBlank() && it.startsWith("615") }
                                            ?: page.additionalProfileId.takeIf { it.isNotBlank() }
                                            ?: pageDisplayUid.takeIf { it.isNotBlank() }
                                            ?: page.pageId).trim()

                                        val avatarToDisplay = livePageAvatars[page.pageId] ?: (
                                            if (page.avatar.isNotBlank() && !page.avatar.contains("silhouette") && !page.avatar.endsWith(".gif") && !page.avatar.contains(page.displayUid)) page.avatar
                                            else "https://graph.facebook.com/v21.0/${page.pageId}/picture?type=large"
                                        )

                                        val pageKey = "${account.uid}_${page.pageId}"
                                        val isPageChecked = pageKey in selectedPageKeys
                                        val pageStatus = accountStatusMap[pageKey] ?: accountStatusMap[effectivePageUid]
                                        val isPageRunning = isRunning && runningAccountUid == pageKey
                                        val isPageError = pageStatus != null && (pageStatus.contains("Lỗi", ignoreCase = true) || pageStatus.contains("Thất bại", ignoreCase = true))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (activeTab == 1 && isPageChecked) Color(0xFFEFF6FF)
                                                    else Color(0xFFF8FAFC)
                                                )
                                                .border(
                                                    if (activeTab == 1 && isPageChecked) 1.2.dp else 0.8.dp,
                                                    if (activeTab == 1 && isPageChecked) Cobalt600.copy(alpha = 0.6f) else Color(0xFFE2E8F0),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable(enabled = activeTab == 1 && !isReceiver) {
                                                    val willCheck = !isPageChecked
                                                    if (willCheck) {
                                                        selectedPageKeys = selectedPageKeys + pageKey
                                                        selectedForRunUids = selectedForRunUids + account.uid
                                                    } else {
                                                        selectedPageKeys = selectedPageKeys - pageKey
                                                        val remaining = account.pages.any { it.pageId != page.pageId && "${account.uid}_${it.pageId}" in selectedPageKeys }
                                                        if (!remaining) {
                                                            selectedForRunUids = selectedForRunUids - account.uid
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 7.dp)
                                        ) {
                                            // Checkbox chọn page con (Chỉ hiện và tương tác ở Tab 1 Chuyển Quyền Page)
                                            if (activeTab == 1) {
                                                Checkbox(
                                                    checked = isPageChecked,
                                                    enabled = !isReceiver,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            selectedPageKeys = selectedPageKeys + pageKey
                                                            selectedForRunUids = selectedForRunUids + account.uid
                                                        } else {
                                                            selectedPageKeys = selectedPageKeys - pageKey
                                                            val remaining = account.pages.any { it.pageId != page.pageId && "${account.uid}_${it.pageId}" in selectedPageKeys }
                                                            if (!remaining) {
                                                                selectedForRunUids = selectedForRunUids - account.uid
                                                            }
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = Cobalt600,
                                                        uncheckedColor = Color(0xFF94A3B8)
                                                    ),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }

                                            // Avatar Page
                                            if (avatarToDisplay.isNotBlank()) {
                                                AsyncImage(
                                                    model = avatarToDisplay,
                                                    contentDescription = "Page Avatar",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .border(1.dp, Color(0xFF1877F2).copy(alpha = 0.3f), CircleShape)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF1877F2).copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Flag,
                                                        contentDescription = null,
                                                        tint = Color(0xFF1877F2),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Page: ${page.pageName.ifBlank { effectivePageUid.ifBlank { page.pageId } }}",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (effectivePageUid.isNotBlank()) {
                                                    Text(
                                                        text = "UID: $effectivePageUid",
                                                        fontSize = 10.5.sp,
                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                        color = TextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (!pageStatus.isNullOrBlank() || isPageRunning) {
                                                    Spacer(Modifier.height(3.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (isPageRunning) {
                                                            CircularProgressIndicator(
                                                                strokeWidth = 1.5.dp,
                                                                modifier = Modifier.size(10.dp),
                                                                color = Cobalt600
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                        }
                                                        Text(
                                                            text = pageStatus ?: "Đang xử lý...",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = when {
                                                                isPageError -> DangerRed
                                                                isPageRunning -> Cobalt600
                                                                else -> Color(0xFF16A34A)
                                                            },
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // Nút (i) xem chi tiết Page (Ảnh đại diện, Ảnh bìa cover, UID, Token...)
                                            IconButton(
                                                onClick = { selectedPageDetail = Pair(account, page) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF1877F2).copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Info,
                                                        contentDescription = "Chi tiết Fanpage",
                                                        tint = Color(0xFF1877F2),
                                                        modifier = Modifier.size(15.dp)
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

    // Modal Cấu hình Reg Page (trượt từ dưới lên)
    if (showConfigSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConfigSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Cấu hình Reg Page",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = { showConfigSheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = TextSecondary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Hàng 1: Chọn Tên Việt / Tên Tây
                Text("1. Loại tên Page ngẫu nhiên", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Tên Việt", "Tên Tây").forEach { type ->
                        val isSelected = nameTypeOption == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Cobalt600 else CardBorderColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(if (isSelected) Cobalt600.copy(alpha = 0.08f) else Color(0xFFF8FAFC))
                                .clickable { nameTypeOption = type }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isSelected) Cobalt600 else TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Hàng 2: Số lượng Reg Page (Mặc định 15)
                Text("2. Số lượng Reg Page", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = regCountInput,
                    onValueChange = { regCountInput = it.filter { char -> char.isDigit() } },
                    placeholder = { Text("Mặc định: 15") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Text("Page", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cobalt600,
                        unfocusedBorderColor = CardBorderColor
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Hàng 3: Thời gian tạo giữa các page (Mặc định 2000 giây)
                Text("3. Thời gian tạo giữa các Page (Giây)", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = delaySecondsInput,
                    onValueChange = { delaySecondsInput = it.filter { char -> char.isDigit() } },
                    placeholder = { Text("Mặc định: 2000") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Text("Giây", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cobalt600,
                        unfocusedBorderColor = CardBorderColor
                    )
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { showConfigSheet = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cobalt600),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Lưu cấu hình", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    if (selectedErrorDetail != null) {
        val (accName, errDetail) = selectedErrorDetail!!
        ModalBottomSheet(
            onDismissRequest = { selectedErrorDetail = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DangerRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chi tiết lỗi Facebook",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tài khoản: $accName",
                            fontSize = 12.5.sp,
                            color = Cobalt600,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = errDetail,
                            fontSize = 12.5.sp,
                            color = DangerRed,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { selectedErrorDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Cobalt600),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Đóng", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (selectedFbDetailAccount != null) {
        FacebookAccountDetailSheet(
            account = selectedFbDetailAccount!!,
            onDismiss = { selectedFbDetailAccount = null }
        )
    }

    if (showFacebookLoginSheet) {
        FacebookLoginBottomSheet(
            onDismiss = { showFacebookLoginSheet = false },
            onAccountSaved = {
                facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                showFacebookLoginSheet = false
            }
        )
    }

    // Modal Cấu hình Chuyển Page (trượt từ dưới lên)
    if (showTransferConfigSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTransferConfigSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Cấu hình Chuyển Page",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            "Chọn 1 trong 2 loại quyền gán sang UID nhận",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { showTransferConfigSheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = TextSecondary)
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 1. Chức năng Full quyền
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFullPermission) Cobalt600.copy(alpha = 0.06f) else Color(0xFFF8FAFC)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isFullPermission) 1.5.dp else 1.dp,
                        color = if (isFullPermission) Cobalt600 else CardBorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { isFullPermission = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Full quyền",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isFullPermission) Cobalt600 else TextPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isFullPermission) Cobalt600 else Color(0xFFE2E8F0))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Toàn quyền",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFullPermission) Color.White else TextSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Toàn quyền quản trị Admin (gồm quyền MANAGE: quản lý người dùng, xóa Page, đăng bài, tin nhắn, quảng cáo).",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = isFullPermission,
                            onCheckedChange = { isFullPermission = true },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Cobalt600
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 2. Chức năng No full
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isFullPermission) Cobalt600.copy(alpha = 0.06f) else Color(0xFFF8FAFC)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (!isFullPermission) 1.5.dp else 1.dp,
                        color = if (!isFullPermission) Cobalt600 else CardBorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { isFullPermission = false }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "No full",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (!isFullPermission) Cobalt600 else TextPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!isFullPermission) Color(0xFF0284C7) else Color(0xFFE2E8F0))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Quyền tác vụ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isFullPermission) Color.White else TextSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Chỉ cấp quyền tác vụ (Tạo nội dung, Tin nhắn, Kiểm duyệt, Quảng cáo). Tuyệt đối KHÔNG có quyền MANAGE quản lý hoặc xóa Trang.",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = !isFullPermission,
                            onCheckedChange = { isFullPermission = false },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Cobalt600
                            )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { showTransferConfigSheet = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cobalt600),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Lưu cấu hình", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }

    if (selectedPageDetail != null) {
        val (parentAcc, pageItem) = selectedPageDetail!!
        FacebookPageDetailSheet(
            parentAccount = parentAcc,
            page = pageItem,
            onUidResolved = { uid615 ->
                livePageUids[pageItem.pageId] = uid615
            },
            onMediaUpdated = { avt, cov ->
                livePageAvatars[pageItem.pageId] = avt
            },
            onDismiss = { selectedPageDetail = null }
        )
    }
}

