package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.function.consumer.TriConsumer
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.NPCPlugin
import com.hypixel.hytale.server.npc.entities.NPCEntity
import com.hypixel.hytale.server.spawning.SpawnTestResult
import org.joml.Vector3d
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

internal data class GrizzlecogVisitRecord(
    val uuid: String,
    val spawnedAtNanos: Long,
)

internal enum class GrizzlecogVisitExpiry {
    KEEP,
    DESPAWN_AND_DROP,
    DROP,
}

internal class GrizzlecogVisitLedger(
    var npcIds: Array<String> = emptyArray(),
    var spawnedAtNanos: LongArray = longArrayOf(),
) : Resource<ChunkStore> {
    fun records(): List<GrizzlecogVisitRecord> =
        (0 until minOf(npcIds.size, spawnedAtNanos.size)).map {
            GrizzlecogVisitRecord(npcIds[it], spawnedAtNanos[it])
        }

    fun replace(records: List<GrizzlecogVisitRecord>) {
        npcIds = records.map { it.uuid }.toTypedArray()
        spawnedAtNanos = records.map { it.spawnedAtNanos }.toLongArray()
    }

    fun add(record: GrizzlecogVisitRecord) = replace(records() + record)

    override fun clone(): Resource<ChunkStore> = GrizzlecogVisitLedger(npcIds.copyOf(), spawnedAtNanos.copyOf())

    companion object {
        lateinit var type: ResourceType<ChunkStore, GrizzlecogVisitLedger>
        val CODEC: BuilderCodec<GrizzlecogVisitLedger> =
            BuilderCodec
                .builder(GrizzlecogVisitLedger::class.java, ::GrizzlecogVisitLedger)
                .append(
                    KeyedCodec("NpcIds", Codec.STRING_ARRAY),
                    { state, value -> state.npcIds = value },
                    { it.npcIds },
                ).add()
                .append(
                    KeyedCodec("SpawnedAtNanos", Codec.LONG_ARRAY),
                    { state, value -> state.spawnedAtNanos = value },
                    { it.spawnedAtNanos },
                ).add()
                .build()
    }
}

internal object GrizzlecogVisitLogic {
    fun needsNpcLookup(
        nowNanos: Long,
        record: GrizzlecogVisitRecord,
    ): Boolean = (nowNanos - record.spawnedAtNanos).coerceAtLeast(0L) >= WorldTimeResource.NANOS_PER_DAY

    fun shouldCheckExpiry(
        nowNanos: Long,
        lastCheckNanos: Long,
    ): Boolean =
        lastCheckNanos == Long.MIN_VALUE ||
            nowNanos - lastCheckNanos >= NANOS_PER_SECOND

    fun expiryDecision(
        nowNanos: Long,
        record: GrizzlecogVisitRecord,
        npcLoaded: Boolean,
    ): GrizzlecogVisitExpiry {
        val age = (nowNanos - record.spawnedAtNanos).coerceAtLeast(0L)
        if (age >= 7L * WorldTimeResource.NANOS_PER_DAY) return GrizzlecogVisitExpiry.DROP
        if (age < WorldTimeResource.NANOS_PER_DAY || !npcLoaded) return GrizzlecogVisitExpiry.KEEP
        return GrizzlecogVisitExpiry.DESPAWN_AND_DROP
    }

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

    private const val NANOS_PER_SECOND = 1_000_000_000L
}

