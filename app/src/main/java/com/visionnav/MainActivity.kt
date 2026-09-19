package com.visionnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.visionnav.ui.screens.MainNavScreen
import com.visionnav.ui.state.VisionNavViewModel
import com.visionnav.ui.theme.VisionNavTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisionNavTheme {
                val viewModel: VisionNavViewModel = viewModel()
                MainNavScreen(viewModel = viewModel)
            }
        }
    }
}

