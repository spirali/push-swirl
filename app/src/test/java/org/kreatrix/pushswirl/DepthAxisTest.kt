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
    fun labelsCarryUnit() {
        assertEquals("14 cm", formatDepthTick(14f, DepthUnit.CM))
        assertEquals("5.25 in", formatDepthTick(5.25f, DepthUnit.INCH))
    }
}
