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
import com.hypixel.hytale.server.core.util.Config
import com.hypixel.hytale.server.core.util.TargetUtil
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.multiblock.GhostMode
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellIndex
import studio.craftory.core.api.transport.TubeNode
import studio.craftory.core.energy.AnchorCoverageIndex
import studio.craftory.core.energy.EnergyClock
import studio.craftory.core.energy.EnergyMemberLifecycle
import studio.craftory.core.energy.EnergyWorld
import studio.craftory.core.multiblock.MultiblockBreakSystem
import studio.craftory.core.multiblock.MultiblockIndex
import studio.craftory.core.multiblock.MultiblockLifecycle
import studio.craftory.core.multiblock.MultiblockPlaceSystem
import studio.craftory.core.multiblock.MultiblockValidationSystem
import studio.craftory.core.well.WellLifecycle
import studio.craftory.core.transport.TubeNodeLifecycle
import studio.craftory.core.transport.TubeSectionActivation
import studio.craftory.core.transport.TubeSectionLifecycle
import studio.craftory.core.transport.TubeWorld
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

class CraftoryCorePlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    private val config: Config<CraftoryCoreConfig> = withConfig("config", CraftoryCoreConfig.CODEC)
    private var crewService: CrewService? = null

    override fun setup() {
        val pluginVersion = getManifest().getVersion().toString()
        CraftoryCore.registerModule("CraftoryCore", pluginVersion)
        val crews = CrewService(getDataDirectory().resolve("crews.json"))
        crewService = crews
        CraftoryCore.registerCrewService(crews)
        val chunkRegistry = getChunkStoreRegistry()
        EnergyMember.type =
            chunkRegistry.registerComponent(
                EnergyMember::class.java,
                "Craftory_EnergyMember",
                EnergyMember.CODEC,
            )
        EnergyWorld.type = chunkRegistry.registerResource(EnergyWorld::class.java, ::EnergyWorld)
        TubeNode.type = chunkRegistry.registerComponent(TubeNode::class.java, "CraftoryTubeNode", TubeNode.CODEC)
        TubeWorld.type = chunkRegistry.registerResource(TubeWorld::class.java, ::TubeWorld)
        chunkRegistry.registerSystem(TubeNodeLifecycle())
        chunkRegistry.registerSystem(TubeSectionLifecycle())
        chunkRegistry.registerSystem(TubeSectionActivation())
        AnchorCoverageIndex.type =
            chunkRegistry.registerResource(
                AnchorCoverageIndex::class.java,
                ::AnchorCoverageIndex,
            )
        EnergyClock.type = chunkRegistry.registerResource(EnergyClock::class.java, ::EnergyClock)
        MultiblockController.type =
            chunkRegistry.registerComponent(
                MultiblockController::class.java,
                "Craftory_MultiblockController",
                MultiblockController.CODEC,
            )
        MultiblockIndex.type = chunkRegistry.registerResource(MultiblockIndex::class.java, ::MultiblockIndex)
        Glimmerwell.type =
            chunkRegistry.registerComponent(
                Glimmerwell::class.java,
                "Craftory_Glimmerwell",
                Glimmerwell.CODEC,
            )
        WellIndex.type = chunkRegistry.registerResource(WellIndex::class.java, ::WellIndex)
        chunkRegistry.registerSystem(EnergyMemberLifecycle())
        chunkRegistry.registerSystem(MultiblockLifecycle())
        chunkRegistry.registerSystem(MultiblockValidationSystem())
        getEntityStoreRegistry().registerSystem(MultiblockPlaceSystem())
        getEntityStoreRegistry().registerSystem(MultiblockBreakSystem())
        chunkRegistry.registerSystem(WellLifecycle())
        chunkRegistry.registerSystem(EnergySolveSystem())
        val command = CraftoryCommand(pluginVersion, config, crews)
        getCommandRegistry().registerCommand(command)
        LOGGER.atInfo().log("Craftory Core v%s loaded", pluginVersion)
    }

    override fun start() {
        val minutes = config.get().anchorMaxChargeMinutes
        CraftoryCore.setAnchorMaxChargeMinutes(minutes.coerceAtLeast(0))
        CraftoryCore.setGhostMode(GhostMode.parse(config.get().ghostMode))
    }

    @Suppress("TooGenericExceptionCaught")
    override fun shutdown() {
        crewService?.let { crews ->
            try {
                crews.close()
            } catch (exception: Exception) {
                LOGGER.at(Level.SEVERE).log("Could not flush Craftory crew data: %s", exception.message)
            } finally {
                CraftoryCore.unregisterCrewService(crews)
                crewService = null
            }
        }
    }

    private class CraftoryCommand(
        private val pluginVersion: String,
        config: Config<CraftoryCoreConfig>,
        crews: CrewService,
    ) : AbstractCommand("craftory", "Show Craftory plugin information") {
        init {
            addSubCommand(NetCommand())
            addSubCommand(StatsCommand())
            addSubCommand(InspectCommand())
            addSubCommand(SelfTestCommand())
            addSubCommand(SummonCommand())
            addSubCommand(ScrapRushCommand())
            addSubCommand(ConfigCommand(config))
            addSubCommand(CraftoryCrewCommand(crews))
            addSubCommand(CraftoryGrantCommand(crews))
            addSubCommand(WellCommand())
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val modules = CraftoryCore.loadedModules().ifEmpty { listOf("none") }.joinToString(", ")
            context.sendMessage(Message.raw("Craftory Core v$pluginVersion | Modules: $modules"))
            return CompletableFuture.completedFuture(null)
        }
    }

    private class ConfigCommand(
        config: Config<CraftoryCoreConfig>,
    ) : AbstractCommand("config", "Configure Craftory") {
        init {
            addSubCommand(AnchorMaxCommand(config))
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> {
            context.sendMessage(Message.raw("Usage: /craftory config anchor-max <minutes>"))
            return CompletableFuture.completedFuture(null)
        }
    }

    private class AnchorMaxCommand(
        private val config: Config<CraftoryCoreConfig>,
    ) : AbstractCommand("anchor-max", "Set the maximum Anchor charge in minutes") {
        init {
            setAllowsExtraArguments(true)
            requirePermission("hytale.command.craftory.config")
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val minutes =
                context.inputString
                    .substringAfter("anchor-max")
                    .trim()
                    .toIntOrNull()
            if (minutes == null || minutes < 0) {
                context.sendMessage(Message.raw("Usage: /craftory config anchor-max <minutes> (0 or greater)"))
                return CompletableFuture.completedFuture(null)
            }
            config.get().anchorMaxChargeMinutes = minutes
            CraftoryCore.setAnchorMaxChargeMinutes(minutes)
            context.sendMessage(Message.raw("Anchor maximum charge set to $minutes minutes."))
            return config.save()
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

    private class ScrapRushCommand : AbstractCommand("scraprush", "Start a Scrap Rush raid") {
        init {
            setAllowsExtraArguments(true)
            requirePermission(HytalePermissions.fromCommand("craftory.scraprush"))
        }

        override fun execute(context: CommandContext): CompletableFuture<Void> =
            CraftoryCore.dispatchCommandHandler("scraprush", context)
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
