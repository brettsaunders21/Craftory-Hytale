package studio.craftory.tech.machine

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.spatial.SpatialResource
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.EntityModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockGeometry
import studio.craftory.core.api.multiblock.MultiblockQuality
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.core.api.multiblock.WorldPart
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.straightenTetherStake

/** Restarts the heart clock on load and ejects the fuel slot when the controller block is removed. */
internal class JunkHeartLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(JunkHeartState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        commandBuffer.getComponent(ref, JunkHeartState.type)?.lastAdvancedTick = store.externalData.world.tick
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != RemoveReason.REMOVE) return
        val state = commandBuffer.getComponent(ref, JunkHeartState.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (state.fuelCount <= 0 || state.fuelItemId.isBlank()) return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val itemId = state.fuelItemId
        val quantity = state.fuelCount
        state.fuelCount = 0
        state.fuelItemId = ""
        val world = store.externalData.world
        world.execute { JunkHeartActions.ejectFuel(world, position, itemId, quantity) }
    }
}

/** Despawns a controller's ghost preview entity when the controller is broken or its chunk unloads. */
internal class GhostPreviewLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(MultiblockController.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) = Unit

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val controllerPos = BlockPos(position.x, position.y, position.z)
        if (!store.getResource(AssemblyViewIndex.type).hasPreview(controllerPos)) return
        val world = store.externalData.world
        world.execute { GhostPreviews.hide(world, controllerPos) }
    }
}

internal class JunkHeartSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            JunkHeartState.type,
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
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, JunkHeartState.type) ?: return
        val controller = chunk.getComponent(index, MultiblockController.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        val tick = world.tick
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            state.lastAdvancedTick = tick
            return
        }
        if (state.burning && controller.isFormed && ++state.smokeTicks >= SMOKE_INTERVAL_TICKS) {
            state.smokeTicks = 0
            emitChimneySmoke(blockState, commandBuffer, controller, world)
        }
        if (!CraftoryCore.isEnergyTick(store)) return
        if (JunkHeartMachine.advanceTo(state, tick, controller.state)) member.unsaved = true
        setBlockState(world, blockState, commandBuffer, if (state.burning && controller.isFormed) "On" else "Off")
        member.production = JunkHeartMachine.productionPerSolveTick(state, controller.state, controller.quality)
        member.persist(blockState, commandBuffer, tick, HEART_SAVE_INTERVAL_TICKS, immediate = false)
    }

    private fun emitChimneySmoke(
        blockState: BlockModule.BlockStateInfo,
        commandBuffer: CommandBuffer<ChunkStore>,
        controller: MultiblockController,
        world: World,
    ) {
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val chimneyTop =
            MultiblockGeometry.worldPosition(
                BlockPos(position.x, position.y, position.z),
                JunkHeartTemplate.CHIMNEY_TOP,
                controller.rotation,
            )
        val top = MachineVents.position(CraftoryIds.JUNK_HEART, chimneyTop.x, chimneyTop.y, chimneyTop.z)
        world.execute {
            ParticleUtil.spawnParticleEffect(
                CraftoryIds.CHIMNEY_SMOKE_PARTICLE,
                top,
                world.entityStore.store,
            )
        }
    }

    private companion object {
        const val HEART_SAVE_INTERVAL_TICKS = 20L
        const val SMOKE_INTERVAL_TICKS = 20
        const val HALF = 0.5
    }
}

/**
 * Ghost frame fallback and kick-start gauge. Throttled like AnchorSystem: each controller only
 * scans on its refresh tick. Each player is shown the nearest ASSEMBLING or READY controller only.
 */
