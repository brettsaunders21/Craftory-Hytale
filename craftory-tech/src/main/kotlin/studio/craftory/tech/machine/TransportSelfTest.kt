@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "LoopWithTooManyJumpStatements",
    "LargeClass",
    "TooManyFunctions",
    "TooGenericExceptionCaught",
)

package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.ItemPipe
import studio.craftory.core.api.transport.ItemPipeTransitRecord
import studio.craftory.core.api.transport.ItemTransport
import studio.craftory.core.api.transport.TransportColour
import studio.craftory.tech.CraftoryIds
import java.util.function.BiConsumer

internal class TransportSelfTestRun : Resource<ChunkStore> {
    class Run(
        val context: com.hypixel.hytale.server.core.command.system.CommandContext,
        val world: World,
        val startTick: Long,
    ) {
        val currentBlocks = mutableListOf<Position>()
        val allBlocks = linkedSetOf<Position>()
        val keepLoadedChunks = linkedSetOf<Long>()
        val testSections =
            linkedSetOf(
                SectionPosition(318, 4, 312),
                SectionPosition(319, 4, 312),
                SectionPosition(320, 4, 312),
                SectionPosition(321, 4, 312),
            )
        val testTickingSections = testSections.toMutableSet()
        var ready = false
        var anchorLoading = false
        var anchorPlaced = false
        var anchorPosition: Position? = null
        var setupFailure: String? = null
        var t4Failure: String? = null
        var pendingLoads = 0
        var stage = 0
        var stageStartTick = 0L
        var caseTimerStartTick: Long? = null
        var passed = 0
        var failed = 0
        var source: Position? = null
        var claw: Position? = null
        var targetPipe: Position? = null
        var destinationA: Position? = null
        var destinationB: Position? = null
        var t4ItemInstanceId: Long? = null
        var t4Owner: Position? = null
        var t4Target: Position? = null
        var t4Progress: Int? = null
        var t4PersistedFromPos: Long? = null
        var t4PersistedToPos: Long? = null
        var t4PersistedProgress: Int? = null
        var t4LoadedTick = 0L
        var t4AnchorDisableTick = 0L
        var t4AnchorPosition: Position? = null
        var t4AnchorReadyStartTick = 0L
        var t4AnchorDisableStarted = false
        var t4ItemInserted = false
        var t4Prepared = false
        var t4Loading = false
        var t4Saving = false
        var t4SaveStarted = false
        var t4SaveKeepLoadedChunk: Long? = null
        var t5ItemInstanceId: Long? = null
        var t5Record: ItemPipeTransitRecord? = null
        var t5FreezeStartTick: Long? = null
        var t5SectionRemoved = false
        var t5AnchorDisabled = false
        var t5OriginalAnchorChargeSeconds: Long? = null
        var t5ReactivationStarted = false
        var t5FreezePassed = false
        var t5FreezeDetail = ""
        var t5SkipReason: String? = null
        var t6ItemInstanceId: Long? = null
        var t6BreakTick: Long? = null
        var cleanupLoading = false
        var cleanupLoadFailed = false
        var cleanupFinished = false
    }

    var active: Run? = null

    override fun clone(): Resource<ChunkStore> = TransportSelfTestRun()

    companion object {
        lateinit var type: ResourceType<ChunkStore, TransportSelfTestRun>
    }
}

internal data class SectionPosition(
    val x: Int,
    val y: Int,
    val z: Int,
)

internal data class Position(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    operator fun plus(other: Position) = Position(x + other.x, y + other.y, z + other.z)

    operator fun times(scale: Int) = Position(x * scale, y * scale, z * scale)
}

