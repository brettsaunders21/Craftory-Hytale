package studio.craftory.tech.well

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import java.util.UUID

internal class WellBirthState : Resource<ChunkStore> {
    val lastStormcallByRegion = HashMap<Long, Long>()
    val lastFlickerByPlayer = HashMap<UUID, Long>()
    val lastStormcallByPlayer = HashMap<UUID, Long>()

    override fun clone(): Resource<ChunkStore> = WellBirthState()

    companion object {
        lateinit var type: ResourceType<ChunkStore, WellBirthState>
    }
}

internal class WellPulseState : Resource<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> {
    val lastPulseByPosition = HashMap<Long, Long>()

    override fun clone(): Resource<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> = WellPulseState()

    companion object {
        lateinit var type:
            ResourceType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, WellPulseState>
    }
}
