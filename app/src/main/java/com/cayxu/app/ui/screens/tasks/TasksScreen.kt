package com.cayxu.app.ui.screens.tasks

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
fun TasksScreen(navController: NavController) {
    Scaffold(containerColor = AppBackground, bottomBar = { CayXuBottomBar(navController) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(AppBackground), contentAlignment = Alignment.Center) {
            Text("Danh sách nhiệm vụ (đang phát triển)", color = TextSecondary)
        }
    }
}
