package studio.craftory.tech.well

import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import java.util.UUID

internal object Slice5bPersistenceSelfTest {
    private val claimCrew = UUID.fromString("00000000-0000-0000-0000-000000000002")

    fun run(
        world: World,
        position: BlockPos,
        complete: (Boolean, Boolean, String) -> Unit,
    ) {
        val tappedPosition = BlockPos(position.x + 8, position.y, position.z)
        val corruptedPosition = BlockPos(position.x + 12, position.y, position.z)

        fun finish(
            tappedPassed: Boolean,
            corruptedPassed: Boolean,
            detail: String,
        ) {
            WellSelfTestExclusions.releaseWell(tappedPosition)
            WellSelfTestExclusions.releaseWell(corruptedPosition)
            complete(tappedPassed, corruptedPassed, detail)
        }
        val tapped =
            runCatching { prepare(world, tappedPosition, WellState.TAPPED, false) }
                .getOrElse { failure ->
                    finish(false, false, "TAPPED fixture setup failed (${failure.message})")
                    return
                }
        WellPersistenceSelfTest.saveAndUnload(
            tapped,
            complete = { tappedPassed, tappedDetail ->
                if (!tappedPassed) {
                    finish(false, false, tappedDetail)
                    return@saveAndUnload
                }
                val corrupted =
                    runCatching { prepare(world, corruptedPosition, WellState.CORRUPTED, true) }
                        .getOrElse { failure ->
                            finish(true, false, "Corrupted fixture setup failed (${failure.message})")
                            return@saveAndUnload
                        }
                WellPersistenceSelfTest.saveAndUnload(
                    corrupted,
                    complete = { corruptedPassed, corruptedDetail ->
                        finish(true, corruptedPassed, "$tappedDetail; $corruptedDetail")
                    },
                )
            },
        )
    }

    private fun prepare(
        world: World,
        position: BlockPos,
        state: WellState,
        corrupted: Boolean,
    ): WellPersistenceSelfTest.Fixture {
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?: error("Slice 5b persistence section is not loaded")
        val fixture =
            WellPersistenceSelfTest.Fixture(
                world = world,
                wellPosition = position,
                sectionRef = sectionRef,
                stakePositions = emptyList(),
                chunkIndex =
                    com.hypixel.hytale.math.util.ChunkUtil
                        .indexChunkFromBlock(position.x, position.z),
                stakeRefs = mutableListOf(),
                expectedState = state,
                expectedStrength = WellStrength.STEADY,
                expectedClaimCrew = claimCrew,
                expectedCorrupted = corrupted,
            )
        val wellRef =
            CraftoryCore.wells().spawn(
                world,
                position,
                Temperament.SPARK,
                WellStrength.STEADY,
                state,
                claimCrewId = claimCrew,
            ) ?: error("Slice 5b persistence Well did not spawn")
        WellSelfTestExclusions.registerWell(position)
        if (corrupted) CraftoryCore.wells().setCorrupted(world, position, true)
        val store = world.chunkStore.store
        store.getComponent(wellRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
        return fixture
    }
}
