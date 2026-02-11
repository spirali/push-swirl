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

    fun getLastDepthForSize(size: PhaseSize): Float {
        // Get the last recorded depth from sessions that have depth recorded
        val sessions = loadSessions()
        val lastPhaseWithDepth = sessions
            .flatMap { it.phases }
            .filter { it.size == size && it.depthCm != null }
            .firstOrNull()

        return lastPhaseWithDepth?.depthCm ?: 14f
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

    fun exportSessionsToJson(): String {
        val sessions = loadSessions()

        // ISO format for timestamps
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        // Create export data with metadata
        val exportData = ExportData(
            exportDate = isoFormat.format(Date()),
            appVersion = getAppVersion(),
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
                        timestamp = timestamp
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

            ImportResult.Success(importedCount, skippedCount)
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}")
        }
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

        return SessionConfig(small, medium, large, xl, recordDepth = hasDepth)
    }
}

/**
 * Wrapper class for export data with metadata
 */
data class ExportData(
    val exportDate: String,
    val appVersion: String,
    val sessions: List<SessionExport>
)

/**
 * Session with human-readable timestamp for export
 */
data class SessionExport(
    val id: String,
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
        phases = phases,
        totalSeconds = totalSeconds,
        timestamp = isoFormat.format(Date(timestamp))
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