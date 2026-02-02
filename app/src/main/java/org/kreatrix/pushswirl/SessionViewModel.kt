package org.kreatrix.pushswirl

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

sealed class AppScreen {
    object Home : AppScreen()
    object NewSession : AppScreen()
    data class ActiveSession(val config: SessionConfig) : AppScreen()
    object SessionHistory : AppScreen()
    object Statistics : AppScreen()
}

sealed class SessionState {
    object Idle : SessionState()
    data class TTD(val phase: PhaseSize) : SessionState()
    data class Dilation(val phase: PhaseSize, val action: DilationAction) : SessionState()
}

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ACTION_TIME = 15
    }
    private val storage = SessionStorage(application)
    var currentScreen by mutableStateOf<AppScreen>(AppScreen.Home)

    // Session configuration
    var sessionConfig by mutableStateOf(storage.getLastSessionConfig() ?: SessionConfig())

    // Notification settings
    var notificationSettings by mutableStateOf(storage.loadNotificationSettings())

    // Active session state
    var sessionState by mutableStateOf<SessionState>(SessionState.Idle)
    var activePhases by mutableStateOf<List<PhaseSize>>(emptyList())
    var currentPhaseIndex by mutableIntStateOf(0)
    var dilationPaused by mutableStateOf(false)

    // ============================================================================
    // ABSOLUTE TIME TRACKING - survives device sleep
    // Using SystemClock.elapsedRealtime() which continues during sleep
    // ============================================================================

    // TTD (Time To Dilation) - counts UP from 0
    var ttdRunning by mutableStateOf(false)
    private var ttdStartTime = 0L           // elapsedRealtime when TTD started
    private var ttdAccumulatedMs = 0L       // accumulated time if paused

    // Exposed TTD seconds as mutable state (updated by timer loop)
    var ttdSeconds by mutableLongStateOf(0L)
        private set

    // Dilation timer - counts DOWN
    var dilationTotalSeconds by mutableIntStateOf(0)
    private var dilationStartTime = 0L      // elapsedRealtime when dilation started
    private var dilationAccumulatedMs = 0L  // accumulated elapsed time before pause

    // Exposed remaining seconds as mutable state (updated by timer loop)
    var dilationRemainingSeconds by mutableIntStateOf(0)
        private set

    var actionRemainingSeconds by mutableIntStateOf(ACTION_TIME)
        private set

    // Session tracking
    private val completedPhases = mutableStateListOf<PhaseData>()
    private var sessionStartTime = 0L

    // Store TTD value when finishing (before state changes)
    private var lastTtdSeconds = 0L

    // Store early finish seconds remaining (null if not early finished)
    private var earlyFinishSecondsRemaining: Int? = null

    // Timer jobs
    private var ttdJob: Job? = null
    private var dilationJob: Job? = null

    // Track last action index to detect changes for notifications
    private var lastNotifiedActionIndex = -1

    // Service
    private var timerService: TimerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.TimerBinder
            timerService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            timerService = null
        }
    }

    // History and stats
    var sessions by mutableStateOf(storage.loadSessions())
    var stats by mutableStateOf(storage.calculateStats())

    // ============================================================================
    // HELPER FUNCTIONS - calculate time from absolute clock
    // ============================================================================

    private fun calculateTtdSeconds(): Long {
        if (!ttdRunning) return ttdAccumulatedMs / 1000
        val elapsed = ttdAccumulatedMs + (SystemClock.elapsedRealtime() - ttdStartTime)
        return elapsed / 1000
    }

    private fun calculateDilationElapsedMs(): Long {
        return if (dilationPaused) {
            dilationAccumulatedMs
        } else {
            dilationAccumulatedMs + (SystemClock.elapsedRealtime() - dilationStartTime)
        }
    }

    private fun calculateDilationRemainingSeconds(): Int {
        if (dilationTotalSeconds == 0) return 0
        val elapsedMs = calculateDilationElapsedMs()
        val remainingMs = (dilationTotalSeconds * 1000L) - elapsedMs
        return (remainingMs.coerceAtLeast(0) / 1000).toInt()
    }

    private fun calculateActionRemainingSeconds(): Int {
        if (dilationTotalSeconds == 0) return ACTION_TIME
        val elapsedMs = calculateDilationElapsedMs()
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        val intoCurrentAction = elapsedSeconds % ACTION_TIME
        return ACTION_TIME - intoCurrentAction
    }

    private fun calculateCurrentActionIndex(): Int {
        if (dilationTotalSeconds == 0) return 0
        val elapsedMs = calculateDilationElapsedMs()
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        return elapsedSeconds / ACTION_TIME
    }

    // ============================================================================
    // UPDATE FUNCTION - refreshes all time-based state values
    // ============================================================================

    private fun updateTimeDisplays() {
        ttdSeconds = calculateTtdSeconds()
        dilationRemainingSeconds = calculateDilationRemainingSeconds()
        actionRemainingSeconds = calculateActionRemainingSeconds()
    }

    // ============================================================================
    // NAVIGATION & CONFIG
    // ============================================================================

    fun navigateTo(screen: AppScreen) {
        currentScreen = screen
    }

    fun updateConfig(config: SessionConfig) {
        sessionConfig = config
    }

    fun updateNotificationSettings(settings: NotificationSettings) {
        notificationSettings = settings
        storage.saveNotificationSettings(settings)
        timerService?.updateNotificationSettings(settings)
    }

    // ============================================================================
    // SESSION LIFECYCLE
    // ============================================================================

    fun startSession() {
        activePhases = sessionConfig.getActivePhases()
        if (activePhases.isEmpty()) return

        currentPhaseIndex = 0
        completedPhases.clear()
        sessionStartTime = System.currentTimeMillis()

        // Start service
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            putExtra("vibrationEnabled", notificationSettings.vibrationEnabled)
            putExtra("soundEnabled", notificationSettings.soundEnabled)
        }
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        startTTDForCurrentPhase()
        currentScreen = AppScreen.ActiveSession(sessionConfig)
    }

    private fun startTTDForCurrentPhase() {
        val phase = activePhases[currentPhaseIndex]
        sessionState = SessionState.TTD(phase)
        ttdStartTime = 0L
        ttdAccumulatedMs = 0L
        ttdRunning = false
        ttdSeconds = 0L
        earlyFinishSecondsRemaining = null  // Reset early finish tracking
    }

    fun startTTD() {
        ttdRunning = true
        ttdStartTime = SystemClock.elapsedRealtime()

        // Start a UI refresh loop
        ttdJob = viewModelScope.launch {
            while (isActive && ttdRunning) {
                updateTimeDisplays()
                delay(200) // Update UI 5 times per second
            }
        }
    }

    fun pauseTTD() {
        if (ttdRunning) {
            // Save accumulated time before pausing
            ttdAccumulatedMs += SystemClock.elapsedRealtime() - ttdStartTime
        }
        ttdRunning = false
        ttdJob?.cancel()
        updateTimeDisplays() // Final update
    }

    fun finishTTD() {
        // Store the TTD value before stopping
        lastTtdSeconds = calculateTtdSeconds()
        pauseTTD()
        startDilationForCurrentPhase()
    }

    // ============================================================================
    // DILATION PHASE
    // ============================================================================

    private fun startDilationForCurrentPhase() {
        val phase = activePhases[currentPhaseIndex]
        val duration = sessionConfig.getDuration(phase)

        dilationTotalSeconds = duration.minutes * 60
        dilationStartTime = SystemClock.elapsedRealtime()
        dilationAccumulatedMs = 0L
        dilationPaused = false
        lastNotifiedActionIndex = -1
        earlyFinishSecondsRemaining = null  // Reset early finish tracking

        sessionState = SessionState.Dilation(phase, DilationAction.PUSH)
        updateTimeDisplays()

        startDilationTimer()
    }

    private fun startDilationTimer() {
        dilationJob = viewModelScope.launch {
            // Send initial notification
            timerService?.makeNotification(NotificationEvent.PUSH_BEGIN)
            lastNotifiedActionIndex = 0

            while (isActive && calculateDilationRemainingSeconds() > 0) {
                if (!dilationPaused) {
                    // Update displayed values from absolute time
                    updateTimeDisplays()

                    // Check if we need to send a notification for a new action
                    val currentIdx = calculateCurrentActionIndex()
                    if (currentIdx != lastNotifiedActionIndex) {
                        lastNotifiedActionIndex = currentIdx

                        // Update session state with current action
                        val currentAction = if (currentIdx % 2 == 0) DilationAction.PUSH else DilationAction.SWIRL
                        sessionState = SessionState.Dilation(activePhases[currentPhaseIndex], currentAction)

                        // Send notification
                        if (currentAction == DilationAction.PUSH) {
                            timerService?.makeNotification(NotificationEvent.PUSH_BEGIN)
                        } else {
                            timerService?.makeNotification(NotificationEvent.SWIRL_BEGIN)
                        }
                    }

                    // Update service notification
                    val phase = (sessionState as? SessionState.Dilation)?.phase
                    val action = (sessionState as? SessionState.Dilation)?.action
                    timerService?.updateTimerState(phase, action, dilationRemainingSeconds, actionRemainingSeconds)
                }

                delay(200) // Check frequently for responsive UI
            }

            if (calculateDilationRemainingSeconds() <= 0) {
                finishDilation()
            }
        }
    }

    fun toggleDilationPause() {
        if (dilationPaused) {
            // Resuming - restart the clock from now
            dilationStartTime = SystemClock.elapsedRealtime()
            dilationPaused = false

            timerService?.let {
                TimerService.isPaused = false
                val intent = Intent(getApplication(), TimerService::class.java).apply {
                    action = TimerService.ACTION_RESUME
                }
                getApplication<Application>().startService(intent)
            }
        } else {
            // Pausing - save accumulated time
            dilationAccumulatedMs += SystemClock.elapsedRealtime() - dilationStartTime
            dilationPaused = true

            timerService?.let {
                TimerService.isPaused = true
                val intent = Intent(getApplication(), TimerService::class.java).apply {
                    action = TimerService.ACTION_PAUSE
                }
                getApplication<Application>().startService(intent)
            }
        }
        updateTimeDisplays() // Update UI immediately
    }

    // ============================================================================
    // EARLY FINISH - ends dilation phase before timer completes
    // ============================================================================

    fun earlyFinishDilation() {
        // Record the remaining seconds at time of early finish
        earlyFinishSecondsRemaining = calculateDilationRemainingSeconds()

        // Cancel the timer job
        dilationJob?.cancel()

        // Finish the dilation (this will save the phase with early finish info)
        finishDilation()
    }

    private fun finishDilation() {
        dilationJob?.cancel()

        // Make phase end notification
        timerService?.makeNotification(NotificationEvent.PHASE_END)

        // Save completed phase (with early finish info if applicable)
        val phase = activePhases[currentPhaseIndex]
        val duration = sessionConfig.getDuration(phase)
        completedPhases.add(
            PhaseData(
                size = phase,
                ttdSeconds = lastTtdSeconds,
                dilationMinutes = duration.minutes,
                earlyFinishSecondsRemaining = earlyFinishSecondsRemaining
            )
        )

        // Reset early finish tracking
        earlyFinishSecondsRemaining = null

        // Move to next phase or finish session
        currentPhaseIndex++
        if (currentPhaseIndex < activePhases.size) {
            startTTDForCurrentPhase()
        } else {
            finishSession()
        }
    }

    private fun finishSession() {
        val totalTime = (System.currentTimeMillis() - sessionStartTime) / 1000
        val session = Session(
            config = sessionConfig,
            phases = completedPhases.toList(),
            totalSeconds = totalTime
        )
        storage.addSession(session)

        // Stop service
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        getApplication<Application>().stopService(Intent(getApplication(), TimerService::class.java))

        // Reload data
        sessions = storage.loadSessions()
        stats = storage.calculateStats()

        currentScreen = AppScreen.Home
        sessionState = SessionState.Idle
    }

    // ============================================================================
    // SESSION MANAGEMENT
    // ============================================================================

    fun deleteSession(sessionId: String) {
        storage.deleteSession(sessionId)
        sessions = storage.loadSessions()
        stats = storage.calculateStats()
    }

    fun deleteAllSessions() {
        storage.deleteAllSessions()
        sessions = storage.loadSessions()
        stats = storage.calculateStats()
    }

    // ============================================================================
    // EXPORT
    // ============================================================================

    /**
     * Export all sessions and return Uri for sharing, or null if no sessions.
     */
    fun exportSessions(): android.net.Uri? {
        return storage.exportSessionsToFile()
    }

    fun cancelSession() {
        // Stop all timers
        ttdJob?.cancel()
        dilationJob?.cancel()

        // Stop service
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        getApplication<Application>().stopService(Intent(getApplication(), TimerService::class.java))

        // Reset state
        sessionState = SessionState.Idle
        completedPhases.clear()
        currentPhaseIndex = 0
        ttdStartTime = 0L
        ttdAccumulatedMs = 0L
        ttdRunning = false
        ttdSeconds = 0L
        dilationTotalSeconds = 0
        dilationStartTime = 0L
        dilationAccumulatedMs = 0L
        dilationRemainingSeconds = 0
        actionRemainingSeconds = ACTION_TIME
        dilationPaused = false
        earlyFinishSecondsRemaining = null

        // Navigate to home
        currentScreen = AppScreen.Home
    }

    override fun onCleared() {
        super.onCleared()
        ttdJob?.cancel()
        dilationJob?.cancel()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
        }
    }
}