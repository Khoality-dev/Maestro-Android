package com.maestro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.maestro.android.ui.screen.MainScreen
import com.maestro.android.ui.theme.MaestroTheme
import com.maestro.android.ui.viewmodel.PlayerViewModel
import com.maestro.android.update.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModel.Factory(applicationContext)
    }
    private val updateViewModel: UpdateViewModel by viewModels {
        UpdateViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateViewModel.checkOnLaunch()
        setContent {
            MaestroTheme {
                MainScreen(viewModel = viewModel, updateViewModel = updateViewModel)
            }
        }
    }
}
