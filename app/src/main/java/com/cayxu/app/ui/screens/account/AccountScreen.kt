package com.cayxu.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.cayxu.app.ui.components.CayXuBottomBar
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.TextSecondary

@Composable
fun AccountScreen(navController: NavController) {
    Scaffold(containerColor = AppBackground, bottomBar = { CayXuBottomBar(navController) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(AppBackground), contentAlignment = Alignment.Center) {
            Text("Tài khoản (đang phát triển)", color = TextSecondary)
        }
    }
}
