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
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.facebook.FacebookPageService
import com.cayxu.app.ui.screens.xsmm.FacebookAccountDetailSheet
import com.cayxu.app.ui.screens.xsmm.FacebookLoginBottomSheet
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

    // Trạng thái đang chạy và Job điều khiển
    var isRunning by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }
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
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang đổi ảnh đại diện Facebook...", Toast.LENGTH_SHORT).show()
                    }
                    val fbManager = FacebookAccountManager()
                    val token = acc.bio.ifBlank { null }
                    var newPicUrl: String? = null
                    if (!token.isNullOrBlank()) {
                        newPicUrl = fbManager.changeProfilePicture(token, bytes, acc.phone.ifBlank { null })
                    }
                    val fallbackPic = "https://graph.facebook.com/v19.0/$uid/picture?type=large"
                    val finalAvatar = newPicUrl ?: fallbackPic
                    val updatedAcc = acc.copy(avatar = finalAvatar)
                    FacebookAccountsStore.addAccount(context, updatedAcc)
                    withContext(Dispatchers.Main) {
                        avatarVersion = System.currentTimeMillis()
                        facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                        Toast.makeText(context, "Đổi avatar Facebook thành công!", Toast.LENGTH_SHORT).show()
                        isUploadingAvatar = false
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
                    // Nút Cấu hình (chỉ hiện khi ở tab Reg Page)
                    if (activeTab == 0) {
                        OutlinedButton(
                            onClick = { showConfigSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Cobalt600),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cobalt600),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cấu hình", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    // Nút tam giác nhỏ Bắt đầu chạy (Icon Play, không có chữ)
                    Button(
                        onClick = {
                            if (isRunning) return@Button

                            val targetAccounts = facebookAccounts.filter { it.uid in selectedForRunUids }
                            if (targetAccounts.isEmpty()) {
                                Toast.makeText(context, "Vui lòng tick chọn ít nhất 1 tài khoản Facebook!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (activeTab == 0) {
                                // Chạy Reg Page
                                val count = regCountInput.toIntOrNull() ?: 15
                                val delaySec = delaySecondsInput.toIntOrNull() ?: 2000

                                isRunning = true
                                runJob = scope.launch(Dispatchers.IO) {
                                    var totalCreatedAll = 0
                                    try {
                                        for (account in targetAccounts) {
                                            if (!isActive) break
                                            runningAccountUid = account.uid
                                            var token = account.bio.ifBlank { null }
                                            
                                            withContext(Dispatchers.Main) {
                                                try {
                                                    accountStatusMap[account.uid] = "Đang kiểm tra Token..."
                                                } catch (_: Throwable) {}
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
                                                withContext(Dispatchers.Main) {
                                                    try {
                                                        accountStatusMap[account.uid] = "Lỗi: Thiếu Token EAAA"
                                                        Toast.makeText(context.applicationContext, "Tài khoản ${account.name} thiếu Token EAAA!", Toast.LENGTH_SHORT).show()
                                                    } catch (_: Throwable) {}
                                                }
                                                continue
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
                                                    try {
                                                        accountStatusMap[account.uid] = "Đang tạo ($idx/$count): $pageName"
                                                    } catch (_: Throwable) {}
                                                }

                                                var isCreated = false
                                                try {
                                                    val res = pageService.createFacebookPage(pageName, token)
                                                    isCreated = true
                                                    createdSuccessCount++
                                                    totalCreatedAll++
                                                    try {
                                                        val updatedPages = pageService.getPages(token)
                                                        if (updatedPages.isNotEmpty()) {
                                                            val updatedAcc = account.copy(pages = updatedPages)
                                                            FacebookAccountsStore.addAccount(context, updatedAcc)
                                                        }
                                                    } catch (_: Throwable) {}
                                                    withContext(Dispatchers.Main) {
                                                        try {
                                                            facebookAccounts = FacebookAccountsStore.getAccounts(context)
                                                            accountStatusMap[account.uid] = "Đã tạo ($createdSuccessCount/$count): $pageName"
                                                            Toast.makeText(context.applicationContext, "Đã tạo Fanpage: $pageName", Toast.LENGTH_SHORT).show()
                                                        } catch (_: Throwable) {}
                                                    }
                                                } catch (e: Throwable) {
                                                    val errText = e.message ?: "Thất bại"
                                                    lastErrorMsg = errText
                                                    withContext(Dispatchers.Main) {
                                                        try {
                                                            accountStatusMap[account.uid] = "Lỗi ($idx/$count): $errText"
                                                            Toast.makeText(context.applicationContext, "Tạo thất bại: $errText", Toast.LENGTH_SHORT).show()
                                                        } catch (_: Throwable) {}
                                                    }
                                                }

                                                // Đếm ngược thời gian nếu tạo THÀNH CÔNG và còn lần tạo tiếp
                                                if (isCreated && idx < count && isActive) {
                                                    for (s in delaySec downTo 1) {
                                                        if (!isActive) break
                                                        withContext(Dispatchers.Main) {
                                                            try {
                                                                accountStatusMap[account.uid] = "Chờ tạo tiếp ($createdSuccessCount/$count): ${s}s"
                                                            } catch (_: Throwable) {}
                                                        }
                                                        delay(1000L)
                                                    }
                                                } else if (!isCreated) {
                                                    // Nếu tạo lỗi, dừng vòng lặp tài khoản này để không giam người dùng
                                                    break
                                                }
                                            }

                                            if (isActive) {
                                                withContext(Dispatchers.Main) {
                                                    try {
                                                        if (createdSuccessCount == count) {
                                                            accountStatusMap[account.uid] = "Hoàn tất $count/$count Page"
                                                        } else if (createdSuccessCount > 0) {
                                                            accountStatusMap[account.uid] = "Đã tạo $createdSuccessCount/$count Page (${lastErrorMsg ?: "Dừng"})"
                                                        } else {
                                                            accountStatusMap[account.uid] = "Thất bại: ${lastErrorMsg ?: "Lỗi tạo page"}"
                                                        }
                                                    } catch (_: Throwable) {}
                                                }
                                            }
                                        }

                                        if (isActive) {
                                            withContext(Dispatchers.Main) {
                                                try {
                                                    if (totalCreatedAll > 0) {
                                                        Toast.makeText(context.applicationContext, "Đã tạo thành công $totalCreatedAll Page!", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context.applicationContext, "Tiến trình kết thúc (0 Page được tạo)", Toast.LENGTH_SHORT).show()
                                                    }
                                                    facebookAccounts = FacebookAccountsStore.getAccounts(context, forceReload = true)
                                                } catch (_: Throwable) {}
                                            }
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        withContext(Dispatchers.Main) {
                                            try {
                                                runningAccountUid?.let { uid ->
                                                    accountStatusMap[uid] = "Đã dừng"
                                                }
                                            } catch (_: Throwable) {}
                                        }
                                    } catch (e: Throwable) {
                                        val errMsg = e.localizedMessage ?: "Lỗi xử lý"
                                        withContext(Dispatchers.Main) {
                                            try {
                                                runningAccountUid?.let { uid ->
                                                    accountStatusMap[uid] = "Lỗi: $errMsg"
                                                }
                                                Toast.makeText(context.applicationContext, "Lỗi thực thi: $errMsg", Toast.LENGTH_SHORT).show()
                                            } catch (_: Throwable) {}
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isRunning = false
                                            runningAccountUid = null
                                            runJob = null
                                        }
                                    }
                                }
                            } else {
                                // Chuyển Page
                                if (transferReceiverUid.trim().isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập UID người nhận quyền admin!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isRunning = true
                                runJob = scope.launch(Dispatchers.IO) {
                                    try {
                                        var transferredCount = 0
                                        for (account in targetAccounts) {
                                            if (!isActive) break
                                            for (page in account.pages) {
                                                if (!isActive) break
                                                if (page.pageToken.isNotBlank()) {
                                                    val ok = pageService.transferPageRole(page.pageId, page.pageToken, transferReceiverUid.trim())
                                                    if (ok) transferredCount++
                                                }
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Đã chuyển $transferredCount Page sang UID $transferReceiverUid!", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (_: kotlinx.coroutines.CancellationException) {
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
                        enabled = !isRunning && selectedForRunUids.isNotEmpty(),
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
                        },
                        enabled = isRunning,
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
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("UID người nhận quyền quản trị Admin", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = transferReceiverUid,
                                onValueChange = { transferReceiverUid = it },
                                placeholder = { Text("Nhập UID Facebook người nhận...", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Cobalt600,
                                    unfocusedBorderColor = CardBorderColor
                                )
                            )
                        }
                    }
                }
            }

            // Header: Tài khoản Facebook (được dời xuống một chút dưới 2 Tab)
            item {
                val allFbUids = facebookAccounts.map { it.uid }
                val isAllSelected = allFbUids.isNotEmpty() && allFbUids.all { it in selectedForRunUids }

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
                                        selectedForRunUids = if (isAllSelected) selectedForRunUids - allFbUids.toSet()
                                        else selectedForRunUids + allFbUids.toSet()
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

                        if (selectedForRunUids.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        selectedForRunUids.forEach { uid ->
                                            FacebookAccountsStore.removeAccount(context, uid)
                                        }
                                        withContext(Dispatchers.Main) {
                                            selectedForRunUids = emptySet()
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
                        border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1877F2)) else androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
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

                                // Avatar Facebook
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

                                    // Lớp phủ và icon bút sửa ảnh
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

                                        // Badge Live/Die
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

                            // Khu vực hiển thị trạng thái chạy trực tiếp của tài khoản này
                            val liveStatus = accountStatusMap[account.uid]
                            val isRunningThis = isRunning && runningAccountUid == account.uid
                            val isAccError = liveStatus != null && (liveStatus.contains("Lỗi", ignoreCase = true) || liveStatus.contains("Thất bại", ignoreCase = true))
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
                                                        runJob?.cancel()
                                                        runJob = null
                                                        accountStatusMap[account.uid] = "Đã dừng"
                                                        isRunning = false
                                                        runningAccountUid = null
                                                        Toast.makeText(context.applicationContext, "Đã dừng tài khoản ${account.name}!", Toast.LENGTH_SHORT).show()
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
                                    Text(
                                        "Danh sách Fanpage (${account.pages.size}):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
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
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    account.pages.forEach { page ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF8FAFC))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Flag,
                                                contentDescription = null,
                                                tint = Color(0xFF1877F2),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Page: ${page.pageName.ifBlank { page.displayUid }}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "UID: ${page.displayUid}",
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
        AlertDialog(
            onDismissRequest = { selectedErrorDetail = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DangerRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chi tiết lỗi Facebook",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Tài khoản: $accName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cobalt600
                    )
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errDetail,
                            fontSize = 12.sp,
                            color = DangerRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedErrorDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Cobalt600),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Đóng", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
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
}

