package studio.craftory.tech.well

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.inventory.container.ItemContainer
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.modules.physics.component.Velocity
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.tech.CraftoryIds

class GobJarUseInteraction : SimpleInteraction() {
    override fun tick0(
        firstRun: Boolean,
        time: Float,
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler,
    ) {
        if (!firstRun) return
        val item = context.heldItem ?: return
        if (item.itemId != CraftoryIds.GOB_JAR) return
        val commandBuffer = context.commandBuffer ?: return
        val playerRef = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val container = context.heldItemContainer ?: return
        val slot = context.heldItemSlot.toShort()
        val slotItem = container.getItemStack(slot) ?: return
        if (slotItem.itemId != CraftoryIds.GOB_JAR) return
        catchJar(context, commandBuffer, playerRef, container, slot)
    }

    private fun catchJar(
        context: InteractionContext,
        commandBuffer: CommandBuffer<EntityStore>,
        playerRef: PlayerRef,
        container: ItemContainer,
        slot: Short,
    ) {
        val originalStack = container.getItemStack(slot) ?: return
        if (originalStack.itemId != CraftoryIds.GOB_JAR || originalStack.quantity <= 0) return
        val position = targetPosition(context, commandBuffer)
        if (position == null) {
            playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
            return
        }
        val world = commandBuffer.externalData.world
        val ref = wellRef(world, position)
        if (ref == null) {
            playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
            return
        }
        val crews = CraftoryCore.crewService()
        val result =
            JarActions.checkCatch(
                ref,
                playerRef.uuid,
                crews,
                world.chunkStore.store,
                position.pack().toString(),
            )
        handleCatchResult(
            result,
            world,
            position,
            ref,
            playerRef,
            crews,
            container,
            slot,
            originalStack,
        )
    }

    private fun handleCatchResult(
        result: JarCatchResult,
        world: World,
        position: BlockPos,
        ref: Ref<ChunkStore>,
        playerRef: PlayerRef,
        crews: CrewService,
        container: ItemContainer,
        slot: Short,
        originalStack: ItemStack,
    ) {
        when (result) {
            JarCatchResult.NotWell -> {
                playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
            }

            JarCatchResult.TooBig -> {
                playerRef.sendMessage(Message.translation("server.craftory.jar.too-big"))
            }

            JarCatchResult.TierLocked -> {
                playerRef.sendMessage(Message.translation("server.craftory.tier.locked"))
            }

            JarCatchResult.ForeignCrew -> {
                playerRef.sendMessage(Message.translation("server.craftory.jar.foreign-crew"))
            }

            is JarCatchResult.Caught -> {
                if (!advanceCapture(world, playerRef, ref, result.wellId)) return
                commitJarCatch(result, world, position, ref, playerRef, crews, container, slot, originalStack)
            }
        }
    }

    private fun advanceCapture(
        world: World,
        playerRef: PlayerRef,
        wellRef: Ref<ChunkStore>,
        wellId: String,
    ): Boolean {
        val state = world.entityStore.store.getResource(WellCaptureState.type)
        val current = state.progressByPlayer[playerRef.uuid]
        val tick = world.tick
        if (current == null || current.wellId != wellId) {
            state.progressByPlayer[playerRef.uuid] = CaptureProgress(wellId, 1, tick)
            playerRef.sendMessage(Message.translation("server.craftory.jar.capture-press").param("press", 1))
            return false
        }
        if (!WellCaptureRules.capturePressIntervalValid(tick - current.lastPressTick)) {
            state.progressByPlayer.remove(playerRef.uuid)
            resetStakeAndKnockBack(world, wellRef, playerRef, wellId)
            playerRef.sendMessage(Message.translation("server.craftory.jar.capture-missed"))
            return false
        }
        val pressCount = current.pressCount + 1
        if (!WellCaptureRules.captureComplete(pressCount)) {
            state.progressByPlayer[playerRef.uuid] = CaptureProgress(wellId, pressCount, tick)
            playerRef.sendMessage(
                Message.translation("server.craftory.jar.capture-press").param("press", pressCount),
            )
            return false
        }
        state.progressByPlayer.remove(playerRef.uuid)
        return true
    }

    private fun resetStakeAndKnockBack(
        world: World,
        wellRef: Ref<ChunkStore>,
        playerRef: PlayerRef,
        wellId: String,
    ) {
        val store = world.chunkStore.store
        if (store.getComponent(wellRef, Glimmerwell.type) == null) return
        val wellPosition = BlockPos.unpack(wellId.toLongOrNull() ?: return)
        val packedWellId = wellPosition.pack().toString()
        markStakeLeaning(world, wellPosition, packedWellId)
        knockBackAfterMiss(world, playerRef, wellPosition)
    }

    private fun commitJarCatch(
        caught: JarCatchResult.Caught,
        world: World,
        position: BlockPos,
        ref: Ref<ChunkStore>,
        playerRef: PlayerRef,
        crews: CrewService,
        container: ItemContainer,
        slot: Short,
        originalStack: ItemStack,
    ) {
        val committed =
            if (originalStack.quantity == 1) {
                commitSingleJarCatch(
                    world,
                    position,
                    ref,
                    playerRef,
                    crews,
                    container,
                    slot,
                    originalStack,
                    caught,
                )
            } else {
                commitStackedJarCatch(
                    world,
                    position,
                    ref,
                    playerRef,
                    crews,
                    container,
                    slot,
                    originalStack,
                    caught,
                )
            }
        if (!committed) return
        playerRef.sendMessage(
            Message
                .translation("server.craftory.jar.caught")
                .param("temperament", caught.temperament.name),
        )
    }

