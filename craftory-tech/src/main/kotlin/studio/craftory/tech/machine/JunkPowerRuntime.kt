package studio.craftory.tech.machine

import com.hypixel.hytale.builtin.crafting.component.BenchBlock
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.Holder
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.FluidTank
import studio.craftory.core.api.transport.FluidTransport
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.notebook.NotebookPage
import studio.craftory.tech.notebook.NotebookPages
import java.util.logging.Level

private const val BUCKET_VOLUME = 1_000L
private const val FILLED_WATER_BUCKET_STATE = "Filled_Water"

private fun filledWaterBucketItemId(stack: ItemStack?): String? {
    if (stack == null || stack.quantity != 1) return null
    val emptyBucket = Item.getAssetMap().getAsset(CraftoryIds.EMPTY_WOODEN_BUCKET) ?: return null
    if (emptyBucket.getStateForItem(stack.itemId) != FILLED_WATER_BUCKET_STATE) return null
    return emptyBucket.getItemIdForState(FILLED_WATER_BUCKET_STATE)
}

internal class CrankSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            CrankState.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

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
        super.tick(dt, chunk, store, commandBuffer)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
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
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            MachineClock.pause(state, tick)
            return
        }
        if (!CraftoryCore.isEnergyTick(store)) return
        if (CrankMachine.advanceTo(state, tick)) member.unsaved = true
        member.production = CrankMachine.productionPerSolveTick(state)
        member.persist(blockState, commandBuffer, tick, SAVE_INTERVAL_TICKS, immediate = false)
    }
}

internal class BurnerSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            BurnerState.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

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
        super.tick(dt, chunk, store, commandBuffer)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
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
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            MachineClock.pause(state, tick)
            return
        }
        if (!CraftoryCore.isEnergyTick(store)) return
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

internal class ScorchOvenSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            OvenPower.type,
            EnergyMember.type,
            ProcessingBenchBlock.getComponentType(),
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

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val power = chunk.getComponent(index, OvenPower.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val bench = chunk.getComponent(index, ProcessingBenchBlock.getComponentType()) ?: return
        val benchBlock = chunk.getComponent(index, BenchBlock.getComponentType()) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        val tick = world.tick
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            MachineClock.pause(power, tick)
            return
        }
        if (!CraftoryCore.isEnergyTick(store)) return
        val powerLoss = OvenPowerMath.updatePowerLoss(power.lastDeliveredTick, tick, member.delivered > 0)
        power.lastDeliveredTick = powerLoss.lastDeliveredTick
        if (bench.isActive() && powerLoss.shouldStop) {
            bench.setActive(false, benchBlock, blockState)
        }
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val id = BlockPos.pack(position.x, position.y, position.z)
        val chargeStack = bench.fuelContainer.getItemStack(0.toShort())
        val chargeCount =
            if (chargeStack?.itemId == CraftoryIds.FIZZ_CHARGE) {
                chargeStack.quantity
            } else {
                0
            }
        val fuelSlotAvailable =
            chargeStack == null || chargeStack.isEmpty || chargeStack.itemId == CraftoryIds.FIZZ_CHARGE
        val energy = CraftoryCore.energyAccess(world)
        val completedSolveTick = energy.lastSolveTick()
        val fizzUpdate =
            OvenFizzAccounting.update(
                power.accumulatedFizz,
                power.lastFizzSolveTick,
                completedSolveTick,
                member.delivered,
                hasRecipeInput(bench),
                chargeCount,
                fuelSlotAvailable,
            )
        if (member.demand != fizzUpdate.demand) energy.setDemand(id, fizzUpdate.demand)
        var dirty = false
        power.lastFizzSolveTick = fizzUpdate.lastFizzSolveTick
        if (power.accumulatedFizz != fizzUpdate.accumulatedFizz) {
            power.accumulatedFizz = fizzUpdate.accumulatedFizz
            dirty = true
        }
        if (fizzUpdate.shouldInsertCharge) {
            val transaction =
                bench.fuelContainer.setItemStackForSlot(
                    0.toShort(),
                    ItemStack(CraftoryIds.FIZZ_CHARGE, chargeCount + 1),
                )
            if (transaction.succeeded()) {
                power.accumulatedFizz -= OvenFizzAccounting.FIZZ_PER_CHARGE
                dirty = true
            }
        }
        if (dirty) blockState.markNeedsSaving(commandBuffer)
    }

    private fun hasRecipeInput(bench: ProcessingBenchBlock): Boolean =
        bench.recipe != null &&
            (0 until bench.inputContainer.capacity).any { slot ->
                bench.inputContainer.getItemStack(slot.toShort())?.isEmpty == false
            }

    private companion object {
        const val MAX_CHARGES = OvenFizzAccounting.MAX_CHARGES
    }
}

