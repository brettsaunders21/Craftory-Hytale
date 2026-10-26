package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import java.util.concurrent.ConcurrentHashMap

internal object WellSelfTestExclusions {
    private val blocks: MutableSet<Ref<ChunkStore>> = ConcurrentHashMap.newKeySet()
    private val entities: MutableSet<Ref<EntityStore>> = ConcurrentHashMap.newKeySet()
    private val wells: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun registerBlock(ref: Ref<ChunkStore>) {
        blocks.add(ref)
    }

    fun releaseBlock(ref: Ref<ChunkStore>) {
        blocks.remove(ref)
    }

    fun isBlock(ref: Ref<ChunkStore>): Boolean = ref in blocks

    fun registerEntity(ref: Ref<EntityStore>) {
        entities.add(ref)
    }

    fun releaseEntity(ref: Ref<EntityStore>) {
        entities.remove(ref)
    }

    fun isEntity(ref: Ref<EntityStore>): Boolean = ref in entities

    fun registerWell(position: BlockPos) {
        wells.add(position.pack())
    }

    fun releaseWell(position: BlockPos) {
        wells.remove(position.pack())
    }

    fun isWell(position: BlockPos): Boolean = position.pack() in wells

    fun cleanupCounts(): Triple<Int, Int, Int> = Triple(blocks.size, entities.size, wells.size)
}
