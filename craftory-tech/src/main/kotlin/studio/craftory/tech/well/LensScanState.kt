package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.well.Glimmerwell
import java.util.UUID

internal data class LensWellSession(
    val pos: BlockPos,
    val ref: Ref<ChunkStore>,
    val well: Glimmerwell,
    val startTick: Long,
    val lockTicks: MutableList<Long> = mutableListOf(),
)

internal class LensScanState : Resource<EntityStore> {
    val sessions = HashMap<UUID, LensWellSession>()

    override fun clone(): Resource<EntityStore> = LensScanState()

    companion object {
        lateinit var type: ResourceType<EntityStore, LensScanState>
    }
}
