package studio.craftory.tech.machine

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.machine.jank.BodgePart
import studio.craftory.tech.machine.jank.BodgeSlots

class BodgeUseInteraction : SimpleBlockInteraction() {
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
        val part = BodgePart.fromItemId(heldItem?.itemId) ?: return
        val partName = Message.translation("server.items.${part.itemId}.name")
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val movement = commandBuffer.getComponent(context.entity, MovementStatesComponent.getComponentType())
        if (movement?.movementStates?.let { it.crouching || it.forcedCrouching } != true) {
            player.sendMessage(Message.translation("server.craftory.bodge.hint"))
            return
        }
        val blockRef = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
        val store = world.chunkStore.store
        val slots = store.getComponent(blockRef, BodgeSlots.type) ?: return
        if (store.getComponent(blockRef, EnergyMember.type) == null) return
        val owner = store.getComponent(blockRef, MachineOwner.type)?.placerUuid
        if (!MachineActions.mayUseWrench(owner, player.uuid, player, CraftoryCore.crewService())) {
            player.sendMessage(Message.translation("server.craftory.wrench.foreign-crew"))
            return
        }
        val blockState = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (slots.contains(part.itemId)) {
            if (!returnPart(world, context.entity, part.itemId)) return
            slots.remove(part.itemId)
            blockState.markNeedsSaving(store)
            player.sendMessage(Message.translation("server.craftory.bodge.removed").param("part", partName))
            return
        }
        if (slots.itemIds().size == 2) {
            player.sendMessage(Message.translation("server.craftory.bodge.full"))
            return
        }
        val container = context.heldItemContainer ?: return
        val slot = context.heldItemSlot.toShort()
        val held = container.getItemStack(slot) ?: return
        if (held.itemId != part.itemId) return
        val replacement = if (held.quantity == 1) null else held.withQuantity(held.quantity - 1)
        if (!container.setItemStackForSlot(slot, replacement).succeeded()) return
        if (!slots.install(part.itemId)) {
            container.setItemStackForSlot(slot, held)
            return
        }
        blockState.markNeedsSaving(store)
        player.sendMessage(Message.translation("server.craftory.bodge.installed").param("part", partName))
    }

    private fun returnPart(
        world: World,
        playerRef: com.hypixel.hytale.component.Ref<EntityStore>,
        itemId: String,
    ): Boolean {
        val entityStore = world.entityStore.store
        val inventory =
            InventoryComponent.getCombined(
                entityStore,
                playerRef,
                *InventoryComponent.HOTBAR_STORAGE_BACKPACK,
            )
        if (inventory.addItemStack(ItemStack(itemId, 1)).succeeded()) return true
        val transform = entityStore.getComponent(playerRef, TransformComponent.getComponentType()) ?: return false
        val position = transform.position
        val holder =
            ItemComponent.generateItemDrop(
                entityStore,
                ItemStack(itemId, 1),
                Vector3d(position.x, position.y, position.z),
                Rotation3f.IDENTITY,
                0f,
                0f,
                0f,
            ) ?: return false
        return entityStore.addEntity(holder, AddReason.SPAWN)?.isValid == true
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
        val CODEC: BuilderCodec<BodgeUseInteraction> =
            BuilderCodec
                .builder(
                    BodgeUseInteraction::class.java,
                    ::BodgeUseInteraction,
                    SimpleBlockInteraction.CODEC,
                ).build()
    }
}
