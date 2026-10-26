package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.CraftorySelfTest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal object WellPersistenceSelfTest {
    private val CLAIM_CREW_ID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private const val PLACER_UUID = "00000000-0000-0000-0000-000000000001"

    internal data class Fixture(
        val world: World,
        val wellPosition: BlockPos,
        val sectionRef: Ref<ChunkStore>,
        val stakePositions: List<CraftorySelfTest.TestPosition>,
        val chunkIndex: Long,
        val stakeRefs: MutableList<Ref<ChunkStore>>,
        val expectedState: WellState = WellState.TETHERED,
        val expectedStrength: WellStrength = WellStrength.FLICKER,
        val expectedClaimCrew: UUID? = CLAIM_CREW_ID,
        val extraBlockPositions: List<CraftorySelfTest.TestPosition> = emptyList(),
    )

    fun run(
        world: World,
        complete: (Boolean, String) -> Unit,
    ) {
        val fixture =
            try {
                prepare(world)
            } catch (failure: IllegalStateException) {
                complete(false, "TETHERED fixture setup failed (${failure.message})")
                return
            }
        saveAndUnload(fixture, { tetheredPassed, tetheredDetail ->
            if (!tetheredPassed) {
                complete(false, tetheredDetail)
            } else {
                val smothered =
                    runCatching { prepareSmotheredReloadFixture(world) }
                        .getOrElse { failure ->
                            complete(false, "smothered Well fixture setup failed (${failure.message})")
                            null
                        }
                if (smothered != null) {
                    saveAndUnload(smothered, { smotheredPassed, smotheredDetail ->
                        complete(
                            smotheredPassed,
                            "$tetheredDetail; $smotheredDetail",
                        )
                    })
                }
            }
        })
    }

    private fun prepare(world: World): Fixture {
        val wellPosition = WellSelfTest.STAKED_POS
        val stakePositions =
            listOf(
                CraftorySelfTest.TestPosition(wellPosition.x + 4, wellPosition.y, wellPosition.z),
                CraftorySelfTest.TestPosition(wellPosition.x + 4, wellPosition.y, wellPosition.z + 3),
                CraftorySelfTest.TestPosition(wellPosition.x - 4, wellPosition.y, wellPosition.z),
            )
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(
                wellPosition.x,
                wellPosition.y,
                wellPosition.z,
            ) ?: error("test section is not loaded")
        val store = world.chunkStore.store
        val section =
            store.getComponent(
                sectionRef,
                com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
                    .getComponentType(),
            )
                ?: error("test section is missing")
        WellSelfTestExclusions.registerWell(wellPosition)
        val fixture =
            Fixture(
                world,
                wellPosition,
                sectionRef,
                stakePositions,
                com.hypixel.hytale.math.util.ChunkUtil
                    .indexChunkFromBlock(wellPosition.x, wellPosition.z),
                mutableListOf(),
            )
        try {
            setupBlocks(fixture, store, section)
        } catch (failure: IllegalStateException) {
            cleanupFixture(fixture)
            throw failure
        }
        return fixture
    }

    private fun setupBlocks(
        fixture: Fixture,
        store: Store<ChunkStore>,
        section: com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection,
    ) {
        val wellRef =
            CraftoryCore.wells().spawn(
                fixture.world,
                fixture.wellPosition,
                Temperament.SPARK,
                WellStrength.FLICKER,
                WellState.TETHERED,
            ) ?: error("TETHERED Well did not spawn")
        store.getComponent(wellRef, Glimmerwell.type)?.claimCrewId = CLAIM_CREW_ID
        store.getComponent(wellRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
        fixture.stakePositions.forEach { position ->
            CraftorySelfTest.place(
                fixture.world,
                store,
                fixture.sectionRef,
                section,
                CraftoryIds.TETHER_STAKE,
                position,
                TetherStakeState.type,
            )
            val ref =
                BlockModule.getBlockEntity(fixture.world, position.x, position.y, position.z)
                    ?: error("stake block entity did not spawn")
            val state = store.getComponent(ref, TetherStakeState.type) ?: error("stake state is missing")
            state.wellId = fixture.wellPosition.pack().toString()
            state.placerUuid = PLACER_UUID
            state.leaning = false
            store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
            WellSelfTestExclusions.registerBlock(ref)
            fixture.stakeRefs.add(ref)
        }
    }

    private fun saveAndUnload(
        fixture: Fixture,
        complete: (Boolean, String) -> Unit,
        attempt: Int = 0,
    ) {
        val grid = fixture.world.chunkStore
        val chunkRef = grid.getChunkReference(fixture.chunkIndex)?.takeIf { it.isValid }
        val worldChunk = chunkRef?.let { grid.store.getComponent(it, WorldChunk.getComponentType()) }
        if (chunkRef == null || worldChunk == null) {
            cleanupFixture(fixture)
            complete(false, "TETHERED chunk was not available to save")
            return
        }
        if (worldChunk.isSaving) {
            if (attempt < 100) {
                scheduleReloadRetry(fixture.world) { saveAndUnload(fixture, complete, attempt + 1) }
            } else {
                cleanupFixture(fixture)
                complete(false, "TETHERED chunk remained busy saving")
            }
            return
        }
        try {
            val saveEvent = ChunkSaveEvent(worldChunk)
            grid.store.invoke(chunkRef, saveEvent)
            check(!saveEvent.isCancelled) { "chunk save was cancelled" }
            worldChunk.addKeepLoaded()
            worldChunk.setSaving(true)
            val saver = grid.getSaver() ?: error("chunk saver is unavailable")
            saver
                .saveChunkColumn(worldChunk.x, worldChunk.z, grid.store, chunkRef, null, null)
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete { _, failure ->
                    fixture.world.execute {
                        if (failure != null) {
                            releaseSaveHold(worldChunk, markNeedsSaving = true)
                            cleanupFixture(fixture)
                            complete(false, "TETHERED chunk save failed (${failure.message})")
                            return@execute
                        }
                        unloadSavedChunk(fixture, chunkRef, worldChunk, complete)
                    }
                }
        } catch (failure: IllegalStateException) {
            releaseSaveHold(worldChunk, markNeedsSaving = true)
            complete(false, "TETHERED chunk save failed (${failure.message})")
        }
    }

    private fun unloadSavedChunk(
        fixture: Fixture,
        chunkRef: Ref<ChunkStore>,
        worldChunk: WorldChunk,
        complete: (Boolean, String) -> Unit,
    ) {
        val grid = fixture.world.chunkStore
        try {
            worldChunk.consumeNeedsSaving()
            worldChunk.setFlag(ChunkFlag.ON_DISK, true)
            releaseSaveHold(worldChunk)
            grid.remove(chunkRef, RemoveReason.UNLOAD)
            check(!chunkRef.isValid) { "chunk did not unload" }
            fixture.stakeRefs.forEach(WellSelfTestExclusions::releaseBlock)
            fixture.stakeRefs.clear()
            grid.getChunkReferenceAsync(fixture.chunkIndex, GetChunkFlags.SET_TICKING).whenComplete { _, failure ->
                fixture.world.execute {
                    if (failure != null) {
                        complete(false, "TETHERED chunk reload failed (${failure.message})")
                    } else {
                        fixture.stakePositions.forEach { position ->
                            BlockModule
                                .getBlockEntity(fixture.world, position.x, position.y, position.z)
                                ?.let { ref ->
                                    WellSelfTestExclusions.registerBlock(ref)
                                    fixture.stakeRefs.add(ref)
                                }
                        }
                        verifyReloaded(fixture, complete)
                    }
                }
            }
        } catch (failure: IllegalStateException) {
            releaseSaveHold(worldChunk, markNeedsSaving = true)
            complete(false, "TETHERED chunk unload failed (${failure.message})")
        }
    }

    private fun verifyReloaded(
        fixture: Fixture,
        complete: (Boolean, String) -> Unit,
        attempt: Int = 0,
    ) {
        try {
            val world = fixture.world
            val sectionRef =
                world.chunkStore.getChunkSectionReferenceAtBlock(
                    fixture.wellPosition.x,
                    fixture.wellPosition.y,
                    fixture.wellPosition.z,
                ) ?: error("reloaded test section is missing")
            val store = world.chunkStore.store
            store.getComponent(sectionRef, ChunkSection.getComponentType())?.resetActiveTimer()
            store.removeComponentIfExists(sectionRef, ChunkStore.REGISTRY.getNonTickingComponentType())
            val blockId =
                store
                    .getComponent(sectionRef, BlockSection.getComponentType())
                    ?.get(fixture.wellPosition.x and 31, fixture.wellPosition.y and 31, fixture.wellPosition.z and 31)
            val blockType = blockId?.let { BlockType.getAssetMap().getAsset(it) }
            val blockName = blockType?.id
            val componentDeclared = blockType?.let { BlockEntity.declaresComponent(it, Glimmerwell.type) }
            val wellRef =
                BlockModule.getBlockEntity(
                    world,
                    fixture.wellPosition.x,
                    fixture.wellPosition.y,
                    fixture.wellPosition.z,
                )
            val indexedWell =
                CraftoryCore.wells().wellsNear(store, fixture.wellPosition, 0).firstOrNull()
            if (indexedWell == null && attempt < 100) {
                scheduleReloadRetry(world) { verifyReloaded(fixture, complete, attempt + 1) }
                return
            }
            check(indexedWell != null) {
                val hasBlockState =
                    wellRef?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) != null }
                "reloaded Well was not indexed " +
                    "(attempts=$attempt, blockEntityValid=${wellRef?.isValid}, blockState=$hasBlockState, " +
                    "block=$blockName, componentDeclared=$componentDeclared, " +
                    "${describePersistedWellHolder(store, sectionRef, fixture.wellPosition)}, " +
                    "indexCount=${CraftoryCore.wells().count(store)})"
            }
            checkRestoredState(fixture, world, store, indexedWell.well)
            cleanupFixture(fixture, sectionRef)
            saveCleanChunk(fixture, complete)
        } catch (failure: IllegalStateException) {
            val detail = "TETHERED persistence verification failed (${failure.message})"
            val sectionRef =
                fixture.world.chunkStore.getChunkSectionReferenceAtBlock(
                    fixture.wellPosition.x,
                    fixture.wellPosition.y,
                    fixture.wellPosition.z,
                )
            if (sectionRef == null) {
                complete(false, detail)
            } else {
                runCatching { cleanupFixture(fixture, sectionRef) }
                saveCleanChunk(fixture) { _, cleanupDetail -> complete(false, "$detail; cleanup: $cleanupDetail") }
            }
        }
    }

    private fun checkRestoredState(
        fixture: Fixture,
        world: World,
        store: Store<ChunkStore>,
        well: Glimmerwell,
    ) {
        check(
            well.state == fixture.expectedState &&
                well.strength == fixture.expectedStrength &&
                well.claimCrewId == fixture.expectedClaimCrew,
        ) {
            "reloaded Well lost its ${fixture.expectedState} state, strength band, or crew claim"
        }
        check(fixture.stakePositions.all { position -> isRestoredStake(fixture, world, store, position) }) {
            "one or more tether stakes did not persist"
        }
    }

    private fun isRestoredStake(
        fixture: Fixture,
        world: World,
        store: Store<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): Boolean {
        val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return false
        val state = store.getComponent(ref, TetherStakeState.type) ?: return false
        WellSelfTestExclusions.registerBlock(ref)
        fixture.stakeRefs.add(ref)
        return state.wellId == fixture.wellPosition.pack().toString() && !state.leaning
    }

    private fun cleanupFixture(
        fixture: Fixture,
        sectionRef: Ref<ChunkStore> = fixture.sectionRef,
    ) {
        CraftoryCore.wells().remove(fixture.world, fixture.wellPosition)
        val wellPosition =
            CraftorySelfTest.TestPosition(
                fixture.wellPosition.x,
                fixture.wellPosition.y,
                fixture.wellPosition.z,
            )
        val extraRefs =
            fixture.extraBlockPositions.mapNotNull { position ->
                BlockModule.getBlockEntity(fixture.world, position.x, position.y, position.z)
            }
        extraRefs.forEach(WellSelfTestExclusions::registerBlock)
        CraftorySelfTest.clear(
            fixture.world,
            sectionRef,
            listOf(wellPosition) + fixture.stakePositions + fixture.extraBlockPositions,
        )
        (fixture.stakeRefs + extraRefs).forEach(WellSelfTestExclusions::releaseBlock)
        fixture.stakeRefs.clear()
        WellSelfTestExclusions.releaseWell(fixture.wellPosition)
    }

    private fun saveCleanChunk(
        fixture: Fixture,
        complete: (Boolean, String) -> Unit,
    ) {
        val grid = fixture.world.chunkStore
        val chunkRef = grid.getChunkReference(fixture.chunkIndex)?.takeIf { it.isValid }
        val worldChunk = chunkRef?.let { grid.store.getComponent(it, WorldChunk.getComponentType()) }
        if (chunkRef == null || worldChunk == null) {
            complete(false, "cleaned test chunk is unavailable")
            return
        }
        try {
            val saveEvent = ChunkSaveEvent(worldChunk)
            grid.store.invoke(chunkRef, saveEvent)
            check(!saveEvent.isCancelled) { "cleaned chunk save was cancelled" }
            worldChunk.addKeepLoaded()
            worldChunk.setSaving(true)
            val saver = grid.getSaver() ?: error("chunk saver is unavailable")
            saver
                .saveChunkColumn(worldChunk.x, worldChunk.z, grid.store, chunkRef, null, null)
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete { _, failure ->
                    fixture.world.execute {
                        releaseSaveHold(worldChunk, markNeedsSaving = failure != null)
                        if (failure == null) {
                            complete(true, "TETHERED Well and stakes survived real chunk unload/reload")
                        } else {
                            complete(false, "cleaned chunk save failed (${failure.message})")
                        }
                    }
                }
        } catch (failure: IllegalStateException) {
            releaseSaveHold(worldChunk, markNeedsSaving = true)
            complete(false, "cleaned chunk save failed (${failure.message})")
        }
    }

    private fun releaseSaveHold(
        worldChunk: WorldChunk,
        markNeedsSaving: Boolean = false,
    ) {
        if (markNeedsSaving) worldChunk.markNeedsSaving()
        worldChunk.setSaving(false)
        worldChunk.removeKeepLoaded()
    }
}

