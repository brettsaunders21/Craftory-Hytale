@file:Suppress("LongMethod", "CyclomaticComplexMethod")

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
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.fluid.FireFluidTicker
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid
import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import com.hypixel.hytale.server.core.util.FillerBlockUtil
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.FluidTransport
import studio.craftory.tech.CraftoryIds
import java.util.function.BiConsumer

internal class FluidSelfTestRun : Resource<ChunkStore> {
    class Run(
        val context: com.hypixel.hytale.server.core.command.system.CommandContext,
        val world: World,
        val startTick: Long,
    ) {
        var onComplete: (() -> Unit)? = null
        val blocks = FluidSelfTestCases.testPositions()
        var stage = 0
        var stageStartTick = startTick
        var initialized = false
        var testTicking = true
        var loading = false
        var setupFailure: String? = null
        var setupFailureReported = false
        var cleanupComplete = false
        var heldChunk = false
        var keepLoadedCount = 0
        var f3Setup = false
        var f5Setup = false
        var freezeStarted = false
        var gasbagPatched = false
        var blocksCleared = false
        var cleanupStartedTick = 0L
        var passed = 0
        var failed = 0
        var highPulseTick = Long.MIN_VALUE
        var lowPulseTick = Long.MIN_VALUE
        var highPulseInterval = 0L
        var lowPulseInterval = 0L
        var leakAmount = 0L
        var frozenAmount = 0L
        var reloadStarted = false
        var reloadSaving = false
        var reloadSaveHold = false
        var reloadSaved = false
        var reloadRequested = false
        var reloadComplete = false
        var reloadFailure: String? = null
        var f3Pops = 0
        var f3BoilerWasOnline = true
        var f6Phase = "CHARGE"
        var f6ChargeTarget = 0L
        var f6LeakStart = 0L
        var f6PatchedAmount = 0L
        var f6FireFluid: Fluid? = null
        var f6FireTicker: FireFluidTicker? = null
        var f6SupportBlock: BlockType? = null
        var f6FireReasserts = 0
        var f6FireResolved = false
        var f6SupportPlaced = false
    }

    var active: Run? = null

    override fun clone(): Resource<ChunkStore> = FluidSelfTestRun()

    companion object {
        lateinit var type: ResourceType<ChunkStore, FluidSelfTestRun>
    }
}

internal class FluidSelfTestSystem : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val run = store.getResource(FluidSelfTestRun.type).active ?: return
        try {
            if (run.cleanupComplete) return
            if (run.blocksCleared) {
                FluidSelfTestCleanup.cleanup(run, store)
                return
            }
            if (run.setupFailure != null) {
                run.testTicking = false
                if (!run.setupFailureReported) {
                    run.setupFailureReported = true
                    FluidSelfTestState.fail(run, "setup", run.setupFailure ?: "unknown setup failure")
                }
                FluidSelfTestCleanup.cleanup(run, store)
                return
            }
            if (run.world.tick - run.startTick >= FluidSelfTestCases.MAX_RUNTIME_TICKS) {
                run.testTicking = false
                val caseNumber = (run.stage + 1).coerceAtMost(9)
                FluidSelfTestState.fail(
                    run,
                    "MAX_RUNTIME",
                    "after ${run.world.tick - run.startTick} ticks while waiting for F$caseNumber; " +
                        FluidSelfTestCases.timeoutDetail(run, store),
                )
                FluidSelfTestCleanup.cleanup(run, store)
                return
            }
            FluidSelfTestSetup.keepTestSectionsTicking(run, store)
            if (!run.initialized) {
                if (!run.loading) FluidSelfTestCases.loadAndPlace(run)
                return
            }
            val stage = run.stage
            when (run.stage) {
                0 -> FluidSelfTestCases.tickF1(run)
                1 -> FluidSelfTestCases.tickF2(run)
                2 -> FluidSelfTestCases.tickF3(run, store)
                3 -> FluidSelfTestCases.tickF4(run)
                4 -> FluidSelfTestCases.tickF5(run)
                5 -> FluidSelfTestCases.tickF6(run)
                6 -> FluidSelfTestCases.tickF7(run)
                7 -> FluidSelfTestCases.tickF8(run, store)
                else -> FluidSelfTestCleanup.cleanup(run, store)
            }
            if (run.stage == stage && stage in 0..7 &&
                FluidSelfTestState.elapsed(run) > fluidSelfTestTimeoutTicks(stage)
            ) {
                FluidSelfTestState.fail(
                    run,
                    "F${stage + 1}",
                    FluidSelfTestCases.timeoutDetail(run, store),
                )
                if (stage == 6) {
                    val chunkIndex =
                        ChunkUtil.indexChunk(
                            FluidSelfTestCases.BASE.x shr 5,
                            FluidSelfTestCases.BASE.z shr 5,
                        )
                    FluidSelfTestReload.releaseSaveHold(run, chunkIndex)
                    run.testTicking = true
                }
                FluidSelfTestState.next(run)
            }
        } catch (failure: IllegalStateException) {
            handleFailure(run, store, failure)
        } catch (failure: IllegalArgumentException) {
            handleFailure(run, store, failure)
        }
    }

    private fun handleFailure(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        failure: RuntimeException,
    ) {
        run.testTicking = false
        val machineDetails =
            if (run.stage in 2..5) {
                "; ${fluidSelfTestMachineComponentDetails(run, store)}"
            } else {
                ""
            }
        FluidSelfTestState.fail(
            run,
            "F${run.stage}",
            (failure.message ?: failure.javaClass.simpleName) + machineDetails,
        )
        FluidSelfTestCleanup.cleanup(run, store)
    }
}

