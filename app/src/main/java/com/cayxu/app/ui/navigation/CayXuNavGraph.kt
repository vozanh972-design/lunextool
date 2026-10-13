package com.cayxu.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cayxu.app.data.local.AppLockState
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.components.CayXuBottomBar
import com.cayxu.app.ui.components.ForceUpdateDialog
import com.cayxu.app.ui.screens.account.AccountScreen
import com.cayxu.app.ui.screens.blocked.BlockedScreen
import com.cayxu.app.util.AppUpdateManager
import com.cayxu.app.ui.screens.friends.FriendsScreen
import com.cayxu.app.ui.home.HomeScreen
import com.cayxu.app.ui.screens.login.LoginScreen
import com.cayxu.app.ui.screens.settings.SettingsScreen
import com.cayxu.app.ui.screens.tasks.TasksScreen
import com.cayxu.app.ui.screens.wallet.WalletScreen
import com.cayxu.app.ui.screens.welcome.LanguageSelectionScreen
import com.cayxu.app.ui.screens.welcome.OnboardingScreen
import com.cayxu.app.ui.screens.welcome.WelcomeScreen
import com.cayxu.app.ui.success.ActivationSuccessScreen
import com.cayxu.app.ui.theme.AppBackground

object Routes {
    const val ONBOARDING = "onboarding"
    const val LANGUAGE_SELECTION = "language_selection"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val BLOCKED = "blocked"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val WALLET = "wallet"
    const val FRIENDS = "friends"
    const val ACCOUNT = "account"
    const val SETTINGS = "settings"
    const val UTILITIES = "utilities"
    const val NURTURE_SETUP = "nurture_setup"
    const val REG_AND_TRANSFER_PAGE = "reg_and_transfer_page"
    const val FB_WEB_LOGIN = "fb_web_login"
    const val FB_NURTURE = "fb_nurture"
    const val FB_NUOI_CONFIG = "fb_nuoi_config"

    /** Màn nhiệm vụ đơn giản (Facebook + TikTok) dùng chung cho 2 dịch vụ: Trao đổi Sub,
     *  Tương tác chéo - nhận theo tên dịch vụ để hiển thị đúng tiêu đề/màu sắc. */
    const val SIMPLE_TASK_PLATFORM = "simple_task_platform/{service}"

    fun simpleTaskPlatform(service: String) = "simple_task_platform/$service"

    const val XSMM_LOGIN = "xsmm_login"
    const val XSMM_ACCOUNT = "xsmm_account"
    const val XSMM_RUN_CONFIG = "xsmm_run_config"
    const val GOLIKE_ACCOUNT = "golike_account"
    const val TUONG_TAC_CHEO_TIKTOK = "tuong_tac_cheo_tiktok"

    const val LINK_ACCOUNT = "link_account/{platform}/{iconRes}"
    const val ADD_ACCOUNT = "add_account/{platform}/{iconRes}"

    // Facebook có route RIÊNG, độc lập với route dùng chung ở trên
    // (Instagram/LinkedIn/... vẫn dùng LINK_ACCOUNT/ADD_ACCOUNT như cũ).
    const val LINK_ACCOUNT_FACEBOOK = "link_account_facebook"
    const val ADD_ACCOUNT_FACEBOOK = "add_account_facebook"

    // TikTok cũng có route RIÊNG (khác Facebook và khác route dùng chung) vì có luồng
    // thêm tài khoản bằng cách check trực tiếp trong app TikTok/Lite/Studio.
    const val LINK_ACCOUNT_TIKTOK = "link_account_tiktok"

    fun linkAccount(platform: String, iconRes: Int) = "link_account/$platform/$iconRes"
    fun addAccount(platform: String, iconRes: Int) = "add_account/$platform/$iconRes"
}

// Các route hiện thanh điều hướng dưới (bottom bar cố định, không nằm trong vùng chuyển
// cảnh mờ dần, nên không bị "nẩy" hay animate theo nội dung mỗi lần chuyển màn).
// Routes.HOME dùng NavBar riêng dạng viên thuốc nổi theo thiết kế canvas.
private val routesWithBottomBar = setOf(Routes.TASKS, Routes.UTILITIES, Routes.ACCOUNT, Routes.WALLET, Routes.FRIENDS)