internal class GnasherSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            GnasherState.type,
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
        val state = chunk.getComponent(index, GnasherState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            MachineClock.pause(state, world.tick)
            return
        }
        if (!CraftoryCore.isEnergyTick(store)) return
        val position = blockState.worldPosition(commandBuffer) ?: return
        val id = BlockPos.pack(position.x, position.y, position.z)
        val demand =
            if (!state.jammed && GritTable.queuedOreCount(state.oreQueue) > 0) {
                GnasherMachine.FIZZ_PER_SOLVE_TICK
            } else {
                0L
            }
        val energy = CraftoryCore.energyAccess(world)
        if (member.demand != demand) energy.setDemand(id, demand)
        val result =
            MachineActions.autoCrankGnasher(
                world,
                state,
                blockState,
                commandBuffer,
                world.tick,
                member.delivered,
                member.deliveredRank,
                position,
            )
        if (!result.pulse.accepted) return
    }
}

internal fun BlockModule.BlockStateInfo.worldPosition(store: ComponentAccessor<ChunkStore>): BlockPos? {
    val sectionRef = this.sectionRef
    if (!sectionRef.isValid) return null
    val section = store.getComponent(sectionRef, ChunkSection.getComponentType()) ?: return null
    val blockIndex = this.index
    return BlockPos(
        ChunkUtil.worldCoordFromLocalCoord(section.getX(), ChunkUtil.xFromIndex(blockIndex)),
        ChunkUtil.worldCoordFromLocalCoord(section.getY(), ChunkUtil.yFromIndex(blockIndex)),
        ChunkUtil.worldCoordFromLocalCoord(section.getZ(), ChunkUtil.zFromIndex(blockIndex)),
    )
}

internal sealed interface GritDropResult {
    data class Spawned(
        val ref: Ref<EntityStore>,
    ) : GritDropResult

    data class InvalidItem(
        val itemId: String,
    ) : GritDropResult

    data object Parked : GritDropResult

    data object SpawnRejected : GritDropResult

    data object NoPosition : GritDropResult
}

internal data class GnasherPulseActionResult(
    val pulse: GnasherPulseResult,
    val drop: GritDropResult?,
)

internal fun dropGrit(
    world: World,
    store: Store<EntityStore>,
    machinePosition: BlockPos?,
    itemId: String,
    targetSection: Ref<ChunkStore>,
): GritDropResult {
    if (machinePosition == null) {
        LOGGER.at(Level.WARNING).log("Gnasher grit drop failed: machine world position is unavailable")
        return GritDropResult.NoPosition
    }
    val holder =
        ItemComponent.generateItemDrop(
            store,
            ItemStack(itemId, GnasherMachine.GRIT_OUTPUT_QUANTITY),
            Vector3d(machinePosition.x + 0.5, machinePosition.y + 1.0, machinePosition.z + 0.5),
            Rotation3f.IDENTITY,
            0f,
            0f,
            0f,
        )
    if (holder == null) {
        LOGGER.at(Level.WARNING).log("Gnasher grit drop failed: invalid item %s", itemId)
        return GritDropResult.InvalidItem(itemId)
    }
    val ref = world.entityStore.store.addEntity(holder, AddReason.SPAWN)
    if (ref == null || !ref.isValid) {
        if (
            targetSection.isValid &&
            world.chunkStore.store.getComponent(
                targetSection,
                ChunkStore.REGISTRY.getNonTickingComponentType(),
            ) != null
        ) {
            return GritDropResult.Parked
        }
        LOGGER.at(Level.WARNING).log("Gnasher grit drop failed: EntityStore invalidated the new entity")
        return GritDropResult.SpawnRejected
    }
    return GritDropResult.Spawned(ref)
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
        val pos = event.targetBlock
        val ref = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z) ?: return
        val chunks = world.chunkStore.store
        if (event.blockType.id == CraftoryIds.GNASHER_PIT) {
            val state = chunks.getComponent(ref, GnasherState.type) ?: return
            val blockState = chunks.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
            if (!MachineActions.kickGnasher(state, blockState, chunks)) return
            event.isCancelled = true
            return
        }
        if (event.blockType.id != CraftoryIds.BURNER) return
        val state = chunks.getComponent(ref, BurnerState.type) ?: return
        val member = chunks.getComponent(ref, EnergyMember.type) ?: return
        val blockState = chunks.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!MachineActions.kick(state, member, blockState, chunks, world.tick)) return
        event.isCancelled = true
    }
}

