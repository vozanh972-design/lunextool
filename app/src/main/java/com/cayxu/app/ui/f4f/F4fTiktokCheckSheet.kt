package com.cayxu.app.ui.f4f

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cayxu.app.automation.tiktok.TikTokAppLauncher
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.PrimaryButton
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.overlay.xsmm.startXsmmVerifyTikTokAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Các app TikTok có thể chọn để kiểm tra trong Follow4Follow.
 */
enum class F4fTiktokVariant(val title: String, val variant: TikTokAppVariant) {
    TikTok("TikTok", TikTokAppVariant.STANDARD),
    TikTokLite("TikTok Lite", TikTokAppVariant.LITE),
    TikTokStudio("TikTok Studio", TikTokAppVariant.STUDIO),
}

/**
 * Sheet kiểm tra tài khoản TikTok cho Follow4Follow (F4F)
 * Giao diện và phong cách thiết kế đồng bộ chuẩn 100% với màn kiểm tra TikTok của hệ thống.
 */
@Composable
fun F4fTikTokCheckSheet(
    initialVariant: TikTokAppVariant = TikTokAppVariant.STANDARD,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedApp by remember {
        mutableStateOf(
            when (initialVariant) {
                TikTokAppVariant.LITE -> F4fTiktokVariant.TikTokLite
                TikTokAppVariant.STUDIO -> F4fTiktokVariant.TikTokStudio
                else -> F4fTiktokVariant.TikTok
            }
        )
    }
    var overlayGranted by remember { mutableStateOf(TikTokAppLauncher.isOverlayPermissionGranted(context)) }
    var accessibilityGranted by remember { mutableStateOf(TikTokAppLauncher.isAccessibilityServiceEnabled(context)) }

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

    val installedApps = remember(context) {
        val set = mutableSetOf<F4fTiktokVariant>()
        if (TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STANDARD)) set.add(F4fTiktokVariant.TikTok)
        if (TikTokAppLauncher.isInstalled(context, TikTokAppVariant.LITE)) set.add(F4fTiktokVariant.TikTokLite)
        if (TikTokAppLauncher.isInstalled(context, TikTokAppVariant.STUDIO)) set.add(F4fTiktokVariant.TikTokStudio)
        set
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

        var shown by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            shown = true
        }

        val dismissWithAnim: () -> Unit = {
            scope.launch {
                shown = false
                delay(220)
                onDismiss()
            }
        }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(220),
            label = "scrimAlpha"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(Color(0x730F172A))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismissWithAnim,
                    )
            )

            AnimatedVisibility(
                visible = shown,
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
                            onClick = {}
                        )
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp)
                ) {
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

                    PermissionRow(
                        iconPaths = IconOverlayWindow,
                        title = "Hiển thị trên ứng dụng khác",
                        subtitle = "Để hiện màn nổi kiểm tra và điều khiển trên TikTok",
                        granted = overlayGranted,
                        grantedLabel = "Đã cấp",
                        requestLabel = "Cấp quyền",
                        onGrant = { TikTokAppLauncher.openOverlayPermissionSettings(context) },
                    )

                    Spacer(Modifier.height(10.dp))

                    PermissionRow(
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

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(64.dp),
                    ) {
                        items(F4fTiktokVariant.entries) { app ->
                            AppVariantCard(
                                title = app.title,
                                installed = installedApps.contains(app),
                                selected = selectedApp == app,
                                onClick = { selectedApp = app },
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    val checkEnabled = accessibilityGranted

                    PrimaryButton(
                        label = "Kiểm tra tài khoản",
                        onClick = {
                            val variant = selectedApp.variant
                            if (!TikTokAppLauncher.isInstalled(context, variant)) {
                                Toast.makeText(context, "Chưa cài đặt ${selectedApp.title} trên máy này", Toast.LENGTH_SHORT).show()
                                return@PrimaryButton
                            }
                            if (!overlayGranted || !accessibilityGranted) {
                                Toast.makeText(context, "Vui lòng cấp đủ 2 quyền ở trên trước khi kiểm tra", Toast.LENGTH_SHORT).show()
                                return@PrimaryButton
                            }

                            startXsmmVerifyTikTokAccount(context, variant)
                            onDismiss()
                        },
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
}

/* ───────────────────────────── Thành phần con ───────────────────────────── */

@Composable
private fun PermissionRow(
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
            PressBox(onClick = onGrant) { _ ->
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
private fun AppVariantCard(
    title: String,
    installed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PressBox(onClick = onClick, enabled = installed) { _ ->
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
private fun PressBox(
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

private val IconAccessibility = listOf(
    "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
    "M10.7 7.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0",
    "M7 11.5l5-1 5 1",
    "M12 11.5v3.5",
    "M9.5 18.5l2.5-3.5 2.5 3.5",
)

private const val IconCheckMark = "M5 12.5l4.5 4.5L19 7.5"
