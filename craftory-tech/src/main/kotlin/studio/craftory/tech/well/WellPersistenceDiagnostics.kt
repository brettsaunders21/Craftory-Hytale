package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.well.Glimmerwell

internal fun describePersistedWellHolder(
    store: Store<ChunkStore>,
    sectionRef: Ref<ChunkStore>,
    position: BlockPos,
): String {
    val index = ChunkUtil.indexBlock(position.x and 31, position.y and 31, position.z and 31)
    val well =
        store
            .getComponent(sectionRef, BlockComponentSection.getComponentType())
            ?.getBlockHolder(index)
            ?.getComponent(Glimmerwell.type)
    return "holderWell=${well != null}, holderState=${well?.state}, holderClaim=${well?.claimCrewId}"
}
