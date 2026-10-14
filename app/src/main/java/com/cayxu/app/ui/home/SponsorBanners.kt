package com.cayxu.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.EaseOutSoft
import com.cayxu.app.ui.design.animationsEnabled
import com.cayxu.app.ui.design.type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Khối "Được tài trợ" ở Trang chủ: các banner ảnh tự trượt, chạm banner là mở web (link ẩn).
 * 100% dữ liệu ảnh, URL và mô tả được mã hóa ma trận và giải mã độc quyền từ C++ (.so).
 * Không lưu trữ bất kỳ file ảnh hoặc chuỗi URL nào ở tầng Java/Kotlin/Resources.
 */

data class SponsorBanner(val index: Int) {
    val url: String get() = NativeBannerBridge.loadBannerUrl(index)
    val description: String get() = NativeBannerBridge.loadBannerDesc(index)
}

val DefaultSponsorBanners = listOf(
    SponsorBanner(0),
    SponsorBanner(1),
)

/** Tỉ lệ khung banner (rộng : cao) = 310 : 177 ở màn 390dp; khung co giãn theo bề rộng màn hình. */
private const val BannerRatio = 310f / 177f

/**
 * @param autoSlideMs thời gian giữa hai lần tự chuyển banner. Tự chuyển dừng khi người dùng đang kéo, và tắt hẳn khi máy tắt
 * animation (Cài đặt hệ thống). Chấm phía trên bấm được để chọn banner.
 */
@Composable
fun SponsorCarousel(
    banners: List<SponsorBanner>,
    modifier: Modifier = Modifier,
    autoSlideMs: Long = 4000,
    onOpen: (String) -> Unit = {},
) {
    if (banners.isEmpty()) return
    val pager = rememberPagerState { banners.size }
    val scope = rememberCoroutineScope()
    val dragging by pager.interactionSource.collectIsDraggedAsState()

    if (banners.size > 1 && animationsEnabled()) {
        LaunchedEffect(pager, dragging) {
            if (dragging) return@LaunchedEffect
            while (true) {
                delay(autoSlideMs)
                pager.animateScrollToPage((pager.currentPage + 1) % banners.size, animationSpec = tween(450, easing = EaseOutSoft))
            }
        }
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("ĐƯỢC TÀI TRỢ", Modifier.weight(1f), style = type(11f, FontWeight.ExtraBold, Alx.Muted, spacing = 0.12f))
            if (banners.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    banners.indices.forEach { i ->
                        val on = pager.currentPage == i
                        val w by animateDpAsState(if (on) 18.dp else 6.dp, tween(250), label = "dotW")
                        val c by animateColorAsState(if (on) Alx.Blue else Color(0xFFCBD2DF), tween(250), label = "dotC")
                        Box(
                            Modifier.clickable(role = Role.Button, onClickLabel = "Banner ${i + 1}") { scope.launch { pager.animateScrollToPage(i) } }
                                .padding(horizontal = 2.5.dp, vertical = 8.dp),
                        ) { Box(Modifier.width(w).height(6.dp).background(c, CircleShape)) }
                    }
                }
            }
        }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = 40.dp),
            pageSpacing = 12.dp,
            snapPosition = SnapPosition.Start,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Banner(banners[page], onOpen)
        }
    }
}

@Composable
private fun Banner(b: SponsorBanner, onOpen: (String) -> Unit) {
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(22.dp)
    val nativeBitmap = remember(b.index) { NativeBannerBridge.loadBannerBitmap(b.index) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(BannerRatio)
            .clip(shape)
            .border(1.dp, Alx.Line, shape)
            .semantics { contentDescription = b.description }
            .clickable(role = Role.Button) {
                val targetUrl = b.url
                if (targetUrl.isNotBlank()) {
                    onOpen(targetUrl)
                    uri.openUri(targetUrl)
                }
            },
    ) {
        if (nativeBitmap != null) {
            Image(
                bitmap = nativeBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFFE2E8F0)))
        }
        BasicText(
            "QUẢNG CÁO",
            Modifier.align(Alignment.TopEnd).padding(8.dp)
                .background(Color(0x8C0F172A), CircleShape)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            style = type(9.5f, FontWeight.ExtraBold, Alx.White, spacing = 0.06f),
        )
    }
}
