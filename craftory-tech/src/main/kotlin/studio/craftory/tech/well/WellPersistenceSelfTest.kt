package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
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
import java.util.concurrent.TimeUnit

internal object WellPersistenceSelfTest {
    private val CLAIM_CREW_ID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private const val PLACER_UUID = "00000000-0000-0000-0000-000000000001"

    private data class Fixture(
        val world: World,
        val wellPosition: BlockPos,
        val sectionRef: Ref<ChunkStore>,
        val stakePositions: List<CraftorySelfTest.TestPosition>,
        val chunkIndex: Long,
        val stakeRefs: MutableList<Ref<ChunkStore>>,
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
        saveAndUnload(fixture, complete)
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
    ) {
        val grid = fixture.world.chunkStore
        val chunkRef = grid.getChunkReference(fixture.chunkIndex)?.takeIf { it.isValid }
        val worldChunk = chunkRef?.let { grid.store.getComponent(it, WorldChunk.getComponentType()) }
        if (chunkRef == null || worldChunk == null) {
            cleanupFixture(fixture)
            complete(false, "TETHERED chunk was not available to save")
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
            val well =
                CraftoryCore
                    .wells()
                    .wellsNear(store, fixture.wellPosition, 0)
                    .firstOrNull()
                    ?.well ?: error("reloaded Well was not indexed")
            check(well.state == WellState.TETHERED && well.claimCrewId == CLAIM_CREW_ID) {
                "reloaded Well lost its TETHERED state or crew claim"
            }
            val restoredStakes =
                fixture.stakePositions.map { position ->
                    isRestoredStake(fixture, world, store, position)
                }
            check(restoredStakes.all { it }) {
                "one or more tether stakes did not persist"
            }
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
        CraftorySelfTest.clear(fixture.world, sectionRef, listOf(wellPosition) + fixture.stakePositions)
        fixture.stakeRefs.forEach(WellSelfTestExclusions::releaseBlock)
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
