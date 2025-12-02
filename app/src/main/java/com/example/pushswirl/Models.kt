package org.kreatrix.pushswirl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class PhaseSize {
    SMALL, MEDIUM, LARGE, XL
}

enum class PhaseDuration(val minutes: Int) {
    SKIP(0),
    FIVE(5),
    TEN(10),
    FIFTEEN(15)
}

enum class DilationAction {
    PUSH, SWIRL
}

@Parcelize
data class SessionConfig(
    val small: PhaseDuration = PhaseDuration.SKIP,
    val medium: PhaseDuration = PhaseDuration.FIFTEEN,
    val large: PhaseDuration = PhaseDuration.TEN,
    val xl: PhaseDuration = PhaseDuration.SKIP
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
    val dilationMinutes: Int
) : Parcelable

@Parcelize
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val config: SessionConfig,
    val phases: List<PhaseData>,
    val totalSeconds: Long,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

data class SessionStats(
    val wmaSmallTTD: Double,
    val wmaMediumTTD: Double,
    val wmaLargeTTD: Double,
    val wmaXlTTD: Double,
    val wmaSessionLength: Double,
    val totalSessions: Int
)

data class NotificationSettings(
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true
)