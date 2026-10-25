package studio.craftory.tech.machine

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.protocol.InteractionType
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
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.api.tick.Handle
import studio.craftory.core.api.tick.TickScheduler
import studio.craftory.tech.CraftoryIds

internal class JunkPowerWorld : Resource<ChunkStore> {
    private data class MachineEntry(
        val id: Long,
        val state: JunkPowerState,
        val blockState: BlockModule.BlockStateInfo,
        val machine: Any?,
        val schedulerHandle: Handle?,
        var lastProduction: Long = 0,
        var lastSavedTick: Long = 0,
        var unsaved: Boolean = false,
        var visualBurning: Boolean? = null,
    ) {
        fun persist(
            store: Store<ChunkStore>?,
            currentTick: Long,
            immediate: Boolean,
        ) {
            if (immediate) unsaved = true
            if (
                !MachineSaveThrottle.shouldPersist(
                    unsaved,
                    currentTick,
                    lastSavedTick,
                    SAVE_INTERVAL_TICKS,
                    immediate,
                )
            ) {
                return
            }
            val currentStore = store ?: return
            blockState.markNeedsSaving(currentStore)
            lastSavedTick = currentTick
            unsaved = false
        }
    }

    private val scheduler = TickScheduler()
    private val machines = HashMap<Long, MachineEntry>()
    private var world: World? = null
    private var store: Store<ChunkStore>? = null

    override fun clone(): Resource<ChunkStore> = JunkPowerWorld()

    fun add(
        id: Long,
        state: JunkPowerState,
        blockState: BlockModule.BlockStateInfo,
        world: World,
    ) {
        if (machines.containsKey(id)) return
        val machine =
            when (state.machine) {
                "Crank" -> CrankMachine(state.spinTicks)
                "Burner" -> BurnerMachine(state.burnTicks, state.burning, FUEL_DEFINITIONS)
                else -> null
            }
        val handle =
            if (machine != null) {
                scheduler.register(id, MACHINE_TICK_INTERVAL) { currentTick ->
                    tickMachine(id, currentTick)
                }
            } else {
                null
            }
        machines[id] = MachineEntry(id, state, blockState, machine, handle, lastSavedTick = world.tick)
    }

    fun remove(id: Long) {
        val entry = machines.remove(id) ?: return
        entry.schedulerHandle?.let(scheduler::unregister)
    }

    fun tick(
        world: World,
        store: Store<ChunkStore>,
    ) {
        this.world = world
        this.store = store
        scheduler.tick(world.tick)
    }

    fun use(
        id: Long,
        heldItem: ItemStack?,
        currentTick: Long,
    ): String? {
        val entry = machines[id] ?: return null
        when (val machine = entry.machine) {
            is CrankMachine -> {
                if (heldItem == null) machine.pulse(currentTick)
                entry.state.spinTicks = machine.spinTicks
                entry.persist(store, currentTick, immediate = true)
                updateProduction(entry, machine.productionPerSolveTick())
                return if (heldItem == null) readout(entry) else null
            }

            is BurnerMachine -> {
                if (heldItem != null) return null
            }
        }
        return readout(entry)
    }

    fun addFuel(
        id: Long,
        itemId: String,
        currentTick: Long,
    ): Long {
        val entry = machines[id] ?: return 0
        val burner = entry.machine as? BurnerMachine ?: return 0
        val added = burner.addFuel(itemId)
        if (added == 0L) return 0
        entry.state.burnTicks = burner.burnTicks
        entry.persist(store, currentTick, immediate = true)
        return added
    }

    fun kick(
        id: Long,
        currentTick: Long,
    ): Boolean {
        val entry = machines[id] ?: return false
        val burner = entry.machine as? BurnerMachine ?: return false
        if (!burner.kick(currentTick)) return false
        entry.state.burning = true
        entry.persist(store, currentTick, immediate = true)
        updateProduction(entry, burner.productionPerSolveTick())
        setBurnerState(entry, true)
        entry.visualBurning = true
        return true
    }

    private fun tickMachine(
        id: Long,
        currentTick: Long,
    ) {
        val entry = machines[id] ?: return
        var dirty = false
        val production =
            when (val machine = entry.machine) {
                is CrankMachine -> {
                    dirty = machine.advanceTo(currentTick)
                    entry.state.spinTicks = machine.spinTicks
                    machine.productionPerSolveTick()
                }

                is BurnerMachine -> {
                    dirty = machine.advanceTo(currentTick)
                    entry.state.burnTicks = machine.burnTicks
                    entry.state.burning = machine.isBurning
                    machine.productionPerSolveTick()
                }

                else -> {
                    0L
                }
            }
        updateProduction(entry, production)
        val visualChanged = entry.machine is BurnerMachine && entry.visualBurning != entry.state.burning
        if (visualChanged) {
            setBurnerState(entry, entry.state.burning)
            entry.visualBurning = entry.state.burning
        }
        if (dirty) entry.unsaved = true
        if (entry.unsaved || visualChanged) entry.persist(store, currentTick, immediate = visualChanged)
    }

