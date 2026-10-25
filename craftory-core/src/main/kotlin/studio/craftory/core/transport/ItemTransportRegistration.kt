package studio.craftory.core.transport

import com.hypixel.hytale.server.core.util.Config
import studio.craftory.core.CraftoryCoreConfig
import studio.craftory.core.api.transport.ItemPipe

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
    }

    fun shutdown() {
        ItemTransitDisplaySystem.shutdown()
    }
}
