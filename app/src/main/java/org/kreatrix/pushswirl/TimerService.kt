package org.kreatrix.pushswirl

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

enum class NotificationEvent {
    PUSH_BEGIN,
    SWIRL_BEGIN,
    PHASE_END,
    /** Last phase of the session: the session-end sound, optionally preceded by the phase-end sound. */
    SESSION_END,
    PAUSE_BEGIN
}

/** Which timer the session is on; decides what the ongoing notification shows and offers. */
enum class TimerMode { IDLE, TTD, DILATION }

class TimerService : Service() {
    companion object {
        const val CHANNEL_ID = "pushswirl_timer"
        const val ACTION_TOGGLE_PAUSE = "org.kreatrix.pushswirl.TOGGLE_PAUSE"
        const val ACTION_DEPTH_REACHED = "org.kreatrix.pushswirl.DEPTH_REACHED"
        const val ACTION_EARLY_EXIT = "org.kreatrix.pushswirl.EARLY_EXIT"

        var isRunning = false
        var currentPhase: PhaseSize? = null
        var currentAction: DilationAction? = null
        var remainingSeconds = 0
        var actionRemainingSeconds = 0
        var isPaused = false
        var mode = TimerMode.IDLE
        /** Formatted TTD elapsed time, or null while the TTD timer is blinded. */
        var ttdLabel: String? = null
        /** Whether the TTD timer has started, i.e. whether "Depth reached" applies yet. */
        var ttdCanFinish = false
    }

    // Set by the ViewModel while it is bound, so the notification's buttons drive the session
    // itself rather than a copy of its state. Null when nothing is bound.
    var onPauseToggle: (() -> Boolean)? = null
    var onDepthReached: (() -> Unit)? = null
    var onEarlyExit: (() -> Unit)? = null

    private var lastNotificationKey: String? = null
    private val binder = TimerBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var soundMode = SoundMode.SYSTEM
    private var vibrationMode = VibrationMode.SYSTEM
    private var volumeLevel = 0.5f
    private var vibrationAmplitude = 1.0f
    private var switchBeepsEnabled = true
    private var phaseFanfareEnabled = true
    private var pushSoundUri: String? = null
    private var swirlSoundUri: String? = null
    private var sessionEndEnabled = true
    private var lastPhaseEndEnabled = false
    private var phaseEndSoundUri: String? = null
    private var sessionEndSoundUri: String? = null
    private var pauseSoundUri: String? = null