class CraftoryBlockUseInteraction : SimpleBlockInteraction() {
    @Suppress("CyclomaticComplexMethod", "LongMethod")
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
        val blockState = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val tick = world.tick
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val owner = store.getComponent(ref, MachineOwner.type)?.placerUuid
        if (!mayUseMachine(owner, player.uuid, player, CraftoryCore.crewService())) {
            player.sendMessage(Message.translation("server.craftory.wrench.foreign-crew"))
            return
        }
        val fluidTank = store.getComponent(ref, FluidTank.type)
        if (fluidTank != null) {
            val transport = FluidTransport.of(world)
            val filledBucketItemId = filledWaterBucketItemId(heldItem)
            val hasRoom =
                transport.capacity(position.x, position.y, position.z) -
                    transport.amount(position.x, position.y, position.z) >= BUCKET_VOLUME
            if (filledBucketItemId != null &&
                hasRoom &&
                context.replaceHeldStack(heldItem, CraftoryIds.EMPTY_WOODEN_BUCKET)
            ) {
                val accepted = transport.insert(position.x, position.y, position.z, "Water_Source", BUCKET_VOLUME)
                if (accepted != BUCKET_VOLUME) {
                    if (accepted > 0) transport.drain(position.x, position.y, position.z, accepted)
                    val rolledBack =
                        context.replaceHeldStack(
                            ItemStack(CraftoryIds.EMPTY_WOODEN_BUCKET, 1),
                            filledBucketItemId,
                        )
                    check(rolledBack) {
                        "failed to roll back an incomplete water bucket exchange"
                    }
                    return
                }
                blockState.markNeedsSaving(store)
                player.sendMessage(Message.raw("Added $BUCKET_VOLUME mB of water."))
            } else {
                player.sendMessage(
                    Message.raw(
                        "Fluid: ${transport.fluid(position.x, position.y, position.z) ?: "empty"}; " +
                            "${transport.amount(position.x, position.y, position.z)}/" +
                            "${transport.capacity(position.x, position.y, position.z)} mB",
                    ),
                )
            }
            return
        }
        val anchor = store.getComponent(ref, AnchorState.type)
        if (anchor != null) {
            val message =
                context.useAnchor(
                    world,
                    anchor,
                    blockState,
                    store,
                    BlockPos(position.x, position.y, position.z),
                    heldItem,
                )
            commandBuffer.getComponent(context.entity, PlayerRef.getComponentType())?.sendMessage(Message.raw(message))
            return
        }
        val member = store.getComponent(ref, EnergyMember.type) ?: return
        val boiler = store.getComponent(ref, BoilerState.type)
        if (boiler != null) {
            when {
                boiler.online && heldItem == null && GutterBoilerRules.vent(boiler) -> {
                    blockState.markNeedsSaving(store)
                    GutterBoilerEffects.vent(world, position.x, position.y, position.z)
                    player.sendMessage(Message.raw("Vented boiler; pressure is ${boiler.pressure}."))
                }

                !boiler.online && heldItem?.itemId == "Ingredient_Scraps" && heldItem.quantity >= 2 -> {
                    if (context.consumeHeld(2) && GutterBoilerRules.rerivet(boiler, 2)) {
                        blockState.markNeedsSaving(store)
                        player.sendMessage(Message.raw("Boiler re-riveted."))
                    }
                }

                heldItem != null &&
                    filledWaterBucketItemId(heldItem) != null &&
                    boiler.water <= GutterBoilerRules.CAPACITY - BUCKET_VOLUME -> {
                    if (context.replaceHeldStack(heldItem, CraftoryIds.EMPTY_WOODEN_BUCKET)) {
                        boiler.water += BUCKET_VOLUME
                        blockState.markNeedsSaving(store)
                        player.sendMessage(Message.raw("Added $BUCKET_VOLUME mB of water."))
                    }
                }

                heldItem != null -> {
                    context.addBoilerFuel(heldItem, boiler, blockState, store)
                }

                else -> {
                    player.sendMessage(
                        Message.raw(
                            "Boiler: ${if (boiler.online) "online" else "offline"}; " +
                                "pressure ${boiler.pressure}%; water ${boiler.water}/${GutterBoilerRules.CAPACITY} mB.",
                        ),
                    )
                }
            }
            return
        }
        val gasbag = store.getComponent(ref, GasbagState.type)
        if (gasbag != null) {
            val patchRequested = heldItem?.itemId == CraftoryIds.SCRAP_PLATING && gasbag.leaking
            if (patchRequested && context.consumeHeld() && GasbagRules.patch(gasbag)) {
                blockState.markNeedsSaving(store)
                player.sendMessage(Message.raw("Gasbag leak patched."))
            } else {
                val energy = CraftoryCore.energyAccess(world)
                player.sendMessage(
                    Message.raw(
                        "Gasbag: ${energy.stored(BlockPos.pack(position.x, position.y, position.z))}/400000 fizz; " +
                            if (gasbag.leaking) "leaking" else "sealed",
                    ),
                )
            }
            return
        }
        val gnasher = store.getComponent(ref, GnasherState.type)
        if (gnasher != null) {
            val message =
                if (heldItem == null) {
                    val action =
                        MachineActions.pulseGnasher(
                            world,
                            gnasher,
                            blockState,
                            store,
                            tick,
                            BlockPos(position.x, position.y, position.z),
                        )
                    when (val drop = action.drop) {
                        null, is GritDropResult.Spawned -> gnasherReadout(gnasher, member)
                        else -> "Gnasher grit drop failed: $drop"
                    }
                } else {
                    if (context.depositOre(heldItem, gnasher, blockState, store)) {
                        "Deposited ${heldItem.itemId}."
                    } else {
                        gnasherReadout(gnasher, member)
                    }
                }
            commandBuffer.getComponent(context.entity, PlayerRef.getComponentType())?.sendMessage(Message.raw(message))
            return
        }
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
                    context.addFuelTo(heldItem, state, member, blockState, store, tick)
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

class CraftoryWrenchUseInteraction : SimpleBlockInteraction() {
    @Suppress("CyclomaticComplexMethod")
    override fun interactWithBlock(
        world: World,
        commandBuffer: CommandBuffer<EntityStore>,
        type: InteractionType,
        context: InteractionContext,
        heldItem: ItemStack?,
        position: Vector3i,
        cooldownHandler: CooldownHandler,
    ) {
        if (heldItem?.itemId != CraftoryIds.TINKERS_WRENCH) return
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
        val result = MachineActions.useWrench(world, ref, position, player, world.chunkStore.store)
        val message =
            when (result) {
                WrenchActionResult.NotMachine -> {
                    Message.translation("server.craftory.wrench.not-machine")
                }

                WrenchActionResult.RemovalFailed -> {
                    Message.translation("server.craftory.wrench.failed")
                }

                WrenchActionResult.ForeignCrew -> {
                    Message.translation("server.craftory.wrench.foreign-crew")
                }

                WrenchActionResult.HasContents -> {
                    Message.translation("server.craftory.wrench.empty-first")
                }

                is WrenchActionResult.Learned -> {
                    Message
                        .translation(
                            if (result.burnTimeLost) {
                                "server.craftory.wrench.learned-burn-lost"
                            } else {
                                "server.craftory.wrench.learned"
                            },
                        ).param("machine", Message.translation(result.machineNameKey))
                        .param("page", Message.translation(result.page.nameKey))
                }

                is WrenchActionResult.Packed -> {
                    Message
                        .translation(
                            when {
                                !result.itemDropped && result.burnTimeLost -> {
                                    "server.craftory.wrench.pack-failed-burn-lost"
                                }

                                !result.itemDropped -> {
                                    "server.craftory.wrench.pack-failed"
                                }

                                result.burnTimeLost -> {
                                    "server.craftory.wrench.packed-burn-lost"
                                }

                                else -> {
                                    "server.craftory.wrench.packed"
                                }
                            },
                        ).param("machine", Message.translation(result.machineNameKey))
                }
            }
        player.sendMessage(message)
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
                    CraftoryWrenchUseInteraction::class.java,
                    ::CraftoryWrenchUseInteraction,
                    SimpleBlockInteraction.CODEC,
                ).build()
    }
}

