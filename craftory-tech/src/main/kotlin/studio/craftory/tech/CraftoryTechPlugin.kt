package studio.craftory.tech

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import studio.craftory.core.api.CraftoryCore

class CraftoryTechPlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    override fun setup() {
        val pluginVersion = getManifest().getVersion().toString()
        CraftoryCore.registerModule("CraftoryTech", pluginVersion)
        LOGGER.atInfo().log("Craftory Tech v%s loaded", pluginVersion)
    }

    private companion object {
        val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryTech")
    }
}
