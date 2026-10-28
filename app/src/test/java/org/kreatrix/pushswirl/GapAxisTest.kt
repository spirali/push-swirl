package org.kreatrix.pushswirl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GapAxisTest {

    /** Numeric value a label stands for, in hours. */
    private fun labelHours(label: String): Float = when {
        label == "0" -> 0f
        label.endsWith("d") -> label.dropLast(1).toFloat() * 24f
        label.endsWith("h") -> label.dropLast(1).toFloat()
        else -> label.dropLast(1).toFloat() / 60f
    }

    @Test
    fun everyTickLabelMatchesItsValue() {
        var maxH = 0.3f
        while (maxH < 2000f) {
            val inDays = maxH >= 60f
            val candidates = if (inDays) GAP_STEPS_DAYS_H else GAP_STEPS_HOURS_H
            val formatter = { h: Float -> formatGapAxis(h, inDays) }
            val (_, ticks) = niceYTicks(maxH, formatter, stepCandidates = candidates)
            for (t in ticks) {
                assertEquals("max=$maxH tick=$t", t, labelHours(formatter(t)), 0.001f)
            }
            maxH *= 1.07f
        }
    }

    @Test
    fun noMixedHourAndDayLabels() {
        var maxH = 0.3f
        while (maxH < 2000f) {
            val inDays = maxH >= 60f
            val candidates = if (inDays) GAP_STEPS_DAYS_H else GAP_STEPS_HOURS_H
            val (_, ticks) = niceYTicks(maxH, { formatGapAxis(it, inDays) }, stepCandidates = candidates)
            if (inDays) assertTrue("max=$maxH", ticks.drop(1).all { formatGapAxis(it, true).endsWith("d") })
            else assertTrue("max=$maxH", ticks.none { formatGapAxis(it, false).endsWith("d") })
            maxH *= 1.07f
        }
    }
}
