package studio.craftory.core.transport

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class FluidTransportTickSystem : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val world = store.externalData.world
        store.getResource(FluidTransportWorld.type).tick(store, world.tick)
    }
}
