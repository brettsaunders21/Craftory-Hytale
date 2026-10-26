package studio.craftory.core.api.transport

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import studio.craftory.core.transport.FluidPipeLifecycle
import studio.craftory.core.transport.FluidTankLifecycle
import studio.craftory.core.transport.FluidTransportSaveSystem
import studio.craftory.core.transport.FluidTransportTickSystem
import studio.craftory.core.transport.FluidTransportWorld

internal object FluidTransportRegistration {
    fun register(plugin: JavaPlugin) {
        val chunkRegistry = plugin.getChunkStoreRegistry()
        FluidPipe.type =
            chunkRegistry.registerComponent(
                FluidPipe::class.java,
                "Craftory_FluidPipe",
                FluidPipe.CODEC,
            )
        FluidTank.type =
            chunkRegistry.registerComponent(
                FluidTank::class.java,
                "Craftory_FluidTank",
                FluidTank.CODEC,
            )
        FluidTransportWorld.type =
            chunkRegistry.registerResource(FluidTransportWorld::class.java, ::FluidTransportWorld)
        chunkRegistry.registerSystem(FluidPipeLifecycle())
        chunkRegistry.registerSystem(FluidTankLifecycle())
        chunkRegistry.registerSystem(FluidTransportTickSystem())
        chunkRegistry.registerSystem(FluidTransportSaveSystem())
    }
}
