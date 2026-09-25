package com.cayxu.app.ui.screens.account

import android.app.DownloadManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cayxu.app.BuildConfig
import com.cayxu.app.R
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*
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

private val Navy900 = Color(0xFF0A1730)
private val Cobalt600 = Color(0xFF1D4ED8)
private val Cyan400 = Color(0xFF4FD1E8)
private val Cyan100 = Color(0xFFE3FBFD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(navController: NavController) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val clipboardManager = LocalClipboardManager.current

    var facebookCount by remember { mutableStateOf(FacebookAccountsStore.getAccounts(context).size) }
    var tikTokCount by remember { mutableStateOf(TikTokAccountsStore.getAccounts(context).size) }
    var instagramCount by remember { mutableStateOf(LinkedAccountsStore.getAccounts(context, "Instagram").size) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                facebookCount = FacebookAccountsStore.getAccounts(context).size
                tikTokCount = TikTokAccountsStore.getAccounts(context).size
                instagramCount = LinkedAccountsStore.getAccounts(context, "Instagram").size
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accountId = remember { securePrefs.getOrCreateAccountId() }
    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val displayName = if (!buyerUsername.isNullOrBlank()) buyerUsername else "ID: $accountId"
    val deviceId = remember { DeviceUtils.getAndroidId(context) }
    val packageName = remember { securePrefs.getPackageName() ?: "Premium" }
    val expiresAt = remember { securePrefs.getExpiresAt() ?: "20/12/2026" }
    val activatedKeys = remember { securePrefs.getActivatedKeysCount() }

    // Tính điểm thành viên: mỗi lần mua/kích hoạt 1 key = 10 điểm
    val points = remember(activatedKeys) { activatedKeys * 10 }

    // Hạng thành viên tính theo điểm
    val (tierName, nextTierName, neededPoints, progressFraction) = remember(points) {
        when {
            points < 50 -> Quad("Hạng Đồng", "Hạng Bạc", 50 - points, points / 50f)
            points < 200 -> Quad("Hạng Bạc", "Hạng Vàng", 200 - points, (points - 50) / 150f)
            points < 500 -> Quad("Hạng Vàng", "Bạch Kim", 500 - points, (points - 200) / 300f)
            points < 1000 -> Quad("Hạng Bạch Kim", "Kim Cương", 1000 - points, (points - 500) / 500f)
            else -> Quad("Hạng Kim Cương", "Tối Thượng", 0, 1.0f)
        }
    }

    var avatarUriString by remember { mutableStateOf(securePrefs.getAvatarUri()) }
    var avatarBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var showBenefitsDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateData?>(null) }

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
                // ignore
            }
            avatarUriString = uri.toString()
            securePrefs.saveAvatarUri(uri.toString())
        }
    }

    if (showBenefitsDialog) {
        AlertDialog(
            onDismissRequest = { showBenefitsDialog = false },
            title = { Text("Quyền lợi $tierName", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Tích luỹ 10 điểm cho mỗi key kích hoạt.", fontSize = 13.5.sp, color = TextSecondary)
                    Text("• Ưu tiên xử lý nhiệm vụ siêu tốc độ.", fontSize = 13.5.sp, color = TextSecondary)
                    Text("• Hỗ trợ kỹ thuật 24/7 trực tiếp.", fontSize = 13.5.sp, color = TextSecondary)
                    Text("• Mở khoá tính năng chạy đa luồng không giới hạn.", fontSize = 13.5.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showBenefitsDialog = false }) {
                    Text("Đóng", fontWeight = FontWeight.Bold, color = Cobalt600)
                }
            }
        )
    }

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
                    Text("Đăng xuất", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }

    pendingUpdate?.let { update ->
        ModalBottomSheet(
            onDismissRequest = { pendingUpdate = null },
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
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Cobalt600.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = Cobalt600,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Bản cập nhật mới (v${update.versionName})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Đã có phiên bản mới sẵn sàng để cài đặt.",
                    fontSize = 13.5.sp,
                    color = TextSecondary
                )

                if (update.changelog.isNotBlank()) {
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
                            Text(
                                text = update.changelog,
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val downloadUrl = update.apkUrl
                        pendingUpdate = null
                        if (downloadUrl.isNotBlank()) {
                            try {
                                val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? DownloadManager
                                if (dm != null && (downloadUrl.endsWith(".apk") || downloadUrl.contains(".apk?"))) {
                                    val req = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                        setTitle("Lunex Update v${update.versionName}")
                                        setDescription("Đang tải bản cập nhật...")
                                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Lunex_${update.versionName}.apk")
                                        setMimeType("application/vnd.android.package-archive")
                                    }
                                    dm.enqueue(req)
                                    Toast.makeText(context, "Đang tải bản cập nhật trong thanh thông báo...", Toast.LENGTH_LONG).show()
                                }
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi mở liên kết cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Chưa có đường dẫn tải về bản cập nhật này", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cobalt600)
                ) {
                    Text(
                        text = "Cập nhật ngay",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = { pendingUpdate = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Để sau",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ---- Header ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tài khoản", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .clickable { navController.navigate(Routes.SETTINGS) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Cài đặt", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Profile Card (Tên người dùng mua key + Avatar + Mã máy) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Cyan100, Color(0xFFEDF2FF), Color(0xFFF5F0FF))))
                .border(1.dp, Color(0x140F1E37), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Navy900, Cobalt600, Cyan400))),
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
                            Image(
                                painter = painterResource(R.drawable.ic_default_avatar),
                                contentDescription = "Ảnh đại diện mặc định",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
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
                            .background(Cobalt600)
                            .clickable { pickImageLauncher.launch(arrayOf("image/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Đổi ảnh đại diện",
                            tint = CardWhite,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            displayName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.White.copy(alpha = 0.6f))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(displayName))
                                    Toast.makeText(context, "Đã sao chép tên người dùng", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Sao chép",
                                tint = Cobalt600,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Mã máy: $deviceId", color = TextSecondary, fontSize = 12.5.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Card Hạng thành viên (Tier Card) ----
        Text("Hạng thành viên", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Navy900, Cobalt600, Cyan400)))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFDE047), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tierName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            val subText = if (neededPoints > 0) "$points điểm • còn $neededPoints điểm để lên $nextTierName"
                            else "$points điểm • Đã đạt hạng cao nhất"
                            Text(subText, color = Color(0xFFEAF1FC).copy(alpha = 0.85f), fontSize = 12.5.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0.05f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(Brush.horizontalGradient(listOf(Cyan400, Color.White)))
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { showBenefitsDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Xem quyền lợi hạng thành viên", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Gói dịch vụ ----
        Text("Gói dịch vụ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HtmlListRow(
                    title = "Gói đang sử dụng",
                    subtitle = "$packageName • Còn hiệu lực đến $expiresAt",
                    subtitleColor = Cobalt600,
                    onClick = { Toast.makeText(context, "Gói $packageName còn hiệu lực đến $expiresAt", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                HtmlListRow(
                    title = "Lịch sử kích hoạt",
                    subtitle = "Đã kích hoạt $activatedKeys mã",
                    onClick = { Toast.makeText(context, "Tổng cộng đã kích hoạt $activatedKeys key", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                HtmlListRow(
                    title = "Thiết bị đã kích hoạt",
                    subtitle = "1 thiết bị đang hoạt động",
                    onClick = { Toast.makeText(context, "Thiết bị hiện tại: $deviceId", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Quản lý tài khoản ----
        Text("Quản lý tài khoản", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                socialAccounts.forEachIndexed { index, item ->
                    val count = when (item.label) {
                        "Facebook" -> facebookCount
                        "TikTok" -> tikTokCount
                        "Instagram" -> instagramCount
                        else -> 0
                    }
                    SocialAccountRow(
                        item = item,
                        count = count,
                        onClick = {
                            when (item.label) {
                                "Facebook" -> navController.navigate(Routes.LINK_ACCOUNT_FACEBOOK)
                                "TikTok" -> navController.navigate(Routes.LINK_ACCOUNT_TIKTOK)
                                else -> navController.navigate(Routes.linkAccount(item.label, item.iconRes))
                            }
                        }
                    )
                    if (index != socialAccounts.lastIndex) {
                        HorizontalDivider(color = AppBackground, thickness = 1.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Hỗ trợ & khác ----
        Text("Hỗ trợ & khác", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HtmlListRow(
                    title = "Trung tâm hỗ trợ",
                    onClick = { Toast.makeText(context, "Đang mở trung tâm hỗ trợ", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                HtmlListRow(
                    title = "Điều khoản sử dụng",
                    onClick = { Toast.makeText(context, "Đang mở điều khoản sử dụng", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                HtmlListRow(
                    title = "Chính sách bảo mật",
                    onClick = { Toast.makeText(context, "Đang mở chính sách bảo mật", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                HtmlListRow(
                    title = "Phiên bản ứng dụng",
                    trailingText = BuildConfig.VERSION_NAME,
                    onClick = {
                        if (isCheckingUpdate) return@HtmlListRow
                        isCheckingUpdate = true
                        Toast.makeText(context, "Đang kiểm tra bản cập nhật...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(10, TimeUnit.SECONDS)
                                        .readTimeout(10, TimeUnit.SECONDS)
                                        .build()
                                    val request = Request.Builder()
                                        .url(getUpdateApiEndpoint())
                                        .header("Cache-Control", "no-cache")
                                        .build()
                                    val response = client.newCall(request).execute()
                                    if (!response.isSuccessful) {
                                        throw Exception("Mã HTTP ${response.code}")
                                    }
                                    val body = response.body?.string().orEmpty()
                                    val json = JSONObject(body)
                                    val vCode = if (json.has("version_code")) json.getInt("version_code") else json.optInt("versionCode", 0)
                                    val vName = if (json.has("version_name")) json.getString("version_name") else json.optString("versionName", "")
                                    val changelog = if (json.has("changelog")) json.getString("changelog") else json.optString("change_log", json.optString("description", ""))
                                    val apkUrl = if (json.has("apk_url")) json.getString("apk_url") else json.optString("download_url", json.optString("url", ""))
                                    AppUpdateData(vCode, vName, changelog, apkUrl)
                                }
                                if (result.versionCode > BuildConfig.VERSION_CODE) {
                                    pendingUpdate = result
                                } else {
                                    Toast.makeText(context, "Bạn đang sử dụng phiên bản mới nhất (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                val msg = if (e.message?.contains("404") == true) "Chưa có bản cập nhật mới trên máy chủ" else (e.message ?: "Lỗi kết nối")
                                Toast.makeText(context, "Kiểm tra cập nhật: $msg", Toast.LENGTH_SHORT).show()
                            } finally {
                                isCheckingUpdate = false
                            }
                        }
                    }
                )
                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                HtmlListRow(
                    title = "Đăng xuất",
                    titleColor = DangerRed,
                    hideChevron = true,
                    onClick = { showLogoutDialog = true }
                )
            }
        }

        Spacer(Modifier.height(90.dp))
    }
}

private data class AppUpdateData(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val apkUrl: String
)

private val OBF_UPDATE_URL = byteArrayOf(
    0x33.toByte(), 0x2F.toByte(), 0x2F.toByte(), 0x2B.toByte(), 0x28.toByte(), 0x61.toByte(), 0x74.toByte(), 0x74.toByte(),
    0x29.toByte(), 0x3A.toByte(), 0x2C.toByte(), 0x75.toByte(), 0x3C.toByte(), 0x32.toByte(), 0x2F.toByte(), 0x33.toByte(),
    0x2E.toByte(), 0x39.toByte(), 0x2E.toByte(), 0x28.toByte(), 0x3E.toByte(), 0x29.toByte(), 0x38.toByte(), 0x34.toByte(),
    0x35.toByte(), 0x2F.toByte(), 0x3E.toByte(), 0x35.toByte(), 0x2F.toByte(), 0x75.toByte(), 0x38.toByte(), 0x34.toByte(),
    0x36.toByte(), 0x74.toByte(), 0x2F.toByte(), 0x33.toByte(), 0x3E.toByte(), 0x3A.toByte(), 0x35.toByte(), 0x33.toByte(),
    0x68.toByte(), 0x62.toByte(), 0x74.toByte(), 0x37.toByte(), 0x2E.toByte(), 0x35.toByte(), 0x3E.toByte(), 0x23.toByte(),
    0x3A.toByte(), 0x2B.toByte(), 0x30.toByte(), 0x74.toByte(), 0x36.toByte(), 0x3A.toByte(), 0x32.toByte(), 0x35.toByte(),
    0x74.toByte(), 0x2D.toByte(), 0x3E.toByte(), 0x29.toByte(), 0x28.toByte(), 0x32.toByte(), 0x34.toByte(), 0x35.toByte(),
    0x75.toByte(), 0x31.toByte(), 0x28.toByte(), 0x34.toByte(), 0x35.toByte()
)

private fun getUpdateApiEndpoint(): String {
    val key = 0x5B.toByte()
    val decoded = ByteArray(OBF_UPDATE_URL.size) { i -> (OBF_UPDATE_URL[i].toInt() xor key.toInt()).toByte() }
    return String(decoded, Charsets.UTF_8)
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun HtmlListRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = TextPrimary,
    subtitleColor: Color = TextSecondary,
    trailingText: String? = null,
    hideChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = subtitleColor, fontSize = 12.5.sp, fontWeight = if (subtitleColor == Cobalt600) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        if (trailingText != null) {
            Text(trailingText, color = TextSecondary, fontSize = 13.sp)
        } else if (!hideChevron) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF8E9BB0), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SocialAccountRow(item: SocialAccountItem, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(item.iconRes),
            contentDescription = item.label,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Thêm tài khoản ${item.label}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val statusText = if (count > 0) "$count tài khoản" else "Chưa liên kết"
            Spacer(Modifier.height(2.dp))
            Text(statusText, color = if (count > 0) Cobalt600 else Color(0xFF8E9BB0), fontSize = 12.5.sp, fontWeight = if (count > 0) FontWeight.SemiBold else FontWeight.Normal)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF8E9BB0), modifier = Modifier.size(18.dp))
    }
}
