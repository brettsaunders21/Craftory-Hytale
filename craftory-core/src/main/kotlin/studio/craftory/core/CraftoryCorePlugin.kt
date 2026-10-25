package studio.craftory.core

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.util.TargetUtil
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.energy.EnergyClock
import studio.craftory.core.energy.EnergyMemberLifecycle
import studio.craftory.core.energy.EnergyWorld
import java.util.concurrent.CompletableFuture

class CraftoryCorePlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    override fun setup() {
        val pluginVersion = getManifest().getVersion().toString()
        CraftoryCore.registerModule("CraftoryCore", pluginVersion)
        val chunkRegistry = getChunkStoreRegistry()
        EnergyMember.type =
            chunkRegistry.registerComponent(
                EnergyMember::class.java,
                "Craftory_EnergyMember",
                EnergyMember.CODEC,
            )
        EnergyWorld.type = chunkRegistry.registerResource(EnergyWorld::class.java, ::EnergyWorld)
        EnergyClock.type = chunkRegistry.registerResource(EnergyClock::class.java, ::EnergyClock)
        chunkRegistry.registerSystem(EnergyMemberLifecycle())
        chunkRegistry.registerSystem(EnergySolveSystem())
        val command = CraftoryCommand(pluginVersion)
        getCommandRegistry().registerCommand(command)
        LOGGER.atInfo().log("Craftory Core v%s loaded", pluginVersion)
    }

    private class CraftoryCommand(
        private val pluginVersion: String,
    ) : AbstractCommand("craftory", "Show Craftory plugin information") {
        init {
            addSubCommand(NetCommand())
            addSubCommand(StatsCommand())
            addSubCommand(InspectCommand())
            addSubCommand(SelfTestCommand())
            addSubCommand(SummonCommand())
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val modules = CraftoryCore.loadedModules().ifEmpty { listOf("none") }.joinToString(", ")
            context.sendMessage(Message.raw("Craftory Core v$pluginVersion | Modules: $modules"))
            return CompletableFuture.completedFuture(null)
        }
    }

    private class SummonCommand : AbstractCommand("summon", "Summon a Craftory trader") {
        init {
            setAllowsExtraArguments(true)
            requirePermission(HytalePermissions.fromCommand("craftory.summon"))
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> =
            CraftoryCore.dispatchCommandHandler("summon", context)
    }

    private class NetCommand : AbstractCommand("net", "Show the energy network for a targeted block or coordinates") {
        init {
            setAllowsExtraArguments(true)
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val playerRef = context.senderAsPlayerRef()
            if (playerRef == null) {
                context.sendMessage(Message.raw("Run /craftory net as a player."))
                return CompletableFuture.completedFuture(null)
            }
            val entityStore = playerRef.store
            val world = entityStore.externalData.world
            val arguments =
                context.inputString
                    .substringAfter(
                        "net",
                    ).trim()
                    .split(Regex("\\s+"))
                    .filter(String::isNotEmpty)
            val explicitPosition =
                if (arguments.isEmpty()) {
                    null
                } else if (arguments.size == 3) {
                    val coordinates = arguments.mapNotNull(String::toIntOrNull)
                    if (coordinates.size != 3) {
                        context.sendMessage(Message.raw("Usage: /craftory net [x y z]"))
                        return CompletableFuture.completedFuture(null)
                    }
                    BlockPos(coordinates[0], coordinates[1], coordinates[2])
                } else {
                    context.sendMessage(Message.raw("Usage: /craftory net [x y z]"))
                    return CompletableFuture.completedFuture(null)
                }
            return CompletableFuture.runAsync(
                {
                    val position =
                        explicitPosition
                            ?: TargetUtil
                                .getTargetBlock(playerRef, 8.0, entityStore)
                                ?.let { BlockPos(it.x, it.y, it.z) }
                    if (position == null) {
                        context.sendMessage(Message.raw("No block targeted within 8 blocks."))
                        return@runAsync
                    }
                    val energy = world.chunkStore.store.getResource(EnergyWorld.type)
                    val network = energy.diagnostics(position.pack())
                    if (network == null) {
                        context.sendMessage(
                            Message.raw("No energy network at ${position.x} ${position.y} ${position.z}."),
                        )
                    } else {
                        context.sendMessage(
                            Message.raw(
                                "Network ${network.id}: members=${network.members} grade=${network.grade} " +
                                    "supply=${network.supply} demand=${network.demand} " +
                                    "state=${if (network.awake) "awake" else "asleep"}",
                            ),
                        )
                    }
                },
                world,
            )
        }
    }

    private class StatsCommand : AbstractCommand("stats", "Show energy statistics for this world") {
        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val playerRef = context.senderAsPlayerRef()
            if (playerRef == null) {
                context.sendMessage(Message.raw("Run /craftory stats as a player."))
                return CompletableFuture.completedFuture(null)
            }
            val world = playerRef.store.externalData.world
            return CompletableFuture.runAsync(
                {
                    val energy = world.chunkStore.store.getResource(EnergyWorld.type)
                    context.sendMessage(
                        Message.raw(
                            "Energy networks=${energy.networkCount} awake=${energy.awakeNetworkCount} " +
                                "lastSolveNs=${energy.lastSolveNanos}",
                        ),
                    )
                },
                world,
            )
        }
    }

    private class InspectCommand : AbstractCommand("inspect", "Inspect Craftory components at block coordinates") {
        init {
            setAllowsExtraArguments(true)
            requirePermission(HytalePermissions.fromCommand("craftory.inspect"))
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val arguments =
                context.inputString
                    .substringAfter("inspect")
                    .trim()
                    .split(Regex("\\s+"))
                    .filter(String::isNotEmpty)
            if (arguments.size !in 3..4) {
                context.sendMessage(Message.raw("Usage: /craftory inspect <x> <y> <z> [world]"))
                return CompletableFuture.completedFuture(null)
            }
            val coordinates = arguments.take(3).mapNotNull(String::toIntOrNull)
            if (coordinates.size != 3) {
                context.sendMessage(Message.raw("Coordinates must be integers."))
                return CompletableFuture.completedFuture(null)
            }
            val universe = Universe.get()
            val world = if (arguments.size == 4) universe.getWorld(arguments[3]) else universe.defaultWorld
            if (world == null) {
                context.sendMessage(Message.raw("World not found."))
                return CompletableFuture.completedFuture(null)
            }
            val (x, y, z) = coordinates
            return CompletableFuture.runAsync(
                {
                    val ref = BlockModule.getBlockEntity(world, x, y, z)
                    if (ref == null) {
                        context.sendMessage(Message.raw("No block entity at $x $y $z in ${world.name}."))
                        return@runAsync
                    }
                    val store = world.chunkStore.store
                    val components = CraftoryCore.inspectableComponents(store, ref)
                    if (components.isEmpty()) {
                        context.sendMessage(Message.raw("No Craftory components at $x $y $z in ${world.name}."))
                    } else {
                        components.forEach { (id, component) ->
                            val fields = component.describe().joinToString(" ") { (key, value) -> "$key=$value" }
                            context.sendMessage(Message.raw("$id: $fields"))
                        }
                    }
                    val network = store.getResource(EnergyWorld.type).diagnostics(BlockPos.pack(x, y, z))
                    context.sendMessage(
                        Message.raw(
                            if (network == null) {
                                "Network: none"
                            } else {
                                "Network ${network.id}: members=${network.members} grade=${network.grade} " +
                                    "supply=${network.supply} demand=${network.demand} " +
                                    "state=${if (network.awake) "awake" else "asleep"}"
                            },
                        ),
                    )
                },
                world,
            )
        }
    }

    private class SelfTestCommand : AbstractCommand("selftest", "Run Craftory runtime checks") {
        init {
            requirePermission(HytalePermissions.fromCommand("craftory.selftest"))
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> =
            CraftoryCore.dispatchCommandHandler("selftest", context)
    }

    private companion object {
        val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryCore")
    }
}
