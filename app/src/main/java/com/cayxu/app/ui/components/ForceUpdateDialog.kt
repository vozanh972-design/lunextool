package com.cayxu.app.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cayxu.app.BuildConfig
import com.cayxu.app.ui.theme.DangerRed
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import com.cayxu.app.util.AppUpdateData
import com.cayxu.app.util.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val Navy900 = Color(0xFF0A1730)
private val Cobalt600 = Color(0xFF1D4ED8)

@Composable
fun ForceUpdateDialog(update: AppUpdateData) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    // Khi người dùng bấm phím Back trên thiết bị, thoát app luôn để vô hiệu hóa hoàn toàn
    BackHandler(enabled = true) {
        (context as? Activity)?.finishAffinity()
    }

    val authCheckCode = remember { com.cayxu.app.util.NativeSecurity.verifyUpdateMatrix(context) }
    val isKeyAuthorized = authCheckCode == 0

    val startDownload: () -> Unit = {
        val checkCode = com.cayxu.app.util.NativeSecurity.verifyUpdateMatrix(context)
        if (checkCode != 0) {
            val errorMsg = when (checkCode) {
                1 -> "Thiết bị chưa kích hoạt key bản quyền chính hãng từ máy chủ lunex.io.vn."
                2 -> "Phát hiện can thiệp hệ thống hoặc công cụ hook bộ nhớ. Từ chối cập nhật."
                3, 4 -> "Phát hiện tập tin ứng dụng đã bị can thiệp trái phép. Từ chối cập nhật."
                else -> "Mã key không hợp lệ hoặc không được cấp phép từ máy chủ lunex.io.vn."
            }
            downloadError = errorMsg
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            return@startDownload
        }

        val downloadUrl = update.apkUrl
        if (downloadUrl.isBlank()) {
            Toast.makeText(context, "Chưa có liên kết tải bản cập nhật mới", Toast.LENGTH_SHORT).show()
        } else {
            isDownloading = true
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
                    if (!response.isSuccessful) {
                        throw Exception("Mã phản hồi HTTP ${response.code}")
                    }
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
                                    val downloadedMb = downloadedBytes.toDouble() / (1024 * 1024)
                                    val totalMb = totalBytes.toDouble() / (1024 * 1024)
                                    val percent = (progress * 100).toInt().coerceIn(0, 100)
                                    val status = if (totalBytes > 0) {
                                        "Đang tải bản cập nhật: $percent% (${"%.1f".format(java.util.Locale.US, downloadedMb)} MB / ${"%.1f".format(java.util.Locale.US, totalMb)} MB)"
                                    } else {
                                        "Đang tải bản cập nhật: ${"%.1f".format(java.util.Locale.US, downloadedMb)} MB..."
                                    }
                                    withContext(Dispatchers.Main) {
                                        downloadProgress = progress
                                        downloadStatusText = status
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        downloadProgress = 1f
                        downloadStatusText = "Tải thành công! Đang mở trình cài đặt..."
                        downloadedApkFile = destFile
                        AppUpdateManager.installApk(context, destFile)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) return@launch
                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        downloadError = "Lỗi tải bản cập nhật: ${e.message ?: "Mất kết nối mạng"}"
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Không cho phép tắt dialog khi chưa cập nhật */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Thanh gạt (Drag handle) đặc trưng của Bottom Sheet
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 18.dp)
                            .size(width = 42.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFCBD5E1))
                    )

                    // Squircle bo góc màu xanh chứa icon mũi tên Download màu trắng
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2563EB), Cobalt600)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Dòng cảnh báo YÊU CẦU CẬP NHẬT có icon tam giác cảnh báo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "YÊU CẦU CẬP NHẬT",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DangerRed,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Bản phát hành mới (v${update.versionName})",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Phiên bản bạn đang sử dụng (v${BuildConfig.VERSION_NAME}) đã cũ và đã bị vô hiệu hóa để bảo đảm an toàn. Vui lòng cập nhật để tiếp tục sử dụng.",
                        fontSize = 13.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    val rawChangelog = update.changelog.ifBlank {
                        BuildConfig.UPDATE_CHANGELOG.takeIf { it.isNotBlank() } ?: "Cập nhật và tối ưu hóa hệ thống"
                    }
                    val changelogLines = rawChangelog
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    Spacer(Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Nội dung cập nhật:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                changelogLines.forEach { line ->
                                    val cleanLine = line.removePrefix("-").removePrefix("•").removePrefix("*").trim()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text("• ", color = Cobalt600, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = cleanLine,
                                            fontSize = 13.sp,
                                            color = TextSecondary,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isDownloading) {
                        Spacer(Modifier.height(20.dp))
                        if (downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Cobalt600,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Cobalt600,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = downloadStatusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        if (downloadProgress >= 1f && downloadedApkFile != null) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    AppUpdateManager.installApk(context, downloadedApkFile!!)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Cobalt600)
                            ) {
                                Icon(Icons.Filled.InstallMobile, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Mở trình cài đặt APK",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else if (downloadError != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = downloadError ?: "Đã xảy ra lỗi khi tải",
                            fontSize = 12.5.sp,
                            color = DangerRed,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = startDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cobalt600)
                        ) {
                            Text("Thử lại", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        if (!isKeyAuthorized) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "⚠ Bạn cần kích hoạt key bản quyền chính hãng từ máy chủ lunex.io.vn để được phép tải bản cập nhật này.",
                                fontSize = 12.5.sp,
                                color = DangerRed,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = startDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isKeyAuthorized) Cobalt600 else Color(0xFF64748B)
                            )
                        ) {
                            Text(
                                text = if (isKeyAuthorized) "Cập nhật ngay (v${update.versionName})" else "Yêu cầu kích hoạt bản quyền",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    TextButton(
                        onClick = { (context as? Activity)?.finishAffinity() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Thoát ứng dụng",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
