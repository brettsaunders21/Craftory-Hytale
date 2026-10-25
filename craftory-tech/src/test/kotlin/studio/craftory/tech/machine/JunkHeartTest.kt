package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.multiblock.MultiblockGeometry
import studio.craftory.core.api.multiblock.MultiblockQuality
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.notebook.NotebookPages
import studio.craftory.tech.notebook.NotebookTierProgression

class JunkHeartTest {
    @Test
    fun `kick timing thresholds pick quality`() {
        assertEquals(MultiblockQuality.MASTERWORK, KickStartTiming.quality(0.0))
        assertEquals(MultiblockQuality.MASTERWORK, KickStartTiming.quality(0.15))
        assertEquals(MultiblockQuality.STURDY, KickStartTiming.quality(0.16))
        assertEquals(MultiblockQuality.STURDY, KickStartTiming.quality(0.5))
        assertEquals(MultiblockQuality.WOBBLY, KickStartTiming.quality(0.51))
    }

    @Test
    fun `kick phase cycles every three seconds around the sweet spot`() {
        assertEquals(1.5, KickStartTiming.phaseSeconds(30, 20), 1e-9)
        assertEquals(1.5, KickStartTiming.phaseSeconds(90, 20), 1e-9)
        assertEquals(0.0, KickStartTiming.errorSeconds(KickStartTiming.phaseSeconds(30, 20)), 1e-9)
        assertEquals(1.5, KickStartTiming.errorSeconds(KickStartTiming.phaseSeconds(0, 20)), 1e-9)
        assertTrue('^' in KickStartTiming.gauge(1.5))
    }

    @Test
    fun `quality multipliers`() {
        assertEquals(0.9, MultiblockQuality.WOBBLY.multiplier)
        assertEquals(1.0, MultiblockQuality.STURDY.multiplier)
        assertEquals(1.15, MultiblockQuality.MASTERWORK.multiplier)
    }

    @Test
    fun `heart produces only while formed and burning`() {
        val state = JunkHeartState()
        val fuel = JunkPowerFuels.definitions.first().itemId
        assertTrue(JunkHeartMachine.addFuel(state, fuel))
        assertFalse(JunkHeartMachine.addFuel(state, "Not_A_Fuel"))
        JunkHeartMachine.advanceTo(state, 10, MultiblockState.READY_TO_START)
        assertEquals(
            0,
            JunkHeartMachine.productionPerSolveTick(state, MultiblockState.READY_TO_START, MultiblockQuality.NONE),
        )
        assertEquals(1, state.fuelCount)
        JunkHeartMachine.advanceTo(state, 11, MultiblockState.FORMED)
        assertEquals(0, state.fuelCount)
        assertEquals(
            184,
            JunkHeartMachine.productionPerSolveTick(state, MultiblockState.FORMED, MultiblockQuality.MASTERWORK),
        )
        assertEquals(
            144,
            JunkHeartMachine.productionPerSolveTick(state, MultiblockState.FORMED, MultiblockQuality.WOBBLY),
        )
        JunkHeartMachine.advanceTo(state, 12, MultiblockState.DAMAGED)
        assertEquals(
            0,
            JunkHeartMachine.productionPerSolveTick(state, MultiblockState.DAMAGED, MultiblockQuality.MASTERWORK),
        )
    }

    @Test
    fun `junk heart template is a plated base with a chimney inside the footprint cap`() {
        val template = JunkHeartTemplate.template
        assertEquals(10, template.parts.size)
        assertEquals(8, template.parts.count { it.itemId == CraftoryIds.SCRAP_PLATING })
        assertEquals(3 to 3, MultiblockGeometry.footprint(template))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 2), template.hammerOrder.map { it.offset.y })
    }

    @Test
    fun `tier two needs every page and the junk heart`() {
        assertFalse(NotebookTierProgression.meetsTinkeredRequirements(NotebookPages.ids))
        assertFalse(NotebookTierProgression.meetsTinkeredRequirements(setOf(CraftoryIds.JUNK_HEART_KNOWLEDGE)))
        assertTrue(
            NotebookTierProgression.meetsTinkeredRequirements(NotebookPages.ids + CraftoryIds.JUNK_HEART_KNOWLEDGE),
        )
    }
}
