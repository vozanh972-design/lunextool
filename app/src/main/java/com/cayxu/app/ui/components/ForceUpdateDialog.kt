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
import com.cayxu.app.ui.theme.Cobalt600
import com.cayxu.app.ui.theme.DangerRed
import com.cayxu.app.ui.theme.Navy900
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

    val startDownload: () -> Unit = {
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
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Navy900, Cobalt600))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDownloading && downloadProgress < 1f) Icons.Filled.Download else Icons.Filled.SecurityUpdateGood,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "YÊU CẦU CẬP NHẬT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed,
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Bản phát hành mới (v${update.versionName})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Phiên bản bạn đang sử dụng (v${BuildConfig.VERSION_NAME}) đã cũ và đã bị vô hiệu hóa để bảo đảm an toàn. Vui lòng cập nhật để tiếp tục sử dụng.",
                        fontSize = 13.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
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
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Nội dung cập nhật:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                changelogLines.forEach { line ->
                                    val cleanLine = line.removePrefix("-").removePrefix("•").removePrefix("*").trim()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text("• ", color = Cobalt600, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = cleanLine,
                                            fontSize = 12.5.sp,
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
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
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
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cobalt600)
                        ) {
                            Text("Thử lại", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = startDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cobalt600)
                        ) {
                            Text(
                                text = "Cập nhật ngay (v${update.versionName})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(
                        onClick = { (context as? Activity)?.finishAffinity() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Thoát ứng dụng",
                            color = TextSecondary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
