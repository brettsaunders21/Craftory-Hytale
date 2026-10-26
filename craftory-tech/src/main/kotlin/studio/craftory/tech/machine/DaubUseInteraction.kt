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
import studio.craftory.core.api.transport.ItemTransport
import studio.craftory.core.api.transport.TransportColour
import studio.craftory.tech.CraftoryIds

class DaubUseInteraction : SimpleBlockInteraction() {
    override fun interactWithBlock(
        world: World,
        commandBuffer: CommandBuffer<EntityStore>,
        type: InteractionType,
        context: InteractionContext,
        heldItem: ItemStack?,
        position: Vector3i,
        cooldownHandler: CooldownHandler,
    ) {
        val colour = heldItem?.itemId?.let(::daubColour) ?: return
        val player = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val ref =
            BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: run {
                player.sendMessage(Message.translation("server.craftory.transport.not-pipe"))
                return
            }
        val store = world.chunkStore.store
        val owner = store.getComponent(ref, MachineOwner.type)?.placerUuid
        if (!mayUseMachine(owner, player.uuid, player, CraftoryCore.crewService())) {
            player.sendMessage(Message.translation("server.craftory.transport.foreign-crew"))
            return
        }
        val transport = ItemTransport.of(world)
        if (!transport.isPipe(position.x, position.y, position.z)) {
            player.sendMessage(Message.translation("server.craftory.transport.not-pipe"))
            return
        }
        if (!transport.setColour(position.x, position.y, position.z, colour)) return
        player.sendMessage(
            Message
                .translation("server.craftory.transport.daubed")
                .param("colour", Message.translation(colourKey(colour))),
        )
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
        val CODEC: BuilderCodec<DaubUseInteraction> =
            BuilderCodec
                .builder(
                    DaubUseInteraction::class.java,
                    ::DaubUseInteraction,
                    SimpleBlockInteraction.CODEC,
                ).build()
    }
}
