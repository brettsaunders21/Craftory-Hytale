package studio.craftory.tech.well

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.world.World
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import java.nio.file.Files
import java.util.UUID
import kotlin.random.Random

internal object WellSelfTest {
    fun run(
        world: World,
        report: (String) -> Unit,
    ) {
        val store = world.chunkStore.store
        val caughtPos = BlockPos(10_030, 148, 10_010)
        val forcedPos = BlockPos(10_029, 148, 10_010)
        val tempDir = Files.createTempDirectory("craftory-well-selftest")
        try {
            val spawned =
                CraftoryCore.wells().spawn(
                    world,
                    caughtPos,
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
                val result = JarActions.catchFlicker(world, spawned, caughtPos, player, crews, store)
                check(result is JarCatchResult.Caught) { "Flicker catch failed: $result" }
                check(crews.insight(crew.id)[Temperament.SPARK] == 15L) { "Flicker Insight missing" }
                check(crews.hasKnowledge(player, JarActions.FIRST_FLICKER_ID)) { "first Flicker knowledge missing" }
                val filled =
                    ItemStack(CraftoryIds.GOB_JAR_FILLED, 1)
                        .withMetadata(GobJarMetadata.TEMPERAMENT, Temperament.SPARK.id)
                        .withMetadata(GobJarMetadata.STRENGTH, WellStrength.FLICKER.name)
                        .withMetadata(GobJarMetadata.TASTE, "Ingredient_Scraps")
                check(filled.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT) == "spark") {
                    "filled jar metadata missing"
                }
                check(CraftoryCore.wells().count(store) == 0) { "jarred well remained indexed" }
                report("selftest: PASS Flicker jar metadata, knowledge, and Insight")
            } finally {
                crews.close()
            }

            check(WellBirths.forceStormcall(world, forcedPos, Random(7)) != null) { "forced Stormcall failed" }
            report("selftest: PASS forced Stormcall")
            CraftoryCore.wells().remove(world, forcedPos)
            check(CraftoryCore.wells().count(store) == 0) { "well cleanup left indexed wells" }
            report("selftest: PASS cleanup wells=0")
        } finally {
            CraftoryCore.wells().remove(world, caughtPos)
            CraftoryCore.wells().remove(world, forcedPos)
            Files.deleteIfExists(tempDir.resolve("crews.json"))
            Files.deleteIfExists(tempDir)
        }
    }
}
