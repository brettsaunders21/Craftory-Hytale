package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal const val TICKS_PER_SECOND = 20L
internal const val CLEANSING_RADIUS = 16
internal const val FEED_DISTANCE_SQUARED = 36.0
internal const val MAX_SPAWN_DISTANCE = 12
internal const val BEAM_REFRESH_TICKS = 40L
internal const val FEED_BEAM_ID = "Craftory_Riftmoth_Feed"
internal const val SIPHON_BEAM_ID = "Craftory_Slurp_Siphon"
internal const val SIPHON_DISTANCE_SQUARED = 16.0
internal const val WALKER_DEATH_TIMEOUT_SECONDS = 180L
internal val SURFACE_OFFSETS = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1, 1 to 1, -1 to 1, -1 to -1, 1 to -1)
internal val lastMothAttempt = ConcurrentHashMap<WellKey, Long>()
internal val lastVoidAttempt = ConcurrentHashMap<WellKey, Long>()
internal val lastRaidAttempt = ConcurrentHashMap<WellKey, Long>()
internal val feedSeconds = ConcurrentHashMap<WellKey, Int>()
internal val siphonSeconds = ConcurrentHashMap<WellKey, Int>()
internal val feedBeams = ConcurrentHashMap<UUID, Ref<EntityStore>>()
internal val siphonBeams = ConcurrentHashMap<UUID, Ref<EntityStore>>()

internal data class WellKey(
    val worldId: UUID,
    val wellPosition: Long,
)

internal data class FeedingMoth(
    val uuid: UUID,
    val position: Vector3d,
)
