package studio.craftory.tech.well

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore

internal fun markWellNeedsSaving(
    store: Store<ChunkStore>,
    position: BlockPos,
) {
    val wellRef = CraftoryCore.wells().wellsNear(store, position, 0).firstOrNull() ?: return
    val stateInfo = store.getComponent(wellRef.ref, BlockModule.BlockStateInfo.getComponentType())
    if (stateInfo != null) stateInfo.markNeedsSaving(store)
}