internal object FluidSelfTestCases {
    fun loadAndPlace(run: FluidSelfTestRun.Run) {
        run.loading = true
        val anchor = run.blocks.first()
        run.world.chunkStore
            .getChunkSectionReferenceAtBlockAsync(
                anchor.x,
                anchor.y,
                anchor.z,
                GetChunkFlags.SET_TICKING,
            ).whenComplete { sectionRef, failure ->
                run.world.execute {
                    if (failure != null || sectionRef == null || !sectionRef.isValid) {
                        run.setupFailure = "fluid test chunk load failed (${failure?.message ?: "invalid ref"})"
                        run.loading = false
                        return@execute
                    }
                    try {
                        val store = run.world.chunkStore.store
                        val section =
                            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                                ?: error("fluid test block section missing")
                        run.blocks.forEach { position -> FluidSelfTestSetup.clearBlock(run.world, position) }
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.SLOSHTANK,
                            F1_TANK,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.SLOSHTANK,
                            F2_BOTTOM,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.SLOSHTANK,
                            F2_TOP,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.SLOSHTANK,
                            F2_SIDE,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.GUTTER_BOILER,
                            BOILER,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.GNASHER_PIT,
                            PRESSURISED_GNASHER,
                        )
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.BURNER, BURNER)
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.GNASHER_PIT,
                            PLAIN_GNASHER,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.BURNER,
                            GASBAG_BURNER,
                        )
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.GASBAG, GASBAG)
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, F1_PIPE)
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, BOILER_PIPE)
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.PIPE,
                            BOILER_LINK_PIPE,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.PIPE,
                            PRESSURISED_GNASHER_PIPE,
                        )
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, BURNER_PIPE)
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.PIPE,
                            BURNER_LINK_PIPE,
                        )
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.PIPE,
                            PLAIN_GNASHER_PIPE,
                        )
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, GASBAG_PIPE)
                        FluidSelfTestDiagnostics.verifyFixtureBlocks(run, store, sectionRef)
                        val chunkIndex = ChunkUtil.indexChunk(anchor.x shr 5, anchor.z shr 5)
                        FluidSelfTestSetup.retainTestChunk(run, store, chunkIndex)
                        val fluidSection =
                            store.ensureAndGetComponent(sectionRef, FluidSection.getComponentType())
                        val water = Fluid.getAssetMap().getAsset(WATER_SOURCE) ?: error("Water_Source asset missing")
                        fluidSection.setFluid(
                            SOURCE.x and 31,
                            SOURCE.y and 31,
                            SOURCE.z and 31,
                            water,
                            8.toByte(),
                        )
                        run.initialized = true
                        run.stageStartTick = run.world.tick
                    } catch (failure: IllegalStateException) {
                        run.setupFailure = "fluid test setup failed (${failure.message})"
                    } catch (failure: IllegalArgumentException) {
                        run.setupFailure = "fluid test setup failed (${failure.message})"
                    } finally {
                        run.loading = false
                    }
                }
            }
    }

    fun tickF1(run: FluidSelfTestRun.Run) {
        val fluid = FluidTransport.of(run.world)
        if (fluid.amount(F1_TANK.x, F1_TANK.y, F1_TANK.z) > 0) {
            FluidSelfTestState.pass(run, "F1 pipe to Sloshtank source fill")
            FluidSelfTestState.next(run)
        } else if (FluidSelfTestState.elapsed(run) > CASE_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F1", timeoutDetail(run, run.world.chunkStore.store))
            FluidSelfTestState.next(run)
        }
    }

    fun tickF2(run: FluidSelfTestRun.Run) {
        val fluid = FluidTransport.of(run.world)
        val stacked = fluid.capacity(F2_BOTTOM.x, F2_BOTTOM.y, F2_BOTTOM.z)
        val side = fluid.capacity(F2_SIDE.x, F2_SIDE.y, F2_SIDE.z)
        if (stacked == 16_000L && side == 8_000L) {
            FluidSelfTestState.pass(run, "F2 stacked tanks merge; horizontal tank stays separate")
            FluidSelfTestState.next(run)
        } else if (FluidSelfTestState.elapsed(run) > CASE_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F2", timeoutDetail(run, run.world.chunkStore.store))
            FluidSelfTestState.next(run)
        }
    }

    fun tickF3(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val boilerRef = FluidSelfTestSetup.blockEntity(run.world, BOILER)
        val boilerState = boilerRef?.let { store.getComponent(it, BoilerState.type) }
        val boilerMember = boilerRef?.let { store.getComponent(it, EnergyMember.type) }
        val pressurisedRef = FluidSelfTestSetup.blockEntity(run.world, PRESSURISED_GNASHER)
        val plainRef = FluidSelfTestSetup.blockEntity(run.world, PLAIN_GNASHER)
        val pressurisedState = pressurisedRef?.let { store.getComponent(it, GnasherState.type) }
        val plainState = plainRef?.let { store.getComponent(it, GnasherState.type) }
        val missing =
            buildList {
                if (boilerRef == null) add("BoilerBlockEntity")
                if (boilerState == null) add("BoilerState")
                if (boilerMember == null) add("EnergyMember")
                if (pressurisedRef == null) add("PressurisedGnasherBlockEntity")
                if (pressurisedState == null) add("PressurisedGnasherState")
                if (plainRef == null) add("PlainGnasherBlockEntity")
                if (plainState == null) add("PlainGnasherState")
            }
        if (waitForFluidSelfTestComponents(run, store, "F3", F3_TIMEOUT_TICKS, missing)) return
        val boiler = checkNotNull(boilerState)
        val activeBoilerMember = checkNotNull(boilerMember)
        val activePressurisedState = checkNotNull(pressurisedState)
        val activePlainState = checkNotNull(plainState)
        if (!run.f3Setup) {
            GutterBoilerActions.acceptWater(boiler, WATER_SOURCE, GutterBoilerRules.CAPACITY)
            repeat(5) { GutterBoilerActions.addFuel(boiler, CraftoryIds.FIZZ_CHARGE) }
            boiler.pressure = 85
            boiler.online = true
            boilerRef.let {
                store.getComponent(it, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
            }
            activePressurisedState.oreQueue = GritTable.encodeOreQueue(listOf("Ore_Iron_Stone"))
            activePlainState.oreQueue = GritTable.encodeOreQueue(listOf("Ore_Iron_Stone"))
            FluidSelfTestSetup.addBurnerFuel(run, store, BURNER)
            FluidSelfTestSetup.addBurnerFuel(run, store, GASBAG_BURNER, 6)
            run.f3Setup = true
        }
        if (run.f3BoilerWasOnline && !boiler.online) run.f3Pops++
        run.f3BoilerWasOnline = boiler.online
        // F3 measures boosted production; F5 exercises the pop behavior.
        if (boiler.pressure > 90) boiler.pressure = 85
        val tick = run.world.tick
        if (activePressurisedState.lastAutoCrankTick != run.highPulseTick) {
            if (run.highPulseTick != Long.MIN_VALUE) {
                run.highPulseInterval = activePressurisedState.lastAutoCrankTick - run.highPulseTick
            }
            run.highPulseTick = activePressurisedState.lastAutoCrankTick
        }
        if (activePlainState.lastAutoCrankTick != run.lowPulseTick) {
            if (run.lowPulseTick != Long.MIN_VALUE) {
                run.lowPulseInterval = activePlainState.lastAutoCrankTick - run.lowPulseTick
            }
            run.lowPulseTick = activePlainState.lastAutoCrankTick
        }
        val boilerRank = activeBoilerMember.productionRank
        val productionReady =
            boilerRank == GutterBoilerSystem.PRESSURISED_RANK && activeBoilerMember.production > 0
        val pulseIntervalsReady =
            run.highPulseInterval == GnasherMachine.AUTO_PULSE_INTERVAL_TICKS &&
                run.lowPulseInterval == GnasherMachine.AUTO_PULSE_INTERVAL_TICKS * 2
        if (productionReady && pulseIntervalsReady) {
            FluidSelfTestState.pass(run, "F3 Pressurised rank 1 and 20/40 tick Gnasher pulses")
            FluidSelfTestState.next(run)
        } else if (tick - run.stageStartTick > F3_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F3", timeoutDetail(run, store))
            FluidSelfTestState.next(run)
        }
    }

    fun tickF4(run: FluidSelfTestRun.Run) {
        val store = run.world.chunkStore.store
        val boilerRef = FluidSelfTestSetup.blockEntity(run.world, BOILER)
        val boilerState = boilerRef?.let { store.getComponent(it, BoilerState.type) }
        val missing =
            buildList {
                if (boilerRef == null) add("BoilerBlockEntity")
                if (boilerState == null) add("BoilerState")
            }
        if (waitForFluidSelfTestComponents(run, store, "F4", CASE_TIMEOUT_TICKS, missing)) return
        val boiler = checkNotNull(boilerState)
        boiler.pressure = 97
        val vented = GutterBoilerRules.vent(boiler)
        if (vented && boiler.pressure == 57) {
            FluidSelfTestState.pass(run, "F4 shared vent path drops 97 to 57")
        } else {
            FluidSelfTestState.fail(
                run,
                "F4",
                "vented=$vented pressure=${boiler.pressure}; ${fluidSelfTestMachineComponentDetails(run, store)}",
            )
        }
        FluidSelfTestState.next(run)
    }

    fun tickF5(run: FluidSelfTestRun.Run) {
        val boilerRef = FluidSelfTestSetup.blockEntity(run.world, BOILER)
        val store = run.world.chunkStore.store
        val boilerState = boilerRef?.let { store.getComponent(it, BoilerState.type) }
        val missing =
            buildList {
                if (boilerRef == null) add("BoilerBlockEntity")
                if (boilerState == null) add("BoilerState")
            }
        if (waitForFluidSelfTestComponents(run, store, "F5", CASE_TIMEOUT_TICKS, missing)) return
        val boiler = checkNotNull(boilerState)
        if (!run.f5Setup) {
            boiler.pressure = 99
            boiler.water = GutterBoilerRules.CAPACITY
            boiler.fuelTicks = 1_000
            boiler.online = true
            run.f5Setup = true
        }
        if (!boiler.online) {
            val currentType = FluidSelfTestDiagnostics.blockTypeAt(run, store, BOILER)
            val currentBaseId = currentType?.let(::baseBlockId)
            val noPressureOrWater = boiler.pressure == 0 && boiler.water == 0L
            val boilerBlockUnchanged = boilerRef.isValid && currentBaseId == CraftoryIds.GUTTER_BOILER
            if (noPressureOrWater && boilerBlockUnchanged) {
                val reriveted = GutterBoilerRules.rerivet(boiler, 2)
                if (reriveted && boiler.online) {
                    FluidSelfTestState.pass(run, "F5 pop stays in place and two scraps re-rivet")
                } else {
                    FluidSelfTestState.fail(
                        run,
                        "F5",
                        "shared re-rivet failed; ${fluidSelfTestMachineComponentDetails(run, store)}",
                    )
                }
            } else {
                FluidSelfTestState.fail(
                    run,
                    "F5",
                    "offline=${!boiler.online} pressure=${boiler.pressure} " +
                        "water=${boiler.water} blockUnchanged=$boilerBlockUnchanged " +
                        "${FluidSelfTestDiagnostics.boilerStateDetail(run, store)}; " +
                        fluidSelfTestMachineComponentDetails(run, store),
                )
            }
            FluidSelfTestState.next(run)
        } else if (FluidSelfTestState.elapsed(run) > CASE_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F5", timeoutDetail(run, store))
            FluidSelfTestState.next(run)
        }
    }

    fun tickF6(run: FluidSelfTestRun.Run) = FluidSelfTestF6.tick(run)

    fun tickF7(run: FluidSelfTestRun.Run) {
        FluidSelfTestReload.tick(run)
    }

    fun tickF8(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val sectionRef = run.world.chunkStore.getChunkSectionReferenceAtBlock(BASE.x, BASE.y, BASE.z) ?: return
        if (!sectionRef.isValid) return
        val nonTicking = ChunkStore.REGISTRY.getNonTickingComponentType()
        if (!store.getArchetype(sectionRef).contains(nonTicking)) {
            run.testTicking = false
            if (FluidSelfTestState.elapsed(run) > SECTION_TIMEOUT_TICKS) {
                FluidSelfTestState.fail(run, "F8", timeoutDetail(run, store))
                FluidSelfTestState.next(run)
            }
            return
        }
        if (!run.freezeStarted) {
            val fluid = FluidTransport.of(run.world)
            if (fluid.amount(F1_TANK.x, F1_TANK.y, F1_TANK.z) == 0L) {
                fluid.insert(F1_TANK.x, F1_TANK.y, F1_TANK.z, "Water_Source", 1_000)
            }
            val amount = fluid.amount(F1_TANK.x, F1_TANK.y, F1_TANK.z)
            fluid.drain(F1_TANK.x, F1_TANK.y, F1_TANK.z, amount)
            val fluidSection = store.ensureAndGetComponent(sectionRef, FluidSection.getComponentType())
            val water = Fluid.getAssetMap().getAsset(WATER_SOURCE) ?: error("Water_Source asset missing")
            fluidSection.setFluid(SOURCE.x and 31, SOURCE.y and 31, SOURCE.z and 31, water, 8.toByte())
            run.frozenAmount = 0
            run.stageStartTick = run.world.tick
            run.freezeStarted = true
            return
        }
        if (FluidSelfTestState.elapsed(run) >= TICKS_PER_SECOND) {
            val amount = FluidTransport.of(run.world).amount(F1_TANK.x, F1_TANK.y, F1_TANK.z)
            if (amount == run.frozenAmount) {
                FluidSelfTestState.pass(run, "F8 Sloshtank fluid freezes in NonTicking section")
            } else {
                FluidSelfTestState.fail(
                    run,
                    "F8",
                    timeoutDetail(run, store) + " tank changed ${run.frozenAmount}->$amount",
                )
            }
            FluidSelfTestState.next(run)
        }
    }

    fun testPositions(): List<Position> {
        val positions =
            listOf(
                SOURCE,
                F1_PIPE,
                F1_TANK,
                F2_BOTTOM,
                F2_TOP,
                F2_SIDE,
                BOILER,
                BOILER_PIPE,
                BOILER_LINK_PIPE,
                PRESSURISED_GNASHER_PIPE,
                F3_LINK_GAP_A,
                F3_LINK_GAP_B,
                PRESSURISED_GNASHER,
                BURNER,
                BURNER_PIPE,
                BURNER_LINK_PIPE,
                PLAIN_GNASHER_PIPE,
                PLAIN_GNASHER,
                GASBAG_BURNER,
                GASBAG_PIPE,
                GASBAG,
                GASBAG_FIRE,
            )
        val gnasherType = BlockType.fromString(CraftoryIds.GNASHER_PIT) ?: error("missing fluid test Gnasher")
        val gnasherFootprints =
            listOf(PRESSURISED_GNASHER, PLAIN_GNASHER)
                .associateWith { position -> FluidSelfTestFootprint.fixture(position, gnasherType) }
        FluidSelfTestFootprint.assertSafe(positions, gnasherFootprints)
        return positions
    }

    const val MAX_RUNTIME_TICKS = 8_000L
    const val CASE_TIMEOUT_TICKS = 120L
    const val F3_TIMEOUT_TICKS = 1_000L
    const val RELOAD_TIMEOUT_TICKS = 1_500L
    const val SECTION_TIMEOUT_TICKS = 600L
    const val CLEANUP_TIMEOUT_TICKS = 100L
    const val TICKS_PER_SECOND = 20L
    const val WATER_SOURCE = "Water_Source"
    const val GASBAG_CAPACITY = 400_000L
    val BASE = Position(10_400, 150, 10_000)
    val SOURCE = Position(BASE.x, BASE.y, BASE.z)
    val F1_PIPE = Position(BASE.x + 1, BASE.y, BASE.z)
    val F1_TANK = Position(BASE.x + 2, BASE.y, BASE.z)
    val F2_BOTTOM = Position(BASE.x + 4, BASE.y, BASE.z)
    val F2_TOP = Position(BASE.x + 4, BASE.y + 1, BASE.z)
    val F2_SIDE = Position(BASE.x + 5, BASE.y, BASE.z)
    val BOILER = Position(BASE.x + 7, BASE.y, BASE.z)
    val BOILER_PIPE = Position(BASE.x + 7, BASE.y - 1, BASE.z)
    val BOILER_LINK_PIPE = Position(BASE.x + 8, BASE.y - 1, BASE.z)
    val PRESSURISED_GNASHER_PIPE = Position(BASE.x + 9, BASE.y - 1, BASE.z)
    val F3_LINK_GAP_A = Position(BASE.x + 10, BASE.y - 1, BASE.z)
    val F3_LINK_GAP_B = Position(BASE.x + 11, BASE.y - 1, BASE.z)
    val PRESSURISED_GNASHER = Position(BASE.x + 9, BASE.y, BASE.z)
    val BURNER = Position(BASE.x + 12, BASE.y, BASE.z)
    val BURNER_PIPE = Position(BASE.x + 12, BASE.y - 1, BASE.z)
    val BURNER_LINK_PIPE = Position(BASE.x + 13, BASE.y - 1, BASE.z)
    val PLAIN_GNASHER_PIPE = Position(BASE.x + 14, BASE.y - 1, BASE.z)
    val PLAIN_GNASHER = Position(BASE.x + 14, BASE.y, BASE.z)
    val GASBAG_BURNER = Position(BASE.x + 17, BASE.y, BASE.z)
    val GASBAG_PIPE = Position(BASE.x + 18, BASE.y, BASE.z)
    val GASBAG = Position(BASE.x + 19, BASE.y, BASE.z)
    val GASBAG_FIRE = Position(BASE.x + 19, BASE.y + 1, BASE.z)

    fun timeoutDetail(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ): String {
        val fluid = FluidTransport.of(run.world)
        val boilerRef = FluidSelfTestSetup.blockEntity(run.world, BOILER)
        val boiler = boilerRef?.let { store.getComponent(it, BoilerState.type) }
        val boilerMember = boilerRef?.let { store.getComponent(it, EnergyMember.type) }
        val gasbagPosition = BlockPos.pack(GASBAG.x, GASBAG.y, GASBAG.z)
        val gasbagStored = CraftoryCore.energyAccess(run.world).stored(gasbagPosition)
        val gasbagRef = FluidSelfTestSetup.blockEntity(run.world, GASBAG)
        val gasbag = gasbagRef?.let { store.getComponent(it, GasbagState.type) }
        val elapsed = FluidSelfTestState.elapsed(run)
        val details =
            when (run.stage) {
                0 -> {
                    val sectionRef =
                        run.world.chunkStore.getChunkSectionReferenceAtBlock(SOURCE.x, SOURCE.y, SOURCE.z)
                    val blockSection = sectionRef?.let { store.getComponent(it, BlockSection.getComponentType()) }
                    val fluidSection = sectionRef?.let { store.getComponent(it, FluidSection.getComponentType()) }
                    val blockIndex = blockSection?.get(SOURCE.x, SOURCE.y, SOURCE.z)
                    val blockId =
                        blockIndex?.let { BlockType.getAssetMap().getAsset(it)?.id ?: "index=$it" }
                            ?: "missing"
                    val sourceFluid = fluidSection?.getFluid(SOURCE.x, SOURCE.y, SOURCE.z)
                    val sourceFluidIndex = fluidSection?.getFluidId(SOURCE.x, SOURCE.y, SOURCE.z) ?: -1
                    val sourceFluidId =
                        sourceFluid?.id
                            ?: if (sourceFluidIndex >= 0) {
                                Fluid.getAssetMap().getAsset(sourceFluidIndex)?.id ?: "index=$sourceFluidIndex"
                            } else {
                                "none"
                            }
                    val sourceLevel = fluidSection?.getFluidLevel(SOURCE.x, SOURCE.y, SOURCE.z) ?: 0
                    val pipeRef = FluidSelfTestSetup.blockEntity(run.world, F1_PIPE)
                    val pipeState =
                        pipeRef?.let {
                            store.getComponent(it, BlockModule.BlockStateInfo.getComponentType())
                        }
                    val pipeActive = pipeState?.let { CraftoryCore.isAreaActive(store, it) } ?: false
                    val pipeNeighbours =
                        listOf(
                            fluid.isNode(F1_PIPE.x + 1, F1_PIPE.y, F1_PIPE.z),
                            fluid.isNode(F1_PIPE.x - 1, F1_PIPE.y, F1_PIPE.z),
                            fluid.isNode(F1_PIPE.x, F1_PIPE.y + 1, F1_PIPE.z),
                            fluid.isNode(F1_PIPE.x, F1_PIPE.y - 1, F1_PIPE.z),
                            fluid.isNode(F1_PIPE.x, F1_PIPE.y, F1_PIPE.z + 1),
                            fluid.isNode(F1_PIPE.x, F1_PIPE.y, F1_PIPE.z - 1),
                        ).count { it }
                    "waiting for source pull into tank; sourceBlock=$blockId " +
                        "sourceFluid=$sourceFluidId(index=$sourceFluidIndex) level=$sourceLevel " +
                        "pipeActive=$pipeActive pipeNeighbours=$pipeNeighbours " +
                        "pipeAmount=${fluid.amount(F1_PIPE.x, F1_PIPE.y, F1_PIPE.z)} " +
                        "tankAmount=${fluid.amount(F1_TANK.x, F1_TANK.y, F1_TANK.z)}"
                }

                1 -> {
                    val positions = listOf(F2_BOTTOM, F2_TOP, F2_SIDE)
                    val values =
                        positions.joinToString { position ->
                            "${position.x},${position.y},${position.z}:" +
                                "node=${fluid.isNode(position.x, position.y, position.z)}," +
                                "capacity=${fluid.capacity(position.x, position.y, position.z)}," +
                                "amount=${fluid.amount(position.x, position.y, position.z)}," +
                                "fluid=${fluid.fluid(position.x, position.y, position.z)}"
                        }
                    "waiting for vertical capacity 16000 and horizontal capacity 8000; nodes=[$values]"
                }

                2 -> {
                    "waiting for rank-1 boiler production and 20/40 tick pulses; " +
                        "boilerPresent=${boiler != null} pressure=${boiler?.pressure} online=${boiler?.online} " +
                        "water=${boiler?.water} fuelTicks=${boiler?.fuelTicks} " +
                        "production=${boilerMember?.production} rank=${boilerMember?.productionRank} " +
                        "stored=${boilerMember?.stored} " +
                        "pulses=${run.highPulseInterval}/${run.lowPulseInterval} " +
                        "last=${run.highPulseTick}/${run.lowPulseTick} " +
                        "${FluidSelfTestDiagnostics.f3Diagnostics(run, store)} pops=${run.f3Pops}"
                }

                3 -> {
                    "waiting for vent to reduce pressure to 57; pressure=${boiler?.pressure} " +
                        "online=${boiler?.online} water=${boiler?.water} fuelTicks=${boiler?.fuelTicks}"
                }

                4 -> {
                    "waiting for pop then re-rivet; online=${boiler?.online} pressure=${boiler?.pressure} " +
                        "water=${boiler?.water} fuelTicks=${boiler?.fuelTicks} " +
                        "blockEntityPresent=${boilerRef != null} scraps=2 " +
                        FluidSelfTestDiagnostics.boilerStateDetail(run, store)
                }

                5 -> {
                    "waiting for ${run.f6Phase.lowercase()} charge/leak/patch; stored=$gasbagStored " +
                        "capacity=$GASBAG_CAPACITY leaking=${gasbag?.leaking} " +
                        "patched=${run.gasbagPatched} baseline=${run.leakAmount} " +
                        FluidSelfTestF6.diagnostics(run, store, gasbagStored)
                }

                6 -> {
                    "waiting for real chunk unload/reload; started=${run.reloadStarted} saving=${run.reloadSaving} " +
                        "saved=${run.reloadSaved} requested=${run.reloadRequested} complete=${run.reloadComplete} " +
                        "failure=${run.reloadFailure} tank=${fluid.amount(F1_TANK.x, F1_TANK.y, F1_TANK.z)}/" +
                        "${fluid.capacity(F1_TANK.x, F1_TANK.y, F1_TANK.z)} expected=${run.frozenAmount}"
                }

                7 -> {
                    val sectionRef =
                        run.world.chunkStore.getChunkSectionReferenceAtBlock(BASE.x, BASE.y, BASE.z)
                    val sectionValid = sectionRef?.isValid == true
                    val nonTicking =
                        sectionRef?.takeIf { it.isValid }?.let {
                            store.getArchetype(it).contains(ChunkStore.REGISTRY.getNonTickingComponentType())
                        } ?: false
                    "waiting for NonTicking freeze; sectionValid=$sectionValid nonTicking=$nonTicking " +
                        "tank=${fluid.amount(F1_TANK.x, F1_TANK.y, F1_TANK.z)}/" +
                        "${fluid.capacity(F1_TANK.x, F1_TANK.y, F1_TANK.z)} expected=${run.frozenAmount}"
                }

                else -> {
                    FluidSelfTestCleanup.timeoutDetail(run)
                }
            }
        val machineComponents =
            if (run.stage in 2..4) {
                " ${fluidSelfTestMachineComponentDetails(run, store)}"
            } else {
                ""
            }
        return "$details$machineComponents elapsed=$elapsed ticks"
    }
}

