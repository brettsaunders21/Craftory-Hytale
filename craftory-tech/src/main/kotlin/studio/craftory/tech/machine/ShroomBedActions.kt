package studio.craftory.tech.machine

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.tech.CraftoryIds

internal object ShroomBedActions {
    fun harvest(
        world: World,
        state: ShroomBedState,
        blockState: BlockModule.BlockStateInfo,
        store: Store<ChunkStore>,
    ): Int {
        val quantity = state.stored
        if (quantity <= 0) return 0
        val position = Vector3i()
        if (!blockState.fillWorldPos(store, position)) return 0
        val entityStore = world.entityStore.store
        val holder =
            ItemComponent.generateItemDrop(
                entityStore,
                ItemStack(CraftoryIds.BOOMSHROOM_FUEL, quantity),
                Vector3d(position.x + 0.5, position.y + 1.0, position.z + 0.5),
                Rotation3f.IDENTITY,
                0f,
                0f,
                0f,
            ) ?: return 0
        if (entityStore.addEntity(holder, AddReason.SPAWN)?.isValid != true) return 0
        state.stored = 0
        blockState.markNeedsSaving(store)
        return quantity
    }
}
