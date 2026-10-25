package studio.craftory.core.multiblock

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

/** Derived per-world cache: footprint position to controller position. Rebuilt from controllers on load. */
internal class MultiblockIndex : Resource<ChunkStore> {
    private val controllerByPart = HashMap<Long, Long>()
    private val partsByController = HashMap<Long, LongArray>()

    override fun clone(): Resource<ChunkStore> = MultiblockIndex()

    fun add(
        controller: Long,
        parts: LongArray,
    ) {
        remove(controller)
        partsByController[controller] = parts
        parts.forEach { controllerByPart[it] = controller }
    }

    fun remove(controller: Long) {
        partsByController.remove(controller)?.forEach { part ->
            if (controllerByPart[part] == controller) controllerByPart.remove(part)
        }
    }

    fun controllerFor(position: Long): Long? = controllerByPart[position]

    fun controllerCount(): Int = partsByController.size

    companion object {
        lateinit var type: ResourceType<ChunkStore, MultiblockIndex>
    }
}
