package studio.craftory.tech.command

import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.util.TargetUtil
import com.hypixel.hytale.server.npc.NPCPlugin
import org.joml.Vector3d
import studio.craftory.tech.CraftoryIds
import java.util.concurrent.CompletableFuture

object SummonCommand {
    fun execute(context: CommandContext): CompletableFuture<Void> {
        if (context.inputString.substringAfter("summon").trim() != "grizzlecog") {
            context.sendMessage(Message.raw("Usage: /craftory summon grizzlecog"))
            return CompletableFuture.completedFuture(null)
        }
        val playerRef = context.senderAsPlayerRef()
        if (playerRef == null) {
            context.sendMessage(Message.raw("Run /craftory summon grizzlecog as a player."))
            return CompletableFuture.completedFuture(null)
        }
        val entityStore = playerRef.store
        val world = entityStore.externalData.world
        return CompletableFuture.runAsync(
            {
                val target = TargetUtil.getTargetBlock(playerRef, 8.0, entityStore)
                if (target == null) {
                    context.sendMessage(Message.raw("Look at a block within 8 blocks to summon Grizzlecog."))
                    return@runAsync
                }
                val player = entityStore.getComponent(playerRef, PlayerRef.getComponentType())
                NPCPlugin.get().spawnNPC(
                    entityStore,
                    CraftoryIds.GRIZZLECOG,
                    null,
                    Vector3d(target.x + 0.5, target.y + 1.0, target.z + 0.5),
                    player?.headRotation ?: Rotation3f(0f, 0f, 0f),
                )
                context.sendMessage(Message.raw("Grizzlecog summoned."))
            },
            world,
        )
    }
}