private fun prepareSmotheredReloadFixture(world: World): WellPersistenceSelfTest.Fixture {
    val position = WellSelfTest.SMOTHERED_POS
    val sectionRef =
        world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
            ?: error("smothered test section is not loaded")
    val chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z)
    val damperPositions =
        listOf(
            CraftorySelfTest.TestPosition(position.x + 2, position.y, position.z),
            CraftorySelfTest.TestPosition(position.x - 2, position.y, position.z),
            CraftorySelfTest.TestPosition(position.x, position.y, position.z + 2),
        )
    val fixture =
        WellPersistenceSelfTest.Fixture(
            world,
            position,
            sectionRef,
            emptyList(),
            chunkIndex,
            mutableListOf(),
            WellState.DORMANT,
            WellStrength.FLICKER,
            null,
            damperPositions,
        )
    var setupComplete = false
    try {
        WellSelfTestExclusions.registerWell(position)
        val ref =
            CraftoryCore.wells().spawn(
                world,
                position,
                Temperament.SPARK,
                WellStrength.STEADY,
                WellState.WILD,
            ) ?: error("WILD Well for smothered reload did not spawn")
        val well =
            world.chunkStore.store.getComponent(ref, Glimmerwell.type)
                ?: error("Well state is missing")
        val store = world.chunkStore.store
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: error("smothered test block section is missing")
        WellSelfTest.Helpers.placeTestDampers(
            world,
            store,
            sectionRef,
            section,
            damperPositions,
            fixture.stakeRefs,
        )
        WellSelfTest.Helpers.assertDamperSmothering(store, well, fixture.stakeRefs)
        setupComplete = true
        return fixture
    } finally {
        if (!setupComplete) cleanupFailedSmotheredFixture(world, position, sectionRef, damperPositions, fixture)
    }
}

private fun cleanupFailedSmotheredFixture(
    world: World,
    position: BlockPos,
    sectionRef: Ref<ChunkStore>,
    damperPositions: List<CraftorySelfTest.TestPosition>,
    fixture: WellPersistenceSelfTest.Fixture,
) {
    CraftoryCore.wells().remove(world, position)
    val wellPosition = CraftorySelfTest.TestPosition(position.x, position.y, position.z)
    CraftorySelfTest.clear(world, sectionRef, listOf(wellPosition) + damperPositions)
    fixture.stakeRefs.forEach(WellSelfTestExclusions::releaseBlock)
    WellSelfTestExclusions.releaseWell(position)
}

private fun scheduleReloadRetry(
    world: World,
    action: () -> Unit,
) {
    CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute {
        world.execute(action)
    }
}
