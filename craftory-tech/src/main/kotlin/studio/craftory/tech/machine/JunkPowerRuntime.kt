package studio.craftory.tech.machine

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
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
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.api.tick.Handle
import studio.craftory.core.api.tick.TickScheduler
import studio.craftory.tech.CraftoryIds

@Suppress("TooManyFunctions")
internal class JunkPowerWorld : Resource<ChunkStore> {
    private data class MachineEntry(
        val id: Long,
        val state: JunkPowerState,
        val blockState: BlockModule.BlockStateInfo,
        val machine: Any?,
        val processingBench: ProcessingBenchBlock?,
        val schedulerHandle: Handle?,
        var lastProduction: Long = 0,
        var lastDemand: Long = 0,
        var ovenFizzRemainder: Long = 0,
        var lastFizzSolveTick: Long = Long.MIN_VALUE,
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
        processingBench: ProcessingBenchBlock?,
    ) {
        if (machines.containsKey(id)) return
        val machine =
            when (state.machine) {
                "Crank" -> {
                    CrankMachine(state.spinTicks)
                }

                "Burner" -> {
                    BurnerMachine(state.burnTicks, state.burning, FUEL_DEFINITIONS)
                }

                "GnasherPit" -> {
                    GnasherPitMachine(
                        GritTable.decodeQueue(state.oreQueue),
                        state.grindPulses,
                        state.jammed,
                    )
                }

                else -> {
                    null
                }
            }
        val handle =
            if (machine != null || state.machine == "ScorchOven") {
                scheduler.register(id, MACHINE_TICK_INTERVAL) { currentTick ->
                    tickMachine(id, currentTick)
                }
            } else {
                null
            }
        machines[id] =
            MachineEntry(
                id,
                state,
                blockState,
                machine,
                processingBench,
                handle,
                lastSavedTick = world.tick,
            )
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
        CanisterPage.tickAll(world, world.tick)
    }

