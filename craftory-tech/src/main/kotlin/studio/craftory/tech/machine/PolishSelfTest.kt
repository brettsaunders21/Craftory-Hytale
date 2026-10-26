package studio.craftory.tech.machine

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.core.api.multiblock.MultiblockTemplate
import studio.craftory.core.api.transport.FluidTank
import studio.craftory.core.api.transport.FluidTransport
import studio.craftory.core.api.transport.ItemPipe
import studio.craftory.core.api.transport.ItemTransport
import studio.craftory.tech.CraftoryIds

internal class PolishSelfTestRun : Resource<ChunkStore> {
    internal class Run(
        val context: com.hypixel.hytale.server.core.command.system.CommandContext,
        val world: World,
        val startTick: Long,
    ) {
        var sectionRef: Ref<ChunkStore>? = null
        var loading = false
        var ready = false
        var setupFailure: String? = null
        var stage = 0
        var stageStartTick = startTick
        var previousAnchorMaxChargeMinutes = 0
        var bandIndex = 0
        var bandStarted = false
        var bandStartTick = 0L
        var expectedAmount = 0L
        var expectedState = ""
        var cleanupStartTick = 0L
        var visitStarted = false
        var visitStartedTick = 0L
        var visitUuid: String? = null
        var summarySent = false
        var passed = 0
        var failed = 0
        var completed = false
    }

    var active: Run? = null

    override fun clone(): Resource<ChunkStore> = PolishSelfTestRun()

    companion object {
        lateinit var type: ResourceType<ChunkStore, PolishSelfTestRun>
    }
}

