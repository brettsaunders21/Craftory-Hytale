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
import com.hypixel.hytale.server.core.asset.type.fluid.FireFluidTicker
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.FluidHandler
import studio.craftory.core.api.transport.FluidTransport

private val LAVA_IDS =
    intArrayOf(
        Fluid.getAssetMap().getIndex("Lava_Source"),
        Fluid.getAssetMap().getIndex("Lava"),
    )

internal class GutterBoilerSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(BoilerState.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

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
        val state = chunk.getComponent(index, BoilerState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        advanceBoiler(state, member, blockState, store, commandBuffer)
    }

    companion object {
        const val PRESSURISED_RANK = 1
    }
}

private fun advanceBoiler(
    state: BoilerState,
    member: EnergyMember,
    blockState: BlockModule.BlockStateInfo,
    store: Store<ChunkStore>,
    commandBuffer: CommandBuffer<ChunkStore>,
) {
    if (!CraftoryCore.isAreaActive(store, blockState) || !CraftoryCore.isEnergyTick(store)) {
        member.production = 0
        return
    }
    val position = blockState.worldPosition(commandBuffer) ?: return
    val lavaBelow =
        hasFluid(
            store,
            position.x,
            position.y - 1,
            position.z,
            LAVA_IDS,
        )
    val update =
        GutterBoilerRules.step(
            state.pressure,
            state.water,
            state.fuelTicks > 0,
            lavaBelow,
            state.online,
        )
    applyBoilerStep(state, update, lavaBelow)
    member.production = update.production
    member.productionRank = GutterBoilerSystem.PRESSURISED_RANK
    val visualState =
        when {
            !state.online -> "Offline"
            update.production > 0 -> "On"
            else -> "Off"
        }
    setBlockState(store.externalData.world, blockState, commandBuffer, visualState)
    blockState.markNeedsSaving(commandBuffer)
}

private fun applyBoilerStep(
    state: BoilerState,
    update: BoilerStep,
    lavaBelow: Boolean,
) {
    state.pressure = update.pressure
    state.water = update.water
    if (update.popped) {
        state.online = false
        state.fuelTicks = 0
    } else {
        consumeBoilerFuel(state, lavaBelow)
    }
}

private fun consumeBoilerFuel(
    state: BoilerState,
    lavaBelow: Boolean,
) {
    if (state.fuelTicks > 0 && !lavaBelow) {
        state.fuelTicks = (state.fuelTicks - 20L).coerceAtLeast(0)
    }
}

private fun hasFluid(
    store: Store<ChunkStore>,
    x: Int,
    y: Int,
    z: Int,
    ids: IntArray,
): Boolean {
    val sectionRef =
        store.externalData.world.chunkStore
            .getChunkSectionReferenceAtBlock(x, y, z) ?: return false
    val section = store.getComponent(sectionRef, FluidSection.getComponentType()) ?: return false
    return section.getFluidId(x, y, z) in ids
}

private fun setBlockState(
    world: com.hypixel.hytale.server.core.universe.world.World,
    blockState: BlockModule.BlockStateInfo,
    commandBuffer: CommandBuffer<ChunkStore>,
    state: String,
) {
    val position = Vector3i()
    if (!blockState.fillWorldPos(commandBuffer, position)) return
    val sectionRef = blockState.sectionRef
    val section = commandBuffer.getComponent(sectionRef, BlockSection.getComponentType()) ?: return
    val blockType = BlockType.getAssetMap().getAsset(section.get(position.x, position.y, position.z)) ?: return
    if (blockType.getState() == null || blockType.getStateForBlock(blockType) == state) return
    if (blockType.getBlockForState(state) == null) return
    BlockOperations.setBlockInteractionState(
        world.chunkStore,
        sectionRef,
        position.x,
        position.y,
        position.z,
        blockType,
        state,
        false,
    )
}

internal class GasbagSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(GasbagState.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

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
        val state = chunk.getComponent(index, GasbagState.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState) || !CraftoryCore.isEnergyTick(store)) return
        val position = blockState.worldPosition(commandBuffer) ?: return
        if (!state.leaking && fireAdjacent(store, position.x, position.y, position.z)) {
            state.leaking = true
            blockState.markNeedsSaving(commandBuffer)
        }
        if (state.leaking) {
            val pos = BlockPos.pack(position.x, position.y, position.z)
            val amount = GasbagRules.leakAmount(CAPACITY)
            CraftoryCore.energyAccess(store.externalData.world).drainStored(pos, amount)
        }
    }

    private fun fireAdjacent(
        store: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        for (face in FACES) {
            if (isFireAt(store, x + face[0], y + face[1], z + face[2])) return true
        }
        return false
    }

    private fun isFireAt(
        store: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val sectionRef =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(x, y, z) ?: return false
        val section = store.getComponent(sectionRef, FluidSection.getComponentType()) ?: return false
        val fluid = Fluid.getAssetMap().getAsset(section.getFluidId(x, y, z)) ?: return false
        return fluid.ticker is FireFluidTicker
    }

    companion object {
        const val CAPACITY = 400_000L
        private val FACES =
            arrayOf(
                intArrayOf(1, 0, 0),
                intArrayOf(-1, 0, 0),
                intArrayOf(0, 1, 0),
                intArrayOf(0, -1, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 0, -1),
            )
    }
}

internal fun registerBoilerFluidHandler() {
    FluidTransport.registerHandler(
        BoilerState.type,
        FluidHandler { state, fluid, amount ->
            if (fluid != "Water_Source" && fluid != "Water") return@FluidHandler 0
            val accepted = minOf(amount, GutterBoilerRules.CAPACITY - state.water)
            state.water += accepted
            accepted
        },
    )
}
