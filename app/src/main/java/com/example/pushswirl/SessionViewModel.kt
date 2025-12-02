package org.kreatrix.pushswirl

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
    var ttdSeconds by mutableLongStateOf(0L)
    var ttdRunning by mutableStateOf(false)
    var dilationTotalSeconds by mutableIntStateOf(0)
    var dilationRemainingSeconds by mutableIntStateOf(0)
    var actionRemainingSeconds by mutableIntStateOf(15)
    var dilationPaused by mutableStateOf(false)

    // Session tracking
    private val completedPhases = mutableStateListOf<PhaseData>()
    private var sessionStartTime = 0L

    // Timer jobs
    private var ttdJob: Job? = null
    private var dilationJob: Job? = null

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
        ttdSeconds = 0L
        ttdRunning = false
    }

    fun startTTD() {
        ttdRunning = true
        ttdJob = viewModelScope.launch {
            while (isActive && ttdRunning) {
                delay(1000)
                ttdSeconds++
            }
        }
    }

    fun pauseTTD() {
        ttdRunning = false
        ttdJob?.cancel()
    }

    fun finishTTD() {
        pauseTTD()
        startDilationForCurrentPhase()
    }

    private fun startDilationForCurrentPhase() {
        val phase = activePhases[currentPhaseIndex]
        val duration = sessionConfig.getDuration(phase)

        dilationTotalSeconds = duration.minutes * 60
        dilationRemainingSeconds = dilationTotalSeconds
        actionRemainingSeconds = ACTION_TIME
        dilationPaused = false
        sessionState = SessionState.Dilation(phase, DilationAction.PUSH)

        startDilationTimer()
    }

    private fun startDilationTimer() {
        dilationJob = viewModelScope.launch {
            while (isActive && dilationRemainingSeconds > 0) {
                if (!dilationPaused) {

                    if (actionRemainingSeconds == ACTION_TIME) {
                        // Make notification
                        if ((sessionState as SessionState.Dilation).action == DilationAction.PUSH) {
                            timerService?.makeNotification(NotificationEvent.PUSH_BEGIN)
                        } else {
                            timerService?.makeNotification(NotificationEvent.SWIRL_BEGIN)
                        }
                    }
                    delay(1000)
                    dilationRemainingSeconds--
                    actionRemainingSeconds--

                    // Update service notification
                    val phase = (sessionState as? SessionState.Dilation)?.phase
                    val action = (sessionState as? SessionState.Dilation)?.action
                    timerService?.updateTimerState(phase, action, dilationRemainingSeconds, actionRemainingSeconds)

                    if (actionRemainingSeconds <= 0) {
                        // Switch actions
                        val currentAction = (sessionState as SessionState.Dilation).action
                        val nextAction = if (currentAction == DilationAction.PUSH) DilationAction.SWIRL else DilationAction.PUSH

                        sessionState = SessionState.Dilation(activePhases[currentPhaseIndex], nextAction)
                        actionRemainingSeconds = ACTION_TIME
                    }
                } else {
                    delay(100)
                }
            }

            if (dilationRemainingSeconds <= 0) {
                finishDilation()
            }
        }
    }

    fun toggleDilationPause() {
        dilationPaused = !dilationPaused

        if (dilationPaused) {
            timerService?.let {
                TimerService.isPaused = true
                val intent = Intent(getApplication(), TimerService::class.java).apply {
                    action = TimerService.ACTION_PAUSE
                }
                getApplication<Application>().startService(intent)
            }
        } else {
            timerService?.let {
                TimerService.isPaused = false
                val intent = Intent(getApplication(), TimerService::class.java).apply {
                    action = TimerService.ACTION_RESUME
                }
                getApplication<Application>().startService(intent)
            }
        }
    }

    private fun finishDilation() {
        dilationJob?.cancel()

        // Make phase end notification
        timerService?.makeNotification(NotificationEvent.PHASE_END)

        // Save completed phase
        val phase = activePhases[currentPhaseIndex]
        val duration = sessionConfig.getDuration(phase)
        completedPhases.add(PhaseData(phase, ttdSeconds, duration.minutes))

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
        getApplication<Application>().unbindService(serviceConnection)
        getApplication<Application>().stopService(Intent(getApplication(), TimerService::class.java))

        // Reload data
        sessions = storage.loadSessions()
        stats = storage.calculateStats()

        currentScreen = AppScreen.Home
        sessionState = SessionState.Idle
    }

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

    fun cancelSession() {
        // Stop all timers
        ttdJob?.cancel()
        dilationJob?.cancel()

        // Stop service
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
        }
        getApplication<Application>().stopService(Intent(getApplication(), TimerService::class.java))

        // Reset state
        sessionState = SessionState.Idle
        completedPhases.clear()
        currentPhaseIndex = 0
        ttdSeconds = 0L
        ttdRunning = false
        dilationPaused = false

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