internal class GrizzlecogVisitSystem(
    private val settings: GrizzlecogVisitsSettings,
) : TickingSystem<ChunkStore>() {
    private data class ScheduleState(
        var lastEvaluationTick: Long = Long.MIN_VALUE,
        var lastGameTimeNanos: Long = Long.MIN_VALUE,
        var lastExpiryCheckNanos: Long = Long.MIN_VALUE,
    )

    private val schedules = mutableMapOf<UUID, ScheduleState>()

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
        val schedule = schedules.getOrPut(worldId, ::ScheduleState)
        if (GrizzlecogVisitLogic.shouldCheckExpiry(now, schedule.lastExpiryCheckNanos)) {
            schedule.lastExpiryCheckNanos = now
            despawnExpiredVisits(world, store, now)
        }
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
        spawnVisit(world, now, candidate, player)
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
        storeVisit(world, uuid, now)
        player.sendMessage(Message.translation("server.craftory.grizzlecog.visit"))
    }

    private fun despawnExpiredVisits(
        world: World,
        store: Store<ChunkStore>,
        now: Long,
    ) {
        val ledger = store.getResource(GrizzlecogVisitLedger.type)
        val records = ledger.records()
        val remaining =
            records.filter { record ->
                val loaded =
                    GrizzlecogVisitLogic.needsNpcLookup(now, record) &&
                        isGrizzlecogPresent(world, record.uuid)
                when (GrizzlecogVisitLogic.expiryDecision(now, record, loaded)) {
                    GrizzlecogVisitExpiry.KEEP -> {
                        true
                    }

                    GrizzlecogVisitExpiry.DESPAWN_AND_DROP -> {
                        despawnByIds(world, setOf(record.uuid))
                        false
                    }

                    GrizzlecogVisitExpiry.DROP -> {
                        if (loaded) despawnByIds(world, setOf(record.uuid))
                        false
                    }
                }
            }
        if (remaining != records) ledger.replace(remaining)
    }

    companion object {
        @Suppress("TooGenericExceptionCaught")
        fun startForSelfTest(
            world: World,
            position: Vector3i,
            onComplete: (Boolean, String?, String) -> Unit,
        ) {
            val result =
                try {
                    if (!hasSolidTestFloor(world, position)) {
                        Triple(false, null, "spawn=skipped solid-floor=false")
                    } else {
                        val spawn =
                            NPCPlugin.get().spawnNPC(
                                world.entityStore.store,
                                CraftoryIds.GRIZZLECOG,
                                null,
                                Vector3d(position.x + 0.5, position.y.toDouble(), position.z + 0.5),
                                Rotation3f(0f, 0f, 0f),
                            )
                        val ref = spawn?.left()
                        val npc = spawn?.right() as? NPCEntity
                        val uuid =
                            ref?.let {
                                world.entityStore.store
                                    .getComponent(it, UUIDComponent.getComponentType())
                                    ?.uuid
                                    ?.toString()
                            }
                        if (ref != null && (npc?.roleName != CraftoryIds.GRIZZLECOG || uuid == null)) {
                            world.entityStore.store.removeEntity(ref, RemoveReason.REMOVE)
                        }
                        if (uuid != null && npc?.roleName == CraftoryIds.GRIZZLECOG) {
                            storeVisit(world, uuid, gameTimeNanos(world) ?: 0L)
                        }
                        val present = uuid != null && isGrizzlecogPresent(world, uuid)
                        Triple(present, uuid, "spawn=${uuid ?: "failed"} present=$present floor=solid")
                    }
                } catch (failure: Exception) {
                    Triple(false, null, "spawn=failed error=${failure.message ?: failure.javaClass.simpleName}")
                }
            onComplete(result.first, result.second, result.third)
        }

        fun despawnForSelfTest(
            world: World,
            uuid: String,
        ): Boolean {
            findGrizzlecog(world, uuid)?.let { world.entityStore.store.removeEntity(it, RemoveReason.REMOVE) }
            val ledger = world.chunkStore.store.getResource(GrizzlecogVisitLedger.type)
            ledger.replace(ledger.records().filterNot { it.uuid == uuid })
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

        private fun storeVisit(
            world: World,
            uuid: String,
            spawnedAtNanos: Long,
        ) {
            world.chunkStore.store
                .getResource(GrizzlecogVisitLedger.type)
                .add(GrizzlecogVisitRecord(uuid, spawnedAtNanos))
        }

        private fun hasSolidTestFloor(
            world: World,
            position: Vector3i,
        ): Boolean {
            val x = position.x
            val y = position.y - 1
            val z = position.z
            val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(x, y, z) ?: return false
            val section =
                world.chunkStore.store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return false
            val blockType = BlockType.getAssetMap().getAsset(section.get(x, y, z)) ?: return false
            return blockType.id == CraftoryIds.SCRAP_PLATING
        }

        private fun gameTimeNanos(world: World): Long? {
            val gameTime =
                world.entityStore.store
                    .getResource(WorldTimeResource.getResourceType())
                    .gameTime ?: return null
            return gameTime.epochSecond * 1_000_000_000L + gameTime.nano
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
