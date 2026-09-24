package com.bonniedicocco.bonnienotes.recording

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bonniedicocco.bonnienotes.MainActivity
import com.bonniedicocco.bonnienotes.data.AppDatabase
import com.bonniedicocco.bonnienotes.data.MeetingEntity
import com.bonniedicocco.bonnienotes.work.ProcessRecordingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

data class RecordingStatus(val active: Boolean = false, val startedElapsedMs: Long = 0, val title: String = "")

object RecordingState {
    private val mutable = MutableStateFlow(RecordingStatus())
    val status = mutable.asStateFlow()
    internal fun update(value: RecordingStatus) { mutable.value = value }
}

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var title: String = ""
    private var startedAt = ""
    private var startedElapsed = 0L

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Active recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getStringExtra(EXTRA_TITLE).orEmpty())
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(requestedTitle: String) {
        if (recorder != null) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        title = requestedTitle.ifBlank { "Recording ${Instant.now().toString().take(16).replace('T', ' ')}" }
        startedAt = Instant.now().toString()
        startedElapsed = SystemClock.elapsedRealtime()
        val directory = File(filesDir, "recordings").apply { mkdirs() }
        output = File(directory, "${UUID.randomUUID()}.m4a")
        recorder = createRecorder()
        recorder!!.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(output!!.absolutePath)
            prepare()
            start()
        }
        RecordingState.update(RecordingStatus(true, startedElapsed, title))
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("Bonnie Notes is recording")
        .setContentText(title)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        .addAction(
            android.R.drawable.ic_media_pause,
            "Stop",
            PendingIntent.getService(
                this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).build()

    private fun stopRecording() {
        val file = output ?: return
        val duration = SystemClock.elapsedRealtime() - startedElapsed
        val stoppedCleanly = try {
            recorder?.stop()
            true
        } catch (_: RuntimeException) {
            false
        } finally {
            recorder?.release()
            recorder = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            RecordingState.update(RecordingStatus())
        }
        if (!stoppedCleanly || file.length() == 0L) {
            file.delete()
            stopSelf()
            return
        }
        val id = file.nameWithoutExtension
        scope.launch {
            AppDatabase.get(applicationContext).meetingDao().insert(MeetingEntity(
                id = id,
                title = title,
                recordedAt = startedAt,
                durationMs = duration,
                audioPath = file.absolutePath
            ))
            val request = OneTimeWorkRequestBuilder<ProcessRecordingWorker>()
                .setInputData(Data.Builder().putString(ProcessRecordingWorker.KEY_MEETING_ID, id).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(request)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_START = "com.bonniedicocco.bonnienotes.START"
        private const val ACTION_STOP = "com.bonniedicocco.bonnienotes.STOP"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String) {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_TITLE, title))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
