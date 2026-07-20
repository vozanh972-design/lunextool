package com.cayxu.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.cayxu.app.ui.theme.AppBackground

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val WALLET = "wallet"
    const val FRIENDS = "friends"
    const val ACCOUNT = "account"
    const val SETTINGS = "settings"
    const val LINK_ACCOUNT = "link_account/{platform}/{iconRes}"

    fun linkAccount(platform: String, iconRes: Int) = "link_account/$platform/$iconRes"
}

// Các route hiện thanh điều hướng dưới (bottom bar cố định, không nằm trong vùng chuyển
// cảnh mờ dần, nên không bị "nẩy" hay animate theo nội dung mỗi lần chuyển màn).
private val routesWithBottomBar = setOf(Routes.HOME, Routes.TASKS, Routes.WALLET, Routes.ACCOUNT, Routes.FRIENDS)

private const val FADE_DURATION_MS = 260

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
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(innerPadding),
            // Chỉ mờ dần nội dung cũ -> hiện nội dung mới, KHÔNG trượt ngang trái/phải.
            enterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) }
        ) {

            composable(Routes.LOGIN) {
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
        }
    }
}
