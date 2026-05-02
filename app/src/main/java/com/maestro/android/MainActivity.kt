package com.maestro.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        updateViewModel.checkOnLaunch()
        setContent {
            MaestroTheme {
                MainScreen(viewModel = viewModel, updateViewModel = updateViewModel)
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
