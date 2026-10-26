package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.physics.component.Velocity
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.machine.jank.BodgeSlots
import studio.craftory.tech.machine.jank.Jank
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

internal class ShroomBedSystem(
    private val directSpawnForSelfTest: Boolean = false,
) : EntityTickingSystem<ChunkStore>() {
    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            ShroomBedState.type,
            Jank.type,
            BodgeSlots.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun tick(
        dt: Float,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        super.tick(dt, chunk, store, commandBuffer)
    }

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val blockRef = chunk.getReferenceTo(index)
        if (!directSpawnForSelfTest && blockRef in selfTestBlocks) return
        val state = chunk.getComponent(index, ShroomBedState.type) ?: return
        val jank = chunk.getComponent(index, Jank.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        val tick = world.tick
        if (state.lastUpdateTick != Long.MIN_VALUE && tick - state.lastUpdateTick < UPDATE_INTERVAL_TICKS) return
        updateBed(world, blockRef, state, jank, blockState, store, commandBuffer, tick)
    }

    private fun updateBed(
        world: World,
        blockRef: Ref<ChunkStore>,
        state: ShroomBedState,
        jank: Jank,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
        tick: Long,
    ) {
        state.lastUpdateTick = tick
        if (!CraftoryCore.isAreaActive(store, blockState) || jank.isStalled) {
            blockState.markNeedsSaving(commandBuffer)
            return
        }
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val density = density(store, position)
        val dark = forcedDarkness[blockRef] ?: isDark(store, blockState)
        val playerWithin2 = playerWithin(world, position, 2.0)
        val lightOrDay = !dark
        val forcePop = blockRef in forcedPops
        if (forcePop) state.twitch = 100
        val shouldPopNow =
            advanceState(
                state,
                dark,
                density,
                lightOrDay,
                playerWithin2,
                if (forcePop) 0.0 else ThreadLocalRandom.current().nextDouble(),
            )
        if (forcePop) forcedPops.remove(blockRef)
        if (shouldPopNow) {
            pop(world, commandBuffer, state, blockState, position)
        }
        blockState.markNeedsSaving(commandBuffer)
    }

    internal fun isDark(
        store: Store<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
    ): Boolean {
        val lightLevels = lightLevels(store, blockState) ?: return false
        return lightLevels.first < DARK_LIGHT_THRESHOLD && lightLevels.second < DARK_LIGHT_THRESHOLD
    }

    internal fun lightLevels(
        store: Store<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
    ): Pair<Int, Int>? {
        val section = store.getComponent(blockState.sectionRef, BlockSection.getComponentType()) ?: return null
        val light =
            when {
                section.hasLocalLight() -> section.localLight
                section.hasGlobalLight() -> section.globalLight
                else -> return null
            }
        return light.getSkyLight(blockState.index).toInt() to
            light.getBlockLightIntensity(blockState.index).toInt()
    }

    private fun density(
        store: Store<ChunkStore>,
        position: Vector3i,
    ): Int =
        BED_NEIGHBOURS.count { offset ->
            val ref =
                BlockModule.getBlockEntity(
                    store.externalData.world,
                    position.x + offset[0],
                    position.y,
                    position.z + offset[1],
                )
            ref != null && store.getComponent(ref, ShroomBedState.type) != null
        }

    private fun playerWithin(
        world: World,
        position: Vector3i,
        radius: Double,
    ): Boolean =
        Universe.get().getPlayers().any { player ->
            player.worldUuid == world.worldConfig.uuid &&
                player.transform.position.distanceSquared(
                    position.x + 0.5,
                    position.y + 0.5,
                    position.z + 0.5,
                ) <= radius * radius
        }

    private fun pop(
        world: World,
        commandBuffer: CommandBuffer<ChunkStore>,
        state: ShroomBedState,
        blockState: BlockModule.BlockStateInfo,
        position: Vector3i,
    ) {
        state.stored = 0
        state.twitch = 0
        ParticleUtil.spawnParticleEffect(
            "Impact_Spark",
            Vector3d(position.x + 0.5, position.y + 0.8, position.z + 0.5),
            world.entityStore.store,
        )
        applyKnockback(world, position)
        BED_NEIGHBOURS.forEach { offset ->
            val adjacent =
                BlockModule.getBlockEntity(
                    world,
                    position.x + offset[0],
                    position.y,
                    position.z + offset[1],
                ) ?: return@forEach
            val neighbour = commandBuffer.getComponent(adjacent, ShroomBedState.type) ?: return@forEach
            applyChainTrigger(neighbour)
            commandBuffer
                .getComponent(adjacent, BlockModule.BlockStateInfo.getComponentType())
                ?.markNeedsSaving(commandBuffer)
        }
        blockState.markNeedsSaving(commandBuffer)
    }

    private fun applyKnockback(
        world: World,
        position: Vector3i,
    ) {
        val entities = world.entityStore.store
        Universe.get().getPlayers().forEach { player ->
            if (player.worldUuid != world.worldConfig.uuid) return@forEach
            val playerPosition = player.transform.position
            val dx = playerPosition.x - (position.x + 0.5)
            val dz = playerPosition.z - (position.z + 0.5)
            val distanceSquared = dx * dx + dz * dz
            val dy = playerPosition.y - (position.y + 0.5)
            if (distanceSquared + dy * dy > POP_RADIUS * POP_RADIUS) return@forEach
            val entity = player.reference ?: return@forEach
            val velocity = entities.getComponent(entity, Velocity.getComponentType()) ?: return@forEach
            val inverseLength = 1.0 / kotlin.math.sqrt(distanceSquared).coerceAtLeast(0.1)
            velocity.addVelocity(
                dx * inverseLength * KNOCKBACK_STRENGTH,
                KNOCKBACK_UPWARD,
                dz * inverseLength * KNOCKBACK_STRENGTH,
            )
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_TICKS = 20L
        private const val DARK_LIGHT_THRESHOLD = 4
        private const val MAX_GROWTH = 1000.0
        private const val MAX_STORED = 9
        private const val CHAIN_TWITCH = 80
        private const val POP_RADIUS = 3.0
        private const val KNOCKBACK_STRENGTH = 0.75
        private const val KNOCKBACK_UPWARD = 0.3
        private val BED_NEIGHBOURS =
            arrayOf(
                intArrayOf(-1, -1),
                intArrayOf(-1, 0),
                intArrayOf(-1, 1),
                intArrayOf(0, -1),
                intArrayOf(0, 1),
                intArrayOf(1, -1),
                intArrayOf(1, 0),
                intArrayOf(1, 1),
            )
        private val selfTestBlocks: MutableSet<Ref<ChunkStore>> = ConcurrentHashMap.newKeySet()
        private val forcedDarkness: MutableMap<Ref<ChunkStore>, Boolean> = ConcurrentHashMap()
        private val forcedPops: MutableSet<Ref<ChunkStore>> = ConcurrentHashMap.newKeySet()

        internal fun advanceState(
            state: ShroomBedState,
            dark: Boolean,
            density: Int,
            lightOrDay: Boolean,
            playerWithin2Blocks: Boolean,
            roll: Double,
        ): Boolean {
            state.twitch =
                (state.twitch + twitchDelta(lightOrDay, playerWithin2Blocks, density))
                    .coerceIn(0, 100)
            if (state.stored < MAX_STORED) {
                state.growth =
                    (state.growth + shroomGrowth(dark, density))
                        .coerceAtMost(MAX_GROWTH)
                if (state.growth >= MAX_GROWTH) {
                    state.stored = (state.stored + 1).coerceAtMost(MAX_STORED)
                    state.growth = 0.0
                }
            }
            return shouldPop(state.twitch, roll)
        }

        internal fun applyChainTrigger(state: ShroomBedState) {
            state.twitch = maxOf(state.twitch, CHAIN_TWITCH)
        }

        internal fun applyPopForSelfTest(
            state: ShroomBedState,
            neighbours: Iterable<ShroomBedState>,
        ) {
            state.stored = 0
            state.twitch = 0
            neighbours.forEach(::applyChainTrigger)
        }

        internal fun registerSelfTestBlock(ref: Ref<ChunkStore>) {
            selfTestBlocks.add(ref)
        }

        internal fun forceDarknessForSelfTest(
            ref: Ref<ChunkStore>,
            dark: Boolean,
        ) {
            forcedDarkness[ref] = dark
        }

        internal fun forcePopForSelfTest(ref: Ref<ChunkStore>) {
            forcedPops.add(ref)
        }

        internal fun advanceForSelfTest(
            ref: Ref<ChunkStore>,
            state: ShroomBedState,
            dark: Boolean,
            density: Int,
            lightOrDay: Boolean,
            playerWithin2Blocks: Boolean,
            roll: Double,
        ): Boolean =
            advanceState(
                state,
                forcedDarkness[ref] ?: dark,
                density,
                lightOrDay,
                playerWithin2Blocks,
                roll,
            )

        internal fun popForSelfTest(
            ref: Ref<ChunkStore>,
            state: ShroomBedState,
            neighbours: Iterable<ShroomBedState>,
        ): Boolean {
            if (!forcedPops.remove(ref)) return false
            state.twitch = 100
            applyPopForSelfTest(state, neighbours)
            return true
        }

        internal fun releaseSelfTestBlock(ref: Ref<ChunkStore>) {
            selfTestBlocks.remove(ref)
            forcedDarkness.remove(ref)
            forcedPops.remove(ref)
        }

        internal fun releaseSelfTestBlocks() {
            selfTestBlocks.clear()
            forcedDarkness.clear()
            forcedPops.clear()
        }
    }
}
