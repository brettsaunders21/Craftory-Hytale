package studio.craftory.tech.machine

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.notebook.NotebookPages

class ReceiptUseInteraction : SimpleInteraction() {
    override fun tick0(
        firstRun: Boolean,
        time: Float,
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler,
    ) {
        if (!firstRun) return
        val item = context.heldItem ?: return
        if (item.itemId != CraftoryIds.GRIZZLECOG_RECEIPT) return
        val playerRef = context.commandBuffer?.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val crews = CraftoryCore.crewService()
        if (crews.hasKnowledge(playerRef.uuid, NotebookPages.DELIVERY_PAGE_ID)) {
            playerRef.sendMessage(Message.translation("server.craftory.receipt.already-used"))
            return
        }
        val container = context.heldItemContainer ?: return
        val slot = context.heldItemSlot.toShort()
        val slotItem = container.getItemStack(slot) ?: return
        if (slotItem.itemId != item.itemId || slotItem.quantity != item.quantity) return
        val replacement = if (slotItem.quantity == 1) null else slotItem.withQuantity(slotItem.quantity - 1)
        if (!container.setItemStackForSlot(slot, replacement).succeeded()) return
        if (!crews.grantKnowledge(playerRef.uuid, NotebookPages.DELIVERY_PAGE_ID)) {
            container.setItemStackForSlot(slot, slotItem)
            playerRef.sendMessage(Message.translation("server.craftory.receipt.already-used"))
            return
        }
        playerRef.sendMessage(Message.translation("server.craftory.receipt.granted"))
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
        val CODEC: BuilderCodec<ReceiptUseInteraction> =
            BuilderCodec
                .builder(
                    ReceiptUseInteraction::class.java,
                    ::ReceiptUseInteraction,
                    SimpleInteraction.CODEC,
                ).build()
    }
}
