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
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.core.api.transport.FluidHandler
import studio.craftory.core.api.transport.FluidTank
import studio.craftory.core.api.transport.FluidTransport
import studio.craftory.tech.CraftoryIds

private val LAVA_IDS = setOf("Lava_Source", "Lava")
private const val TICKS_PER_SECOND = 20L

internal class GutterBoilerSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            BoilerState.type,
            MultiblockController.type,
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
        val state = chunk.getComponent(index, BoilerState.type) ?: return
        val controller = chunk.getComponent(index, MultiblockController.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            clearBoilerProduction(member)
            return
        }
        if (controller.state != MultiblockState.FORMED) {
            clearBoilerProduction(member)
            if (setBlockState(store.externalData.world, blockState, commandBuffer, "Off")) {
                blockState.markNeedsSaving(commandBuffer)
            }
            return
        }
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
    val world = store.externalData.world
    if (!CraftoryCore.isAreaActive(store, blockState)) {
        clearBoilerProduction(member)
        return
    }
    if (!CraftoryCore.isEnergyTick(store)) return
    val position = blockState.worldPosition(commandBuffer)
    if (position == null) {
        clearBoilerProduction(member)
        return
    }
    val lavaBelow = hasFluid(store, position.x, position.y - 1, position.z, LAVA_IDS)
    if (world.tick % TICKS_PER_SECOND == 0L) {
        updateBoilerState(
            state,
            blockState,
            commandBuffer,
            world,
            position.x,
            position.y,
            position.z,
            lavaBelow,
        )
    }
    member.production =
        GutterBoilerRules.production(
            state.pressure,
            state.water,
            state.fuelTicks > 0,
            lavaBelow,
            state.online,
        )
    member.productionRank = if (member.production > 0) GutterBoilerSystem.PRESSURISED_RANK else 0
}

private fun updateBoilerState(
    state: BoilerState,
    blockState: BlockModule.BlockStateInfo,
    commandBuffer: CommandBuffer<ChunkStore>,
    world: com.hypixel.hytale.server.core.universe.world.World,
    x: Int,
    y: Int,
    z: Int,
    lavaBelow: Boolean,
) {
    val beforePressure = state.pressure
    val beforeWater = state.water
    val beforeFuelTicks = state.fuelTicks
    val beforeOnline = state.online
    val beforeLastWhistleTick = state.lastWhistleTick
    val update = GutterBoilerActions.advance(state, lavaBelow)
    if (state.online &&
        state.pressure > HIGH_PRESSURE_THRESHOLD &&
        GutterBoilerRules.isWhistleDue(world.tick, state.lastWhistleTick)
    ) {
        state.lastWhistleTick = world.tick
        GutterBoilerEffects.whistle(world, x, y, z)
    }
    val stateChanged =
        hasBoilerStateChanged(beforePressure, beforeWater, beforeFuelTicks, beforeOnline, beforeLastWhistleTick, state)
    val visualChanged = setBlockState(world, blockState, commandBuffer, boilerVisualState(state, lavaBelow))
    if (stateChanged || visualChanged) blockState.markNeedsSaving(commandBuffer)
    if (update.popped) GutterBoilerEffects.pop(world, x, y, z)
}

private fun hasBoilerStateChanged(
    pressure: Int,
    water: Long,
    fuelTicks: Long,
    online: Boolean,
    lastWhistleTick: Long,
    state: BoilerState,
): Boolean {
    if (pressure != state.pressure) return true
    if (water != state.water) return true
    if (fuelTicks != state.fuelTicks) return true
    if (online != state.online) return true
    return lastWhistleTick != state.lastWhistleTick
}

private const val HIGH_PRESSURE_THRESHOLD = 95

private fun boilerVisualState(
    state: BoilerState,
    lavaBelow: Boolean,
): String =
    when {
        !state.online -> "Offline"
        hasBoilerPressureAndHeat(state, lavaBelow) -> "On"
        else -> "Off"
    }

private fun hasBoilerPressureAndHeat(
    state: BoilerState,
    lavaBelow: Boolean,
): Boolean {
    val heatAvailable = state.fuelTicks > 0 || lavaBelow
    return state.pressure > 0 && state.water > 0 && heatAvailable
}

private fun clearBoilerProduction(member: EnergyMember) {
    member.production = 0
    member.productionRank = 0
}

private fun hasFluid(
    store: Store<ChunkStore>,
    x: Int,
    y: Int,
    z: Int,
    ids: Set<String>,
): Boolean {
    val sectionRef =
        store.externalData.world.chunkStore
            .getChunkSectionReferenceAtBlock(x, y, z) ?: return false
    val section = store.getComponent(sectionRef, FluidSection.getComponentType()) ?: return false
    return section.getFluid(x and 31, y and 31, z and 31)?.id in ids
}

