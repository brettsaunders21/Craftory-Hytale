package studio.craftory.tech.machine

import com.hypixel.hytale.codec.ExtraInfo
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.mob.CraftoryMobKill
import studio.craftory.core.api.mob.CraftoryMobs
import studio.craftory.core.api.mob.MobHome
import studio.craftory.tech.CraftoryIds
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

@Suppress("TooManyFunctions", "TooGenericExceptionCaught", "ComplexCondition")
internal object MobSelfTest {
    internal class State(
        val run: SelfTestRun.Run,
        val sectionRef: Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>,
    ) {
        val refs = mutableListOf<Ref<EntityStore>>()
        val roleRefs = mutableListOf<Ref<EntityStore>>()
        val floorCells = mutableListOf<BlockPos>()
        var kill: CraftoryMobKill? = null
        var subscription: AutoCloseable? = null
        var spawnPolls = 0
        var killPolls = 0
        var expectedKilledRoleId: String? = null
        var drainStart = 0L
        var drainChargePolls = 0
    }

    fun start(run: SelfTestRun.Run) {
        val world = run.world
        world.chunkStore
            .getChunkSectionReferenceAtBlockAsync(MOB_AREA.x, MOB_AREA.y, MOB_AREA.z)
            .whenComplete { sectionRef, error ->
                world.execute {
                    if (error != null || sectionRef == null) {
                        check(run, false, "mob test area", error?.cause?.message ?: "section load failed")
                        finish(run)
                        return@execute
                    }
                    val state = State(run, sectionRef)
                    run.mobSelfTest = state
                    try {
                        placeFloor(state)
                        prepareMobArea(state)
                        verifyHomeCodec(state)
                        logSectionState(
                            state,
                            "mob setup",
                            Vector3d(MOB_AREA.x.toDouble(), MOB_AREA.y.toDouble(), MOB_AREA.z.toDouble()),
                        )
                        beginDrainTest(state)
                    } catch (failure: Exception) {
                        check(run, false, "mob test setup", failure.message ?: failure.javaClass.simpleName)
                        cleanup(state)
                        finish(run)
                    }
                }
            }
    }

    fun cleanup(run: SelfTestRun.Run) {
        run.mobSelfTest?.let(::cleanup)
    }

    private fun prepareMobArea(state: State) {
        val world = state.run.world
        val store = world.chunkStore.store
        val section =
            store.getComponent(state.sectionRef, BlockComponentSection.getComponentType())
                ?: error("mob test block section is missing")
        CraftorySelfTest.place(
            world,
            store,
            state.sectionRef,
            section,
            CraftoryIds.WAKEBELL_ANCHOR,
            MOB_ANCHOR,
            AnchorState.type,
        )
        CraftorySelfTest.place(
            world,
            store,
            state.sectionRef,
            section,
            CraftoryIds.BURNER,
            MOB_BURNER,
            BurnerState.type,
        )
        CraftorySelfTest.place(world, store, state.sectionRef, section, CraftoryIds.PIPE, MOB_PIPE, EnergyMember.type)
        CraftorySelfTest.place(
            world,
            store,
            state.sectionRef,
            section,
            CraftoryIds.CANISTER,
            MOB_CANISTER,
            EnergyMember.type,
        )
        CraftorySelfTest.fuelAndKickBurner(world, store, MOB_BURNER)
        val anchorPosition = MOB_ANCHOR
        val anchorRef =
            BlockModule.getBlockEntity(world, anchorPosition.x, anchorPosition.y, anchorPosition.z)
                ?: error("mob test Anchor is missing")
        val anchor = store.getComponent(anchorRef, AnchorState.type) ?: error("mob test Anchor state is missing")
        val anchorBlock =
            store.getComponent(anchorRef, BlockModule.BlockStateInfo.getComponentType())
                ?: error("mob test Anchor block state is missing")
        CraftoryCore.setAnchorMaxChargeMinutesInMemory(maxOf(state.run.anchorMaxChargeBeforeTest, 1))
        anchor.chargeSeconds = maxOf(anchor.chargeSeconds, DRAIN_AREA_CHARGE_SECONDS)
        MachineActions.activateAnchor(
            world,
            anchor,
            anchorBlock,
            store,
            BlockPos(anchorPosition.x, anchorPosition.y, anchorPosition.z),
        )
        val areaActive = CraftoryCore.isAreaActive(store, state.sectionRef)
        check(state.run, areaActive, "mob test area active")
        check(areaActive) { "mob test area is inactive" }
    }

