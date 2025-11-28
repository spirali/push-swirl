package com.example.pushswirl

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.ToneGenerator
import android.media.AudioManager
import android.media.AudioTrack
import android.os.*
import androidx.core.app.NotificationCompat

enum class NotificationEvent {
    PUSH_END,
    SWIRL_END,
    PHASE_END
}

class TimerService : Service() {
    companion object {
        const val CHANNEL_ID = "pushswirl_timer"
        const val ACTION_PAUSE = "com.example.pushswirl.PAUSE"
        const val ACTION_RESUME = "com.example.pushswirl.RESUME"

        // Tone configuration
        const val TONE_FREQUENCY = 800.0 // Hz - adjust this value to change frequency
        const val SAMPLE_RATE = 44100

        var isRunning = false
        var currentPhase: PhaseSize? = null
        var currentPart: DilationPart? = null
        var remainingSeconds = 0
        var partRemainingSeconds = 0
        var isPaused = false
    }

    private val binder = TimerBinder()
    private var vibrator: Vibrator? = null
    private var audioTrack: AudioTrack? = null
    private var toneGenerator: ToneGenerator? = null
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
        initAudioTrack()
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
            currentPhase != null && currentPart != null ->
                "${currentPhase?.name} - ${currentPart?.name}: ${formatTime(partRemainingSeconds)}"
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

    fun updateTimerState(phase: PhaseSize?, part: DilationPart?, remaining: Int, partRemaining: Int) {
        currentPhase = phase
        currentPart = part
        remainingSeconds = remaining
        partRemainingSeconds = partRemaining
        updateNotification()
    }

    private fun initAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun playTone(durationMs: Int) {
        if (!soundEnabled) return

        try {
            val numSamples = (durationMs * SAMPLE_RATE) / 1000
            val buffer = ShortArray(numSamples)

            // Generate sine wave at specified frequency
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i * TONE_FREQUENCY / SAMPLE_RATE
                buffer[i] = (kotlin.math.sin(angle) * Short.MAX_VALUE).toInt().toShort()
            }

            audioTrack?.let { track ->
                // Set volume to maximum
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.play()
                    track.write(buffer, 0, buffer.size)
                    track.stop()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun makeNotification(type: NotificationEvent) {
        when (type) {
            NotificationEvent.PUSH_END -> {
                // Two short signals (200ms each with 200ms gap)
                if (soundEnabled) {
                    playTone(200)
                    Handler(Looper.getMainLooper()).postDelayed({
                        playTone(200)
                    }, 400) // 200ms signal + 200ms gap
                }
                if (vibrationEnabled) {
                    val pattern = longArrayOf(0, 200, 200, 200)
                    vibrate(pattern)
                }
            }
            NotificationEvent.SWIRL_END -> {
                // One long signal (400ms)
                if (soundEnabled) {
                    playTone(400)
                }
                if (vibrationEnabled) {
                    val pattern = longArrayOf(0, 400)
                    vibrate(pattern)
                }
            }
            NotificationEvent.PHASE_END -> {
                // Three long signals (400ms each with 200ms gaps)
                if (soundEnabled) {
                    playTone(400)
                    Handler(Looper.getMainLooper()).postDelayed({
                        playTone(400)
                    }, 600) // 400ms signal + 200ms gap
                    Handler(Looper.getMainLooper()).postDelayed({
                        playTone(400)
                    }, 1200) // 400ms + 200ms + 400ms + 200ms
                }
                if (vibrationEnabled) {
                    val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
                    vibrate(pattern)
                }
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
        currentPart = null
        toneGenerator?.release()
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}