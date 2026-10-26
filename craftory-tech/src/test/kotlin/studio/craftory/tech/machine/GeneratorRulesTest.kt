package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratorRulesTest {
    @Test
    fun `rattlewheel water and wind rates are bounded and mutually exclusive`() {
        assertEquals(0L, rattlewheelOutput(0, false, 100, 1.0, 1.0))
        assertEquals(2L, rattlewheelOutput(1, false, 200, 1.0, 1.0))
        assertEquals(10L, rattlewheelOutput(5, true, 200, 1.0, 1.0))
        assertEquals(3L, rattlewheelOutput(0, true, 110, 1.0, 1.0))
        assertEquals(8L, rattlewheelOutput(0, true, 230, 1.0, 1.0))
    }

    @Test
    fun `rattlewheel applies weather and speed before flooring`() {
        assertEquals(6L, rattlewheelOutput(2, false, 110, 1.5, 1.0))
        assertEquals(4L, rattlewheelOutput(2, false, 110, 1.0, 1.2))
        assertEquals(10L, rattlewheelOutput(6, false, 110, 1.0, 1.0))
        assertEquals(0L, rattlewheelOutput(2, false, 110, -1.0, 1.0))
    }

    @Test
    fun `rattlewheel distributes per second rate across five solve ticks`() {
        assertEquals(listOf(1L, 1L, 0L, 0L, 0L), (0L..16L step 4).map { rattlewheelProductionPerSolveTick(2, it) })
        assertEquals(3L, rattlewheelProductionPerSolveTick(12, 0))
        assertEquals(3L, rattlewheelProductionPerSolveTick(12, 4))
        assertEquals(2L, rattlewheelProductionPerSolveTick(12, 8))
    }

    @Test
    fun `shroom growth scales by density and pauses in light`() {
        assertEquals(0.0, shroomGrowth(false, 8))
        assertEquals(10.0, shroomGrowth(true, 0))
        assertEquals(22.0, shroomGrowth(true, 8))
        assertEquals(10.0, shroomGrowth(true, -1))
    }

    @Test
    fun `darkness prefers available light and falls back to night or cover`() {
        assertTrue(resolveDarkness(3, 2, false, false))
        assertFalse(resolveDarkness(8, 9, true, true))
        assertTrue(resolveDarkness(null, null, true, false))
        assertTrue(resolveDarkness(null, null, false, true))
        assertFalse(resolveDarkness(null, null, false, false))
    }

    @Test
    fun `twitch combines stimuli scales density and decays when calm`() {
        assertEquals(5, twitchDelta(true, false, 0))
        assertEquals(10, twitchDelta(false, true, 0))
        assertEquals(15, twitchDelta(true, true, 0))
        assertEquals(45, twitchDelta(true, true, 8))
        assertEquals(-3, twitchDelta(false, false, 8))
    }

    @Test
    fun `pop chance has strict threshold and clamps twitch`() {
        assertFalse(shouldPop(79, 0.0))
        assertFalse(shouldPop(80, 0.0))
        assertTrue(shouldPop(90, 0.09))
        assertFalse(shouldPop(90, 0.10))
        assertTrue(shouldPop(120, 0.19))
        assertFalse(shouldPop(120, 0.20))
    }
}
