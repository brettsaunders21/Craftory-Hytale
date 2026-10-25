package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.tech.CraftoryIds

internal class MachineOwnerPlacementSystem :
    EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent,
    ) {
        val itemId = event.itemInHand?.itemId ?: return
        if (itemId !in MACHINE_ITEMS) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val ownerUuid = player.uuid
        val position = Vector3i(event.targetBlock)
        val world = store.externalData.world
        world.execute {
            val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return@execute
            val chunkStore = world.chunkStore.store
            val owner = chunkStore.getComponent(ref, MachineOwner.type) ?: return@execute
            owner.placerUuid = ownerUuid
            val blockState = chunkStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
            blockState?.markNeedsSaving(chunkStore)
        }
    }

    private companion object {
        val MACHINE_ITEMS =
            setOf(
                CraftoryIds.CRANK,
                CraftoryIds.BURNER,
                CraftoryIds.CANISTER,
                CraftoryIds.PIPE,
                CraftoryIds.GNASHER_PIT,
                CraftoryIds.SCORCH_OVEN,
            )
    }
}
