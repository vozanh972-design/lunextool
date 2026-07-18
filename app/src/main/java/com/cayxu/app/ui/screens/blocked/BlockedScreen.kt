package com.cayxu.app.ui.screens.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.DangerRed
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Màn hình duy nhất được phép hiển thị khi app bị khoá vĩnh viễn (phát hiện
 * app bị patch/bypass, hoặc key đã bị server thu hồi khi re-check định kỳ).
 * KHÔNG có nút "thử lại" hay đường quay về Login - đúng theo yêu cầu "lỗi app
 * không thể sử dụng thêm" khi phát hiện bị crack.
 */
@Composable
fun BlockedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Block,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Ứng dụng đã bị khoá",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hệ thống phát hiện bất thường hoặc key đã hết hiệu lực. " +
                    "Vui lòng gỡ cài đặt và cài lại ứng dụng, hoặc liên hệ hỗ trợ để được cấp key mới.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
