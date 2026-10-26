package studio.craftory.tech.well

import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.CraftorySelfTest
import kotlin.random.Random

internal object WellStormVaneSelfTest {
    fun run(
        world: com.hypixel.hytale.server.core.universe.world.World,
        report: (String) -> Unit,
    ) {
        val vane = WellSelfTest.VANE_POS
        val position = CraftorySelfTest.TestPosition(vane.x, vane.y, vane.z)
        val store = world.chunkStore.store
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?: error("Storm Vane test section is not loaded")
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: error("Storm Vane test section is missing")
        CraftorySelfTest.place(world, store, sectionRef, section, CraftoryIds.STORM_VANE, position, StormVaneState.type)
        val ref =
            BlockModule.getBlockEntity(world, position.x, position.y, position.z)
                ?: error("Storm Vane test block entity is missing")
        WellSelfTestExclusions.registerBlock(ref)
        try {
            val vaneCount = WellBirths.stormVanesWithin(world, vane, 64)
            val seed = (0..10_000).first { Random(it).nextDouble() in 0.15..<0.25 }
            val roll = Random(seed).nextDouble()
            check(vaneCount == 1) { "Storm Vane count was $vaneCount" }
            check(
                StormHuntRules.stormStrength(0, roll) == WellStrength.FLICKER &&
                    StormHuntRules.stormStrength(vaneCount, roll) == WellStrength.STEADY,
            ) { "seeded Storm Vane roll did not raise strength" }
            report("selftest: PASS Storm Vane seeded strength bonus")
        } finally {
            WellSelfTestExclusions.releaseBlock(ref)
            CraftorySelfTest.clear(world, sectionRef, listOf(position))
        }
    }
}
