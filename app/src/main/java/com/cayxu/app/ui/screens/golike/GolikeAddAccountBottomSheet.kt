package com.cayxu.app.ui.screens.golike

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.text.selection.SelectionContainer
import com.cayxu.app.automation.instagram.IgTaToolClient
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.automation.tiktok.TikTokCaptureBridge
import com.cayxu.app.automation.tiktok.TikTokCaptureState
import com.cayxu.app.data.local.InstagramAccount
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.instagram.InstagramApiClient
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.PrimaryButton
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GolikeBrandOrange = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolikeAddAccountBottomSheet(
    platform: String,
    onDismiss: () -> Unit,
    onAccountAdded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var usernameOrUid by remember { mutableStateOf("") }
    var proxyInput by remember { mutableStateOf("") }
    var igCookieInput by remember { mutableStateOf("") }
    var isAddingIg by remember { mutableStateOf(false) }
    var isScanningTikTok by remember { mutableStateOf(false) }
    var selectedVariant by remember {
        mutableStateOf(
            when {
                TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STANDARD) -> TikTokAppVariant.STANDARD
                TikTokAppLauncher.isInstalled(context, TikTokAppVariant.LITE) -> TikTokAppVariant.LITE
                TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STUDIO) -> TikTokAppVariant.STUDIO
                else -> TikTokAppVariant.STANDARD
            }
        )
    }

    var overlayGranted by remember {
        mutableStateOf(TikTokAppLauncher.isOverlayPermissionGranted(context))
    }
    var accessibilityGranted by remember {
        mutableStateOf(TikTokAppLauncher.isAccessibilityServiceEnabled(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = TikTokAppLauncher.isOverlayPermissionGranted(context)
                accessibilityGranted = TikTokAppLauncher.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var shownTikTokSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shownTikTokSheet = true
    }

    val dismissTikTokWithAnim: () -> Unit = {
        scope.launch {
            shownTikTokSheet = false
            delay(220)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        TikTokCaptureBridge.state.collect { state ->
            when (state) {
                is TikTokCaptureState.Captured -> {
                    isScanningTikTok = false
                    val clean = state.handle.trim().removePrefix("@")
                    usernameOrUid = clean
                    if (platform.lowercase() == "tiktok" && clean.isNotBlank()) {
                        val gAcc = GolikeAccount(
                            id = clean,
                            platform = "tiktok",
                            username = clean,
                            avatar = state.avatarUrl,
                            isLive = true,
                            isGolikeLinked = false,
                            lastStatus = "Cần liên kết Golike"
                        )
                        GolikeAccountsStore.addOrUpdateAccount(context, gAcc)
                    }
                    TikTokCaptureBridge.reset()
                    Toast.makeText(context, "Đã quét thành công nick: @$clean", Toast.LENGTH_SHORT).show()
                    onAccountAdded()
                    dismissTikTokWithAnim()
                }
                is TikTokCaptureState.CapturedBatch -> {
                    isScanningTikTok = false
                    val active = state.accounts.firstOrNull { it.isActive } ?: state.accounts.firstOrNull()
                    val clean = active?.handle?.ifBlank { active.displayName }?.trim()?.removePrefix("@").orEmpty()
                    if (clean.isNotBlank()) {
                        usernameOrUid = clean
                        state.accounts.forEach { entry ->
                            val h = entry.handle.ifBlank { entry.displayName }.trim().removePrefix("@")
                            if (h.isNotBlank() && platform.lowercase() == "tiktok") {
                                val gAcc = GolikeAccount(
                                    id = h,
                                    platform = "tiktok",
                                    username = h,
                                    avatar = "",
                                    isLive = true,
                                    isGolikeLinked = false,
                                    lastStatus = "Cần liên kết Golike"
                                )
                                GolikeAccountsStore.addOrUpdateAccount(context, gAcc)
                            }
                        }
                    }
                    TikTokCaptureBridge.reset()
                    Toast.makeText(context, "Đã quét thành công nick: @$usernameOrUid", Toast.LENGTH_SHORT).show()
                    onAccountAdded()
                    dismissTikTokWithAnim()
                }
                is TikTokCaptureState.Failed -> {
                    isScanningTikTok = false
                    Toast.makeText(context, state.reason, Toast.LENGTH_LONG).show()
                    TikTokCaptureBridge.reset()
                }
                else -> Unit
            }
        }
    }

    fun startScanTikTok() {
        val variant = selectedVariant
        if (!TikTokAppLauncher.isInstalled(context, variant)) {
            val variantTitle = when (variant) {
                TikTokAppVariant.STANDARD -> "TikTok"
                TikTokAppVariant.LITE -> "TikTok Lite"
                TikTokAppVariant.STUDIO -> "TikTok Studio"
            }
            Toast.makeText(context, "Chưa cài đặt $variantTitle trên thiết bị này", Toast.LENGTH_SHORT).show()
            return
        }

        if (!overlayGranted) {
            Toast.makeText(context, "Vui lòng cấp quyền Hiển thị trên ứng dụng khác trước khi kiểm tra", Toast.LENGTH_LONG).show()
            TikTokAppLauncher.openOverlayPermissionSettings(context)
            return
        }

        if (!accessibilityGranted) {
            Toast.makeText(context, "Vui lòng bật quyền Trợ năng (Accessibility) trước khi kiểm tra", Toast.LENGTH_LONG).show()
            TikTokAppLauncher.openAccessibilitySettings(context)
            return
        }

        // Đánh dấu cờ quét tài khoản cho Golike
        GolikeAccountsStore.setScanningTikTok(context, true)
        // Mở màn nổi kiểm tra tài khoản riêng biệt của Golike
        startGolikeVerifyTikTokAccount(context, variant)
        dismissTikTokWithAnim()
    }

    // ─────────────────────────────────────────────────────────────
    // NHÁNH TIKTOK: Giao diện bottom sheet animated cao cấp chuẩn Alx
    // ─────────────────────────────────────────────────────────────
    if (platform.lowercase() == "tiktok") {
        Dialog(
            onDismissRequest = dismissTikTokWithAnim,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

            val scrimAlpha by animateFloatAsState(
                targetValue = if (shownTikTokSheet) 1f else 0f,
                animationSpec = tween(220),
                label = "scrimAlpha"
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // Nền mờ — chạm để đóng, có fade mượt mà
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = scrimAlpha }
                        .background(Color(0x730F172A))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismissTikTokWithAnim
                        )
                )

                AnimatedVisibility(
                    visible = shownTikTokSheet,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(300, easing = EaseOutSoft)) { it } + fadeIn(tween(250)),
                    exit = slideOutVertically(tween(220, easing = EaseOutSoft)) { it } + fadeOut(tween(200)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(Alx.White)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // Chặn click xuyên qua scrim
                            )
                            .navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp)
                    ) {
                        // Thanh gạt (Drag Handle)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Alx.InputBorder)
                        )

                        Spacer(Modifier.height(18.dp))

                        BasicText("Kiểm tra tài khoản TikTok", style = type(size = 20f, weight = FontWeight.ExtraBold, color = Alx.Ink))

                        Spacer(Modifier.height(6.dp))

                        BasicText(
                            "Màn hình nổi và Trợ năng cần được cấp quyền để tự động mở TikTok và kiểm tra trạng thái nick.",
                            style = type(size = 13.5f, weight = FontWeight.Medium, color = Alx.Muted, lineHeight = 1.45f),
                        )

                        Spacer(Modifier.height(16.dp))

                        // Thẻ 1: Quyền Hiển thị trên ứng dụng khác
                        GolikePermissionRow(
                            iconPaths = IconOverlayWindow,
                            title = "Hiển thị trên ứng dụng khác",
                            subtitle = "Để hiện màn nổi kiểm tra và điều khiển trên TikTok",
                            granted = overlayGranted,
                            grantedLabel = "Đã cấp",
                            requestLabel = "Cấp quyền",
                            onGrant = { TikTokAppLauncher.openOverlayPermissionSettings(context) },
                        )

                        Spacer(Modifier.height(10.dp))

                        // Thẻ 2: Dịch vụ Trợ năng
                        GolikePermissionRow(
                            iconPaths = IconAccessibility,
                            title = "Dịch vụ Trợ năng",
                            subtitle = "Để tự động bấm tab \"Tôi\" và kiểm tra @username TikTok",
                            granted = accessibilityGranted,
                            grantedLabel = "Đã cấp",
                            requestLabel = "Cấp quyền",
                            onGrant = { TikTokAppLauncher.openAccessibilitySettings(context) },
                        )

                        Spacer(Modifier.height(20.dp))

                        BasicText(
                            "CHỌN ỨNG DỤNG CẦN KIỂM TRA",
                            style = type(size = 11f, weight = FontWeight.ExtraBold, color = Alx.Muted, spacing = 0.12f),
                        )

                        Spacer(Modifier.height(10.dp))

                        val variants = listOf(
                            Pair(TikTokAppVariant.STANDARD, "TikTok"),
                            Pair(TikTokAppVariant.LITE, "TikTok Lite"),
                            Pair(TikTokAppVariant.STUDIO, "TikTok Studio")
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(64.dp),
                        ) {
                            items(variants) { (variant, title) ->
                                val installed = TikTokAppLauncher.isInstalled(context, variant)
                                val selected = selectedVariant == variant
                                GolikeAppVariantCard(
                                    title = title,
                                    installed = installed,
                                    selected = selected,
                                    onClick = { selectedVariant = variant },
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        val checkEnabled = accessibilityGranted

                        PrimaryButton(
                            label = "Kiểm tra tài khoản",
                            onClick = { startScanTikTok() },
                            enabled = checkEnabled,
                        )

                        if (!checkEnabled) {
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                "Cấp quyền Trợ năng để tiếp tục",
                                style = type(size = 12f, weight = FontWeight.Medium, color = Alx.Muted),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // ─────────────────────────────────────────────────────────────
    // NHÁNH FACEBOOK / INSTAGRAM: Giữ nguyên ModalBottomSheet
    // ─────────────────────────────────────────────────────────────
    val platformTitle = when (platform.lowercase()) {
        "facebook" -> "Facebook"
        "instagram" -> "Instagram"
        else -> "TikTok"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GolikeBrandOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            tint = GolikeBrandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Thêm tài khoản $platformTitle",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Quản lý & làm nhiệm vụ cho Golike",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(18.dp))

            // Tên tài khoản / UID
            if (platform.lowercase() == "instagram") {
                Text(
                    text = "Danh sách Cookie / Cookie|Proxy (mỗi tài khoản một dòng):",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    OutlinedTextField(
                        value = igCookieInput,
                        onValueChange = { igCookieInput = it },
                        enabled = !isAddingIg,
                        placeholder = {
                            Text(
                                "Định dạng: Cookie hoặc Cookie|Proxy\nVí dụ:\nsessionid=...; ds_user_id=123...; csrftoken=...|103.152.118.23:8080:user:pass\nsessionid=...; ds_user_id=456...; csrftoken=...|103.152.118.24:8080\nsessionid=...; ds_user_id=789...; csrftoken=...",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        },
                        minLines = 6,
                        maxLines = 10,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Tên tài khoản / UID (Facebook, etc.)
                Text(
                    text = "Tên đăng nhập / UID / @handle $platformTitle",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = usernameOrUid,
                    onValueChange = { usernameOrUid = it },
                    placeholder = {
                        Text(
                            when (platform.lowercase()) {
                                "facebook" -> "Nhập UID Facebook hoặc link trang"
                                else -> "Nhập UID hoặc tên đăng nhập"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // Proxy (tùy chọn)
                Text(
                    text = "Proxy gắn riêng cho nick (Tùy chọn: host:port hoặc host:port:user:pass)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = proxyInput,
                    onValueChange = { proxyInput = it },
                    placeholder = { Text("127.0.0.1:8080:user:pass") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            // Nút Thêm tài khoản Facebook / Instagram
            Button(
                onClick = {
                    if (platform.lowercase() == "instagram") {
                        val lines = igCookieInput.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        if (lines.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập ít nhất 1 dòng Cookie Instagram", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isAddingIg = true
                        scope.launch {
                            var addedCount = 0
                            withContext(Dispatchers.IO) {
                                val golikeClient = GolikeAccountsStore.getApiClient(context)
                                val remoteIgRes = try { golikeClient.getInstagramAccounts() } catch (_: Exception) { null }
                                val remoteIgList = mutableMapOf<String, String>()
                                val dataArr = remoteIgRes?.optJSONArray("data")
                                if (dataArr != null) {
                                    for (i in 0 until dataArr.length()) {
                                        val obj = dataArr.optJSONObject(i) ?: continue
                                        val gId = obj.optString("id")
                                        val un = obj.optString("nickname").ifBlank { obj.optString("username").ifBlank { obj.optString("unique_username") } }
                                        if (un.isNotBlank()) remoteIgList[un.lowercase()] = gId
                                        val fbId = obj.optString("fb_id")
                                        if (fbId.isNotBlank()) remoteIgList[fbId] = gId
                                    }
                                }

                                for (line in lines) {
                                    var rawCookie = ""
                                    var proxyStr: String? = null
                                    if (line.contains("|")) {
                                        val lastPipeIndex = line.lastIndexOf("|")
                                        rawCookie = line.substring(0, lastPipeIndex).trim()
                                        proxyStr = line.substring(lastPipeIndex + 1).trim().takeIf { it.isNotBlank() }
                                    } else {
                                        rawCookie = line
                                    }

                                    if (!rawCookie.contains("sessionid") || !rawCookie.contains("ds_user_id") || !rawCookie.contains("csrftoken")) {
                                        continue
                                    }

                                    val igClient = IgTaToolClient.buildClient(proxyStr)
                                    val info = IgTaToolClient.checkCookieIg(igClient, rawCookie)

                                    if (info.isLive && info.username.isNotBlank()) {
                                        val tokens = IgTaToolClient.extractPageTokens(
                                            client = igClient,
                                            cookie = rawCookie,
                                            targetUrl = "https://www.instagram.com/"
                                        )
                                        val dev = InstagramApiClient.getDeviceProfileFor(info.userId)
                                        val pic = try {
                                            InstagramApiClient.fetchProfilePic(info.username, rawCookie, proxyStr)
                                        } catch (_: Exception) { null }

                                        val igAcc = InstagramAccount(
                                            username = info.username,
                                            userId = info.userId,
                                            cookie = rawCookie,
                                            userAgent = dev.userAgent,
                                            proxy = proxyStr.orEmpty(),
                                            fullName = info.username,
                                            avatar = pic.orEmpty(),
                                            fbDtsg = tokens.dtsg,
                                            lsd = tokens.lsd,
                                            isLive = true
                                        )
                                        InstagramAccountsStore.addAccount(context, igAcc)

                                        val matchedGolikeId = remoteIgList[info.username.lowercase()] ?: remoteIgList[info.userId] ?: ""
                                        val gAcc = GolikeAccount(
                                            id = info.username,
                                            platform = "instagram",
                                            username = info.username,
                                            avatar = pic.orEmpty(),
                                            isLive = true,
                                            isGolikeLinked = matchedGolikeId.isNotBlank(),
                                            golikeAccountId = matchedGolikeId,
                                            proxy = proxyStr.orEmpty(),
                                            lastStatus = if (matchedGolikeId.isNotBlank()) "Đã liên kết Golike • Sẵn sàng" else "Đã thêm nick • Sẵn sàng làm job"
                                        )
                                        GolikeAccountsStore.addOrUpdateAccount(context, gAcc)
                                        addedCount++
                                    }
                                }
                            }
                            isAddingIg = false
                            if (addedCount > 0) {
                                Toast.makeText(context, "Đã thêm thành công $addedCount tài khoản Instagram!", Toast.LENGTH_SHORT).show()
                                onAccountAdded()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Không có cookie Instagram hợp lệ hoặc nick bị checkpoint!", Toast.LENGTH_LONG).show()
                            }
                        }
                        return@Button
                    }

                    val rawInput = usernameOrUid.trim()
                    if (rawInput.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập tên tài khoản hoặc UID", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val cleanId = rawInput.removePrefix("@").trim()
                    val newAccount = GolikeAccount(
                        id = cleanId,
                        platform = platform.lowercase(),
                        username = rawInput,
                        isLive = true,
                        isGolikeLinked = true,
                        proxy = proxyInput.trim(),
                        lastStatus = "Đã thêm vào Golike • Sẵn sàng"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, newAccount)
                    Toast.makeText(context, "Đã thêm tài khoản $cleanId vào Golike!", Toast.LENGTH_SHORT).show()
                    onAccountAdded()
                    onDismiss()
                },
                enabled = !isAddingIg,
                colors = ButtonDefaults.buttonColors(containerColor = GolikeBrandOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isAddingIg) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Đang kiểm tra & lưu tài khoản...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Lưu tài khoản vào danh sách",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ───────────────────────────── Thành phần con UI ───────────────────────────── */

@Composable
private fun GolikePermissionRow(
    iconPaths: List<String>,
    title: String,
    subtitle: String,
    granted: Boolean,
    grantedLabel: String,
    requestLabel: String,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Alx.Bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Alx.BlueSoft),
            contentAlignment = Alignment.Center,
        ) { SvgIcon(paths = iconPaths, size = 19.dp, color = Alx.Blue, strokeWidth = 1.8f) }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            BasicText(title, style = type(size = 14.5f, weight = FontWeight.Bold, color = Alx.Ink))
            Spacer(Modifier.height(2.dp))
            BasicText(subtitle, style = type(size = 12f, weight = FontWeight.Medium, color = Alx.Muted, lineHeight = 1.4f))
        }

        Spacer(Modifier.width(12.dp))

        if (granted) {
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Alx.BlueSoft)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SvgIcon(paths = listOf(IconCheckMark), size = 13.dp, color = Alx.Blue, strokeWidth = 3f)
                BasicText(grantedLabel, style = type(size = 12.5f, weight = FontWeight.ExtraBold, color = Alx.Blue))
            }
        } else {
            GolikePressBox(onClick = onGrant) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Alx.Blue)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(requestLabel, style = type(size = 13f, weight = FontWeight.Bold, color = Alx.White))
                }
            }
        }
    }
}

@Composable
private fun GolikeAppVariantCard(
    title: String,
    installed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GolikePressBox(onClick = onClick, enabled = installed) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) Alx.Ink else Alx.White)
                .then(
                    if (selected) Modifier else Modifier.border(1.dp, Alx.Line, RoundedCornerShape(18.dp))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    title,
                    style = type(
                        size = 14f,
                        weight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (selected) Alx.White else Alx.Ink2,
                        align = TextAlign.Center,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    if (installed) "Đã cài" else "Chưa cài",
                    style = type(
                        size = 11.5f,
                        weight = FontWeight.SemiBold,
                        color = if (selected) Color(0xFFB6C0D2) else Alx.Muted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GolikePressBox(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable (pressed: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else .45f)
    ) { content(pressed) }
}

private val IconOverlayWindow = listOf("M4 4h12v12H4z", "M8 20h12V8")

/* Icon Dịch vụ Trợ năng chuẩn Universal Accessibility (được định dạng cẩn thận các cờ SVG arc) */
private val IconAccessibility = listOf(
    "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
    "M10.7 7.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0",
    "M7 11.5l5-1 5 1",
    "M12 11.5v3.5",
    "M9.5 18.5l2.5-3.5 2.5 3.5",
)

private const val IconCheckMark = "M5 12.5l4.5 4.5L19 7.5"