internal object FluidSelfTestDiagnostics {
    fun verifyFixtureBlocks(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        sectionRef: com.hypixel.hytale.component.Ref<ChunkStore>,
    ) {
        val blockSection =
            store.getComponent(sectionRef, BlockSection.getComponentType())
                ?: error("fluid test BlockSection missing")
        fixtureBlocks().forEach { (position, expectedId) ->
            val index = blockSection.get(position.x, position.y, position.z)
            val currentType = BlockType.getAssetMap().getAsset(index)
            val currentId = currentType?.id ?: "index=$index"
            val actualBaseId = currentType?.let(::baseBlockId)
            val filler = blockSection.getFiller(position.x, position.y, position.z)
            val blockEntityPresent = FluidSelfTestSetup.blockEntity(run.world, position) != null
            if (actualBaseId != expectedId || filler != 0 || !blockEntityPresent) {
                error(
                    "fixture block at $position overwritten: expected $expectedId got $currentId " +
                        "filler=$filler blockEntity=$blockEntityPresent",
                )
            }
        }
        listOf(FluidSelfTestCases.F3_LINK_GAP_A, FluidSelfTestCases.F3_LINK_GAP_B).forEach { position ->
            val index = blockSection.get(position.x, position.y, position.z)
            val type = BlockType.getAssetMap().getAsset(index)
            val filler = blockSection.getFiller(position.x, position.y, position.z)
            val blockEntityPresent = FluidSelfTestSetup.blockEntity(run.world, position) != null
            if (index != BlockType.EMPTY_ID || filler != 0 || blockEntityPresent) {
                error(
                    "fixture gap at $position overwritten: expected ${BlockType.EMPTY.id} got ${type?.id} " +
                        "filler=$filler blockEntity=$blockEntityPresent",
                )
            }
        }
    }

