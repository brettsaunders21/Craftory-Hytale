package studio.craftory.core

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import studio.craftory.core.api.CraftoryCore
import java.util.concurrent.CompletableFuture

private const val PLUGIN_VERSION = "0.1.0"

class CraftoryCorePlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    override fun setup() {
        CraftoryCore.registerModule("CraftoryCore", PLUGIN_VERSION)
        getCommandRegistry().registerCommand(CraftoryCommand())
        LOGGER.atInfo().log("Craftory Core v%s loaded", PLUGIN_VERSION)
    }

    private class CraftoryCommand : AbstractCommand("craftory", "Show Craftory plugin information") {
        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val modules = CraftoryCore.loadedModules().ifEmpty { listOf("none") }.joinToString(", ")
            context.sendMessage(Message.raw("Craftory Core v$PLUGIN_VERSION | Modules: $modules"))
            return CompletableFuture.completedFuture(null)
        }
    }

    private companion object {
        val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryCore")
    }
}
