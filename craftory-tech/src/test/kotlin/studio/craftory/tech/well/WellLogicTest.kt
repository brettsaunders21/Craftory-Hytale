package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.well.WellStrength
import kotlin.random.Random

class WellLogicTest {
    @Test
    fun `scan scoring distinguishes perfect and sloppy rings`() {
        val perfect = ScanScoring.score(100, listOf(100, 100, 100), 40, 8)
        val sloppy = ScanScoring.score(100, listOf(110, 125, 135), 40, 8)
        assertTrue(perfect.perfect)
        assertEquals(listOf(0L, 0L, 0L), perfect.errors)
        assertFalse(sloppy.perfect)
        assertEquals(listOf(10L, 25L, 25L), sloppy.errors)
    }

    @Test
    fun `insight values use strength and perfect well bonus`() {
        assertEquals(20L, InsightRules.well(WellStrength.FLICKER, true, false))
        assertEquals(30L, InsightRules.well(WellStrength.FLICKER, true, true))
        assertEquals(2L, InsightRules.well(WellStrength.FLICKER, false, true))
    }

    @Test
    fun `repeat scans grant ten percent with minimum one insight`() {
        assertEquals(4L, InsightRules.well(WellStrength.STEADY, false, false))
        assertEquals(1L, InsightRules.mob(false))
        assertEquals(1L, InsightRules.oreCrystal(false))
        assertEquals(1L, InsightRules.machine(false, null))
    }

    @Test
    fun `birth rules support deterministic forced outcomes`() {
        val rules = WellBirthRules(Random(42))
        assertTrue(rules.stormcall(1.0))
        assertFalse(rules.rainFlicker(0.0))
        assertTrue(rules.surfaceOffset() in 8..24)
    }
}
