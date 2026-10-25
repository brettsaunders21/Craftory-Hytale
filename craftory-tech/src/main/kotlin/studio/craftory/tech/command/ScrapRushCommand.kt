package studio.craftory.tech.command

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.PlayerRef
import studio.craftory.tech.ScrapRushSettings
import studio.craftory.tech.machine.ScrapRushSystem
import java.util.concurrent.CompletableFuture

object ScrapRushCommand {
    fun execute(
        context: CommandContext,
        settings: ScrapRushSettings,
    ): CompletableFuture<Void> {
        val playerEntity = context.senderAsPlayerRef()
        if (playerEntity == null) {
            context.sendMessage(Message.translation("server.craftory.scraprush.player-only"))
        } else {
            val player = playerEntity.store.getComponent(playerEntity, PlayerRef.getComponentType())
            if (player?.hasPermission("hytale.command.craftory.scraprush") != true) {
                context.sendMessage(Message.translation("server.craftory.scraprush.operator-only"))
            } else if (!settings.enabled) {
                context.sendMessage(Message.translation("server.craftory.scraprush.disabled"))
            } else if (context.inputString.substringAfter("scraprush").trim() != "start") {
                context.sendMessage(Message.translation("server.craftory.scraprush.usage"))
            } else {
                return ScrapRushSystem
                    .startNearest(player, settings) { started, diagnostic ->
                        context.sendMessage(
                            if (started) {
                                Message.translation("server.craftory.scraprush.started")
                            } else {
                                Message
                                    .translation("server.craftory.scraprush.start-failed")
                                    .param("reason", diagnostic)
                            },
                        )
                    }.thenApply { null }
            }
        }
        return CompletableFuture.completedFuture(null)
    }
}
