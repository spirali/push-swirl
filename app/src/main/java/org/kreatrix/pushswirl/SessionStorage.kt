package org.kreatrix.pushswirl

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class SessionStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("pushswirl_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    private fun getAppVersion(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    fun saveSessions(sessions: List<Session>) {
        val json = gson.toJson(sessions)
        prefs.edit().putString("sessions", json).apply()
    }

    fun loadSessions(): List<Session> {
        val json = prefs.getString("sessions", null) ?: return emptyList()
        val type = object : TypeToken<List<Session>>() {}.type
        val raw: List<Session> = gson.fromJson(json, type) ?: return emptyList()
        return raw.map { it.withNullDefaults() }
    }

    fun addSession(session: Session) {
        val sessions = loadSessions().toMutableList()
        sessions.add(0, session)
        saveSessions(sessions)
    }

    fun saveOrUpdateSession(session: Session) {
        val sessions = loadSessions().toMutableList()
        val idx = sessions.indexOfFirst { it.id == session.id }
        if (idx >= 0) sessions[idx] = session else sessions.add(0, session)
        saveSessions(sessions)
    }

    fun deleteSession(sessionId: String) {
        val sessions = loadSessions().filter { it.id != sessionId }
        saveSessions(sessions)
    }

    fun deleteAllSessions() {
        saveSessions(emptyList())
    }

    fun getLastSessionConfig(): SessionConfig? {
        return loadSessions().firstOrNull()?.config
    }

    fun saveNotificationSettings(settings: NotificationSettings) {
        prefs.edit()
            .putString("notification_sound_mode", settings.soundMode.name)
            .putString("notification_vibration_mode", settings.vibrationMode.name)
            .putFloat("notification_volume", settings.volumeLevel)
            .putFloat("notification_vibration_amplitude", settings.vibrationAmplitude)
            .apply()
    }

    fun loadNotificationSettings(): NotificationSettings {
        val soundModeStr = prefs.getString("notification_sound_mode", null)
        val vibModeStr = prefs.getString("notification_vibration_mode", null)
        val soundMode = if (soundModeStr != null) SoundMode.valueOf(soundModeStr)
                        else if (prefs.getBoolean("notification_sound", true)) SoundMode.SYSTEM else SoundMode.OFF
        val vibMode = if (vibModeStr != null) VibrationMode.valueOf(vibModeStr)
                      else if (prefs.getBoolean("notification_vibration", true)) VibrationMode.SYSTEM else VibrationMode.OFF
        return NotificationSettings(
            soundMode = soundMode,
            vibrationMode = vibMode,
            volumeLevel = prefs.getFloat("notification_volume", 0.5f),
            vibrationAmplitude = prefs.getFloat("notification_vibration_amplitude", 1.0f)
        )
    }

    fun saveKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun loadKeepScreenOn(): Boolean {
        return prefs.getBoolean("keep_screen_on", true)
    }

    fun saveWakeLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("wake_lock_enabled", enabled).apply()
    }

    fun loadWakeLockEnabled(): Boolean {
        return prefs.getBoolean("wake_lock_enabled", true)
    }

    fun saveActiveSessionSnapshot(snapshot: ActiveSessionSnapshot) {
        prefs.edit().putString("active_session_snapshot", gson.toJson(snapshot)).apply()
    }

    fun loadActiveSessionSnapshot(): ActiveSessionSnapshot? {
        val json = prefs.getString("active_session_snapshot", null) ?: return null
        return try {
            gson.fromJson(json, ActiveSessionSnapshot::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clearActiveSessionSnapshot() {
        prefs.edit().remove("active_session_snapshot").apply()
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun loadThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
        return ThemeMode.valueOf(name)
    }

    fun saveCountdownEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("countdown_enabled", enabled).apply()
    }

    fun loadCountdownEnabled(): Boolean {
        return prefs.getBoolean("countdown_enabled", false)
    }

    fun saveCountdownIntervalMinutes(minutes: Int) {
        prefs.edit().putInt("countdown_interval_minutes", minutes).apply()
    }

    fun loadCountdownIntervalMinutes(): Int {
        return prefs.getInt("countdown_interval_minutes", 8 * 60)
    }

    fun saveMilestones(milestones: List<Milestone>) {
        prefs.edit().putString("milestones", gson.toJson(milestones)).apply()
    }

    fun loadMilestones(): List<Milestone> {
        val json = prefs.getString("milestones", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Milestone>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDay0Date(epochMillis: Long?) {
        if (epochMillis != null) {
            prefs.edit().putLong("day0_date", epochMillis).apply()
        } else {
            prefs.edit().remove("day0_date").apply()
        }
    }

    fun loadDay0Date(): Long? {
        val v = prefs.getLong("day0_date", -1L)
        return if (v == -1L) null else v
    }

    fun saveTags(tags: List<Tag>) {
        prefs.edit().putString("tags", gson.toJson(tags)).apply()
    }

    fun loadTags(): List<Tag> {
        val json = prefs.getString("tags", null)
        return if (json != null) {
            try {
                gson.fromJson(json, object : TypeToken<List<Tag>>() {}.type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        } else {
            val defaults = listOf(
                Tag(name = "Pain",        color = TagColor.RED),
                Tag(name = "Disruption",  color = TagColor.ORANGE),
                Tag(name = "Good",        color = TagColor.GREEN),
                Tag(name = "Painkillers", color = TagColor.BLUE)
            )
            saveTags(defaults)
            defaults
        }
    }

    fun saveStatsFilter(days: Int?, excludedKeys: Set<Long?>) {
        prefs.edit()
            .putInt("stats_filter_days", days ?: -1)
            .putString("stats_excluded_period_keys", gson.toJson(excludedKeys.toList()))
            .apply()
    }

    fun loadStatsFilter(): Pair<Int?, Set<Long?>> {
        val days = prefs.getInt("stats_filter_days", 14).let { if (it == -1) null else it }
        val keysJson = prefs.getString("stats_excluded_period_keys", null)
        val keys: Set<Long?> = if (keysJson != null) {
            try {
                val type = object : TypeToken<List<Long?>>() {}.type
                val list: List<Long?> = gson.fromJson(keysJson, type)
                list.toSet()
            } catch (e: Exception) { emptySet() }
        } else emptySet()
        return Pair(days, keys)
    }

    fun getLastDepthForSize(size: PhaseSize): Float {
        // Get the last recorded depth from sessions that have depth recorded
        val sessions = loadSessions()
        val lastPhaseWithDepth = sessions
            .flatMap { it.phases }
            .filter { it.size == size && it.depthCm != null }
            .firstOrNull()

        return lastPhaseWithDepth?.depthCm ?: 14f
    }

    fun calculateStats(intervalDays: Int? = null): SessionStats {
        val allSessions = loadSessions()
        val sessions = if (intervalDays != null) {
            val cutoff = System.currentTimeMillis() - intervalDays * 24L * 60 * 60 * 1000
            allSessions.filter { it.timestamp >= cutoff }
        } else allSessions
        return calculateStatsFromSessions(sessions)
    }

    fun calculateStatsFromSessions(sessions: List<Session>): SessionStats {
        if (sessions.isEmpty()) return SessionStats(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0)

        val smallTTDs = mutableListOf<Double>()
        val mediumTTDs = mutableListOf<Double>()
        val largeTTDs = mutableListOf<Double>()
        val xlTTDs = mutableListOf<Double>()

        sessions.forEach { session ->
            session.phases.forEach { phase ->
                when (phase.size) {
                    PhaseSize.SMALL -> smallTTDs.add(phase.ttdSeconds.toDouble())
                    PhaseSize.MEDIUM -> mediumTTDs.add(phase.ttdSeconds.toDouble())
                    PhaseSize.LARGE -> largeTTDs.add(phase.ttdSeconds.toDouble())
                    PhaseSize.XL -> xlTTDs.add(phase.ttdSeconds.toDouble())
                }
            }
        }

        val avgTimeBetweenSessions = if (sessions.size < 2) 0.0 else {
            val sorted = sessions.sortedBy { it.timestamp }
            val gaps = sorted.zipWithNext { a, b -> (b.timestamp - a.timestamp).toDouble() / 1000.0 }
            gaps.average()
        }

        return SessionStats(
            totalSessions = sessions.size,
            smallTTD = calculateSimpleAverage(smallTTDs),
            mediumTTD = calculateSimpleAverage(mediumTTDs),
            largeTTD = calculateSimpleAverage(largeTTDs),
            xlTTD = calculateSimpleAverage(xlTTDs),
            sessionLength = calculateSimpleAverage(sessions.map { it.totalSeconds.toDouble() }),
            avgTimeBetweenSessions = avgTimeBetweenSessions
        )
    }

    fun exportSessionsToJson(): String {
        val sessions = loadSessions()

        // ISO format for timestamps
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        // Create export data with metadata
        val exportData = ExportData(
            exportDate = isoFormat.format(Date()),
            appVersion = getAppVersion(),
            day0Date = loadDay0Date()?.let { isoFormat.format(Date(it)) },
            milestones = loadMilestones().map { MilestoneExport(isoFormat.format(Date(it.date)), it.comment) },
            tags = loadTags().map { TagExport(it.id, it.name, it.color.name) },
            sessions = sessions.map { it.toExport() }
        )

        return prettyGson.toJson(exportData)
    }

    /**
     * Export all sessions to a JSON file and return the Uri for sharing.
     * Returns null if there are no sessions to export.
     */
    fun exportSessionsToFile(): Uri? {
        val json = exportSessionsToJson();

        // Create filename with timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val filename = "pushswirl_export_$timestamp.json"

        // Write to cache directory
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val exportFile = File(exportDir, filename)
        exportFile.writeText(json)

        // Return Uri via FileProvider
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )
    }

    /**
     * Export all sessions and save to Downloads folder.
     * Returns an ExportResult indicating success or failure.
     */
    fun saveExportToDownloads(): ExportResult {
        val json = exportSessionsToJson();
        return try {
            // Create filename with timestamp
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val filename = "pushswirl_export_$timestamp.json"

            // For Android Q (API 29) and above, use MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                    ExportResult.Success(filename)
                } else {
                    ExportResult.Error("Could not create file")
                }
            } else {
                // For older Android versions, write directly to Downloads
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                downloadsDir.mkdirs()
                val exportFile = File(downloadsDir, filename)
                exportFile.writeText(json)
                ExportResult.Success(filename)
            }
        } catch (e: Exception) {
            ExportResult.Error("Export failed: ${e.message}")
        }
    }

    /**
     * Import sessions from a JSON file.
     * Returns an ImportResult indicating success or failure.
     */
    fun importSessionsFromUri(uri: Uri): ImportResult {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val json = inputStream?.bufferedReader()?.use { it.readText() }
                ?: return ImportResult.Error("Could not read file")

            // Try to parse as ExportData first
            val exportData = try {
                gson.fromJson(json, ExportData::class.java)
            } catch (e: JsonSyntaxException) {
                return ImportResult.Error("Invalid file format")
            }

            if (exportData.sessions.isEmpty()) {
                return ImportResult.Error("No sessions found in file")
            }

            // Convert SessionExport back to Session
            val currentSessions = loadSessions().toMutableList()
            var importedCount = 0
            var skippedCount = 0

            exportData.sessions.forEach { sessionExport ->
                // Check if session already exists by ID
                if (currentSessions.none { it.id == sessionExport.id }) {
                    // Infer config from phases (config is never exported)
                    val config = inferConfigFromPhases(sessionExport.phases)

                    // Parse timestamp
                    val timestamp = try {
                        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                        isoFormat.parse(sessionExport.timestamp)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }

                    val session = Session(
                        id = sessionExport.id,
                        config = config,
                        phases = sessionExport.phases,
                        totalSeconds = sessionExport.totalSeconds,
                        timestamp = timestamp,
                        tagIds = sessionExport.tagIds ?: emptyList(),
                        note = sessionExport.note ?: ""
                    )
                    currentSessions.add(session)
                    importedCount++
                } else {
                    skippedCount++
                }
            }

            // Sort by timestamp (newest first) and save
            currentSessions.sortByDescending { it.timestamp }
            saveSessions(currentSessions)

            // Restore Day 0 date if present in the export
            exportData.day0Date?.let { isoStr ->
                try {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                    isoFormat.parse(isoStr)?.time?.let { saveDay0Date(it) }
                } catch (_: Exception) {}
            }

            // Restore milestones if present in the export
            exportData.milestones?.let { exported ->
                try {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                    val restored = exported.mapNotNull { m ->
                        isoFormat.parse(m.date)?.time?.let { Milestone(it, m.comment) }
                    }.sortedBy { it.date }
                    saveMilestones(restored)
                } catch (_: Exception) {}
            }

            // Merge imported tags — add any tag whose UUID is not already in local storage
            exportData.tags?.let { exportedTags ->
                val localTags = loadTags().toMutableList()
                val localTagIds = localTags.map { it.id }.toHashSet()
                var changed = false
                for (tagExport in exportedTags) {
                    if (tagExport.id !in localTagIds) {
                        try {
                            localTags += Tag(
                                id = tagExport.id,
                                name = tagExport.name,
                                color = TagColor.valueOf(tagExport.color)
                            )
                            changed = true
                        } catch (_: IllegalArgumentException) {}
                    }
                }
                if (changed) saveTags(localTags)
            }

            ImportResult.Success(importedCount, skippedCount)
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    /**
     * Import sessions from a Butterfly app CSV export.
     *
     * Column mapping:
     *   Insertion Duration (s)       → TTD for Medium phase; Large TTD is always 0
     *   Dynamic Medium Duration (s)  → Medium dilation length (rounded to nearest 5/10/15/30 min)
     *   Dynamic Large Duration (s)   → Large dilation length (rounded to nearest 5/10/15/30 min)
     *   Static Duration (s)          → fallback when Dynamic columns are empty; split equally
     *   Final Depth                  → depthCm on the Large phase
     *
     * Each row gets a stable UUID derived from its Date+StartTime so re-importing the same
     * file never creates duplicates.
     */
    fun importFromButterflyUri(uri: Uri): ImportResult {
        return try {
            val lines = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readLines() }
                ?: return ImportResult.Error("Could not read file")

            if (lines.size < 2) return ImportResult.Error("Empty or invalid CSV")

            val currentSessions = loadSessions().toMutableList()
            val existingIds = currentSessions.map { it.id }.toHashSet()
            var importedCount = 0
            var skippedCount = 0

            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            for (line in lines.drop(1)) {
                if (line.isBlank()) continue
                val f = line.split(",").map { it.trim() }
                if (f.size < 5) continue

                val dateStr      = f.getOrElse(0) { "" }
                val startTimeStr = f.getOrElse(1) { "" }
                val endTimeStr   = f.getOrElse(2) { "" }
                // f[3] = Type — ignored
                val insertionSec  = f.getOrElse(4) { "" }.toLongOrNull() ?: 0L
                val staticSec     = f.getOrElse(5) { "" }.toIntOrNull()
                val dynMediumSec  = f.getOrElse(6) { "" }.toIntOrNull()
                val dynLargeSec   = f.getOrElse(7) { "" }.toIntOrNull()
                val finalDepth    = f.getOrElse(8) { "" }.toFloatOrNull()

                // Stable duplicate-proof ID
                val id = UUID.nameUUIDFromBytes("butterfly:${dateStr}T${startTimeStr}".toByteArray()).toString()
                if (id in existingIds) { skippedCount++; continue }

                val timestamp = try {
                    dateFmt.parse("$dateStr $startTimeStr")?.time ?: continue
                } catch (_: Exception) { continue }

                val startSec = butterflyTimeToSeconds(startTimeStr)
                val endSec   = butterflyTimeToSeconds(endTimeStr)
                val totalSec = if (endSec >= startSec) (endSec - startSec).toLong()
                               else (endSec + 86400 - startSec).toLong()

                // Determine per-phase durations in seconds
                val (mediumDurSec, largeDurSec) = when {
                    dynMediumSec != null || dynLargeSec != null ->
                        Pair(dynMediumSec ?: 0, dynLargeSec ?: 0)
                    staticSec != null ->
                        Pair(staticSec / 2, staticSec / 2)
                    else -> continue
                }

                val phases = mutableListOf<PhaseData>()
                if (mediumDurSec > 0) {
                    phases += PhaseData(
                        size = PhaseSize.MEDIUM,
                        ttdSeconds = insertionSec,
                        dilationMinutes = nearestPhaseDuration(mediumDurSec).minutes
                    )
                }
                if (largeDurSec > 0) {
                    phases += PhaseData(
                        size = PhaseSize.LARGE,
                        ttdSeconds = 0L,
                        dilationMinutes = nearestPhaseDuration(largeDurSec).minutes,
                        depthCm = finalDepth
                    )
                }
                if (phases.isEmpty()) continue

                val session = Session(
                    id = id,
                    config = inferConfigFromPhases(phases),
                    phases = phases,
                    totalSeconds = totalSec,
                    timestamp = timestamp
                )
                currentSessions += session
                existingIds += id
                importedCount++
            }

            currentSessions.sortByDescending { it.timestamp }
            saveSessions(currentSessions)

            ImportResult.Success(importedCount, skippedCount)
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    private fun butterflyTimeToSeconds(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 3600 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    private fun nearestPhaseDuration(seconds: Int): PhaseDuration {
        val minutes = seconds / 60.0
        return listOf(PhaseDuration.FIVE, PhaseDuration.TEN, PhaseDuration.FIFTEEN, PhaseDuration.THIRTY)
            .minByOrNull { kotlin.math.abs(it.minutes - minutes) }!!
    }

    /**
     * Infer session config from the phases that were completed.
     * Config is not exported, so it's always reconstructed from phases on import.
     */
    private fun inferConfigFromPhases(phases: List<PhaseData>): SessionConfig {
        val small = phases.find { it.size == PhaseSize.SMALL }?.let {
            PhaseDuration.entries.find { duration -> duration.minutes == it.dilationMinutes }
        } ?: PhaseDuration.SKIP

        val medium = phases.find { it.size == PhaseSize.MEDIUM }?.let {
            PhaseDuration.entries.find { duration -> duration.minutes == it.dilationMinutes }
        } ?: PhaseDuration.SKIP

        val large = phases.find { it.size == PhaseSize.LARGE }?.let {
            PhaseDuration.entries.find { duration -> duration.minutes == it.dilationMinutes }
        } ?: PhaseDuration.SKIP

        val xl = phases.find { it.size == PhaseSize.XL }?.let {
            PhaseDuration.entries.find { duration -> duration.minutes == it.dilationMinutes }
        } ?: PhaseDuration.SKIP

        // Check if any phase has depth recorded
        val hasDepth = phases.any { it.depthCm != null }

        val actionTime = phases.firstOrNull()?.actionTime ?: 15

        return SessionConfig(small, medium, large, xl, actionTime, recordDepth = hasDepth)
    }
}

/**
 * Wrapper class for export data with metadata.
 * [day0Date] is nullable for backward compatibility —
 * older exports that lack this field will deserialize it as null.
 */
data class MilestoneExport(
    val date: String,
    val comment: String = ""
)

data class TagExport(val id: String, val name: String, val color: String)

data class ExportData(
    val exportDate: String,
    val appVersion: String,
    val day0Date: String? = null,
    val milestones: List<MilestoneExport>? = null,
    val tags: List<TagExport>? = null,
    val sessions: List<SessionExport>
)

/**
 * Session with human-readable timestamp for export
 */
data class SessionExport(
    val id: String,
    val phases: List<PhaseData>,
    val totalSeconds: Long,
    val timestamp: String,
    val tagIds: List<String>? = null,
    val note: String? = null
)

// Gson sets missing fields to null even on non-nullable Kotlin types.
// This patches sessions deserialized from older JSON that predate tagIds/note.
@Suppress("SENSELESS_COMPARISON")
private fun Session.withNullDefaults() = copy(
    tagIds = if (tagIds == null) emptyList() else tagIds,
    note = if (note == null) "" else note
)

/**
 * Convert Session to SessionExport with ISO timestamp
 */
fun Session.toExport(): SessionExport {
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
    return SessionExport(
        id = id,
        phases = phases,
        totalSeconds = totalSeconds,
        timestamp = isoFormat.format(Date(timestamp)),
        tagIds = tagIds.ifEmpty { null },
        note = note.ifBlank { null }
    )
}

/**
 * Result of an import operation
 */
sealed class ImportResult {
    data class Success(val imported: Int, val skipped: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * Result of an export operation
 */
sealed class ExportResult {
    data class Success(val filename: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

fun calculateSimpleAverage(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    return values.average()
}

