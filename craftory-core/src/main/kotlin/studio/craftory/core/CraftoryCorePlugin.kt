package studio.craftory.core

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import studio.craftory.core.api.CraftoryCore
import java.util.concurrent.CompletableFuture

class CraftoryCorePlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    override fun setup() {
        val pluginVersion = getManifest().getVersion().toString()
        CraftoryCore.registerModule("CraftoryCore", pluginVersion)
        getCommandRegistry().registerCommand(CraftoryCommand(pluginVersion))
        LOGGER.atInfo().log("Craftory Core v%s loaded", pluginVersion)
    }

    private class CraftoryCommand(
        private val pluginVersion: String,
    ) : AbstractCommand("craftory", "Show Craftory plugin information") {
        override fun execute(context: CommandContext): CompletableFuture<Void> {
            val modules = CraftoryCore.loadedModules().ifEmpty { listOf("none") }.joinToString(", ")
            context.sendMessage(Message.raw("Craftory Core v$pluginVersion | Modules: $modules"))
            return CompletableFuture.completedFuture(null)
        }
    }

    private companion object {
        val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryCore")
    }
}