@Suppress("TooManyFunctions", "LargeClass")
internal class PolishSelfTestSystem : TickingSystem<ChunkStore>() {
    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught", "LongMethod", "MaxLineLength")
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val run = store.getResource(PolishSelfTestRun.type).active ?: return
        if (run.completed) return
        try {
            if (!run.ready) {
                if (run.world.tick - run.stageStartTick > SETUP_TIMEOUT_TICKS) {
                    run.setupFailure =
                        "setup phase timeout elapsed=${run.world.tick - run.stageStartTick} loading=${run.loading}"
                    finishFailedSetup(run, store)
                    return
                }
                if (run.setupFailure != null) {
                    finishFailedSetup(run, store)
                } else if (run.sectionRef != null) {
                    keepTestSectionTicking(run, store)
                    pollAnchorReady(run, store)
                } else if (!run.loading) {
                    prepare(run, store)
                }
                return
            }
            keepTestSectionTicking(run, store)
            val phase = run.stage
            when (phase) {
                0 -> setupClawCase(run, store)
                1 -> checkClawCase(run, store)
                2 -> checkPipeShapes(run)
                3 -> checkRemovedPipeArm(run)
                4 -> checkCornerTransfer(run)
                5 -> tickCanisterBands(run)
                6 -> tickSloshtankBands(run)
                7 -> setupMultiblocks(run, store)
                8 -> checkUnformedBoilerAndAssemble(run, store)
                9 -> checkFormedBoilerAndDamage(run, store)
                10 -> checkDamagedController(run, store)
                11 -> cleanup(run, store)
                12 -> pollCleanup(run, store)
            }
            if (!run.completed && run.stage == phase && run.world.tick - run.stageStartTick > phaseTimeout(phase)) {
                val detail = phaseDetails(run)
                PolishSelfTest.fail(
                    run,
                    "phase ${phaseName(phase)}",
                    "timeout elapsed=${run.world.tick - run.stageStartTick} $detail",
                )
                if (phase >= 11) {
                    finish(run, store)
                } else {
                    run.stage = 11
                    run.stageStartTick = run.world.tick
                }
            }
        } catch (failure: Exception) {
            val detail = failure.message ?: failure::class.simpleName.orEmpty()
            if (!run.ready) {
                run.setupFailure = detail
                run.loading = false
            } else if (run.stage >= 11) {
                PolishSelfTest.fail(run, "phase ${phaseName(run.stage)}", detail)
                finish(run, store)
            } else {
                PolishSelfTest.fail(run, "phase ${phaseName(run.stage)}", detail)
                run.stage = 11
                run.stageStartTick = run.world.tick
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "LongMethod", "MaxLineLength")
    private fun prepare(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        run.loading = true
        val anchor = PolishSelfTest.ANCHOR
        run.world.chunkStore
            .getChunkSectionReferenceAtBlockAsync(
                anchor.x,
                anchor.y,
                anchor.z,
                GetChunkFlags.SET_TICKING,
            ).whenComplete { sectionRef, error ->
                run.world.execute {
                    if (error != null || sectionRef == null) {
                        run.setupFailure = error?.message ?: "burner section unavailable"
                        run.loading = false
                        return@execute
                    }
                    try {
                        run.sectionRef = sectionRef
                        val testPositions = CraftorySelfTest.testPositions(run.world)
                        check(PolishSelfTest.fixturePositions().all { it in testPositions }) {
                            "polish fixtures missing from burner test positions"
                        }
                        check(PolishSelfTest.fixturesShareSection()) {
                            "polish fixture crosses the burner chunk section"
                        }
                        PolishSelfTest.clearTestArea(run, store)
                        PolishSelfTest.place(
                            run,
                            store,
                            CraftoryIds.WAKEBELL_ANCHOR,
                            anchor,
                            AnchorState.type,
                        )
                        run.previousAnchorMaxChargeMinutes = CraftoryCore.anchorMaxChargeMinutes()
                        CraftoryCore.setAnchorMaxChargeMinutesInMemory(
                            run.previousAnchorMaxChargeMinutes.coerceAtLeast(1),
                        )
                        val anchorRef =
                            BlockModule.getBlockEntity(run.world, anchor.x, anchor.y, anchor.z)
                                ?: error("selftest anchor entity missing")
                        val anchorState =
                            store.getComponent(anchorRef, AnchorState.type)
                                ?: error("selftest anchor state missing")
                        anchorState.chargeSeconds = 60
                        val blockState =
                            store.getComponent(anchorRef, BlockModule.BlockStateInfo.getComponentType())
                                ?: error("selftest anchor block state missing")
                        MachineActions.activateAnchor(
                            run.world,
                            anchorState,
                            blockState,
                            store,
                            BlockPos(anchor.x, anchor.y, anchor.z),
                        )
                        run.loading = false
                        run.stageStartTick = run.world.tick
                    } catch (failure: Exception) {
                        run.setupFailure = failure.message ?: failure::class.simpleName.orEmpty()
                        run.loading = false
                    }
                }
            }
    }

    private fun pollAnchorReady(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val anchor =
            BlockModule.getBlockEntity(
                run.world,
                PolishSelfTest.ANCHOR.x,
                PolishSelfTest.ANCHOR.y,
                PolishSelfTest.ANCHOR.z,
            )
        val state = anchor?.takeIf { it.isValid }?.let { store.getComponent(it, AnchorState.type) }
        if (
            state?.coverageActive == true &&
            state.pendingChunkLoads.isEmpty() &&
            state.keepLoadedChunks.size >= 9
        ) {
            run.ready = true
            run.stageStartTick = run.world.tick
            run.context.sendMessage(Message.raw("selftest: starting polish cases"))
        } else if (run.world.tick - run.stageStartTick >= ANCHOR_TIMEOUT_TICKS) {
            run.setupFailure = "anchor coverage did not become active"
        }
    }

    private fun keepTestSectionTicking(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val ref = run.sectionRef ?: return
        store.getComponent(ref, ChunkSection.getComponentType())?.resetActiveTimer()
    }

    private fun setupClawCase(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val claw = PolishSelfTest.CLAW
        PolishSelfTest.place(run, store, CraftoryIds.SNATCHER_CLAW, claw, SnatcherClawState.type)
        val clawRef =
            BlockModule
                .getBlockEntity(run.world, claw.x, claw.y, claw.z)
                ?.takeIf { it.isValid }
                ?: error("claw entity missing")
        val blockState =
            store.getComponent(clawRef, BlockModule.BlockStateInfo.getComponentType())
                ?: error("claw block state missing")
        val direction =
            snatcherClawFrontDirection(blockState, store)
                ?: error("claw output direction unavailable")
        val front = PolishSelfTest.position(direction.x(), direction.y(), direction.z())
        val source = PolishSelfTest.offset(claw, front, 1)
        val pipe = PolishSelfTest.offset(claw, front, -1)
        val destination = PolishSelfTest.offset(claw, front, -2)
        PolishSelfTest.place(run, store, CHEST_ID, source, ItemContainerBlock.getComponentType())
        PolishSelfTest.place(run, store, CraftoryIds.PIPE, pipe, ItemPipe.type)
        PolishSelfTest.place(run, store, CHEST_ID, destination, ItemContainerBlock.getComponentType())
        val sourceContainer = PolishSelfTest.containerAt(run, store, source) ?: error("claw source container missing")
        check(
            sourceContainer.setItemStackForSlot(0.toShort(), ItemStack(ITEM_ID, 1)).succeeded(),
        ) { "claw source item setup failed" }
        val state = store.getComponent(clawRef, SnatcherClawState.type) ?: error("claw state missing")
        state.fizzBuffer = SnatcherClawState.FIZZ_PER_ITEM
        state.lastExtractTick = run.world.tick - SnatcherClawState.EXTRACT_INTERVAL_TICKS
        run.stageStartTick = run.world.tick
        run.stage = 1
    }

    @Suppress("CyclomaticComplexMethod")
    private fun checkClawCase(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        if (run.world.tick - run.stageStartTick < CLAW_WAIT_TICKS) return
        val claw = PolishSelfTest.CLAW
        val clawRef = BlockModule.getBlockEntity(run.world, claw.x, claw.y, claw.z)
        val clawValid = clawRef?.isValid == true
        val clawType = PolishSelfTest.blockTypeAt(run, claw)
        val clawPreserved = clawType?.let(::baseBlockId) == CraftoryIds.SNATCHER_CLAW
        val direction =
            clawRef
                ?.takeIf { it.isValid }
                ?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
                ?.let { snatcherClawFrontDirection(it, store) }
        val behind =
            direction?.let {
                PolishSelfTest.offset(claw, PolishSelfTest.position(it.x(), it.y(), it.z()), -1)
            }
        val state = behind?.let { PolishSelfTest.stateName(run, it, CraftoryIds.PIPE) }
        val mask = state?.removePrefix("C")?.toIntOrNull()
        val faceBit =
            direction?.let { face ->
                GutterpipeMask.FACES
                    .firstOrNull { it.dx == face.x() && it.dy == face.y() && it.dz == face.z() }
                    ?.mask
            }
        val source =
            direction?.let {
                PolishSelfTest.offset(claw, PolishSelfTest.position(it.x(), it.y(), it.z()), 1)
            }
        val pipe = behind
        val destination =
            direction?.let {
                PolishSelfTest.offset(claw, PolishSelfTest.position(it.x(), it.y(), it.z()), -2)
            }
        val delivered = destination?.let { PolishSelfTest.containerQuantity(run, store, it) } ?: 0
        val pipeItems =
            pipe?.let { ItemTransport.of(run.world).itemsOwnedBy(it.x, it.y, it.z) } ?: 0
        val sourceItems = source?.let { PolishSelfTest.containerQuantity(run, store, it) } ?: 1
        val passed =
            clawValid &&
                clawPreserved &&
                faceBit != null &&
                mask != null &&
                mask.and(faceBit) != 0 &&
                delivered + pipeItems > 0 &&
                sourceItems == 0
        PolishSelfTest.report(
            run,
            "a claw and pipe",
            passed,
            "clawValid=$clawValid claw=$clawPreserved pipeState=$state faceBit=$faceBit " +
                "delivered=$delivered pipeItems=$pipeItems sourceItems=$sourceItems",
        )
        PolishSelfTest.clearFixtures(run, store, PolishSelfTest.clawPositions())
        setupPipeShapes(run, store)
    }

    private fun setupPipeShapes(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        PolishSelfTest.PIPE_SHAPES.forEach { shape ->
            (listOf(shape.center) + shape.arms).forEach { position ->
                PolishSelfTest.place(run, store, CraftoryIds.PIPE, position, ItemPipe.type)
            }
        }
        run.stage = 2
        run.stageStartTick = run.world.tick
    }

    private fun checkPipeShapes(run: PolishSelfTestRun.Run) {
        if (run.world.tick - run.stageStartTick < SHAPE_WAIT_TICKS) return
        PolishSelfTest.PIPE_SHAPES.forEach { shape ->
            val state = PolishSelfTest.stateName(run, shape.center, CraftoryIds.PIPE)
            PolishSelfTest.report(
                run,
                "b ${shape.name} shape",
                state == GutterpipeMask.stateName(shape.mask),
                "state=$state expected=${GutterpipeMask.stateName(shape.mask)}",
            )
        }
        val plus = PolishSelfTest.PIPE_SHAPES.last()
        CraftorySelfTest.clear(
            run.world,
            run.sectionRef ?: error("section reference missing"),
            listOf(plus.arms.last()),
        )
        run.stage = 3
        run.stageStartTick = run.world.tick
    }

    private fun checkRemovedPipeArm(run: PolishSelfTestRun.Run) {
        if (run.world.tick - run.stageStartTick < SHAPE_WAIT_TICKS) return
        val plus = PolishSelfTest.PIPE_SHAPES.last()
        val state = PolishSelfTest.stateName(run, plus.center, CraftoryIds.PIPE)
        val expected = GutterpipeMask.stateName(plus.mask and plus.removedArmMask.inv())
        PolishSelfTest.report(run, "b removed arm", state == expected, "state=$state expected=$expected")
        val corner = PolishSelfTest.PIPE_SHAPES[1]
        val destination =
            PolishSelfTest.position(
                corner.center.x,
                corner.center.y,
                corner.center.z + 2,
            )
        PolishSelfTest.place(
            run,
            run.world.chunkStore.store,
            CHEST_ID,
            destination,
            ItemContainerBlock.getComponentType(),
        )
        val sourcePipe = corner.arms.first()
        val inserted =
            ItemTransport.of(run.world).insert(
                sourcePipe.x,
                sourcePipe.y,
                sourcePipe.z,
                ITEM_ID,
                1,
            )
        PolishSelfTest.report(run, "b corner item insert", inserted, "inserted=$inserted source=$sourcePipe")
        run.stage = 4
        run.stageStartTick = run.world.tick
    }

    private fun checkCornerTransfer(run: PolishSelfTestRun.Run) {
        if (run.world.tick - run.stageStartTick < ITEM_WAIT_TICKS) return
        val corner = PolishSelfTest.PIPE_SHAPES[1]
        val destination =
            PolishSelfTest.position(
                corner.center.x,
                corner.center.y,
                corner.center.z + 2,
            )
        val quantity = PolishSelfTest.containerQuantity(run, run.world.chunkStore.store, destination)
        PolishSelfTest.report(run, "b corner item traversal", quantity > 0, "destinationItems=$quantity")
        PolishSelfTest.clearFixtures(run, run.world.chunkStore.store, PolishSelfTest.pipeShapePositions())
        val store = run.world.chunkStore.store
        PolishSelfTest.place(run, store, CraftoryIds.CANISTER, PolishSelfTest.CANISTER, EnergyMember.type)
        PolishSelfTest.place(run, store, CraftoryIds.SLOSHTANK, PolishSelfTest.SLOSHTANK, FluidTank.type)
        run.stage = 5
        run.stageStartTick = run.world.tick
        run.bandIndex = 0
        run.bandStarted = false
    }

    private fun tickCanisterBands(run: PolishSelfTestRun.Run) {
        val energy = CraftoryCore.energyAccess(run.world)
        val position = PolishSelfTest.CANISTER
        val packed = BlockPos.pack(position.x, position.y, position.z)
        if (!run.bandStarted) {
            if (run.bandIndex >= PolishSelfTest.BANDS.size) {
                run.stage = 6
                run.stageStartTick = run.world.tick
                run.bandIndex = 0
                return
            }
            val capacity = energy.capacity(packed)
            if (capacity <= 0) {
                if (run.world.tick - run.stageStartTick >= BAND_TIMEOUT_TICKS) {
                    PolishSelfTest.report(run, "c fizz canister capacity", false, "capacity=$capacity")
                    run.stage = 6
                    run.bandIndex = 0
                }
                return
            }
            val band = PolishSelfTest.BANDS[run.bandIndex]
            run.expectedAmount = capacity * band.percent / 100
            run.expectedState = band.state
            val stored = energy.setStored(packed, run.expectedAmount, 0)
            run.bandStartTick = run.world.tick
            run.bandStarted = true
            if (stored != run.expectedAmount) {
                PolishSelfTest.report(
                    run,
                    "c fizz canister ${band.state}",
                    false,
                    "setStored=$stored expected=${run.expectedAmount} capacity=$capacity",
                )
                run.bandIndex++
                run.bandStarted = false
                run.stageStartTick = run.world.tick
            }
            return
        }
        val state = PolishSelfTest.stateName(run, position, CraftoryIds.CANISTER)
        val stored = energy.stored(packed)
        val matches = state == run.expectedState && stored == run.expectedAmount
        if (matches || run.world.tick - run.bandStartTick >= BAND_TIMEOUT_TICKS) {
            PolishSelfTest.report(
                run,
                "c fizz canister ${run.expectedState}",
                matches,
                "state=$state stored=$stored expected=${run.expectedAmount}",
            )
            run.bandIndex++
            run.bandStarted = false
            run.stageStartTick = run.world.tick
        }
    }

    private fun tickSloshtankBands(run: PolishSelfTestRun.Run) {
        val fluid = FluidTransport.of(run.world)
        val position = PolishSelfTest.SLOSHTANK
        if (!run.bandStarted) {
            if (run.bandIndex >= PolishSelfTest.BANDS.size) {
                run.stage = 7
                run.stageStartTick = run.world.tick
                return
            }
            val capacity = fluid.capacity(position.x, position.y, position.z)
            if (capacity <= 0) {
                if (run.world.tick - run.stageStartTick >= BAND_TIMEOUT_TICKS) {
                    PolishSelfTest.report(run, "d sloshtank capacity", false, "capacity=$capacity")
                    run.stage = 7
                }
                return
            }
            val band = PolishSelfTest.BANDS[run.bandIndex]
            run.expectedAmount = capacity * band.percent / 100
            run.expectedState = band.state
            val current = fluid.amount(position.x, position.y, position.z)
            if (current > 0) fluid.drain(position.x, position.y, position.z, current)
            val inserted =
                if (run.expectedAmount == 0L) {
                    0L
                } else {
                    fluid.insert(position.x, position.y, position.z, WATER_SOURCE, run.expectedAmount)
                }
            run.bandStartTick = run.world.tick
            run.bandStarted = true
            if (inserted != run.expectedAmount) {
                PolishSelfTest.report(
                    run,
                    "d sloshtank ${band.state}",
                    false,
                    "inserted=$inserted expected=${run.expectedAmount} capacity=$capacity",
                )
                run.bandIndex++
                run.bandStarted = false
                run.stageStartTick = run.world.tick
            }
            return
        }
        val state = PolishSelfTest.stateName(run, position, CraftoryIds.SLOSHTANK)
        val amount = fluid.amount(position.x, position.y, position.z)
        val matches = state == run.expectedState && amount == run.expectedAmount
        if (matches || run.world.tick - run.bandStartTick >= BAND_TIMEOUT_TICKS) {
            PolishSelfTest.report(
                run,
                "d sloshtank ${run.expectedState}",
                matches,
                "state=$state amount=$amount expected=${run.expectedAmount}",
            )
            run.bandIndex++
            run.bandStarted = false
            run.stageStartTick = run.world.tick
        }
    }

    private fun cleanup(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val anchorId =
            BlockPos.pack(
                PolishSelfTest.ANCHOR.x,
                PolishSelfTest.ANCHOR.y,
                PolishSelfTest.ANCHOR.z,
            )
        CraftoryCore.removeAnchorCoverage(store, anchorId)
        PolishSelfTest.clearTestArea(run, store)
        CraftoryCore.setAnchorMaxChargeMinutesInMemory(run.previousAnchorMaxChargeMinutes)
        run.cleanupStartTick = run.world.tick
        run.stage = 12
        run.stageStartTick = run.world.tick
    }

    private fun setupMultiblocks(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        PolishSelfTest.place(run, store, CraftoryIds.GUTTER_BOILER, PolishSelfTest.BOILER, BoilerState.type)
        PolishSelfTest.place(run, store, CraftoryIds.GASBAG, PolishSelfTest.GASBAG, GasbagState.type)
        val boilerRef = PolishSelfTest.controllerAt(run, PolishSelfTest.BOILER) ?: error("boiler controller missing")
        val boiler = store.getComponent(boilerRef, BoilerState.type) ?: error("boiler state missing")
        GutterBoilerActions.acceptWater(boiler, WATER_SOURCE, GutterBoilerRules.CAPACITY)
        GutterBoilerActions.addFuel(boiler, CraftoryIds.FIZZ_CHARGE)
        run.stageStartTick = run.world.tick
        run.stage = 8
    }

    private fun checkUnformedBoilerAndAssemble(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        if (run.world.tick - run.stageStartTick < MULTIBLOCK_WAIT_TICKS) return
        val boilerRef = PolishSelfTest.controllerAt(run, PolishSelfTest.BOILER) ?: error("boiler controller missing")
        val boiler = store.getComponent(boilerRef, BoilerState.type) ?: error("boiler state missing")
        val controller = store.getComponent(boilerRef, MultiblockController.type) ?: error("boiler multiblock missing")
        val member = store.getComponent(boilerRef, EnergyMember.type) ?: error("boiler energy member missing")
        val state = PolishSelfTest.stateName(run, PolishSelfTest.BOILER, CraftoryIds.GUTTER_BOILER)
        val passed =
            controller.state != MultiblockState.FORMED &&
                boiler.pressure == 0 &&
                member.production == 0L &&
                state == "Off"
        PolishSelfTest.report(
            run,
            "f unformed boiler idle",
            passed,
            "structure=${controller.state} pressure=${boiler.pressure} production=${member.production} state=$state",
        )
        val gasbagRef = PolishSelfTest.controllerAt(run, PolishSelfTest.GASBAG) ?: error("gasbag controller missing")
        val boilerFormed = assembleAndKick(run, store, boilerRef, GutterBoilerTemplate.template)
        val gasbagFormed = assembleAndKick(run, store, gasbagRef, GasbagTemplate.template)
        PolishSelfTest.report(
            run,
            "f boiler/gasbag formed",
            boilerFormed && gasbagFormed,
            "boiler=$boilerFormed gasbag=$gasbagFormed",
        )
        run.stageStartTick = run.world.tick
        run.stage = 9
    }

    private fun checkFormedBoilerAndDamage(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        if (run.world.tick - run.stageStartTick < MULTIBLOCK_WAIT_TICKS) return
        val boilerRef = PolishSelfTest.controllerAt(run, PolishSelfTest.BOILER) ?: error("boiler controller missing")
        val gasbagRef = PolishSelfTest.controllerAt(run, PolishSelfTest.GASBAG) ?: error("gasbag controller missing")
        val boilerState = store.getComponent(boilerRef, BoilerState.type) ?: error("boiler state missing")
        val boilerController =
            store.getComponent(boilerRef, MultiblockController.type) ?: error("boiler multiblock missing")
        val gasbagController =
            store.getComponent(gasbagRef, MultiblockController.type) ?: error("gasbag multiblock missing")
        val boilerFormed = boilerController.state == MultiblockState.FORMED
        val gasbagFormed = gasbagController.state == MultiblockState.FORMED
        val pressureBuilt = boilerState.pressure > 0
        PolishSelfTest.report(
            run,
            "f formed boiler pressure",
            boilerFormed && gasbagFormed && pressureBuilt,
            "boiler=${boilerController.state} gasbag=${gasbagController.state} pressure=${boilerState.pressure}",
        )
        val part =
            GutterBoilerTemplate.template.parts
                .first()
                .offset
        val broken =
            PolishSelfTest.position(
                PolishSelfTest.BOILER.x + part.x,
                PolishSelfTest.BOILER.y + part.y,
                PolishSelfTest.BOILER.z + part.z,
            )
        PolishSelfTest.clearFixtures(run, store, listOf(broken))
        CraftoryCore.multiblocks().recheck(store, boilerRef)
        run.stage = 10
    }

    private fun checkDamagedController(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val boilerRef = PolishSelfTest.controllerAt(run, PolishSelfTest.BOILER) ?: error("boiler controller missing")
        val controller = store.getComponent(boilerRef, MultiblockController.type) ?: error("boiler multiblock missing")
        val passed = controller.state != MultiblockState.FORMED && controller.state == MultiblockState.DAMAGED
        PolishSelfTest.report(run, "f damaged boiler recheck", passed, "structure=${controller.state}")
        cleanup(run, store)
    }

    private fun assembleAndKick(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        template: MultiblockTemplate,
    ): Boolean {
        repeat(template.parts.size) {
            JunkHeartActions.hammer(run.world, store, controllerRef, take = { true }, refund = {})
        }
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return false
        if (controller.state != MultiblockState.READY_TO_START) return false
        val kickTick = controller.readySinceTick + (run.world.tps.toDouble() * 1.5).toLong()
        return JunkHeartActions.kick(run.world, store, controllerRef, kickTick, mayOperate = true) is KickResult.Formed
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "TooGenericExceptionCaught")
    private fun pollCleanup(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        if (run.visitStarted) {
            if (run.world.tick - run.visitStartedTick > VISIT_TIMEOUT_TICKS) {
                PolishSelfTest.fail(
                    run,
                    "e grizzlecog visit",
                    "timeout elapsed=${run.world.tick - run.visitStartedTick} uuid=${run.visitUuid}",
                )
                run.visitUuid?.let { GrizzlecogVisitSystem.despawnForSelfTest(run.world, it) }
                val floorRemoved = clearVisitFloor(run, store)
                val finalCounters = PolishSelfTest.cleanupCounters(run)
                PolishSelfTest.report(
                    run,
                    "cleanup after visit",
                    finalCounters.isClear && floorRemoved,
                    "$finalCounters floorRemoved=$floorRemoved",
                )
                finish(run, store)
            }
            return
        }
        val counters = PolishSelfTest.cleanupCounters(run)
        if (counters.isClear || run.world.tick - run.cleanupStartTick >= CLEANUP_TIMEOUT_TICKS) {
            val passed = counters.isClear
            PolishSelfTest.report(run, "cleanup", passed, counters.toString())
            run.visitStarted = true
            run.visitStartedTick = run.world.tick
            run.stageStartTick = run.world.tick
            placeVisitFloor(run)
            GrizzlecogVisitSystem.startForSelfTest(
                run.world,
                Vector3i(PolishSelfTest.ANCHOR.x, PolishSelfTest.ANCHOR.y, PolishSelfTest.ANCHOR.z),
            ) { present, uuid, detail ->
                if (run.completed) {
                    uuid?.let { GrizzlecogVisitSystem.despawnForSelfTest(run.world, it) }
                } else {
                    run.visitUuid = uuid
                    try {
                        PolishSelfTest.report(run, "e grizzlecog visit", present, detail)
                        val despawned = uuid?.let { GrizzlecogVisitSystem.despawnForSelfTest(run.world, it) } == true
                        val absent = uuid?.let { !GrizzlecogVisitSystem.isGrizzlecogPresent(run.world, it) } == true
                        PolishSelfTest.report(
                            run,
                            "e grizzlecog cleanup",
                            despawned && absent,
                            "despawned=$despawned absent=$absent",
                        )
                    } catch (failure: Exception) {
                        PolishSelfTest.fail(run, "e grizzlecog visit", failure.message ?: "visit completion failed")
                    } finally {
                        val floorRemoved = clearVisitFloor(run, store)
                        val finalCounters = PolishSelfTest.cleanupCounters(run)
                        PolishSelfTest.report(
                            run,
                            "cleanup after visit",
                            finalCounters.isClear && floorRemoved,
                            "$finalCounters floorRemoved=$floorRemoved",
                        )
                        finish(run, store)
                    }
                }
            }
        }
    }

    private fun placeVisitFloor(run: PolishSelfTestRun.Run) {
        val position = PolishSelfTest.VISIT_FLOOR
        val sectionRef = run.sectionRef ?: error("visit floor section missing")
        val type = BlockType.fromString(CraftoryIds.SCRAP_PLATING) ?: error("scrap floor block missing")
        val blockId = BlockType.getAssetMap().getIndex(type.id)
        check(
            BlockOperations.setBlock(
                run.world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                blockId,
                type,
                0,
                0,
                SetBlockSettings.NONE,
            ),
        ) { "visit floor placement failed" }
    }

    private fun clearVisitFloor(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ): Boolean =
        runCatching {
            PolishSelfTest.clearFixtures(run, store, listOf(PolishSelfTest.VISIT_FLOOR))
            PolishSelfTest.isEmptyAt(run, PolishSelfTest.VISIT_FLOOR)
        }.getOrDefault(false)

    private fun phaseTimeout(phase: Int): Long =
        if (phase == 12) PHASE_TIMEOUT_TICKS + VISIT_TIMEOUT_TICKS else PHASE_TIMEOUT_TICKS

    private fun phaseName(phase: Int) =
        when (phase) {
            0 -> "claw setup"
            1 -> "claw transfer"
            2 -> "pipe shapes"
            3 -> "pipe arm removal"
            4 -> "corner transfer"
            5 -> "canister fill bands"
            6 -> "sloshtank fill bands"
            7 -> "multiblock setup"
            8 -> "unformed boiler"
            9 -> "formed boiler"
            10 -> "damaged controller"
            11 -> "cleanup"
            else -> "visit cleanup"
        }

    private fun phaseDetails(run: PolishSelfTestRun.Run) =
        "stage=${run.stage} bandIndex=${run.bandIndex} cleanup=${PolishSelfTest.cleanupCounters(run)}"

    private fun finish(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        if (run.summarySent) return
        run.summarySent = true
        runCatching {
            run.context.sendMessage(Message.raw("selftest: polish ${run.passed} passed, ${run.failed} failed"))
        }
        run.completed = true
        store.getResource(PolishSelfTestRun.type).active = null
    }

    private fun finishFailedSetup(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        PolishSelfTest.fail(run, "setup", run.setupFailure ?: "unknown setup error")
        run.setupFailure = null
        if (run.sectionRef == null) {
            finish(run, store)
            return
        }
        run.ready = true
        cleanup(run, store)
    }

    companion object {
        private const val SETUP_TIMEOUT_TICKS = 300L
        private const val VISIT_TIMEOUT_TICKS = 100L
        private const val PHASE_TIMEOUT_TICKS = 400L
        private const val CHEST_ID = "Furniture_Crude_Chest_Small"
        private const val ITEM_ID = "Plant_Crop_Mushroom_Common_Brown"
        private const val WATER_SOURCE = "Water_Source"
        private const val ANCHOR_TIMEOUT_TICKS = 200L
        private const val CLAW_WAIT_TICKS = 40L
        private const val SHAPE_WAIT_TICKS = 20L
        private const val ITEM_WAIT_TICKS = 80L
        private const val BAND_TIMEOUT_TICKS = 30L
        private const val CLEANUP_TIMEOUT_TICKS = 60L
        private const val MULTIBLOCK_WAIT_TICKS = 40L
    }
}

@Suppress("TooManyFunctions")
internal object PolishSelfTest {
    internal data class Band(
        val percent: Int,
        val state: String,
    )

