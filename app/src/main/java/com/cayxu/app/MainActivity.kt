package com.cayxu.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.navigation.CayXuNavGraph
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.CayXuTheme
import com.cayxu.app.ui.theme.ThemeState
import com.cayxu.app.util.DeviceUtils
import com.cayxu.app.util.IntegrityGuard

/**
 * Activity duy nhất của app (Single-Activity + Navigation Compose).
 * Sử dụng AppFlow điều hướng:
 *  - Lần đầu: Ngôn ngữ -> Nhập key -> Kiểm tra (ổ khóa) -> Chào mừng -> Trang chủ
 *  - Mở lại: Kiểm tra -> Trang chủ (bảo lưu key khi mất mạng)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nạp lựa chọn "Chế độ tối" đã lưu từ lần trước (nếu có) trước khi vẽ UI đầu tiên.
        ThemeState.init(this)
        // Nạp lựa chọn "Ngôn ngữ" (Tiếng Việt / English) đã lưu từ lần trước.
        LanguageState.init(this)

        setContent {
            CayXuTheme(darkTheme = ThemeState.isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val context = LocalContext.current
                        val store = remember { SecurePrefsSessionStore(context) }
                        val authRepo = remember { AuthRepository() }
                        val deviceId = remember { DeviceUtils.getAndroidId(context) }
                        val prefs = remember { SecurePrefs(context) }

                        AppFlow(
                            store = store,
                            validate = { key ->
                                when (val result = authRepo.verifyKey(key, deviceId)) {
                                    is AuthResult.Success -> {
                                        IntegrityGuard.bindKeyToDevice(context, key)
                                        result.data.effectiveUsername?.let { if (it.isNotBlank()) prefs.saveBuyerUsername(it) }
                                        result.data.packageName?.let { prefs.savePackageName(it) }
                                        result.data.expiresAt?.let { prefs.saveExpiresAt(it) }
                                        true
                                    }
                                    is AuthResult.NetworkError -> {
                                        // Nếu mở lại app có key đã lưu mà mất mạng -> giữ key, cho vào Trang chủ
                                        store.licenseKey != null
                                    }
                                    is AuthResult.ApiError -> {
                                        false
                                    }
                                }
                            },
                            onBuy = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://t.me/cayxu_admin")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            },
                            home = {
                                CayXuNavGraph(startDestination = Routes.HOME)
                            }
                        )
                    }
                }
            }
        }
    }
}
