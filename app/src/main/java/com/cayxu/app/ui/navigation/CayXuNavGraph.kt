package com.cayxu.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cayxu.app.ui.screens.account.AccountScreen
import com.cayxu.app.ui.screens.friends.FriendsScreen
import com.cayxu.app.ui.screens.home.HomeScreen
import com.cayxu.app.ui.screens.login.LoginScreen
import com.cayxu.app.ui.screens.tasks.TasksScreen
import com.cayxu.app.ui.screens.wallet.WalletScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val WALLET = "wallet"
    const val FRIENDS = "friends"
    const val ACCOUNT = "account"
}

@Composable
fun CayXuNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {

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
    }
}