@Suppress("TooManyFunctions")
internal object MachineActions {
    @Suppress("NestedBlockDepth")
    fun activateAnchor(
        world: World,
        state: AnchorState,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        position: BlockPos,
    ) {
        if (state.chargeSeconds <= 0 || CraftoryCore.anchorMaxChargeMinutes() <= 0) return
        val id = BlockPos.pack(position.x, position.y, position.z)
        state.coverageId = id
        state.coverageActive = true
        state.areaRefreshTicks = 9
        CraftoryCore.setAnchorCoverage(store, id, position.x shr 5, position.z shr 5, true)
        AnchorSystem.requestArea(world, state, position, store)
        blockState.markNeedsSaving(store)
    }

    fun useWrench(
        world: World,
        blockRef: Ref<ChunkStore>,
        position: Vector3i,
        playerRef: PlayerRef,
        store: Store<ChunkStore>,
    ): WrenchActionResult =
        useWrench(
            world,
            blockRef,
            position,
            playerRef.uuid,
            playerRef,
            CraftoryCore.crewService(),
            store,
            bypassOwnership = false,
        )

    fun useWrench(
        world: World,
        blockRef: Ref<ChunkStore>,
        position: Vector3i,
        playerUuid: java.util.UUID,
        playerRef: PlayerRef?,
        crews: CrewService,
        store: Store<ChunkStore>,
        bypassOwnership: Boolean = false,
    ): WrenchActionResult {
        val member = store.getComponent(blockRef, EnergyMember.type) ?: return WrenchActionResult.NotMachine
        val page = NotebookPages.forMachineType(member.typeId) ?: return WrenchActionResult.NotMachine
        val itemId = machineItemId(member.typeId) ?: return WrenchActionResult.NotMachine
        val ownerUuid = store.getComponent(blockRef, MachineOwner.type)?.placerUuid
        if (!bypassOwnership && !mayUseMachine(ownerUuid, playerUuid, playerRef, crews)) {
            return WrenchActionResult.ForeignCrew
        }
        val blockState =
            store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType())
                ?: return WrenchActionResult.NotMachine
        val blockType = BlockType.fromString(itemId) ?: return WrenchActionResult.NotMachine
        val hasPage = crews.hasKnowledge(playerUuid, page.id)
        if (hasNonEmptyMachineContents(blockRef, store)) {
            return WrenchActionResult.HasContents
        }
        val dropHolders =
            createWrenchDropHolders(world, position, blockRef, itemId, hasPage, store)
                ?: return WrenchActionResult.RemovalFailed
        val burnTimeLost = store.getComponent(blockRef, BurnerState.type)?.burnTicks?.let { it > 0 } ?: false
        if (!removeWrenchedBlock(world, blockState, blockType, position, playerRef)) {
            return WrenchActionResult.RemovalFailed
        }

