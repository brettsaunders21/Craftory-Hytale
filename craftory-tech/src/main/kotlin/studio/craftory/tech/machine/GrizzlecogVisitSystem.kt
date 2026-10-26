package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.function.consumer.TriConsumer
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.NPCPlugin
import com.hypixel.hytale.server.npc.entities.NPCEntity
import com.hypixel.hytale.server.spawning.SpawnTestResult
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.GrizzlecogVisitsSettings
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.function.BiConsumer
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal data class GrizzlecogVisitCandidate(
    val uuid: UUID,
    val x: Double,
    val y: Double,
    val z: Double,
    val online: Boolean = true,
    val areaActive: Boolean = true,
)

internal data class GrizzlecogVisitPosition(
    val x: Double,
    val y: Double,
    val z: Double,
)

internal object GrizzlecogVisitLogic {
    fun chanceFromElapsed(
        elapsedNanos: Long,
        averageIntervalInGameDays: Double,
    ): Double {
        val intervalNanos = averageIntervalInGameDays.coerceAtLeast(0.001) * WorldTimeResource.NANOS_PER_DAY
        return (elapsedNanos.coerceAtLeast(0L) / intervalNanos).coerceIn(0.0, 1.0)
    }

    fun eligiblePlayer(
        players: List<GrizzlecogVisitCandidate>,
        nearbyGrizzlecogs: List<GrizzlecogVisitPosition>,
        nearbyRadius: Int,
    ): GrizzlecogVisitCandidate? {
        val radiusSquared = nearbyRadius.coerceAtLeast(0).toDouble().let { it * it }
        return players.firstOrNull { player ->
            player.online &&
                player.areaActive &&
                nearbyGrizzlecogs.none { npc ->
                    val dx = player.x - npc.x
                    val dy = player.y - npc.y
                    val dz = player.z - npc.z
                    dx * dx + dy * dy + dz * dz <= radiusSquared
                }
        }
    }
}

