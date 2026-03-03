package com.maestro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.maestro.android.ui.screen.MainScreen
import com.maestro.android.ui.screen.SetupScreen
import com.maestro.android.ui.theme.MaestroTheme
import com.maestro.android.ui.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaestroTheme {
                val needsSetup by viewModel.needsSetup.collectAsState()

                if (needsSetup) {
                    SetupScreen(onComplete = viewModel::completeSetup)
                } else {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
