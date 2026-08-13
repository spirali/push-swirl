package org.kreatrix.pushswirl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class TagColor { RED, ORANGE, GREEN, BLUE }

@Parcelize
data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: TagColor
) : Parcelable

enum class PhaseDuration(val minutes: Int) {
    SKIP(0),
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    TWENTY_FIVE(25),
    THIRTY(30),
    FORTY(40),
    FIFTY(50),
    SIXTY(60)
}

enum class DilationAction {
    PUSH, SWIRL
}

// Default values shipped in the app
object BuiltInSizeIds {
    const val SMALL = "builtin-small"
    const val MEDIUM = "builtin-medium"
    const val LARGE = "builtin-large"
    const val XL = "builtin-xl"
}

@Parcelize
data class PhaseSize(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val durations: List<PhaseDuration> = PhaseDuration.entries.filter { it != PhaseDuration.SKIP }
) : Parcelable

@Parcelize
data class SessionConfig(
    val phaseDurations: Map<String, PhaseDuration> = emptyMap(),
    val actionTime: Int = 0,
    val recordDepth: Boolean = false,
    val addTagsNoteAtEnd: Boolean = false,
    val blindedTtdTimer: Boolean = false,
    // Rest inserted after each push/swirl switch, in seconds. 0 = no pause.
    val pauseSeconds: Int = 0
) : Parcelable {
    fun getDuration(sizeId: String): PhaseDuration {
        return phaseDurations[sizeId] ?: PhaseDuration.SKIP
    }

    fun getActivePhases(sizes: List<PhaseSize>): List<PhaseSize> {
        return sizes.filter { getDuration(it.id) != PhaseDuration.SKIP }
    }
}

@Parcelize
data class PhaseData(
    val sizeId: String = "",
    val sizeName: String = "",
    val ttdSeconds: Long,
    val dilationMinutes: Int,
    // Nullable for backward compatibility with old sessions that predate action time config
    val actionTime: Int? = null,
    // Nullable for backward compatibility with old logs
    // If not null, the phase was finished early at this many seconds remaining
    val earlyFinishSecondsRemaining: Int? = null,
    // Nullable when depth recording is disabled or for backward compatibility
    // Depth in centimeters
    val depthCm: Float? = null,
    // Inter-action pause (seconds) used during this phase; null for older records / when disabled
    val pauseSeconds: Int? = null
) : Parcelable {
    // Helper to check if phase was finished early
    val wasFinishedEarly: Boolean
        get() = earlyFinishSecondsRemaining != null && earlyFinishSecondsRemaining > 0

    // Actual dilation time in seconds (planned minus remaining if early finish)
    val actualDilationSeconds: Int
        get() = if (earlyFinishSecondsRemaining != null) {
            (dilationMinutes * 60) - earlyFinishSecondsRemaining
        } else {
            dilationMinutes * 60
        }
}

@Parcelize
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val config: SessionConfig,
    val phases: List<PhaseData>,
    val totalSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val tagIds: List<String> = emptyList(),
    val note: String = "",
    // Start time of the session; null for records saved before this field existed
    val startTimestamp: Long? = null
) : Parcelable

data class SessionStats(
    val ttdBySizeId: Map<String, Double>,
    val sessionLength: Double,
    val totalSessions: Int,
    val avgTimeBetweenSessions: Double  // in seconds; 0 if fewer than 2 sessions
)


data class Milestone(
    val date: Long,
    val comment: String = ""
)

enum class SoundMode { OFF, SYSTEM, MANUAL }
enum class VibrationMode { OFF, SYSTEM, MANUAL }

data class NotificationSettings(
    val soundMode: SoundMode = SoundMode.SYSTEM,
    val vibrationMode: VibrationMode = VibrationMode.SYSTEM,
    val volumeLevel: Float = 0.5f,
    val vibrationAmplitude: Float = 1.0f,
    val switchBeepsEnabled: Boolean = true,
    val phaseFanfareEnabled: Boolean = true,
    // Custom sound URIs (content:// from the file picker); null = use the built-in default.
    val pushSoundUri: String? = null,
    val swirlSoundUri: String? = null,
    val phaseEndSoundUri: String? = null,
    val pauseSoundUri: String? = null
) {
    val soundEnabled: Boolean get() = soundMode != SoundMode.OFF
    val vibrationEnabled: Boolean get() = vibrationMode != VibrationMode.OFF
}

data class ActiveSessionSnapshot(
    val sessionId: String,
    val sessionConfig: SessionConfig,
    val completedPhases: List<PhaseData>,
    val currentPhaseIndex: Int,
    val stateType: String,            // "TTD", "DEPTH_INPUT", "DILATION"
    val currentPhaseSizeId: String,   // PhaseSize id
    val sessionStartWallClock: Long,  // System.currentTimeMillis() at session start
    val saveWallClock: Long,          // System.currentTimeMillis() when snapshot was saved
    val ttdElapsedMs: Long,           // total accumulated TTD ms at save time
    val ttdRunning: Boolean,
    val lastTtdSeconds: Long,
    val dilationTotalSeconds: Int,
    val dilationElapsedMs: Long,      // total elapsed dilation ms at save time
    val dilationPaused: Boolean,
    val earlyFinishSecondsRemaining: Int?,
    val currentPhaseDepth: Float?,
    val sessionEndWallClock: Long = 0L  // set only for DEPTH_INPUT state
)