    private fun fixtureBlocks(): List<Pair<Position, String>> =
        listOf(
            FluidSelfTestCases.F1_TANK to CraftoryIds.SLOSHTANK,
            FluidSelfTestCases.F1_PIPE to CraftoryIds.PIPE,
            FluidSelfTestCases.F2_BOTTOM to CraftoryIds.SLOSHTANK,
            FluidSelfTestCases.F2_TOP to CraftoryIds.SLOSHTANK,
            FluidSelfTestCases.F2_SIDE to CraftoryIds.SLOSHTANK,
            FluidSelfTestCases.BOILER to CraftoryIds.GUTTER_BOILER,
            FluidSelfTestCases.PRESSURISED_GNASHER to CraftoryIds.GNASHER_PIT,
            FluidSelfTestCases.BURNER to CraftoryIds.BURNER,
            FluidSelfTestCases.PLAIN_GNASHER to CraftoryIds.GNASHER_PIT,
            FluidSelfTestCases.GASBAG_BURNER to CraftoryIds.BURNER,
            FluidSelfTestCases.GASBAG to CraftoryIds.GASBAG,
            FluidSelfTestCases.BOILER_PIPE to CraftoryIds.PIPE,
            FluidSelfTestCases.BOILER_LINK_PIPE to CraftoryIds.PIPE,
            FluidSelfTestCases.PRESSURISED_GNASHER_PIPE to CraftoryIds.PIPE,
            FluidSelfTestCases.BURNER_PIPE to CraftoryIds.PIPE,
            FluidSelfTestCases.BURNER_LINK_PIPE to CraftoryIds.PIPE,
            FluidSelfTestCases.PLAIN_GNASHER_PIPE to CraftoryIds.PIPE,
            FluidSelfTestCases.GASBAG_PIPE to CraftoryIds.PIPE,
        )

    fun f3Diagnostics(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ): String {
        val energy = CraftoryCore.energyAccess(run.world)

        fun blockSection(position: Position): BlockSection? {
            val ref = run.world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
            return ref?.takeIf { it.isValid }?.let { store.getComponent(it, BlockSection.getComponentType()) }
        }

        fun blockId(position: Position): String {
            val section = blockSection(position) ?: return "missing"
            val index = section.get(position.x, position.y, position.z)
            return BlockType.getAssetMap().getAsset(index)?.id ?: "index=$index"
        }

        fun gnasher(position: Position): String {
            val ref = FluidSelfTestSetup.blockEntity(run.world, position)
            val state = ref?.let { store.getComponent(it, GnasherState.type) }
            val id = BlockPos.pack(position.x, position.y, position.z)
            return "gnasher{blockId=${blockId(position)} delivered=${energy.delivered(id)} " +
                "deliveredRank=${energy.deliveredRank(id)} demand=${energy.networkDemand(id)} " +
                "networkOf=${energy.networkOf(id)} networkMembers=${energy.networkMembers(id)} " +
                "queuedOre=${state?.let { GritTable.queuedOreCount(it.oreQueue) }} " +
                "jammed=${state?.jammed} lastAutoCrankTick=${state?.lastAutoCrankTick}}"
        }

        fun pipe(position: Position): String {
            val section = blockSection(position)
            val index = section?.get(position.x, position.y, position.z)
            val id = BlockPos.pack(position.x, position.y, position.z)
            val ref = FluidSelfTestSetup.blockEntity(run.world, position)
            val energyMember = ref?.let { store.getComponent(it, EnergyMember.type) } != null
            val filler = section?.getFiller(position.x, position.y, position.z)
            val blockId = index?.let { BlockType.getAssetMap().getAsset(it)?.id ?: "index=$it" } ?: "missing"
            return "pipe{blockId=$blockId filler=$filler EnergyMember=$energyMember networkOf=${energy.networkOf(id)}}"
        }
        val pipes =
            listOf(
                FluidSelfTestCases.BOILER_PIPE,
                FluidSelfTestCases.BOILER_LINK_PIPE,
                FluidSelfTestCases.PRESSURISED_GNASHER_PIPE,
                FluidSelfTestCases.BURNER_PIPE,
                FluidSelfTestCases.BURNER_LINK_PIPE,
                FluidSelfTestCases.PLAIN_GNASHER_PIPE,
            ).joinToString(" ") { pipe(it) }
        return "${gnasher(FluidSelfTestCases.PRESSURISED_GNASHER)} " +
            "${gnasher(FluidSelfTestCases.PLAIN_GNASHER)} pipes=[$pipes]"
    }