    fun use(
        id: Long,
        heldItem: ItemStack?,
        currentTick: Long,
        entityStore: com.hypixel.hytale.component.ComponentAccessor<EntityStore>? = null,
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

            is GnasherPitMachine -> {
                if (heldItem == null) {
                    val previousQueue = machine.snapshot()
                    val previousProgress = machine.progressPulses
                    val output = machine.crank(currentTick)
                    entry.state.oreQueue = GritTable.encodeQueue(machine.snapshot())
                    entry.state.grindPulses = machine.progressPulses
                    entry.state.jammed = machine.isJammed
                    if (output != null) dropGrit(entry, output, entityStore)
                    if (
                        output != null ||
                        previousQueue != machine.snapshot() ||
                        previousProgress != machine.progressPulses
                    ) {
                        entry.persist(store, currentTick, immediate = true)
                    }
                }
                return if (machine.isJammed) "Jammed! Kick it." else readout(entry)
            }
        }
        return readout(entry)
    }

    fun depositOre(
        id: Long,
        oreItemId: String,
        currentTick: Long,
    ): Boolean {
        val entry = machines[id] ?: return false
        val gnasher = entry.machine as? GnasherPitMachine ?: return false
        if (!gnasher.deposit(oreItemId)) return false
        entry.state.oreQueue = GritTable.encodeQueue(gnasher.snapshot())
        entry.state.jammed = gnasher.isJammed
        entry.persist(store, currentTick, immediate = true)
        return true
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

    fun kickGnasher(
        id: Long,
        currentTick: Long,
    ): Boolean {
        val entry = machines[id] ?: return false
        val gnasher = entry.machine as? GnasherPitMachine ?: return false
        if (!gnasher.kick()) return false
        entry.state.jammed = false
        entry.persist(store, currentTick, immediate = true)
        return true
    }

    fun chargeInsertionFailed(id: Long): Boolean = machines[id]?.ovenFizzRemainder == OVEN_INSERTION_FAILED

    @Suppress("CyclomaticComplexMethod")
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

                is GnasherPitMachine -> {
                    val previousQueue = machine.snapshot()
                    val previousProgress = machine.progressPulses
                    updateDemand(
                        entry,
                        if (machine.queuedOreCount > 0 && !machine.isJammed) {
                            GnasherPitMachine.FIZZ_PER_SOLVE_TICK
                        } else {
                            0L
                        },
                    )
                    val output = machine.autoCrank(currentTick, energyAccess().delivered(entry.id))
                    entry.state.oreQueue = GritTable.encodeQueue(machine.snapshot())
                    entry.state.grindPulses = machine.progressPulses
                    entry.state.jammed = machine.isJammed
                    if (output != null) dropGrit(entry, output, world?.entityStore?.store)
                    dirty =
                        output != null ||
                        previousProgress != machine.progressPulses ||
                        previousQueue != machine.snapshot()
                    0L
                }

                else -> {
                    0L
                }
            }
        if (entry.state.machine == "ScorchOven") {
            tickOven(entry, currentTick)
            return
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

    private fun tickOven(
        entry: MachineEntry,
        currentTick: Long,
    ) {
        val bench = entry.processingBench ?: return
        val input = bench.inputContainer
        var hasInput = false
        var slot = 0
        while (slot < input.capacity) {
            if (input.getItemStack(slot.toShort())?.isEmpty == false) {
                hasInput = true
                break
            }
            slot++
        }
        val chargeStack = bench.fuelContainer.getItemStack(0.toShort())
        val chargeCount = if (chargeStack?.itemId == CraftoryIds.FIZZ_CHARGE) chargeStack.quantity else 0
        val demand =
            if (hasInput && bench.recipe != null && chargeCount < OVEN_MAX_CHARGES) {
                OVEN_FIZZ_PER_SOLVE_TICK
            } else {
                0L
            }
        updateDemand(entry, demand)
        if (demand == 0L) return

        val energy = energyAccess()
        val solveTick = energy.lastSolveTick()
        if (solveTick == entry.lastFizzSolveTick) return
        entry.lastFizzSolveTick = solveTick
        entry.ovenFizzRemainder += energy.delivered(entry.id)
        if (entry.ovenFizzRemainder < OVEN_FIZZ_PER_CHARGE) return
        val transaction =
            bench.fuelContainer.setItemStackForSlot(
                0.toShort(),
                ItemStack(CraftoryIds.FIZZ_CHARGE, chargeCount + 1),
            )
        if (!transaction.succeeded()) {
            entry.ovenFizzRemainder = OVEN_INSERTION_FAILED
            return
        }
        entry.ovenFizzRemainder -= OVEN_FIZZ_PER_CHARGE
        entry.persist(store, currentTick, immediate = true)
    }

    private fun updateDemand(
        entry: MachineEntry,
        demand: Long,
    ) {
        if (entry.lastDemand == demand) return
        val energy = energyAccess()
        if (energy.networkOf(entry.id) < 0) return
        energy.setDemand(entry.id, demand)
        entry.lastDemand = demand
    }

    private fun energyAccess() = CraftoryCore.energyAccess(world ?: error("JunkPowerWorld is not bound"))

    private fun dropGrit(
        entry: MachineEntry,
        gritItemId: String,
        entityStore: com.hypixel.hytale.component.ComponentAccessor<EntityStore>?,
    ) {
        val accessor = entityStore ?: return
        val currentStore = store ?: return
        val position = Vector3i()
        if (!entry.blockState.fillWorldPos(currentStore, position)) return
        val item =
            ItemComponent.generateItemDrop(
                accessor,
                ItemStack(gritItemId, GnasherPitMachine.GRIT_OUTPUT_QUANTITY),
                Vector3d(position.x + 0.5, position.y + 1.0, position.z + 0.5),
                Rotation3f.IDENTITY,
                0f,
                0f,
                0f,
            ) ?: return
        accessor.addEntity(item, AddReason.SPAWN)
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
        val stored = energy.stored(entry.id)
        val capacity = energy.capacity(entry.id)
        val production = entry.lastProduction
        val activity =
            when {
                production > 0 -> "producing $production"
                entry.lastDemand > 0 -> "consuming ${energy.delivered(entry.id)}"
                else -> "net flow ${energy.networkFlow(entry.id)}"
            }
        val name =
            when (entry.state.machine) {
                "Crank" -> "Crank"
                "Burner" -> "Boomshroom Burner"
                "ScorchOven" -> "Scorch Oven"
                "GnasherPit" -> "Gnasher Pit"
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
        private const val OVEN_FIZZ_PER_SOLVE_TICK = 30L
        private const val OVEN_FIZZ_PER_CHARGE = 120L
        private const val OVEN_MAX_CHARGES = 2
        private const val OVEN_INSERTION_FAILED = -1L
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
            ).add(
                BlockPos.pack(position.x, position.y, position.z),
                state,
                blockState,
                world,
                commandBuffer.getComponent(ref, ProcessingBenchBlock.getComponentType()),
            )
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
        val world = store.externalData.world
        val position = event.targetBlock
        val runtime = world.chunkStore.store.getResource(JunkPowerWorld.type)
        val id = BlockPos.pack(position.x, position.y, position.z)
        val consumed =
            when (event.blockType.id) {
                CraftoryIds.BURNER -> runtime.kick(id, world.tick)
                CraftoryIds.GNASHER_PIT -> runtime.kickGnasher(id, world.tick)
                else -> false
            }
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
        if (state.machine == "GnasherPit" && heldItem != null) {
            val deposited = runtime.depositOre(id, heldItem.itemId, world.tick)
            if (deposited) {
                val container = context.heldItemContainer
                val replacement = if (heldItem.quantity == 1) null else heldItem.withQuantity(heldItem.quantity - 1)
                container?.setItemStackForSlot(context.heldItemSlot.toShort(), replacement)
            }
        }
        val entry = runtime.use(id, heldItem, world.tick, commandBuffer)
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
