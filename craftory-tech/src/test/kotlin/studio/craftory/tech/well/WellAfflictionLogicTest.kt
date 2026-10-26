package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.well.WellStrength

class WellAfflictionLogicTest {
    @Test
    fun `corruption cleanses on nearby aligned wells or confirmed moth deaths`() {
        assertTrue(WellAfflictionRules.shouldCleanse(true, hasNearbyAlignedWell = true, mothsConfirmedDead = false))
        assertTrue(WellAfflictionRules.shouldCleanse(true, hasNearbyAlignedWell = false, mothsConfirmedDead = true))
        assertFalse(WellAfflictionRules.shouldCleanse(false, hasNearbyAlignedWell = true, mothsConfirmedDead = true))
        assertFalse(WellAfflictionRules.shouldCleanse(true, hasNearbyAlignedWell = false, mothsConfirmedDead = false))
    }

    @Test
    fun `siphoning lowers a well by one strength band but never below flicker`() {
        assertEquals(WellStrength.ROARING, WellAfflictionRules.strengthAfterSiphon(WellStrength.PRIMAL))
        assertEquals(WellStrength.STEADY, WellAfflictionRules.strengthAfterSiphon(WellStrength.ROARING))
        assertEquals(WellStrength.FLICKER, WellAfflictionRules.strengthAfterSiphon(WellStrength.STEADY))
        assertEquals(WellStrength.FLICKER, WellAfflictionRules.strengthAfterSiphon(WellStrength.FLICKER))
    }

    @Test
    fun `siphon progress pauses while owner crew is offline`() {
        assertEquals(12, SiphonProgressRules.nextElapsedSeconds(12, ownerCrewOnline = false))
        assertEquals(13, SiphonProgressRules.nextElapsedSeconds(12, ownerCrewOnline = true))
    }
}
