package studio.craftory.tech

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import studio.craftory.core.api.CraftoryCore

private const val PLUGIN_VERSION = "0.1.0"

class CraftoryTechPlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    override fun setup() {
        CraftoryCore.registerModule("CraftoryTech", PLUGIN_VERSION)
        LOGGER.atInfo().log("Craftory Tech v%s loaded", PLUGIN_VERSION)
    }

    private companion object {
        val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryTech")
    }
}
