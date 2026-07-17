package com.cayxu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cayxu.app.ui.navigation.CayXuNavGraph
import com.cayxu.app.ui.theme.CayXuTheme

/**
 * Activity duy nhất của app (Single-Activity + Navigation Compose).
 * KHÔNG vẽ giả status bar - dùng StatusBar thật của hệ thống Android.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CayXuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CayXuNavGraph()
                    }
                }
            }
        }
    }
}