internal class GrizzlecogVisitSystem(
    private val settings: GrizzlecogVisitsSettings,
) : TickingSystem<ChunkStore>() {
    private data class ScheduleState(
        var lastEvaluationTick: Long = Long.MIN_VALUE,
        var lastGameTimeNanos: Long = Long.MIN_VALUE,
    )

    private data class ActiveVisit(
        val uuid: String,
        val spawnedAtNanos: Long,
    )

    private val schedules = mutableMapOf<UUID, ScheduleState>()
    private val activeVisits = mutableMapOf<UUID, MutableList<ActiveVisit>>()

    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val world = store.externalData.world
        val worldId = world.worldConfig.uuid
        val gameTime =
            world.entityStore.store
                .getResource(WorldTimeResource.getResourceType())
                .gameTime ?: return
        val now = gameTime.epochSecond * 1_000_000_000L + gameTime.nano
        despawnExpiredVisits(world, worldId, now)

        val schedule = schedules.getOrPut(worldId, ::ScheduleState)
        if (schedule.lastEvaluationTick == world.tick) return
        schedule.lastEvaluationTick = world.tick
        val elapsed =
            if (schedule.lastGameTimeNanos == Long.MIN_VALUE) {
                0L
            } else {
                (now - schedule.lastGameTimeNanos).coerceAtLeast(0L)
            }
        schedule.lastGameTimeNanos = now
        if (!settings.enabled) return
        if (ThreadLocalRandom.current().nextDouble() >=
            GrizzlecogVisitLogic.chanceFromElapsed(elapsed, settings.averageIntervalInGameDays)
        ) {
            return
        }

        val players = eligibleCandidates(world, store)
        val nearbyGrizzlecogs = nearbyGrizzlecogPositions(world)
        val candidate =
            GrizzlecogVisitLogic.eligiblePlayer(
                players.map { it.first },
                nearbyGrizzlecogs,
                settings.nearbyRadius,
            ) ?: return
        val player = players.firstOrNull { it.first.uuid == candidate.uuid }?.second ?: return
        spawnVisit(world, worldId, now, candidate, player)
    }

    private fun eligibleCandidates(
        world: World,
        chunkStore: Store<ChunkStore>,
    ): List<Pair<GrizzlecogVisitCandidate, PlayerRef>> {
        val worldId = world.worldConfig.uuid
        return Universe.get().getPlayers().mapNotNull { player ->
            if (player.worldUuid != worldId) return@mapNotNull null
            val position = player.transform.position
            val sectionRef =
                world.chunkStore.getChunkSectionReferenceAtBlock(
                    floor(position.x).toInt(),
                    floor(position.y).toInt(),
                    floor(position.z).toInt(),
                ) ?: return@mapNotNull null
            if (!CraftoryCore.isAreaActive(chunkStore, sectionRef)) return@mapNotNull null
            GrizzlecogVisitCandidate(player.uuid, position.x, position.y, position.z) to player
        }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun nearbyGrizzlecogPositions(world: World): List<GrizzlecogVisitPosition> {
        val positions = mutableListOf<GrizzlecogVisitPosition>()
        val npcType = NPCEntity.getComponentType() ?: return positions
        world.entityStore.store.forEachChunk(
            Query.and(npcType, TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val npc = chunk.getComponent(index, npcType) ?: continue
                    if (npc.roleName != CraftoryIds.GRIZZLECOG) continue
                    val point = chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: continue
                    positions.add(GrizzlecogVisitPosition(point.x, point.y, point.z))
                }
            },
        )
        return positions
    }

    private fun spawnVisit(
        world: World,
        worldId: UUID,
        now: Long,
        candidate: GrizzlecogVisitCandidate,
        player: PlayerRef,
    ) {
        val minDistance = settings.spawnDistanceMin.coerceAtLeast(1)
        val maxDistance = settings.spawnDistanceMax.coerceAtLeast(minDistance)
        val angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2)
        val distance =
            ThreadLocalRandom.current().nextDouble(minDistance.toDouble(), maxDistance.toDouble() + 1.0)
        val position =
            Vector3i(
                floor(candidate.x + distance * cos(angle)).toInt(),
                floor(candidate.y).toInt(),
                floor(candidate.z + distance * sin(angle)).toInt(),
            )
        val uuid =
            spawnGrizzlecogWithColumnProbe(
                world,
                position.x,
                position.y,
                position.z,
            ) ?: return
        activeVisits.getOrPut(worldId, ::mutableListOf).add(ActiveVisit(uuid, now))
        player.sendMessage(Message.translation("server.craftory.grizzlecog.visit"))
    }

    private fun despawnExpiredVisits(
        world: World,
        worldId: UUID,
        now: Long,
    ) {
        val visits = activeVisits[worldId] ?: return
        val expired =
            visits.filter { now - it.spawnedAtNanos >= WorldTimeResource.NANOS_PER_DAY }
        if (expired.isEmpty()) return
        despawnByIds(world, expired.mapTo(mutableSetOf()) { it.uuid })
        visits.removeAll(expired.toSet())
    }

    companion object {
        fun startForSelfTest(
            world: World,
            position: Vector3i,
            onComplete: (Boolean, String?, String) -> Unit,
        ) {
            world.execute {
                val uuid = spawnGrizzlecogWithColumnProbe(world, position.x, position.y, position.z)
                val present = uuid != null && isGrizzlecogPresent(world, uuid)
                onComplete(present, uuid, "spawn=${uuid ?: "failed"} present=$present")
            }
        }

        fun despawnForSelfTest(
            world: World,
            uuid: String,
        ): Boolean {
            val ref = findGrizzlecog(world, uuid) ?: return false
            world.entityStore.store.removeEntity(ref, RemoveReason.REMOVE)
            return !isGrizzlecogPresent(world, uuid)
        }

        fun isGrizzlecogPresent(
            world: World,
            uuid: String,
        ): Boolean = findGrizzlecog(world, uuid) != null

        private fun spawnGrizzlecogWithColumnProbe(
            world: World,
            x: Int,
            y: Int,
            z: Int,
        ): String? {
            var uuid: String? = null
            val onSpawn =
                TriConsumer<NPCEntity, Ref<EntityStore>, Store<EntityStore>> { _, ref, entityStore ->
                    uuid = entityStore.getComponent(ref, UUIDComponent.getComponentType())?.uuid?.toString()
                }
            val result =
                NPCPlugin.get().spawnNPCWithColumnProbe(
                    world.entityStore.store,
                    CraftoryIds.GRIZZLECOG,
                    null,
                    world,
                    x,
                    z,
                    y.toDouble(),
                    Rotation3f(0f, 0f, 0f),
                    onSpawn,
                )
            return uuid.takeIf { result == SpawnTestResult.TEST_OK }
        }

        @Suppress("LoopWithTooManyJumpStatements")
        private fun findGrizzlecog(
            world: World,
            uuid: String,
        ): Ref<EntityStore>? {
            val target = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return null
            var found: Ref<EntityStore>? = null
            val npcType = NPCEntity.getComponentType() ?: return null
            world.entityStore.store.forEachChunk(
                Query.and(UUIDComponent.getComponentType(), npcType),
                BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                    for (index in 0 until chunk.size()) {
                        val npc = chunk.getComponent(index, npcType) ?: continue
                        val currentUuid =
                            chunk.getComponent(index, UUIDComponent.getComponentType())?.uuid ?: continue
                        if (currentUuid == target && npc.roleName == CraftoryIds.GRIZZLECOG) {
                            found = chunk.getReferenceTo(index)
                            return@BiConsumer
                        }
                    }
                },
            )
            return found?.takeIf { it.isValid }
        }

        @Suppress("LoopWithTooManyJumpStatements")
        private fun despawnByIds(
            world: World,
            uuids: Set<String>,
        ) {
            val targets = uuids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
            val npcType = NPCEntity.getComponentType() ?: return
            world.entityStore.store.forEachChunk(
                Query.and(UUIDComponent.getComponentType(), npcType),
                BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                    for (index in 0 until chunk.size()) {
                        val npc = chunk.getComponent(index, npcType) ?: continue
                        if (npc.roleName != CraftoryIds.GRIZZLECOG) continue
                        val uuid =
                            chunk.getComponent(index, UUIDComponent.getComponentType())?.uuid ?: continue
                        if (uuid in
                            targets
                        ) {
                            commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
                        }
                    }
                },
            )
        }
    }
}