        if (hasPage) {
            val itemDropped = spawnDrops(world, position, dropHolders, itemId, playerRef)
            return WrenchActionResult.Packed("server.items.$itemId.name", itemDropped, burnTimeLost)
        }
        val scrapsDropped = spawnDrops(world, position, dropHolders, null, playerRef)
        val pageGranted = crews.grantKnowledge(playerUuid, page.id)
        return WrenchActionResult.Learned("server.items.$itemId.name", page, scrapsDropped, pageGranted, burnTimeLost)
    }

    private fun hasNonEmptyMachineContents(
        blockRef: Ref<ChunkStore>,
        store: Store<ChunkStore>,
    ): Boolean {
        val processingBench = store.getComponent(blockRef, ProcessingBenchBlock.getComponentType())
        val itemContainerBlock = store.getComponent(blockRef, ItemContainerBlock.getComponentType())
        val itemContainer = itemContainerBlock?.itemContainer
        val containers =
            listOfNotNull(
                processingBench?.inputContainer,
                processingBench?.fuelContainer,
                processingBench?.outputContainer,
                itemContainer,
            )
        if (containers.any { !it.isEmpty }) return true
        val bench = store.getComponent(blockRef, BenchBlock.getComponentType()) ?: return false
        return bench.upgradeItems?.any { it?.isEmpty == false } == true
    }

    private fun createWrenchDropHolders(
        world: World,
        position: Vector3i,
        blockRef: Ref<ChunkStore>,
        itemId: String,
        hasPage: Boolean,
        store: Store<ChunkStore>,
    ): List<Holder<EntityStore>>? {
        val firstDropId = if (hasPage) itemId else "Ingredient_Scraps"
        val firstDropQuantity = if (hasPage) 1 else WRENCH_SCRAP_QUANTITY
        val drops = mutableListOf(firstDropId to firstDropQuantity)
        val gnasher = store.getComponent(blockRef, GnasherState.type)
        val queuedOres =
            gnasher
                ?.oreQueue
                ?.split(',')
                ?.filter(String::isNotBlank)
                .orEmpty()
        queuedOres.forEach { drops.add(it to 1) }
        val holders = ArrayList<Holder<EntityStore>>(drops.size)
        for ((dropId, quantity) in drops) {
            holders.add(createDropHolder(world, position, dropId, quantity) ?: return null)
        }
        return holders
    }

    private fun removeWrenchedBlock(
        world: World,
        blockState: BlockModule.BlockStateInfo,
        blockType: BlockType,
        position: Vector3i,
        playerRef: PlayerRef?,
    ): Boolean {
        val removed =
            BlockOperations.setBlock(
                world.chunkStore,
                blockState.sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            )
        if (!removed) return false
        BlockOperations.spawnBlockParticles(
            world.chunkStore,
            BlockType.getAssetMap().getIndex(blockType.id),
            BlockType.EMPTY_ID,
            position.x,
            position.y,
            position.z,
            false,
        )
        val soundIndex = SoundEvent.getAssetMap().getIndex("SFX_GoblinLab_Broken_Device")
        if (soundIndex > 0 && playerRef != null) {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.SFX)
        }
        return true
    }

    private fun machineItemId(typeId: String): String? =
        when (typeId) {
            CraftoryIds.CRANK_TYPE -> CraftoryIds.CRANK
            CraftoryIds.BURNER_TYPE -> CraftoryIds.BURNER
            CraftoryIds.CANISTER_TYPE -> CraftoryIds.CANISTER
            CraftoryIds.PIPE_TYPE -> CraftoryIds.PIPE
            CraftoryIds.GNASHER_TYPE -> CraftoryIds.GNASHER_PIT
            CraftoryIds.OVEN_TYPE -> CraftoryIds.SCORCH_OVEN
            else -> null
        }

    private fun createDropHolder(
        world: World,
        position: Vector3i,
        itemId: String,
        quantity: Int,
    ): Holder<EntityStore>? {
        val store = world.entityStore.store
        return ItemComponent.generateItemDrop(
            store,
            ItemStack(itemId, quantity),
            Vector3d(position.x + 0.5, position.y + 1.0, position.z + 0.5),
            Rotation3f.IDENTITY,
            0f,
            0f,
            0f,
        )
    }

    private fun spawnDrops(
        world: World,
        position: Vector3i,
        holders: List<Holder<EntityStore>>,
        packedItemId: String?,
        playerRef: PlayerRef?,
    ): Boolean {
        val entityStore = world.entityStore.store
        var packedItemSpawned = packedItemId == null
        holders.forEachIndexed { index, holder ->
            val ref = entityStore.addEntity(holder, AddReason.SPAWN)
            if (ref?.isValid == true) {
                if (index == 0 && packedItemId != null) packedItemSpawned = true
            } else if (index == 0 && packedItemId != null) {
                val inventory =
                    playerRef?.let {
                        val playerEntity = it.reference ?: return@let null
                        InventoryComponent.getCombined(
                            world.entityStore.store,
                            playerEntity,
                            *InventoryComponent.HOTBAR_STORAGE_BACKPACK,
                        )
                    }
                val transaction = inventory?.addItemStack(ItemStack(packedItemId, 1))
                if (transaction?.succeeded() == true) {
                    packedItemSpawned = true
                } else {
                    LOGGER.at(Level.SEVERE).log("Unable to return packed item %s at %s", packedItemId, position)
                }
            }
        }
        return packedItemSpawned
    }

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

    fun depositGnasher(
        state: GnasherState,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        itemId: String,
    ): Boolean {
        if (!GnasherMachine.deposit(state, itemId)) return false
        blockState.markNeedsSaving(store)
        return true
    }

    fun pulseGnasher(
        world: World,
        state: GnasherState,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
        tick: Long,
        position: BlockPos?,
    ): GnasherPulseActionResult =
        persistGnasherPulse(
            world,
            state,
            GnasherMachine.pulse(state, tick),
            blockState,
            store,
            position,
        )

    fun autoCrankGnasher(
        world: World,
        state: GnasherState,
        blockState: BlockModule.BlockStateInfo,
        store: ComponentAccessor<ChunkStore>,
        tick: Long,
        deliveredFizz: Long,
        deliveredRank: Int,
        position: BlockPos?,
    ): GnasherPulseActionResult =
        persistGnasherPulse(
            world,
            state,
            GnasherMachine.autoCrank(state, tick, deliveredFizz, deliveredRank),
            blockState,
            store,
            position,
        )

    fun persistGnasherPulse(
        world: World,
        state: GnasherState,
        pulse: GnasherPulseResult,
        blockState: BlockModule.BlockStateInfo,
        store: ComponentAccessor<ChunkStore>,
        position: BlockPos?,
    ): GnasherPulseActionResult {
        if (!pulse.accepted) return GnasherPulseActionResult(pulse, null)
        val drop =
            pulse.outputItemId?.let {
                dropGrit(world, world.entityStore.store, position, it, blockState.sectionRef)
            }
        if (
            drop != null &&
            drop !is GritDropResult.Spawned &&
            drop != GritDropResult.Parked
        ) {
            GnasherMachine.restoreFailedDrop(state, pulse)
        }
        blockState.markNeedsSaving(store)
        return GnasherPulseActionResult(pulse, drop)
    }

    fun kickGnasher(
        state: GnasherState,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
    ): Boolean {
        if (!GnasherMachine.kick(state)) return false
        blockState.markNeedsSaving(store)
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

    private const val WRENCH_SCRAP_QUANTITY = 3
}