internal class MultiblockAssemblySystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(MultiblockController.type, BlockModule.BlockStateInfo.getComponentType())

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
        val controller = chunk.getComponent(index, MultiblockController.type) ?: return
        val world = store.externalData.world
        val tick = world.tick
        if (controller.state != MultiblockState.ASSEMBLING && controller.state != MultiblockState.READY_TO_START) {
            if (tick % GHOST_INTERVAL_TICKS == 0L) hideStalePreview(chunk, index, store, commandBuffer)
            return
        }
        val interval =
            if (controller.state ==
                MultiblockState.ASSEMBLING
            ) {
                GHOST_INTERVAL_TICKS
            } else {
                GAUGE_INTERVAL_TICKS
            }
        if (tick % interval != 0L) return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val controllerPos = BlockPos(position.x, position.y, position.z)
        val views = store.getResource(AssemblyViewIndex.type)
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            if (views.hasPreview(controllerPos)) world.execute { GhostPreviews.hide(world, controllerPos) }
            return
        }
        val ref = chunk.getReferenceTo(index)
        views.seen(controllerPos, tick)
        world.execute {
            if (!ref.isValid) return@execute
            JunkHeartActions.showAssembly(world, ref, controllerPos)
        }
    }

    /** Catches previews left by transitions that skip the hammer path, such as a hand-placed last part. */
    private fun hideStalePreview(
        chunk: ArchetypeChunk<ChunkStore>,
        index: Int,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val views = store.getResource(AssemblyViewIndex.type)
        if (!views.hasAnyPreview()) return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val controllerPos = BlockPos(position.x, position.y, position.z)
        if (!views.hasPreview(controllerPos)) return
        val world = store.externalData.world
        world.execute { GhostPreviews.hide(world, controllerPos) }
    }

    private companion object {
        const val GHOST_INTERVAL_TICKS = 20L
        const val GAUGE_INTERVAL_TICKS = 10L
    }
}

class CraftoryHammerUseInteraction : SimpleBlockInteraction() {
    override fun interactWithBlock(
        world: World,
        commandBuffer: CommandBuffer<EntityStore>,
        type: InteractionType,
        context: InteractionContext,
        heldItem: ItemStack?,
        position: Vector3i,
        cooldownHandler: CooldownHandler,
    ) {
        if (heldItem?.itemId != CraftoryIds.TINKERS_HAMMER) return
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        if (straightenTetherStake(world, position, player)) return
        val store = world.chunkStore.store
        val target = BlockPos(position.x, position.y, position.z)
        val ref =
            BlockModule
                .getBlockEntity(world, position.x, position.y, position.z)
                ?.takeIf { store.getComponent(it, MultiblockController.type) != null }
                ?: CraftoryCore.multiblocks().controllerAt(store, target)
                ?: return player.sendMessage(Message.translation("server.craftory.hammer.not-controller"))
        val owner = store.getComponent(ref, MachineOwner.type)?.placerUuid
        if (!MachineActions.mayUseWrench(owner, player.uuid, player, CraftoryCore.crewService())) {
            player.sendMessage(Message.translation("server.craftory.hammer.foreign-crew"))
            return
        }
        val inventory =
            InventoryComponent.getCombined(
                commandBuffer,
                context.entity,
                *InventoryComponent.HOTBAR_STORAGE_BACKPACK,
            )
        val result =
            JunkHeartActions.hammer(
                world,
                store,
                ref,
                take = { itemId -> inventory.removeItemStack(ItemStack(itemId, 1)).succeeded() },
                refund = { itemId -> inventory.addItemStack(ItemStack(itemId, 1)) },
            )
        player.sendMessage(JunkHeartActions.hammerMessage(result))
        world.execute { if (ref.isValid) GhostPreviews.sync(world, ref) }
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
                    CraftoryHammerUseInteraction::class.java,
                    ::CraftoryHammerUseInteraction,
                    SimpleBlockInteraction.CODEC,
                ).build()
    }
}

internal sealed interface HammerResult {
    data object NotAssembling : HammerResult

    data object Ready : HammerResult

    data class Needs(
        val part: WorldPart,
    ) : HammerResult