    private val largeIcon: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
    }

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
            // Let the session do the pausing so its clock and this notification agree;
            // with nothing bound there is no clock to stop, so just reflect the new state.
            ACTION_TOGGLE_PAUSE -> {
                if (onPauseToggle?.invoke() != true) {
                    isPaused = !isPaused
                    updateNotification()
                }
            }
            ACTION_DEPTH_REACHED -> onDepthReached?.invoke()
            ACTION_EARLY_EXIT -> onEarlyExit?.invoke()
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
                lastNotificationKey = notificationKey()
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
        pushSoundUri = settings.pushSoundUri
        swirlSoundUri = settings.swirlSoundUri
        sessionEndEnabled = settings.sessionEndEnabled
        lastPhaseEndEnabled = settings.lastPhaseEndEnabled
        phaseEndSoundUri = settings.phaseEndSoundUri
        sessionEndSoundUri = settings.sessionEndSoundUri
        pauseSoundUri = settings.pauseSoundUri
    }

    private fun customUriFor(sound: AppSound): String? = when (sound) {
        AppSound.PUSH -> pushSoundUri
        AppSound.SWIRL -> swirlSoundUri
        AppSound.PHASE_END -> phaseEndSoundUri
        AppSound.SESSION_END -> sessionEndSoundUri
        AppSound.PAUSE -> pauseSoundUri
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PushSwirl")
            .setContentText(notificationText())
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                servicePendingIntent(1, ACTION_TOGGLE_PAUSE)
            )

        // The second action mirrors the button the active-session screen offers right now.
        when (mode) {
            TimerMode.TTD -> if (ttdCanFinish) builder.addAction(
                android.R.drawable.ic_menu_save,
                "Depth reached",
                servicePendingIntent(2, ACTION_DEPTH_REACHED)
            )
            TimerMode.DILATION -> builder.addAction(
                android.R.drawable.ic_media_next,
                "Early exit",
                servicePendingIntent(3, ACTION_EARLY_EXIT)
            )
            TimerMode.IDLE -> {}
        }

        return builder.build()
    }

    private fun servicePendingIntent(requestCode: Int, intentAction: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply { action = intentAction }
        return PendingIntent.getService(
            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notificationText(): String {
        val body = when {
            !isRunning -> "Timer stopped"
            mode == TimerMode.TTD -> ttdLabel?.let { "TTD timer: $it" } ?: "TTD timer"
            currentPhase != null && currentAction != null ->
                "${currentPhase?.name} - ${currentAction?.name}: ${formatTime(actionRemainingSeconds)}"
            else -> "Timer running"
        }
        return if (isRunning && isPaused) "$body (paused)" else body
    }

    /** Everything the notification renders, so identical re-posts can be skipped. */
    private fun notificationKey(): String =
        "$isRunning|$isPaused|$mode|$ttdCanFinish|${notificationText()}"

    /**
     * Re-posts the notification only when something visible changed. The timers push updates
     * five times a second, but the notification shows whole seconds; re-posting it that often
     * makes its action buttons drop taps, so identical updates are skipped.
     */
    private fun updateNotification() {
        val key = notificationKey()
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification())
    }

    /**
     * Reflects the dilation timer. [paused] is passed on every update rather than tracked
     * separately, so a pause left over from the previous phase cannot linger here.
     */
    fun updateTimerState(
        phase: PhaseSize?,
        action: DilationAction?,
        remaining: Int,
        actionRemaining: Int,
        paused: Boolean
    ) {
        mode = TimerMode.DILATION
        currentPhase = phase
        currentAction = action
        remainingSeconds = remaining
        actionRemainingSeconds = actionRemaining
        isPaused = paused
        updateNotification()
    }

    /** Reflects the TTD timer; [elapsedLabel] is null while the TTD timer is blinded. */
    fun updateTtdState(phase: PhaseSize?, elapsedLabel: String?, running: Boolean, canFinish: Boolean) {
        mode = TimerMode.TTD
        currentPhase = phase
        currentAction = null
        ttdLabel = elapsedLabel
        ttdCanFinish = canFinish
        isPaused = !running
        updateNotification()
    }

    /** Drops the phase-specific action while the session is between timers (depth/tag input). */
    fun clearPhaseActions() {
        mode = TimerMode.IDLE
        currentAction = null
        ttdLabel = null
        isPaused = false
        updateNotification()
    }

    /** Reflects a dilation pause/resume driven from the app. */
    fun setPaused(paused: Boolean) {
        isPaused = paused
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

    // Plays a configurable cue: the user's custom sound if set, otherwise the built-in default.
    private fun playSound(sound: AppSound, onComplete: (() -> Unit)? = null) {
        if (soundMode == SoundMode.OFF) {
            onComplete?.invoke()
            return
        }
        try {
            val mp = createAppSoundPlayer(this, sound, customUriFor(sound))
            if (mp == null) {
                onComplete?.invoke()
                return
            }
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
                if (switchBeepsEnabled) playSound(AppSound.PUSH, restore) else restore?.invoke()
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 400))
            }
            NotificationEvent.SWIRL_BEGIN -> {
                if (switchBeepsEnabled) playSound(AppSound.SWIRL, restore) else restore?.invoke()
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 200, 200, 200))
            }
            NotificationEvent.PHASE_END -> {
                if (phaseFanfareEnabled) playSound(AppSound.PHASE_END, restore) else restore?.invoke()
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            }
            NotificationEvent.SESSION_END -> {
                val playSessionEnd: () -> Unit = {
                    if (sessionEndEnabled) playSound(AppSound.SESSION_END, restore) else restore?.invoke()
                    Unit
                }
                if (phaseFanfareEnabled && lastPhaseEndEnabled) playSound(AppSound.PHASE_END, playSessionEnd)
                else playSessionEnd()
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            }
            NotificationEvent.PAUSE_BEGIN -> {
                playSound(AppSound.PAUSE, restore)
                if (vibrationMode != VibrationMode.OFF) vibrate(longArrayOf(0, 150))
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        mode = TimerMode.IDLE
        ttdLabel = null
        ttdCanFinish = false
        isPaused = false
        lastNotificationKey = null
        onPauseToggle = null
        onDepthReached = null
        onEarlyExit = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}