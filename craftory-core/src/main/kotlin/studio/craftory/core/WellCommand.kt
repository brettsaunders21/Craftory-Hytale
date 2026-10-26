package studio.craftory.core

import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellStrength
import java.util.concurrent.CompletableFuture

internal class WellCommand : AbstractCommand("well", "Inspect or spawn Glimmerwells") {
    init {
        setAllowsExtraArguments(true)
        requirePermission(HytalePermissions.fromCommand("craftory.well"))
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun execute(context: CommandContext): CompletableFuture<Void> {
        val playerRef = context.senderAsPlayerRef()
        if (playerRef == null) {
            context.sendMessage(Message.raw("Run /craftory well as a player."))
            return CompletableFuture.completedFuture(null)
        }
        val entityStore = playerRef.store
        val world = entityStore.externalData.world
        val arguments =
            context.inputString
                .substringAfter("well")
                .trim()
                .split(Regex("\\s+"))
                .filter(String::isNotEmpty)
        if (arguments.isEmpty()) {
            context.sendMessage(Message.raw("Usage: /craftory well spawn <temperament> <strength> | list"))
            return CompletableFuture.completedFuture(null)
        }
        return CompletableFuture.runAsync(
            {
                val playerPosition =
                    playerPosition(playerRef, entityStore) ?: run {
                        context.sendMessage(Message.raw("Could not read your position."))
                        return@runAsync
                    }
                when (arguments[0].lowercase()) {
                    "list" -> {
                        val wells = CraftoryCore.wells().wellsNear(world.chunkStore.store, playerPosition, 128)
                        context.sendMessage(
                            Message.raw(
                                "Glimmerwells: ${CraftoryCore.wells().count(world.chunkStore.store)} total, " +
                                    "${wells.size} within 128 blocks.",
                            ),
                        )
                        wells.forEach { well ->
                            context.sendMessage(
                                Message.raw(
                                    "${well.pos.x} ${well.pos.y} ${well.pos.z}: " +
                                        "${well.well.temperament.name} ${well.well.strength.name} " +
                                        well.well.state.name,
                                ),
                            )
                        }
                    }

                    "spawn" -> {
                        if (arguments.size != 3) {
                            context.sendMessage(Message.raw("Usage: /craftory well spawn <temperament> <strength>"))
                            return@runAsync
                        }
                        val temperament = Temperament.entries.firstOrNull { it.name.equals(arguments[1], true) }
                        val strength = WellStrength.entries.firstOrNull { it.name.equals(arguments[2], true) }
                        if (temperament == null || strength == null) {
                            context.sendMessage(Message.raw("Unknown temperament or strength."))
                            return@runAsync
                        }
                        val target =
                            TargetUtil
                                .getTargetBlock(playerRef, 8.0, entityStore)
                                ?.let { BlockPos(it.x, it.y + 1, it.z) }
                        val targetRef = target?.let { CraftoryCore.wells().spawn(world, it, temperament, strength) }
                        val fallbackRef =
                            if (targetRef == null) {
                                CraftoryCore.wells().spawn(world, playerPosition, temperament, strength)
                            } else {
                                null
                            }
                        val ref = targetRef ?: fallbackRef
                        val spawnPosition = if (targetRef != null) target else playerPosition
                        context.sendMessage(
                            Message.raw(
                                if (ref == null) {
                                    if (target == null) {
                                        "Could not spawn a Glimmerwell at your position; it is occupied."
                                    } else {
                                        "Could not spawn a Glimmerwell; both the target space and your position are occupied."
                                    }
                                } else {
                                    "Spawned ${temperament.name} ${strength.name} Glimmerwell at $spawnPosition."
                                },
                            ),
                        )
                    }

                    else -> {
                        context.sendMessage(Message.raw("Usage: /craftory well spawn <temperament> <strength> | list"))
                    }
                }
            },
            world,
        )
    }

    private fun playerPosition(
        playerRef: Ref<EntityStore>,
        entityStore: ComponentAccessor<EntityStore>,
    ): BlockPos? {
        val position =
            entityStore.getComponent(playerRef, TransformComponent.getComponentType())?.position ?: return null
        return BlockPos(
            kotlin.math.floor(position.x).toInt(),
            kotlin.math.floor(position.y).toInt(),
            kotlin.math.floor(position.z).toInt(),
        )
    }
}