    internal data class PipeShape(
        val name: String,
        val center: CraftorySelfTest.TestPosition,
        val arms: List<CraftorySelfTest.TestPosition>,
        val mask: Int,
        val removedArmMask: Int = 0,
    )

    internal data class CleanupCounters(
        val blocks: Int,
        val energyNodes: Int,
        val fluidNodes: Int,
        val itemTransit: Int,
        val displays: Int,
    ) {
        val isClear: Boolean
            get() =
                blocks == 0 &&
                    energyNodes == 0 &&
                    fluidNodes == 0 &&
                    itemTransit == 0 &&
                    displays == 0
    }

    val ANCHOR = position(10_020, 150, 10_002)
    val CLAW = position(10_023, 152, 10_012)
    val CANISTER = position(10_024, 152, 10_014)
    val SLOSHTANK = position(10_027, 152, 10_014)
    val BOILER = position(10_028, 150, 10_014)
    val GASBAG = position(10_030, 150, 10_014)
    val VISIT_FLOOR = position(ANCHOR.x, ANCHOR.y - 1, ANCHOR.z)

    val BANDS =
        listOf(
            Band(0, "Empty"),
            Band(30, "Quarter"),
            Band(60, "Half"),
            Band(90, "ThreeQuarter"),
            Band(100, "Full"),
        )

