package studio.craftory.tech.machine

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class AnchorRefreshIndex : Resource<ChunkStore> {
    private var tick = Long.MIN_VALUE
    private val refreshedChunks = HashSet<Long>()

    fun markRefreshed(
        chunkIndex: Long,
        currentTick: Long,
    ): Boolean {
        if (currentTick != tick) {
            tick = currentTick
            refreshedChunks.clear()
        }
        return refreshedChunks.add(chunkIndex)
    }

    override fun clone(): Resource<ChunkStore> = AnchorRefreshIndex()

    companion object {
        lateinit var type: ResourceType<ChunkStore, AnchorRefreshIndex>
    }
}