internal sealed interface WrenchActionResult {
    data object NotMachine : WrenchActionResult

    data object RemovalFailed : WrenchActionResult

    data object ForeignCrew : WrenchActionResult

    data object HasContents : WrenchActionResult

    data class Learned(
        val machineNameKey: String,
        val page: NotebookPage,
        val scrapsDropped: Boolean,
        val pageGranted: Boolean,
        val burnTimeLost: Boolean,
    ) : WrenchActionResult

    data class Packed(
        val machineNameKey: String,
        val itemDropped: Boolean,
        val burnTimeLost: Boolean,
    ) : WrenchActionResult
}

private fun machineReadout(
    name: String,
    member: EnergyMember,
): String =
    "$name: ${member.stored} Fizz, network ${member.networkId}, " +
        "production ${member.production}, delivered ${member.delivered}"

private fun gnasherReadout(
    state: GnasherState,
    member: EnergyMember,
): String =
    if (state.jammed) {
        "Jammed! Kick it."
    } else {
        "Gnasher Pit: ${GritTable.queuedOreCount(state.oreQueue)}/${GnasherMachine.HOPPER_CAPACITY} ore, " +
            "${state.progressPulses}/${GnasherMachine.PULSES_PER_ORE} pulses, " +
            "${member.delivered} Fizz delivered."
    }