internal class SloshtankStateSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(FluidTank.type, BlockModule.BlockStateInfo.getComponentType())

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
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        if (!CraftoryCore.isAreaActive(store, blockState) ||
            !CraftoryCore.isEnergyTick(store) ||
            world.tick % TICKS_PER_SECOND != 0L
        ) {
            return
        }
        val position = blockState.worldPosition(commandBuffer) ?: return
        val fluid = FluidTransport.of(world)
        val capacity = fluid.capacity(position.x, position.y, position.z)
        val amount = fluid.amount(position.x, position.y, position.z)
        val state = FillBand.stateName(FillBand.of(amount, capacity))
        if (setBlockState(world, blockState, commandBuffer, state)) blockState.markNeedsSaving(commandBuffer)
    }
}

internal class CanisterStateSystem : EntityTickingSystem<ChunkStore>() {
    private val position = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.AFTER, EnergySolveSystem::class.java))

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
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState) || !blockState.fillWorldPos(commandBuffer, position)) return
        val world = store.externalData.world
        if ((world.tick + position.x + position.z) % STATE_UPDATE_INTERVAL_TICKS != 0L) return

        val section = commandBuffer.getComponent(blockState.sectionRef, BlockSection.getComponentType()) ?: return
        val blockType = BlockType.getAssetMap().getAsset(section.get(position.x, position.y, position.z)) ?: return
        if (baseBlockId(blockType) != CraftoryIds.CANISTER) return

        val positionId = BlockPos.pack(position.x, position.y, position.z)
        val energy = CraftoryCore.energyAccess(world)
        val state = FillBand.stateName(FillBand.of(energy.stored(positionId), energy.capacity(positionId)))
        if (setBlockState(world, blockState, commandBuffer, state)) blockState.markNeedsSaving(commandBuffer)
    }
}

private const val STATE_UPDATE_INTERVAL_TICKS = 10L

internal fun setBlockState(
    world: com.hypixel.hytale.server.core.universe.world.World,
    blockState: BlockModule.BlockStateInfo,
    commandBuffer: CommandBuffer<ChunkStore>,
    state: String,
): Boolean {
    val position = Vector3i()
    if (!blockState.fillWorldPos(commandBuffer, position)) return false
    val sectionRef = blockState.sectionRef
    val section = commandBuffer.getComponent(sectionRef, BlockSection.getComponentType()) ?: return false
    val blockType = BlockType.getAssetMap().getAsset(section.get(position.x, position.y, position.z)) ?: return false
    if (blockType.getState() == null || blockType.getStateForBlock(blockType) == state) return false
    if (blockType.getBlockForState(state) == null) return false
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
    return true
}

internal class GasbagSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            GasbagState.type,
            MultiblockController.type,
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

    @Suppress("CyclomaticComplexMethod")
    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, GasbagState.type) ?: return
        val controller = chunk.getComponent(index, MultiblockController.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState)) return
        if (controller.state != MultiblockState.FORMED) {
            member.production = 0
            if (setBlockState(store.externalData.world, blockState, commandBuffer, "Off")) {
                blockState.markNeedsSaving(commandBuffer)
            }
            return
        }
        if (!CraftoryCore.isEnergyTick(store) ||
            store.externalData.world.tick % TICKS_PER_SECOND != 0L
        ) {
            return
        }
        val position = blockState.worldPosition(commandBuffer) ?: return
        if (store.externalData.world.tick % STATE_UPDATE_INTERVAL_TICKS == 0L) {
            val positionId = BlockPos.pack(position.x, position.y, position.z)
            val energy = CraftoryCore.energyAccess(store.externalData.world)
            val visualState = FillBand.stateName(FillBand.of(energy.stored(positionId), energy.capacity(positionId)))
            if (setBlockState(store.externalData.world, blockState, commandBuffer, visualState)) {
                blockState.markNeedsSaving(commandBuffer)
            }
        }
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
        val fluidId = section.getFluidId(x and 31, y and 31, z and 31)
        if (fluidId < 0) return false
        val fluid = Fluid.getAssetMap().getAsset(fluidId) ?: return false
        return fluid.ticker is FireFluidTicker
    }

    companion object {
        const val CAPACITY = 400_000L
        private const val STATE_UPDATE_INTERVAL_TICKS = 10L
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
            GutterBoilerActions.acceptWater(state, fluid, amount)
        },
    )
}
