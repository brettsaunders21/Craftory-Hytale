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
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.transport.ItemPipe
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
        var ready = false
        var setupFailure: String? = null
        var t4Failure: String? = null
        var pendingLoads = 0
        var stage = 0
        var stageStartTick = 0L
        var passed = 0
        var failed = 0
        var source: Position? = null
        var destinationA: Position? = null
        var destinationB: Position? = null
        var t4PipeComponent: ItemPipe? = null
        var t4ItemInstanceId: Long? = null
        var t4SerializedItems: String? = null
        var t4Loading = false
        var t5ItemInstanceId: Long? = null
        var t5Progress: Int? = null
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
            if (run.stage != 7 && store.externalData.world.tick - run.startTick >= MAX_RUNTIME_TICKS) {
                timeoutRemaining(run)
                beginCleanup(run)
                return
            }
            if (!run.ready) return
            if (run.setupFailure != null) {
                timeoutRemaining(run)
                beginCleanup(run)
                return
            }
            when (run.stage) {
                0 -> startT1(run)
                1 -> tickT1(run)
                2 -> tickT2(run)
                3 -> tickT3(run)
                4 -> tickT4(run)
                5 -> tickT5(run, store)
                7 -> pollCleanup(run)
            }
        } catch (failure: Exception) {
            failCurrent(run, "exception (${failure.message ?: failure.javaClass.simpleName})")
            beginCleanup(run)
        }
    }

    private fun startT1(run: TransportSelfTestRun.Run) {
        val layout = placeClawCase(run, Position(10_200, 150, 10_000), quantity = 3)
        run.source = layout.source
        run.destinationA = layout.destinationA
        run.stage = 1
        run.stageStartTick = run.world.tick
    }

    private fun tickT1(run: TransportSelfTestRun.Run) {
        if (containerQuantity(run, run.destinationA) >= 1) {
            finishCase(run, "claw chest delivery", true)
            startT2(run)
        } else if (run.world.tick - run.stageStartTick >= 400) {
            finishCase(run, "claw chest delivery", false, "400-tick timeout")
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
        run.stage = 2
        run.stageStartTick = run.world.tick
    }

    private fun tickT2(run: TransportSelfTestRun.Run) {
        if (run.world.tick - run.stageStartTick < 80) return
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
        run.stage = 3
        run.stageStartTick = run.world.tick
    }

    private fun tickT3(run: TransportSelfTestRun.Run) {
        val outputA = containerQuantity(run, run.destinationA)
        val outputB = containerQuantity(run, run.destinationB)
        if (outputA >= 3 || run.world.tick - run.stageStartTick >= 400) {
            finishCase(
                run,
                "red branch routing",
                outputA == 3 && outputB == 0,
                "branchA=$outputA branchB=$outputB",
            )
            startT4(run)
        }
    }

    private fun startT4(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        run.stage = 4
        run.stageStartTick = run.world.tick
        run.t4Loading = true
        val position = Position(10_272, 150, 10_000)
        val chunkIndex = chunkIndex(position)
        run.world.chunkStore.getChunkReferenceAsync(chunkIndex, 0).whenComplete { ref, failure ->
            run.world.execute {
                if (failure != null || ref == null || !ref.isValid) {
                    run.t4Failure = "initial chunk load failed (${failure?.message})"
                    run.t4Loading = false
                    return@execute
                }
                try {
                    placeBlock(run, CraftoryIds.PIPE, position)
                    val transport = ItemTransport.of(run.world)
                    check(transport.insert(position.x, position.y, position.z, ITEM_ID, 1))
                    run.t4ItemInstanceId = findItemInstance(transport, position, ITEM_ID)
                        ?: error("T4 inserted item was not found")
                    run.t4PipeComponent =
                        BlockModule
                            .getBlockEntity(run.world, position.x, position.y, position.z)
                            ?.let {
                                run.world.chunkStore.store
                                    .getComponent(it, ItemPipe.type)
                            }
                    check(run.t4PipeComponent != null)
                    run.world.chunkStore.remove(ref, RemoveReason.UNLOAD)
                    check(!ref.isValid) { "T4 chunk did not unload" }
                    run.t4SerializedItems = run.t4PipeComponent?.items
                    check(!run.t4SerializedItems.isNullOrBlank())
                    run.t4Loading = false
                    reloadT4(run, position)
                } catch (failure: Exception) {
                    run.t4Failure = "unload failed (${failure.message})"
                    run.t4Loading = false
                }
            }
        }
    }

    private fun reloadT4(
        run: TransportSelfTestRun.Run,
        position: Position,
    ) {
        val chunkIndex = chunkIndex(position)
        run.t4Loading = true
        run.world.chunkStore.getChunkReferenceAsync(chunkIndex, 0).whenComplete { ref, failure ->
            run.world.execute {
                try {
                    check(failure == null && ref != null && ref.isValid) {
                        "T4 reload failed (${failure?.message})"
                    }
                    val blockRef =
                        BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
                            ?: error("T4 reloaded pipe block entity is missing")
                    val pipe =
                        run.world.chunkStore.store
                            .getComponent(blockRef, ItemPipe.type)
                            ?: error("T4 reloaded ItemPipe component is missing")
                    val transport = ItemTransport.of(run.world)
                    val restoredItemId = findItemInstance(transport, position, ITEM_ID)
                    check(restoredItemId == run.t4ItemInstanceId) {
                        "T4 transit item id changed: before=${run.t4ItemInstanceId}, after=$restoredItemId"
                    }
                    check(pipe.items == run.t4SerializedItems) {
                        "T4 component persistence changed across reload"
                    }
                    finishCase(run, "unload reload persistence", restoredItemId != null)
                    startT5(run)
                } catch (failure: Exception) {
                    finishCase(run, "unload reload persistence", false, failure.message ?: "reload failed")
                    run.t4Loading = false
                    startT5(run)
                }
                run.t4Loading = false
            }
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
        if (!run.t4Loading && run.world.tick - run.stageStartTick >= 250) {
            finishCase(run, "unload reload persistence", false, "chunk operation timeout")
            startT5(run)
        }
    }

    private fun startT5(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        val first = Position(10_240, 150, 10_000)
        val second = Position(10_241, 150, 10_000)
        placeBlock(run, CraftoryIds.PIPE, first)
        placeBlock(run, CraftoryIds.PIPE, second)
        val transport = ItemTransport.of(run.world)
        check(transport.insert(first.x, first.y, first.z, ITEM_ID, 1))
        run.t5ItemInstanceId = findItemInstance(transport, first, ITEM_ID)
        run.stage = 5
        run.stageStartTick = run.world.tick
        val store = run.world.chunkStore.store
        CraftoryCore.removeAnchorCoverage(store, TransportSelfTest.coverageId())
        releaseKeepLoaded(run)
        val sectionRef = run.world.chunkStore.getChunkSectionReferenceAtBlock(first.x, first.y, first.z)
        val section = sectionRef?.let { store.getComponent(it, ChunkSection.getComponentType()) }
        section?.resetActiveTimer()
        if (playerWithinRange(run.world, first, 64.0)) {
            reportSkip(run, "NonTicking freeze", "player within range")
            startT6(run)
            return
        }
    }

    private fun tickT5(
        run: TransportSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val position = Position(10_240, 150, 10_000)
        val sectionRef = run.world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
        val nonTickingType = ChunkStore.REGISTRY.getNonTickingComponentType()
        val nonTicking = sectionRef?.let { store.getComponent(it, nonTickingType) != null } ?: false
        if (run.t5Progress == null) {
            if (nonTicking) {
                run.t5Progress =
                    run.t5ItemInstanceId?.let { ItemTransport.of(run.world).progressOf(it) }
                if (run.t5Progress == null) {
                    finishCase(run, "NonTicking freeze", false, "transit item missing")
                    startT6(run)
                    return
                }
                run.stageStartTick = run.world.tick
                return
            }
            if (run.world.tick - run.stageStartTick >= T5_MARKER_TIMEOUT_TICKS) {
                if (playerWithinRange(run.world, position, 64.0)) {
                    reportSkip(run, "NonTicking freeze", "player within range")
                } else {
                    finishCase(run, "NonTicking freeze", false, "NonTicking marker timeout")
                }
                startT6(run)
            }
            return
        }
        if (run.world.tick - run.stageStartTick < 40) return
        val progress = run.t5ItemInstanceId?.let { ItemTransport.of(run.world).progressOf(it) }
        finishCase(
            run,
            "NonTicking freeze",
            progress == run.t5Progress,
            "before=${run.t5Progress} after=$progress",
        )
        startT6(run)
    }

    private fun startT6(run: TransportSelfTestRun.Run) {
        clearCurrentBlocks(run)
        run.stage = 6
        run.stageStartTick = run.world.tick
        val first = Position(10_250, 150, 10_000)
        val broken = Position(10_251, 150, 10_000)
        placeBlock(run, CraftoryIds.PIPE, first)
        placeBlock(run, CraftoryIds.PIPE, broken)
        val transport = ItemTransport.of(run.world)
        check(transport.insert(first.x, first.y, first.z, ITEM_ID, 1))
        check(
            BlockOperations.setBlock(
                run.world.chunkStore,
                run.world.chunkStore.getChunkSectionReferenceAtBlock(broken.x, broken.y, broken.z)
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
        val dropped = countDropsNear(run.world, broken, ITEM_ID)
        finishCase(run, "break drops", dropped == 1, "matchingDrops=$dropped")
        clearCurrentBlocks(run)
        beginCleanup(run)
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
        placeBlock(run, CraftoryIds.SNATCHER_CLAW, claw, rotation = 0)
        val clawRef =
            BlockModule.getBlockEntity(run.world, claw.x, claw.y, claw.z)
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

        val firstPipe = claw + (forward * -1)
        val destinationA: Position
        var destinationB: Position? = null
        if (branches) {
            val junction = claw + (forward * -2)
            placeBlock(run, CraftoryIds.PIPE, firstPipe)
            placeBlock(run, CraftoryIds.PIPE, junction)
            val branchA = junction + perpendicular
            val branchB = junction + (perpendicular * -1)
            placeBlock(run, CraftoryIds.PIPE, branchA)
            placeBlock(run, CraftoryIds.PIPE, branchB)
            placeBlock(run, CHEST_ID, branchA + perpendicular)
            destinationA = branchA + perpendicular
            destinationB = branchB + (perpendicular * -1)
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
            val pipes = (1..4).map { claw + (forward * -it) }
            pipes.forEach { placeBlock(run, CraftoryIds.PIPE, it) }
            destinationA = claw + (forward * -5)
            placeBlock(run, CHEST_ID, destinationA)
        }
        val burner = firstPipe + perpendicular
        placeBlock(run, CraftoryIds.BURNER, burner)
        fuelBurner(run, burner)
        return ClawLayout(source, destinationA, destinationB)
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
        run.currentBlocks.add(position)
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
            finishCase(run, names[index], false, run.setupFailure ?: "transport selftest timeout")
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
                        kotlin.math.abs(point.y - center.y - 0.5) < 2.0 &&
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
    }

    private fun chunkIndex(position: Position): Long = ChunkUtil.indexChunk(position.x shr 5, position.z shr 5)

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

    companion object {
        private const val CHEST_ID = "Furniture_Crude_Chest_Small"
        private const val ITEM_ID = "Plant_Crop_Mushroom_Common_Brown"
        private const val MAX_RUNTIME_TICKS = 3_000L
        private const val T5_MARKER_TIMEOUT_TICKS = 1_000L
        private const val AREA_MIN_X = 10_190
        private const val AREA_MAX_X = 10_290
        private const val AREA_MIN_Y = 140
        private const val AREA_MAX_Y = 160
        private const val AREA_MIN_Z = 9_990
        private const val AREA_MAX_Z = 10_010
    }
}

internal object TransportSelfTest {
    fun start(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
    ) {
        world.execute {
            val store = world.chunkStore.store
            val resource = store.getResource(TransportSelfTestRun.type)
            if (resource.active != null) return@execute
            val run = TransportSelfTestRun.Run(context, world, world.tick)
            resource.active = run
            val chunks =
                longArrayOf(
                    ChunkUtil.indexChunk(318, 312),
                    ChunkUtil.indexChunk(319, 312),
                    ChunkUtil.indexChunk(320, 312),
                )
            chunks.forEach { index ->
                val chunkX = (index shr 32).toInt()
                val chunkZ = index.toInt()
                CraftoryCore.setAnchorCoverage(store, coverageId(), chunkX, chunkZ, true)
            }
            run.pendingLoads = chunks.size
            chunks.forEach { index ->
                world.chunkStore.getChunkReferenceAsync(index, GetChunkFlags.SET_TICKING).whenComplete { ref, failure ->
                    world.execute {
                        if (failure != null || ref == null || !ref.isValid) {
                            run.setupFailure = "test area chunk load failed (${failure?.message})"
                        } else {
                            val worldChunk = store.getComponent(ref, WorldChunk.getComponentType())
                            if (worldChunk != null && run.keepLoadedChunks.add(index)) worldChunk.addKeepLoaded()
                        }
                        run.pendingLoads--
                        if (run.pendingLoads == 0) run.ready = true
                    }
                }
            }
        }
    }

    internal fun coverageId(): Long = BlockPos.pack(10_200, 150, 10_000)
}
