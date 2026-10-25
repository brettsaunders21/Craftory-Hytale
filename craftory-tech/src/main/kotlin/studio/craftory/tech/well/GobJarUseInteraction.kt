package studio.craftory.tech.well

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.util.TargetUtil
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
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
        if (slotItem.itemId != CraftoryIds.GOB_JAR || slotItem.quantity != 1) return
        val position =
            TargetUtil
                .getTargetBlock(context.entity, 8.0, commandBuffer)
                ?.let { BlockPos(it.x, it.y, it.z) }
        if (position == null) {
            playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
            return
        }
        val world = commandBuffer.externalData.world
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z) ?: run {
                playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
                return
            }
        val ref =
            BlockModule.getBlockEntity(world.chunkStore.store, sectionRef, position.x, position.y, position.z) ?: run {
                playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
                return
            }
        when (
            val result =
                JarActions.catchFlicker(
                    world,
                    ref,
                    position,
                    playerRef.uuid,
                    CraftoryCore.crewService(),
                    world.chunkStore.store,
                )
        ) {
            JarCatchResult.NotWell -> {
                playerRef.sendMessage(Message.translation("server.craftory.jar.not-well"))
            }

            JarCatchResult.TooBig -> {
                playerRef.sendMessage(Message.translation("server.craftory.jar.too-big"))
            }

            JarCatchResult.TierLocked -> {
                playerRef.sendMessage(Message.translation("server.craftory.tier.locked"))
            }

            is JarCatchResult.Caught -> {
                val filled =
                    ItemStack(CraftoryIds.GOB_JAR_FILLED, 1)
                        .withMetadata(GobJarMetadata.TEMPERAMENT, result.temperament.id)
                        .withMetadata(GobJarMetadata.STRENGTH, result.strength.name)
                        .withMetadata(GobJarMetadata.TASTE, result.taste)
                if (container.setItemStackForSlot(slot, filled).succeeded()) {
                    playerRef.sendMessage(
                        Message
                            .translation("server.craftory.jar.caught")
                            .param("temperament", result.temperament.name),
                    )
                }
            }
        }
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
