package studio.craftory.core.multiblock

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

/** Derived per-world cache: footprint position to controller positions. Rebuilt from controllers on load. */
internal class MultiblockIndex : Resource<ChunkStore> {
    private val controllersByPart = HashMap<Long, LongArray>()
    private val partsByController = HashMap<Long, LongArray>()

    override fun clone(): Resource<ChunkStore> = MultiblockIndex()

    fun add(
        controller: Long,
        parts: LongArray,
    ) {
        remove(controller)
        partsByController[controller] = parts
        parts.forEach { part ->
            val current = controllersByPart[part]
            controllersByPart[part] = if (current == null) longArrayOf(controller) else current + controller
        }
    }

    fun remove(controller: Long) {
        partsByController.remove(controller)?.forEach { part ->
            val rest = controllersByPart[part]?.filter { it != controller }
            if (rest.isNullOrEmpty()) controllersByPart.remove(part) else controllersByPart[part] = rest.toLongArray()
        }
    }

    fun controllersFor(position: Long): LongArray = controllersByPart[position] ?: EMPTY

    fun controllerCount(): Int = partsByController.size

    companion object {
        private val EMPTY = LongArray(0)

        lateinit var type: ResourceType<ChunkStore, MultiblockIndex>
    }
}
