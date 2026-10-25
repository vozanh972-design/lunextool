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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type
import com.cayxu.app.ui.overlay.xsmm.startXsmmVerifyTikTokAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val F4fViolet = Color(0xFF7C3AED)
private val F4fVioletSoft = Color(0xFFF5F3FF)

enum class F4fTiktokVariant(val title: String, val appVariant: TikTokAppVariant) {
    TikTok("TikTok", TikTokAppVariant.STANDARD),
    TikTokLite("TikTok Lite", TikTokAppVariant.LITE),
    TikTokStudio("TikTok Studio", TikTokAppVariant.STUDIO),
}

private val IconOverlay = listOf(
    "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2z",
    "M13 11h5v5h-5z"
)

private val IconAccess = listOf(
    "M12 4a2 2 0 1 0 0 -4 2 2 0 0 0 0 4z",
    "M4 8h16M12 8v12M8 12l4 4 4 -4M9 20l3 -4 3 4"
)

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

        LaunchedEffect(Unit) { shown = true }

        val dismissWithAnim: () -> Unit = {
            scope.launch {
                shown = false
                delay(220)
                onDismiss()
            }
        }

        val scrimAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "f4fScrim")

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
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Alx.InputBorder)
                    )

                    Spacer(Modifier.height(16.dp))
                    BasicText("Kiểm tra tài khoản TikTok", style = type(size = 20f, weight = FontWeight.ExtraBold, color = Alx.Ink))
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        "Màn hình nổi và Trợ năng cần được cấp quyền để tự động mở TikTok và kiểm tra trạng thái nick.",
                        style = type(size = 13f, weight = FontWeight.Medium, color = Alx.Muted, lineHeight = 1.45f),
                    )

                    Spacer(Modifier.height(16.dp))

                    F4fPermissionRow(
                        icon = IconOverlay,
                        title = "Hiển thị trên ứng dụng khác",
                        subtitle = "Để hiện màn nổi kiểm tra tài khoản TikTok",
                        granted = overlayGranted,
                        onGrant = { TikTokAppLauncher.openOverlayPermissionSettings(context) }
                    )

                    Spacer(Modifier.height(10.dp))

                    F4fPermissionRow(
                        icon = IconAccess,
                        title = "Dịch vụ Trợ năng",
                        subtitle = "Để tự động bấm tab \"Tôi\" và kiểm tra @username TikTok",
                        granted = accessibilityGranted,
                        onGrant = { TikTokAppLauncher.openAccessibilitySettings(context) }
                    )

                    Spacer(Modifier.height(18.dp))
                    BasicText("CHỌN ỨNG DỤNG CẦN KIỂM TRA", style = type(size = 11f, weight = FontWeight.ExtraBold, color = Alx.Muted, spacing = 0.12f))
                    Spacer(Modifier.height(10.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(64.dp),
                    ) {
                        items(F4fTiktokVariant.entries) { app ->
                            val selected = selectedApp == app
                            val installed = installedApps.contains(app)
                            Box(
                                modifier = Modifier
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) F4fVioletSoft else Alx.Bg)
                                    .border(if (selected) 2.dp else 1.dp, if (selected) F4fViolet else Alx.Line, RoundedCornerShape(14.dp))
                                    .clickable { selectedApp = app },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    BasicText(app.title, style = type(13f, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) F4fViolet else Alx.Ink))
                                    BasicText(if (installed) "Đã cài" else "Chưa cài", style = type(10.5f, FontWeight.Normal, if (installed) Color(0xFF16A34A) else Alx.Muted))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    val canCheck = accessibilityGranted && overlayGranted
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (canCheck) F4fViolet else Alx.InputBorder)
                            .clickable(enabled = canCheck) {
                                val variant = selectedApp.appVariant
                                if (!TikTokAppLauncher.isInstalled(context, variant)) {
                                    Toast.makeText(context, "Chưa cài đặt ${selectedApp.title} trên máy này", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                startXsmmVerifyTikTokAccount(context, variant)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("Kiểm tra tài khoản", style = type(15.5f, FontWeight.Bold, Alx.White))
                    }

                    if (!canCheck) {
                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            "Vui lòng cấp đủ 2 quyền ở trên để tiếp tục",
                            style = type(12f, FontWeight.Medium, Alx.Muted),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun F4fPermissionRow(
    icon: List<String>,
    title: String,
    subtitle: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (granted) Color(0xFFF0FDF4) else Color(0xFFFFFBEB))
            .border(1.dp, if (granted) Color(0xFFBBF7D0) else Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center
        ) {
            SvgIcon(icon, 20.dp, if (granted) Color(0xFF16A34A) else Color(0xFFD97706), 2f)
        }

        Column(Modifier.weight(1f)) {
            BasicText(title, style = type(13.5f, FontWeight.Bold, Alx.Ink))
            BasicText(subtitle, style = type(11.5f, FontWeight.Normal, Alx.Muted))
        }

        if (granted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFDCFCE7))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                BasicText("Đã cấp", style = type(12f, FontWeight.Bold, Color(0xFF16A34A)))
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(F4fViolet)
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                BasicText("Cấp quyền", style = type(12f, FontWeight.Bold, Alx.White))
            }
        }
    }
}
