package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.function.consumer.TriConsumer
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.path.IPath
import com.hypixel.hytale.server.core.universe.world.path.IPathWaypoint
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.NPCPlugin
import com.hypixel.hytale.server.npc.entities.NPCEntity
import com.hypixel.hytale.server.spawning.SpawnTestResult
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.ScrapRushSettings
import studio.craftory.tech.machine.jank.BodgeSlots
import studio.craftory.tech.machine.jank.Jank
import studio.craftory.tech.notebook.NotebookPages
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.function.BiConsumer

@Suppress("TooManyFunctions")
internal class ScrapRushSystem(
    private val settings: ScrapRushSettings,
    private val crews: CrewService = CraftoryCore.crewService(),
    private val requireOnlineCrew: Boolean = true,
    private val directSpawnForSelfTest: Boolean = false,
    private val evaluationIntervalTicks: Long = EVALUATION_TICKS,
    private val areaIsActive: (Store<ChunkStore>, BlockModule.BlockStateInfo) -> Boolean = { store, blockState ->
        CraftoryCore.isAreaActive(store, blockState)
    },
) : EntityTickingSystem<ChunkStore>() {
    internal var lastStartDiagnostic: String = ""
        private set
    internal var lastSpawnDiagnostic: String = ""
        private set

    private data class ScheduleState(
        var lastEvaluationTick: Long = Long.MIN_VALUE,
        var lastGameTimeNanos: Long = Long.MIN_VALUE,
    )

    private val schedules = mutableMapOf<UUID, ScheduleState>()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            MachineOwner.type,
            Jank.type,
            BodgeSlots.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.AFTER, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        super.tick(dt, chunk, store, commandBuffer)
    }

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val world = store.externalData.world
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!areaIsActive(store, blockState)) return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        if (member.typeId !in RAIDABLE_MACHINE_TYPES) return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val blockRef = chunk.getReferenceTo(index)
        val raid = commandBuffer.getComponent(blockRef, ScrapRushRaid.type)
        if (raid != null) {
            processRaid(
                blockRef,
                raid,
                chunk.getComponent(index, Jank.type) ?: return,
                member.typeId,
                blockState,
                position,
                store,
                commandBuffer,
            )
            return
        }
        if (settings.enabled && world.tick % evaluationIntervalTicks == 0L) evaluateSchedule(world, store)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun processRaid(
        blockRef: com.hypixel.hytale.component.Ref<ChunkStore>,
        raid: ScrapRushRaid,
        jank: Jank,
        machineType: String,
        blockState: BlockModule.BlockStateInfo,
        position: Vector3i,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val world = store.externalData.world
        if (world.tick % evaluationIntervalTicks != 0L) return
        val entityStore = world.entityStore
        val target = Vector3d(position.x + 0.5, position.y + 0.5, position.z + 0.5)
        var alive = 0
        var adjacent = false
        raid.npcIds.forEach { id ->
            val ref = entityStore.getRefFromUUID(UUID.fromString(id)) ?: return@forEach
            if (!ref.isValid) return@forEach
            val transform =
                entityStore.store.getComponent(ref, TransformComponent.getComponentType()) ?: return@forEach
            alive++
            if (transform.position.distanceSquared(target) <= STEAL_DISTANCE_SQUARED) adjacent = true
        }
        if (alive == 0) {
            val owner = commandBuffer.getComponent(blockRef, MachineOwner.type)?.placerUuid
            owner?.let { crews.grantKnowledge(it, NotebookPages.SCRAP_RUSH_PAGE_ID) }
            jank.stolen = false
            commandBuffer.removeComponent(blockRef, ScrapRushRaid.type)
            blockState.markNeedsSaving(commandBuffer)
            notifyCrew(
                owner,
                Message
                    .translation("server.craftory.scraprush.defeated")
                    .param("position", "${position.x}, ${position.y}, ${position.z}"),
            )
            return
        }
        val newAdjacentTicks = if (adjacent) raid.adjacentTicks + evaluationIntervalTicks.toInt() else 0
        if (newAdjacentTicks != raid.adjacentTicks) {
            raid.adjacentTicks = newAdjacentTicks
            if (!jank.stolen && raid.adjacentTicks >= settings.stealSeconds * TICKS_PER_SECOND) {
                jank.stolen = true
                if (commandBuffer.getComponent(blockRef, BodgeSlots.type)?.modifiers()?.squawk == true) {
                    notifyCrew(
                        commandBuffer.getComponent(blockRef, MachineOwner.type)?.placerUuid,
                        Message
                            .translation("server.craftory.bodge.squawk")
                            .param("machine", machineName(machineType))
                            .param("position", "${position.x}, ${position.y}, ${position.z}"),
                    )
                }
            }
            blockState.markNeedsSaving(commandBuffer)
        }
    }

    private fun startRaid(
        blockRef: com.hypixel.hytale.component.Ref<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
        position: Vector3i,
        world: World,
        commandBuffer: CommandBuffer<ChunkStore>,
    ): Boolean {
        val owner =
            commandBuffer.getComponent(blockRef, MachineOwner.type)?.placerUuid
                ?: run {
                    lastStartDiagnostic = "machine owner missing"
                    return false
                }
        val crew = crews.crewOf(owner)
        val members = crew.members.ifEmpty { setOf(owner) }
        if (
            requireOnlineCrew &&
            members.none { member ->
                isOnlineNear(member, world, position, settings.crewRadius)
            }
        ) {
            lastStartDiagnostic = "no online crew member nearby"
            return false
        }
        val minimum = settings.minGoblins.coerceIn(1, 5)
        val maximum = settings.maxGoblins.coerceAtLeast(minimum).coerceAtMost(5)
        val count = ThreadLocalRandom.current().nextInt(minimum, maximum + 1)
        val ids = spawnGoblins(world, position, count)
        if (ids.size != count) {
            lastStartDiagnostic = "spawned ${ids.size} of $count"
            despawn(ids, world)
            return false
        }
        val startedAt =
            world.entityStore.store
                .getResource(WorldTimeResource.getResourceType())
                ?.gameTime
                ?.toEpochMilli() ?: 0L
        commandBuffer.addComponent(
            blockRef,
            ScrapRushRaid.type,
            ScrapRushRaid(ids.toTypedArray(), 0, crew.id.toString(), startedAt),
        )
        notifyCrew(
            owner,
            Message
                .translation("server.craftory.scraprush.raiding")
                .param("position", "${position.x}, ${position.y}, ${position.z}"),
        )
        blockState.markNeedsSaving(commandBuffer)
        lastStartDiagnostic = "started"
        return true
    }

    internal fun startForSelfTest(
        blockRef: com.hypixel.hytale.component.Ref<ChunkStore>,
        world: World,
        commandBuffer: CommandBuffer<ChunkStore>,
    ): Boolean {
        lastStartDiagnostic = "entered test helper"
        val blockState =
            commandBuffer.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType())
                ?: run {
                    lastStartDiagnostic = "block state missing"
                    return false
                }
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) {
            lastStartDiagnostic = "machine position unavailable"
            return false
        }
        return startRaid(blockRef, blockState, position, world, commandBuffer)
    }

    internal fun despawnForSelfTest(
        ids: List<String>,
        world: World,
    ) {
        despawn(ids, world)
    }

    @Suppress("LongMethod")
    private fun spawnGoblins(
        world: World,
        position: Vector3i,
        count: Int,
    ): List<String> {
        val spawned = mutableListOf<String>()
        for (goblin in 0 until count) {
            for (attempt in 0 until SPAWN_ATTEMPTS) {
                val angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2)
                val distance = ThreadLocalRandom.current().nextDouble(8.0, 12.0)
                val x = (position.x + distance * kotlin.math.cos(angle)).toInt()
                val z = (position.z + distance * kotlin.math.sin(angle)).toInt()
                var id: String? = null
                val onSpawn =
                    TriConsumer<NPCEntity, Ref<EntityStore>, Store<EntityStore>> { npc, ref, entityStore ->
                        val uuid =
                            entityStore.getComponent(ref, UUIDComponent.getComponentType())?.uuid
                                ?: return@TriConsumer
                        id = uuid.toString()
                        npc.leashPoint = Vector3d(position.x + 0.5, position.y + 0.5, position.z + 0.5)
                        npc.pathManager.setTransientPath(
                            RaidPath(Vector3d(position.x + 0.5, position.y + 0.5, position.z + 0.5)),
                        )
                    }
                val result =
                    if (directSpawnForSelfTest) {
                        id = spawnNpcDirectlyForSelfTest(world, position, x, z)
                        if (id != null) SpawnTestResult.TEST_OK else SpawnTestResult.FAIL_INVALID_POSITION
                    } else {
                        NPCPlugin.get().spawnNPCWithColumnProbe(
                            world.entityStore.store,
                            GOBLIN_ROLE,
                            null,
                            world,
                            x,
                            z,
                            position.y.toDouble(),
                            Rotation3f(0f, 0f, 0f),
                            onSpawn,
                        )
                    }
                lastSpawnDiagnostic =
                    if (directSpawnForSelfTest) {
                        "direct test spawn uuid=${id != null}"
                    } else {
                        "result=$result uuid=${id != null} at=$x,${position.y},$z"
                    }
                if (result == SpawnTestResult.TEST_OK && id != null) {
                    spawned.add(id)
                    break
                }
            }
        }
        return spawned
    }

    private fun spawnNpcDirectlyForSelfTest(
        world: World,
        position: Vector3i,
        x: Int,
        z: Int,
    ): String? {
        val spawnedNpc =
            NPCPlugin.get().spawnNPC(
                world.entityStore.store,
                GOBLIN_ROLE,
                null,
                Vector3d(x + 0.5, position.y.toDouble(), z + 0.5),
                Rotation3f(0f, 0f, 0f),
            ) ?: return null
        val npc = spawnedNpc.right() as? NPCEntity ?: return null
        val uuid =
            world.entityStore.store
                .getComponent(
                    spawnedNpc.left(),
                    UUIDComponent.getComponentType(),
                )?.uuid ?: return null
        npc.leashPoint = Vector3d(position.x + 0.5, position.y + 0.5, position.z + 0.5)
        npc.pathManager.setTransientPath(
            RaidPath(Vector3d(position.x + 0.5, position.y + 0.5, position.z + 0.5)),
        )
        return uuid.toString()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
    private fun evaluateSchedule(
        world: World,
        store: Store<ChunkStore>,
    ) {
        val schedule = schedules.getOrPut(world.worldConfig.uuid, ::ScheduleState)
        if (schedule.lastEvaluationTick == world.tick) return
        schedule.lastEvaluationTick = world.tick
        val gameTime =
            world.entityStore.store
                .getResource(WorldTimeResource.getResourceType())
                ?.gameTime ?: return
        val now = gameTime.epochSecond * 1_000_000_000L + gameTime.nano
        val elapsed =
            if (schedule.lastGameTimeNanos == Long.MIN_VALUE) {
                0L
            } else {
                (now - schedule.lastGameTimeNanos).coerceAtLeast(0L)
            }
        schedule.lastGameTimeNanos = now
        val interval = settings.averageIntervalInGameDays.coerceAtLeast(0.001) * WorldTimeResource.NANOS_PER_DAY
        val chance = (elapsed / interval).coerceIn(0.0, 1.0)

        val activeCrews = mutableSetOf<UUID>()
        store.forEachChunk(
            Query.and(MachineOwner.type, ScrapRushRaid.type),
            BiConsumer<ArchetypeChunk<ChunkStore>, CommandBuffer<ChunkStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val owner = chunk.getComponent(index, MachineOwner.type)?.placerUuid ?: continue
                    activeCrews.add(crews.crewOf(owner).id)
                }
            },
        )
        val candidates = linkedMapOf<UUID, MutableList<RaidCandidate>>()
        store.forEachChunk(
            Query.and(
                MachineOwner.type,
                Jank.type,
                BodgeSlots.type,
                EnergyMember.type,
                BlockModule.BlockStateInfo.getComponentType(),
            ),
            BiConsumer<ArchetypeChunk<ChunkStore>, CommandBuffer<ChunkStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    val member = chunk.getComponent(index, EnergyMember.type) ?: continue
                    if (member.typeId !in RAIDABLE_MACHINE_TYPES) continue
                    val owner = chunk.getComponent(index, MachineOwner.type)?.placerUuid ?: continue
                    val crew = crews.crewOf(owner)
                    if (crew.id in activeCrews) continue
                    val blockState =
                        chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: continue
                    if (!CraftoryCore.isAreaActive(store, blockState)) continue
                    val position = Vector3i()
                    if (!blockState.fillWorldPos(commandBuffer, position)) {
                        continue
                    }
                    val members = crew.members.ifEmpty { setOf(owner) }
                    if (members.none { isOnlineNear(it, world, position, settings.crewRadius) }) continue
                    candidates.getOrPut(crew.id, ::mutableListOf).add(
                        RaidCandidate(chunk.getReferenceTo(index), blockState, position),
                    )
                }
            },
        )
        candidates.values.forEach { eligible ->
            if (eligible.isEmpty() || ThreadLocalRandom.current().nextDouble() >= chance) return@forEach
            val target = eligible[ThreadLocalRandom.current().nextInt(eligible.size)]
            store.forEachChunk(
                target.ref,
                Query.and(
                    MachineOwner.type,
                    Jank.type,
                    BodgeSlots.type,
                    EnergyMember.type,
                    BlockModule.BlockStateInfo.getComponentType(),
                ),
                BiConsumer<ArchetypeChunk<ChunkStore>, CommandBuffer<ChunkStore>> { _, commandBuffer ->
                    startRaid(target.ref, target.blockState, target.position, world, commandBuffer)
                },
            )
        }
    }

    private data class RaidCandidate(
        val ref: com.hypixel.hytale.component.Ref<ChunkStore>,
        val blockState: BlockModule.BlockStateInfo,
        val position: Vector3i,
    )

    private fun hasOnlineCrewMemberNear(
        owner: UUID,
        position: Vector3i,
        world: World,
        radius: Int,
    ): Boolean {
        val crew = crews.crewOf(owner)
        val members = crew.members.ifEmpty { setOf(owner) }
        return members.any { isOnlineNear(it, world, position, radius) }
    }

    private fun isOnlineNear(
        member: UUID,
        world: World,
        position: Vector3i,
        radius: Int,
    ): Boolean =
        Universe.get().getPlayers().any { player ->
            player.uuid == member &&
                player.worldUuid == world.worldConfig.uuid &&
                player.transform.position.distanceSquared(
                    position.x + 0.5,
                    position.y + 0.5,
                    position.z + 0.5,
                ) <= radius.toDouble() * radius
        }

    private fun notifyCrew(
        owner: UUID?,
        message: String,
    ) {
        notifyCrew(owner, Message.raw(message))
    }

    private fun notifyCrew(
        owner: UUID?,
        message: Message,
    ) {
        owner ?: return
        val crew = crews.crewOf(owner)
        notifyCrew(crew.members.ifEmpty { setOf(owner) }, message)
    }

    private fun notifyCrew(
        members: Set<UUID>,
        message: String,
    ) {
        notifyCrew(members, Message.raw(message))
    }

    private fun notifyCrew(
        members: Set<UUID>,
        message: Message,
    ) {
        Universe
            .get()
            .getPlayers()
            .filter { it.uuid in members }
            .forEach { it.sendMessage(message) }
    }

    private fun despawn(
        ids: List<String>,
        world: World,
    ) {
        val npcType = NPCEntity.getComponentType() ?: return
        val uuids = ids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
        if (uuids.isEmpty()) return
        world.entityStore.store.forEachChunk(
            Query.and(UUIDComponent.getComponentType(), npcType),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    val uuid = chunk.getComponent(index, UUIDComponent.getComponentType())?.uuid ?: continue
                    if (uuid in uuids) commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
                }
            },
        )
    }

    private class RaidPath(
        private val target: Vector3d,
    ) : IPath<IPathWaypoint> {
        override fun getId(): UUID = UUID.randomUUID()

        override fun getName(): String = "CraftoryScrapRush"

        override fun getPathWaypoints(): List<IPathWaypoint> = listOf(RaidWaypoint(target))

        override fun length(): Int = 1

        override fun get(index: Int): IPathWaypoint = getPathWaypoints()[index]
    }

    private data class RaidWaypoint(
        private val target: Vector3d,
    ) : IPathWaypoint {
        override fun getOrder(): Int = 0

        override fun getWaypointPosition(
            accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
        ): Vector3d = target

        override fun getWaypointRotation(
            accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
        ): Rotation3f = Rotation3f(0f, 0f, 0f)

        override fun getPauseTime(): Double = 0.0

        override fun getObservationAngle(): Float = 0f
    }

    companion object {
        private const val EVALUATION_TICKS = 100L
        private const val TICKS_PER_SECOND = 20
        private const val STEAL_DISTANCE_SQUARED = 4.0
        private const val SPAWN_ATTEMPTS = 12
        private const val GOBLIN_ROLE = "Coffer_Goblin_Scrapper"
        private val RAIDABLE_MACHINE_TYPES =
            setOf(
                CraftoryIds.CRANK_TYPE,
                CraftoryIds.BURNER_TYPE,
                CraftoryIds.GNASHER_TYPE,
                CraftoryIds.OVEN_TYPE,
            )

        fun forceComplete(
            blockRef: com.hypixel.hytale.component.Ref<ChunkStore>,
            world: World,
            commandBuffer: CommandBuffer<ChunkStore>,
            crews: CrewService = CraftoryCore.crewService(),
        ): Boolean {
            val raid = commandBuffer.getComponent(blockRef, ScrapRushRaid.type) ?: return false
            ScrapRushSystem(ScrapRushSettings(), crews).despawn(raid.npcIds.toList(), world)
            commandBuffer.getComponent(blockRef, Jank.type)?.stolen = false
            commandBuffer.removeComponent(blockRef, ScrapRushRaid.type)
            commandBuffer
                .getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType())
                ?.markNeedsSaving(commandBuffer)
            val owner = commandBuffer.getComponent(blockRef, MachineOwner.type)?.placerUuid
            owner?.let { crews.grantKnowledge(it, NotebookPages.SCRAP_RUSH_PAGE_ID) }
            owner?.let { ownerUuid ->
                val members = crews.crewOf(ownerUuid).members.ifEmpty { setOf(ownerUuid) }
                Universe.get().getPlayers().filter { it.uuid in members }.forEach { player ->
                    player.sendMessage(
                        com.hypixel.hytale.server.core.Message
                            .raw("Scrap Rush defeated."),
                    )
                }
            }
            return true
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
        fun startNearest(
            player: PlayerRef,
            settings: ScrapRushSettings,
            onComplete: (Boolean, String) -> Unit = { _, _ -> },
        ): CompletableFuture<Boolean> {
            val future = CompletableFuture<Boolean>()

            fun complete(
                started: Boolean,
                diagnostic: String,
            ) {
                future.complete(started)
                onComplete(started, diagnostic)
            }
            if (!settings.enabled) {
                complete(false, "Scrap Rush is disabled")
                return future
            }
            val world =
                Universe.get().getWorld(
                    player.worldUuid ?: run {
                        complete(false, "player has no world")
                        return future
                    },
                ) ?: run {
                    complete(false, "player world is unavailable")
                    return future
                }
            val ownerCrew = CraftoryCore.crewService().crewOf(player.uuid)
            val playerPosition = player.transform.position
            val activeCrews = mutableSetOf<UUID>()
            world.chunkStore.store.forEachChunk(
                Query.and(MachineOwner.type, ScrapRushRaid.type),
                BiConsumer<ArchetypeChunk<ChunkStore>, CommandBuffer<ChunkStore>> { chunk, _ ->
                    for (index in 0 until chunk.size()) {
                        val owner = chunk.getComponent(index, MachineOwner.type)?.placerUuid ?: continue
                        activeCrews.add(CraftoryCore.crewService().crewOf(owner).id)
                    }
                },
            )
            var closest: Pair<com.hypixel.hytale.component.Ref<ChunkStore>, Vector3i>? = null
            var closestDistance = (settings.crewRadius * settings.crewRadius).toDouble()
            world.chunkStore.store.forEachChunk(
                Query.and(
                    MachineOwner.type,
                    Jank.type,
                    EnergyMember.type,
                    BlockModule.BlockStateInfo.getComponentType(),
                ),
                BiConsumer<ArchetypeChunk<ChunkStore>, CommandBuffer<ChunkStore>> { chunk, _ ->
                    for (index in 0 until chunk.size()) {
                        val member = chunk.getComponent(index, EnergyMember.type) ?: continue
                        if (member.typeId !in RAIDABLE_MACHINE_TYPES) continue
                        val owner = chunk.getComponent(index, MachineOwner.type)?.placerUuid ?: continue
                        if (CraftoryCore.crewService().crewOf(owner).id != ownerCrew.id) continue
                        if (ownerCrew.id in activeCrews) continue
                        val blockState =
                            chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: continue
                        if (
                            world.chunkStore.store.getComponent(chunk.getReferenceTo(index), ScrapRushRaid.type) != null
                        ) {
                            continue
                        }
                        if (!CraftoryCore.isAreaActive(world.chunkStore.store, blockState)) continue
                        val position = Vector3i()
                        if (!blockState.fillWorldPos(world.chunkStore.store, position)) continue
                        val distance =
                            playerPosition.distanceSquared(position.x + 0.5, position.y + 0.5, position.z + 0.5)
                        if (distance < closestDistance) {
                            closestDistance = distance
                            closest = chunk.getReferenceTo(index) to position
                        }
                    }
                },
            )
            val (ref, position) =
                closest ?: run {
                    complete(false, "no eligible owned machine within ${settings.crewRadius} blocks")
                    return future
                }
            val blockState =
                world.chunkStore.store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                    ?: run {
                        complete(false, "machine block state is unavailable")
                        return future
                    }
            val system = ScrapRushSystem(settings)
            world.execute {
                var started = false
                world.chunkStore.store.forEachChunk(
                    ref,
                    Query.and(
                        MachineOwner.type,
                        Jank.type,
                        BodgeSlots.type,
                        EnergyMember.type,
                        BlockModule.BlockStateInfo.getComponentType(),
                    ),
                    BiConsumer<ArchetypeChunk<ChunkStore>, CommandBuffer<ChunkStore>> { _, commandBuffer ->
                        started = system.startRaid(ref, blockState, position, world, commandBuffer)
                    },
                )
                complete(started, system.lastStartDiagnostic)
            }
            return future
        }
    }
}
