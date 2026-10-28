package org.kreatrix.pushswirl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthAxisTest {

    @Test
    fun tickCountIsReasonableAndLabelsUniqueForBothUnits() {
        for (unit in DepthUnit.entries) {
            var max = 0.3f
            while (max < 45f) {
                val (top, ticks) = niceYTicks(
                    max, { formatDepthTick(it, unit) }, stepCandidates = depthStepCandidates(unit)
                )
                val labels = ticks.map { formatDepthTick(it, unit) }
                assertTrue("$unit max=$max ticks=${ticks.size}", ticks.size in 2..8)
                assertEquals("$unit max=$max", labels.size, labels.toSet().size)
                assertTrue("$unit max=$max", top >= max)
                max *= 1.05f
            }
        }
    }

    @Test
    fun axisStartsNearMinimumWhenGiven() {
        for (unit in DepthUnit.entries) {
            for ((min, max) in listOf(12f to 15f, 9.5f to 10f, 14f to 14f, 0.5f to 20f, 5.6f to 9.8f, 17.3f to 31f)) {
                val (top, ticks) = niceYTicks(
                    max, { formatDepthTick(it, unit) },
                    stepCandidates = depthStepCandidates(unit), dataMin = min
                )
                assertTrue("$unit $min..$max ticks=$ticks", ticks.size in 2..8)
                // Bottom is the minimum rounded down to a whole unit
                assertEquals("$unit $min..$max", kotlin.math.floor(min), ticks.first(), 1e-3f)
                assertTrue("$unit $min..$max top=$top", top >= max - 1e-3f)
            }
        }
    }

    @Test
    fun labelsCarryUnit() {
        assertEquals("14 cm", formatDepthTick(14f, DepthUnit.CM))
        assertEquals("5.25 in", formatDepthTick(5.25f, DepthUnit.INCH))
    }
}
