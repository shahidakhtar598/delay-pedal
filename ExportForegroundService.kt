package com.naatdelay.dd7.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.naatdelay.dd7.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ExportServiceState {
    data object Idle : ExportServiceState
    data class Running(val progressPercent: Int) : ExportServiceState
    data class Completed(val outputUri: Uri, val fileName: String, val fileBytes: Long, val tailMs: Int) : ExportServiceState
    data class Failed(val errorMessage: String) : ExportServiceState
}

@AndroidEntryPoint
class ExportForegroundService : Service() {

    @Inject lateinit var nativeBridge: NativeAudioBridge

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "dd7_export_channel"
        const val NOTIFICATION_ID = 7001
        const val ACTION_START_EXPORT = "com.naatdelay.dd7.ACTION_START_EXPORT"
        const val ACTION_CANCEL_EXPORT = "com.naatdelay.dd7.ACTION_CANCEL_EXPORT"

        const val EXTRA_INPUT_PCM_PATH = "input_pcm_path"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_SAMPLE_RATE = "sample_rate"
        const val EXTRA_FORMAT_CODE = "format_code"
        const val EXTRA_E_LEVEL = "e_level"
        const val EXTRA_F_BACK = "f_back"
        const val EXTRA_D_TIME_MS = "d_time_ms"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PING_PONG = "ping_pong"
        const val EXTRA_PING_PONG_WIDTH = "ping_pong_width"
        const val EXTRA_ANTI_BOOM = "anti_boom"
        const val EXTRA_BYPASSED = "bypassed"
        const val EXTRA_MAX_DURATION_SEC = "max_duration_sec"

        private val _state = MutableStateFlow<ExportServiceState>(ExportServiceState.Idle)
        val state: StateFlow<ExportServiceState> = _state.asStateFlow()

        fun resetState() {
            _state.value = ExportServiceState.Idle
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_EXPORT -> {
                nativeBridge.cancelOfflineExport()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_EXPORT -> {
                startForeground(NOTIFICATION_ID, buildNotification(0))
                runExportJob(intent)
            }
        }
        return START_NOT_STICKY
    }

    private fun runExportJob(intent: Intent) {
        val pcmPath = intent.getStringExtra(EXTRA_INPUT_PCM_PATH) ?: run {
            _state.value = ExportServiceState.Failed("No audio track loaded.")
            stopSelf()
            return
        }
        val rawName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Naat_DD7X_Export"
        val formatCode = intent.getIntExtra(EXTRA_FORMAT_CODE, NativeAudioBridge.FORMAT_MP3_320)
        val ext = if (formatCode == NativeAudioBridge.FORMAT_WAV_16) "wav" else "mp3"
        val mime = if (formatCode == NativeAudioBridge.FORMAT_WAV_16) "audio/wav" else "audio/mpeg"
        val finalFileName = "${rawName.removeSuffix(".$ext")}.$ext"

        serviceScope.launch {
            // Security & Edge Case Check: verify >= 50 MB free storage before rendering
            if (!hasMinimumFreeSpace(50L * 1024L * 1024L)) {
                _state.value = ExportServiceState.Failed(
                    "Your phone is almost out of storage space. Please free up at least 50 MB and try exporting again."
                )
                stopSelf()
                return@launch
            }

            val tempFile = File(cacheDir, "export_tmp_${System.currentTimeMillis()}.$ext")
            _state.value = ExportServiceState.Running(0)

            val result = nativeBridge.renderOfflineExport(
                inputPcmPath = pcmPath,
                outputFilePath = tempFile.absolutePath,
                sampleRate = intent.getIntExtra(EXTRA_SAMPLE_RATE, 48000),
                formatCode = formatCode,
                effectLevel = intent.getFloatExtra(EXTRA_E_LEVEL, 0.45f),
                feedback = intent.getFloatExtra(EXTRA_F_BACK, 0.35f),
                delayTimeMs = intent.getFloatExtra(EXTRA_D_TIME_MS, 420f),
                modeOrdinal = intent.getIntExtra(EXTRA_MODE, 4),
                pingPong = intent.getBooleanExtra(EXTRA_PING_PONG, false),
                pingPongWidth = intent.getFloatExtra(EXTRA_PING_PONG_WIDTH, 0.85f),
                antiBoomCut = intent.getBooleanExtra(EXTRA_ANTI_BOOM, true),
                bypassed = intent.getBooleanExtra(EXTRA_BYPASSED, false),
                maxDurationSec = intent.getIntExtra(EXTRA_MAX_DURATION_SEC, 30),
                progressCallback = ExportProgressCallback { pct ->
                    _state.value = ExportServiceState.Running(pct)
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(pct))
                }
            )

            if (!result.success) {
                tempFile.delete()
                val friendlyMsg = when (result.errorCode) {
                    "EXPORT_CANCELLED" -> "Export paused or cancelled. No partial file was saved—tap Export to try again."
                    "MP3_ENCODER_LIBRARY_MISSING" -> "MP3 encoder library is missing in this build. Please export as WAV 16-bit."
                    else -> "Export could not finish cleanly. Please try again."
                }
                _state.value = ExportServiceState.Failed(friendlyMsg)
                stopSelf()
                return@launch
            }

            val savedUri = saveToMediaStore(tempFile, finalFileName, mime)
            tempFile.delete()

            if (savedUri == null) {
                _state.value = ExportServiceState.Failed("Could not save exported audio to Music/DD7_Vocal_Exports/.")
            } else {
                _state.value = ExportServiceState.Completed(
                    outputUri = savedUri,
                    fileName = finalFileName,
                    fileBytes = result.fileBytes,
                    tailMs = result.tailMs
                )
            }
            stopSelf()
        }
    }

    private fun saveToMediaStore(sourceFile: File, fileName: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/DD7_Vocal_Exports")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
            }
            uri
        } catch (_: Throwable) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    private fun hasMinimumFreeSpace(minBytes: Long): Boolean {
        return try {
            val stat = StatFs(cacheDir.absolutePath)
            stat.availableBytes >= minBytes
        } catch (_: Throwable) {
            true
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.export_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(progress: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Exporting Naat Audio (DD-7X)")
            .setContentText("Rendering studio delay & echo tail: $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
