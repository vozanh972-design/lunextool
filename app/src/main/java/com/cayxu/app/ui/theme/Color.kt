package com.cayxu.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Giữ nguyên TÊN cũ để không phải sửa lại toàn bộ màn hình đang import các hằng số này,
// nhưng giờ mỗi tên đọc màu theo palette hiện tại (sáng/tối) qua LocalCayXuColors,
// nên khi bật "Chế độ tối" ở Cài đặt, toàn bộ app đổi màu theo, không cần đụng vào
// từng file HomeScreen/WalletScreen/AccountScreen/...

val Primary: Color @Composable get() = LocalCayXuColors.current.primary
val PrimaryDark: Color @Composable get() = LocalCayXuColors.current.primaryDark
val AppBackground: Color @Composable get() = LocalCayXuColors.current.appBackground
val CardWhite: Color @Composable get() = LocalCayXuColors.current.cardWhite
val TextPrimary: Color @Composable get() = LocalCayXuColors.current.textPrimary
val TextSecondary: Color @Composable get() = LocalCayXuColors.current.textSecondary
val SuccessGreen: Color @Composable get() = LocalCayXuColors.current.successGreen
val DangerRed: Color @Composable get() = LocalCayXuColors.current.dangerRed
val InfoBlueBg: Color @Composable get() = LocalCayXuColors.current.infoBlueBg
