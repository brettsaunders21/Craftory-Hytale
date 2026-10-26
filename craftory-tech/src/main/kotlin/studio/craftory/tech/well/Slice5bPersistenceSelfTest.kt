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
    data class Result(
        val passed: Boolean,
        val detail: String,
        val elapsedTicks: Long,
    )

    private val claimCrew = UUID.fromString("00000000-0000-0000-0000-000000000002")

    fun run(
        world: World,
        position: BlockPos,
        complete: (Result, Result) -> Unit,
    ) {
        val tappedPosition = BlockPos(position.x + 8, position.y, position.z)
        val corruptedPosition = BlockPos(position.x + 12, position.y, position.z)
        val tappedStarted = world.tick

        fun finish(
            tapped: Result,
            corrupted: Result,
        ) {
            WellSelfTestExclusions.releaseWell(tappedPosition)
            WellSelfTestExclusions.releaseWell(corruptedPosition)
            complete(tapped, corrupted)
        }
        val tapped =
            runCatching { prepare(world, tappedPosition, WellState.TAPPED, false) }
                .getOrElse { failure ->
                    finish(
                        Result(false, "fixture setup failed (${failure.message})", world.tick - tappedStarted),
                        Result(false, "skipped because TAPPED setup failed", 0),
                    )
                    return
                }
        WellPersistenceSelfTest.saveAndUnload(
            tapped,
            complete = { tappedPassed, tappedDetail ->
                val tappedResult = Result(tappedPassed, tappedDetail, world.tick - tappedStarted)
                if (!tappedPassed) {
                    finish(tappedResult, Result(false, "skipped because TAPPED reload failed", 0))
                    return@saveAndUnload
                }
                val corruptedStarted = world.tick
                val corrupted =
                    runCatching { prepare(world, corruptedPosition, WellState.CORRUPTED, true) }
                        .getOrElse { failure ->
                            finish(
                                tappedResult,
                                Result(
                                    false,
                                    "fixture setup failed (${failure.message})",
                                    world.tick - corruptedStarted,
                                ),
                            )
                            return@saveAndUnload
                        }
                WellPersistenceSelfTest.saveAndUnload(
                    corrupted,
                    complete = { corruptedPassed, corruptedDetail ->
                        finish(
                            tappedResult,
                            Result(corruptedPassed, corruptedDetail, world.tick - corruptedStarted),
                        )
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
