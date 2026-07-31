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
import java.util.UUID

sealed class AppScreen {
    object Home : AppScreen()
    object NewSession : AppScreen()
    data class ActiveSession(val config: SessionConfig) : AppScreen()
    object SessionHistory : AppScreen()
    data class EditSession(val session: Session) : AppScreen()
    object Statistics : AppScreen()
    object StatsFilter : AppScreen()
    object Settings : AppScreen()
    object ImportExport : AppScreen()
}

sealed class SessionState {
    object Idle : SessionState()
    data class TTD(val phase: PhaseSize) : SessionState()
    data class DepthInput(val phase: PhaseSize) : SessionState()
    data class Dilation(val phase: PhaseSize, val action: DilationAction) : SessionState()
    object TagNoteInput : SessionState()
}

enum class HistorySortField(val label: String) {
    DATE("Date"),
    SESSION_LENGTH("Length"),
    TTD_SMALL("TTD Small"),
    TTD_MEDIUM("TTD Medium"),
    TTD_LARGE("TTD Large"),
    TTD_XL("TTD XL")
}

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = SessionStorage(application)
    var currentScreen by mutableStateOf<AppScreen>(AppScreen.Home)

    // Session configuration
    var sessionConfig by mutableStateOf(storage.getLastSessionConfig() ?: SessionConfig())

    // Notification settings
    var notificationSettings by mutableStateOf(storage.loadNotificationSettings())

    // App settings
    var keepScreenOn by mutableStateOf(storage.loadKeepScreenOn())
    var wakeLockEnabled by mutableStateOf(storage.loadWakeLockEnabled())
    var themeMode by mutableStateOf(storage.loadThemeMode())
    var countdownEnabled by mutableStateOf(storage.loadCountdownEnabled())
    var countdownIntervalMinutes by mutableIntStateOf(storage.loadCountdownIntervalMinutes())
    var day0Date by mutableStateOf(storage.loadDay0Date())
    var milestones by mutableStateOf(storage.loadMilestones())
    var tags by mutableStateOf(storage.loadTags())
        private set

    // Interrupted session resume
    var pendingResume by mutableStateOf<ActiveSessionSnapshot?>(null)
        private set

    // Active session state
    var sessionState by mutableStateOf<SessionState>(SessionState.Idle)
    var activePhases by mutableStateOf<List<PhaseSize>>(emptyList())
    var currentPhaseIndex by mutableIntStateOf(0)
    var dilationPaused by mutableStateOf(false)

    // Depth recording
    var currentDepthInput by mutableStateOf(14f)
        private set

    // ============================================================================
    // ABSOLUTE TIME TRACKING - survives device sleep
    // Using SystemClock.elapsedRealtime() which continues during sleep
    // ============================================================================

    // TTD (Time To Depth) - counts UP from 0
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

    var actionRemainingSeconds by mutableIntStateOf(sessionConfig.actionTime)
        private set

    // Inter-action rest ("Pause"): active flag + remaining seconds, for the active-session UI.
    var inActionPause by mutableStateOf(false)
        private set
    var actionPauseRemainingSeconds by mutableIntStateOf(0)
        private set
    private var actionPauseEndTime = 0L
    private var pendingActionIndex = -1

    // Session tracking
    private val completedPhases = mutableStateListOf<PhaseData>()
    private var sessionStartTime = 0L
    // Captured when dilation ends; keeps totalSeconds from growing while depth input is open
    private var sessionEndTime = 0L
    private var sessionId = ""

    // Store TTD value when finishing (before state changes)
    private var lastTtdSeconds = 0L

    // Store early finish seconds remaining (null if not early finished)
    private var earlyFinishSecondsRemaining: Int? = null

    // Store depth for current phase
    private var currentPhaseDepth: Float? = null

    // Pending tags/note collected on the TagNoteInput screen before finishSession()
    private var pendingTagIds: List<String> = emptyList()
    private var pendingNote: String = ""

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
            // Push full settings (incl. custom sound URIs, which the start intent doesn't carry).
            timerService?.updateNotificationSettings(notificationSettings)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            timerService = null
        }
    }

    // History and stats
    var sessions by mutableStateOf(storage.loadSessions())
    var historySortField by mutableStateOf(HistorySortField.DATE)
    var historySortAscending by mutableStateOf(false)

    val sortedSessions: List<Session>
        get() {
            fun Session.ttdFor(size: PhaseSize): Long =
                phases.find { it.size == size }?.ttdSeconds ?: Long.MAX_VALUE
            val cmp = Comparator<Session> { a, b ->
                when (historySortField) {
                    HistorySortField.DATE           -> a.timestamp.compareTo(b.timestamp)
                    HistorySortField.SESSION_LENGTH -> a.totalSeconds.compareTo(b.totalSeconds)
                    HistorySortField.TTD_SMALL      -> a.ttdFor(PhaseSize.SMALL).compareTo(b.ttdFor(PhaseSize.SMALL))
                    HistorySortField.TTD_MEDIUM     -> a.ttdFor(PhaseSize.MEDIUM).compareTo(b.ttdFor(PhaseSize.MEDIUM))
                    HistorySortField.TTD_LARGE      -> a.ttdFor(PhaseSize.LARGE).compareTo(b.ttdFor(PhaseSize.LARGE))
                    HistorySortField.TTD_XL         -> a.ttdFor(PhaseSize.XL).compareTo(b.ttdFor(PhaseSize.XL))
                }
            }
            return if (historySortAscending) sessions.sortedWith(cmp) else sessions.sortedWith(cmp.reversed())
        }

    fun updateSession(session: Session) {
        storage.saveOrUpdateSession(session)
        sessions = storage.loadSessions()
        stats = recomputeStats()
    }

    private val savedFilter = storage.loadStatsFilter()
    var statsFilterDays: Int? by mutableStateOf(savedFilter.days)
    var statsExcludedPeriodKeys: Set<Long?> by mutableStateOf(savedFilter.excludedPeriodKeys)
    var statsFilterTagIds: Set<String> by mutableStateOf(savedFilter.tagIds)
    var stats by mutableStateOf(recomputeStats())

    fun updateStatsFilter(days: Int?, excludedPeriodKeys: Set<Long?>, tagIds: Set<String>) {
        statsFilterDays = days
        statsExcludedPeriodKeys = excludedPeriodKeys
        statsFilterTagIds = tagIds
        storage.saveStatsFilter(days, excludedPeriodKeys, tagIds)
        stats = recomputeStats()
    }

    private fun recomputeStats(): SessionStats =
        storage.calculateStatsFromSessions(filteredStatsSessions())

    private fun filteredStatsSessions(): List<Session> {
        val cutoff = statsFilterDays?.let { System.currentTimeMillis() - it * 24L * 60 * 60 * 1000 }
        var result = if (cutoff != null) sessions.filter { it.timestamp >= cutoff } else sessions
        if (statsExcludedPeriodKeys.isNotEmpty()) {
            val sortedMilestones = milestones.sortedBy { it.date }
            result = result.filter { session ->
                var key: Long? = null
                for (m in sortedMilestones) { if (session.timestamp >= m.date) key = m.date else break }
                key !in statsExcludedPeriodKeys
            }
        }
        if (statsFilterTagIds.isNotEmpty()) {
            result = result.filter { session -> session.tagIds.any { it in statsFilterTagIds } }
        }
        return result
    }

    var ttdVisibleSizes by mutableStateOf(PhaseSize.entries.toSet())

    fun toggleTtdSize(size: PhaseSize) {
        ttdVisibleSizes = if (size in ttdVisibleSizes) ttdVisibleSizes - size else ttdVisibleSizes + size
    }

    init {
        pendingResume = storage.loadActiveSessionSnapshot()
    }

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
        if (sessionConfig.actionTime == 0) return calculateDilationRemainingSeconds()
        if (dilationTotalSeconds == 0) return sessionConfig.actionTime
        val elapsedMs = calculateDilationElapsedMs()
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        val intoCurrentAction = elapsedSeconds % sessionConfig.actionTime
        return sessionConfig.actionTime - intoCurrentAction
    }

    private fun calculateCurrentActionIndex(): Int {
        if (sessionConfig.actionTime == 0) return 0
        if (dilationTotalSeconds == 0) return 0
        val elapsedMs = calculateDilationElapsedMs()
        val elapsedSeconds = (elapsedMs / 1000).toInt()
        return elapsedSeconds / sessionConfig.actionTime
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

    fun updateKeepScreenOn(enabled: Boolean) {
        keepScreenOn = enabled
        storage.saveKeepScreenOn(enabled)
    }

    fun updateWakeLockEnabled(enabled: Boolean) {
        wakeLockEnabled = enabled
        storage.saveWakeLockEnabled(enabled)
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        storage.saveThemeMode(mode)
    }

    fun updateCountdownEnabled(enabled: Boolean) {
        countdownEnabled = enabled
        storage.saveCountdownEnabled(enabled)
    }

    fun updateCountdownIntervalMinutes(minutes: Int) {
        countdownIntervalMinutes = minutes
        storage.saveCountdownIntervalMinutes(minutes)
    }

    fun updateDay0Date(epochMillis: Long?) {
        day0Date = epochMillis
        storage.saveDay0Date(epochMillis)
    }

    fun addMilestone(milestone: Milestone) {
        val updated = (milestones + milestone).sortedBy { it.date }
        milestones = updated
        storage.saveMilestones(updated)
    }

    fun removeMilestone(milestone: Milestone) {
        val updated = milestones - milestone
        milestones = updated
        storage.saveMilestones(updated)
    }

    fun updateMilestoneComment(milestone: Milestone, newComment: String) {
        val updated = milestones.map { if (it == milestone) it.copy(comment = newComment) else it }
        milestones = updated
        storage.saveMilestones(updated)
    }

    fun addTag(tag: Tag) {
        tags = tags + tag
        storage.saveTags(tags)
    }

    fun removeTag(tag: Tag) {
        tags = tags.filter { it.id != tag.id }
        storage.saveTags(tags)
        val updatedSessions = sessions.map { session ->
            if (tag.id in session.tagIds) session.copy(tagIds = session.tagIds - tag.id)
            else session
        }
        if (updatedSessions != sessions) {
            updatedSessions.forEach { storage.saveOrUpdateSession(it) }
            sessions = storage.loadSessions()
            stats = recomputeStats()
        }
    }

    fun updateTag(updated: Tag) {
        tags = tags.map { if (it.id == updated.id) updated else it }
        storage.saveTags(tags)
    }

    // ============================================================================
    // DEPTH INPUT
    // ============================================================================

    fun updateDepthInput(depth: Float) {
        currentDepthInput = depth.coerceIn(0.1f, 99.9f)
    }

    fun confirmDepth() {
        currentPhaseDepth = currentDepthInput
        saveCurrentPhaseAndAdvance()
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
        sessionId = UUID.randomUUID().toString()

        // Start service
        val intent = Intent(getApplication(), TimerService::class.java).apply {
            putExtra("soundMode", notificationSettings.soundMode.name)
            putExtra("vibrationMode", notificationSettings.vibrationMode.name)
            putExtra("volumeLevel", notificationSettings.volumeLevel)
            putExtra("vibrationAmplitude", notificationSettings.vibrationAmplitude)
            putExtra("wakeLockEnabled", wakeLockEnabled)
        }
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        startTTDForCurrentPhase()
        currentScreen = AppScreen.ActiveSession(sessionConfig)
        saveSessionSnapshot()
    }

    private fun startTTDForCurrentPhase() {
        val phase = activePhases[currentPhaseIndex]
        sessionState = SessionState.TTD(phase)
        ttdStartTime = 0L
        ttdAccumulatedMs = 0L
        ttdRunning = false
        ttdSeconds = 0L
        earlyFinishSecondsRemaining = null  // Reset early finish tracking
        currentPhaseDepth = null  // Reset depth tracking
        sessionEndTime = 0L  // Resume live clock for next phase

        // Load the last depth for this phase size
        if (sessionConfig.recordDepth) {
            currentDepthInput = storage.getLastDepthForSize(phase)
        }
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
        val phase = activePhases[currentPhaseIndex]
        val plannedMinutes = sessionConfig.getDuration(phase).minutes
        saveSessionCheckpoint(PhaseData(
            size = phase,
            ttdSeconds = lastTtdSeconds,
            dilationMinutes = plannedMinutes,
            actionTime = sessionConfig.actionTime,
            pauseSeconds = sessionConfig.pauseSeconds.takeIf { it > 0 },
            earlyFinishSecondsRemaining = plannedMinutes * 60
        ))
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
        inActionPause = false
        lastNotifiedActionIndex = -1
        earlyFinishSecondsRemaining = null  // Reset early finish tracking

        sessionState = SessionState.Dilation(phase, DilationAction.PUSH)
        updateTimeDisplays()

        startDilationTimer()
        saveSessionSnapshot()
    }

    private fun startDilationTimer(isResume: Boolean = false) {
        dilationJob = viewModelScope.launch {
            if (!isResume) {
                timerService?.makeNotification(NotificationEvent.PUSH_BEGIN)
                lastNotifiedActionIndex = 0
            }

            while (isActive && calculateDilationRemainingSeconds() > 0) {
                if (dilationPaused) {
                    delay(200)
                    continue
                }

                if (inActionPause) {
                    // Resting between actions: the dilation clock stays frozen.
                    val now = SystemClock.elapsedRealtime()
                    actionPauseRemainingSeconds =
                        (((actionPauseEndTime - now) + 999) / 1000).toInt().coerceAtLeast(0)
                    timerService?.updateTimerState(
                        (sessionState as? SessionState.Dilation)?.phase, null,
                        dilationRemainingSeconds, actionPauseRemainingSeconds
                    )
                    if (now >= actionPauseEndTime) {
                        // Rest over: resume the clock and begin the deferred action.
                        dilationStartTime = now
                        inActionPause = false
                        actionPauseRemainingSeconds = 0
                        lastNotifiedActionIndex = pendingActionIndex
                        val currentAction = if (pendingActionIndex % 2 == 0) DilationAction.PUSH else DilationAction.SWIRL
                        sessionState = SessionState.Dilation(activePhases[currentPhaseIndex], currentAction)
                        if (currentAction == DilationAction.PUSH) {
                            timerService?.makeNotification(NotificationEvent.PUSH_BEGIN)
                        } else {
                            timerService?.makeNotification(NotificationEvent.SWIRL_BEGIN)
                        }
                    }
                    delay(200)
                    continue
                }

                // Update displayed values from absolute time
                updateTimeDisplays()

                // Check if we need to send a notification for a new action
                val currentIdx = calculateCurrentActionIndex()
                if (currentIdx != lastNotifiedActionIndex) {
                    if (sessionConfig.pauseSeconds > 0 && currentIdx > 0) {
                        // Insert a rest before the switched-to action; freeze the dilation clock.
                        val now = SystemClock.elapsedRealtime()
                        dilationAccumulatedMs += now - dilationStartTime
                        dilationStartTime = now
                        inActionPause = true
                        pendingActionIndex = currentIdx
                        actionPauseEndTime = now + sessionConfig.pauseSeconds * 1000L
                        actionPauseRemainingSeconds = sessionConfig.pauseSeconds
                        timerService?.makeNotification(NotificationEvent.PAUSE_BEGIN)
                        saveSessionSnapshot()
                        delay(200)
                        continue
                    }

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
        saveSessionSnapshot()
    }

    private fun saveSessionCheckpoint(currentPartialPhase: PhaseData? = null) {
        val phases = completedPhases.toList() + listOfNotNull(currentPartialPhase)
        if (phases.isEmpty()) return
        val effectiveEnd = if (sessionEndTime > 0) sessionEndTime else System.currentTimeMillis()
        storage.saveOrUpdateSession(Session(
            id = sessionId,
            config = sessionConfig,
            phases = phases,
            totalSeconds = (effectiveEnd - sessionStartTime) / 1000,
            timestamp = effectiveEnd,
            startTimestamp = sessionStartTime
        ))
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

        // Freeze the clock here so depth input time is never counted in session length
        sessionEndTime = System.currentTimeMillis()

        // If depth recording is enabled, show depth input before advancing
        if (sessionConfig.recordDepth) {
            val phase = activePhases[currentPhaseIndex]
            val duration = sessionConfig.getDuration(phase)
            saveSessionCheckpoint(PhaseData(
                size = phase,
                ttdSeconds = lastTtdSeconds,
                dilationMinutes = duration.minutes,
                actionTime = sessionConfig.actionTime,
                pauseSeconds = sessionConfig.pauseSeconds.takeIf { it > 0 },
                earlyFinishSecondsRemaining = earlyFinishSecondsRemaining
            ))
            sessionState = SessionState.DepthInput(phase)
        } else {
            saveCurrentPhaseAndAdvance()
        }
    }

    private fun saveCurrentPhaseAndAdvance() {
        // Save completed phase (with early finish info and depth if applicable)
        val phase = activePhases[currentPhaseIndex]
        val duration = sessionConfig.getDuration(phase)
        completedPhases.add(
            PhaseData(
                size = phase,
                ttdSeconds = lastTtdSeconds,
                dilationMinutes = duration.minutes,
                actionTime = sessionConfig.actionTime,
                pauseSeconds = sessionConfig.pauseSeconds.takeIf { it > 0 },
                earlyFinishSecondsRemaining = earlyFinishSecondsRemaining,
                depthCm = currentPhaseDepth
            )
        )
        saveSessionCheckpoint()

        // Reset early finish and depth tracking
        earlyFinishSecondsRemaining = null
        currentPhaseDepth = null

        // Move to next phase or finish session
        currentPhaseIndex++
        if (currentPhaseIndex < activePhases.size) {
            startTTDForCurrentPhase()
            saveSessionSnapshot()
        } else if (sessionConfig.addTagsNoteAtEnd) {
            sessionState = SessionState.TagNoteInput
            saveSessionSnapshot()
        } else {
            finishSession()
        }
    }

    fun confirmTagNote(tagIds: List<String>, note: String) {
        pendingTagIds = tagIds
        pendingNote   = note
        finishSession()
    }

    private fun finishSession() {
        val effectiveEnd = if (sessionEndTime > 0) sessionEndTime else System.currentTimeMillis()
        val totalTime = (effectiveEnd - sessionStartTime) / 1000
        val session = Session(
            id = sessionId,
            config = sessionConfig,
            phases = completedPhases.toList(),
            totalSeconds = totalTime,
            timestamp = effectiveEnd,
            tagIds = pendingTagIds,
            note = pendingNote,
            startTimestamp = sessionStartTime
        )
        storage.saveOrUpdateSession(session)
        storage.clearActiveSessionSnapshot()

        // Stop service
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        getApplication<Application>().stopService(Intent(getApplication(), TimerService::class.java))

        // Reload data
        sessions = storage.loadSessions()
        stats = recomputeStats()

        currentScreen = AppScreen.Home
        sessionState = SessionState.Idle
    }

    // ============================================================================
    // SESSION MANAGEMENT
    // ============================================================================

    fun deleteSession(sessionId: String) {
        storage.deleteSession(sessionId)
        sessions = storage.loadSessions()
        stats = recomputeStats()
    }

    fun deleteAllSessions() {
        storage.deleteAllSessions()
        sessions = storage.loadSessions()
        stats = recomputeStats()
    }

    // ============================================================================
    // EXPORT & IMPORT
    // ============================================================================

    /**
     * Export all sessions and return Uri for sharing, or null if no sessions.
     */
    fun exportSessions(): android.net.Uri? {
        return storage.exportSessionsToFile()
    }

    /**
     * Save export to Downloads folder.
     */
    fun saveExportToDownloads(): ExportResult {
        return storage.saveExportToDownloads()
    }

    /**
     * Import sessions from a file Uri.
     */
    fun importSessions(uri: android.net.Uri): ImportResult {
        val result = storage.importSessionsFromUri(uri)
        sessions = storage.loadSessions()
        stats = recomputeStats()
        return result
    }

    fun importFromButterfly(uri: android.net.Uri): ImportResult {
        val result = storage.importFromButterflyUri(uri)
        sessions = storage.loadSessions()
        stats = recomputeStats()
        return result
    }

    fun cancelSession() {
        ttdJob?.cancel()
        dilationJob?.cancel()
        storage.clearActiveSessionSnapshot()
        stopService()
        resetSessionState()
        currentScreen = AppScreen.Home
    }

    fun saveAndCancelSession() {
        ttdJob?.cancel()
        dilationJob?.cancel()

        // Build the in-progress phase data based on the current state
        val partialPhase: PhaseData? = when (sessionState) {
            is SessionState.Dilation -> {
                val phase = activePhases[currentPhaseIndex]
                PhaseData(
                    size = phase,
                    ttdSeconds = lastTtdSeconds,
                    dilationMinutes = sessionConfig.getDuration(phase).minutes,
                    actionTime = sessionConfig.actionTime,
                    pauseSeconds = sessionConfig.pauseSeconds.takeIf { it > 0 },
                    earlyFinishSecondsRemaining = calculateDilationRemainingSeconds(),
                    depthCm = currentPhaseDepth
                )
            }
            is SessionState.DepthInput -> {
                // Dilation already finished; save the phase without depth (not yet entered)
                val phase = activePhases[currentPhaseIndex]
                PhaseData(
                    size = phase,
                    ttdSeconds = lastTtdSeconds,
                    dilationMinutes = sessionConfig.getDuration(phase).minutes,
                    actionTime = sessionConfig.actionTime,
                    pauseSeconds = sessionConfig.pauseSeconds.takeIf { it > 0 },
                    earlyFinishSecondsRemaining = earlyFinishSecondsRemaining,
                    depthCm = null
                )
            }
            else -> null  // TTD phase not complete — only save already-finished phases
        }

        saveSessionCheckpoint(partialPhase)
        storage.clearActiveSessionSnapshot()
        sessions = storage.loadSessions()
        stats = recomputeStats()

        stopService()
        resetSessionState()
        currentScreen = AppScreen.Home
    }

    private fun stopService() {
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        getApplication<Application>().stopService(Intent(getApplication(), TimerService::class.java))
    }

    private fun resetSessionState() {
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
        actionRemainingSeconds = sessionConfig.actionTime
        dilationPaused = false
        inActionPause = false
        actionPauseRemainingSeconds = 0
        earlyFinishSecondsRemaining = null
        currentPhaseDepth = null
        pendingTagIds = emptyList()
        pendingNote   = ""
    }

    private fun saveSessionSnapshot() {
        if (sessionState == SessionState.Idle) return

        val stateType = when (sessionState) {
            is SessionState.TTD -> "TTD"
            is SessionState.DepthInput -> "DEPTH_INPUT"
            is SessionState.Dilation -> "DILATION"
            is SessionState.TagNoteInput -> "TAG_NOTE_INPUT"
            else -> return
        }

        // TagNoteInput: all phases done, currentPhaseIndex is past end — use last phase
        val phase = if (sessionState is SessionState.TagNoteInput) {
            activePhases.lastOrNull() ?: return
        } else {
            activePhases.getOrNull(currentPhaseIndex) ?: return
        }

        val ttdElapsedMs = ttdAccumulatedMs +
            if (ttdRunning) SystemClock.elapsedRealtime() - ttdStartTime else 0L

        val dilationElapsedMs = dilationAccumulatedMs +
            if (!dilationPaused && sessionState is SessionState.Dilation)
                SystemClock.elapsedRealtime() - dilationStartTime
            else 0L

        storage.saveActiveSessionSnapshot(ActiveSessionSnapshot(
            sessionId = sessionId,
            sessionConfig = sessionConfig,
            completedPhases = completedPhases.toList(),
            currentPhaseIndex = currentPhaseIndex,
            stateType = stateType,
            currentPhaseSize = phase.name,
            sessionStartWallClock = sessionStartTime,
            saveWallClock = System.currentTimeMillis(),
            ttdElapsedMs = ttdElapsedMs,
            ttdRunning = ttdRunning,
            lastTtdSeconds = lastTtdSeconds,
            dilationTotalSeconds = dilationTotalSeconds,
            dilationElapsedMs = dilationElapsedMs,
            dilationPaused = dilationPaused,
            earlyFinishSecondsRemaining = earlyFinishSecondsRemaining,
            currentPhaseDepth = currentPhaseDepth,
            sessionEndWallClock = if (sessionState is SessionState.DepthInput || sessionState is SessionState.TagNoteInput) sessionEndTime else 0L
        ))
    }

    fun resumeSession() {
        val snapshot = pendingResume ?: return
        pendingResume = null

        sessionId = snapshot.sessionId
        sessionConfig = snapshot.sessionConfig
        sessionStartTime = snapshot.sessionStartWallClock
        currentPhaseIndex = snapshot.currentPhaseIndex
        completedPhases.clear()
        completedPhases.addAll(snapshot.completedPhases)
        activePhases = sessionConfig.getActivePhases()

        val intent = Intent(getApplication(), TimerService::class.java).apply {
            putExtra("soundMode", notificationSettings.soundMode.name)
            putExtra("vibrationMode", notificationSettings.vibrationMode.name)
            putExtra("volumeLevel", notificationSettings.volumeLevel)
            putExtra("vibrationAmplitude", notificationSettings.vibrationAmplitude)
            putExtra("wakeLockEnabled", wakeLockEnabled)
        }
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        currentScreen = AppScreen.ActiveSession(sessionConfig)

        val phase = activePhases.getOrNull(currentPhaseIndex)
            ?: run { discardInterruptedSession(); return }

        when (snapshot.stateType) {
            "TTD" -> {
                sessionState = SessionState.TTD(phase)
                ttdAccumulatedMs = snapshot.ttdElapsedMs
                ttdStartTime = 0L
                ttdRunning = false
                ttdSeconds = snapshot.ttdElapsedMs / 1000
                sessionEndTime = 0L
                earlyFinishSecondsRemaining = null
                currentPhaseDepth = null
                if (sessionConfig.recordDepth) {
                    currentDepthInput = storage.getLastDepthForSize(phase)
                }
            }

            "DEPTH_INPUT" -> {
                lastTtdSeconds = snapshot.lastTtdSeconds
                sessionEndTime = if (snapshot.sessionEndWallClock > 0)
                    snapshot.sessionEndWallClock else System.currentTimeMillis()
                sessionState = SessionState.DepthInput(phase)
                if (sessionConfig.recordDepth) {
                    currentDepthInput = storage.getLastDepthForSize(phase)
                }
            }

            "TAG_NOTE_INPUT" -> {
                sessionEndTime = if (snapshot.sessionEndWallClock > 0)
                    snapshot.sessionEndWallClock else System.currentTimeMillis()
                finishSession()
                return
            }

            "DILATION" -> {
                lastTtdSeconds = snapshot.lastTtdSeconds
                dilationTotalSeconds = snapshot.dilationTotalSeconds
                earlyFinishSecondsRemaining = snapshot.earlyFinishSecondsRemaining
                currentPhaseDepth = snapshot.currentPhaseDepth
                sessionEndTime = 0L

                val timeSinceSave = System.currentTimeMillis() - snapshot.saveWallClock
                val adjustedElapsedMs = if (snapshot.dilationPaused)
                    snapshot.dilationElapsedMs
                else
                    snapshot.dilationElapsedMs + timeSinceSave

                if (adjustedElapsedMs >= snapshot.dilationTotalSeconds * 1000L) {
                    // Dilation finished while app was down — fast-forward to completion
                    dilationAccumulatedMs = snapshot.dilationTotalSeconds * 1000L
                    dilationStartTime = SystemClock.elapsedRealtime()
                    dilationPaused = false
                    sessionState = SessionState.Dilation(phase, DilationAction.PUSH)
                    updateTimeDisplays()
                    finishDilation()
                    return
                }

                dilationAccumulatedMs = adjustedElapsedMs
                dilationStartTime = SystemClock.elapsedRealtime()
                dilationPaused = snapshot.dilationPaused

                val actionIdx = if (sessionConfig.actionTime > 0)
                    (adjustedElapsedMs / 1000).toInt() / sessionConfig.actionTime
                else 0
                val action = if (actionIdx % 2 == 0) DilationAction.PUSH else DilationAction.SWIRL
                sessionState = SessionState.Dilation(phase, action)
                lastNotifiedActionIndex = actionIdx

                updateTimeDisplays()
                startDilationTimer(isResume = true)
            }
        }

        saveSessionSnapshot()
    }

    fun discardInterruptedSession() {
        storage.clearActiveSessionSnapshot()
        pendingResume = null
    }

    override fun onCleared() {
        if (sessionState !is SessionState.Idle) {
            saveSessionSnapshot()
        }
        super.onCleared()
        ttdJob?.cancel()
        dilationJob?.cancel()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
        }
    }
}
