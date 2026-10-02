package com.cayxu.app.ui.screens.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.cayxu.app.BuildConfig
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.util.AppUpdateData
import com.cayxu.app.util.AppUpdateManager
import com.cayxu.app.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val FigmaScreenBg = Color(0xFFF9FAFB)
private val FigmaCardBg = Color(0xFFFFFFFF)
private val FigmaBorder = Color(0xFFF1F5F9)
private val FigmaDivider = Color(0xFFF3F4F6)
private val FigmaTextPrimary = Color(0xFF111827)
private val FigmaTextSecondary = Color(0xFF6B7280)
private val FigmaTextMuted = Color(0xFF9CA3AF)
private val FigmaBlue = Color(0xFF0284C7)
private val FigmaBlueLink = Color(0xFF2563EB)
private val FigmaPurple = Color(0xFF4F46E5)
private val FigmaDanger = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(navController: NavController) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val accountId = remember { securePrefs.getOrCreateAccountId() }
    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val deviceId = remember { DeviceUtils.getAndroidId(context) }

    val displayName = if (!buyerUsername.isNullOrBlank()) buyerUsername else "Minh Anh"
    val userHandle = "@${(buyerUsername ?: "minhanh").lowercase().replace(" ", "")} · Thành viên từ 08/2026"

    val userInitials = remember(displayName) {
        val trimmed = displayName.trim()
        val parts = trimmed.split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            "${parts[0].first()}${parts[1].first()}".uppercase()
        } else {
            trimmed.take(2).uppercase().ifBlank { "MA" }
        }
    }

    var avatarUriString by remember { mutableStateOf(securePrefs.getAvatarUri()) }
    var avatarBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    // Update manager states
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateData?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(avatarUriString) {
        val uriString = avatarUriString
        avatarBitmap = if (uriString == null) {
            null
        } else {
            try {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            avatarUriString = uri.toString()
            securePrefs.saveAvatarUri(uri.toString())
        }
    }

    // Dialog đăng xuất
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Đăng xuất", fontWeight = FontWeight.Bold, color = FigmaTextPrimary) },
            text = { Text("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?", fontSize = 14.sp, color = FigmaTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        securePrefs.clearKey()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FigmaDanger)
                ) {
                    Text("Đăng xuất", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Hủy", color = FigmaTextSecondary)
                }
            }
        )
    }

    // Dialog hiển thị mã QR tài khoản
    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Text(
                    text = "Mã QR tài khoản",
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE0F2FE))
                            .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            tint = FigmaBlue,
                            modifier = Modifier.size(90.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "ID: $accountId",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = FigmaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mã máy: $deviceId",
                        fontSize = 12.sp,
                        color = FigmaTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(accountId))
                        Toast.makeText(context, "Đã sao chép ID tài khoản", Toast.LENGTH_SHORT).show()
                        showQrDialog = false
                    }
                ) {
                    Text("Sao chép ID", fontWeight = FontWeight.Bold, color = FigmaBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Đóng", color = FigmaTextSecondary)
                }
            }
        )
    }

    // Modal Sheet cập nhật phiên bản
    pendingUpdate?.let { update ->
        val startDownload: () -> Unit = {
            val downloadUrl = update.apkUrl
            if (downloadUrl.isBlank()) {
                Toast.makeText(context, "Chưa có đường dẫn tải về bản cập nhật", Toast.LENGTH_SHORT).show()
            } else {
                isDownloadingUpdate = true
                downloadProgress = 0f
                downloadStatusText = "Đang kết nối tới máy chủ..."
                downloadError = null
                downloadedApkFile = null
                downloadJob?.cancel()
                downloadJob = coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .build()
                        val request = Request.Builder()
                            .url(downloadUrl)
                            .header("User-Agent", "Mozilla/5.0 LunexApp")
                            .build()
                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val body = response.body ?: throw Exception("Nội dung tải về rỗng")
                        val totalBytes = body.contentLength()

                        val downloadDir = context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")
                        if (!downloadDir.exists()) downloadDir.mkdirs()
                        val destFile = File(downloadDir, "Lunex_${update.versionName}.apk")
                        if (destFile.exists()) destFile.delete()

                        body.byteStream().use { input ->
                            destFile.outputStream().use { output ->
                                val buffer = ByteArray(16 * 1024)
                                var bytesRead: Int
                                var downloadedBytes = 0L
                                var lastUiUpdate = 0L

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    val now = System.currentTimeMillis()
                                    if (now - lastUiUpdate > 100 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                                        lastUiUpdate = now
                                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                                        val percent = (progress * 100).toInt().coerceIn(0, 100)
                                        withContext(Dispatchers.Main) {
                                            downloadProgress = progress
                                            downloadStatusText = "Đang tải bản cập nhật: $percent%"
                                        }
                                    }
                                }
                                output.flush()
                            }
                        }

                        withContext(Dispatchers.Main) {
                            downloadProgress = 1f
                            downloadStatusText = "Tải thành công 100%! Đang mở cài đặt..."
                            downloadedApkFile = destFile
                            installApk(context, destFile)
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) return@launch
                        withContext(Dispatchers.Main) {
                            isDownloadingUpdate = false
                            downloadError = "Lỗi tải bản cập nhật: ${e.message ?: "Mất kết nối mạng"}"
                        }
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                if (!isDownloadingUpdate && (!update.forceUpdate && update.versionCode <= BuildConfig.VERSION_CODE)) {
                    pendingUpdate = null
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bản cập nhật mới (v${update.versionName})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isDownloadingUpdate) downloadStatusText else "Đã có phiên bản mới sẵn sàng để cài đặt.",
                    fontSize = 13.5.sp,
                    color = FigmaTextSecondary
                )
                if (isDownloadingUpdate) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = FigmaBlue,
                        trackColor = Color(0xFFE2E8F0)
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = startDownload,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue)
                    ) {
                        Text("Cập nhật ngay", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // MAIN CONTENT
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FigmaScreenBg)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 📌 1. HEADER TIÊU ĐỀ & NÚT CÀI ĐẶT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hồ sơ",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary
                )

                // Nút icon Cài đặt bánh răng trong khung vuông bo góc 12.dp
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FigmaCardBg)
                        .border(1.dp, FigmaBorder, RoundedCornerShape(12.dp))
                        .clickable { navController.navigate(Routes.SETTINGS) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Cài đặt",
                        tint = FigmaTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 📌 2. HÀNG THÔNG TIN NGƯỜI DÙNG (PROFILE INFO ROW)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bên trái: Avatar tròn bo viền mềm
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2FE))
                        .clickable { pickImageLauncher.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = avatarBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Ảnh đại diện",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = userInitials,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = FigmaBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Ở giữa: Tên người dùng & handle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FigmaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userHandle,
                        fontSize = 13.sp,
                        color = FigmaTextSecondary
                    )
                }

                // Bên phải: Icon quét mã QR màu xanh dương #0284C7
                IconButton(
                    onClick = { showQrDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "Mã QR",
                        tint = FigmaBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 📌 3. THẺ THỐNG KÊ ĐIỂM & CẤP ĐỘ UY TÍN (STAT CARD)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, FigmaBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    // Hàng trên: Tổng điểm & Cấp độ uy tín
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Cột trái: Tổng điểm
                        Column {
                            Text(
                                text = "Tổng điểm",
                                fontSize = 13.sp,
                                color = FigmaTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1.280",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = FigmaTextPrimary,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        // Cột phải: Cấp độ uy tín
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Cấp độ uy tín",
                                fontSize = 13.sp,
                                color = FigmaTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEEF2FF)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = FigmaPurple,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Uy tín 4",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FigmaPurple
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Hàng dưới: Thanh tiến trình cấp bậc
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thêm 120 điểm để lên cấp 5",
                            fontSize = 12.5.sp,
                            color = FigmaTextSecondary
                        )
                        Text(
                            text = "72%",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FigmaPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Thanh LinearProgressIndicator bo tròn 2 đầu, gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE5E7EB))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF6366F1), FigmaPurple)
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 📌 4. MỤC "LỊCH SỬ NHẬN THƯỞNG"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch sử nhận thưởng",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary
                )
                Text(
                    text = "Xem tất cả",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FigmaBlueLink,
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Hiển thị toàn bộ lịch sử", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
                border = BorderStroke(1.dp, FigmaBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Item 1: Theo dõi Nhà Có Studio
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = FigmaBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Theo dõi Nhà Có Studio",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FigmaTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Hôm nay, 09:24",
                                fontSize = 12.5.sp,
                                color = FigmaTextSecondary
                            )
                        }
                        Text(
                            text = "+40 điểm",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FigmaBlueLink
                        )
                    }

                    HorizontalDivider(color = FigmaDivider, thickness = 1.dp)

                    // Item 2: Thích video Góc làm việc
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEEF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Thích video Góc làm việc",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FigmaTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Hôm qua, 18:10",
                                fontSize = 12.5.sp,
                                color = FigmaTextSecondary
                            )
                        }
                        Text(
                            text = "+25 điểm",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FigmaBlueLink
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 📌 5. MỤC "TÀI KHOẢN & HỖ TRỢ"
            Text(
                text = "Tài khoản & hỗ trợ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FigmaTextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
                border = BorderStroke(1.dp, FigmaBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Dòng 1: Cài đặt tài khoản
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Routes.SETTINGS) }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = FigmaTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Cài đặt tài khoản",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = FigmaTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = FigmaTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    HorizontalDivider(color = FigmaDivider, thickness = 1.dp)

                    // Dòng 2: Thông báo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                notificationsEnabled = !notificationsEnabled
                                Toast.makeText(
                                    context,
                                    if (notificationsEnabled) "Đã bật thông báo" else "Đã tắt thông báo",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = FigmaTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Thông báo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = FigmaTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (notificationsEnabled) "Bật" else "Tắt",
                            fontSize = 14.sp,
                            color = FigmaTextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = FigmaTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    HorizontalDivider(color = FigmaDivider, thickness = 1.dp)

                    // Dòng 3: Trung tâm hỗ trợ
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Đang mở Trung tâm hỗ trợ", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = FigmaTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Trung tâm hỗ trợ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = FigmaTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = FigmaTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    HorizontalDivider(color = FigmaDivider, thickness = 1.dp)

                    // Dòng 4: Đăng xuất
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutDialog = true }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Logout,
                            contentDescription = null,
                            tint = FigmaDanger,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Đăng xuất",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = FigmaDanger,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = FigmaDanger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

// ── Cài APK bằng FileProvider ──────────────────────────────────────
private fun installApk(context: Context, apkFile: File) {
    if (!apkFile.exists()) {
        Toast.makeText(context, "Không tìm thấy tệp cài đặt APK", Toast.LENGTH_SHORT).show()
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Vui lòng cho phép quyền cài đặt ứng dụng cho Lunex", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
    }

    try {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Lỗi khởi chạy cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