    fun blockTypeAt(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        position: Position,
    ): BlockType? {
        val ref = run.world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
        val section =
            ref?.takeIf { it.isValid }?.let { store.getComponent(it, BlockSection.getComponentType()) }
                ?: return null
        return BlockType.getAssetMap().getAsset(section.get(position.x, position.y, position.z))
    }

    fun boilerStateDetail(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ): String {
        val type = blockTypeAt(run, store, FluidSelfTestCases.BOILER)
        val blockId = type?.id ?: "missing"
        val base = type?.let(::baseBlockId) ?: "missing"
        val state = BlockType.fromString(CraftoryIds.GUTTER_BOILER)?.getStateForBlock(blockId)
        return "blockId=$blockId base=$base state=$state"
    }
}

internal object FluidSelfTestF6 {
    fun tick(run: FluidSelfTestRun.Run) {
        if (run.f6ChargeTarget == 0L) {
            run.f6ChargeTarget = 2 * GasbagRules.leakAmount(FluidSelfTestCases.GASBAG_CAPACITY)
        }
        val ref = FluidSelfTestSetup.blockEntity(run.world, FluidSelfTestCases.GASBAG)
        val store = run.world.chunkStore.store
        val gasbagState = ref?.let { store.getComponent(it, GasbagState.type) }
        val energyMember = ref?.let { store.getComponent(it, EnergyMember.type) }
        val missing =
            buildList {
                if (ref == null) add("GasbagBlockEntity")
                if (gasbagState == null) add("GasbagState")
                if (energyMember == null) add("EnergyMember")
            }
        if (waitForFluidSelfTestComponents(run, store, "F6", FluidSelfTestCases.F3_TIMEOUT_TICKS, missing)) return
        val state = checkNotNull(gasbagState)
        val pos =
            BlockPos.pack(
                FluidSelfTestCases.GASBAG.x,
                FluidSelfTestCases.GASBAG.y,
                FluidSelfTestCases.GASBAG.z,
            )
        val stored = CraftoryCore.energyAccess(run.world).stored(pos)
        when (run.f6Phase) {
            "CHARGE" -> tickCharge(run, store, stored)
            "IGNITE" -> tickIgnite(run, store, state, stored)
            "LEAK" -> tickLeak(run, store, state, stored)
            "PATCHED" -> tickPatched(run, store, state, stored)
        }
    }

    private fun tickCharge(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        stored: Long,
    ) {
        if (stored >= run.f6ChargeTarget) {
            val burnerRef = FluidSelfTestSetup.blockEntity(run.world, FluidSelfTestCases.GASBAG_BURNER)
            val burner = burnerRef?.let { store.getComponent(it, BurnerState.type) }
            val blockState =
                burnerRef?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
            if (burner == null || blockState == null) {
                fail(run, store, stored, "missing Gasbag Burner state")
                return
            }
            burner.burnTicks = 0
            burner.burning = false
            blockState.markNeedsSaving(store)
            run.f6Phase = "IGNITE"
            run.stageStartTick = run.world.tick
            resolveFire(run)
            if (run.f6FireFluid == null) fail(run, store, stored, "no Fluid using FireFluidTicker")
        } else if (FluidSelfTestState.elapsed(run) > FluidSelfTestCases.F3_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F6", FluidSelfTestCases.timeoutDetail(run, store))
            FluidSelfTestState.next(run)
        }
    }

    private fun tickIgnite(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        state: GasbagState,
        stored: Long,
    ) {
        resolveFire(run)
        val fire = run.f6FireFluid
        val error =
            when {
                fire == null -> "no Fluid using FireFluidTicker"
                else -> placeSupport(run) ?: reassertFire(run, store, fire)
            }
        if (error != null) {
            fail(run, store, stored, error)
            return
        }
        if (state.leaking) {
            clearFire(run)
            run.f6LeakStart = stored
            run.leakAmount = stored
            run.f6Phase = "LEAK"
            run.stageStartTick = run.world.tick
        } else if (FluidSelfTestState.elapsed(run) > FluidSelfTestCases.F3_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F6", FluidSelfTestCases.timeoutDetail(run, store))
            FluidSelfTestState.next(run)
        }
    }

    private fun placeSupport(run: FluidSelfTestRun.Run): String? {
        val support = run.f6SupportBlock ?: return null
        if (run.f6SupportPlaced) return null
        val sectionRef =
            run.world.chunkStore
                .getChunkSectionReferenceAtBlock(
                    FluidSelfTestCases.GASBAG_FIRE.x,
                    FluidSelfTestCases.GASBAG_FIRE.y,
                    FluidSelfTestCases.GASBAG_FIRE.z,
                )?.takeIf { it.isValid } ?: return "missing fire support section"
        val placed =
            BlockOperations.setBlock(
                run.world.chunkStore,
                sectionRef,
                FluidSelfTestCases.GASBAG_FIRE.x,
                FluidSelfTestCases.GASBAG_FIRE.y,
                FluidSelfTestCases.GASBAG_FIRE.z,
                BlockType.getAssetMap().getIndex(support.id),
                support,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            )
        if (!placed) return "failed to place fire support ${support.id}"
        run.f6SupportPlaced = true
        return null
    }

    private fun reassertFire(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        fire: Fluid,
    ): String? {
        val sectionRef =
            run.world.chunkStore
                .getChunkSectionReferenceAtBlock(
                    FluidSelfTestCases.GASBAG_FIRE.x,
                    FluidSelfTestCases.GASBAG_FIRE.y,
                    FluidSelfTestCases.GASBAG_FIRE.z,
                )?.takeIf { it.isValid } ?: return "missing fire section"
        val fluidSection =
            store.getComponent(sectionRef, FluidSection.getComponentType())
                ?: return "missing fire FluidSection"
        val seen =
            fluidSection.getFluid(
                FluidSelfTestCases.GASBAG_FIRE.x,
                FluidSelfTestCases.GASBAG_FIRE.y,
                FluidSelfTestCases.GASBAG_FIRE.z,
            )
        if (seen?.id != fire.id) {
            fluidSection.setFluid(
                FluidSelfTestCases.GASBAG_FIRE.x and 31,
                FluidSelfTestCases.GASBAG_FIRE.y and 31,
                FluidSelfTestCases.GASBAG_FIRE.z and 31,
                fire,
                1.toByte(),
            )
            run.f6FireReasserts++
        }
        return null
    }

    private fun fail(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        stored: Long,
        reason: String,
    ) {
        FluidSelfTestState.fail(run, "F6", "$reason; ${diagnostics(run, store, stored)}")
        FluidSelfTestState.next(run)
    }

    private fun tickLeak(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        state: GasbagState,
        stored: Long,
    ) {
        val leakAmount = GasbagRules.leakAmount(FluidSelfTestCases.GASBAG_CAPACITY)
        val lost = run.f6LeakStart - stored
        if (FluidSelfTestState.elapsed(run) >= FluidSelfTestCases.TICKS_PER_SECOND && lost >= leakAmount) {
            if (!GasbagRules.patch(state)) {
                fail(run, store, stored, "GasbagRules.patch failed")
            } else {
                val pos =
                    BlockPos.pack(
                        FluidSelfTestCases.GASBAG.x,
                        FluidSelfTestCases.GASBAG.y,
                        FluidSelfTestCases.GASBAG.z,
                    )
                run.f6PatchedAmount = CraftoryCore.energyAccess(run.world).stored(pos)
                run.leakAmount = run.f6PatchedAmount
                run.gasbagPatched = true
                run.f6Phase = "PATCHED"
                run.stageStartTick = run.world.tick
            }
        } else if (FluidSelfTestState.elapsed(run) > FluidSelfTestCases.F3_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F6", FluidSelfTestCases.timeoutDetail(run, store))
            FluidSelfTestState.next(run)
        }
    }

    private fun tickPatched(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        state: GasbagState,
        stored: Long,
    ) {
        if (FluidSelfTestState.elapsed(run) >= FluidSelfTestCases.TICKS_PER_SECOND) {
            if (stored == run.f6PatchedAmount && !state.leaking) {
                FluidSelfTestState.pass(run, "F6 Gasbag charges, fire leaks 1%/s, patch stops it")
            } else {
                FluidSelfTestState.fail(run, "F6", "patch did not stop leak; ${diagnostics(run, store, stored)}")
            }
            FluidSelfTestState.next(run)
        } else if (FluidSelfTestState.elapsed(run) > FluidSelfTestCases.F3_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F6", FluidSelfTestCases.timeoutDetail(run, store))
            FluidSelfTestState.next(run)
        }
    }

