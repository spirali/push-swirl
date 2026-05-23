package org.kreatrix.pushswirl

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

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
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var soundMode = SoundMode.SYSTEM
    private var vibrationMode = VibrationMode.SYSTEM
    private var volumeLevel = 0.5f
    private var vibrationAmplitude = 1.0f
    private var switchBeepsEnabled = true
    private var phaseFanfareEnabled = true

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
                soundMode = intent?.getStringExtra("soundMode")?.let { SoundMode.valueOf(it) } ?: SoundMode.SYSTEM
                vibrationMode = intent?.getStringExtra("vibrationMode")?.let { VibrationMode.valueOf(it) } ?: VibrationMode.SYSTEM
                volumeLevel = intent?.getFloatExtra("volumeLevel", 0.5f) ?: 0.5f
                vibrationAmplitude = intent?.getFloatExtra("vibrationAmplitude", 1.0f) ?: 1.0f
                isRunning = true
                if (intent?.getBooleanExtra("wakeLockEnabled", true) == true) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PushSwirl::TimerWakeLock")
                    wakeLock?.acquire()
                }
                startForeground(1, createNotification())
            }
        }
        return START_STICKY
    }

    fun updateNotificationSettings(settings: NotificationSettings) {
        soundMode = settings.soundMode
        vibrationMode = settings.vibrationMode
        volumeLevel = settings.volumeLevel
        vibrationAmplitude = settings.vibrationAmplitude
        switchBeepsEnabled = settings.switchBeepsEnabled
        phaseFanfareEnabled = settings.phaseFanfareEnabled
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
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, TimerService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
            .setContentIntent(openPendingIntent)
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
        if (soundMode == SoundMode.OFF) {
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
        val restore = applyCustomVolume()
        when (type) {
            NotificationEvent.PUSH_BEGIN -> {
                if (switchBeepsEnabled) playSound(R.raw.beep_long, restore) else restore?.invoke()
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 400))
            }
            NotificationEvent.SWIRL_BEGIN -> {
                if (switchBeepsEnabled) playSound(R.raw.beep_short) { playSound(R.raw.beep_short, restore) } else restore?.invoke()
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 200, 200, 200))
            }
            NotificationEvent.PHASE_END -> {
                if (phaseFanfareEnabled) playSound(R.raw.finish, restore) else restore?.invoke()
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            }
        }
    }

    // Sets the stream volume to the user-configured level and returns a lambda that restores
    // the original volume. Returns null when using system volume (nothing to restore).
    private fun applyCustomVolume(): (() -> Unit)? {
        if (soundMode != SoundMode.MANUAL) return null
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val originalVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (volumeLevel * maxVol).roundToInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        return { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVol, 0) }
    }

    private fun vibrate(pattern: LongArray) {
        if (vibrationMode == VibrationMode.OFF) return
        val vib = vibrator ?: return
        val effect = if (vibrationMode == VibrationMode.MANUAL) {
            val amp = (vibrationAmplitude * 255).roundToInt().coerceIn(1, 255)
            VibrationEffect.createWaveform(pattern, IntArray(pattern.size) { i -> if (i % 2 == 1) amp else 0 }, -1)
        } else {
            VibrationEffect.createWaveform(pattern, -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vib.vibrate(effect, attrs)
        } else {
            vib.vibrate(effect)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        currentPhase = null
        currentAction = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}