    private fun commitSingleJarCatch(
        world: World,
        position: BlockPos,
        ref: Ref<ChunkStore>,
        playerRef: PlayerRef,
        crews: CrewService,
        container: ItemContainer,
        slot: Short,
        originalStack: ItemStack,
        caught: JarCatchResult.Caught,
    ): Boolean {
        val filledJar = JarActions.filledJar(caught)
        if (!container.setItemStackForSlot(slot, filledJar).succeeded()) return false
        if (JarActions.commitCatch(world, ref, position, playerRef.uuid, caught, crews)) {
            return true
        }
        container.setItemStackForSlot(slot, originalStack)
        playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
        return false
    }

    private fun commitStackedJarCatch(
        world: World,
        position: BlockPos,
        ref: Ref<ChunkStore>,
        playerRef: PlayerRef,
        crews: CrewService,
        container: ItemContainer,
        slot: Short,
        originalStack: ItemStack,
        caught: JarCatchResult.Caught,
    ): Boolean {
        val filledJar = JarActions.filledJar(caught)
        val inventory =
            playerRef.reference?.let {
                InventoryComponent.getCombined(
                    world.entityStore.store,
                    it,
                    *InventoryComponent.HOTBAR_STORAGE_BACKPACK,
                )
            }
        if (inventory?.canAddItemStack(filledJar) != true) {
            playerRef.sendMessage(Message.translation("server.craftory.jar.full"))
            return false
        }
        val remainingJars = originalStack.withQuantity(originalStack.quantity - 1)
        if (!container.setItemStackForSlot(slot, remainingJars).succeeded()) return false
        if (!inventory.addItemStack(filledJar).succeeded()) {
            container.setItemStackForSlot(slot, originalStack)
            playerRef.sendMessage(Message.translation("server.craftory.jar.full"))
            return false
        }
        if (JarActions.commitCatch(world, ref, position, playerRef.uuid, caught, crews)) {
            return true
        }
        inventory.removeItemStack(filledJar)
        container.setItemStackForSlot(slot, originalStack)
        playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
        return false
    }

    override fun simulateTick0(
        firstRun: Boolean,
        time: Float,
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler,
    ) = Unit

    companion object {
        @JvmField
        val CODEC: BuilderCodec<GobJarUseInteraction> =
            BuilderCodec
                .builder(GobJarUseInteraction::class.java, ::GobJarUseInteraction, SimpleInteraction.CODEC)
                .build()
    }
}

private fun targetPosition(
    context: InteractionContext,
    commandBuffer: CommandBuffer<EntityStore>,
): BlockPos? =
    TargetUtil
        .getTargetBlock(context.entity, 8.0, commandBuffer)
        ?.let { BlockPos(it.x, it.y, it.z) }

private fun wellRef(
    world: World,
    position: BlockPos,
): Ref<ChunkStore>? {
    val sectionRef =
        world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z) ?: return null
    return BlockModule.getBlockEntity(
        world.chunkStore.store,
        sectionRef,
        position.x,
        position.y,
        position.z,
    )
}

private fun markStakeLeaning(
    world: World,
    wellPosition: BlockPos,
    packedWellId: String,
) {
    val stake = findStakeToLean(world, wellPosition, packedWellId) ?: return
    val store = world.chunkStore.store
    val state = store.getComponent(stake, TetherStakeState.type) ?: return
    state.leaning = true
    store.getComponent(stake, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
}

private fun findStakeToLean(
    world: World,
    center: BlockPos,
    packedWellId: String,
): Ref<ChunkStore>? {
    val store = world.chunkStore.store
    for (x in center.x - 6..center.x + 6) {
        for (y in center.y - 2..center.y + 2) {
            for (z in center.z - 6..center.z + 6) {
                val stake = stakeRefAt(world, store, x, y, z, packedWellId)
                if (stake != null) return stake
            }
        }
    }
    return null
}

private fun stakeRefAt(
    world: World,
    store: com.hypixel.hytale.component.Store<ChunkStore>,
    x: Int,
    y: Int,
    z: Int,
    packedWellId: String,
): Ref<ChunkStore>? {
    val section = world.chunkStore.getChunkSectionReferenceAtBlock(x, y, z) ?: return null
    val stake = BlockModule.getBlockEntity(store, section, x, y, z) ?: return null
    return stake.takeIf { store.getComponent(it, TetherStakeState.type)?.wellId == packedWellId }
}

private fun knockBackAfterMiss(
    world: World,
    playerRef: PlayerRef,
    wellPosition: BlockPos,
) {
    val entity = playerRef.reference ?: return
    val store = world.entityStore.store
    val transform = store.getComponent(entity, TransformComponent.getComponentType()) ?: return
    val velocity = store.getComponent(entity, Velocity.getComponentType()) ?: return
    val dx = transform.position.x - wellPosition.x - 0.5
    val dz = transform.position.z - wellPosition.z - 0.5
    val length = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
    velocity.addVelocity(dx / length * 0.6, 0.35, dz / length * 0.6)
}
