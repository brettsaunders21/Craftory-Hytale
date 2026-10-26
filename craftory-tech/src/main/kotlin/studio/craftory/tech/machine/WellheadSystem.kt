package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.ConverterMath
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.WellSelfTestExclusions

internal object WellOutput {
    fun base(strength: WellStrength): Long =
        when (strength) {
            WellStrength.FLICKER -> 4
            WellStrength.STEADY -> 10
            WellStrength.ROARING -> 25
            WellStrength.PRIMAL -> 40
        }

    fun total(
        strength: WellStrength,
        harmony: Int,
        delivered: Long,
        corrupted: Boolean,
    ): Long {
        val output = base(strength) * harmony.coerceIn(0, 5) + ConverterMath.catalyse(delivered)
        return if (corrupted) output / 2 else output
    }
}

internal class WellheadSystem : EntityTickingSystem<ChunkStore>() {
    private val cursor = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(WellheadState.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (!CraftoryCore.isEnergyTick(store)) return
        val state = chunk.getComponent(index, WellheadState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        if (member.typeId != CraftoryIds.WELLHEAD_TYPE) return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!blockState.fillWorldPos(commandBuffer, cursor)) return
        val position = BlockPos(cursor.x, cursor.y, cursor.z)
        val world = store.externalData.world
        val energy = CraftoryCore.energyAccess(world)
        val id = position.pack()
        val wellPosition =
            state.wellPosition
                .takeIf { it != WellheadState.NO_WELL }
                ?.let { BlockPos.unpack(it) }
        val well =
            wellPosition
                ?.takeUnless { WellSelfTestExclusions.isWell(it) }
                ?.let {
                    CraftoryCore
                        .wells()
                        .wellsNear(store, it, 0)
                        .firstOrNull()
                        ?.well
                }?.takeIf { it.state == WellState.TAPPED }
        if (!CraftoryCore.isAreaActive(store, blockState) || well == null) {
            energy.setDemand(id, 0)
            energy.setConverterOutput(id, 0, DIM_GLIMMER_RANK)
            updatePumping(world, position, state, blockState, commandBuffer, false)
            return
        }
        refreshHarmony(store, state, blockState, commandBuffer, position, well)
        energy.setDemand(id, INPUT_DEMAND)
        val delivered = energy.converterInputDelivered(id)
        val output = WellOutput.total(well.strength, state.harmony, delivered, well.corrupted)
        energy.setConverterOutput(id, output, DIM_GLIMMER_RANK)
        updatePumping(world, position, state, blockState, commandBuffer, output > 0)
    }

    private fun refreshHarmony(
        store: Store<ChunkStore>,
        state: WellheadState,
        blockState: BlockModule.BlockStateInfo,
        commandBuffer: CommandBuffer<ChunkStore>,
        position: BlockPos,
        well: Glimmerwell,
    ) {
        val tick = store.externalData.world.tick
        if (state.lastHarmonyTick != Long.MIN_VALUE && tick - state.lastHarmonyTick < HARMONY_REFRESH_TICKS) return
        state.lastHarmonyTick = tick
        val harmony = CradleSystem.harmonyAt(store, position, well.taste)
        if (state.harmony == harmony) return
        state.harmony = harmony
        blockState.markNeedsSaving(commandBuffer)
    }

    private fun updatePumping(
        world: com.hypixel.hytale.server.core.universe.world.World,
        position: BlockPos,
        state: WellheadState,
        blockState: BlockModule.BlockStateInfo,
        commandBuffer: CommandBuffer<ChunkStore>,
        pumping: Boolean,
    ) {
        if (state.pumping == pumping) return
        state.pumping = pumping
        blockState.markNeedsSaving(commandBuffer)
        world.execute { setVisualState(world, position, if (pumping) "Pumping" else "Off") }
    }

    companion object {
        private const val INPUT_DEMAND = 40L
        private const val HARMONY_REFRESH_TICKS = 10L * 20L
        private val DIM_GLIMMER_RANK = requireNotNull(CraftoryCore.gradeRank("dim_glimmer"))

        private fun setVisualState(
            world: com.hypixel.hytale.server.core.universe.world.World,
            position: BlockPos,
            name: String,
        ) {
            val sectionRef =
                world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z) ?: return
            val store = world.chunkStore.store
            val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return
            val current =
                BlockType.getAssetMap().getAsset(section.get(position.x and 31, position.y and 31, position.z and 31))
                    ?: return
            if (current.getBlockForState(name) == null) return
            BlockOperations.setBlockInteractionState(
                world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                current,
                name,
                false,
            )
        }
    }
}
