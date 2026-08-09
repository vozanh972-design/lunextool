package com.cayxu.app.ui.screens.golike

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.cayxu.app.data.repository.GolikeAuthRepository
import com.cayxu.app.data.repository.GolikeLoginResult
import com.cayxu.app.data.repository.GolikeStatisticsRepository
import com.cayxu.app.data.repository.GolikeStatisticsResult
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.launch

private const val GOLIKE_LOGIN_URL = "https://app.golike.net/login"

/**
 * Màn đăng nhập Golike - đăng nhập THẬT ngay trên trang web thật của GoLike qua WebView
 * (KHÔNG còn dán token thủ công) - vì trang đăng nhập của GoLike có dùng reCAPTCHA (site key
 * chỉ hoạt động đúng khi chạy TRONG trình duyệt/WebView trên đúng domain app.golike.net,
 * KHÔNG hoạt động khi app native tự gọi API), nên cách chắc ăn nhất là để người dùng tự đăng
 * nhập trên chính trang web thật đó, y hệt như trên máy tính.
 *
 * Cách lấy token: "nghe lén" (không sửa/chặn) mọi request mà WebView tự gửi đi qua
 * shouldInterceptRequest() - ngay khi thấy request nào có header "Authorization: Bearer ..."
 * (chắc chắn có sau khi đăng nhập xong, vì trang web tự gọi API bằng token đó) là lấy được
 * token thật, gọi GET /api/users/me y hệt luồng cũ để xác nhận + lưu phiên, rồi đóng WebView
 * quay lại app - người dùng không cần tự tìm/copy gì cả.
 *
 * WebView CHỈ DÙNG 1 LẦN cho việc đăng nhập rồi đóng ngay (không giữ WebView chạy nền) - giữ
 * "siêu nhẹ": không bật thêm tính năng gì ngoài JS + DOM storage (bắt buộc phải có 2 cái này
 * vì trang GoLike là SPA hiện đại, thiếu là trắng trang).
 */
@Composable
fun GolikeLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Chặn xử lý > 1 lần nếu WebView gửi nhiều request cùng lúc đều có Authorization.
    val alreadyCaptured = remember { mutableStateOf(false) }

    fun handleCapturedToken(rawToken: String) {
        if (alreadyCaptured.value) return
        alreadyCaptured.value = true
        isProcessing = true
        errorMessage = null
        val token = rawToken.trim()
        scope.launch {
            when (val result = GolikeAuthRepository.fetchMe(token)) {
                is GolikeLoginResult.Success -> {
                    GolikeSession.login(
                        context = context,
                        token = token,
                        userName = result.info.name,
                        userHandle = result.info.handle,
                        userEmail = result.info.email,
                        userCoin = result.info.coin
                    )
                    when (val statsResult = GolikeStatisticsRepository.fetchReport(token)) {
                        is GolikeStatisticsResult.Success -> GolikeSession.updateStatistics(context, statsResult.report)
                        is GolikeStatisticsResult.Error -> Unit
                    }
                    isProcessing = false
                    Toast.makeText(context, "Đăng nhập Golike thành công", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is GolikeLoginResult.Error -> {
                    // Token bắt được không hợp lệ (hiếm, có thể do request nội bộ khác) - cho
                    // thử lại, KHÔNG khoá màn hình vĩnh viễn.
                    isProcessing = false
                    alreadyCaptured.value = false
                    errorMessage = result.message
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("Đăng nhập Golike", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        if (errorMessage != null) {
            Text(
                errorMessage.orEmpty(),
                color = DangerRed,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            GolikeLoginWebView(
                onTokenCaptured = { token -> handleCapturedToken(token) }
            )
            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(AppBackground.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF7C3AED))
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GolikeLoginWebView(onTokenCaptured: (String) -> Unit) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    AndroidView(
        factory = { ctx ->
            // XOÁ SẠCH dữ liệu web cũ (localStorage/sessionStorage) TRƯỚC - cái này chạy
            // ĐỒNG BỘ nên xong ngay, không cần đợi. WebStorage KHÔNG liên quan gì tới việc
            // app đã "Đăng xuất" hay chưa (đăng xuất trong app chỉ xoá token app tự lưu),
            // nên nếu không xoá sẽ bị web tự dùng lại phiên GoLike cũ thay vì cho đăng nhập
            // mới thật sự.
            WebStorage.getInstance().deleteAllData()

            val webView = WebView(ctx).apply {
                // "Siêu nhẹ" - chỉ bật đúng 2 thứ BẮT BUỘC để trang SPA của GoLike chạy được
                // (thiếu JS/DOM storage là trắng trang), không bật gì thêm ngoài ra.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        // CHỈ đọc header để "nghe lén" token, KHÔNG chặn/sửa gì - luôn trả về
                        // null để WebView tự xử lý request như bình thường.
                        val authHeader = request.requestHeaders.entries
                            .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
                            ?.value
                        if (!authHeader.isNullOrBlank() && authHeader.startsWith("Bearer ", ignoreCase = true)) {
                            mainHandler.post { onTokenCaptured(authHeader) }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
            }

            // XOÁ COOKIE chạy BẤT ĐỒNG BỘ - PHẢI đợi đúng lúc nó báo xong (qua callback) rồi
            // MỚI thật sự loadUrl(), nếu không trang có thể đã bắt đầu tải (dùng cookie cũ)
            // trước khi lệnh xoá thực sự hoàn tất, dẫn tới vẫn dính token cũ đã hết hạn.
            CookieManager.getInstance().apply {
                removeAllCookies { _ ->
                    flush()
                    webView.loadUrl(GOLIKE_LOGIN_URL)
                }
            }

            webView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose { }
    }
}