private fun InteractionContext.depositOre(
    heldItem: ItemStack,
    state: GnasherState,
    blockState: BlockModule.BlockStateInfo,
    store: Store<ChunkStore>,
): Boolean {
    val container = heldItemContainer ?: return false
    val slot = heldItemSlot.toShort()
    val slotItem = container.getItemStack(slot) ?: return false
    if (
        slotItem.itemId != heldItem.itemId ||
        slotItem.quantity != heldItem.quantity ||
        !GnasherMachine.canDeposit(state, heldItem.itemId)
    ) {
        return false
    }
    val replacement = if (slotItem.quantity == 1) null else slotItem.withQuantity(slotItem.quantity - 1)
    if (!container.setItemStackForSlot(slot, replacement).succeeded()) return false
    if (MachineActions.depositGnasher(state, blockState, store, heldItem.itemId)) return true
    container.setItemStackForSlot(slot, slotItem)
    return false
}

private fun InteractionContext.addFuelTo(
    heldItem: ItemStack,
    state: BurnerState,
    member: EnergyMember,
    blockState: BlockModule.BlockStateInfo,
    store: Store<ChunkStore>,
    tick: Long,
) {
    val container = heldItemContainer ?: return
    val slot = heldItemSlot.toShort()
    val slotItem = container.getItemStack(slot) ?: return
    if (
        slotItem.itemId != heldItem.itemId ||
        slotItem.quantity != heldItem.quantity ||
        !BurnerMachine.acceptsFuel(heldItem.itemId)
    ) {
        return
    }
    val replacement =
        if (slotItem.quantity == 1) {
            null
        } else {
            slotItem.withQuantity(slotItem.quantity - 1)
        }
    if (!container.setItemStackForSlot(slot, replacement).succeeded()) return
    MachineActions.addFuel(state, member, blockState, store, tick, heldItem.itemId)
}