internal class TransportSelfTestSystem : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val run = store.getResource(TransportSelfTestRun.type).active ?: return
        try {
            if (run.cleanupFinished) return
            keepTestSectionsTicking(run, store)
            if (run.stage != 7 && store.externalData.world.tick - run.startTick >= MAX_RUNTIME_TICKS) {
                timeoutRemaining(run)
                beginCleanup(run)
                return
            }
            if (run.setupFailure != null) {
                timeoutRemaining(run)
                beginCleanup(run)
                return
            }
            if (!run.ready) {
                tryPrepareAnchorArea(run)
                return
            }
            when (run.stage) {
                0 -> startT1(run)
                1 -> tickT1(run)
                2 -> tickT2(run)
                3 -> tickT3(run)
                4 -> tickT4(run)
                5 -> tickT5(run, store)
                6 -> tickT6(run)
                7 -> pollCleanup(run)
            }
        } catch (failure: Exception) {
            failCurrent(run, "exception (${failure.message ?: failure.javaClass.simpleName})")
            beginCleanup(run)
        }
    }

    private fun tryPrepareAnchorArea(run: TransportSelfTestRun.Run) {
        if (!run.anchorPlaced) {
            if (!run.anchorLoading) placeAnchor(run)
            if (run.world.tick - run.startTick >= ANCHOR_READY_TIMEOUT_TICKS) {
                run.setupFailure = "Wakebell Anchor placement timed out"
            }
            return
        }
        val position =
            run.anchorPosition
                ?: run {
                    run.setupFailure = "Wakebell Anchor position is missing"
                    return
                }
        val anchorRef =
            BlockModule
                .getBlockEntity(run.world, position.x, position.y, position.z)
                ?.takeIf { it.isValid }
        val anchor =
            anchorRef?.let {
                run.world.chunkStore.store
                    .getComponent(it, AnchorState.type)
            }
        if (anchor == null) {
            if (run.world.tick - run.startTick >= ANCHOR_READY_TIMEOUT_TICKS) {
                run.setupFailure = "Wakebell Anchor state did not load"
            }
            return
        }
        val activeRigPositions =
            arrayOf(
                Position(10_200, 150, 10_000),
                Position(10_212, 150, 10_000),
                Position(10_224, 150, 10_000),
                Position(10_240, 150, 10_000),
            )
        val areaLoaded =
            anchor.chargeSeconds > 0 &&
                anchor.coverageActive == true &&
                anchor.pendingChunkLoads.isEmpty() &&
                anchor.keepLoadedChunks.size == 9
        if (areaLoaded && activeRigPositions.all { !sectionIsNonTicking(run, it) }) {
            run.ready = true
        } else if (run.world.tick - run.startTick >= ANCHOR_READY_TIMEOUT_TICKS) {
            run.setupFailure = "Wakebell Anchor area did not become ticking"
        }
    }

    private fun placeAnchor(run: TransportSelfTestRun.Run) {
        val position = run.anchorPosition ?: return
        run.anchorLoading = true
        run.world.chunkStore
            .getChunkSectionReferenceAtBlockAsync(
                position.x,
                position.y,
                position.z,
                GetChunkFlags.SET_TICKING,
            ).whenComplete { sectionRef, failure ->
                run.world.execute {
                    if (run.cleanupFinished || run.stage == 7) return@execute
                    if (failure != null || sectionRef == null || !sectionRef.isValid) {
                        run.setupFailure =
                            "Wakebell Anchor chunk load failed " +
                            "(${failure?.message ?: "invalid section ref"})"
                        run.anchorLoading = false
                        return@execute
                    }
                    try {
                        placeBlock(run, CraftoryIds.WAKEBELL_ANCHOR, position, keepAcrossCases = true)
                        val anchorRef =
                            BlockModule
                                .getBlockEntity(run.world, position.x, position.y, position.z)
                                ?.takeIf { it.isValid }
                                ?: error("Wakebell Anchor block entity missing")
                        val store = run.world.chunkStore.store
                        val anchor =
                            store.getComponent(anchorRef, AnchorState.type)
                                ?: error("Wakebell Anchor state missing")
                        val blockState =
                            store.getComponent(anchorRef, BlockModule.BlockStateInfo.getComponentType())
                                ?: error("Wakebell Anchor block state missing")
                        anchor.chargeSeconds = 900
                        blockState.markNeedsSaving(store)
                        MachineActions.activateAnchor(
                            run.world,
                            anchor,
                            blockState,
                            store,
                            BlockPos(position.x, position.y, position.z),
                        )
                        run.anchorPlaced = true
                    } catch (failure: Exception) {
                        run.setupFailure = "Wakebell Anchor setup failed (${failure.message})"
                    } finally {
                        run.anchorLoading = false
                    }
                }
            }
    }

    private fun keepTestSectionsTicking(
        run: TransportSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val grid = run.world.chunkStore
        run.testTickingSections.forEach { sectionPosition ->
            val sectionRef =
                runCatching {
                    grid
                        .getChunkSectionReferenceAsync(
                            sectionPosition.x,
                            sectionPosition.y,
                            sectionPosition.z,
                            GetChunkFlags.SET_TICKING,
                        ).getNow(null)
                }.getOrNull()
            if (sectionRef == null) {
                return@forEach
            }
            if (!sectionRef.isValid) return@forEach
            val section = store.getComponent(sectionRef, ChunkSection.getComponentType()) ?: return@forEach
            section.resetActiveTimer()
            retainTestChunk(run, store, ChunkUtil.indexChunk(sectionPosition.x, sectionPosition.z))
        }
    }

    private fun retainTestChunk(
        run: TransportSelfTestRun.Run,
        store: Store<ChunkStore>,
        chunkIndex: Long,
    ) {
        val ref =
            run.world.chunkStore
                .getChunkReference(chunkIndex)
                ?.takeIf { it.isValid } ?: return
        val worldChunk = store.getComponent(ref, WorldChunk.getComponentType()) ?: return
        if (run.keepLoadedChunks.add(chunkIndex)) worldChunk.addKeepLoaded()
    }

    private fun releaseTestChunk(
        run: TransportSelfTestRun.Run,
        chunkIndex: Long,
    ) {
        if (!run.keepLoadedChunks.remove(chunkIndex)) return
        val grid = run.world.chunkStore
        val ref = grid.getChunkReference(chunkIndex)?.takeIf { it.isValid } ?: return
        grid.store.getComponent(ref, WorldChunk.getComponentType())?.removeKeepLoaded()
    }

    private fun sectionTimeoutDetail(
        run: TransportSelfTestRun.Run,
        detail: String,
    ): String = "$detail; ${testSectionDiagnostics(run)}"

    private fun testSectionDiagnostics(run: TransportSelfTestRun.Run): String {
        val grid = run.world.chunkStore
        val store = grid.store
        val nonTickingType = ChunkStore.REGISTRY.getNonTickingComponentType()
        return run.testSections.joinToString(prefix = "sections=[", postfix = "]") { sectionPosition ->
            val blockX = sectionPosition.x shl 5
            val blockY = sectionPosition.y shl 5
            val blockZ = sectionPosition.z shl 5
            val ref =
                grid
                    .getChunkSectionReferenceAtBlock(blockX, blockY, blockZ)
                    ?.takeIf { it.isValid }
            if (ref == null) {
                "(${sectionPosition.x},${sectionPosition.y},${sectionPosition.z}:loaded=false," +
                    "NonTicking=unknown,refreshing=${sectionPosition in run.testTickingSections})"
            } else {
                val nonTicking = store.getComponent(ref, nonTickingType) != null
                "(${sectionPosition.x},${sectionPosition.y},${sectionPosition.z}:loaded=true," +
                    "NonTicking=$nonTicking,refreshing=${sectionPosition in run.testTickingSections})"
            }
        }
    }

    private fun caseTimerStarted(
        run: TransportSelfTestRun.Run,
        position: Position?,
    ): Boolean {
        if (run.caseTimerStartTick != null) return true
        if (position != null && !sectionIsNonTicking(run, position)) {
            run.caseTimerStartTick = run.world.tick
            return true
        }
        return false
    }

    private fun activeWaitExpired(run: TransportSelfTestRun.Run): Boolean =
        run.caseTimerStartTick == null && run.world.tick - run.stageStartTick >= ACTIVE_SECTION_TIMEOUT_TICKS

    private fun anchorAreaReady(
        run: TransportSelfTestRun.Run,
        position: Position,
    ): Boolean {
        val anchorRef =
            BlockModule
                .getBlockEntity(run.world, position.x, position.y, position.z)
                ?.takeIf { it.isValid }
                ?: return false
        val anchor =
            run.world.chunkStore.store
                .getComponent(anchorRef, AnchorState.type) ?: return false
        return anchor.chargeSeconds > 0 &&
            anchor.coverageActive == true &&
            anchor.pendingChunkLoads.isEmpty() &&
            anchor.keepLoadedChunks.size == 9
    }

    private fun anchorIsInactive(
        run: TransportSelfTestRun.Run,
        position: Position,
    ): Boolean {
        val anchorRef =
            BlockModule
                .getBlockEntity(run.world, position.x, position.y, position.z)
                ?.takeIf { it.isValid }
                ?: return false
        val anchor =
            run.world.chunkStore.store
                .getComponent(anchorRef, AnchorState.type) ?: return false
        return anchor.chargeSeconds <= 0 &&
            anchor.coverageActive != true &&
            anchor.pendingChunkLoads.isEmpty() &&
            anchor.keepLoadedChunks.isEmpty()
    }

    private fun sectionIsNonTicking(
        run: TransportSelfTestRun.Run,
        position: Position,
    ): Boolean {
        val sectionRef =
            run.world.chunkStore
                .getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?.takeIf { it.isValid }
                ?: return true
        return run.world.chunkStore.store.getComponent(
            sectionRef,
            ChunkStore.REGISTRY.getNonTickingComponentType(),
        ) != null
    }

    private fun startT1(run: TransportSelfTestRun.Run) {
        val layout = placeClawCase(run, Position(10_200, 150, 10_000), quantity = 3)
        run.source = layout.source
        run.destinationA = layout.destinationA
        run.caseTimerStartTick = null
        run.stage = 1
        run.stageStartTick = run.world.tick
    }

    private fun tickT1(run: TransportSelfTestRun.Run) {
        if (!caseTimerStarted(run, run.claw)) {
            if (activeWaitExpired(run)) {
                finishCase(
                    run,
                    "claw chest delivery",
                    false,
                    sectionTimeoutDetail(run, "claw section remained NonTicking"),
                )
                startT2(run)
            }
            return
        }
        if (containerQuantity(run, run.destinationA) >= 1) {
            finishCase(run, "claw chest delivery", true)
            startT2(run)
        } else if (run.world.tick - (run.caseTimerStartTick ?: run.stageStartTick) >= 400) {
            finishCase(
                run,
                "claw chest delivery",
                false,
                sectionTimeoutDetail(run, "400-tick timeout; ${clawDiagnostics(run)}"),
            )
            startT2(run)
        }
    }

    private fun startT2(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        val layout =
            placeClawCase(
                run,
                Position(10_212, 150, 10_000),
                quantity = 3,
                filterItemId = "Ore_Copper",
            )
        run.source = layout.source
        run.destinationA = layout.destinationA
        run.caseTimerStartTick = null
        run.stage = 2
        run.stageStartTick = run.world.tick
    }

    private fun tickT2(run: TransportSelfTestRun.Run) {
        if (!caseTimerStarted(run, run.claw)) {
            if (activeWaitExpired(run)) {
                finishCase(
                    run,
                    "claw filter",
                    false,
                    sectionTimeoutDetail(run, "claw section remained NonTicking"),
                )
                startT3(run)
            }
            return
        }
        if (run.world.tick - (run.caseTimerStartTick ?: run.stageStartTick) < 80) return
        val sourceQuantity = containerQuantity(run, run.source)
        val destinationQuantity = containerQuantity(run, run.destinationA)
        finishCase(
            run,
            "claw filter",
            sourceQuantity == 3 && destinationQuantity == 0,
            "source=$sourceQuantity destination=$destinationQuantity",
        )
        startT3(run)
    }

    private fun startT3(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        val layout =
            placeClawCase(
                run,
                Position(10_224, 150, 10_000),
                quantity = 3,
                filterColour = TransportColour.RED.value,
                branches = true,
            )
        run.source = layout.source
        run.destinationA = layout.destinationA
        run.destinationB = layout.destinationB
        run.caseTimerStartTick = null
        run.stage = 3
        run.stageStartTick = run.world.tick
    }

    private fun tickT3(run: TransportSelfTestRun.Run) {
        if (!caseTimerStarted(run, run.claw)) {
            if (activeWaitExpired(run)) {
                finishCase(
                    run,
                    "red branch routing",
                    false,
                    sectionTimeoutDetail(run, "claw section remained NonTicking"),
                )
                startT4(run)
            }
            return
        }
        val outputA = containerQuantity(run, run.destinationA)
        val outputB = containerQuantity(run, run.destinationB)
        if (outputA >= 3 || run.world.tick - (run.caseTimerStartTick ?: run.stageStartTick) >= 400) {
            finishCase(
                run,
                "red branch routing",
                outputA == 3 && outputB == 0,
                sectionTimeoutDetail(run, "branchA=$outputA branchB=$outputB; ${clawDiagnostics(run)}"),
            )
            startT4(run)
        }
    }

    private fun startT4(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        run.stage = 4
        run.stageStartTick = run.world.tick
        run.t4Prepared = false
        run.t4Loading = true
        run.t4Saving = false
        run.t4SaveStarted = false
        run.t4SaveKeepLoadedChunk = null
        run.t4ItemInserted = false
        run.t4AnchorDisableStarted = false
        run.t4ItemInstanceId = null
        run.t4Owner = null
        run.t4Target = null
        run.t4Progress = null
        run.t4PersistedFromPos = null
        run.t4PersistedToPos = null
        run.t4PersistedProgress = null
        run.caseTimerStartTick = null
        val first = Position(10_272, 150, 10_000)
        val second = Position(10_273, 150, 10_000)
        val third = Position(10_274, 150, 10_000)
        val anchorPosition = Position(10_280, 150, 10_005)
        val chunkIndex = chunkIndex(first)
        run.world.chunkStore.getChunkReferenceAsync(chunkIndex, GetChunkFlags.SET_TICKING).whenComplete {
            ref,
            failure,
            ->
            run.world.execute {
                if (failure != null || ref == null || !ref.isValid) {
                    run.t4Failure = "initial chunk load failed (${failure?.message})"
                    run.t4Loading = false
                    return@execute
                }
                try {
                    placeBlock(run, CraftoryIds.PIPE, first)
                    placeBlock(run, CraftoryIds.PIPE, second)
                    placeBlock(run, CraftoryIds.PIPE, third)
                    placeBlock(run, CraftoryIds.WAKEBELL_ANCHOR, anchorPosition)
                    val anchorRef =
                        BlockModule
                            .getBlockEntity(run.world, anchorPosition.x, anchorPosition.y, anchorPosition.z)
                            ?.takeIf { it.isValid }
                            ?: error("T4 Wakebell Anchor block entity missing")
                    val store = run.world.chunkStore.store
                    val anchor =
                        store.getComponent(anchorRef, AnchorState.type)
                            ?: error("T4 Wakebell Anchor state missing")
                    val blockState =
                        store.getComponent(anchorRef, BlockModule.BlockStateInfo.getComponentType())
                            ?: error("T4 Wakebell Anchor block state missing")
                    anchor.chargeSeconds = 900
                    blockState.markNeedsSaving(store)
                    MachineActions.activateAnchor(
                        run.world,
                        anchor,
                        blockState,
                        store,
                        BlockPos(anchorPosition.x, anchorPosition.y, anchorPosition.z),
                    )
                    run.t4AnchorPosition = anchorPosition
                    run.t4AnchorReadyStartTick = run.world.tick
                    run.t4Prepared = true
                    run.t4Loading = false
                } catch (failure: Exception) {
                    run.t4Failure = "setup failed (${failure.message})"
                    run.t4Loading = false
                }
            }
        }
    }

    private fun reloadT4(
        run: TransportSelfTestRun.Run,
        owner: Position,
    ) {
        val chunkIndex = chunkIndex(owner)
        run.testTickingSections.add(sectionPosition(owner))
        run.t4Loading = true
        run.world.chunkStore.getChunkReferenceAsync(chunkIndex, GetChunkFlags.SET_TICKING).whenComplete {
            ref,
            failure,
            ->
            run.world.execute {
                if (failure != null || ref == null || !ref.isValid) {
                    completeT4Reload(run, null, failure ?: IllegalStateException("T4 chunk reload failed"))
                    return@execute
                }
                retainTestChunk(run, run.world.chunkStore.store, chunkIndex)
                run.world.chunkStore
                    .getChunkSectionReferenceAtBlockAsync(
                        owner.x,
                        owner.y,
                        owner.z,
                        GetChunkFlags.SET_TICKING,
                    ).whenComplete { sectionRef, sectionFailure ->
                        run.world.execute {
                            completeT4Reload(run, sectionRef, sectionFailure)
                        }
                    }
            }
        }
    }

    private fun completeT4Reload(
        run: TransportSelfTestRun.Run,
        sectionRef: Ref<ChunkStore>?,
        failure: Throwable?,
    ) {
        try {
            check(failure == null && sectionRef != null && sectionRef.isValid) {
                "T4 section reload failed (${failure?.message})"
            }
            val expectedItemId = run.t4ItemInstanceId ?: error("T4 item id was not captured")
            val persistedRecord =
                (10_272..10_274)
                    .asSequence()
                    .mapNotNull { x ->
                        val ref = BlockModule.getBlockEntity(run.world, x, 150, 10_000)?.takeIf { it.isValid }
                        ref
                            ?.let {
                                run.world.chunkStore.store
                                    .getComponent(it, ItemPipe.type)
                            }?.transitRecords()
                            ?.firstOrNull { it.id == expectedItemId }
                    }.firstOrNull() ?: error("T4 reloaded ItemPipe record is missing")
            val expectedFrom = run.t4PersistedFromPos ?: error("T4 persisted source segment was not captured")
            val expectedTo = run.t4PersistedToPos ?: error("T4 persisted target segment was not captured")
            val expectedProgress = run.t4PersistedProgress ?: error("T4 persisted progress was not captured")
            check(
                persistedRecord.itemId == ITEM_ID &&
                    persistedRecord.fromPos == expectedFrom &&
                    persistedRecord.toPos == expectedTo &&
                    persistedRecord.progress == expectedProgress,
            ) {
                "T4 reloaded ItemPipe record changed: ${describeT5Record(persistedRecord)}"
            }
            val restoredSegment =
                findItemSegment(ItemTransport.of(run.world), expectedItemId)
                    ?: error("T4 transit item missing after reload")
            check(restoredSegment.itemId == expectedItemId) { "T4 transit item id changed after reload" }
            check(
                BlockPos.pack(restoredSegment.from.x, restoredSegment.from.y, restoredSegment.from.z) == expectedFrom &&
                    BlockPos.pack(restoredSegment.to.x, restoredSegment.to.y, restoredSegment.to.z) == expectedTo &&
                    restoredSegment.progress >= expectedProgress,
            ) {
                "T4 segment changed: before=$expectedFrom->$expectedTo, " +
                    "after=${restoredSegment.from}->${restoredSegment.to}"
            }
            finishCase(run, "unload reload persistence", true)
            startT5(run)
        } catch (failure: Exception) {
            finishCase(run, "unload reload persistence", false, failure.message ?: "reload failed")
            startT5(run)
        } finally {
            run.t4Loading = false
        }
    }

    private fun tickT4(run: TransportSelfTestRun.Run) {
        val failure = run.t4Failure
        if (failure != null) {
            finishCase(run, "unload reload persistence", false, failure)
            run.t4Failure = null
            startT5(run)
            return
        }
        if (!run.t4Prepared || run.t4Loading) return
        val anchorPosition = run.t4AnchorPosition
        if (anchorPosition == null) {
            run.t4Failure = "T4 Wakebell Anchor position is missing"
            return
        }
        if (!run.t4ItemInserted) {
            if (
                anchorAreaReady(run, anchorPosition) &&
                !sectionIsNonTicking(run, Position(10_272, 150, 10_000))
            ) {
                try {
                    val first = Position(10_272, 150, 10_000)
                    val transport = ItemTransport.of(run.world)
                    check(transport.insert(first.x, first.y, first.z, ITEM_ID, 1)) {
                        "T4 pipe insert rejected"
                    }
                    run.t4ItemInstanceId =
                        findItemInstance(transport, first, ITEM_ID)
                            ?: error("T4 inserted item was not found")
                    run.t4LoadedTick = run.world.tick
                    run.t4ItemInserted = true
                } catch (failure: Exception) {
                    run.t4Failure = "item insert failed (${failure.message})"
                }
            } else if (run.world.tick - run.t4AnchorReadyStartTick >= ACTIVE_SECTION_TIMEOUT_TICKS) {
                run.t4Failure = "T4 Wakebell Anchor area did not become ticking"
            }
            return
        }
        if (!run.t4AnchorDisableStarted && run.world.tick - run.t4LoadedTick >= T4_PROGRESS_TICKS) {
            try {
                val segment =
                    findItemSegment(ItemTransport.of(run.world), run.t4ItemInstanceId)
                        ?: error("T4 transit item missing before unload")
                check(segment.progress > 0) { "T4 transit item did not progress before unload" }
                check(segment.from != segment.to) { "T4 transit item is not on a pipe segment" }
                run.t4Owner = segment.from
                run.t4Target = segment.to
                run.t4Progress = segment.progress
                val anchorRef =
                    BlockModule
                        .getBlockEntity(
                            run.world,
                            anchorPosition.x,
                            anchorPosition.y,
                            anchorPosition.z,
                        )?.takeIf { it.isValid }
                        ?: error("T4 Wakebell Anchor entity missing before unload")
                val store = run.world.chunkStore.store
                val anchor =
                    store.getComponent(anchorRef, AnchorState.type)
                        ?: error("T4 Wakebell Anchor state missing before unload")
                val anchorBlockState =
                    store.getComponent(anchorRef, BlockModule.BlockStateInfo.getComponentType())
                        ?: error("T4 Wakebell Anchor block state missing before unload")
                anchor.chargeSeconds = 0
                anchorBlockState.markNeedsSaving(store)
                run.t4AnchorDisableStarted = true
                run.t4AnchorDisableTick = run.world.tick
                run.testTickingSections.remove(sectionPosition(segment.from))
                return
            } catch (failure: Exception) {
                run.t4Failure = "anchor shutdown failed (${failure.message})"
                return
            }
        }
        val t4Section = Position(10_272, 150, 10_000)
        if (run.t4AnchorDisableStarted && !run.t4SaveStarted) {
            if (!sectionIsNonTicking(run, t4Section)) {
                if (run.world.tick - run.t4AnchorDisableTick >= ACTIVE_SECTION_TIMEOUT_TICKS) {
                    run.t4Failure =
                        sectionTimeoutDetail(run, "T4 section remained ticking after refresh stopped")
                }
                return
            }
            try {
                val store = run.world.chunkStore.store
                val expectedItemId = run.t4ItemInstanceId ?: error("T4 item id was not captured")
                val expectedFrom = run.t4Owner ?: error("T4 source segment was not captured")
                val persistedRecord =
                    (10_272..10_274)
                        .asSequence()
                        .mapNotNull { x ->
                            persistedPipeRecordAt(run, store, Position(x, 150, 10_000), expectedItemId)
                        }.firstOrNull()
                        ?: error("T4 ItemPipe did not serialize transit item")
                check(
                    persistedRecord.itemId == ITEM_ID &&
                        persistedRecord.fromPos != persistedRecord.toPos,
                ) {
                    "T4 persisted segment changed: ${describeT5Record(persistedRecord)}"
                }
                run.t4PersistedFromPos = persistedRecord.fromPos
                run.t4PersistedToPos = persistedRecord.toPos
                run.t4PersistedProgress = persistedRecord.progress
                val chunkRef =
                    run.world.chunkStore
                        .getChunkReference(chunkIndex(expectedFrom))
                        ?.takeIf { it.isValid }
                        ?: error("T4 live chunk reference missing")
                val chunkStore = run.world.chunkStore.store
                val worldChunk =
                    chunkStore.getComponent(chunkRef, WorldChunk.getComponentType())
                        ?: error("T4 live WorldChunk missing")
                if (worldChunk.isSaving) return
                run.t4SaveStarted = true
                run.t4Saving = true
                run.t4Loading = true
                val ownerChunkIndex = chunkIndex(expectedFrom)
                releaseTestChunk(run, ownerChunkIndex)
                val saveEvent = ChunkSaveEvent(worldChunk)
                chunkStore.invoke(chunkRef, saveEvent)
                check(!saveEvent.isCancelled) { "T4 chunk save was cancelled" }
                val chunkGrid = run.world.chunkStore
                worldChunk.addKeepLoaded()
                worldChunk.setSaving(true)
                run.t4SaveKeepLoadedChunk = ownerChunkIndex
                val saver = chunkGrid.getSaver() ?: error("T4 chunk saver is unavailable")
                saver
                    .saveChunkColumn(
                        worldChunk.x,
                        worldChunk.z,
                        chunkGrid.store,
                        chunkRef,
                        null,
                        null,
                    ).whenComplete { _, saveFailure ->
                        run.world.execute {
                            if (run.cleanupFinished || run.stage != 4) {
                                releaseT4SaveHold(run)
                                return@execute
                            }
                            run.t4Saving = false
                            if (saveFailure != null) {
                                releaseT4SaveHold(run, markNeedsSaving = true)
                                run.t4Failure = "chunk save failed (${saveFailure.message})"
                                run.t4Loading = false
                                return@execute
                            }
                            try {
                                val currentRef =
                                    chunkGrid
                                        .getChunkReference(ownerChunkIndex)
                                        ?.takeIf { it.isValid }
                                        ?: error("T4 saved chunk reference missing")
                                val currentChunk =
                                    chunkGrid.store.getComponent(currentRef, WorldChunk.getComponentType())
                                        ?: error("T4 saved WorldChunk is missing")
                                currentChunk.consumeNeedsSaving()
                                currentChunk.setFlag(ChunkFlag.ON_DISK, true)
                                releaseT4SaveHold(run)
                                chunkGrid.remove(currentRef, RemoveReason.UNLOAD)
                                check(!currentRef.isValid) { "T4 chunk did not unload" }
                                run.t4Prepared = false
                                reloadT4(run, expectedFrom)
                            } catch (failure: Exception) {
                                releaseT4SaveHold(run, markNeedsSaving = true)
                                run.t4Failure = "unload failed (${failure.message})"
                                run.t4Prepared = false
                                run.t4Loading = false
                            }
                        }
                    }
            } catch (failure: Exception) {
                releaseT4SaveHold(run, markNeedsSaving = true)
                run.t4Failure = "unload failed (${failure.message})"
                run.t4Prepared = false
                run.t4Loading = false
            }
        }
        if (run.world.tick - run.stageStartTick >= T4_TIMEOUT_TICKS) {
            val anchorInactive =
                run.t4AnchorPosition?.let { anchorIsInactive(run, it) } ?: false
            val sectionNonTicking = sectionIsNonTicking(run, Position(10_272, 150, 10_000))
            finishCase(
                run,
                "unload reload persistence",
                false,
                sectionTimeoutDetail(
                    run,
                    "chunk operation timeout (prepared=${run.t4Prepared}, loading=${run.t4Loading}, " +
                        "saving=${run.t4Saving}, inserted=${run.t4ItemInserted}, " +
                        "anchorInactive=$anchorInactive, sectionNonTicking=$sectionNonTicking)",
                ),
            )
            startT5(run)
        }
    }

    private fun releaseT4SaveHold(
        run: TransportSelfTestRun.Run,
        markNeedsSaving: Boolean = false,
    ) {
        val ownerChunkIndex = run.t4SaveKeepLoadedChunk ?: return
        val chunkGrid = run.world.chunkStore
        val chunkRef = chunkGrid.getChunkReference(ownerChunkIndex)?.takeIf { it.isValid }
        val worldChunk =
            chunkRef?.let { chunkGrid.store.getComponent(it, WorldChunk.getComponentType()) }
        if (worldChunk != null) {
            if (markNeedsSaving) worldChunk.markNeedsSaving()
            worldChunk.setSaving(false)
            worldChunk.removeKeepLoaded()
        }
        run.t4SaveKeepLoadedChunk = null
    }

    private fun startT5(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        val first = Position(10_240, 150, 10_000)
        val second = Position(10_241, 150, 10_000)
        placeBlock(run, CraftoryIds.PIPE, first)
        placeBlock(run, CraftoryIds.PIPE, second)
        run.t5ItemInstanceId = null
        run.t5Record = null
        run.t5FreezeStartTick = null
        run.t5SectionRemoved = false
        run.t5AnchorDisabled = false
        run.t5OriginalAnchorChargeSeconds = null
        run.t5ReactivationStarted = false
        run.t5FreezePassed = false
        run.t5FreezeDetail = ""
        run.t5SkipReason = null
        run.caseTimerStartTick = null
        run.stage = 5
        run.stageStartTick = run.world.tick
    }

    private fun tickT5(
        run: TransportSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val position = Position(10_240, 150, 10_000)
        if (run.t5ReactivationStarted) {
            finishT5Reactivation(run)
            return
        }
        if (run.t5ItemInstanceId == null) {
            if (!caseTimerStarted(run, position)) {
                if (activeWaitExpired(run)) {
                    finishCase(
                        run,
                        "NonTicking freeze",
                        false,
                        sectionTimeoutDetail(run, "T5 section remained NonTicking before insertion"),
                    )
                    startT6(run)
                }
                return
            }
            val transport = ItemTransport.of(run.world)
            check(transport.insert(position.x, position.y, position.z, ITEM_ID, 1)) {
                "T5 pipe insert rejected"
            }
            run.t5ItemInstanceId = findItemInstance(transport, position, ITEM_ID)
                ?: error("T5 inserted item was not found")
            run.stageStartTick = run.world.tick
            return
        }
        if (!run.t5SectionRemoved) {
            val segment = findItemSegment(ItemTransport.of(run.world), run.t5ItemInstanceId)
            if (segment == null || segment.progress <= 0) {
                if (run.world.tick - run.stageStartTick >= T5_PROGRESS_TIMEOUT_TICKS) {
                    finishT5(
                        run,
                        passed = false,
                        detail =
                            sectionTimeoutDetail(
                                run,
                                "transit item did not progress before NonTicking " +
                                    "(progress=${segment?.progress})",
                            ),
                    )
                }
                return
            }
            run.testTickingSections.remove(sectionPosition(position))
            val anchorPosition = run.anchorPosition ?: error("T5 Wakebell Anchor position is missing")
            val anchorRef =
                BlockModule
                    .getBlockEntity(run.world, anchorPosition.x, anchorPosition.y, anchorPosition.z)
                    ?.takeIf { it.isValid }
                    ?: error("T5 Wakebell Anchor is missing")
            val anchor = store.getComponent(anchorRef, AnchorState.type) ?: error("T5 Anchor state is missing")
            val anchorBlock =
                store.getComponent(anchorRef, BlockModule.BlockStateInfo.getComponentType())
                    ?: error("T5 Anchor block state is missing")
            run.t5OriginalAnchorChargeSeconds = anchor.chargeSeconds
            anchor.chargeSeconds = 0
            anchorBlock.markNeedsSaving(store)
            run.t5AnchorDisabled = true
            run.t5SectionRemoved = true
            run.stageStartTick = run.world.tick
            return
        }
        val sectionRef =
            run.world.chunkStore
                .getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?.takeIf { it.isValid }
        val nonTickingType = ChunkStore.REGISTRY.getNonTickingComponentType()
        val nonTicking = sectionRef?.let { store.getComponent(it, nonTickingType) != null } ?: false
        if (run.t5Record == null) {
            if (nonTicking) {
                val record =
                    run.t5ItemInstanceId?.let { persistedT5Record(run, store, it) }
                if (record == null) {
                    if (run.world.tick - run.stageStartTick >= T5_HOLDER_TIMEOUT_TICKS) {
                        finishT5(
                            run,
                            false,
                            sectionTimeoutDetail(run, "persisted ItemPipe record missing in block holder"),
                        )
                    }
                    return
                }
                if (record.itemId != ITEM_ID || record.fromPos == record.toPos) {
                    finishT5(
                        run,
                        false,
                        "unexpected persisted record (${describeT5Record(record)})",
                    )
                    return
                }
                run.t5Record = record
                run.t5FreezeStartTick = run.world.tick
                return
            }
            if (run.world.tick - run.stageStartTick >= T5_MARKER_TIMEOUT_TICKS) {
                if (playerWithinRange(run.world, position, 64.0)) {
                    finishT5(
                        run,
                        false,
                        sectionTimeoutDetail(run, "NonTicking marker timeout"),
                        "player within range",
                    )
                } else {
                    finishT5(run, false, sectionTimeoutDetail(run, "NonTicking marker timeout"))
                }
            }
            return
        }
        if (run.world.tick - (run.t5FreezeStartTick ?: run.world.tick) < 40) return
        val frozenRecord = run.t5Record ?: return
        val persistedAgain =
            run.t5ItemInstanceId?.let { persistedT5Record(run, store, it) }
        val chunkRef =
            run.world.chunkStore
                .getChunkReference(chunkIndex(position))
                ?.takeIf { it.isValid }
        val chunkHeld = chunkRef?.let { store.getComponent(it, WorldChunk.getComponentType())?.shouldKeepLoaded() }
        val unchanged = persistedAgain != null && sameT5Record(frozenRecord, persistedAgain)
        finishT5(
            run,
            unchanged && nonTicking && chunkHeld == true,
            "before=${describeT5Record(frozenRecord)} after=${describeT5Record(persistedAgain)} " +
                "NonTicking=$nonTicking chunkHeld=$chunkHeld",
        )
    }

    private fun finishT5(
        run: TransportSelfTestRun.Run,
        passed: Boolean,
        detail: String,
        skipReason: String? = null,
    ) {
        if (!run.t5SectionRemoved) {
            if (skipReason == null) {
                finishCase(run, "NonTicking freeze", passed, detail)
            } else {
                reportSkip(run, "NonTicking freeze", skipReason)
            }
            startT6(run)
            return
        }
        run.t5FreezePassed = passed
        run.t5FreezeDetail = detail
        run.t5SkipReason = skipReason
        if (run.t5AnchorDisabled) {
            val anchorPosition = run.anchorPosition
            val anchorRef =
                anchorPosition?.let {
                    BlockModule.getBlockEntity(run.world, it.x, it.y, it.z)?.takeIf { ref -> ref.isValid }
                }
            val anchor =
                anchorRef?.let {
                    run.world.chunkStore.store
                        .getComponent(it, AnchorState.type)
                }
            val blockState =
                anchorRef?.let {
                    run.world.chunkStore.store
                        .getComponent(it, BlockModule.BlockStateInfo.getComponentType())
                }
            if (anchor != null && blockState != null) {
                anchor.chargeSeconds = run.t5OriginalAnchorChargeSeconds?.takeIf { it > 0 } ?: 900L
                blockState.markNeedsSaving(run.world.chunkStore.store)
            }
            run.t5AnchorDisabled = false
        }
        run.testTickingSections.add(sectionPosition(Position(10_240, 150, 10_000)))
        run.t5ReactivationStarted = true
        run.stageStartTick = run.world.tick
    }

    private fun finishT5Reactivation(run: TransportSelfTestRun.Run) {
        val position = Position(10_240, 150, 10_000)
        val nonTicking = sectionIsNonTicking(run, position)
        val segment = findItemSegment(ItemTransport.of(run.world), run.t5ItemInstanceId)
        if (!nonTicking && segment != null) {
            val expected = run.t5Record
            val restored =
                expected != null &&
                    segment.itemId == expected.id &&
                    segment.itemString == expected.itemId &&
                    BlockPos.pack(segment.from.x, segment.from.y, segment.from.z) == expected.fromPos &&
                    BlockPos.pack(segment.to.x, segment.to.y, segment.to.z) == expected.toPos &&
                    segment.progress >= expected.progress
            if (run.t5SkipReason != null) {
                reportSkip(run, "NonTicking freeze", run.t5SkipReason!!)
            } else {
                finishCase(
                    run,
                    "NonTicking freeze",
                    run.t5FreezePassed && restored,
                    "${run.t5FreezeDetail}; reactivated=${describeSegment(segment)}",
                )
            }
            startT6(run)
            return
        }
        if (run.world.tick - run.stageStartTick >= ACTIVE_SECTION_TIMEOUT_TICKS) {
            finishCase(
                run,
                "NonTicking freeze",
                false,
                sectionTimeoutDetail(
                    run,
                    "${run.t5FreezeDetail}; section reactivation timeout " +
                        "(NonTicking=$nonTicking, transit=${describeSegment(segment)})",
                ),
            )
            startT6(run)
        }
    }

    private fun persistedT5Record(
        run: TransportSelfTestRun.Run,
        store: Store<ChunkStore>,
        itemId: Long,
    ): ItemPipeTransitRecord? {
        val first = Position(10_240, 150, 10_000)
        val second = Position(10_241, 150, 10_000)
        return persistedPipeRecordAt(run, store, first, itemId)
            ?: persistedPipeRecordAt(run, store, second, itemId)
    }

    private fun persistedPipeRecordAt(
        run: TransportSelfTestRun.Run,
        store: Store<ChunkStore>,
        position: Position,
        itemId: Long,
    ): ItemPipeTransitRecord? {
        val sectionRef =
            run.world.chunkStore
                .getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?.takeIf { it.isValid }
                ?: return null
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: return null
        val holder = section.getBlockHolder(ChunkUtil.indexBlock(position.x, position.y, position.z)) ?: return null
        val pipe = holder.getComponent(ItemPipe.type) ?: return null
        return pipe.transitRecords().firstOrNull { it.id == itemId }
    }

    private fun sameT5Record(
        first: ItemPipeTransitRecord,
        second: ItemPipeTransitRecord,
    ): Boolean =
        first.id == second.id &&
            first.itemId == second.itemId &&
            first.fromPos == second.fromPos &&
            first.toPos == second.toPos &&
            first.progress == second.progress

    private fun describeT5Record(record: ItemPipeTransitRecord?): String =
        record?.let {
            "id=${it.id}, item=${it.itemId}, segment=${it.fromPos}->${it.toPos}, progress=${it.progress}"
        } ?: "missing"

    private fun describeSegment(segment: TransitSegment?): String =
        segment?.let {
            "id=${it.itemId}, item=${it.itemString}, segment=${it.from}->${it.to}, progress=${it.progress}"
        } ?: "missing"

    private fun startT6(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        run.stage = 6
        run.stageStartTick = run.world.tick
        val first = Position(10_250, 150, 10_000)
        val broken = first
        placeBlock(run, CraftoryIds.PIPE, first)
        val transport = ItemTransport.of(run.world)
        check(transport.insert(first.x, first.y, first.z, ITEM_ID, 1))
        run.t6ItemInstanceId = findItemInstance(transport, first, ITEM_ID)
        check(
            BlockOperations.setBlock(
                run.world.chunkStore,
                run.world.chunkStore
                    .getChunkSectionReferenceAtBlock(broken.x, broken.y, broken.z)
                    ?.takeIf { it.isValid }
                    ?: error("T6 section reference missing"),
                broken.x,
                broken.y,
                broken.z,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            ),
        ) { "T6 normal pipe removal failed" }
        ItemTransport.of(run.world).clearDisplaysInArea(
            AREA_MIN_X,
            AREA_MIN_Y,
            AREA_MIN_Z,
            AREA_MAX_X,
            AREA_MAX_Y,
            AREA_MAX_Z,
        )
        run.t6BreakTick = run.world.tick
    }

    private fun tickT6(run: TransportSelfTestRun.Run) {
        val broken = Position(10_250, 150, 10_000)
        val transport = ItemTransport.of(run.world)
        val dropped = countDropsNear(run.world, broken, ITEM_ID)
        if (dropped > 1) {
            finishCase(run, "break drops", false, "matchingDrops=$dropped")
            clearCurrentBlocks(run)
            beginCleanup(run)
            return
        }
        if (dropped == 1) {
            finishCase(run, "break drops", true, "matchingDrops=1")
            clearCurrentBlocks(run)
            beginCleanup(run)
            return
        }
        if (run.world.tick - (run.t6BreakTick ?: run.world.tick) >= 40) {
            val progress = run.t6ItemInstanceId?.let { transport.progressOf(it) }
            finishCase(
                run,
                "break drops",
                false,
                sectionTimeoutDetail(
                    run,
                    "matchingDrops=0; itemProgress=$progress; items=${transport.itemsInTransit()}",
                ),
            )
            clearCurrentBlocks(run)
            beginCleanup(run)
        }
    }

    private fun placeClawCase(
        run: TransportSelfTestRun.Run,
        center: Position,
        quantity: Int,
        filterItemId: String = "",
        filterColour: Int = 0,
        branches: Boolean = false,
    ): ClawLayout {
        val claw = center
        run.claw = claw
        placeBlock(run, CraftoryIds.SNATCHER_CLAW, claw, rotation = 0)
        val clawRef =
            BlockModule
                .getBlockEntity(run.world, claw.x, claw.y, claw.z)
                ?.takeIf { it.isValid }
                ?: error("Snatcher Claw block entity missing at $claw")
        val store = run.world.chunkStore.store
        val state =
            store.getComponent(clawRef, SnatcherClawState.type)
                ?: error("Snatcher Claw state component missing")
        state.filterItemId = filterItemId
        state.filterColour = filterColour
        val blockState =
            store.getComponent(clawRef, BlockModule.BlockStateInfo.getComponentType())
                ?: error("Snatcher Claw block state missing")
        val direction =
            snatcherClawFrontDirection(blockState, store)
                ?: error("Snatcher Claw front direction is unavailable")
        val forward = Position(direction.x(), 0, direction.z())
        val perpendicular =
            if (forward.x != 0) {
                Position(0, 0, 1)
            } else {
                Position(1, 0, 0)
            }
        val source = claw + forward
        placeBlock(run, CHEST_ID, source)
        val sourceContainer = containerAt(run.world, source) ?: error("source chest container missing")
        check(
            sourceContainer
                .setItemStackForSlot(
                    0.toShort(),
                    ItemStack(ITEM_ID, quantity),
                ).succeeded(),
        ) { "source chest item setup failed" }

        val firstPipe = claw + perpendicular
        run.targetPipe = firstPipe
        val destinationA: Position
        var destinationB: Position? = null
        if (branches) {
            val junction = firstPipe + perpendicular
            placeBlock(run, CraftoryIds.PIPE, firstPipe)
            placeBlock(run, CraftoryIds.PIPE, junction)
            val branchA = junction + forward
            val branchB = junction + (forward * -1)
            placeBlock(run, CraftoryIds.PIPE, branchA)
            placeBlock(run, CraftoryIds.PIPE, branchB)
            placeBlock(run, CHEST_ID, branchA + forward)
            destinationA = branchA + forward
            destinationB = branchB + (forward * -1)
            placeBlock(run, CHEST_ID, destinationB)
            check(
                ItemTransport.of(run.world).setColour(
                    branchA.x,
                    branchA.y,
                    branchA.z,
                    TransportColour.RED,
                ),
            ) { "T3 red branch daub failed" }
        } else {
            val pipes = (1..4).map { claw + (perpendicular * it) }
            pipes.forEach { placeBlock(run, CraftoryIds.PIPE, it) }
            destinationA = claw + (perpendicular * 5)
            placeBlock(run, CHEST_ID, destinationA)
        }
        val burner = firstPipe + forward
        placeBlock(run, CraftoryIds.BURNER, burner)
        fuelBurner(run, burner)
        return ClawLayout(source, destinationA, destinationB)
    }

    private fun clawDiagnostics(run: TransportSelfTestRun.Run): String {
        val claw = run.claw ?: return "claw=unknown"
        val store = run.world.chunkStore.store
        val ref =
            BlockModule
                .getBlockEntity(run.world, claw.x, claw.y, claw.z)
                ?.takeIf { it.isValid }
        val state = ref?.let { store.getComponent(it, SnatcherClawState.type) }
        val member = ref?.let { store.getComponent(it, EnergyMember.type) }
        val blockState = ref?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
        val areaActive = blockState?.let { CraftoryCore.isAreaActive(store, it) }
        val queryCount =
            store.getEntityCountFor(
                Query.and(
                    SnatcherClawState.type,
                    EnergyMember.type,
                    BlockModule.BlockStateInfo.getComponentType(),
                ),
            )
        val anchorPosition = run.anchorPosition
        val anchorRef =
            anchorPosition?.let {
                BlockModule.getBlockEntity(run.world, it.x, it.y, it.z)?.takeIf { anchor -> anchor.isValid }
            }
        val anchor = anchorRef?.let { store.getComponent(it, AnchorState.type) }
        val transport = ItemTransport.of(run.world)
        val sectionNonTicking = sectionIsNonTicking(run, claw)
        return "source=${containerQuantity(run, run.source)} " +
            "clawPipe=${run.targetPipe?.let { transport.isPipe(it.x, it.y, it.z) }} " +
            "refValid=${ref?.isValid} sectionNonTicking=$sectionNonTicking " +
            "queryCount=$queryCount areaActive=$areaActive " +
            "anchorActive=${anchor?.coverageActive == true && anchor.chargeSeconds > 0} fizz=${state?.fizzBuffer} " +
            "delivered=${member?.delivered} demand=${member?.demand} " +
            "transit=${transport.itemsInTransit()}"
    }

    private fun fuelBurner(
        run: TransportSelfTestRun.Run,
        position: Position,
    ) {
        val ref =
            BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
                ?: error("transport test burner entity missing")
        val store = run.world.chunkStore.store
        val state = store.getComponent(ref, BurnerState.type) ?: error("transport burner state missing")
        val member =
            store.getComponent(ref, studio.craftory.core.api.energy.EnergyMember.type)
                ?: error("transport burner energy member missing")
        val blockState =
            store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: error("transport burner block state missing")
        repeat(2) {
            check(
                MachineActions.addFuel(
                    state,
                    member,
                    blockState,
                    store,
                    run.world.tick,
                    "Plant_Crop_Mushroom_Common_Brown",
                ) > 0,
            ) { "transport burner fuel setup failed" }
        }
        check(MachineActions.kick(state, member, blockState, store, run.world.tick)) {
            "transport burner kick failed"
        }
    }

    private fun placeBlock(
        run: TransportSelfTestRun.Run,
        blockId: String,
        position: Position,
        rotation: Int = 0,
        keepAcrossCases: Boolean = false,
    ) {
        val world = run.world
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?: error("transport test section is not loaded at $position")
        val store = world.chunkStore.store
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: error("transport block section missing at $position")
        val blockType = BlockType.fromString(blockId) ?: error("missing transport test block $blockId")
        val blockIndex = BlockType.getAssetMap().getIndex(blockType.id)
        check(
            BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                blockIndex,
                blockType,
                rotation,
                0,
                SetBlockSettings.NO_UPDATE_STATE,
            ),
        ) { "failed to place $blockId at $position" }
        BlockEntity.ensureBlockEntity(
            store,
            sectionRef,
            section,
            position.x and 31,
            position.y and 31,
            position.z and 31,
            blockType,
            rotation,
        ) ?: error("failed to create block entity for $blockId at $position")
        if (!keepAcrossCases) run.currentBlocks.add(position)
        run.allBlocks.add(position)
    }

    private fun containerAt(
        world: World,
        position: Position?,
    ) = position
        ?.let { BlockModule.getBlockEntity(world, it.x, it.y, it.z) }
        ?.let {
            world.chunkStore.store
                .getComponent(it, ItemContainerBlock.getComponentType())
                ?.getItemContainer()
        }

    private fun containerQuantity(
        run: TransportSelfTestRun.Run,
        position: Position?,
    ): Int {
        val container = containerAt(run.world, position) ?: return 0
        var total = 0
        var slot = 0.toShort()
        while (slot < container.capacity) {
            total += container.getItemStack(slot)?.quantity ?: 0
            slot++
        }
        return total
    }

    private fun findItemInstance(
        transport: studio.craftory.core.api.transport.ItemTransportAccess,
        owner: Position,
        itemId: String,
    ): Long? {
        val packed = BlockPos.pack(owner.x, owner.y, owner.z)
        for (index in 0 until transport.itemsInTransit()) {
            if (transport.itemOwnerAt(index) == packed && transport.itemStringAt(index) == itemId) {
                return transport.itemIdAt(index)
            }
        }
        return null
    }

    private fun findItemSegment(
        transport: studio.craftory.core.api.transport.ItemTransportAccess,
        itemId: Long?,
    ): TransitSegment? {
        if (itemId == null) return null
        for (index in 0 until transport.itemsInTransit()) {
            if (transport.itemIdAt(index) == itemId) {
                return TransitSegment(
                    itemId,
                    transport.itemStringAt(index),
                    BlockPos.unpack(transport.itemOwnerAt(index)).toPosition(),
                    BlockPos.unpack(transport.itemTargetAt(index)).toPosition(),
                    transport.itemProgressAt(index),
                )
            }
        }
        return null
    }

    private fun BlockPos.toPosition(): Position = Position(x, y, z)

    private fun finishCase(
        run: TransportSelfTestRun.Run,
        name: String,
        passed: Boolean,
        detail: String = "",
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(
            Message.raw(
                "selftest: ${if (passed) "PASS" else "FAIL"} transport $name" +
                    if (detail.isBlank()) "" else " ($detail)",
            ),
        )
    }

    private fun reportSkip(
        run: TransportSelfTestRun.Run,
        name: String,
        reason: String,
    ) {
        run.context.sendMessage(Message.raw("selftest: SKIP transport $name ($reason)"))
    }

    private fun failCurrent(
        run: TransportSelfTestRun.Run,
        reason: String,
    ) {
        val name =
            when (run.stage) {
                1 -> "claw chest delivery"
                2 -> "claw filter"
                3 -> "red branch routing"
                4 -> "unload reload persistence"
                5 -> "NonTicking freeze"
                6 -> "break drops"
                else -> "runtime"
            }
        finishCase(run, name, false, reason)
    }

    private fun timeoutRemaining(run: TransportSelfTestRun.Run) {
        val names =
            arrayOf(
                "claw chest delivery",
                "claw filter",
                "red branch routing",
                "unload reload persistence",
                "NonTicking freeze",
                "break drops",
            )
        val firstUnfinished = (run.stage - 1).coerceAtLeast(0)
        for (index in firstUnfinished until names.size) {
            finishCase(
                run,
                names[index],
                false,
                sectionTimeoutDetail(run, run.setupFailure ?: "transport selftest timeout"),
            )
        }
        run.stage = 7
    }

    private fun clearCurrentBlocks(run: TransportSelfTestRun.Run) {
        val world = run.world
        run.currentBlocks.asReversed().forEach { position ->
            val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
            if (sectionRef != null && sectionRef.isValid) {
                BlockOperations.setBlock(
                    world.chunkStore,
                    sectionRef,
                    position.x,
                    position.y,
                    position.z,
                    BlockType.EMPTY_ID,
                    BlockType.EMPTY,
                    0,
                    0,
                    SetBlockSettings.NO_DROP_ITEMS,
                )
            }
        }
        run.currentBlocks.clear()
        ItemTransport.of(world).clearDisplaysInArea(
            AREA_MIN_X,
            AREA_MIN_Y,
            AREA_MIN_Z,
            AREA_MAX_X,
            AREA_MAX_Y,
            AREA_MAX_Z,
        )
        clearDrops(world)
    }

    private fun beginCleanup(run: TransportSelfTestRun.Run) {
        if (run.stage == 7 && run.cleanupLoading) return
        run.stage = 7
        releaseKeepLoaded(run)
        CraftoryCore.removeAnchorCoverage(run.world.chunkStore.store, TransportSelfTest.coverageId())
        val unloadedTestChunk = chunkIndex(Position(10_272, 150, 10_000))
        val ref = run.world.chunkStore.getChunkReference(unloadedTestChunk)
        if (run.allBlocks.any { chunkIndex(it) == unloadedTestChunk } && (ref == null || !ref.isValid)) {
            run.cleanupLoading = true
            run.world.chunkStore.getChunkReferenceAsync(unloadedTestChunk, 0).whenComplete { ref, failure ->
                run.world.execute {
                    run.cleanupLoading = false
                    run.cleanupLoadFailed = failure != null || ref == null || !ref.isValid
                    cleanupNow(run)
                }
            }
            return
        }
        cleanupNow(run)
    }

    private fun pollCleanup(run: TransportSelfTestRun.Run) {
        if (!run.cleanupFinished && !run.cleanupLoading) beginCleanup(run)
    }

    private fun cleanupNow(run: TransportSelfTestRun.Run) {
        if (run.cleanupFinished) return
        clearCurrentBlocks(run)
        run.allBlocks.forEach { position ->
            val sectionRef = run.world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
            if (sectionRef != null && sectionRef.isValid) {
                BlockOperations.setBlock(
                    run.world.chunkStore,
                    sectionRef,
                    position.x,
                    position.y,
                    position.z,
                    BlockType.EMPTY_ID,
                    BlockType.EMPTY,
                    0,
                    0,
                    SetBlockSettings.NO_DROP_ITEMS,
                )
            }
        }
        clearDrops(run.world)
        var remainingBlocks = 0
        for (position in run.allBlocks) {
            if (BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)?.isValid == true) {
                remainingBlocks++
            }
        }
        if (run.cleanupLoadFailed) remainingBlocks = maxOf(remainingBlocks, 1)
        val transport = ItemTransport.of(run.world)
        val pipePositions = run.allBlocks.mapTo(HashSet()) { BlockPos.pack(it.x, it.y, it.z) }
        var remainingItems = 0
        for (index in 0 until transport.itemsInTransit()) {
            if (transport.itemOwnerAt(index) in pipePositions) remainingItems++
        }
        val remainingDisplays =
            transport.displaysInArea(AREA_MIN_X, AREA_MIN_Y, AREA_MIN_Z, AREA_MAX_X, AREA_MAX_Y, AREA_MAX_Z)
        val remainingDrops = countDropsInArea(run.world)
        run.context.sendMessage(
            Message.raw(
                "selftest: transport cleanup " +
                    "(items=$remainingItems, displays=$remainingDisplays, " +
                    "blocks=$remainingBlocks, drops=$remainingDrops)",
            ),
        )
        run.context.sendMessage(
            Message.raw("selftest: transport ${run.passed} passed, ${run.failed} failed"),
        )
        run.cleanupFinished = true
        run.world.chunkStore.store
            .getResource(TransportSelfTestRun.type)
            .active = null
    }

    private fun clearDrops(world: World) {
        world.entityStore.store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: continue
                    if (isInTestArea(transform.position.x, transform.position.y, transform.position.z)) {
                        commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
                    }
                }
            },
        )
    }

    private fun countDropsInArea(world: World): Int {
        var count = 0
        world.entityStore.store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: continue
                    if (isInTestArea(transform.position.x, transform.position.y, transform.position.z)) count++
                }
            },
        )
        return count
    }

    private fun countDropsNear(
        world: World,
        center: Position,
        itemId: String,
    ): Int {
        var count = 0
        world.entityStore.store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val item = chunk.getComponent(index, ItemComponent.getComponentType()) ?: continue
                    if (item.itemStack?.itemId != itemId) continue
                    val point = chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: continue
                    if (kotlin.math.abs(point.x - center.x - 0.5) < 2.0 &&
                        kotlin.math.abs(point.y - center.y - 0.5) < 32.0 &&
                        kotlin.math.abs(point.z - center.z - 0.5) < 2.0
                    ) {
                        count++
                    }
                }
            },
        )
        return count
    }

    private fun playerWithinRange(
        world: World,
        position: Position,
        range: Double,
    ): Boolean {
        val store = world.entityStore.store
        val rangeSquared = range * range
        for (player in world.playerRefs) {
            val ref = player.reference ?: continue
            if (ref.store !== store) continue
            val point = store.getComponent(ref, TransformComponent.getComponentType())?.position ?: continue
            val dx = point.x - position.x - 0.5
            val dy = point.y - position.y - 0.5
            val dz = point.z - position.z - 0.5
            if (dx * dx + dy * dy + dz * dz <= rangeSquared) return true
        }
        return false
    }

    private fun releaseKeepLoaded(run: TransportSelfTestRun.Run) {
        val grid = run.world.chunkStore
        run.keepLoadedChunks.forEach { index ->
            val ref = grid.getChunkReference(index) ?: return@forEach
            if (ref.isValid) grid.store.getComponent(ref, WorldChunk.getComponentType())?.removeKeepLoaded()
        }
        run.keepLoadedChunks.clear()
        run.testTickingSections.clear()
    }

    private fun chunkIndex(position: Position): Long = ChunkUtil.indexChunk(position.x shr 5, position.z shr 5)

    private fun sectionPosition(position: Position): SectionPosition =
        SectionPosition(position.x shr 5, position.y shr 5, position.z shr 5)

    private fun isInTestArea(
        x: Double,
        y: Double,
        z: Double,
    ): Boolean =
        x in AREA_MIN_X.toDouble()..AREA_MAX_X.toDouble() &&
            y in AREA_MIN_Y.toDouble()..AREA_MAX_Y.toDouble() &&
            z in AREA_MIN_Z.toDouble()..AREA_MAX_Z.toDouble()

    private data class ClawLayout(
        val source: Position,
        val destinationA: Position,
        val destinationB: Position?,
    )

    private data class TransitSegment(
        val itemId: Long,
        val itemString: String?,
        val from: Position,
        val to: Position,
        val progress: Int,
    )

    companion object {
        private const val CHEST_ID = "Furniture_Crude_Chest_Small"
        private const val ITEM_ID = "Plant_Crop_Mushroom_Common_Brown"
        private const val MAX_RUNTIME_TICKS = 8_000L
        private const val T4_PROGRESS_TICKS = 10L
        private const val ANCHOR_READY_TIMEOUT_TICKS = 600L
        private const val ACTIVE_SECTION_TIMEOUT_TICKS = 600L
        private const val T5_PROGRESS_TIMEOUT_TICKS = 400L
        private const val T5_HOLDER_TIMEOUT_TICKS = 40L
        private const val T5_MARKER_TIMEOUT_TICKS = 1_000L
        private const val T4_TIMEOUT_TICKS = 1_500L
        private const val AREA_MIN_X = 10_190
        private const val AREA_MAX_X = 10_290
        private const val AREA_MIN_Y = 140
        private const val AREA_MAX_Y = 160
        private const val AREA_MIN_Z = 9_990
        private const val AREA_MAX_Z = 10_010
    }
}

internal object TransportSelfTest {
    private val ANCHOR_POSITION = Position(10_220, 150, 10_005)

    fun start(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
    ) {
        world.execute {
            val store = world.chunkStore.store
            val resource = store.getResource(TransportSelfTestRun.type)
            if (resource.active != null) return@execute
            val run = TransportSelfTestRun.Run(context, world, world.tick)
            run.anchorPosition = ANCHOR_POSITION
            resource.active = run
        }
    }

    internal fun coverageId(): Long = BlockPos.pack(ANCHOR_POSITION.x, ANCHOR_POSITION.y, ANCHOR_POSITION.z)
}
