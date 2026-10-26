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
import studio.craftory.core.api.transport.TransportColour
import studio.craftory.tech.CraftoryIds

class ClawUseInteraction : SimpleBlockInteraction() {
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
        val state = store.getComponent(ref, SnatcherClawState.type) ?: return
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val owner = store.getComponent(ref, MachineOwner.type)?.placerUuid
        if (!mayUseMachine(owner, player.uuid, player, CraftoryCore.crewService())) {
            player.sendMessage(Message.translation("server.craftory.claw.foreign-crew"))
            return
        }
        val colour = heldItem?.itemId?.let(::daubColour)
        if (heldItem == null) {
            state.filterItemId = ""
            state.filterColour = TransportColour.NONE.value
            player.sendMessage(Message.translation("server.craftory.claw.cleared"))
        } else if (colour != null) {
            state.filterColour = colour.value
            player.sendMessage(
                Message
                    .translation(
                        "server.craftory.claw.colour-set",
                    ).param("colour", Message.translation(colourKey(colour))),
            )
        } else {
            state.filterItemId = heldItem.itemId
            player.sendMessage(
                Message
                    .translation("server.craftory.claw.item-set")
                    .param("item", Message.translation("server.items.${heldItem.itemId}.name")),
            )
        }
        store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
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
        val CODEC: BuilderCodec<ClawUseInteraction> =
            BuilderCodec
                .builder(
                    ClawUseInteraction::class.java,
                    ::ClawUseInteraction,
                    SimpleBlockInteraction.CODEC,
                ).build()
    }
}

internal fun daubColour(itemId: String): TransportColour? =
    when (itemId) {
        CraftoryIds.PAINT_DAUB_RED -> TransportColour.RED
        CraftoryIds.PAINT_DAUB_BLUE -> TransportColour.BLUE
        CraftoryIds.PAINT_DAUB_GREEN -> TransportColour.GREEN
        CraftoryIds.PAINT_DAUB_YELLOW -> TransportColour.YELLOW
        else -> null
    }

internal fun colourKey(colour: TransportColour): String =
    when (colour) {
        TransportColour.RED -> "server.craftory.transport.red"
        TransportColour.BLUE -> "server.craftory.transport.blue"
        TransportColour.GREEN -> "server.craftory.transport.green"
        TransportColour.YELLOW -> "server.craftory.transport.yellow"
        TransportColour.NONE -> "server.craftory.transport.none"
    }
