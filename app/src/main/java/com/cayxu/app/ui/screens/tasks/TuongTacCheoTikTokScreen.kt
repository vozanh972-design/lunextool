package com.cayxu.app.ui.screens.tasks

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.local.TtcAccount
import com.cayxu.app.data.local.TtcAccountsStore
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Bảng màu chuẩn TikTok & TTC
private val TikTokPink = Color(0xFFFE2C55)
private val TikTokCyan = Color(0xFF25F4EE)
private val TikTokDark = Color(0xFF0F172A)
private val TikTokCardBg = Color(0xFFFFFFFF)
private val TikTokBorder = Color(0xFFE2E8F0)
private val TikTokBg = Color(0xFFF8FAFC)
private val TikTokTextPrimary = Color(0xFF1E293B)
private val TikTokTextSecondary = Color(0xFF64748B)
private val SuccessGreen = Color(0xFF10B981)

/**
 * Màn hình Tương Tác Chéo TikTok (Tuongtaccheo TikTok)
 * Giao diện quản lý danh sách nick TikTok và cấu hình làm nhiệm vụ tương tác chéo.
 * (Hiện tại là tầng UI sẵn sàng, chưa kèm logic chạy ngầm).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuongTacCheoTikTokScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Danh sách tài khoản TikTok từ máy
    var tikTokAccounts by remember { mutableStateOf(TikTokAccountsStore.getAccounts(context)) }
    // Danh sách tài khoản TTC
    var ttcAccounts by remember { mutableStateOf(TtcAccountsStore.getAccounts(context)) }

    // Quản lý selection
    var selectedHandles by remember { mutableStateOf<Set<String>>(emptySet()) }

    // BottomSheet states
    var showConfigSheet by remember { mutableStateOf(false) }
    var showAddAccountSheet by remember { mutableStateOf(false) }
    var selectedDetailAccount by remember { mutableStateOf<TikTokAccount?>(null) }

    // State chạy giả lập cho giao diện
    val runningHandles = remember { mutableStateListOf<String>() }
    val statusMap = remember { mutableStateMapOf<String, String>() }

    fun reloadData() {
        tikTokAccounts = TikTokAccountsStore.getAccounts(context)
        ttcAccounts = TtcAccountsStore.getAccounts(context)
    }

    val activeTtcAccount = ttcAccounts.firstOrNull { it.isLive } ?: ttcAccounts.firstOrNull()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(TikTokBg),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TikTokDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = TikTokPink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Tuongtaccheo TikTok",
                                    fontSize = 17.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TikTokTextPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = TikTokPink.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "BETA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TikTokPink,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Tương tác chéo tài khoản TikTok",
                                fontSize = 12.sp,
                                color = TikTokTextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TikTokTextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { reloadData() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Làm mới", tint = TikTokTextPrimary)
                    }
                    IconButton(onClick = { showAddAccountSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Thêm nick", tint = TikTokTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TikTokCardBg)
            )
        },
        bottomBar = {
            // Footer điều khiển
            Surface(
                color = TikTokCardBg,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, TikTokBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showConfigSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = BorderStroke(1.dp, TikTokBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TikTokTextPrimary)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cấu hình", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    val isRunningAny = runningHandles.isNotEmpty()
                    Button(
                        onClick = {
                            if (isRunningAny) {
                                runningHandles.clear()
                                statusMap.clear()
                                Toast.makeText(context, "Đã tạm dừng toàn bộ nhiệm vụ!", Toast.LENGTH_SHORT).show()
                            } else {
                                if (selectedHandles.isEmpty()) {
                                    Toast.makeText(context, "Vui lòng chọn ít nhất 1 nick TikTok để chạy!", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedHandles.forEach { h ->
                                        if (!runningHandles.contains(h)) runningHandles.add(h)
                                        statusMap[h] = "Đang chờ lấy nhiệm vụ TikTok..."
                                    }
                                    Toast.makeText(context, "Giao diện Tuongtaccheo TikTok đã sẵn sàng. Logic chạy sẽ sớm ra mắt!", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunningAny) Color(0xFFEF4444) else TikTokDark
                        )
                    ) {
                        Icon(
                            imageVector = if (isRunningAny) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isRunningAny) "Dừng tất cả" else "Bắt đầu chạy (${selectedHandles.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // 1. BANNER TÀI KHOẢN TƯƠNG TÁC CHÉO
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TikTokCardBg),
                    border = BorderStroke(1.dp, TikTokBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(TikTokDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tài khoản TTC",
                                fontSize = 12.sp,
                                color = TikTokTextSecondary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = activeTtcAccount?.username?.ifBlank { "Chưa có tài khoản TTC" } ?: "Chưa có tài khoản TTC",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TikTokTextPrimary
                            )
                            if (activeTtcAccount != null) {
                                Spacer(Modifier.height(2.dp))
                                val coinsStr = NumberFormat.getNumberInstance(Locale.US).format(activeTtcAccount.coins)
                                Text(
                                    text = "$coinsStr xu",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SuccessGreen
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (activeTtcAccount != null) SuccessGreen.copy(alpha = 0.12f) else Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = if (activeTtcAccount != null) "Sẵn sàng" else "Chưa đăng nhập",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTtcAccount != null) SuccessGreen else TikTokTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 2. THANH TIÊU ĐỀ DANH SÁCH & BỘ CHỌN
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Danh sách Nick TikTok",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TikTokTextPrimary
                        )
                        Text(
                            text = "${tikTokAccounts.size} tài khoản trên máy",
                            fontSize = 12.5.sp,
                            color = TikTokTextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selectedHandles = if (selectedHandles.size == tikTokAccounts.size) {
                                    emptySet()
                                } else {
                                    tikTokAccounts.map { it.handle }.toSet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (selectedHandles.size == tikTokAccounts.size && tikTokAccounts.isNotEmpty()) "Bỏ chọn hết" else "Chọn tất cả",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TikTokPink
                            )
                        }
                    }
                }
            }

            // 3. DANH SÁCH TÀI KHOẢN TIKTOK
            if (tikTokAccounts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TikTokCardBg),
                        border = BorderStroke(1.dp, TikTokBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = TikTokTextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Chưa có tài khoản TikTok nào",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TikTokTextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Thêm tài khoản TikTok mới hoặc quét tài khoản từ ứng dụng TikTok trên máy",
                                fontSize = 13.sp,
                                color = TikTokTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showAddAccountSheet = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TikTokDark)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Thêm nick TikTok")
                            }
                        }
                    }
                }
            } else {
                items(tikTokAccounts, key = { it.handle }) { acc ->
                    val cleanHandle = acc.handle.trim().removePrefix("@")
                    val isSelected = selectedHandles.contains(acc.handle)
                    val isRunning = runningHandles.contains(acc.handle)
                    val currentStatus = statusMap[acc.handle] ?: if (acc.isLive) "Sẵn sàng" else "Chưa sẵn sàng"

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFF8FAFC) else TikTokCardBg
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) TikTokDark else TikTokBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDetailAccount = acc }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox chọn
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedHandles = if (checked) {
                                        selectedHandles + acc.handle
                                    } else {
                                        selectedHandles - acc.handle
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = TikTokDark,
                                    checkmarkColor = Color.White
                                )
                            )

                            Spacer(Modifier.width(6.dp))

                            // Avatar nick TikTok
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (acc.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = acc.avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = TikTokTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            // Thông tin Handle + Tên hiển thị + Trạng thái
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.displayName.ifBlank { "@$cleanHandle" },
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TikTokTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = "@$cleanHandle",
                                    fontSize = 12.sp,
                                    color = TikTokTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isRunning) SuccessGreen else Color(0xFF94A3B8))
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = currentStatus,
                                        fontSize = 11.5.sp,
                                        color = if (isRunning) SuccessGreen else TikTokTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            // Nút Play / Stop độc lập từng nick
                            IconButton(
                                onClick = {
                                    if (isRunning) {
                                        runningHandles.remove(acc.handle)
                                        statusMap.remove(acc.handle)
                                        Toast.makeText(context, "Đã dừng @$cleanHandle", Toast.LENGTH_SHORT).show()
                                    } else {
                                        runningHandles.add(acc.handle)
                                        statusMap[acc.handle] = "Đang nhận nhiệm vụ..."
                                        Toast.makeText(context, "Bắt đầu chạy cho @$cleanHandle", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) Color(0xFFFEE2E2) else Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isRunning) Color(0xFFEF4444) else TikTokDark,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    // ==================== BOTTOMSHEET CẤU HÌNH NHIỆM VỤ TTC TIKTOK ====================
    if (showConfigSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConfigSheet = false },
            containerColor = TikTokCardBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            var jobFollow by remember { mutableStateOf(true) }
            var jobLike by remember { mutableStateOf(true) }
            var jobComment by remember { mutableStateOf(false) }
            var delayMin by remember { mutableStateOf("10") }
            var delayMax by remember { mutableStateOf("25") }
            var maxJobCount by remember { mutableStateOf("50") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cấu hình Tuongtaccheo TikTok",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TikTokTextPrimary
                    )
                    IconButton(onClick = { showConfigSheet = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = TikTokTextSecondary)
                    }
                }

                Divider(color = TikTokBorder)

                // Loại nhiệm vụ
                Text("Loại nhiệm vụ thực hiện", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TikTokTextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = jobFollow,
                        onClick = { jobFollow = !jobFollow },
                        label = { Text("Follow TikTok") },
                        leadingIcon = if (jobFollow) { { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) } } else null
                    )
                    FilterChip(
                        selected = jobLike,
                        onClick = { jobLike = !jobLike },
                        label = { Text("Like / Tim Video") },
                        leadingIcon = if (jobLike) { { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) } } else null
                    )
                    FilterChip(
                        selected = jobComment,
                        onClick = { jobComment = !jobComment },
                        label = { Text("Comment") },
                        leadingIcon = if (jobComment) { { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) } } else null
                    )
                }

                // Cài đặt Delay
                Text("Thời gian nghỉ giữa các nhiệm vụ (giây)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TikTokTextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = delayMin,
                        onValueChange = { delayMin = it },
                        label = { Text("Tối thiểu (s)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = delayMax,
                        onValueChange = { delayMax = it },
                        label = { Text("Tối đa (s)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Giới hạn nhiệm vụ
                OutlinedTextField(
                    value = maxJobCount,
                    onValueChange = { maxJobCount = it },
                    label = { Text("Dừng sau khi đạt số nhiệm vụ") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        showConfigSheet = false
                        Toast.makeText(context, "Đã lưu cấu hình Tuongtaccheo TikTok!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TikTokDark)
                ) {
                    Text("Lưu cấu hình", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                }
            }
        }
    }

    // ==================== BOTTOMSHEET THÊM TÀI KHOẢN TIKTOK ====================
    if (showAddAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddAccountSheet = false },
            containerColor = TikTokCardBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            var inputHandle by remember { mutableStateOf("") }
            var inputDisplayName by remember { mutableStateOf("") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thêm Nick TikTok",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TikTokTextPrimary
                    )
                    IconButton(onClick = { showAddAccountSheet = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = TikTokTextSecondary)
                    }
                }

                Divider(color = TikTokBorder)

                OutlinedTextField(
                    value = inputHandle,
                    onValueChange = { inputHandle = it },
                    label = { Text("Tên người dùng (@username)") },
                    placeholder = { Text("Ví dụ: hsisha.hssosu") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = inputDisplayName,
                    onValueChange = { inputDisplayName = it },
                    label = { Text("Tên hiển thị (Tùy chọn)") },
                    placeholder = { Text("Ví dụ: My TikTok Account") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val clean = inputHandle.trim().removePrefix("@")
                        if (clean.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập tên tài khoản TikTok", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        TikTokAccountsStore.addFromCapture(
                            context = context,
                            handle = clean,
                            displayName = inputDisplayName.ifBlank { clean },
                            avatarUrl = "",
                            variant = TikTokAppVariant.STANDARD
                        )
                        reloadData()
                        showAddAccountSheet = false
                        Toast.makeText(context, "Đã thêm @$clean vào danh sách!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TikTokDark)
                ) {
                    Text("Xác nhận thêm", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                }
            }
        }
    }

    // ==================== BOTTOMSHEET CHI TIẾT NICK TIKTOK ====================
    selectedDetailAccount?.let { acc ->
        val clean = acc.handle.trim().removePrefix("@")
        ModalBottomSheet(
            onDismissRequest = { selectedDetailAccount = null },
            containerColor = TikTokCardBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chi tiết tài khoản TikTok", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TikTokTextPrimary)
                    IconButton(onClick = { selectedDetailAccount = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = TikTokTextSecondary)
                    }
                }

                Divider(color = TikTokBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (acc.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = acc.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Filled.MusicNote, null, tint = TikTokTextSecondary)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(acc.displayName.ifBlank { "@$clean" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TikTokTextPrimary)
                        Text("@$clean", fontSize = 13.sp, color = TikTokTextSecondary)
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = TikTokBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trạng thái:", fontSize = 13.sp, color = TikTokTextSecondary)
                            Text(if (acc.isLive) "Hoạt động (Live)" else "Chưa xác minh", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (acc.isLive) SuccessGreen else Color(0xFFEF4444))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Nhiệm vụ đã làm:", fontSize = 13.sp, color = TikTokTextSecondary)
                            Text("${acc.taskCount} nhiệm vụ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TikTokTextPrimary)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Phiên bản TikTok:", fontSize = 13.sp, color = TikTokTextSecondary)
                            Text(acc.variant.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TikTokTextPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        TikTokAccountsStore.removeAccount(context, acc.uid)
                        reloadData()
                        selectedDetailAccount = null
                        Toast.makeText(context, "Đã xóa @$clean khỏi danh sách", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Xóa tài khoản này")
                }
            }
        }
    }
}
