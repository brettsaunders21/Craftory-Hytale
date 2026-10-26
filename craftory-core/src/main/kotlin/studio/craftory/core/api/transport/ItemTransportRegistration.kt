package studio.craftory.core.api.transport

import com.hypixel.hytale.server.core.util.Config
import studio.craftory.core.CraftoryCoreConfig
import studio.craftory.core.transport.ItemTransitDisplayState
import studio.craftory.core.transport.ItemTransitDisplaySystem
import studio.craftory.core.transport.ItemTransportLifecycle
import studio.craftory.core.transport.ItemTransportSaveSystem
import studio.craftory.core.transport.ItemTransportTickSystem
import studio.craftory.core.transport.ItemTransportWorld

internal object ItemTransportRegistration {
    fun register(
        plugin: com.hypixel.hytale.server.core.plugin.JavaPlugin,
        config: Config<CraftoryCoreConfig>,
    ) {
        val chunkRegistry = plugin.getChunkStoreRegistry()
        ItemPipe.type =
            chunkRegistry.registerComponent(
                ItemPipe::class.java,
                "Craftory_ItemPipe",
                ItemPipe.CODEC,
            )
        ItemTransportWorld.type = chunkRegistry.registerResource(ItemTransportWorld::class.java, ::ItemTransportWorld)
        ItemTransitDisplayState.type =
            chunkRegistry.registerResource(
                ItemTransitDisplayState::class.java,
                ::ItemTransitDisplayState,
            )
        chunkRegistry.registerSystem(ItemTransportLifecycle())
        chunkRegistry.registerSystem(ItemTransportTickSystem())
        chunkRegistry.registerSystem(ItemTransitDisplaySystem(config))
        chunkRegistry.registerSystem(ItemTransportSaveSystem())
    }

    fun shutdown() {
        ItemTransitDisplaySystem.shutdown()
    }
}
