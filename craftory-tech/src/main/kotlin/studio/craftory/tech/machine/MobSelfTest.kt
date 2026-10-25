package studio.craftory.tech.machine

import com.hypixel.hytale.codec.ExtraInfo
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.mob.CraftoryMobKill
import studio.craftory.core.api.mob.CraftoryMobs
import studio.craftory.core.api.mob.MobHome
import studio.craftory.tech.CraftoryIds
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
internal object MobSelfTest {
    internal class State(
        val run: SelfTestRun.Run,
        val sectionRef: Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>,
    ) {
        val refs = mutableListOf<Ref<EntityStore>>()
        var kill: CraftoryMobKill? = null
        var subscription: AutoCloseable? = null
        var spawnPolls = 0
        var killPolls = 0
        var expectedKilledRoleId: String? = null
        var drainStart = 0L
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
                        spawnRoles(state)
                        pollSpawns(state)
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

    private fun placeFloor(state: State) {
        val blockType = BlockType.fromString("Rock_Stone") ?: error("Rock_Stone block is unavailable")
        val blockIndex = BlockType.getAssetMap().getIndex(blockType.id)
        for (x in FLOOR_MIN_X..FLOOR_MAX_X) {
            for (z in FLOOR_MIN_Z..FLOOR_MAX_Z) {
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
            }
        }
    }

    private fun spawnRoles(state: State) {
        val world = state.run.world
        val roles =
            listOf(
                CraftoryIds.GLIMMER_WISP,
                CraftoryIds.SPARKJAW,
                CraftoryIds.RIFTMOTH,
                CraftoryIds.GOBLIN_POACHER,
            )
        roles.forEachIndexed { index, roleId ->
            val position =
                Vector3d(
                    FLOOR_MIN_X + 0.5 + index,
                    MOB_AREA.y.toDouble(),
                    MOB_AREA.z + 0.5,
                )
            CraftoryMobs.spawn(world, roleId, position, HOME)?.let(state.refs::add)
        }
    }

    private fun pollSpawns(state: State) {
        val allSpawned = state.refs.size == ROLE_COUNT && state.refs.all { it.isValid }
        if (allSpawned || state.spawnPolls >= MAX_POLLS) {
            val store = state.run.world.entityStore.store
            ROLE_IDS.forEach { roleId ->
                val ref = state.refs.firstOrNull { store.getComponent(it, MobHome.type)?.roleId == roleId }
                check(state.run, ref?.isValid == true, "mob spawn $roleId")
                val mobHome = ref?.let { store.getComponent(it, MobHome.type) }
                check(
                    state.run,
                    mobHome?.roleId == roleId && mobHome.home == HOME && mobHome.leashRadius == 12.0,
                    "mob home $roleId",
                )
            }
            verifyHomeCodec(state)
            beginKillTest(state)
            return
        }
        state.spawnPolls++
        afterTicks(state.run.world, POLL_INTERVAL) { pollSpawns(state) }
    }

    private fun verifyHomeCodec(state: State) {
        val store = state.run.world.entityStore.store
        val mobHome = state.refs.firstNotNullOfOrNull { store.getComponent(it, MobHome.type) }
        val decoded =
            mobHome?.let { component ->
                val extraInfo = ExtraInfo()
                MobHome.CODEC.decode(MobHome.CODEC.encode(component, extraInfo), ExtraInfo())
            }
        check(
            state.run,
            decoded == mobHome &&
                mobHome?.roleId == ROLE_IDS.first() &&
                mobHome?.home == HOME &&
                mobHome?.leashRadius == 12.0,
            "mob home codec round-trip",
        )
    }

    private fun beginKillTest(state: State) {
        state.subscription = CraftoryMobs.onCraftoryMobKilled { state.kill = it }
        val target =
            state.refs.firstOrNull() ?: run {
                check(state.run, false, "mob kill hook")
                beginDrainTest(state)
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
            beginDrainTest(state)
            return
        }
        state.killPolls++
        afterTicks(state.run.world, POLL_INTERVAL) { pollKill(state) }
    }

    private fun beginDrainTest(state: State) {
        val world = state.run.world
        val canisterPosition =
            BlockPos(CraftorySelfTest.CANISTER.x, CraftorySelfTest.CANISTER.y, CraftorySelfTest.CANISTER.z)
        CraftorySelfTest.detachCanisterProducers(world, state.run.sectionRef)
        afterTicks(world, POLL_INTERVAL) {
            val energy = CraftoryCore.energyAccess(world)
            state.drainStart = energy.stored(canisterPosition.pack())
            val supply = energy.networkSupply(canisterPosition.pack())
            check(
                state.run,
                state.drainStart > 0 && supply == 0L,
                "riftmoth drain canister isolated",
                "stored=${state.drainStart}, supply=$supply",
            )
            if (state.drainStart <= 0 || supply != 0L) {
                finishMobs(state)
                return@afterTicks
            }
            val moth =
                CraftoryMobs.spawn(
                    world,
                    CraftoryIds.RIFTMOTH,
                    Vector3d(canisterPosition.x + 1.5, canisterPosition.y + 1.0, canisterPosition.z + 0.5),
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
                finishMobs(state)
            }
        }
    }

    private fun finishMobs(state: State) {
        cleanup(state)
        afterTicks(state.run.world, POLL_INTERVAL) {
            val count = countMobs(state.run.world)
            check(state.run, count == 0, "mob cleanup", "mobs=$count")
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
        for (x in FLOOR_MIN_X..FLOOR_MAX_X) {
            for (z in FLOOR_MIN_Z..FLOOR_MAX_Z) {
                BlockOperations.setBlock(
                    state.run.world.chunkStore,
                    state.sectionRef,
                    x,
                    FLOOR_Y,
                    z,
                    BlockType.EMPTY_ID,
                    BlockType.EMPTY,
                    0,
                    0,
                    SetBlockSettings.NO_DROP_ITEMS,
                )
            }
        }
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
        run.context.sendMessage(Message.raw("selftest: ${run.passed} passed, ${run.failed} failed"))
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
    private const val FLOOR_Y = 149
    private const val FLOOR_MIN_X = 10_060
    private const val FLOOR_MAX_X = 10_064
    private const val FLOOR_MIN_Z = 10_000
    private const val FLOOR_MAX_Z = 10_004
    private val MOB_AREA = BlockPos(10_062, 150, 10_002)
    private val HOME = BlockPos(10_062, FLOOR_Y, 10_002)
    private val ROLE_IDS =
        listOf(
            CraftoryIds.GLIMMER_WISP,
            CraftoryIds.SPARKJAW,
            CraftoryIds.RIFTMOTH,
            CraftoryIds.GOBLIN_POACHER,
        )
}