    data class Blocked(
        val part: WorldPart,
    ) : HammerResult

    data class Placed(
        val part: WorldPart,
        val next: WorldPart?,
    ) : HammerResult
}

internal sealed interface KickResult {
    data object NotReady : KickResult

    data object ForeignCrew : KickResult

    data class Formed(
        val quality: MultiblockQuality,
        val errorSeconds: Double,
    ) : KickResult
}

@Suppress("TooManyFunctions")
internal object JunkHeartActions {
    private const val VIEW_RADIUS = 16.0
    private const val HALF = 0.5

    /** Hammers the next missing part in. The item is debited before the world changes. */
    fun hammer(
        world: World,
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        take: (String) -> Boolean,
        refund: (String) -> Unit,
    ): HammerResult {
        val controller =
            store.getComponent(controllerRef, MultiblockController.type) ?: return HammerResult.NotAssembling
        val registry = CraftoryCore.multiblocks()
        when (controller.state) {
            MultiblockState.READY_TO_START -> return HammerResult.Ready
            MultiblockState.FORMED -> return HammerResult.NotAssembling
            else -> Unit
        }
        val part = registry.missingParts(store, controllerRef).firstOrNull() ?: return readyOrIdle(store, controllerRef)
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(part.position.x, part.position.y, part.position.z)
                ?: return HammerResult.Blocked(part)
        val section =
            store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return HammerResult.Blocked(part)
        if (section.get(part.position.x, part.position.y, part.position.z) !=
            BlockType.EMPTY_ID
        ) {
            return HammerResult.Blocked(part)
        }
        val blockType = BlockType.fromString(part.part.placedBlockId) ?: return HammerResult.Blocked(part)
        if (!take(part.part.itemId)) return HammerResult.Needs(part)
        val blockId = BlockType.getAssetMap().getIndex(blockType.id)
        val placed =
            BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                part.position.x,
                part.position.y,
                part.position.z,
                blockId,
                blockType,
                0,
                0,
                SetBlockSettings.NONE,
            )
        if (!placed) {
            refund(part.part.itemId)
            return HammerResult.Blocked(part)
        }
        playEffects(world, part.position, blockId)
        registry.recheck(store, controllerRef)
        if (controller.state == MultiblockState.READY_TO_START) return HammerResult.Placed(part, null)
        return HammerResult.Placed(part, registry.missingParts(store, controllerRef).firstOrNull())
    }

    fun kick(
        world: World,
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        tick: Long,
        mayOperate: Boolean,
    ): KickResult {
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return KickResult.NotReady
        if (controller.state != MultiblockState.READY_TO_START) return KickResult.NotReady
        if (!mayOperate) return KickResult.ForeignCrew
        val phase = KickStartTiming.phaseSeconds(tick - controller.readySinceTick, world.tps)
        val error = KickStartTiming.errorSeconds(phase)
        val quality = KickStartTiming.quality(error)
        if (!CraftoryCore.multiblocks().completeKickStart(store, controllerRef, quality)) return KickResult.NotReady
        val position = CraftoryCore.multiblocks().controllerPosition(store, controllerRef) ?: return KickResult.NotReady
        playEffects(world, position, null)
        world.execute { GhostPreviews.hide(world, position) }
        return KickResult.Formed(quality, error)
    }

    fun addFuel(
        context: InteractionContext,
        heldItem: ItemStack,
        state: JunkHeartState,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
    ): Boolean {
        if (!JunkHeartMachine.acceptsFuel(state, heldItem.itemId)) return false
        val container = context.heldItemContainer ?: return false
        val slot = context.heldItemSlot.toShort()
        val slotItem = container.getItemStack(slot) ?: return false
        if (slotItem.itemId != heldItem.itemId || slotItem.quantity != heldItem.quantity) return false
        val replacement = if (slotItem.quantity == 1) null else slotItem.withQuantity(slotItem.quantity - 1)
        if (!container.setItemStackForSlot(slot, replacement).succeeded()) return false
        JunkHeartMachine.addFuel(state, heldItem.itemId)
        blockState.markNeedsSaving(store)
        return true
    }

    fun readout(
        state: JunkHeartState,
        controller: MultiblockController,
        member: EnergyMember,
    ): Message =
        Message
            .translation("server.craftory.heart.readout")
            .param(
                "state",
                Message.translation("server.craftory.multiblock.state.${controller.state.name.lowercase()}"),
            ).param(
                "quality",
                Message.translation("server.craftory.multiblock.quality.${controller.quality.name.lowercase()}"),
            ).param("fuel", state.fuelCount)
            .param("production", member.production)

    fun hammerMessage(result: HammerResult): Message =
        when (result) {
            HammerResult.NotAssembling -> {
                Message.translation("server.craftory.hammer.not-assembling")
            }

            HammerResult.Ready -> {
                Message.translation("server.craftory.hammer.ready")
            }

            is HammerResult.Needs -> {
                Message.translation("server.craftory.hammer.needs").param("part", partName(result.part))
            }

            is HammerResult.Blocked -> {
                Message.translation("server.craftory.hammer.blocked").param("part", partName(result.part))
            }

            is HammerResult.Placed -> {
                val next = result.next
                if (next == null) {
                    Message.translation("server.craftory.hammer.ready")
                } else {
                    Message
                        .translation("server.craftory.hammer.placed")
                        .param("part", partName(result.part))
                        .param("next", partName(next))
                }
            }
        }

    fun kickMessage(result: KickResult): Message? =
        when (result) {
            KickResult.NotReady -> {
                null
            }

            KickResult.ForeignCrew -> {
                Message.translation("server.craftory.hammer.foreign-crew")
            }

            is KickResult.Formed -> {
                Message
                    .translation("server.craftory.multiblock.kicked")
                    .param(
                        "quality",
                        Message.translation("server.craftory.multiblock.quality.${result.quality.name.lowercase()}"),
                    ).param("error", "%.2f".format(result.errorSeconds))
            }
        }

    fun showAssembly(
        world: World,
        controllerRef: Ref<ChunkStore>,
        controllerPos: BlockPos,
    ) {
        val store = world.chunkStore.store
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return
        val entities = world.entityStore.store
        val center = Vector3d(controllerPos.x + HALF, controllerPos.y + HALF, controllerPos.z + HALF)
        val collected: MutableList<Ref<EntityStore>> = SpatialResource.getThreadLocalReferenceList<EntityStore>()
        val players: SpatialResource<Ref<EntityStore>, EntityStore> =
            entities.getResource(EntityModule.get().playerSpatialResourceType)
        players.spatialStructure.collect(center, VIEW_RADIUS, collected)
        val viewers =
            collected.filter { player ->
                val position =
                    entities.getComponent(player, TransformComponent.getComponentType())?.position
                        ?: return@filter false
                nearestController(store, position) == controllerPos
            }
        if (viewers.isEmpty()) return GhostPreviews.hide(world, controllerPos)
        if (controller.state == MultiblockState.READY_TO_START) {
            GhostPreviews.hide(world, controllerPos)
            val phase = KickStartTiming.phaseSeconds(world.tick - controller.readySinceTick, world.tps)
            val gauge =
                Message
                    .translation(
                        "server.craftory.multiblock.gauge",
                    ).param("gauge", KickStartTiming.gauge(phase))
            viewers.forEach { entities.getComponent(it, PlayerRef.getComponentType())?.sendMessage(gauge) }
            return
        }
        val missing = CraftoryCore.multiblocks().missingParts(store, controllerRef)
        val mode = CraftoryCore.ghostMode()
        if (mode.showsPreview) {
            GhostPreviews.show(world, controllerPos, missing)
        } else {
            GhostPreviews.hide(world, controllerPos)
        }
        if (!mode.showsParticles) return
        missing.forEach { part ->
            ParticleUtil.spawnParticleEffect(
                CraftoryIds.GHOST_PARTICLE,
                part.position.x + HALF,
                part.position.y + HALF,
                part.position.z + HALF,
                viewers,
                entities,
            )
        }
    }

    fun ejectFuel(
        world: World,
        position: Vector3i,
        itemId: String,
        quantity: Int,
    ) {
        val entities = world.entityStore.store
        val holder =
            ItemComponent.generateItemDrop(
                entities,
                ItemStack(itemId, quantity),
                Vector3d(position.x + HALF, position.y + 1.0, position.z + HALF),
                Rotation3f.IDENTITY,
                0f,
                0f,
                0f,
            ) ?: return
        entities.addEntity(holder, AddReason.SPAWN)
    }

    private fun nearestController(
        store: Store<ChunkStore>,
        position: Vector3d,
    ): BlockPos? =
        store
            .getResource(AssemblyViewIndex.type)
            .controllers(store.externalData.world.tick)
            .filter { it.distanceSquared(position) <= VIEW_RADIUS * VIEW_RADIUS }
            .minByOrNull { it.distanceSquared(position) }

    private fun BlockPos.distanceSquared(position: Vector3d): Double {
        val dx = x + HALF - position.x
        val dy = y + HALF - position.y
        val dz = z + HALF - position.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun readyOrIdle(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ): HammerResult =
        if (CraftoryCore.multiblocks().recheck(store, controllerRef) == MultiblockState.READY_TO_START) {
            HammerResult.Ready
        } else {
            HammerResult.NotAssembling
        }

    private fun partName(part: WorldPart): Message = Message.translation("server.items.${part.part.itemId}.name")

    private fun playEffects(
        world: World,
        position: BlockPos,
        blockId: Int?,
    ) {
        if (blockId != null) {
            BlockOperations.spawnBlockParticles(
                world.chunkStore,
                BlockType.EMPTY_ID,
                blockId,
                position.x,
                position.y,
                position.z,
                false,
            )
        }
        val sound = SoundEvent.getAssetMap().getIndex(CraftoryIds.HAMMER_SOUND)
        if (sound > 0) {
            SoundUtil.playSoundEvent3d(
                sound,
                SoundCategory.SFX,
                position.x + HALF,
                position.y + HALF,
                position.z + HALF,
                world.entityStore.store,
            )
        }
    }
}

