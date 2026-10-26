package studio.craftory.tech.well

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.UUID

internal data class CaptureProgress(
    val wellId: String,
    val pressCount: Int,
    val lastPressTick: Long,
)

internal class WellCaptureState : Resource<EntityStore> {
    val progressByPlayer = HashMap<UUID, CaptureProgress>()

    override fun clone(): Resource<EntityStore> =
        WellCaptureState().also { it.progressByPlayer.putAll(progressByPlayer) }

    companion object {
        lateinit var type: ResourceType<EntityStore, WellCaptureState>
    }
}