    private fun resolveFire(run: FluidSelfTestRun.Run) {
        if (run.f6FireResolved) return
        run.f6FireResolved = true
        val fluids = Fluid.getAssetMap().getAssetMap().values
        val fire =
            fluids.firstOrNull { it.id == "Fire" && it.getTicker() is FireFluidTicker }
                ?: fluids.firstOrNull { it.getTicker() is FireFluidTicker }
        run.f6FireFluid = fire
        val ticker = fire?.getTicker() as? FireFluidTicker ?: return
        run.f6FireTicker = ticker
        val configs = ticker.getSortedFlammabilityConfigs()
        run.f6SupportBlock =
            BlockType.getAssetMap().getAssetMap().values.firstOrNull { type ->
                !type.isState() &&
                    type.getData()?.let { data ->
                        configs.any { config -> config.getTagPattern()?.test(data.getTags()) == true }
                    } == true &&
                    FluidSelfTestFootprint.isSafe(
                        FluidSelfTestFootprint.fixture(FluidSelfTestCases.GASBAG_FIRE, type),
                    )
            }
    }

    private fun clearFire(run: FluidSelfTestRun.Run) {
        FluidSelfTestSetup.clearFluid(run.world, FluidSelfTestCases.GASBAG_FIRE)
        FluidSelfTestSetup.clearBlock(run.world, FluidSelfTestCases.GASBAG_FIRE)
        run.f6SupportPlaced = false
    }

    fun diagnostics(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        stored: Long,
    ): String {
        val firePosition = FluidSelfTestCases.GASBAG_FIRE
        val sectionRef =
            run.world.chunkStore
                .getChunkSectionReferenceAtBlock(
                    firePosition.x,
                    firePosition.y,
                    firePosition.z,
                )?.takeIf { it.isValid }
        val fluidSection = sectionRef?.let { store.getComponent(it, FluidSection.getComponentType()) }
        val seen = fluidSection?.getFluid(firePosition.x, firePosition.y, firePosition.z)?.id ?: "none"
        val level = fluidSection?.getFluidLevel(firePosition.x, firePosition.y, firePosition.z) ?: 0
        val support = FluidSelfTestDiagnostics.blockTypeAt(run, store, firePosition)?.id ?: "missing"
        val ticker = run.f6FireTicker?.javaClass?.simpleName ?: "none"
        return "phase=${run.f6Phase} stored=$stored chargeTarget=${run.f6ChargeTarget} " +
            "fire{pos=${firePosition.x},${firePosition.y},${firePosition.z} " +
            "expected=${run.f6FireFluid?.id ?: "none"} seen=$seen ticker=$ticker level=$level " +
            "support=$support reasserts=${run.f6FireReasserts} face=+Y} " +
            fluidSelfTestMachineComponentDetails(run, store)
    }
}

private object FluidSelfTestFootprint {
    fun fixture(
        position: Position,
        type: BlockType,
    ): Set<Position> {
        val hitbox =
            BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex())
                ?: error("missing hitbox ${type.getHitboxType()} for ${type.id}")
        val positions = linkedSetOf<Position>()
        FillerBlockUtil.forEachFillerBlock(hitbox.get(0)) { x, y, z ->
            positions += Position(position.x + x, position.y + y, position.z + z)
        }
        return positions
    }

    fun isSafe(positions: Collection<Position>): Boolean {
        val fluidChunk =
            ChunkUtil.indexChunk(
                FluidSelfTestCases.BASE.x shr 5,
                FluidSelfTestCases.BASE.z shr 5,
            )
        return positions.all { position ->
            position.x !in 10_200..10_260 &&
                ChunkUtil.indexChunk(position.x shr 5, position.z shr 5) == fluidChunk
        }
    }

    fun assertSafe(
        fixturePositions: List<Position>,
        gnasherFootprints: Map<Position, Set<Position>>,
    ) {
        assertGnasherFillersDoNotOverlap(fixturePositions, gnasherFootprints)
        val footprint =
            fixturePositions.toMutableSet().apply {
                gnasherFootprints.values.forEach { addAll(it) }
            }
        val overlap = footprint.firstOrNull { it.x in 10_200..10_260 }
        check(overlap == null) { "fluid test fixture overlaps transport range at $overlap" }
        assertInsideRetainedChunk(footprint)
    }

    private fun assertGnasherFillersDoNotOverlap(
        fixturePositions: List<Position>,
        gnasherFootprints: Map<Position, Set<Position>>,
    ) {
        gnasherFootprints.forEach { (origin, footprint) ->
            val fillers = footprint - origin
            val overlappingFixture =
                fillers.firstOrNull { filler ->
                    fixturePositions.any { fixture -> fixture != origin && fixture == filler }
                }
            check(overlappingFixture == null) {
                "fluid test fixture $overlappingFixture overlaps Gnasher filler at $origin"
            }
            val overlappingGnasher =
                fillers.firstOrNull { filler ->
                    gnasherFootprints.any { (otherOrigin, otherFootprint) ->
                        otherOrigin != origin && filler in otherFootprint
                    }
                }
            check(overlappingGnasher == null) {
                "fluid test fixture $overlappingGnasher overlaps Gnasher filler at $origin"
            }
        }
    }

    private fun assertInsideRetainedChunk(positions: Set<Position>) {
        val fluidChunk =
            ChunkUtil.indexChunk(
                FluidSelfTestCases.BASE.x shr 5,
                FluidSelfTestCases.BASE.z shr 5,
            )
        val outsideChunk =
            positions.firstOrNull {
                ChunkUtil.indexChunk(it.x shr 5, it.z shr 5) != fluidChunk
            }
        check(outsideChunk == null) { "fluid test fixture is outside retained chunk at $outsideChunk" }
    }
}

private fun fluidSelfTestMachineComponentDetails(
    run: FluidSelfTestRun.Run,
    store: Store<ChunkStore>,
): String =
    "boiler{${fluidSelfTestBlockComponentDetails(run, store, FluidSelfTestCases.BOILER)}} " +
        "gasbag{${fluidSelfTestBlockComponentDetails(run, store, FluidSelfTestCases.GASBAG)}}"

private fun fluidSelfTestBlockComponentDetails(
    run: FluidSelfTestRun.Run,
    store: Store<ChunkStore>,
    position: Position,
): String {
    val sectionRef =
        run.world.chunkStore
            .getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
            ?.takeIf { it.isValid }
    val blockSection = sectionRef?.let { store.getComponent(it, BlockSection.getComponentType()) }
    val blockIndex = blockSection?.get(position.x, position.y, position.z)
    val blockId =
        blockIndex?.let { BlockType.getAssetMap().getAsset(it)?.id ?: "index=$it" }
            ?: "missing"
    val blockEntityRef = FluidSelfTestSetup.blockEntity(run.world, position)
    val boilerStatePresent =
        blockEntityRef?.let { store.getComponent(it, BoilerState.type) } != null
    val gasbagStatePresent =
        blockEntityRef?.let { store.getComponent(it, GasbagState.type) } != null
    val energyMemberPresent =
        blockEntityRef?.let { store.getComponent(it, EnergyMember.type) } != null
    val machineOwnerPresent =
        blockEntityRef?.let { store.getComponent(it, MachineOwner.type) } != null
    return "pos=${position.x},${position.y},${position.z} blockId=$blockId " +
        "blockEntityPresent=${blockEntityRef != null} BoilerState=$boilerStatePresent " +
        "GasbagState=$gasbagStatePresent EnergyMember=$energyMemberPresent MachineOwner=$machineOwnerPresent"
}

private fun waitForFluidSelfTestComponents(
    run: FluidSelfTestRun.Run,
    store: Store<ChunkStore>,
    caseName: String,
    timeoutTicks: Long,
    missing: List<String>,
): Boolean {
    if (missing.isEmpty()) return false
    if (FluidSelfTestState.elapsed(run) > timeoutTicks) {
        FluidSelfTestState.fail(
            run,
            caseName,
            "missing=${missing.joinToString()} ${FluidSelfTestCases.timeoutDetail(run, store)}",
        )
        FluidSelfTestState.next(run)
    }
    return true
}

private fun fluidSelfTestTimeoutTicks(stage: Int): Long =
    when (stage) {
        0, 1, 3, 4 -> FluidSelfTestCases.CASE_TIMEOUT_TICKS
        2, 5 -> FluidSelfTestCases.F3_TIMEOUT_TICKS
        6 -> FluidSelfTestCases.RELOAD_TIMEOUT_TICKS
        7 -> FluidSelfTestCases.SECTION_TIMEOUT_TICKS
        else -> FluidSelfTestCases.CLEANUP_TIMEOUT_TICKS
    }

internal object FluidSelfTestReload {
    fun tick(run: FluidSelfTestRun.Run) {
        val chunkIndex = ChunkUtil.indexChunk(FluidSelfTestCases.BASE.x shr 5, FluidSelfTestCases.BASE.z shr 5)
        val failure = run.reloadFailure
        if (failure != null) {
            FluidSelfTestState.fail(run, "F7", failure)
            releaseSaveHold(run, chunkIndex)
            FluidSelfTestState.next(run)
            return
        }
        if (!run.reloadStarted) {
            beginUnload(run, chunkIndex)
            return
        }
        if (run.reloadSaved && !run.reloadRequested) requestReload(run, chunkIndex)
        if (run.reloadComplete) verifyFluid(run, chunkIndex)
        if (!run.reloadComplete && FluidSelfTestState.elapsed(run) > FluidSelfTestCases.RELOAD_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F7", FluidSelfTestCases.timeoutDetail(run, run.world.chunkStore.store))
            releaseSaveHold(run, chunkIndex)
            run.testTicking = true
            FluidSelfTestState.next(run)
        }
    }

