package com.cayxu.app.ui.screens.account

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*
import com.cayxu.app.util.AppUpdateData
import com.cayxu.app.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(navController: NavController) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val coroutineScope = rememberCoroutineScope()

    val accountId = remember { securePrefs.getOrCreateAccountId() }
    val deviceId = remember { DeviceUtils.getAndroidId(context) }

    var buyerUsername by remember { mutableStateOf(securePrefs.getBuyerUsername()) }
    var packageName by remember { mutableStateOf(securePrefs.getPackageName()?.uppercase() ?: "PRO") }
    var rawExpiresAt by remember { mutableStateOf(securePrefs.getExpiresAt()) }

    LaunchedEffect(Unit) {
        val key = securePrefs.getKey()
        if (!key.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val repository = com.cayxu.app.data.repository.AuthRepository()
                    val result = repository.verifyKey(key, deviceId)
                    when (result) {
                        is com.cayxu.app.data.repository.AuthResult.Success -> {
                            result.data.effectiveUsername?.let {
                                securePrefs.saveBuyerUsername(it)
                                buyerUsername = it
                            }
                            result.data.packageName?.let {
                                securePrefs.savePackageName(it)
                                packageName = it.uppercase()
                            }
                            result.data.expiresAt?.let {
                                securePrefs.saveExpiresAt(it)
                                rawExpiresAt = it
                            }
                        }
                        is com.cayxu.app.data.repository.AuthResult.ApiError -> {
                            securePrefs.clearKey()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        is com.cayxu.app.data.repository.AuthResult.NetworkError -> {}
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val username = buyerUsername?.trim() ?: ""
    val displayName = if (username.isNotBlank()) username else "Minh Anh"
    val userHandle = if (username.isNotBlank()) "@$username" else "@minhanh28"

    val expiryDate = remember(rawExpiresAt) {
        if (!rawExpiresAt.isNullOrBlank()) {
            rawExpiresAt!!.split(" ").firstOrNull() ?: rawExpiresAt!!
        } else {
            "28/10/2026"
        }
    }

    val avatarUrl = remember(username, deviceId) {
        val seed = if (username.isNotBlank()) username else deviceId
        "https://api.dicebear.com/9.x/bottts-neutral/png?seed=${Uri.encode(seed)}&size=160"
    }

    var avatarUriString by remember { mutableStateOf(securePrefs.getAvatarUri()) }
    var avatarBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Update manager states
    var pendingUpdate by remember { mutableStateOf<AppUpdateData?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
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

    // Dialog đăng xuất chuẩn Figma Frame 38
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Đăng xuất", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?", fontSize = 14.sp, color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        securePrefs.clearKey()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Đăng xuất", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Text("Hủy", color = TextSecondary)
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

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    if (totalBytes > 0) {
                                        downloadProgress = downloadedBytes.toFloat() / totalBytes.toFloat()
                                    }
                                }
                                output.flush()
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isDownloadingUpdate = false
                            installApk(context, destFile)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isDownloadingUpdate = false
                            Toast.makeText(context, "Lỗi tải về: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { if (!isDownloadingUpdate) pendingUpdate = null },
            containerColor = CardWhite,
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
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isDownloadingUpdate) downloadStatusText else "Đã có phiên bản mới sẵn sàng để cài đặt.",
                    fontSize = 13.5.sp,
                    color = TextSecondary
                )
                if (isDownloadingUpdate) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Primary,
                        trackColor = BorderLight
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = startDownload,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("Cập nhật ngay", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // MAIN CONTENT (FIGMA FRAME 29)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 📌 1. AVATAR LỚN & TÊN NGƯỜI DÙNG & HANDLE CHUẨN FIGMA FRAME 29
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE))
                    .clickable {
                        try {
                            pickImageLauncher.launch(arrayOf("image/*"))
                        } catch (_: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                val initials = displayName.split(" ")
                    .filter { it.isNotBlank() }
                    .takeLast(2)
                    .map { it.first().uppercase() }
                    .joinToString("")
                    .ifEmpty { "MA" }

                Text(
                    text = initials,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7C3AED)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = displayName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = userHandle,
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 📌 2. THẺ THÀNH VIÊN PRO (FIGMA FRAME 29)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFBFE)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, Color(0xFFEDE9FE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thành viên $packageName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "Pro",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF7C3AED)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Còn 18 ngày · Hết hạn $expiryDate",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE9E5F5))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF7C3AED))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 📌 3. HAI HOẠT ĐỘNG GẦN ĐÂY (FIGMA FRAME 29)
            FigmaProfileActivityCard(
                icon = Icons.Outlined.PersonAdd,
                iconBg = Color(0xFFEAF0FF),
                iconTint = Primary,
                title = "Theo dõi @linh.daily",
                subtitle = "Hôm nay, 10:24 · +40 điểm"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FigmaProfileActivityCard(
                icon = Icons.Outlined.CardGiftcard,
                iconBg = Color(0xFFEAF0FF),
                iconTint = Primary,
                title = "Đổi thẻ điện thoại",
                subtitle = "Hôm qua, 18:12 · -1.000 điểm"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 📌 4. MỤC "TÀI KHOẢN & HỖ TRỢ" (FIGMA FRAME 29)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Tài khoản & hỗ trợ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FigmaAccountNavCard(
                    icon = Icons.Outlined.Person,
                    iconBg = Color(0xFFEAF0FF),
                    iconTint = Primary,
                    title = "Chỉnh sửa hồ sơ",
                    onClick = {
                        Toast.makeText(context, "Chỉnh sửa hồ sơ", Toast.LENGTH_SHORT).show()
                    }
                )

                FigmaAccountNavCard(
                    icon = Icons.Outlined.Tune,
                    iconBg = Color(0xFFEAF0FF),
                    iconTint = Primary,
                    title = "Cài đặt tài khoản",
                    onClick = { navController.navigate(Routes.SETTINGS) }
                )

                FigmaAccountNavCard(
                    icon = Icons.Outlined.HelpOutline,
                    iconBg = Color(0xFFEAF0FF),
                    iconTint = Primary,
                    title = "Hỗ trợ & điều khoản",
                    onClick = {
                        Toast.makeText(context, "Hỗ trợ & điều khoản", Toast.LENGTH_SHORT).show()
                    }
                )

                FigmaAccountNavCard(
                    icon = Icons.Outlined.Logout,
                    iconBg = Color(0xFFFEE2E2),
                    iconTint = DangerRed,
                    title = "Đăng xuất",
                    titleColor = DangerRed,
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FigmaProfileActivityCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun FigmaAccountNavCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

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
