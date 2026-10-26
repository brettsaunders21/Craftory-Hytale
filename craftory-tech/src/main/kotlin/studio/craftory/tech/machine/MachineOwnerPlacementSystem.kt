package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.tech.CraftoryIds
import java.util.UUID

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
        if (event.isCancelled) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val ownerUuid = player.uuid
        val position = Vector3i(event.targetBlock)
        val world = store.externalData.world
        world.execute {
            assignMachineOwner(world, position, itemId, ownerUuid)
        }
    }

    private companion object {
        val MACHINE_ITEMS =
            setOf(
                CraftoryIds.CRANK,
                CraftoryIds.BURNER,
                CraftoryIds.CANISTER,
                CraftoryIds.PIPE,
                CraftoryIds.HURL_STATION,
                CraftoryIds.HURL_CANNON,
                CraftoryIds.SNATCHER_CLAW,
                CraftoryIds.GNASHER_PIT,
                CraftoryIds.SCORCH_OVEN,
                CraftoryIds.JUNK_HEART,
            )
    }
}

internal fun assignMachineOwner(
    world: World,
    position: Vector3i,
    itemId: String,
    ownerUuid: UUID,
) {
    val placedBlockType = BlockType.fromString(itemId) ?: return
    val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
    val chunkStore = world.chunkStore.store
    val blockState =
        chunkStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
    val blockSection =
        chunkStore.getComponent(blockState.sectionRef, BlockSection.getComponentType()) ?: return
    val currentBlockType = BlockType.getAssetMap().getAsset(blockSection.get(blockState.index)) ?: return
    if (
        currentBlockType.id != placedBlockType.id &&
        placedBlockType.getStateForBlock(currentBlockType.id) == null
    ) {
        return
    }
    val owner = chunkStore.getComponent(ref, MachineOwner.type) ?: return
    if (owner.placerUuid != null) return
    owner.placerUuid = ownerUuid
    blockState.markNeedsSaving(chunkStore)
    val controller = chunkStore.getComponent(ref, MultiblockController.type) ?: return
    if (controller.state == MultiblockState.FORMED || controller.state == MultiblockState.DAMAGED) {
        JunkHeartKnowledge.onFormed(chunkStore, ref, controller)
    }
}