    fun releaseSaveHold(
        run: FluidSelfTestRun.Run,
        chunkIndex: Long,
    ) {
        if (!run.reloadSaveHold) return
        val ref =
            run.world.chunkStore
                .getChunkReference(chunkIndex)
                ?.takeIf { it.isValid }
        val worldChunk =
            ref?.let {
                run.world.chunkStore.store
                    .getComponent(it, WorldChunk.getComponentType())
            }
        if (worldChunk != null) {
            worldChunk.setSaving(false)
            worldChunk.removeKeepLoaded()
        }
        run.reloadSaveHold = false
    }

    private fun beginUnload(
        run: FluidSelfTestRun.Run,
        chunkIndex: Long,
    ) {
        val grid = run.world.chunkStore
        val store = grid.store
        val fluid = FluidTransport.of(run.world)
        run.frozenAmount =
            fluid.amount(
                FluidSelfTestCases.F1_TANK.x,
                FluidSelfTestCases.F1_TANK.y,
                FluidSelfTestCases.F1_TANK.z,
            )
        FluidSelfTestSetup.releaseTestChunk(run, store, chunkIndex)
        run.testTicking = false
        run.reloadStarted = true
        run.stageStartTick = run.world.tick
        val ref = grid.getChunkReference(chunkIndex)?.takeIf { it.isValid } ?: error("F7 test chunk is missing")
        val worldChunk = store.getComponent(ref, WorldChunk.getComponentType()) ?: error("F7 WorldChunk is missing")
        val saveEvent = ChunkSaveEvent(worldChunk)
        store.invoke(ref, saveEvent)
        check(!saveEvent.isCancelled) { "F7 chunk save was cancelled" }
        worldChunk.addKeepLoaded()
        worldChunk.setSaving(true)
        run.reloadSaveHold = true
        run.reloadSaving = true
        val saver = grid.getSaver() ?: error("F7 chunk saver is unavailable")
        saver.saveChunkColumn(worldChunk.x, worldChunk.z, store, ref, null, null).whenComplete { _, failure ->
            run.world.execute {
                run.reloadSaving = false
                if (failure != null) {
                    releaseSaveHold(run, chunkIndex)
                    run.reloadFailure = "chunk save failed (${failure.message})"
                    return@execute
                }
                unloadSavedChunk(run, chunkIndex)
            }
        }
    }

    private fun unloadSavedChunk(
        run: FluidSelfTestRun.Run,
        chunkIndex: Long,
    ) {
        try {
            val grid = run.world.chunkStore
            val ref =
                grid.getChunkReference(chunkIndex)?.takeIf { it.isValid }
                    ?: error("F7 saved chunk reference is missing")
            val worldChunk =
                grid.store.getComponent(ref, WorldChunk.getComponentType())
                    ?: error("F7 saved WorldChunk is missing")
            worldChunk.consumeNeedsSaving()
            worldChunk.setFlag(ChunkFlag.ON_DISK, true)
            releaseSaveHold(run, chunkIndex)
            grid.remove(ref, RemoveReason.UNLOAD)
            check(!ref.isValid) { "F7 chunk did not unload" }
            run.reloadSaved = true
        } catch (failure: IllegalStateException) {
            releaseSaveHold(run, chunkIndex)
            run.reloadFailure = "chunk unload failed (${failure.message})"
        } catch (failure: IllegalArgumentException) {
            releaseSaveHold(run, chunkIndex)
            run.reloadFailure = "chunk unload failed (${failure.message})"
        }
    }

    private fun requestReload(
        run: FluidSelfTestRun.Run,
        chunkIndex: Long,
    ) {
        val grid = run.world.chunkStore
        run.reloadRequested = true
        grid.getChunkReferenceAsync(chunkIndex, GetChunkFlags.SET_TICKING).whenComplete { ref, failure ->
            run.world.execute {
                if (failure != null || ref == null || !ref.isValid) {
                    run.reloadFailure = "chunk reload failed (${failure?.message ?: "invalid ref"})"
                    return@execute
                }
                grid
                    .getChunkSectionReferenceAtBlockAsync(
                        FluidSelfTestCases.BASE.x,
                        FluidSelfTestCases.BASE.y,
                        FluidSelfTestCases.BASE.z,
                        GetChunkFlags.SET_TICKING,
                    ).whenComplete { sectionRef, sectionFailure ->
                        run.world.execute {
                            if (sectionFailure != null || sectionRef == null || !sectionRef.isValid) {
                                run.reloadFailure =
                                    "chunk section reload failed (${sectionFailure?.message ?: "invalid ref"})"
                            } else {
                                run.reloadComplete = true
                                run.testTicking = true
                            }
                        }
                    }
            }
        }
    }

    private fun verifyFluid(
        run: FluidSelfTestRun.Run,
        chunkIndex: Long,
    ) {
        val sectionRef =
            run.world.chunkStore.getChunkSectionReferenceAtBlock(
                FluidSelfTestCases.BASE.x,
                FluidSelfTestCases.BASE.y,
                FluidSelfTestCases.BASE.z,
            )
        if (sectionRef?.isValid != true) return
        FluidSelfTestSetup.retainTestChunk(run, run.world.chunkStore.store, chunkIndex)
        val position = FluidSelfTestCases.F1_TANK
        val amount = FluidTransport.of(run.world).amount(position.x, position.y, position.z)
        if (amount == run.frozenAmount) {
            FluidSelfTestState.pass(run, "F7 fluid amount survives real chunk unload/reload")
        } else {
            FluidSelfTestState.fail(run, "F7", "amount changed ${run.frozenAmount}->$amount")
        }
        FluidSelfTestState.next(run)
    }
}

internal object FluidSelfTestCleanup {
    fun cleanup(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        if (run.cleanupComplete) return
        if (!run.blocksCleared) {
            FluidSelfTestSetup.clearFluid(run.world, FluidSelfTestCases.GASBAG_FIRE)
            run.blocks.forEach { FluidSelfTestSetup.clearBlock(run.world, it) }
            val chunkIndex =
                ChunkUtil.indexChunk(
                    FluidSelfTestCases.BASE.x shr 5,
                    FluidSelfTestCases.BASE.z shr 5,
                )
            FluidSelfTestSetup.releaseTestChunk(run, store, chunkIndex)
            FluidSelfTestReload.releaseSaveHold(run, chunkIndex)
            FluidSelfTestDrops.clearDrops(run.world)
            run.blocksCleared = true
            run.cleanupStartedTick = run.world.tick
            return
        }
        if (run.world.tick - run.cleanupStartedTick < 2L) return
        val fluid = FluidTransport.of(run.world)
        val remainingFluidNodes = run.blocks.count { fluid.isNode(it.x, it.y, it.z) }
        val energy = CraftoryCore.energyAccess(run.world)
        val remainingEnergyMembers =
            run.blocks.count { energy.networkMembers(BlockPos.pack(it.x, it.y, it.z)) > 0 }
        val drops = FluidSelfTestDrops.countDrops(run.world)
        val graphsCleared = remainingFluidNodes == 0 && remainingEnergyMembers == 0
        val resourcesReleased = run.keepLoadedCount == 0 && drops == 0
        if (graphsCleared && resourcesReleased) {
            FluidSelfTestState.pass(run, "F9 graph, energy, keep-loaded, and drop cleanup")
            run.context.sendMessage(Message.raw("selftest: fluids ${run.passed} passed, ${run.failed} failed"))
            run.cleanupComplete = true
            store.getResource(FluidSelfTestRun.type).active = null
            run.onComplete?.invoke()
        } else if (run.world.tick - run.cleanupStartedTick > FluidSelfTestCases.CLEANUP_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(
                run,
                "F9",
                timeoutDetail(run),
            )
            run.context.sendMessage(Message.raw("selftest: fluids ${run.passed} passed, ${run.failed} failed"))
            run.cleanupComplete = true
            store.getResource(FluidSelfTestRun.type).active = null
            run.onComplete?.invoke()
        }
    }

    fun timeoutDetail(run: FluidSelfTestRun.Run): String {
        val fluid = FluidTransport.of(run.world)
        val remainingFluidNodes = run.blocks.count { fluid.isNode(it.x, it.y, it.z) }
        val energy = CraftoryCore.energyAccess(run.world)
        val remainingEnergyMembers =
            run.blocks.count { energy.networkMembers(BlockPos.pack(it.x, it.y, it.z)) > 0 }
        val drops = FluidSelfTestDrops.countDrops(run.world)
        return "waiting for graph/energy/keep-loaded/drop cleanup; fluidNodes=$remainingFluidNodes " +
            "energyMembers=$remainingEnergyMembers keepLoaded=${run.keepLoadedCount} drops=$drops " +
            "cleanupStarted=${run.blocksCleared}"
    }
}

