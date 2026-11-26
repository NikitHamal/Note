package com.neb.ians.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import com.neb.ians.MainActivity
import com.neb.ians.R

/**
 * Handles notification actions for both background audio playback (Play/Pause, Stop/Cancel, Seekbar)
 * and background on-device voice transcription (Cancel).
 */
class NebMediaControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> NebPlaybackNotificationManager.onTogglePlayPause()
            ACTION_STOP -> NebPlaybackNotificationManager.onStopPlayback(context)
            ACTION_CANCEL_TRANSCRIBE -> {
                val blockId = intent.getStringExtra(EXTRA_BLOCK_ID)
                NebTranscriptionNotificationManager.onCancelRequested(context, blockId)
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.neb.ians.action.MEDIA_PLAY_PAUSE"
        const val ACTION_STOP = "com.neb.ians.action.MEDIA_STOP"
        const val ACTION_CANCEL_TRANSCRIBE = "com.neb.ians.action.CANCEL_TRANSCRIBE"
        const val EXTRA_BLOCK_ID = "block_id"
    }
}

/**
 * Unified app-wide MediaSession + MediaStyle notification controller.
 * Whenever any audio plays in the app (Note audio recordings, Note Read-Aloud TTS,
 * Resource audio/video player, or Forum audio attachments), this publishes a system
 * media notification with an interactive Seekbar, Play/Pause toggle, and Cancel/Stop button,
 * and ensures only one audio source plays at a time.
 */
object NebPlaybackNotificationManager {
    private const val CHANNEL_ID = "neb_media_playback"
    private const val NOTIFICATION_ID = 4101

    interface Controller {
        val id: String
        fun onPlayPause()
        fun onSeekTo(posMs: Long)
        fun onStop()
    }

    @Volatile private var activeController: Controller? = null
    @Volatile private var mediaSession: MediaSession? = null
    @Volatile private var lastTitle: String = "Audio playback"
    @Volatile private var lastSubtitle: String = "NEBians"
    @Volatile private var lastDurationMs: Long = 0L

    fun registerAndUpdate(
        context: Context,
        controller: Controller,
        title: String,
        subtitle: String = "NEBians",
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        speed: Float = 1f,
    ) {
        val appCtx = context.applicationContext
        val previous = activeController
        if (previous != null && previous.id != controller.id && isPlaying) {
            runCatching { previous.onStop() }
        }
        activeController = controller
        lastTitle = title.ifBlank { "Audio playback" }
        lastSubtitle = subtitle.ifBlank { "NEBians" }
        lastDurationMs = durationMs.coerceAtLeast(0L)
        publishNotification(appCtx, isPlaying, positionMs.coerceAtLeast(0L), lastDurationMs, speed)
    }

    fun clearIfOwned(context: Context, controllerId: String) {
        if (activeController?.id == controllerId) {
            activeController = null
            dismiss(context.applicationContext)
        }
    }

    internal fun onTogglePlayPause() {
        runCatching { activeController?.onPlayPause() }
    }

    internal fun onStopPlayback(context: Context) {
        val ctrl = activeController
        activeController = null
        runCatching { ctrl?.onStop() }
        dismiss(context.applicationContext)
    }

    private fun ensureSession(context: Context): MediaSession {
        mediaSession?.let { return it }
        ensureChannel(context)
        return MediaSession(context, "NEBiansMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    runCatching { activeController?.onPlayPause() }
                }

                override fun onPause() {
                    runCatching { activeController?.onPlayPause() }
                }

                override fun onStop() {
                    onStopPlayback(context)
                }

                override fun onSeekTo(pos: Long) {
                    runCatching { activeController?.onSeekTo(pos.coerceAtLeast(0L)) }
                }
            })
            isActive = true
        }.also { mediaSession = it }
    }

    private fun publishNotification(
        context: Context,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
    ) {
        try {
            val session = ensureSession(context)
            val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                (if (durationMs > 0L) PlaybackState.ACTION_SEEK_TO else 0L)

            session.setPlaybackState(
                PlaybackState.Builder()
                    .setState(stateCode, positionMs, if (isPlaying) speed else 0f)
                    .setActions(actions)
                    .build()
            )
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, lastTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, lastSubtitle)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, if (durationMs > 0L) durationMs else -1L)
                    .build()
            )

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPi = PendingIntent.getActivity(
                context, 4100, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val togglePi = PendingIntent.getBroadcast(
                context, 4101,
                Intent(context, NebMediaControlReceiver::class.java).setAction(NebMediaControlReceiver.ACTION_PLAY_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopPi = PendingIntent.getBroadcast(
                context, 4102,
                Intent(context, NebMediaControlReceiver::class.java).setAction(NebMediaControlReceiver.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseLabel = if (isPlaying) "Pause" else "Play"

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            val notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(lastTitle)
                .setContentText(lastSubtitle)
                .setContentIntent(contentPi)
                .setDeleteIntent(stopPi)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(Notification.Action.Builder(playPauseIcon, playPauseLabel, togglePi).build())
                .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", stopPi).build())
                .setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(session.sessionToken)
                        .setShowActionsInCompactView(0, 1)
                )
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }

    fun dismiss(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (_: Throwable) {
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Media playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls and seekbar for audio playing in NEBians"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}

/**
 * Notification helper for background on-device audio transcription.
 * Shows live progress (%) and a Cancel button so users can switch apps while transcribing.
 */
object NebTranscriptionNotificationManager {
    private const val CHANNEL_ID = "neb_voice_transcription"
    private const val NOTIFICATION_ID = 4102

    @Volatile var cancelHandler: ((String?) -> Unit)? = null

    fun showProgress(context: Context, blockId: String, modelLabel: String, progressPercent: Int) {
        try {
            val appCtx = context.applicationContext
            ensureChannel(appCtx)
            val openIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPi = PendingIntent.getActivity(
                appCtx, 4200, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cancelIntent = Intent(appCtx, NebMediaControlReceiver::class.java).apply {
                action = NebMediaControlReceiver.ACTION_CANCEL_TRANSCRIBE
                putExtra(NebMediaControlReceiver.EXTRA_BLOCK_ID, blockId)
            }
            val cancelPi = PendingIntent.getBroadcast(
                appCtx, 4201, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(appCtx, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(appCtx)
            }

            val pct = progressPercent.coerceIn(0, 100)
            val notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Transcribing audio ($modelLabel)")
                .setContentText(if (pct > 0) "$pct% completed on device" else "Processing audio on device...")
                .setContentIntent(contentPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct, pct <= 0)
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Cancel",
                        cancelPi
                    ).build()
                )
                .build()

            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }

    fun dismiss(context: Context) {
        try {
            val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Throwable) {
        }
    }

    internal fun onCancelRequested(context: Context, blockId: String?) {
        runCatching { cancelHandler?.invoke(blockId) }
        dismiss(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Voice transcription",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background progress for on-device audio transcription"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
