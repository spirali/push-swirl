package org.kreatrix.pushswirl

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.*
import androidx.core.app.NotificationCompat

enum class NotificationEvent {
    PUSH_BEGIN,
    SWIRL_BEGIN,
    PHASE_END
}

class TimerService : Service() {
    companion object {
        const val CHANNEL_ID = "pushswirl_timer"
        const val ACTION_PAUSE = "org.kreatrix.pushswirl.PAUSE"
        const val ACTION_RESUME = "org.kreatrix.pushswirl.RESUME"

        var isRunning = false
        var currentPhase: PhaseSize? = null
        var currentAction: DilationAction? = null
        var remainingSeconds = 0
        var actionRemainingSeconds = 0
        var isPaused = false
    }

    private val binder = TimerBinder()
    private var vibrator: Vibrator? = null
    private var vibrationEnabled = true
    private var soundEnabled = true

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                isPaused = true
                updateNotification()
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification()
            }
            else -> {
                vibrationEnabled = intent?.getBooleanExtra("vibrationEnabled", true) ?: true
                soundEnabled = intent?.getBooleanExtra("soundEnabled", true) ?: true
                isRunning = true
                startForeground(1, createNotification())
            }
        }
        return START_STICKY
    }

    fun updateNotificationSettings(settings: NotificationSettings) {
        vibrationEnabled = settings.vibrationEnabled
        soundEnabled = settings.soundEnabled
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PushSwirl Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active timer status"
            setSound(null, null)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pauseIntent = Intent(this, TimerService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 0, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = when {
            !isRunning -> "Timer stopped"
            isPaused -> "Timer paused"
            currentPhase != null && currentAction != null ->
                "${currentPhase?.name} - ${currentAction?.name}: ${formatTime(actionRemainingSeconds)}"
            else -> "Timer running"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PushSwirl")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pausePendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification())
    }

    fun updateTimerState(phase: PhaseSize?, action: DilationAction?, remaining: Int, actionRemaining: Int) {
        currentPhase = phase
        currentAction = action
        remainingSeconds = remaining
        actionRemainingSeconds = actionRemaining
        updateNotification()
    }

    private fun playSound(resId: Int, onComplete: (() -> Unit)? = null) {
        if (!soundEnabled) {
            onComplete?.invoke()
            return
        }
        try {
            val mp = MediaPlayer.create(this, resId) ?: return
            mp.setOnCompletionListener {
                it.release()
                onComplete?.invoke()
            }
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete?.invoke()
        }
    }

    fun makeNotification(type: NotificationEvent) {
        when (type) {
            NotificationEvent.PUSH_BEGIN -> {
                playSound(R.raw.beep_long)
                if (vibrationEnabled) vibrate(longArrayOf(0, 400))
            }
            NotificationEvent.SWIRL_BEGIN -> {
                playSound(R.raw.beep_short) { playSound(R.raw.beep_short) }
                if (vibrationEnabled) vibrate(longArrayOf(0, 200, 200, 200))
            }
            NotificationEvent.PHASE_END -> {
                playSound(R.raw.beep_long) {
                    playSound(R.raw.beep_long) {
                        playSound(R.raw.beep_long)
                    }
                }
                if (vibrationEnabled) vibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            }
        }
    }

    private fun vibrate(pattern: LongArray) {
        if (!vibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        currentPhase = null
        currentAction = null
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}