internal object FluidSelfTestSetup {
    fun placeBlock(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        sectionRef: com.hypixel.hytale.component.Ref<ChunkStore>,
        section: BlockComponentSection,
        blockId: String,
        position: Position,
    ) {
        val type = BlockType.fromString(blockId) ?: error("missing fluid test block $blockId")
        val index = BlockType.getAssetMap().getIndex(type.id)
        check(
            BlockOperations.setBlock(
                run.world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                index,
                type,
                0,
                0,
                SetBlockSettings.NO_UPDATE_STATE,
            ),
        ) { placementFailureDetail(run, store, sectionRef, blockId, position) }
        BlockEntity.ensureBlockEntity(
            store,
            sectionRef,
            section,
            position.x and 31,
            position.y and 31,
            position.z and 31,
            type,
            0,
        ) ?: error("failed to create fluid test block entity for $blockId at $position")
    }

    private fun placementFailureDetail(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        sectionRef: com.hypixel.hytale.component.Ref<ChunkStore>,
        blockId: String,
        position: Position,
    ): String {
        val blockSection = store.getComponent(sectionRef, BlockSection.getComponentType())
        val foundIndex = blockSection?.get(position.x, position.y, position.z) ?: BlockType.EMPTY_ID
        val foundType = BlockType.getAssetMap().getAsset(foundIndex)
        val foundId = foundType?.id ?: "index=$foundIndex"
        val baseId = foundType?.let { baseBlockId(it) } ?: foundId
        val filler = blockSection?.getFiller(position.x, position.y, position.z) ?: -1
        val rotation = blockSection?.getRotationIndex(position.x, position.y, position.z) ?: -1
        val blockEntity =
            BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)?.isValid == true
        return "failed to place $blockId at $position " +
            "(found=$foundId base=$baseId filler=$filler rotation=$rotation blockEntity=$blockEntity)"
    }

    fun addBurnerFuel(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        position: Position,
        count: Int = 2,
    ) {
        val ref =
            FluidSelfTestSetup.blockEntity(run.world, position)
                ?: error("test Burner block entity missing at $position")
        val state = store.getComponent(ref, BurnerState.type) ?: error("test Burner state missing")
        val member = store.getComponent(ref, EnergyMember.type) ?: error("test Burner energy member missing")
        val blockState =
            store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: error("test Burner block state missing")
        repeat(count) {
            check(
                MachineActions.addFuel(
                    state,
                    member,
                    blockState,
                    store,
                    run.world.tick,
                    "Plant_Crop_Mushroom_Common_Brown",
                ) > 0,
            ) { "test Burner fuel could not be seeded" }
        }
        check(MachineActions.kick(state, member, blockState, store, run.world.tick)) { "test Burner did not kick" }
    }

    fun clearFluid(
        world: World,
        position: Position,
    ) {
        val sectionRef =
            world.chunkStore
                .getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?.takeIf { it.isValid } ?: return
        val fluidSection =
            world.chunkStore.store.getComponent(sectionRef, FluidSection.getComponentType()) ?: return
        fluidSection.setFluid(position.x and 31, position.y and 31, position.z and 31, 0, 0.toByte())
    }

    fun keepTestSectionsTicking(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        if (!run.testTicking || run.blocksCleared || run.cleanupComplete) return
        val sectionRef =
            runCatching {
                run.world.chunkStore
                    .getChunkSectionReferenceAtBlockAsync(
                        FluidSelfTestCases.BASE.x,
                        FluidSelfTestCases.BASE.y,
                        FluidSelfTestCases.BASE.z,
                        GetChunkFlags.SET_TICKING,
                    ).getNow(null)
            }.getOrNull() ?: return
        if (!sectionRef.isValid) return
        store.getComponent(sectionRef, ChunkSection.getComponentType())?.resetActiveTimer()
        FluidSelfTestSetup.retainTestChunk(
            run,
            store,
            ChunkUtil.indexChunk(FluidSelfTestCases.BASE.x shr 5, FluidSelfTestCases.BASE.z shr 5),
        )
    }

    fun retainTestChunk(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        chunkIndex: Long,
    ) {
        val ref =
            run.world.chunkStore
                .getChunkReference(chunkIndex)
                ?.takeIf { it.isValid } ?: return
        val worldChunk = store.getComponent(ref, WorldChunk.getComponentType()) ?: return
        if (!run.heldChunk) {
            worldChunk.addKeepLoaded()
            run.heldChunk = true
            run.keepLoadedCount++
        }
    }

    fun releaseTestChunk(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        chunkIndex: Long,
    ) {
        if (!run.heldChunk) return
        val ref =
            run.world.chunkStore
                .getChunkReference(chunkIndex)
                ?.takeIf { it.isValid }
        ref?.let { store.getComponent(it, WorldChunk.getComponentType())?.removeKeepLoaded() }
        run.heldChunk = false
        run.keepLoadedCount = (run.keepLoadedCount - 1).coerceAtLeast(0)
    }

    fun clearBlock(
        world: World,
        position: Position,
    ) {
        val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z) ?: return
        if (!sectionRef.isValid) return
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

    fun blockEntity(
        world: World,
        position: Position,
    ) = BlockModule.getBlockEntity(world, position.x, position.y, position.z)?.takeIf { it.isValid }
}

internal object FluidSelfTestDrops {
    fun clearDrops(world: World) {
        world.entityStore.store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    val position =
                        chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: continue
                    if (inTestArea(position.x, position.y, position.z)) {
                        commandBuffer.removeEntity(
                            chunk.getReferenceTo(index),
                            RemoveReason.REMOVE,
                        )
                    }
                }
            },
        )
    }

    fun countDrops(world: World): Int {
        var count = 0
        world.entityStore.store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val position =
                        chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: continue
                    if (inTestArea(position.x, position.y, position.z)) count++
                }
            },
        )
        return count
    }

    private fun inTestArea(
        x: Double,
        y: Double,
        z: Double,
    ): Boolean =
        x in FluidSelfTestCases.BASE.x.toDouble()..(FluidSelfTestCases.BASE.x + 24).toDouble() &&
            y in FluidSelfTestCases.BASE.y.toDouble()..(FluidSelfTestCases.BASE.y + 1).toDouble() &&
            z in FluidSelfTestCases.BASE.z.toDouble()..(FluidSelfTestCases.BASE.z + 1).toDouble()
}

internal object FluidSelfTestState {
    fun pass(
        run: FluidSelfTestRun.Run,
        label: String,
    ) {
        run.passed++
        run.context.sendMessage(Message.raw("selftest: PASS $label"))
    }

    fun fail(
        run: FluidSelfTestRun.Run,
        label: String,
        detail: String,
    ) {
        run.failed++
        run.context.sendMessage(Message.raw("selftest: FAIL $label ($detail)"))
    }

    fun next(run: FluidSelfTestRun.Run) {
        run.stage++
        run.stageStartTick = run.world.tick
    }

    fun elapsed(run: FluidSelfTestRun.Run): Long = run.world.tick - run.stageStartTick
}

internal object FluidSelfTest {
    fun start(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
        onComplete: (() -> Unit)? = null,
    ) {
        world.execute {
            val resource = world.chunkStore.store.getResource(FluidSelfTestRun.type)
            if (resource.active != null) return@execute
            val run = FluidSelfTestRun.Run(context, world, world.tick)
            run.onComplete = onComplete
            resource.active = run
            FluidSelfTestAssetChecks.check(run)
            context.sendMessage(Message.raw("selftest: starting fluid cases F1-F9"))
        }
    }
}

private object FluidSelfTestAssetChecks {
    fun check(run: FluidSelfTestRun.Run) {
        check(run, "Water_Source fluid") { Fluid.getAssetMap().getAsset("Water_Source") != null }
        check(run, "Water fluid") { Fluid.getAssetMap().getAsset("Water") != null }
        check(run, "Lava_Source fluid") { Fluid.getAssetMap().getAsset("Lava_Source") != null }
        check(run, "Lava fluid") { Fluid.getAssetMap().getAsset("Lava") != null }

        val bucket = Item.getAssetMap().getAsset(CraftoryIds.EMPTY_WOODEN_BUCKET)
        check(run, "Container_Bucket item") { bucket != null }
        val filledWaterBucketId = bucket?.getItemIdForState("Filled_Water")
        check(run, "Container_Bucket Filled_Water state") {
            filledWaterBucketId != null && Item.getAssetMap().getAsset(filledWaterBucketId) != null
        }
        check(run, GutterBoilerEffects.VENT_POP_SOUND_ID) {
            SoundEvent.getAssetMap().getIndex(GutterBoilerEffects.VENT_POP_SOUND_ID) >= 0
        }
        check(run, GutterBoilerEffects.HIGH_PRESSURE_SOUND_ID) {
            SoundEvent.getAssetMap().getIndex(GutterBoilerEffects.HIGH_PRESSURE_SOUND_ID) >= 0
        }
        check(run, GutterBoilerEffects.STEAM_PARTICLE_ID) {
            ParticleSystem.getAssetMap().getAsset(GutterBoilerEffects.STEAM_PARTICLE_ID) != null
        }
    }

    private inline fun check(
        run: FluidSelfTestRun.Run,
        id: String,
        isPresent: () -> Boolean,
    ) {
        if (!isPresent()) FluidSelfTestState.fail(run, "assets", "missing asset: $id")
    }
}