/**
 * Luôn đưa được về Trang chủ, dùng cho nút back thủ công ở các màn như Nhiệm vụ/Ví/Tiện ích
 * (khác với navController.popBackStack() đơn thuần, cái đó CHỈ lùi lại đúng 1 bước và có thể
 * "kẹt" không về Home nếu back stack ở trạng thái bất thường - vd màn được mở lại nhiều lần từ
 * nhiều lối vào khác nhau). popBackStack(HOME, inclusive = false) sẽ lùi thẳng tới Home nếu
 * Home còn trong back stack; nếu vì lý do gì đó Home không còn trong stack nữa (edge case),
 * fallback sang navigate thẳng tới Home với back stack sạch.
 */
fun androidx.navigation.NavController.goHome() {
    val reachedHome = popBackStack(Routes.HOME, false)
    if (!reachedHome) {
        navigate(Routes.HOME) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
}

private const val FADE_DURATION_MS = 260

// Animation RIÊNG, rõ ràng hơn fade thường - chỉ áp dụng cho đúng cặp Welcome -> Nhập Key,
// tạo cảm giác "nối tiếp" (nội dung Welcome trượt/mờ dần sang trái thu nhỏ lại, đồng thời
// nội dung màn Key trượt vào từ phải phóng to lên) thay vì chỉ crossfade như mọi màn khác.
private const val WELCOME_TO_LOGIN_DURATION_MS = 450

private fun welcomeToLoginEnter() =
    slideInHorizontally(animationSpec = tween(WELCOME_TO_LOGIN_DURATION_MS)) { fullWidth -> fullWidth / 3 } +
        fadeIn(animationSpec = tween(WELCOME_TO_LOGIN_DURATION_MS)) +
        scaleIn(initialScale = 0.94f, animationSpec = tween(WELCOME_TO_LOGIN_DURATION_MS))

private fun welcomeToLoginExit() =
    slideOutHorizontally(animationSpec = tween(WELCOME_TO_LOGIN_DURATION_MS)) { fullWidth -> -fullWidth / 4 } +
        fadeOut(animationSpec = tween(WELCOME_TO_LOGIN_DURATION_MS)) +
        scaleOut(targetScale = 1.06f, animationSpec = tween(WELCOME_TO_LOGIN_DURATION_MS))

@Composable
fun CayXuNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String? = null,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    // Ngay khi worker/tamper-check báo "đã bị khoá" (key vừa bị server reset, hoặc
    // phát hiện app bị patch) trong lúc app ĐANG MỞ, văng người dùng ra khỏi bất kỳ
    // màn nào họ đang đứng, thẳng tới BlockedScreen, xoá sạch back stack - không giữ
    // họ ở lại phiên đang dùng dở nữa.
    val isBlockedNow by AppLockState.blocked.collectAsState()
    LaunchedEffect(isBlockedNow) {
        if (isBlockedNow && currentRoute != Routes.BLOCKED) {
            navController.navigate(Routes.BLOCKED) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Key bị server thu hồi/hết hạn khi worker re-check định kỳ (KHÁC với bị khoá
    // vĩnh viễn ở trên) -> đưa NGAY người dùng về màn nhập Key để họ tự nhập key
    // mới/gia hạn, dù đang đứng ở màn nào (Home, Tasks...). consumeKeyRevoked()
    // reset lại tín hiệu ngay sau khi điều hướng để không lặp lại nếu currentRoute
    // đổi qua lại.
    val isKeyRevokedNow by AppLockState.keyRevoked.collectAsState()
    LaunchedEffect(isKeyRevokedNow) {
        if (isKeyRevokedNow && currentRoute != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
            AppLockState.consumeKeyRevoked()
        }
    }

    // Tự động kiểm tra bản cập nhật mới ngay khi mở app.
    // Nếu máy chủ đã phát hành bản mới (versionCode > versionCode hiện tại),
    // ứng dụng bản cũ sẽ bị vô hiệu hóa hoàn toàn bằng ForceUpdateDialog.
    val updateRequired by AppUpdateManager.updateRequired.collectAsState()
    LaunchedEffect(Unit) {
        AppUpdateManager.initLocalCheck(context)
        AppUpdateManager.checkUpdate(context)
    }

    // Scaffold + thanh điều hướng dưới nằm CỐ ĐỊNH ở đây, bên NGOÀI NavHost.
    // -> Khi chuyển màn, chỉ phần nội dung bên trong NavHost mờ dần (crossfade),
    // thanh dưới đứng yên hoàn toàn, không nẩy/trượt/mờ theo.
    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            if (currentRoute in routesWithBottomBar) {
                CayXuBottomBar(navController)
            }
        }
    ) { innerPadding ->
        val securePrefs = SecurePrefs(context)
        val defaultStart = when {
            securePrefs.isPermanentlyBlocked() -> Routes.BLOCKED
            !securePrefs.hasSeenWelcome() -> Routes.LANGUAGE_SELECTION
            else -> Routes.LOGIN
        }
        NavHost(
            navController = navController,
            startDestination = startDestination ?: defaultStart,
            modifier = Modifier.padding(innerPadding),
            // Chỉ mờ dần nội dung cũ -> hiện nội dung mới, KHÔNG trượt ngang trái/phải.
            enterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) }
        ) {

            // 1. Màn hình Chọn Ngôn ngữ (LanguageSelectionScreen)
            composable(Routes.LANGUAGE_SELECTION) {
                LanguageSelectionScreen(
                    onFinished = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.LANGUAGE_SELECTION) { inclusive = true }
                        }
                    }
                )
            }

            // 2. Màn hình Đăng nhập Bản quyền (LicenseScreen - Thiết kế AUTOLUNEX chuẩn License.dc.html)
            composable(
                Routes.LOGIN,
                enterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) }
            ) {
                com.cayxu.app.ui.license.LicenseRoute(
                    onActivated = { _ ->
                        navController.navigate(Routes.WELCOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onBuy = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://t.me/cayxu_admin")
                        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    }
                )
            }

            // 3. Màn hình Chào mừng (ActivationSuccessScreen - Chuẩn AUTOLUNEX mới)
            composable(
                Routes.WELCOME,
                exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) }
            ) {
                val currentContext = LocalContext.current
                val prefs = remember { SecurePrefs(currentContext) }
                val buyerName = remember { prefs.getBuyerUsername() }
                val username = remember(buyerName) {
                    if (!buyerName.isNullOrBlank()) buyerName else "benbenteo10"
                }
                val daysLeft = remember { prefs.getDaysLeft() }

                ActivationSuccessScreen(
                    username = username,
                    daysLeft = daysLeft,
                    onFinished = {
                        prefs.setSeenWelcome()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    },
                    home = {
                        HomeScreen(navController)
                    }
                )
            }

            // Onboarding dự phòng (giữ định nghĩa route để tránh breaking change)
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.BLOCKED) { BlockedScreen() }

            composable(Routes.HOME) {
                com.cayxu.app.ui.main.MainRoute(
                    home = { HomeScreen(navController) },
                    tasks = { com.cayxu.app.ui.tasks.TasksScreen(navController) },
                    utilities = { com.cayxu.app.ui.utilities.UtilitiesScreen(navController) },
                    profile = { com.cayxu.app.ui.profile.ProfileScreen(navController) },
                )
            }
            composable(Routes.TASKS) { com.cayxu.app.ui.tasks.TasksScreen(navController) }
            composable(Routes.WALLET) { WalletScreen(navController) }
            composable(Routes.FRIENDS) { FriendsScreen(navController) }
            composable(Routes.ACCOUNT) { com.cayxu.app.ui.profile.ProfileScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.UTILITIES) {
                com.cayxu.app.ui.utilities.UtilitiesScreen(navController)
            }
            composable(Routes.REG_AND_TRANSFER_PAGE) {
                com.cayxu.app.ui.screens.utilities.RegAndTransferPageScreen(navController)
            }
            composable(Routes.NURTURE_SETUP) {
                com.cayxu.app.ui.screens.nurture.NurtureSetupScreen(navController)
            }
            composable(
                Routes.SIMPLE_TASK_PLATFORM,
                arguments = listOf(androidx.navigation.navArgument("service") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val service = backStackEntry.arguments?.getString("service").orEmpty()
                com.cayxu.app.ui.screens.tasks.SimpleTaskPlatformScreen(navController, service)
            }
            composable(Routes.XSMM_LOGIN) {
                com.cayxu.app.ui.screens.xsmm.XsmmLoginScreen(navController)
            }
            composable(Routes.XSMM_ACCOUNT) {
                com.cayxu.app.ui.screens.xsmm.XsmmAccountScreen(navController)
            }
            composable(Routes.XSMM_RUN_CONFIG) {
                com.cayxu.app.ui.screens.xsmm.XsmmRunConfigScreen(navController)
            }
            composable(Routes.GOLIKE_ACCOUNT) {
                com.cayxu.app.ui.screens.golike.GolikeAccountScreen(navController)
            }
            composable(Routes.TUONG_TAC_CHEO_TIKTOK) {
                com.cayxu.app.ui.screens.tasks.TuongTacCheoTikTokScreen(navController)
            }
            composable(
                route = Routes.LINK_ACCOUNT,
                arguments = listOf(
                    androidx.navigation.navArgument("platform") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("iconRes") { type = androidx.navigation.NavType.IntType }
                )
            ) { backStack ->
                val platform = backStack.arguments?.getString("platform") ?: ""
                val iconRes = backStack.arguments?.getInt("iconRes") ?: 0
                com.cayxu.app.ui.screens.linkaccount.LinkAccountScreen(
                    navController = navController,
                    platform = platform,
                    iconRes = iconRes
                )
            }
            composable(
                route = Routes.ADD_ACCOUNT,
                arguments = listOf(
                    androidx.navigation.navArgument("platform") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("iconRes") { type = androidx.navigation.NavType.IntType }
                )
            ) { backStack ->
                val platform = backStack.arguments?.getString("platform") ?: ""
                val iconRes = backStack.arguments?.getInt("iconRes") ?: 0
                com.cayxu.app.ui.screens.linkaccount.AddAccountScreen(
                    navController = navController,
                    platform = platform,
                    iconRes = iconRes
                )
            }

            // Route RIÊNG cho Facebook - không dùng chung với LINK_ACCOUNT/ADD_ACCOUNT ở trên.
            composable(Routes.LINK_ACCOUNT_FACEBOOK) {
                com.cayxu.app.ui.screens.linkaccount.facebook.FacebookLinkAccountScreen(
                    navController = navController
                )
            }
            composable(Routes.ADD_ACCOUNT_FACEBOOK) {
                com.cayxu.app.ui.screens.linkaccount.facebook.FacebookAddAccountScreen(
                    navController = navController
                )
            }

            // Route RIÊNG cho TikTok - không dùng chung với LINK_ACCOUNT/ADD_ACCOUNT ở trên.
            composable(Routes.LINK_ACCOUNT_TIKTOK) {
                com.cayxu.app.ui.screens.linkaccount.tiktok.TikTokLinkAccountScreen(
                    navController = navController
                )
            }

            composable(Routes.FB_WEB_LOGIN) {
                com.cayxu.app.ui.screens.utilities.FacebookLoginWebViewScreen(
                    navController = navController
                )
            }

            composable(Routes.FB_NURTURE) {
                com.cayxu.app.ui.screens.utilities.FacebookNurtureScreen(
                    navController = navController
                )
            }

            composable(Routes.FB_NUOI_CONFIG) {
                com.cayxu.app.ui.screens.utilities.FbNuoiConfigScreen(
                    navController = navController
                )
            }
        }
    }

    // Hiển thị hộp thoại cập nhật bắt buộc nếu phát hiện bản cũ đã bị vô hiệu hóa
    updateRequired?.let { update ->
        ForceUpdateDialog(update = update)
    }
}
