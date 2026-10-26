package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FillBandTest {
    @Test
    fun `fill bands use exact third thresholds and clamp endpoints`() {
        val capacity = 3_000L

        assertEquals(0, FillBand.of(0, capacity))
        assertEquals(1, FillBand.of(1, capacity))
        assertEquals(1, FillBand.of(capacity / 3 - 1, capacity))
        assertEquals(2, FillBand.of(capacity / 3, capacity))
        assertEquals(3, FillBand.of(2 * capacity / 3, capacity))
        assertEquals(3, FillBand.of(capacity - 1, capacity))
        assertEquals(4, FillBand.of(capacity, capacity))
        assertEquals(4, FillBand.of(capacity + 1, capacity))
        assertEquals(0, FillBand.of(1, 0))
    }

    @Test
    fun `fill band names match the five visual states`() {
        assertEquals("Empty", FillBand.stateName(0))
        assertEquals("Quarter", FillBand.stateName(1))
        assertEquals("Half", FillBand.stateName(2))
        assertEquals("ThreeQuarter", FillBand.stateName(3))
        assertEquals("Full", FillBand.stateName(4))
    }
}
