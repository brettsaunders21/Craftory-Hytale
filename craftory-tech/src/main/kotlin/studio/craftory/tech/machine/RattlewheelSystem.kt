package studio.craftory.tech.machine

import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.jank.BodgeSlots
import studio.craftory.tech.machine.jank.Jank
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

internal class RattlewheelSystem(
    private val directSpawnForSelfTest: Boolean = false,
) : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            RattlewheelState.type,
            Jank.type,
            BodgeSlots.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

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
        val state = chunk.getComponent(index, RattlewheelState.type) ?: return
        val jank = chunk.getComponent(index, Jank.type) ?: return
        val slots = chunk.getComponent(index, BodgeSlots.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        val tick = world.tick
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            member.production = 0
            return
        }
        if (!CraftoryCore.isEnergyTick(store)) return
        sampleIfDue(world, blockRef, state, blockState, commandBuffer, tick)
            ?.let { spawnRattlewheelGaleParticle(world, state, it) }
        updateOutput(state, slots, blockState, commandBuffer)
        member.production =
            if (jank.isStalled) {
                0
            } else {
                rattlewheelProductionPerSolveTick(state.fizzPerSecond, tick)
            }
        member.persist(blockState, commandBuffer, tick, SAVE_INTERVAL_TICKS, immediate = false)
        updateVisual(world, state, blockState, commandBuffer, member, tick)
    }

    private fun sampleIfDue(
        world: World,
        blockRef: Ref<ChunkStore>,
        state: RattlewheelState,
        blockState: BlockModule.BlockStateInfo,
        commandBuffer: CommandBuffer<ChunkStore>,
        tick: Long,
    ): Vector3i? {
        if (state.lastSampleTick != Long.MIN_VALUE && tick - state.lastSampleTick < SAMPLE_INTERVAL_TICKS) return null
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return null
        sample(world, blockRef, position, state)
        state.lastSampleTick = tick
        blockState.markNeedsSaving(commandBuffer)
        return position
    }

    private fun updateOutput(
        state: RattlewheelState,
        slots: BodgeSlots,
        blockState: BlockModule.BlockStateInfo,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val output =
            rattlewheelOutput(
                state.waterFlow,
                state.windMode,
                state.y,
                state.weatherMultiplier,
                slots.modifiers().speed,
            )
        if (state.fizzPerSecond == output) return
        state.fizzPerSecond = output
        blockState.markNeedsSaving(commandBuffer)
    }

    private fun updateVisual(
        world: World,
        state: RattlewheelState,
        blockState: BlockModule.BlockStateInfo,
        commandBuffer: CommandBuffer<ChunkStore>,
        member: EnergyMember,
        tick: Long,
    ) {
        val on = state.fizzPerSecond > 0
        if (state.visualOn == on) return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        state.visualOn = on
        world.execute {
            BlockOperations.setBlockInteractionState(
                world.chunkStore,
                blockState.sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.fromString(CraftoryIds.RATTLEWHEEL) ?: return@execute,
                visualState(state.fizzPerSecond),
                true,
            )
        }
        member.persist(blockState, commandBuffer, tick, SAVE_INTERVAL_TICKS, immediate = true)
    }

    internal fun sample(
        world: World,
        blockRef: Ref<ChunkStore>,
        position: Vector3i,
        state: RattlewheelState,
    ) {
        val flow =
            WATER_NEIGHBOURS.count { offset ->
                isWater(world, position.x + offset[0], position.y + offset[1], position.z + offset[2])
            }
        state.waterFlow = flow
        state.y = position.y
        state.windMode = flow == 0 && position.y >= WIND_MIN_Y
        state.weatherMultiplier = selfTestWeatherMultipliers[blockRef] ?: weatherMultiplier(world, position)
    }

    private fun isWater(
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(x, y, z) ?: return false
        val section =
            world.chunkStore.store.getComponent(sectionRef, FluidSection.getComponentType()) ?: return false
        return section.getFluid(x and 31, y and 31, z and 31)?.id == WATER_ID
    }

    private fun weatherMultiplier(
        world: World,
        position: Vector3i,
    ): Double {
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z) ?: return 1.0
        val environment =
            world.chunkStore.store.getComponent(sectionRef, EnvironmentSection.getComponentType())?.get(
                position.x and 31,
                position.y and 31,
                position.z and 31,
            ) ?: return 1.0
        val resource = world.entityStore.store.getResource(WeatherResource.getResourceType())
        val weatherId =
            Weather
                .getAssetMap()
                .getAsset(resource.getEffectiveWeatherIndex(environment))
                ?.id
                ?.lowercase()
                .orEmpty()
        return when {
            "storm" in weatherId -> 2.0
            "rain" in weatherId -> 1.5
            else -> 1.0
        }
    }

    companion object {
        private const val WATER_ID = "Water"
        private const val WIND_MIN_Y = 110
        private const val SAMPLE_INTERVAL_TICKS = 100L
        private const val SAVE_INTERVAL_TICKS = 20L
        private val WATER_NEIGHBOURS =
            arrayOf(
                intArrayOf(1, 0, 0),
                intArrayOf(-1, 0, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 0, -1),
                intArrayOf(0, -1, 0),
            )
        private val selfTestBlocks: MutableSet<Ref<ChunkStore>> = ConcurrentHashMap.newKeySet()
        private val selfTestWeatherMultipliers: MutableMap<Ref<ChunkStore>, Double> = ConcurrentHashMap()

        internal fun registerSelfTestBlock(ref: Ref<ChunkStore>) {
            selfTestBlocks.add(ref)
        }

        internal fun forceWeatherMultiplierForSelfTest(
            ref: Ref<ChunkStore>,
            multiplier: Double,
        ) {
            selfTestWeatherMultipliers[ref] = multiplier
        }

        internal fun releaseSelfTestBlock(ref: Ref<ChunkStore>) {
            selfTestBlocks.remove(ref)
            selfTestWeatherMultipliers.remove(ref)
        }

        internal fun releaseSelfTestBlocks() {
            selfTestBlocks.clear()
            selfTestWeatherMultipliers.clear()
        }

        internal fun visualState(output: Long): String = if (output > 0) "On" else "Off"
    }
}

private fun spawnRattlewheelGaleParticle(
    world: World,
    state: RattlewheelState,
    position: Vector3i,
) {
    if (!state.windMode || state.weatherMultiplier != 2.0 || ThreadLocalRandom.current().nextInt(5) != 0) return
    world.execute {
        BlockOperations.spawnBlockParticles(
            world.chunkStore,
            BlockType.getAssetMap().getIndex("Wood_Softwood_Planks"),
            BlockType.EMPTY_ID,
            position.x,
            position.y,
            position.z,
            false,
        )
    }
}
