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
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
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
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
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
                FluidSelfTestState.fail(run, "F${run.stage}", "selftest timed out")
                FluidSelfTestCleanup.cleanup(run, store)
                return
            }
            FluidSelfTestSetup.keepTestSectionsTicking(run, store)
            if (!run.initialized) {
                if (!run.loading) FluidSelfTestCases.loadAndPlace(run)
                return
            }
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
        FluidSelfTestState.fail(
            run,
            "F${run.stage}",
            failure.message ?: failure.javaClass.simpleName,
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
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, F1_PIPE)
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
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, BOILER_PIPE)
                        FluidSelfTestSetup.placeBlock(
                            run,
                            store,
                            sectionRef,
                            section,
                            CraftoryIds.GNASHER_PIT,
                            PRESSURISED_GNASHER,
                        )
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.BURNER, BURNER)
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, BURNER_PIPE)
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
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.PIPE, GASBAG_PIPE)
                        FluidSelfTestSetup.placeBlock(run, store, sectionRef, section, CraftoryIds.GASBAG, GASBAG)
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
            FluidSelfTestState.fail(run, "F1", "tank stayed empty beside Water_Source")
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
            FluidSelfTestState.fail(run, "F2", "stacked=$stacked side=$side")
            FluidSelfTestState.next(run)
        }
    }

    fun tickF3(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val boilerRef = FluidSelfTestSetup.blockEntity(run.world, BOILER) ?: return
        val boiler = store.getComponent(boilerRef, BoilerState.type) ?: return
        val boilerMember = store.getComponent(boilerRef, EnergyMember.type) ?: return
        val pressurisedRef = FluidSelfTestSetup.blockEntity(run.world, PRESSURISED_GNASHER) ?: return
        val plainRef = FluidSelfTestSetup.blockEntity(run.world, PLAIN_GNASHER) ?: return
        val pressurisedState = store.getComponent(pressurisedRef, GnasherState.type) ?: return
        val plainState = store.getComponent(plainRef, GnasherState.type) ?: return
        if (!run.f3Setup) {
            boiler.pressure = 85
            boiler.water = GutterBoilerRules.CAPACITY
            boiler.fuelTicks = 1_000
            pressurisedState.oreQueue = GritTable.encodeOreQueue(listOf("Ore_Iron_Stone"))
            plainState.oreQueue = GritTable.encodeOreQueue(listOf("Ore_Iron_Stone"))
            FluidSelfTestSetup.addBurnerFuel(run, store, BURNER)
            FluidSelfTestSetup.addBurnerFuel(run, store, GASBAG_BURNER)
            run.f3Setup = true
        }
        val tick = run.world.tick
        if (pressurisedState.lastAutoCrankTick != run.highPulseTick) {
            if (run.highPulseTick != Long.MIN_VALUE) {
                run.highPulseInterval = pressurisedState.lastAutoCrankTick - run.highPulseTick
            }
            run.highPulseTick = pressurisedState.lastAutoCrankTick
        }
        if (plainState.lastAutoCrankTick != run.lowPulseTick) {
            if (run.lowPulseTick != Long.MIN_VALUE) {
                run.lowPulseInterval = plainState.lastAutoCrankTick - run.lowPulseTick
            }
            run.lowPulseTick = plainState.lastAutoCrankTick
        }
        val boilerRank = boilerMember.productionRank
        val productionReady =
            boilerRank == GutterBoilerSystem.PRESSURISED_RANK && boilerMember.production > 0
        val pulseIntervalsReady =
            run.highPulseInterval == GnasherMachine.AUTO_PULSE_INTERVAL_TICKS &&
                run.lowPulseInterval == GnasherMachine.AUTO_PULSE_INTERVAL_TICKS * 2
        if (productionReady && pulseIntervalsReady) {
            FluidSelfTestState.pass(run, "F3 Pressurised rank 1 and 20/40 tick Gnasher pulses")
            FluidSelfTestState.next(run)
        } else if (tick - run.stageStartTick > F3_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(
                run,
                "F3",
                "production=${boilerMember.production}/${boilerMember.productionRank} " +
                    "pulseIntervals=${run.highPulseInterval}/${run.lowPulseInterval}",
            )
            FluidSelfTestState.next(run)
        }
    }

    fun tickF4(run: FluidSelfTestRun.Run) {
        val boilerRef = FluidSelfTestSetup.blockEntity(run.world, BOILER) ?: return
        val boiler =
            run.world.chunkStore.store
                .getComponent(boilerRef, BoilerState.type) ?: return
        boiler.pressure = 97
        val vented = GutterBoilerRules.vent(boiler)
        if (vented && boiler.pressure == 57) {
            FluidSelfTestState.pass(run, "F4 shared vent path drops 97 to 57")
        } else {
            FluidSelfTestState.fail(run, "F4", "vented=$vented pressure=${boiler.pressure}")
        }
        FluidSelfTestState.next(run)
    }

    fun tickF5(run: FluidSelfTestRun.Run) {
        val boilerRef = FluidSelfTestSetup.blockEntity(run.world, BOILER)
        if (boilerRef == null) {
            if (FluidSelfTestState.elapsed(run) > CASE_TIMEOUT_TICKS) {
                FluidSelfTestState.fail(run, "F5", "boiler block entity disappeared during pop")
                FluidSelfTestState.next(run)
            }
            return
        }
        val store = run.world.chunkStore.store
        val boiler = store.getComponent(boilerRef, BoilerState.type) ?: return
        if (!run.f5Setup) {
            boiler.pressure = 99
            boiler.water = GutterBoilerRules.CAPACITY
            boiler.fuelTicks = 1_000
            boiler.online = true
            run.f5Setup = true
        }
        if (!boiler.online) {
            val blockIndex =
                store.getComponent(boilerRef, BlockModule.BlockStateInfo.getComponentType())?.index
            val expectedBlockIndex = BlockType.getAssetMap().getIndex(CraftoryIds.GUTTER_BOILER)
            val noPressureOrWater = boiler.pressure == 0 && boiler.water == 0L
            val boilerBlockUnchanged = boilerRef.isValid && blockIndex == expectedBlockIndex
            if (noPressureOrWater && boilerBlockUnchanged) {
                val reriveted = GutterBoilerRules.rerivet(boiler, 2)
                if (reriveted && boiler.online) {
                    FluidSelfTestState.pass(run, "F5 pop stays in place and two scraps re-rivet")
                } else {
                    FluidSelfTestState.fail(run, "F5", "shared re-rivet failed")
                }
            } else {
                FluidSelfTestState.fail(
                    run,
                    "F5",
                    "offline=${!boiler.online} pressure=${boiler.pressure} " +
                        "water=${boiler.water} blockUnchanged=$boilerBlockUnchanged",
                )
            }
            FluidSelfTestState.next(run)
        } else if (FluidSelfTestState.elapsed(run) > CASE_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F5", "boiler did not pop")
            FluidSelfTestState.next(run)
        }
    }

    fun tickF6(run: FluidSelfTestRun.Run) {
        val ref = FluidSelfTestSetup.blockEntity(run.world, GASBAG) ?: return
        val store = run.world.chunkStore.store
        val state = store.getComponent(ref, GasbagState.type) ?: return
        val energy = CraftoryCore.energyAccess(run.world)
        val pos = BlockPos.pack(GASBAG.x, GASBAG.y, GASBAG.z)
        val stored = energy.stored(pos)
        if (stored < GasbagRules.leakAmount(GASBAG_CAPACITY)) {
            if (FluidSelfTestState.elapsed(run) > F3_TIMEOUT_TICKS) {
                FluidSelfTestState.fail(run, "F6", "gasbag did not charge from its Burner network ($stored)")
                FluidSelfTestState.next(run)
            }
            return
        }
        if (!state.leaking) {
            run.leakAmount = stored
            state.leaking = true
            run.stageStartTick = run.world.tick
            return
        }
        if (run.gasbagPatched) {
            if (FluidSelfTestState.elapsed(run) >= TICKS_PER_SECOND) {
                val stoppedAmount = energy.stored(pos)
                if (stoppedAmount >= run.leakAmount) {
                    FluidSelfTestState.pass(run, "F6 Gasbag leaks 1%/s and shared patch stops it")
                } else {
                    FluidSelfTestState.fail(
                        run,
                        "F6",
                        "stored amount changed after patch ${run.leakAmount}->$stoppedAmount",
                    )
                }
                FluidSelfTestState.next(run)
            }
        } else if (FluidSelfTestState.elapsed(run) >= TICKS_PER_SECOND &&
            run.leakAmount - stored >= GasbagRules.leakAmount(GASBAG_CAPACITY)
        ) {
            if (!GasbagRules.patch(state)) {
                FluidSelfTestState.fail(run, "F6", "shared patch helper did not stop leakage")
            } else {
                val patchedAmount = energy.stored(pos)
                run.stageStartTick = run.world.tick
                run.leakAmount = patchedAmount
                run.gasbagPatched = true
            }
        } else if (FluidSelfTestState.elapsed(run) > F3_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(run, "F6", "leak did not drain at 1%/s (before=${run.leakAmount}, now=$stored)")
            FluidSelfTestState.next(run)
        }
    }

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
                FluidSelfTestState.fail(run, "F8", "test section did not become NonTicking")
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
                    "tank changed in NonTicking area ${run.frozenAmount}->$amount",
                )
            }
            FluidSelfTestState.next(run)
        }
    }

    fun testPositions(): List<Position> =
        listOf(
            SOURCE,
            F1_PIPE,
            F1_TANK,
            F2_BOTTOM,
            F2_TOP,
            F2_SIDE,
            BOILER,
            BOILER_PIPE,
            PRESSURISED_GNASHER,
            BURNER,
            BURNER_PIPE,
            PLAIN_GNASHER,
            GASBAG_BURNER,
            GASBAG_PIPE,
            GASBAG,
        )

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
    val BOILER_PIPE = Position(BASE.x + 8, BASE.y, BASE.z)
    val PRESSURISED_GNASHER = Position(BASE.x + 9, BASE.y, BASE.z)
    val BURNER = Position(BASE.x + 12, BASE.y, BASE.z)
    val BURNER_PIPE = Position(BASE.x + 13, BASE.y, BASE.z)
    val PLAIN_GNASHER = Position(BASE.x + 14, BASE.y, BASE.z)
    val GASBAG_BURNER = Position(BASE.x + 17, BASE.y, BASE.z)
    val GASBAG_PIPE = Position(BASE.x + 18, BASE.y, BASE.z)
    val GASBAG = Position(BASE.x + 19, BASE.y, BASE.z)
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
            FluidSelfTestState.fail(run, "F7", "fluid chunk unload/reload timed out")
            releaseSaveHold(run, chunkIndex)
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
        } else if (run.world.tick - run.cleanupStartedTick > FluidSelfTestCases.CLEANUP_TIMEOUT_TICKS) {
            FluidSelfTestState.fail(
                run,
                "F9",
                "fluidNodes=$remainingFluidNodes energyMembers=$remainingEnergyMembers " +
                    "keepLoaded=${run.keepLoadedCount} drops=$drops",
            )
            run.context.sendMessage(Message.raw("selftest: fluids ${run.passed} passed, ${run.failed} failed"))
            run.cleanupComplete = true
            store.getResource(FluidSelfTestRun.type).active = null
        }
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
        ) { "failed to place $blockId at $position" }
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

    fun addBurnerFuel(
        run: FluidSelfTestRun.Run,
        store: Store<ChunkStore>,
        position: Position,
    ) {
        val ref =
            FluidSelfTestSetup.blockEntity(run.world, position)
                ?: error("test Burner block entity missing at $position")
        val state = store.getComponent(ref, BurnerState.type) ?: error("test Burner state missing")
        val member = store.getComponent(ref, EnergyMember.type) ?: error("test Burner energy member missing")
        val blockState =
            store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: error("test Burner block state missing")
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
            ) { "test Burner fuel could not be seeded" }
        }
        check(MachineActions.kick(state, member, blockState, store, run.world.tick)) { "test Burner did not kick" }
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
    ) {
        world.execute {
            val resource = world.chunkStore.store.getResource(FluidSelfTestRun.type)
            if (resource.active != null) return@execute
            val run = FluidSelfTestRun.Run(context, world, world.tick)
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