/** Derived per-world cache of controllers that showed a ghost frame or gauge recently. */
internal class AssemblyViewIndex : com.hypixel.hytale.component.Resource<ChunkStore> {
    private val lastSeen = HashMap<BlockPos, Long>()

    override fun clone(): com.hypixel.hytale.component.Resource<ChunkStore> = AssemblyViewIndex()

    fun seen(
        position: BlockPos,
        tick: Long,
    ) {
        lastSeen[position] = tick
    }

    private val previews = HashMap<BlockPos, Preview>()

    fun preview(position: BlockPos): Preview? = previews[position]

    fun putPreview(
        position: BlockPos,
        preview: Preview,
    ) {
        previews[position] = preview
    }

    fun removePreview(position: BlockPos): Preview? = previews.remove(position)

    fun hasPreview(position: BlockPos): Boolean = previews.containsKey(position)

    fun hasAnyPreview(): Boolean = previews.isNotEmpty()

    data class Preview(
        val ref: Ref<EntityStore>,
        val parts: Set<BlockPos>,
    )

    fun controllers(tick: Long): List<BlockPos> {
        lastSeen.entries.removeIf { tick - it.value > STALE_TICKS }
        return lastSeen.keys.toList()
    }

    companion object {
        lateinit var type: com.hypixel.hytale.component.ResourceType<ChunkStore, AssemblyViewIndex>
        private const val STALE_TICKS = 40L
    }
}
