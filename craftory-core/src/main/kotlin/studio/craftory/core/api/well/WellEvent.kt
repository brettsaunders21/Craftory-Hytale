package studio.craftory.core.api.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos

data class WellEvent(
    val world: World,
    val pos: BlockPos,
    val ref: Ref<ChunkStore>,
    val well: Glimmerwell,
)

data class WellScanEvent(
    val world: World,
    val pos: BlockPos,
    val well: Glimmerwell,
    val perfect: Boolean,
)
