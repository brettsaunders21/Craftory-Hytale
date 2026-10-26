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
        val requested = context.inputString.substringAfter("summon").trim()
        if (requested !in setOf("grizzlecog", "vessa")) {
            context.sendMessage(Message.raw("Usage: /craftory summon <grizzlecog|vessa>"))
            return CompletableFuture.completedFuture(null)
        }
        val playerRef = context.senderAsPlayerRef()
        if (playerRef == null) {
            context.sendMessage(Message.raw("Run /craftory summon <grizzlecog|vessa> as a player."))
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
                val role = if (requested == "vessa") CraftoryIds.VESSA_ROLE else CraftoryIds.GRIZZLECOG
                NPCPlugin.get().spawnNPC(
                    entityStore,
                    role,
                    null,
                    Vector3d(target.x + 0.5, target.y + 1.0, target.z + 0.5),
                    player?.headRotation ?: Rotation3f(0f, 0f, 0f),
                )
                val name = if (requested == "vessa") "Vessa Lanternwright" else "Grizzlecog"
                context.sendMessage(Message.raw("$name summoned."))
            },
            world,
        )
    }
}