    private fun updateProduction(
        entry: MachineEntry,
        production: Long,
    ) {
        if (entry.lastProduction == production) return
        val currentWorld = world ?: return
        if (CraftoryCore.energyAccess(currentWorld).networkOf(entry.id) < 0) return
        val energy = CraftoryCore.energyAccess(currentWorld)
        energy.setProduction(entry.id, production, FIZZ_RANK)
        entry.lastProduction = production
    }

    private fun setBurnerState(
        entry: MachineEntry,
        burning: Boolean,
    ) {
        val currentStore = store ?: return
        val position = Vector3i()
        if (!entry.blockState.fillWorldPos(currentStore, position)) return
        val currentWorld = world ?: return
        BlockOperations.setBlockInteractionState(
            currentWorld.chunkStore,
            entry.blockState.sectionRef,
            position.x,
            position.y,
            position.z,
            BlockType.fromString(CraftoryIds.BURNER) ?: return,
            if (burning) "On" else "Off",
            true,
        )
    }

    private fun readout(entry: MachineEntry): String? {
        val currentWorld = world ?: return null
        val energy = CraftoryCore.energyAccess(currentWorld)
        val rank = energy.networkGrade(entry.id)
        val grade =
            GradeTable.DEFAULT
                .byRank(rank)
                ?.id
                ?.replace('_', ' ')
                ?.replaceFirstChar(Char::uppercase) ?: "Fizz"
        val capacity =
            when (entry.state.machine) {
                "Canister" -> 100_000
                else -> 0
            }
        val stored = energy.stored(entry.id)
        val production = entry.lastProduction
        val activity = if (production > 0) "producing $production" else "consuming ${energy.delivered(entry.id)}"
        val name =
            when (entry.state.machine) {
                "Crank" -> "Crank"
                "Burner" -> "Boomshroom Burner"
                "Canister" -> "Fizz Canister"
                else -> "Gutterpipe"
            }
        return "$name: $stored/$capacity Fizz, network ${energy.networkMembers(
            entry.id,
        )} members, grade $grade, $activity"
    }

    companion object {
        lateinit var type: ResourceType<ChunkStore, JunkPowerWorld>

        val FUEL_DEFINITIONS =
            listOf(
                FuelDefinition("Plant_Crop_Mushroom_Common_Brown", 200),
                FuelDefinition("Plant_Crop_Mushroom_Common_Blue", 200),
                FuelDefinition("Plant_Crop_Mushroom_Common_Lime", 200),
                FuelDefinition("Plant_Crop_Mushroom_Flatcap_Blue", 200),
                FuelDefinition("Plant_Crop_Mushroom_Flatcap_Green", 200),
                FuelDefinition("Plant_Crop_Mushroom_Glowing_Blue", 200),
                FuelDefinition("Plant_Crop_Mushroom_Glowing_Green", 200),
                FuelDefinition("Plant_Crop_Mushroom_Glowing_Orange", 200),
                FuelDefinition("Plant_Crop_Mushroom_Glowing_Purple", 200),
                FuelDefinition("Plant_Crop_Mushroom_Glowing_Red", 200),
                FuelDefinition("Plant_Crop_Mushroom_Glowing_Violet", 200),
                FuelDefinition("Ingredient_Scraps", 40),
            )

        private const val FIZZ_RANK = 0
        private const val MACHINE_TICK_INTERVAL = 4
        private const val SAVE_INTERVAL_TICKS = 20L
    }
}

internal class JunkPowerLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(JunkPowerState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = commandBuffer.getComponent(ref, JunkPowerState.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val world = store.externalData.world
        store
            .getResource(
                JunkPowerWorld.type,
            ).add(BlockPos.pack(position.x, position.y, position.z), state, blockState, world)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        store.getResource(JunkPowerWorld.type).remove(BlockPos.pack(position.x, position.y, position.z))
    }
}

internal class JunkPowerTickSystem : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val world = store.externalData.world
        store.getResource(JunkPowerWorld.type).tick(world, store)
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
        val position = event.targetBlock
        val consumed =
            world.chunkStore.store
                .getResource(JunkPowerWorld.type)
                .kick(BlockPos.pack(position.x, position.y, position.z), world.tick)
        if (consumed) event.isCancelled = true
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
        val id = BlockPos.pack(position.x, position.y, position.z)
        val runtime = world.chunkStore.store.getResource(JunkPowerWorld.type)
        val state = BlockModule.getComponent(JunkPowerState.type, world, position.x, position.y, position.z) ?: return
        val entry = runtime.use(id, heldItem, world.tick)
        if (state.machine == "Burner" && heldItem != null) {
            val container = context.heldItemContainer ?: return
            val fuel = runtime.addFuel(id, heldItem.itemId, world.tick)
            if (fuel > 0) {
                val replacement = if (heldItem.quantity == 1) null else heldItem.withQuantity(heldItem.quantity - 1)
                container.setItemStackForSlot(context.heldItemSlot.toShort(), replacement)
            }
            return
        }
        if (entry != null) {
            commandBuffer.getComponent(context.entity, PlayerRef.getComponentType())?.sendMessage(
                com.hypixel.hytale.server.core.Message
                    .raw(entry),
            )
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
