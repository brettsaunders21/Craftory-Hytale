package studio.craftory.core.api.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class WellIndex : Resource<ChunkStore> {
    private val refsByPosition = LinkedHashMap<Long, Ref<ChunkStore>>()

    val size: Int
        get() = refsByPosition.size

    override fun clone(): Resource<ChunkStore> = WellIndex()

    fun add(
        position: Long,
        ref: Ref<ChunkStore>,
    ) {
        refsByPosition[position] = ref
    }

    fun remove(position: Long) {
        refsByPosition.remove(position)
    }

    fun entries(): List<Pair<Long, Ref<ChunkStore>>> = refsByPosition.map { it.key to it.value }

    companion object {
        lateinit var type: ResourceType<ChunkStore, WellIndex>
    }
}
