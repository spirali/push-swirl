package org.kreatrix.pushswirl

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SessionStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("pushswirl_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    fun saveSessions(sessions: List<Session>) {
        val json = gson.toJson(sessions)
        prefs.edit().putString("sessions", json).apply()
    }

    fun loadSessions(): List<Session> {
        val json = prefs.getString("sessions", null) ?: return emptyList()
        val type = object : TypeToken<List<Session>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addSession(session: Session) {
        val sessions = loadSessions().toMutableList()
        sessions.add(0, session)
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
            .putBoolean("notification_vibration", settings.vibrationEnabled)
            .putBoolean("notification_sound", settings.soundEnabled)
            .apply()
    }

    fun loadNotificationSettings(): NotificationSettings {
        val vibration = prefs.getBoolean("notification_vibration", true)
        val sound = prefs.getBoolean("notification_sound", true)
        return NotificationSettings(vibration, sound)
    }

    fun calculateStats(): SessionStats {
        val sessions = loadSessions()
        if (sessions.isEmpty()) {
            return SessionStats(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }

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

        return SessionStats(
            totalSessions = sessions.size,
            wmaSmallTTD = calculateWMA(smallTTDs),
            wmaMediumTTD = calculateWMA(mediumTTDs),
            wmaLargeTTD = calculateWMA(largeTTDs),
            wmaXlTTD = calculateWMA(xlTTDs),
            wmaSessionLength = calculateWMA(sessions.map { it.totalSeconds.toDouble() }.toList()),
        )
    }

    /**
     * Export all sessions to a JSON file and return the Uri for sharing.
     * Returns null if there are no sessions to export.
     */
    fun exportSessionsToFile(): Uri? {
        val sessions = loadSessions()
        if (sessions.isEmpty()) {
            return null
        }

        // ISO format for timestamps
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        // Create export data with metadata
        val exportData = ExportData(
            exportDate = isoFormat.format(Date()),
            appVersion = "1.0",
            totalSessions = sessions.size,
            sessions = sessions.map { it.toExport() }
        )

        val json = prettyGson.toJson(exportData)

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
}

/**
 * Wrapper class for export data with metadata
 */
data class ExportData(
    val exportDate: String,
    val appVersion: String,
    val totalSessions: Int,
    val sessions: List<SessionExport>
)

/**
 * Session with human-readable timestamp for export
 */
data class SessionExport(
    val id: String,
    val config: SessionConfig,
    val phases: List<PhaseData>,
    val totalSeconds: Long,
    val timestamp: String
)

/**
 * Convert Session to SessionExport with ISO timestamp
 */
fun Session.toExport(): SessionExport {
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
    return SessionExport(
        id = id,
        config = config,
        phases = phases,
        totalSeconds = totalSeconds,
        timestamp = isoFormat.format(Date(timestamp))
    )
}

fun calculateWMA(values: List<Double>): Double {
    if (values.isEmpty()) {
        return 0.0;
    }

    var weightedSum = 0.0
    var weightTotal = 0.0

    for (i in values.indices) {
        val weight = (i + 1).toDouble() // Weight: 1, 2, 3, ..., n
        weightedSum += values[i] * weight
        weightTotal += weight
    }

    return weightedSum / weightTotal
}