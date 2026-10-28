package org.kreatrix.pushswirl

import org.junit.Assert.assertEquals
import org.junit.Test

class DepthUnitTest {

    @Test
    fun inchValuesWithTwoDecimalsRoundTrip() {
        for (hundredths in 4..3933) {
            val typed = hundredths / 100.0
            val cm = DepthUnit.INCH.toCm(typed)
            assertEquals("typed $typed in", typed, DepthUnit.INCH.fromCm(cm), 0.0)
        }
    }

    @Test
    fun cmValuesWithOneDecimalRoundTrip() {
        for (tenths in 1..999) {
            val typed = tenths / 10.0
            val cm = DepthUnit.CM.toCm(typed)
            assertEquals("typed $typed cm", typed, DepthUnit.CM.fromCm(cm), 0.0)
        }
    }

    @Test
    fun twoInchesIsShownAsTwo() {
        val cm = DepthUnit.INCH.toCm(2.0)
        assertEquals("2.00 in", DepthUnit.INCH.format(cm))
        assertEquals("2", DepthUnit.INCH.inputText(cm))
    }

    @Test
    fun formatMatchesLegacyCmDisplay() {
        assertEquals("14 cm", DepthUnit.CM.format(14f))
        assertEquals("14.5 cm", DepthUnit.CM.format(14.5f))
        assertEquals("5.51 in", DepthUnit.INCH.format(14f))
    }
}
