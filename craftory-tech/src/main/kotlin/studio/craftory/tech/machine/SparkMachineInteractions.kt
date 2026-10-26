package studio.craftory.tech.machine

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.CradleActions

@Suppress("MaxLineLength")
class CradleUseInteraction : SimpleBlockInteraction() {
    override fun interactWithBlock(
        world: World,
        commandBuffer: CommandBuffer<EntityStore>,
        type: InteractionType,
        context: InteractionContext,
        heldItem: ItemStack?,
        position: Vector3i,
        cooldownHandler: CooldownHandler,
    ) {
        val jar = heldItem ?: return
        if (jar.itemId != CraftoryIds.GOB_JAR_FILLED) return
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
        val store = world.chunkStore.store
        val owner = store.getComponent(ref, MachineOwner.type)?.placerUuid
        if (!mayUseMachine(owner, player.uuid, player, CraftoryCore.crewService())) {
            player.sendMessage(Message.translation("server.craftory.wrench.foreign-crew"))
            return
        }
        val container = context.heldItemContainer ?: return
        val slot = context.heldItemSlot.toShort()
        val original = container.getItemStack(slot) ?: return
        if (original.itemId != CraftoryIds.GOB_JAR_FILLED || original.quantity != 1) return
        val replacement =
            ItemStack(CraftoryIds.GOB_JAR, 1)
        if (!container.setItemStackForSlot(slot, replacement).succeeded()) return
        if (CradleActions.seat(
                world,
                studio.craftory.core.api
                    .BlockPos(position.x, position.y, position.z),
                player.uuid,
                original,
                CraftoryCore.crewService(),
            ) ==
            null
        ) {
            container.setItemStackForSlot(slot, original)
            return
        }
        player.sendMessage(Message.raw("The Glimmerwell settles into the cradle."))
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
        val CODEC: BuilderCodec<CradleUseInteraction> =
            BuilderCodec
                .builder(
                    CradleUseInteraction::class.java,
                    ::CradleUseInteraction,
                    SimpleBlockInteraction.CODEC,
                ).build()
    }
}

@Suppress("MaxLineLength")
class ResonatorUseInteraction : SimpleBlockInteraction() {
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
        val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
        val state = world.chunkStore.store.getComponent(ref, ResonatorState.type) ?: return
        val blockState =
            world.chunkStore.store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val owner = world.chunkStore.store.getComponent(ref, MachineOwner.type)?.placerUuid
        if (!mayUseMachine(owner, player.uuid, player, CraftoryCore.crewService())) {
            player.sendMessage(Message.translation("server.craftory.wrench.foreign-crew"))
            return
        }
        if (heldItem == null) {
            state.tuning =
                when (state.tuning) {
                    25 -> 30
                    30 -> 35
                    35 -> 40
                    else -> 25
                }
            blockState.markNeedsSaving(world.chunkStore.store)
            player.sendMessage(Message.raw("Resonator tuning: ${state.tuning}%"))
            return
        }
        if (heldItem.itemId !in SHARD_ITEMS || (state.crystalItemId.isNotEmpty() && !state.clouded)) return
        val container = context.heldItemContainer ?: return
        val slot = context.heldItemSlot.toShort()
        val original = container.getItemStack(slot) ?: return
        if (original.itemId !in SHARD_ITEMS) return
        val replacement = if (original.quantity > 1) original.withQuantity(original.quantity - 1) else null
        if (!container.setItemStackForSlot(slot, replacement).succeeded()) return
        state.crystalItemId = original.itemId
        state.conversions = 0
        state.lastSavedConversions = 0
        state.clouded = false
        blockState.markNeedsSaving(world.chunkStore.store)
        player.sendMessage(Message.raw("The crystal begins to resonate at ${state.tuning}%."))
    }

    override fun simulateInteractWithBlock(
        type: InteractionType,
        context: InteractionContext,
        heldItem: ItemStack?,
        world: World,
        position: Vector3i,
    ) = Unit

    companion object {
        private val SHARD_ITEMS = setOf(CraftoryIds.CYAN_CRYSTAL_SHARD, CraftoryIds.VOID_CRYSTAL_SHARD)

        @JvmField
        val CODEC: BuilderCodec<ResonatorUseInteraction> =
            BuilderCodec
                .builder(ResonatorUseInteraction::class.java, ::ResonatorUseInteraction, SimpleBlockInteraction.CODEC)
                .build()
    }
}
