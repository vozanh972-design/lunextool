package com.cayxu.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cayxu.app.ui.components.CayXuBottomBar
import com.cayxu.app.ui.screens.account.AccountScreen
import com.cayxu.app.ui.screens.friends.FriendsScreen
import com.cayxu.app.ui.screens.home.HomeScreen
import com.cayxu.app.ui.screens.login.LoginScreen
import com.cayxu.app.ui.screens.settings.SettingsScreen
import com.cayxu.app.ui.screens.tasks.TasksScreen
import com.cayxu.app.ui.screens.wallet.WalletScreen
import com.cayxu.app.ui.screens.welcome.WelcomeScreen
import com.cayxu.app.ui.theme.AppBackground

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val WALLET = "wallet"
    const val FRIENDS = "friends"
    const val ACCOUNT = "account"
    const val SETTINGS = "settings"
    const val UTILITIES = "utilities"
    const val NURTURE_SETUP = "nurture_setup"
    const val GOLIKE = "golike"
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
private val routesWithBottomBar = setOf(Routes.HOME, Routes.TASKS, Routes.WALLET, Routes.ACCOUNT, Routes.FRIENDS)

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
fun CayXuNavGraph(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
        NavHost(
            navController = navController,
            startDestination = if (com.cayxu.app.data.local.SecurePrefs(androidx.compose.ui.platform.LocalContext.current).hasSeenWelcome()) {
                Routes.LOGIN
            } else {
                Routes.WELCOME
            },
            modifier = Modifier.padding(innerPadding),
            // Chỉ mờ dần nội dung cũ -> hiện nội dung mới, KHÔNG trượt ngang trái/phải.
            enterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) }
        ) {

            // CHỈ hiện đúng 1 lần ở lần mở app đầu tiên (xem SecurePrefs.hasSeenWelcome).
            // Những lần sau (kể cả khi key hết hạn/đổi máy) vào thẳng Routes.LOGIN, không
            // hiện lại màn này nữa - đúng yêu cầu.
            composable(
                Routes.WELCOME,
                exitTransition = {
                    if (targetState.destination.route == Routes.LOGIN) welcomeToLoginExit()
                    else fadeOut(animationSpec = tween(FADE_DURATION_MS))
                }
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                WelcomeScreen(
                    onGetStarted = {
                        com.cayxu.app.data.local.SecurePrefs(context).setSeenWelcome()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                Routes.LOGIN,
                enterTransition = {
                    if (initialState.destination.route == Routes.WELCOME) welcomeToLoginEnter()
                    else fadeIn(animationSpec = tween(FADE_DURATION_MS))
                }
            ) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.TASKS) { TasksScreen(navController) }
            composable(Routes.WALLET) { WalletScreen(navController) }
            composable(Routes.FRIENDS) { FriendsScreen(navController) }
            composable(Routes.ACCOUNT) { AccountScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.UTILITIES) {
                com.cayxu.app.ui.screens.utilities.UtilitiesScreen(navController)
            }
            composable(Routes.NURTURE_SETUP) {
                com.cayxu.app.ui.screens.nurture.NurtureSetupScreen(navController)
            }
            composable(Routes.GOLIKE) {
                com.cayxu.app.ui.screens.golike.GolikeScreen(navController)
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
        }
    }
}
