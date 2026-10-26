package studio.craftory.tech.transport

import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos

internal object HurlChunks {
    fun refresh(
        world: World,
        ride: HurlRide,
    ): Boolean {
        val ahead = ride.blocksAhead()
        val sections = ahead.map { BlockPos.unpack(it) }.map { BlockPos(it.x shr 5, it.y shr 5, it.z shr 5) }.toSet()
        val wanted = sections.map { ChunkUtil.indexChunk(it.x, it.z) }.toSet()
        ride.wantedChunks.clear()
        ride.wantedChunks.addAll(wanted)
        (ride.heldChunks - wanted).forEach { release(world, ride, it) }
        wanted.forEach { key ->
            if (key !in ride.heldChunks && ride.pendingChunks.add(key)) requestChunk(world, ride, key)
        }
        ride.loadedSections.retainAll(sections.map { it.pack() }.toSet())
        sections.forEach { pos ->
            val key = pos.pack()
            if (key !in ride.loadedSections && ride.pendingSections.add(key)) requestSection(world, ride, pos)
        }
        refreshTimers(world, wanted, sections)
        return ride.heldChunks.containsAll(wanted) && sections.all { it.pack() in ride.loadedSections }
    }

    private fun refreshTimers(
        world: World,
        wanted: Set<Long>,
        sections: Set<BlockPos>,
    ) {
        wanted.forEach { key ->
            val grid = world.chunkStore
            grid.getChunkReference(key)?.takeIf { it.isValid }?.let {
                grid.store.getComponent(it, WorldChunk.getComponentType())?.resetActiveTimer()
            }
        }
        sections.forEach { pos ->
            world.chunkStore.getLoadedSectionReferences(pos.x, pos.z).forEach { ref ->
                if (ref.isValid) {
                    val section = world.chunkStore.store.getComponent(ref, ChunkSection.getComponentType())
                    if (section?.y == pos.y) section.resetActiveTimer()
                }
            }
        }
    }

    private fun requestChunk(
        world: World,
        ride: HurlRide,
        key: Long,
    ) {
        world.chunkStore.getChunkReferenceAsync(key, GetChunkFlags.SET_TICKING).whenComplete { ref, failure ->
            world.execute {
                ride.pendingChunks.remove(key)
                if (!ride.ended && key in ride.wantedChunks) {
                    val chunk =
                        ref?.takeIf { it.isValid }?.let {
                            world.chunkStore.store.getComponent(it, WorldChunk.getComponentType())
                        }
                    if (failure != null || chunk == null) {
                        ride.loadFailed = true
                    } else if (ride.heldChunks.add(key)) {
                        chunk.addKeepLoaded()
                        world.entityStore.store
                            .getResource(HurlCounters.type)
                            .chunkHolders++
                    }
                }
            }
        }
    }

    private fun requestSection(
        world: World,
        ride: HurlRide,
        pos: BlockPos,
    ) {
        world.chunkStore
            .getChunkSectionReferenceAsync(pos.x, pos.y, pos.z, GetChunkFlags.SET_TICKING)
            .whenComplete { ref, failure ->
                world.execute {
                    ride.pendingSections.remove(pos.pack())
                    if (!ride.ended) {
                        if (failure != null || ref == null || !ref.isValid) {
                            ride.loadFailed = true
                        } else {
                            ride.loadedSections.add(pos.pack())
                        }
                    }
                }
            }
    }

    fun releaseAll(
        world: World,
        ride: HurlRide,
    ) {
        ride.wantedChunks.clear()
        ride.heldChunks.toList().forEach { release(world, ride, it) }
    }

    private fun release(
        world: World,
        ride: HurlRide,
        key: Long,
    ) {
        if (!ride.heldChunks.remove(key)) return
        world.chunkStore.getChunkReference(key)?.takeIf { it.isValid }?.let {
            world.chunkStore.store
                .getComponent(it, WorldChunk.getComponentType())
                ?.removeKeepLoaded()
        }
        world.entityStore.store
            .getResource(HurlCounters.type)
            .chunkHolders--
    }
}
