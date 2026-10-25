package studio.craftory.core.multiblock

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3ic
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore

internal class MultiblockPlaceSystem :
    EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent,
    ) {
        if (!event.isCancelled) recheckFootprint(store, event.targetBlock)
    }
}

internal class MultiblockBreakSystem :
    EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent,
    ) {
        if (!event.isCancelled) recheckFootprint(store, event.targetBlock)
    }
}

/** The world changes after the event, so the one affected controller is re-checked on the next world task. */
private fun recheckFootprint(
    store: Store<EntityStore>,
    target: Vector3ic,
) {
    val world = store.externalData.world
    val position = BlockPos(target.x(), target.y(), target.z())
    val chunks = world.chunkStore.store
    if (chunks.getResource(MultiblockIndex.type).controllerFor(position.pack()) == null) return
    world.execute {
        val store = world.chunkStore.store
        val controller = CraftoryCore.multiblocks().controllerAt(store, position) ?: return@execute
        CraftoryCore.multiblocks().recheck(store, controller)
    }
}
