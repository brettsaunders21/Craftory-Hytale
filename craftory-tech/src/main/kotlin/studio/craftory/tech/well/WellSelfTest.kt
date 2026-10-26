package studio.craftory.tech.well

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
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
        try {
            testJarAndScan(world, tempDir, report)
            testEmptyBlockRemoval(world, report)
            testStormcall(world, report)
            val remaining = CraftoryCore.wells().wellsNear(world.chunkStore.store, CAUGHT_POS, 64).size
            check(remaining == 0) { "well cleanup left $remaining indexed wells within radius 64" }
            report("selftest: cleanup (wells=$remaining)")
        } finally {
            CraftoryCore.wells().remove(world, CAUGHT_POS)
            CraftoryCore.wells().remove(world, FORCED_POS)
            CraftoryCore.wells().remove(world, BLOCK_REMOVED_POS)
            Files.deleteIfExists(tempDir.resolve("crews.json"))
            Files.deleteIfExists(tempDir)
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
        check(WellBirths.forceStormcall(world, FORCED_POS, Random(7)) != null) { "forced Stormcall failed" }
        report("selftest: PASS forced Stormcall")
        CraftoryCore.wells().remove(world, FORCED_POS)
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