    val PIPE_SHAPES =
        listOf(
            PipeShape(
                "straight",
                position(10_023, 152, 10_004),
                listOf(position(10_022, 152, 10_004), position(10_024, 152, 10_004)),
                (1 shl 0) or (1 shl 1),
            ),
            PipeShape(
                "L",
                position(10_026, 152, 10_004),
                listOf(position(10_027, 152, 10_004), position(10_026, 152, 10_005)),
                (1 shl 0) or (1 shl 4),
            ),
            PipeShape(
                "T",
                position(10_030, 152, 10_004),
                listOf(
                    position(10_031, 152, 10_004),
                    position(10_029, 152, 10_004),
                    position(10_030, 152, 10_005),
                ),
                (1 shl 0) or (1 shl 1) or (1 shl 4),
            ),
            PipeShape(
                "cross",
                position(10_023, 152, 10_009),
                listOf(
                    position(10_024, 152, 10_009),
                    position(10_022, 152, 10_009),
                    position(10_023, 152, 10_010),
                    position(10_023, 152, 10_008),
                ),
                (1 shl 0) or (1 shl 1) or (1 shl 4) or (1 shl 5),
                1 shl 5,
            ),
        )

    fun start(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
    ) {
        world.execute {
            val resource = world.chunkStore.store.getResource(PolishSelfTestRun.type)
            if (resource.active != null) return@execute
            resource.active = PolishSelfTestRun.Run(context, world, world.tick)
        }
    }