private fun InteractionContext.replaceHeldStack(
    heldItem: ItemStack?,
    itemId: String,
): Boolean {
    if (heldItem == null || heldItem.quantity != 1) return false
    val container = heldItemContainer ?: return false
    val slot = heldItemSlot.toShort()
    val slotItem = container.getItemStack(slot) ?: return false
    if (slotItem.itemId != heldItem.itemId || slotItem.quantity != heldItem.quantity) return false
    return container.setItemStackForSlot(slot, ItemStack(itemId, 1)).succeeded()
}

private fun InteractionContext.consumeHeld(quantity: Int = 1): Boolean {
    val container = heldItemContainer ?: return false
    val slot = heldItemSlot.toShort()
    val held = container.getItemStack(slot) ?: return false
    if (held.quantity < quantity) return false
    val replacement = if (held.quantity == quantity) null else held.withQuantity(held.quantity - quantity)
    return container.setItemStackForSlot(slot, replacement).succeeded()
}

private fun InteractionContext.addBoilerFuel(
    heldItem: ItemStack,
    state: BoilerState,
    blockState: BlockModule.BlockStateInfo,
    store: Store<ChunkStore>,
) {
    if (GutterBoilerActions.fuelBurnTicks(heldItem.itemId) <= 0) return
    val container = heldItemContainer ?: return
    val slot = heldItemSlot.toShort()
    val stack = container.getItemStack(slot) ?: return
    if (stack.itemId != heldItem.itemId || stack.quantity != heldItem.quantity || !consumeHeld()) return
    GutterBoilerActions.addFuel(state, heldItem.itemId)
    blockState.markNeedsSaving(store)
}

private fun InteractionContext.useAnchor(
    world: World,
    state: AnchorState,
    blockState: BlockModule.BlockStateInfo,
    store: Store<ChunkStore>,
    position: BlockPos,
    heldItem: ItemStack?,
): String {
    if (heldItem == null) {
        return "Wakebell Anchor: ${state.chargeSeconds} seconds remaining."
    }
    if (heldItem.itemId != CraftoryIds.FIZZ_CHARGE) {
        return "Wakebell Anchor: ${state.chargeSeconds} seconds remaining."
    }
    val maxChargeMinutes = CraftoryCore.anchorMaxChargeMinutes()
    val nextCharge = AnchorChargeMath.tryAddCharge(state.chargeSeconds, maxChargeMinutes)
    if (nextCharge == null) {
        return if (!AnchorChargeMath.canCharge(maxChargeMinutes)) {
            "Wakebell Anchors are disabled."
        } else {
            "Wakebell Anchor is fully charged."
        }
    }
    val container = heldItemContainer ?: return "Could not access the held Fizz Charge."
    val slot = heldItemSlot.toShort()
    val slotItem = container.getItemStack(slot) ?: return "Could not access the held Fizz Charge."
    if (slotItem.itemId != CraftoryIds.FIZZ_CHARGE || slotItem.quantity != heldItem.quantity) {
        return "Held Fizz Charge changed before it could be used."
    }
    val replacement = if (slotItem.quantity == 1) null else slotItem.withQuantity(slotItem.quantity - 1)
    if (!container.setItemStackForSlot(slot, replacement).succeeded()) {
        return "Could not consume the Fizz Charge."
    }
    state.chargeSeconds = nextCharge
    MachineActions.activateAnchor(world, state, blockState, store, position)
    return "Wakebell Anchor charged to ${state.chargeSeconds} seconds."
}

private const val SAVE_INTERVAL_TICKS = 20L

private val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryTech")
