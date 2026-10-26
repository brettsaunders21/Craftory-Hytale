package studio.craftory.core.api

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.energy.EnergyClock
import studio.craftory.core.energy.EnergyWorld

class EnergySolveSystem : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val world = store.externalData.world
        if (store.getResource(EnergyClock.type).isSolveTick(world.tick)) {
            store.getResource(EnergyWorld.type).solve(store, world.tick)
        }
    }
}