    fun fixturePositions(): List<CraftorySelfTest.TestPosition> {
        val clawNeighbours =
            buildList {
                add(CLAW)
                GutterpipeMask.FACES.forEach { face ->
                    add(position(CLAW.x + face.dx, CLAW.y + face.dy, CLAW.z + face.dz))
                    add(position(CLAW.x + face.dx * 2, CLAW.y + face.dy * 2, CLAW.z + face.dz * 2))
                }
            }
        val shapePositions =
            PIPE_SHAPES.flatMap { shape ->
                listOf(shape.center) + shape.arms +
                    listOf(position(shape.center.x, shape.center.y, shape.center.z + 2))
            }
        val multiblockPositions =
            listOf(BOILER, GASBAG) +
                GutterBoilerTemplate.template.parts.map {
                    position(BOILER.x + it.offset.x, BOILER.y + it.offset.y, BOILER.z + it.offset.z)
                } +
                GasbagTemplate.template.parts.map {
                    position(GASBAG.x + it.offset.x, GASBAG.y + it.offset.y, GASBAG.z + it.offset.z)
                }
        return (
            clawNeighbours + shapePositions + listOf(ANCHOR, CANISTER, SLOSHTANK, VISIT_FLOOR) + multiblockPositions
        ).distinct()
    }