    private fun placeFloor(state: State) {
        val blockType = BlockType.fromString("Rock_Stone") ?: error("Rock_Stone block is unavailable")
        val blockIndex = BlockType.getAssetMap().getIndex(blockType.id)
        val blockSection =
            state.run.world.chunkStore.store
                .getComponent(state.sectionRef, BlockSection.getComponentType())
                ?: error("mob test block section is missing")
        for (x in FLOOR_MIN_X..FLOOR_MAX_X) {
            for (z in FLOOR_MIN_Z..FLOOR_MAX_Z) {
                if (blockSection.get(ChunkUtil.indexBlock(x, FLOOR_Y, z)) != BlockType.EMPTY_ID) continue
                check(
                    BlockOperations.setBlock(
                        state.run.world.chunkStore,
                        state.sectionRef,
                        x,
                        FLOOR_Y,
                        z,
                        blockIndex,
                        blockType,
                        0,
                        0,
                        SetBlockSettings.NO_UPDATE_STATE,
                    ),
                ) { "could not place mob test floor" }
                state.floorCells += BlockPos(x, FLOOR_Y, z)
            }
        }
    }

    private fun spawnRoles(state: State) {
        val world = state.run.world
        ROLE_IDS.forEachIndexed { index, roleId ->
            val position =
                Vector3d(
                    FLOOR_MIN_X + 0.5 + index,
                    MOB_AREA.y.toDouble(),
                    MOB_AREA.z + 0.5,
                )
            CraftoryMobs.spawn(world, roleId, position, HOME)?.let {
                state.refs += it
                state.roleRefs += it
            }
        }
    }

    private fun pollSpawns(state: State) {
        val allSpawned = state.roleRefs.size == ROLE_COUNT && state.roleRefs.all { it.isValid }
        if (allSpawned || state.spawnPolls >= MAX_POLLS) {
            val store = state.run.world.entityStore.store
            ROLE_IDS.forEach { roleId ->
                val ref = state.roleRefs.firstOrNull { store.getComponent(it, MobHome.type)?.roleId == roleId }
                check(state.run, ref?.isValid == true, "mob spawn $roleId")
                val mobHome = ref?.let { store.getComponent(it, MobHome.type) }
                check(
                    state.run,
                    mobHome?.roleId == roleId && mobHome.home == HOME,
                    "mob home $roleId",
                )
            }
            beginKillTest(state)
            return
        }
        state.spawnPolls++
        afterTicks(state.run.world, POLL_INTERVAL) { pollSpawns(state) }
    }

    private fun verifyHomeCodec(state: State) {
        val mobHome =
            MobHome(
                roleId = ROLE_IDS.first(),
                hasHome = true,
                homeX = HOME.x,
                homeY = HOME.y,
                homeZ = HOME.z,
            )
        val extraInfo = ExtraInfo()
        val decoded = MobHome.CODEC.decode(MobHome.CODEC.encode(mobHome, extraInfo), ExtraInfo())
        check(
            state.run,
            decoded == mobHome &&
                decoded.roleId == ROLE_IDS.first() &&
                decoded.home == HOME,
            "mob home codec round-trip",
        )
    }

