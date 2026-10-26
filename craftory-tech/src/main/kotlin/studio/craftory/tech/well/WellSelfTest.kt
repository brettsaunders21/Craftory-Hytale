package studio.craftory.tech.well

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.CraftorySelfTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.random.Random

internal object WellSelfTest {
    private val CAUGHT_POS = BlockPos(10_030, 148, 10_010)
    private val FORCED_POS = BlockPos(10_029, 148, 10_010)
    private val BLOCK_REMOVED_POS = BlockPos(10_028, 148, 10_010)

    fun run(
        world: World,
        report: (String) -> Unit,
    ) {
        val tempDir = Files.createTempDirectory("craftory-well-selftest")
        listOf(CAUGHT_POS, FORCED_POS, BLOCK_REMOVED_POS).forEach(WellSelfTestExclusions::registerWell)
        try {
            testJarAndScan(world, tempDir, report)
            testEmptyBlockRemoval(world, report)
            testStormcall(world, report)
            testChunkReload(world, report)
            val remaining = CraftoryCore.wells().wellsNear(world.chunkStore.store, CAUGHT_POS, 64).size
            check(remaining == 0) { "well cleanup left $remaining indexed wells within radius 64" }
            report("selftest: cleanup (wells=$remaining)")
        } finally {
            CraftoryCore.wells().remove(world, CAUGHT_POS)
            CraftoryCore.wells().remove(world, FORCED_POS)
            CraftoryCore.wells().remove(world, BLOCK_REMOVED_POS)
            Files.deleteIfExists(tempDir.resolve("crews.json"))
            Files.deleteIfExists(tempDir)
            listOf(CAUGHT_POS, FORCED_POS, BLOCK_REMOVED_POS).forEach(WellSelfTestExclusions::releaseWell)
            val exclusions = WellSelfTestExclusions.cleanupCounts()
            val guardians =
                world.entityStore.store
                    .getResource(WellGuardianIndex.type)
                    .refsByWellId.size
            report(
                "selftest: cleanup (wellBlocks=${exclusions.first}, wellEntities=${exclusions.second}, " +
                    "wellPositions=${exclusions.third}, guardians=$guardians)",
            )
        }
    }

