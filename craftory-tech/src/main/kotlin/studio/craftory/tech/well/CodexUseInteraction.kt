package studio.craftory.tech.well

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import studio.craftory.core.api.CraftoryCore

class CodexUseInteraction : SimpleInteraction() {
    override fun tick0(
        firstRun: Boolean,
        time: Float,
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler,
    ) {
        if (!firstRun) return
        val commandBuffer = context.commandBuffer ?: return
        val playerRef = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val player = commandBuffer.getComponent(context.entity, Player.getComponentType()) ?: return
        val crews = CraftoryCore.crewService()
        if (crews.crewOf(playerRef.uuid).tier < 2) {
            playerRef.sendMessage(Message.translation("server.craftory.tier.locked"))
            return
        }
        player.pageManager.openCustomPage(
            context.entity,
            commandBuffer.store,
            CodexPageUi(playerRef, crews),
        )
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
        val CODEC: BuilderCodec<CodexUseInteraction> =
            BuilderCodec
                .builder(CodexUseInteraction::class.java, ::CodexUseInteraction, SimpleInteraction.CODEC)
                .build()
    }
}