    fun fixturesShareSection(): Boolean {
        val section = sectionKey(ANCHOR)
        return fixturePositions().all { sectionKey(it) == section }
    }

    private fun sectionKey(position: CraftorySelfTest.TestPosition) =
        Triple(position.x shr 5, position.y shr 5, position.z shr 5)

    fun clawPositions(): List<CraftorySelfTest.TestPosition> =
        fixturePositions().filter { position ->
            position == CLAW ||
                (
                    kotlin.math.abs(position.x - CLAW.x) <= 2 &&
                        kotlin.math.abs(position.y - CLAW.y) <= 2 &&
                        kotlin.math.abs(position.z - CLAW.z) <= 2
                )
        }

    fun pipeShapePositions(): List<CraftorySelfTest.TestPosition> =
        PIPE_SHAPES.flatMap { shape ->
            listOf(shape.center) + shape.arms +
                listOf(position(shape.center.x, shape.center.y, shape.center.z + 2))
        }

    fun position(
        x: Int,
        y: Int,
        z: Int,
    ) = CraftorySelfTest.TestPosition(x, y, z)

    fun controllerAt(
        run: PolishSelfTestRun.Run,
        position: CraftorySelfTest.TestPosition,
    ) = BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)?.takeIf { it.isValid }

    fun offset(
        position: CraftorySelfTest.TestPosition,
        direction: CraftorySelfTest.TestPosition,
        distance: Int,
    ) = position(
        position.x + direction.x * distance,
        position.y + direction.y * distance,
        position.z + direction.z * distance,
    )

