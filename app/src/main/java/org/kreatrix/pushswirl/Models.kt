package org.kreatrix.pushswirl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class PhaseSize {
    SMALL, MEDIUM, LARGE, XL
}

enum class PhaseDuration(val minutes: Int) {
    SKIP(0),
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    THIRTY(30)
}

enum class DilationAction {
    PUSH, SWIRL
}

@Parcelize
data class SessionConfig(
    val small: PhaseDuration = PhaseDuration.SKIP,
    val medium: PhaseDuration = PhaseDuration.FIFTEEN,
    val large: PhaseDuration = PhaseDuration.TEN,
    val xl: PhaseDuration = PhaseDuration.SKIP,
    val actionTime: Int = 15,
    val recordDepth: Boolean = false
) : Parcelable {
    fun getDuration(size: PhaseSize): PhaseDuration {
        return when (size) {
            PhaseSize.SMALL -> small
            PhaseSize.MEDIUM -> medium
            PhaseSize.LARGE -> large
            PhaseSize.XL -> xl
        }
    }

    fun getActivePhases(): List<PhaseSize> {
        return PhaseSize.entries.filter { getDuration(it) != PhaseDuration.SKIP }
    }
}

@Parcelize
data class PhaseData(
    val size: PhaseSize,
    val ttdSeconds: Long,
    val dilationMinutes: Int,
    // Nullable for backward compatibility with old sessions that predate action time config
    val actionTime: Int? = null,
    // Nullable for backward compatibility with old logs
    // If not null, the phase was finished early at this many seconds remaining
    val earlyFinishSecondsRemaining: Int? = null,
    // Nullable when depth recording is disabled or for backward compatibility
    // Depth in centimeters
    val depthCm: Float? = null
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
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

data class SessionStats(
    val smallTTD: Double,
    val mediumTTD: Double,
    val largeTTD: Double,
    val xlTTD: Double,
    val sessionLength: Double,
    val totalSessions: Int,
    val avgTimeBetweenSessions: Double  // in seconds; 0 if fewer than 2 sessions
)

enum class StatsTimeInterval(val label: String, val days: Int?) {
    DAYS_2("2d", 2),
    DAYS_7("7d", 7),
    DAYS_14("14d", 14),
    DAYS_30("30d", 30),
    MONTHS_3("3mo", 90),
    MONTHS_6("6mo", 180),
    ALL("All", null)
}

data class Milestone(
    val date: Long,
    val comment: String = ""
)

data class NotificationSettings(
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    // null = use current system volume; 0f..1f = fraction of hardware max volume
    val volumeLevel: Float? = null
)
