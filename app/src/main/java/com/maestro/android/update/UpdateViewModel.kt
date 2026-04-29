package com.maestro.android.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(private val appContext: Context) : ViewModel() {

    private val checker = UpdateChecker()
    private val installer = UpdateInstaller(appContext)

    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _dismissed = MutableStateFlow<String?>(null)

    val progress: StateFlow<InstallProgress> = installer.progress

    fun checkOnLaunch() {
        viewModelScope.launch {
            val current = appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName ?: return@launch
            _available.value = runCatching { checker.checkForUpdate(current) }.getOrNull()
        }
    }

    fun startUpdate() {
        val update = _available.value ?: return
        viewModelScope.launch { installer.downloadAndInstall(update) }
    }

    fun dismiss() {
        _dismissed.value = _available.value?.versionName
        _available.value = null
        installer.reset()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpdateViewModel(context.applicationContext) as T
        }
    }
}
