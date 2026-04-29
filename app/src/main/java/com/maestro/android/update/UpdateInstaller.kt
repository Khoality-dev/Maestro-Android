package com.maestro.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface InstallProgress {
    data object Idle : InstallProgress
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : InstallProgress
    data object Installing : InstallProgress
    data class Failed(val message: String) : InstallProgress
}

class UpdateInstaller(private val appContext: Context) {

    private val _progress = MutableStateFlow<InstallProgress>(InstallProgress.Idle)
    val progress: StateFlow<InstallProgress> = _progress.asStateFlow()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAndInstall(update: AvailableUpdate) = withContext(Dispatchers.IO) {
        try {
            _progress.value = InstallProgress.Downloading(0L, update.sizeBytes)
            val request = Request.Builder().url(update.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _progress.value = InstallProgress.Failed("Download failed: HTTP ${response.code}")
                    return@withContext
                }
                val body = response.body ?: run {
                    _progress.value = InstallProgress.Failed("Empty response body")
                    return@withContext
                }
                val total = if (update.sizeBytes > 0) update.sizeBytes else body.contentLength()
                streamToInstaller(body.byteStream(), total, update.versionName)
            }
        } catch (e: IOException) {
            _progress.value = InstallProgress.Failed(e.message ?: "Network error")
        } catch (e: Exception) {
            _progress.value = InstallProgress.Failed(e.message ?: "Install failed")
        }
    }

    private fun streamToInstaller(input: java.io.InputStream, total: Long, versionName: String) {
        val packageInstaller = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(appContext.packageName)
        if (total > 0) params.setSize(total)

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        try {
            session.openWrite("maestro-$versionName.apk", 0, total).use { out ->
                val buffer = ByteArray(64 * 1024)
                var totalRead = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    totalRead += read
                    _progress.value = InstallProgress.Downloading(totalRead, total)
                }
                session.fsync(out)
            }

            _progress.value = InstallProgress.Installing
            val intent = Intent(appContext, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        } finally {
            session.close()
        }
    }

    fun reset() {
        _progress.value = InstallProgress.Idle
    }
}