    fun clearFixtures(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
        positions: List<CraftorySelfTest.TestPosition> = fixturePositions(),
    ) {
        val sectionRef = run.sectionRef ?: return
        store.getComponent(sectionRef, ChunkSection.getComponentType())?.resetActiveTimer()
        CraftorySelfTest.clear(run.world, sectionRef, positions)
    }

    fun clearTestArea(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val sectionRef = run.sectionRef ?: return
        store.getComponent(sectionRef, ChunkSection.getComponentType())?.resetActiveTimer()
        CraftorySelfTest.clear(run.world, sectionRef, fixturePositions())
        ItemTransport.of(run.world).clearDisplaysInArea(
            DISPLAY_MIN_X,
            DISPLAY_MIN_Y,
            DISPLAY_MIN_Z,
            DISPLAY_MAX_X,
            DISPLAY_MAX_Y,
            DISPLAY_MAX_Z,
        )
    }

    fun place(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
        blockId: String,
        position: CraftorySelfTest.TestPosition,
        component: ComponentType<ChunkStore, out Component<ChunkStore>>,
    ) {
        val sectionRef = run.sectionRef ?: error("section reference missing")
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: error("burner block section unavailable")
        CraftorySelfTest.place(run.world, store, sectionRef, section, blockId, position, component)
    }

