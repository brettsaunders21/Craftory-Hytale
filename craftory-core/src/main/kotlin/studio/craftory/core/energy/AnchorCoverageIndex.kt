package studio.craftory.core.energy

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class AnchorCoverageIndex : Resource<ChunkStore> {
    private val coverageByAnchor = HashMap<Long, LongArray>()
    private val anchorsByChunk = HashMap<Long, Int>()

    override fun clone(): Resource<ChunkStore> = AnchorCoverageIndex()

    fun setAnchor(
        id: Long,
        chunkX: Int,
        chunkZ: Int,
        active: Boolean,
    ) {
        removeAnchor(id)
        if (!active) return
        val covered = LongArray(9)
        var index = 0
        for (x in chunkX - 1..chunkX + 1) {
            for (z in chunkZ - 1..chunkZ + 1) {
                val chunk = ChunkUtil.indexChunk(x, z)
                covered[index++] = chunk
                anchorsByChunk[chunk] = (anchorsByChunk[chunk] ?: 0) + 1
            }
        }
        coverageByAnchor[id] = covered
    }

    fun removeAnchor(id: Long) {
        coverageByAnchor.remove(id)?.forEach { chunk ->
            val count = anchorsByChunk[chunk] ?: return@forEach
            if (count <= 1) {
                anchorsByChunk.remove(chunk)
            } else {
                anchorsByChunk[chunk] = count - 1
            }
        }
    }

    fun covers(chunkIndex: Long): Boolean = anchorsByChunk.containsKey(chunkIndex)

    companion object {
        lateinit var type: ResourceType<ChunkStore, AnchorCoverageIndex>
    }
}
