package studio.craftory.core

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import studio.craftory.core.api.crew.Crew
import studio.craftory.core.api.crew.CrewService
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class CraftoryCrewCommand(
    private val crews: CrewService,
) : AbstractCommand("crew", "Manage your Craftory crew") {
    init {
        setAllowsExtraArguments(true)
    }

    override fun execute(context: CommandContext): CompletableFuture<Void> {
        val input = context.inputString.substringAfter("crew").trim()
        val action = input.substringBefore(' ').lowercase()
        val argument = input.substringAfter(' ', "").trim()
        when (action) {
            "create" -> create(context, argument)
            "invite" -> invite(context, argument)
            "accept" -> accept(context, argument)
            "leave" -> leave(context)
            "kick" -> kick(context, argument)
            "info" -> info(context, argument)
            else -> context.sendMessage(Message.translation("server.craftory.crew.usage"))
        }
        return CompletableFuture.completedFuture(null)
    }

    private fun create(
        context: CommandContext,
        name: String,
    ) {
        val player = senderPlayer(context) ?: return
        if (name.isBlank()) {
            context.sendMessage(Message.translation("server.craftory.crew.usage"))
            return
        }
        val crew =
            try {
                crews.create(player.uuid, name)
            } catch (_: IllegalArgumentException) {
                context.sendMessage(Message.translation("server.craftory.crew.invalid-name"))
                return
            } catch (_: IllegalStateException) {
                context.sendMessage(Message.translation("server.craftory.crew.already-member"))
                return
            }
        context.sendMessage(
            Message
                .translation("server.craftory.crew.created")
                .param("name", crew.name),
        )
    }

    private fun invite(
        context: CommandContext,
        targetName: String,
    ) {
        val player = senderPlayer(context) ?: return
        val target = onlinePlayer(targetName)
        if (target == null) {
            context.sendMessage(Message.translation("server.craftory.crew.player-not-found"))
            return
        }
        val crew = crews.crewOf(player.uuid)
        if (!crews.invite(player.uuid, target.uuid)) {
            context.sendMessage(Message.translation("server.craftory.crew.invite-failed"))
            return
        }
        context.sendMessage(
            Message
                .translation("server.craftory.crew.invited")
                .param("player", target.username),
        )
        target.sendMessage(
            Message
                .translation("server.craftory.crew.invite-received")
                .param("owner", player.username)
                .param("crew", crew.name)
                .param("id", crew.id.toString()),
        )
    }

    private fun accept(
        context: CommandContext,
        crewIdText: String,
    ) {
        val player = senderPlayer(context) ?: return
        val crewId = crewIdText.toUuidOrNull()
        val crew = crewId?.let { crews.accept(player.uuid, it) }
        if (crew == null) {
            context.sendMessage(Message.translation("server.craftory.crew.accept-failed"))
            return
        }
        context.sendMessage(
            Message
                .translation("server.craftory.crew.accepted")
                .param("crew", crew.name),
        )
    }

    private fun leave(context: CommandContext) {
        val player = senderPlayer(context) ?: return
        val previous = crews.crewOf(player.uuid)
        if (previous.solo) {
            context.sendMessage(Message.translation("server.craftory.crew.not-in-crew"))
            return
        }
        crews.leave(player.uuid)
        context.sendMessage(Message.translation("server.craftory.crew.left"))
    }

    private fun kick(
        context: CommandContext,
        targetName: String,
    ) {
        val player = senderPlayer(context) ?: return
        val target = onlinePlayer(targetName)
        if (target == null) {
            context.sendMessage(Message.translation("server.craftory.crew.player-not-found"))
            return
        }
        if (!crews.kick(player.uuid, target.uuid)) {
            context.sendMessage(Message.translation("server.craftory.crew.kick-failed"))
            return
        }
        context.sendMessage(
            Message
                .translation("server.craftory.crew.kicked")
                .param("player", target.username),
        )
        target.sendMessage(Message.translation("server.craftory.crew.kicked-from-crew"))
    }

    private fun info(
        context: CommandContext,
        targetName: String,
    ) {
        val player =
            if (targetName.isBlank()) {
                senderPlayer(context) ?: return
            } else {
                if (!context.sender().hasPermission(HytalePermissions.fromCommand("craftory.crew.info"))) {
                    context.sendMessage(Message.translation("server.craftory.crew.permission"))
                    return
                }
                onlinePlayer(targetName)
                    ?: run {
                        context.sendMessage(Message.translation("server.craftory.crew.player-not-found"))
                        return
                    }
            }
        sendInfo(context, crews.crewOf(player.uuid))
    }

    private fun sendInfo(
        context: CommandContext,
        crew: Crew,
    ) {
        val members =
            crew.members
                .sortedBy(UUID::toString)
                .joinToString(", ") { memberId ->
                    Universe
                        .get()
                        .getPlayers()
                        .firstOrNull { it.uuid == memberId }
                        ?.username ?: memberId.toString()
                }
        context.sendMessage(
            Message
                .translation("server.craftory.crew.info")
                .param("name", crew.name)
                .param("tier", crew.tier)
                .param("pages", crew.knowledge.size)
                .param(
                    "known",
                    crew.knowledge
                        .sorted()
                        .joinToString(", ")
                        .ifEmpty { "none" },
                ).param("members", members),
        )
    }

    private fun senderPlayer(context: CommandContext): PlayerRef? =
        (context.sender() as? PlayerRef)
            ?: run {
                context.sendMessage(Message.translation("server.craftory.crew.player-only"))
                null
            }

    private fun onlinePlayer(username: String): PlayerRef? =
        if (username.isBlank()) {
            null
        } else {
            Universe.get().getPlayers().firstOrNull { it.username.equals(username, ignoreCase = true) }
        }

    private fun String.toUuidOrNull(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }
}

internal class CraftoryGrantCommand(
    private val crews: CrewService,
) : AbstractCommand("grant", "Grant a Craftory knowledge page") {
    init {
        setAllowsExtraArguments(true)
        requirePermission("hytale.command.craftory.grant")
    }

    override fun execute(context: CommandContext): CompletableFuture<Void> {
        val arguments =
            context.inputString
                .substringAfter("grant")
                .trim()
                .split(Regex("\\s+"))
                .filter(String::isNotEmpty)
        if (arguments.size != 2) {
            context.sendMessage(Message.translation("server.craftory.grant.usage"))
            return CompletableFuture.completedFuture(null)
        }
        val target =
            Universe.get().getPlayers().firstOrNull {
                it.username.equals(arguments[0], ignoreCase = true)
            }
        if (target == null) {
            context.sendMessage(Message.translation("server.craftory.crew.player-not-found"))
            return CompletableFuture.completedFuture(null)
        }
        val granted =
            try {
                crews.grantKnowledge(target.uuid, arguments[1])
            } catch (_: IllegalArgumentException) {
                context.sendMessage(Message.translation("server.craftory.grant.invalid-id"))
                return CompletableFuture.completedFuture(null)
            }
        context.sendMessage(
            Message
                .translation(
                    if (granted) "server.craftory.grant.success" else "server.craftory.grant.already-known",
                ).param("player", target.username)
                .param("id", arguments[1]),
        )
        return CompletableFuture.completedFuture(null)
    }
}