    fun blockTypeAt(
        run: PolishSelfTestRun.Run,
        position: CraftorySelfTest.TestPosition,
    ): BlockType? {
        val sectionRef = run.sectionRef ?: return null
        val section =
            run.world.chunkStore.store
                .getComponent(sectionRef, BlockSection.getComponentType()) ?: return null
        return BlockType.getAssetMap().getAsset(section.get(position.x, position.y, position.z))
    }

    fun isEmptyAt(
        run: PolishSelfTestRun.Run,
        position: CraftorySelfTest.TestPosition,
    ): Boolean {
        val sectionRef = run.sectionRef ?: return false
        val section =
            run.world.chunkStore.store
                .getComponent(sectionRef, BlockSection.getComponentType()) ?: return false
        return section.get(position.x, position.y, position.z) == BlockType.EMPTY_ID
    }

    fun stateName(
        run: PolishSelfTestRun.Run,
        position: CraftorySelfTest.TestPosition,
        blockId: String,
    ): String? {
        val baseType = BlockType.fromString(blockId) ?: return null
        val actualType = blockTypeAt(run, position) ?: return null
        if (baseBlockId(actualType) != blockId) return null
        return baseType.getStateForBlock(actualType.id)
    }

    fun containerAt(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ) = BlockModule
        .getBlockEntity(run.world, position.x, position.y, position.z)
        ?.takeIf { it.isValid }
        ?.let { store.getComponent(it, ItemContainerBlock.getComponentType())?.getItemContainer() }

    fun containerQuantity(
        run: PolishSelfTestRun.Run,
        store: Store<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): Int {
        val container = containerAt(run, store, position) ?: return 0
        var total = 0
        var slot = 0.toShort()
        while (slot < container.capacity) {
            total += container.getItemStack(slot)?.quantity ?: 0
            slot++
        }
        return total
    }

    fun cleanupCounters(run: PolishSelfTestRun.Run): CleanupCounters {
        val energy = CraftoryCore.energyAccess(run.world)
        val fluid = FluidTransport.of(run.world)
        val items = ItemTransport.of(run.world)
        var blocks = 0
        var energyNodes = 0
        var fluidNodes = 0
        var itemTransit = 0
        fixturePositions().forEach { position ->
            val ref =
                BlockModule
                    .getBlockEntity(run.world, position.x, position.y, position.z)
                    ?.takeIf { it.isValid }
            if (ref != null) {
                blocks++
            }
            if (energy.networkMembers(BlockPos.pack(position.x, position.y, position.z)) > 0) energyNodes++
            if (fluid.isNode(position.x, position.y, position.z)) fluidNodes++
            itemTransit += items.itemsOwnedBy(position.x, position.y, position.z)
        }
        val displays =
            items.displaysInArea(
                DISPLAY_MIN_X,
                DISPLAY_MIN_Y,
                DISPLAY_MIN_Z,
                DISPLAY_MAX_X,
                DISPLAY_MAX_Y,
                DISPLAY_MAX_Z,
            )
        return CleanupCounters(blocks, energyNodes, fluidNodes, itemTransit, displays)
    }

    private const val DISPLAY_MIN_X = 10_020
    private const val DISPLAY_MIN_Y = 149
    private const val DISPLAY_MIN_Z = 10_002
    private const val DISPLAY_MAX_X = 10_031
    private const val DISPLAY_MAX_Y = 154
    private const val DISPLAY_MAX_Z = 10_020

    fun report(
        run: PolishSelfTestRun.Run,
        case: String,
        passed: Boolean,
        detail: String,
    ) {
        if (passed) run.passed++ else run.failed++
        val result = if (passed) "PASS" else "FAIL"
        runCatching { run.context.sendMessage(Message.raw("selftest: polish $case $result ($detail)")) }
    }

    fun fail(
        run: PolishSelfTestRun.Run,
        case: String,
        detail: String,
    ) = report(run, case, false, detail)
}
