package studio.craftory.core.transport

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class ItemTransportSaveSystem : EntityEventSystem<ChunkStore, ChunkSaveEvent>(ChunkSaveEvent::class.java) {
    override fun getQuery(): Query<ChunkStore> = Query.and(WorldChunk.getComponentType())

    override fun handle(
        index: Int,
        archetypeChunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
        event: ChunkSaveEvent,
    ) {
        val chunk = event.chunk
        store.getResource(ItemTransportWorld.type).flushDirtyChunk(
            store,
            chunk.x,
            chunk.z,
            store.externalData.world.tick,
        )
    }
}
