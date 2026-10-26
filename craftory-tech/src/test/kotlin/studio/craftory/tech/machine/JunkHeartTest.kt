package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    fun `boiler template is complete without overlapping offsets across rotations`() {
        val template = GutterBoilerTemplate.template
        assertEquals(7, template.parts.size)
        assertEquals(3, template.parts.count { it.itemId == CraftoryIds.SCRAP_PLATING })
        assertEquals(3, template.parts.count { it.itemId == CraftoryIds.BOILER_TANK })
        assertEquals(1, template.parts.count { it.itemId == CraftoryIds.BOILER_WHISTLE_STACK })
        assertEquals(setOf(0, 1), template.parts.map { it.offset.y }.toSet())
        assertEquals(2 to 2, MultiblockGeometry.footprint(template))
        assertEquals(
            template.parts.size,
            template.parts
                .map { it.offset }
                .toSet()
                .size,
        )
        for (rotation in 0..3) {
            val origin =
                studio.craftory.core.api
                    .BlockPos(10, 64, 10)
            val parts = MultiblockGeometry.worldParts(template, origin, rotation)
            assertEquals(template.parts.size, parts.map { it.position }.toSet().size)
            assertEquals(
                template.parts.map { MultiblockGeometry.worldPosition(origin, it.offset, rotation) }.toSet(),
                parts.map { it.position }.toSet(),
            )
        }
        assertNotEquals(
            MultiblockGeometry.worldPosition(
                studio.craftory.core.api
                    .BlockPos(10, 64, 10),
                template.parts.first().offset,
                0,
            ),
            MultiblockGeometry.worldPosition(
                studio.craftory.core.api
                    .BlockPos(10, 64, 10),
                template.parts.first().offset,
                1,
            ),
        )
    }

    @Test
    fun `gasbag template is complete without overlapping offsets across rotations`() {
        val template = GasbagTemplate.template
        assertEquals(11, template.parts.size)
        assertEquals(3, template.parts.count { it.itemId == CraftoryIds.GASBAG_FRAME })
        assertEquals(8, template.parts.count { it.itemId == CraftoryIds.GASBAG_ENVELOPE })
        assertEquals(setOf(0, 1, 2), template.parts.map { it.offset.y }.toSet())
        assertEquals(2 to 2, MultiblockGeometry.footprint(template))
        assertEquals(
            template.parts.size,
            template.parts
                .map { it.offset }
                .toSet()
                .size,
        )
        for (rotation in 0..3) {
            val origin =
                studio.craftory.core.api
                    .BlockPos(10, 64, 10)
            val parts = MultiblockGeometry.worldParts(template, origin, rotation)
            assertEquals(template.parts.size, parts.map { it.position }.toSet().size)
            assertEquals(
                template.parts.map { MultiblockGeometry.worldPosition(origin, it.offset, rotation) }.toSet(),
                parts.map { it.position }.toSet(),
            )
        }
        assertNotEquals(
            MultiblockGeometry.worldPosition(
                studio.craftory.core.api
                    .BlockPos(10, 64, 10),
                template.parts.first().offset,
                0,
            ),
            MultiblockGeometry.worldPosition(
                studio.craftory.core.api
                    .BlockPos(10, 64, 10),
                template.parts.first().offset,
                1,
            ),
        )
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