    private fun testJarAndScan(
        world: World,
        tempDir: Path,
        report: (String) -> Unit,
    ) {
        val store = world.chunkStore.store
        val spawned =
            CraftoryCore.wells().spawn(
                world,
                CAUGHT_POS,
                Temperament.SPARK,
                WellStrength.FLICKER,
                WellState.WILD,
            ) ?: error("well spawn failed")
        check(CraftoryCore.wells().count(store) > 0) { "well index did not register spawn" }
        check(ScanScoring.score(100, listOf(100, 100, 100), 40, 8).perfect) { "perfect score failed" }
        check(!ScanScoring.score(100, listOf(110, 125, 135), 40, 8).perfect) { "sloppy score failed" }
        report("selftest: PASS well spawn/index and scan scores")

        val player = UUID.randomUUID()
        val crews = CrewService(tempDir.resolve("crews.json"))
        try {
            val crew = crews.crewOf(player)
            crews.setTier(crew.id, 2)
            val well = store.getComponent(spawned, Glimmerwell.type) ?: error("spawned well is missing")
            well.state = WellState.TETHERED
            well.claimCrewId = crew.id
            val result = JarActions.checkCatch(spawned, player, crews, store)
            check(result is JarCatchResult.Caught) { "Flicker catch failed: $result" }
            check(JarActions.commitCatch(world, spawned, CAUGHT_POS, player, result, crews)) {
                "Flicker catch commit failed"
            }
            check(crews.insight(crew.id)[Temperament.SPARK] == 15L) { "Flicker Insight missing" }
            check(crews.hasKnowledge(player, JarActions.FIRST_FLICKER_ID)) { "first Flicker knowledge missing" }
            val filled = JarActions.filledJar(result)
            check(filled.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT) == "spark") {
                "filled jar metadata missing"
            }
            check(filled.getFromMetadataOrNull(GobJarMetadata.STRENGTH) == WellStrength.FLICKER.name) {
                "filled jar strength metadata missing"
            }
            check(filled.getFromMetadataOrNull(GobJarMetadata.TASTE) == "Ingredient_Scraps") {
                "filled jar taste metadata missing"
            }
            check(CraftoryCore.wells().count(store) == 0) { "jarred well remained indexed" }
            report("selftest: PASS Flicker jar metadata, knowledge, and Insight")
        } finally {
            crews.close()
        }
    }

    private fun testEmptyBlockRemoval(
        world: World,
        report: (String) -> Unit,
    ) {
        val store = world.chunkStore.store
        CraftoryCore.wells().spawn(
            world,
            BLOCK_REMOVED_POS,
            Temperament.GROWTH,
            WellStrength.STEADY,
            WellState.WILD,
        ) ?: error("block-removal well spawn failed")
        val before = CraftoryCore.wells().wellsNear(store, BLOCK_REMOVED_POS, 64).size
        check(before > 0) { "block-removal well was not indexed" }
        clearBlock(world, BLOCK_REMOVED_POS)
        val after = CraftoryCore.wells().wellsNear(store, BLOCK_REMOVED_POS, 64).size
        check(after == before - 1) { "empty-block removal did not drop the well index count: $before -> $after" }
        report("selftest: PASS empty-block removal clears well index")
    }

    private fun testStormcall(
        world: World,
        report: (String) -> Unit,
    ) {
        val ref = WellBirths.forceStormcall(world, FORCED_POS, Random(7)) ?: error("forced Stormcall failed")
        val well = world.chunkStore.store.getComponent(ref, Glimmerwell.type) ?: error("forced well state is missing")
        check(
            well.temperament == Temperament.SPARK && well.state == WellState.WILD,
        ) { "forced Stormcall did not create a Wild Spark Well" }
        val expectedExpiry = world.tick + 30L * 20L
        check(
            world.chunkStore.store
                .getResource(WellBirthState.type)
                .stormCrackles[FORCED_POS.pack()] == expectedExpiry,
        ) { "forced Stormcall did not start 30-second crackle tracking" }
        report("selftest: PASS forced Stormcall birth, fake strike, and crackle")
        CraftoryCore.wells().remove(world, FORCED_POS)
    }

    private fun testChunkReload(
        world: World,
        report: (String) -> Unit,
    ) {
        val position = CraftorySelfTest.TestPosition(10_020, 148, 10_015)
        val store = world.chunkStore.store
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?: error("Vane persistence section is not loaded")
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: error("Vane persistence section is missing")
        CraftorySelfTest.place(
            world,
            store,
            sectionRef,
            section,
            CraftoryIds.STORM_VANE,
            position,
            StormVaneState.type,
        )
        var ref =
            BlockModule.getBlockEntity(world, position.x, position.y, position.z)
                ?: error("Vane persistence block entity is missing")
        WellSelfTestExclusions.registerBlock(ref)
        try {
            val state = store.getComponent(ref, StormVaneState.type) ?: error("Vane state is missing")
            state.visualOn = true
            state.lastUpdateTick = world.tick
            store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
            val unloaded = store.removeEntity(ref, RemoveReason.UNLOAD)
            ref = store.addEntity(unloaded, AddReason.LOAD) ?: error("Vane reload failed")
            WellSelfTestExclusions.registerBlock(ref)
            val restored = store.getComponent(ref, StormVaneState.type)
            check(
                restored?.visualOn == true && restored.lastUpdateTick == world.tick,
            ) { "Storm Vane chunk unload/reload lost component state" }
            report("selftest: PASS Storm Vane chunk unload/reload persists state")
        } finally {
            WellSelfTestExclusions.releaseBlock(ref)
            CraftorySelfTest.clear(world, sectionRef, listOf(position))
        }
    }

    private fun clearBlock(
        world: World,
        pos: BlockPos,
    ) {
        val section =
            world.chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z)
                ?: error("test block section is not loaded at $pos")
        check(
            BlockOperations.setBlock(
                world.chunkStore,
                section,
                pos.x,
                pos.y,
                pos.z,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            ),
        ) { "could not clear test block at $pos" }
    }
}
