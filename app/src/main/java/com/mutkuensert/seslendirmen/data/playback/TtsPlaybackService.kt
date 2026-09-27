package com.mutkuensert.seslendirmen.data.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mutkuensert.seslendirmen.MainActivity
import com.mutkuensert.seslendirmen.R
import com.mutkuensert.seslendirmen.domain.playback.PlaybackState
import com.mutkuensert.seslendirmen.domain.playback.TtsPlaybackController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TtsPlaybackService : Service() {
    @Inject lateinit var playbackController: TtsPlaybackController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager
    private var fileName: String = ""
    private var hasActivePlayback = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        fileName = getString(R.string.app_name)

        serviceScope.launch {
            playbackController.state.collectLatest(::onPlaybackStateChanged)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_FILE_NAME)
            ?.takeIf(String::isNotBlank)
            ?.let { fileName = it }

        // startForegroundService() must be promoted before potentially expensive TTS work begins.
        startInForeground(playbackController.state.value)

        when (intent?.action) {
            ACTION_PLAY -> playbackController.play()
            ACTION_PLAY_FROM -> intent.takeIf { it.hasExtra(EXTRA_CHUNK_ID) }
                ?.getLongExtra(EXTRA_CHUNK_ID, 0L)
                ?.let(playbackController::playFrom)
            ACTION_PAUSE -> playbackController.pause()
            ACTION_PREVIOUS -> playbackController.previous()
            ACTION_NEXT -> playbackController.next()
            ACTION_STOP -> {
                playbackController.stop()
                stopPlaybackService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onPlaybackStateChanged(state: PlaybackState) {
        when (state) {
            is PlaybackState.Preparing, is PlaybackState.Playing -> {
                hasActivePlayback = true
                acquireWakeLock()
                notificationManager.notify(NOTIFICATION_ID, createNotification(state))
            }
            is PlaybackState.Paused -> {
                hasActivePlayback = true
                releaseWakeLock()
                notificationManager.notify(NOTIFICATION_ID, createNotification(state))
            }
            is PlaybackState.Error -> {
                releaseWakeLock()
                notificationManager.notify(NOTIFICATION_ID, createNotification(state))
            }
            PlaybackState.Idle -> {
                releaseWakeLock()
                if (hasActivePlayback) stopPlaybackService()
            }
        }
    }

    private fun startInForeground(state: PlaybackState) {
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(state),
            foregroundServiceType,
        )
    }

    private fun stopPlaybackService() {
        hasActivePlayback = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(state: PlaybackState): Notification {
        val isPlaying = state is PlaybackState.Playing || state is PlaybackState.Preparing
        val playPauseAction = if (isPlaying) {
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_pause),
                getString(R.string.notification_pause),
                servicePendingIntent(ACTION_PAUSE, REQUEST_PAUSE),
            ).build()
        } else {
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_play),
                getString(R.string.notification_play),
                servicePendingIntent(ACTION_PLAY, REQUEST_PLAY),
            ).build()
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(fileName)
            .setContentText(state.notificationText())
            .setContentIntent(contentPendingIntent())
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_previous),
                    getString(R.string.notification_previous),
                    servicePendingIntent(ACTION_PREVIOUS, REQUEST_PREVIOUS),
                ).build(),
            )
            .addAction(playPauseAction)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_next),
                    getString(R.string.notification_next),
                    servicePendingIntent(ACTION_NEXT, REQUEST_NEXT),
                ).build(),
            )
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun PlaybackState.notificationText(): String = when (this) {
        is PlaybackState.Preparing -> getString(
            R.string.notification_preparing,
            chunk.sectionIndex,
        )
        is PlaybackState.Playing -> getString(
            R.string.notification_playing,
            chunk.sectionIndex,
        )
        is PlaybackState.Paused -> getString(
            R.string.notification_paused,
            chunk.sectionIndex,
        )
        is PlaybackState.Error -> error.message
        PlaybackState.Idle -> getString(R.string.notification_ready)
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TtsPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_CONTENT,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ttsPlayback")
            .apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
        wakeLock = null
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "tts_playback"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_PLAY = "com.mutkuensert.seslendirmen.action.PLAY"
        private const val ACTION_PLAY_FROM = "com.mutkuensert.seslendirmen.action.PLAY_FROM"
        private const val ACTION_PAUSE = "com.mutkuensert.seslendirmen.action.PAUSE"
        private const val ACTION_PREVIOUS = "com.mutkuensert.seslendirmen.action.PREVIOUS"
        private const val ACTION_NEXT = "com.mutkuensert.seslendirmen.action.NEXT"
        private const val ACTION_STOP = "com.mutkuensert.seslendirmen.action.STOP"
        private const val EXTRA_FILE_NAME = "file_name"
        private const val EXTRA_CHUNK_ID = "chunk_id"

        private const val REQUEST_CONTENT = 0
        private const val REQUEST_PREVIOUS = 1
        private const val REQUEST_PLAY = 2
        private const val REQUEST_PAUSE = 3
        private const val REQUEST_NEXT = 4

        fun play(context: Context, fileName: String?) = sendCommand(context, ACTION_PLAY, fileName)

        fun playFrom(context: Context, fileName: String?, chunkId: Long) =
            sendCommand(context, ACTION_PLAY_FROM, fileName) {
                putExtra(EXTRA_CHUNK_ID, chunkId)
            }

        fun pause(context: Context) = sendCommand(context, ACTION_PAUSE)

        fun previous(context: Context, fileName: String?) =
            sendCommand(context, ACTION_PREVIOUS, fileName)

        fun next(context: Context, fileName: String?) =
            sendCommand(context, ACTION_NEXT, fileName)

        fun stop(context: Context) = sendCommand(context, ACTION_STOP)

        private fun sendCommand(
            context: Context,
            action: String,
            fileName: String? = null,
            extras: Intent.() -> Unit = {},
        ) {
            val intent = Intent(context, TtsPlaybackService::class.java)
                .setAction(action)
                .apply {
                    fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
                    extras()
                }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
