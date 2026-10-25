package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.CraftoryIds

internal class CrankSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(CrankState.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    // Machine state shares a per-world solver and must stay serialized.
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
        if (CraftoryCore.isEnergyTick(store)) super.tick(dt, chunk, store, commandBuffer)
    }

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, CrankState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val tick = store.externalData.world.tick
        if (CrankMachine.advanceTo(state, tick)) member.unsaved = true
        member.production = CrankMachine.productionPerSolveTick(state)
        member.persist(blockState, commandBuffer, tick, SAVE_INTERVAL_TICKS, immediate = false)
    }
}

internal class BurnerSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(BurnerState.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    // Machine state shares a per-world solver and must stay serialized.
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
        if (CraftoryCore.isEnergyTick(store)) super.tick(dt, chunk, store, commandBuffer)
    }

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, BurnerState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val tick = store.externalData.world.tick
        val world = store.externalData.world
        if (BurnerMachine.advanceTo(state, tick)) member.unsaved = true
        member.production = BurnerMachine.productionPerSolveTick(state)
        member.persist(blockState, commandBuffer, tick, SAVE_INTERVAL_TICKS, immediate = false)
        if (state.visualBurning != state.burning) {
            state.visualBurning = state.burning
            val position = Vector3i()
            if (blockState.fillWorldPos(commandBuffer, position)) {
                world.execute {
                    BlockOperations.setBlockInteractionState(
                        world.chunkStore,
                        blockState.sectionRef,
                        position.x,
                        position.y,
                        position.z,
                        BlockType.fromString(CraftoryIds.BURNER) ?: return@execute,
                        if (state.burning) "On" else "Off",
                        true,
                    )
                }
                member.persist(blockState, commandBuffer, tick, SAVE_INTERVAL_TICKS, immediate = true)
            }
        }
    }
}

internal class JunkPowerKickSystem : EntityEventSystem<EntityStore, DamageBlockEvent>(DamageBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: DamageBlockEvent,
    ) {
        if (event.blockType.id != CraftoryIds.BURNER) return
        val world = store.externalData.world
        val pos = event.targetBlock
        val ref = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z) ?: return
        val chunks = world.chunkStore.store
        val state = chunks.getComponent(ref, BurnerState.type) ?: return
        val member = chunks.getComponent(ref, EnergyMember.type) ?: return
        val blockState = chunks.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!MachineActions.kick(state, member, blockState, chunks, world.tick)) return
        event.isCancelled = true
    }
}

class CraftoryBlockUseInteraction : SimpleBlockInteraction() {
    override fun interactWithBlock(
        world: World,
        commandBuffer: CommandBuffer<EntityStore>,
        type: InteractionType,
        context: InteractionContext,
        heldItem: ItemStack?,
        position: Vector3i,
        cooldownHandler: CooldownHandler,
    ) {
        val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
        val store = world.chunkStore.store
        val member = store.getComponent(ref, EnergyMember.type) ?: return
        val blockState = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val tick = world.tick
        val readout =
            when {
                store.getComponent(ref, CrankState.type) != null -> {
                    val state = store.getComponent(ref, CrankState.type)!!
                    if (heldItem == null) {
                        MachineActions.pulse(state, member, blockState, store, tick)
                    }
                    machineReadout("Crank", member)
                }

                store.getComponent(ref, BurnerState.type) != null && heldItem != null -> {
                    val state = store.getComponent(ref, BurnerState.type)!!
                    if (MachineActions.addFuel(state, member, blockState, store, tick, heldItem.itemId) > 0) {
                        val container = context.heldItemContainer ?: return
                        val replacement =
                            if (heldItem.quantity == 1) {
                                null
                            } else {
                                heldItem.withQuantity(heldItem.quantity - 1)
                            }
                        container.setItemStackForSlot(context.heldItemSlot.toShort(), replacement)
                    }
                    null
                }

                store.getComponent(ref, BurnerState.type) != null -> {
                    machineReadout("Boomshroom Burner", member)
                }

                else -> {
                    machineReadout("Energy Member", member)
                }
            }
        if (readout != null) {
            commandBuffer.getComponent(context.entity, PlayerRef.getComponentType())?.sendMessage(Message.raw(readout))
        }
    }

    override fun simulateInteractWithBlock(
        type: InteractionType,
        context: InteractionContext,
        heldItem: ItemStack?,
        world: World,
        position: Vector3i,
    ) = Unit

    companion object {
        @JvmField
        val CODEC =
            com.hypixel.hytale.codec.builder.BuilderCodec
                .builder(
                    CraftoryBlockUseInteraction::class.java,
                    ::CraftoryBlockUseInteraction,
                    SimpleBlockInteraction.CODEC,
                ).build()
    }
}

internal object MachineActions {
    fun pulse(
        state: CrankState,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        tick: Long,
    ): Boolean {
        if (!CrankMachine.pulse(state, tick)) return false
        member.production = CrankMachine.productionPerSolveTick(state)
        persist(member, blockState, store, tick)
        return true
    }

    fun addFuel(
        state: BurnerState,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        tick: Long,
        itemId: String,
    ): Long {
        val added = BurnerMachine.addFuel(state, itemId)
        if (added > 0) persist(member, blockState, store, tick)
        return added
    }

    fun kick(
        state: BurnerState,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        tick: Long,
    ): Boolean {
        if (!BurnerMachine.kick(state, tick)) return false
        member.production = BurnerMachine.productionPerSolveTick(state)
        persist(member, blockState, store, tick)
        return true
    }

    private fun persist(
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        tick: Long,
    ) {
        member.unsaved = true
        member.persist(blockState, store, tick, 0, immediate = true)
    }
}

private fun machineReadout(
    name: String,
    member: EnergyMember,
): String =
    "$name: ${member.stored} Fizz, network ${member.networkId}, " +
        "production ${member.production}, delivered ${member.delivered}"

private const val SAVE_INTERVAL_TICKS = 20L
