package com.cayxu.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.screens.welcome.LanguageRoute
import com.cayxu.app.ui.license.LicenseRoute
import com.cayxu.app.ui.screens.blocked.BlockedScreen
import com.cayxu.app.ui.success.ActivationSuccessScreen
import com.cayxu.app.ui.verify.VerifyingRoute

/** Lưu cục bộ (SharedPreferences / DataStore / EncryptedSharedPreferences). Bạn tự cài đặt. */
interface SessionStore {
    var language: String?        // null = chưa chọn ngôn ngữ (lần đầu mở app)
    var licenseKey: String?      // null = chưa từng kích hoạt
}

/** Cài đặt mặc định của SessionStore sử dụng SecurePrefs (EncryptedSharedPreferences AES-256). */
class SecurePrefsSessionStore(private val context: Context) : SessionStore {
    private val prefs = SecurePrefs(context)

    override var language: String?
        get() = if (prefs.hasSeenWelcome()) LanguageState.language.name else null
        set(value) {
            if (value != null) {
                runCatching {
                    val lang = com.cayxu.app.ui.locale.AppLanguage.valueOf(value)
                    LanguageState.setLanguage(context, lang)
                }
            }
        }

    override var licenseKey: String?
        get() = prefs.getKey()
        set(value) {
            if (value != null) prefs.saveKey(value) else prefs.clearKey()
        }
}

private sealed interface Step {
    data object Language : Step
    data class License(val key: String = "", val error: String? = null) : Step
    /** [firstTime] = true: vừa nhập key (sẽ có màn chào mừng). false: mở lại app (vào Trang chủ luôn). */
    data class Verifying(val key: String, val firstTime: Boolean) : Step
    data object Welcome : Step
    data object Home : Step
}

/**
 * Luồng chính:
 *  - LẦN ĐẦU : Chọn ngôn ngữ → Nhập key → Kiểm tra → Chào mừng → Trang chủ.
 *  - MỞ LẠI  : (đã có key lưu) Kiểm tra → Trang chủ. Không ngôn ngữ, không nhập key, không chào mừng.
 *  - Mở lại mà key hết hạn / bị khóa: Kiểm tra → báo sai → quay về màn Nhập key (có thông báo lỗi).
 */
@Composable
fun AppFlow(
    store: SessionStore,
    validate: suspend (String) -> Boolean,     // gọi server, true nếu key hợp lệ
    onBuy: () -> Unit,
    home: @Composable () -> Unit,              // khung chính của app: MainRoute(home = …, tasks = …, …), dock nằm trong MainRoute
) {
    val context = LocalContext.current
    val isBlocked = remember { SecurePrefs(context).isPermanentlyBlocked() }
    if (isBlocked) {
        BlockedScreen()
        return
    }

    var step by remember {
        mutableStateOf<Step>(
            when {
                store.licenseKey != null -> Step.Verifying(store.licenseKey!!, firstTime = false)
                store.language == null -> Step.Language
                else -> Step.License()
            }
        )
    }

    var showBuyWeb by remember { mutableStateOf(false) }

    if (showBuyWeb) {
        com.cayxu.app.ui.license.SeamlessLoginWebView(
            onClose = { showBuyWeb = false }
        )
        return
    }

    when (val s = step) {
        Step.Language -> LanguageRoute(onDone = { lang ->
            store.language = lang.name
            step = Step.License()
        })

        is Step.License -> LicenseRoute(
            initialKey = s.key,
            initialError = s.error,
            onBuy = { showBuyWeb = true },
            onActivated = { key -> step = Step.Verifying(key, firstTime = true) },
        )

        is Step.Verifying -> VerifyingRoute(
            key = s.key,
            validate = validate,
            onSuccess = {
                store.licenseKey = s.key                 // nhớ key cho các lần mở sau
                step = if (s.firstTime) Step.Welcome else Step.Home
            },
            onFailure = {
                if (!s.firstTime) store.licenseKey = null // key cũ không còn dùng được
                step = Step.License(
                    key = if (s.firstTime) s.key else "",
                    error = if (LanguageState.language == com.cayxu.app.ui.locale.AppLanguage.EN) "Invalid or blocked license key. Please check again." else "Mã key không đúng hoặc đã bị khóa. Vui lòng kiểm tra lại.",
                )
            },
        )

        Step.Welcome -> {
            val prefs = remember { SecurePrefs(context) }
            val buyerName = remember { prefs.getBuyerUsername() }
            val username = remember(buyerName) {
                if (!buyerName.isNullOrBlank()) buyerName else "username"
            }
            val daysLeft = remember { prefs.getDaysLeft() }

            ActivationSuccessScreen(
                username = username,
                daysLeft = daysLeft,
                onFinished = {
                    prefs.setSeenWelcome()
                    step = Step.Home
                },
                home = home,
            )
        }

        Step.Home -> home()
    }
}
