package com.cayxu.app.ui.screens.account

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.BuildConfig
import com.cayxu.app.R
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.InfoBlueBg
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import com.cayxu.app.util.DeviceUtils

private data class SocialAccountItem(
    val label: String,
    val iconRes: Int
)

private val socialAccounts = listOf(
    SocialAccountItem("Facebook", R.drawable.ic_social_facebook),
    SocialAccountItem("TikTok", R.drawable.ic_social_tiktok),
    SocialAccountItem("Instagram", R.drawable.ic_social_instagram),
    SocialAccountItem("LinkedIn", R.drawable.ic_social_linkedin),
    SocialAccountItem("Snapchat", R.drawable.ic_social_snapchat),
    SocialAccountItem("Threads", R.drawable.ic_social_threads)
)

@Composable
fun AccountScreen(navController: NavController) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val clipboardManager = LocalClipboardManager.current

    // ID hiển thị: sinh ngẫu nhiên 1 lần duy nhất trên máy này rồi lưu lại,
    // không dùng username thật.
    val accountId = remember { securePrefs.getOrCreateAccountId() }

    // Mã máy: tự nhận diện bằng ANDROID_ID, không cần người dùng nhập.
    val deviceId = remember { DeviceUtils.getAndroidId(context) }

    var avatarUriString by remember { mutableStateOf(securePrefs.getAvatarUri()) }
    var avatarBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

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
            } catch (e: SecurityException) {
                // Một số nguồn ảnh (ví dụ vài trình quản lý file) không hỗ trợ quyền
                // truy cập lâu dài - ảnh vẫn hiển thị được trong phiên làm việc hiện tại.
            }
            avatarUriString = uri.toString()
            securePrefs.saveAvatarUri(uri.toString())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tài khoản", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navController.navigate(com.cayxu.app.ui.navigation.Routes.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Cài đặt", tint = TextPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))

            // Thẻ thông tin tài khoản: avatar (mặc định hoặc do người dùng tự chọn) + ID + mã máy
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(InfoBlueBg, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CardWhite)
                        ) {
                            val bitmap = avatarBitmap
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Ảnh đại diện",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.ic_default_avatar),
                                    contentDescription = "Ảnh đại diện mặc định",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(CardWhite)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Primary)
                                .clickable { pickImageLauncher.launch(arrayOf("image/*")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Đổi ảnh đại diện",
                                tint = CardWhite,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "ID: $accountId",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(accountId))
                                    Toast.makeText(context, "Đã sao chép ID", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Sao chép ID",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("Mã máy: $deviceId", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Quản lý tài khoản", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    socialAccounts.forEachIndexed { index, item ->
                        SocialAccountRow(item)
                        if (index != socialAccounts.lastIndex) {
                            HorizontalDivider(color = AppBackground, thickness = 1.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Hỗ trợ & khác", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SimpleInfoRow("Trung tâm hỗ trợ")
                    HorizontalDivider(color = AppBackground, thickness = 1.dp)
                    SimpleInfoRow("Điều khoản sử dụng")
                    HorizontalDivider(color = AppBackground, thickness = 1.dp)
                    SimpleInfoRow("Chính sách bảo mật")
                    HorizontalDivider(color = AppBackground, thickness = 1.dp)
                    SimpleInfoRow("Phiên bản ứng dụng", trailing = BuildConfig.VERSION_NAME)
                }
            }

            Spacer(Modifier.height(90.dp))
        }
}

@Composable
private fun SocialAccountRow(item: SocialAccountItem) {
    Row(
        Modifier
            .fillMaxWidth()
            // TODO: chưa có luồng liên kết mạng xã hội thật (OAuth) - sẽ nối API sau.
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(item.iconRes),
            contentDescription = item.label,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Thêm tài khoản ${item.label}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Chưa liên kết", color = TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SimpleInfoRow(label: String, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = trailing == null) { }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, color = TextSecondary, fontSize = 13.sp)
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
