package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.craftory.tech.CraftoryIds

class BuildableMachinesTest {
    @Test
    fun `tier requirements allow items at their required tier`() {
        assertEquals(2, BuildableMachines.missingTier(CraftoryIds.HURL_TUBE, 1))
        assertEquals(null, BuildableMachines.missingTier(CraftoryIds.HURL_TUBE, 2))
        assertEquals(3, BuildableMachines.missingTier(CraftoryIds.TETHER_STAKE, 2))
        assertEquals(null, BuildableMachines.missingTier(CraftoryIds.TETHER_STAKE, 3))
        assertEquals(3, BuildableMachines.missingTier(CraftoryIds.GLIMMER_LENS, 2))
        assertEquals(3, BuildableMachines.missingTier(CraftoryIds.CODEX, 2))
        assertEquals(null, BuildableMachines.missingTier(CraftoryIds.SLOSHTANK, 1))
    }
}
