package studio.craftory.core.energy

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class EnergyClock : Resource<ChunkStore> {
    override fun clone(): Resource<ChunkStore> = EnergyClock()

    fun isSolveTick(tick: Long): Boolean = tick % SOLVE_INTERVAL_TICKS == 0L

    companion object {
        const val SOLVE_INTERVAL_TICKS = 4L
        lateinit var type: ResourceType<ChunkStore, EnergyClock>
    }
}