    private fun beginKillTest(state: State) {
        state.subscription = CraftoryMobs.onCraftoryMobKilled { state.kill = it }
        val target =
            state.roleRefs.firstOrNull() ?: run {
                check(state.run, false, "mob kill hook")
                finishMobs(state)
                return
            }
        val store = state.run.world.entityStore.store
        state.expectedKilledRoleId = store.getComponent(target, MobHome.type)?.roleId
        store.forEachChunk(
            Query.and(MobHome.type),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    if (chunk.getReferenceTo(index) == target) {
                        DamageSystems.executeDamage(
                            target,
                            commandBuffer,
                            Damage(Damage.NULL_SOURCE, requireNotNull(DamageCause.PHYSICAL), 10_000f),
                        )
                    }
                }
            },
        )
        pollKill(state)
    }

    private fun pollKill(state: State) {
        if (state.kill != null || state.killPolls >= MAX_POLLS) {
            check(
                state.run,
                state.kill?.roleId == state.expectedKilledRoleId,
                "mob kill hook",
                "role=${state.kill?.roleId}",
            )
            state.subscription?.close()
            state.subscription = null
            finishMobs(state)
            return
        }
        state.killPolls++
        afterTicks(state.run.world, POLL_INTERVAL) { pollKill(state) }
    }

    private fun beginDrainTest(state: State) {
        val canisterPosition = BlockPos(MOB_CANISTER.x, MOB_CANISTER.y, MOB_CANISTER.z)
        try {
            if (!prepareCanisterDrain(state, canisterPosition)) {
                beginLiveChecks(state)
                return
            }
            pollCanisterCharge(state, canisterPosition)
        } catch (failure: Exception) {
            check(state.run, false, "riftmoth drain setup", failure.message ?: failure.javaClass.simpleName)
            beginLiveChecks(state)
        }
    }

    private fun prepareCanisterDrain(
        state: State,
        canisterPosition: BlockPos,
    ): Boolean {
        val world = state.run.world
        val store = world.chunkStore.store
        val canisterRef =
            BlockModule.getBlockEntity(world, canisterPosition.x, canisterPosition.y, canisterPosition.z)
                ?: error("mob test canister is missing")
        val canisterBlock =
            store.getComponent(canisterRef, BlockModule.BlockStateInfo.getComponentType())
                ?: error("mob test canister block state is missing")
        val areaActive = CraftoryCore.isAreaActive(store, canisterBlock)
        check(state.run, areaActive, "riftmoth drain area active")
        return areaActive
    }

    private fun pollCanisterCharge(
        state: State,
        canisterPosition: BlockPos,
    ) {
        val world = state.run.world
        val energy = CraftoryCore.energyAccess(world)
        if (energy.stored(canisterPosition.pack()) >= DRAIN_PER_SECOND) {
            isolateAndSpawnDrainMoth(state, canisterPosition)
            return
        }
        if (state.drainChargePolls >= MAX_POLLS) {
            check(
                state.run,
                false,
                "riftmoth canister charges",
                "stored=${energy.stored(canisterPosition.pack())}",
            )
            beginLiveChecks(state)
            return
        }
        state.drainChargePolls++
        afterTicks(world, POLL_INTERVAL) { pollCanisterCharge(state, canisterPosition) }
    }

    private fun isolateAndSpawnDrainMoth(
        state: State,
        canisterPosition: BlockPos,
    ) {
        val world = state.run.world
        val energy = CraftoryCore.energyAccess(world)
        val producerStopped = stopMobCanisterProducer(state)
        CraftorySelfTest.detachCanisterProducers(world, state.sectionRef, listOf(MOB_BURNER, MOB_PIPE))
        afterTicks(world, NETWORK_SETTLE_TICKS) {
            state.drainStart = energy.stored(canisterPosition.pack())
            val supply = energy.networkSupply(canisterPosition.pack())
            check(
                state.run,
                state.drainStart >= DRAIN_PER_SECOND && producerStopped,
                "riftmoth drain canister isolated",
                "stored=${state.drainStart}, producerStopped=$producerStopped, networkSupply=$supply",
            )
            if (state.drainStart < DRAIN_PER_SECOND || !producerStopped) {
                beginLiveChecks(state)
                return@afterTicks
            }
            val spawnPosition = Vector3d(canisterPosition.x + 1.5, canisterPosition.y + 1.0, canisterPosition.z + 0.5)
            val nonTicking = logSectionState(state, "Riftmoth drain spawn", spawnPosition)
            if (nonTicking) {
                state.run.context.sendMessage(
                    Message.raw("selftest: SKIP riftmoth drain (NonTicking: needs a nearby player)"),
                )
                beginLiveChecks(state)
                return@afterTicks
            }
            val moth =
                CraftoryMobs.spawn(
                    world,
                    CraftoryIds.RIFTMOTH,
                    spawnPosition,
                    canisterPosition,
                )
            if (moth != null) state.refs += moth
            afterTicks(world, DRAIN_TEST_TICKS) {
                val remaining = CraftoryCore.energyAccess(world).stored(canisterPosition.pack())
                check(
                    state.run,
                    moth != null && state.drainStart - remaining >= DRAIN_PER_SECOND,
                    "riftmoth drains adjacent canister",
                    "stored=${state.drainStart}, remaining=$remaining",
                )
                beginLiveChecks(state)
            }
        }
    }

    private fun beginLiveChecks(state: State) {
        val spawnPosition = Vector3d(FLOOR_MIN_X + 0.5, MOB_AREA.y.toDouble(), MOB_AREA.z + 0.5)
        if (logSectionState(state, "Craftory role spawns", spawnPosition)) {
            state.run.context.sendMessage(
                Message.raw("selftest: SKIP mob live checks (NonTicking: needs a nearby player)"),
            )
            finishMobs(state)
            return
        }
        spawnRoles(state)
        pollSpawns(state)
    }

    private fun logSectionState(
        state: State,
        phase: String,
        position: Vector3d,
    ): Boolean {
        val world = state.run.world
        val blockX = position.x.toInt()
        val blockY = position.y.toInt()
        val blockZ = position.z.toInt()
        val sectionCoordinates =
            Triple(
                ChunkUtil.chunkCoordinate(blockX),
                ChunkUtil.chunkCoordinate(blockY),
                ChunkUtil.chunkCoordinate(blockZ),
            )
        val mobSectionCoordinates =
            Triple(
                ChunkUtil.chunkCoordinate(MOB_AREA.x),
                ChunkUtil.chunkCoordinate(MOB_AREA.y),
                ChunkUtil.chunkCoordinate(MOB_AREA.z),
            )
        val sectionRef =
            world.chunkStore
                .getChunkSectionReferenceAtBlock(blockX, blockY, blockZ)
                ?.takeIf { it.isValid }
                ?: if (sectionCoordinates == mobSectionCoordinates && state.sectionRef.isValid) {
                    state.sectionRef
                } else {
                    error("mob selftest section is unavailable at $blockX,$blockY,$blockZ")
                }
        val nonTicking =
            world.chunkStore.store.getComponent(sectionRef, ChunkStore.REGISTRY.getNonTickingComponentType()) != null
        state.run.context.sendMessage(
            Message.raw(
                "selftest: mob section state " +
                    "(phase=$phase, tick=${world.tick}, pos=${position.x},${position.y},${position.z}, " +
                    "section=$sectionCoordinates, sameAsMobArea=${sectionCoordinates == mobSectionCoordinates}, " +
                    "NonTicking=$nonTicking)",
            ),
        )
        return nonTicking
    }

    private fun stopMobCanisterProducer(state: State): Boolean {
        val world = state.run.world
        val store = world.chunkStore.store
        val producerRef =
            BlockModule.getBlockEntity(world, MOB_BURNER.x, MOB_BURNER.y, MOB_BURNER.z)
                ?: error("mob test burner is missing")
        val burner =
            store.getComponent(producerRef, BurnerState.type)
                ?: error("mob test burner state is missing")
        burner.apply {
            burnTicks = 0
            burning = false
        }
        val producer =
            store.getComponent(producerRef, EnergyMember.type)
                ?: error("mob test producer energy is missing")
        producer.apply {
            production = 0L
            productionRank = 0
            unsaved = true
        }
        store.getComponent(producerRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
        return !burner.burning && burner.burnTicks == 0L && producer.production == 0L
    }

    private fun finishMobs(state: State) {
        cleanup(state)
        afterTicks(state.run.world, POLL_INTERVAL) {
            val mainCleanup =
                runCatching {
                    CraftorySelfTest.cleanup(state.run.world, state.run.sectionRef)
                }
            mainCleanup
                .onSuccess { result ->
                    state.run.context.sendMessage(
                        Message.raw(
                            "selftest: late main fixture cleanup " +
                                "(liveGrit=${result.liveDrops}, sectionHolders=${result.sectionHolders}, " +
                                "chunkHolders=${result.chunkHolders})",
                        ),
                    )
                }.onFailure { failure ->
                    check(state.run, false, "late main fixture cleanup", failure.message ?: "cleanup failed")
                }
            val count = state.refs.count { it.isValid }
            val worldMobCount = countMobs(state.run.world)
            check(state.run, count == 0, "mob cleanup", "mobs=$count, worldMobs=$worldMobCount")
            finish(state.run)
        }
    }

    private fun cleanup(state: State) {
        state.subscription?.close()
        state.subscription = null
        val entityStore = state.run.world.entityStore.store
        val refs = state.refs.toSet()
        entityStore.forEachChunk(
            Query.and(MobHome.type),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    val ref = chunk.getReferenceTo(index)
                    if (ref in refs) commandBuffer.removeEntity(ref, RemoveReason.REMOVE)
                }
            },
        )
        for (position in state.floorCells) {
            BlockOperations.setBlock(
                state.run.world.chunkStore,
                state.sectionRef,
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
        state.floorCells.clear()
        CraftorySelfTest.clear(
            state.run.world,
            state.sectionRef,
            listOf(
                MOB_ANCHOR,
                MOB_BURNER,
                CraftorySelfTest.TestPosition(MOB_BURNER.x, MOB_BURNER.y + 1, MOB_BURNER.z),
                MOB_PIPE,
                MOB_CANISTER,
            ),
        )
    }

    private fun countMobs(world: com.hypixel.hytale.server.core.universe.world.World): Int {
        var count = 0
        world.entityStore.store.forEachChunk(
            Query.and(MobHome.type),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ -> count += chunk.size() },
        )
        return count
    }

    private fun finish(run: SelfTestRun.Run) {
        CraftorySelfTest.finishAfterMobTest(run)
    }

    private fun check(
        run: SelfTestRun.Run,
        passed: Boolean,
        name: String,
        detail: String = "",
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} $name ($detail)"))
    }

    private fun afterTicks(
        world: com.hypixel.hytale.server.core.universe.world.World,
        ticks: Long,
        action: () -> Unit,
    ) {
        world.scheduleAfter({ action() }, ticks * 50, TimeUnit.MILLISECONDS)
    }

    private const val ROLE_COUNT = 4
    private const val MAX_POLLS = 20
    private const val POLL_INTERVAL = 5L
    private const val DRAIN_TEST_TICKS = 60L
    private const val DRAIN_PER_SECOND = 20L
    private const val NETWORK_SETTLE_TICKS = 25L
    private const val DRAIN_AREA_CHARGE_SECONDS = 60L
    private const val FLOOR_Y = 149
    private const val FLOOR_MIN_X = 10_004
    private const val FLOOR_MAX_X = 10_008
    private const val FLOOR_MIN_Z = 10_008
    private const val FLOOR_MAX_Z = 10_012
    private val MOB_AREA = BlockPos(10_006, 150, 10_010)
    private val HOME = BlockPos(10_006, FLOOR_Y, 10_010)
    private val MOB_ANCHOR = CraftorySelfTest.TestPosition(10_004, 150, 10_012)
    private val MOB_BURNER = CraftorySelfTest.TestPosition(10_008, 150, 10_012)
    private val MOB_PIPE = CraftorySelfTest.TestPosition(10_008, 152, 10_012)
    private val MOB_CANISTER = CraftorySelfTest.TestPosition(10_008, 153, 10_012)
    private val ROLE_IDS =
        listOf(
            CraftoryIds.GLIMMER_WISP,
            CraftoryIds.SPARKJAW,
            CraftoryIds.RIFTMOTH,
            CraftoryIds.GOBLIN_POACHER,
        )
}
