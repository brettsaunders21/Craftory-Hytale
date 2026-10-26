package studio.craftory.tech.well

import com.hypixel.hytale.builtin.beam.AttachedBeam
import com.hypixel.hytale.builtin.beam.BeamComponent
import com.hypixel.hytale.builtin.beam.asset.Beam
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.path.IPath
import com.hypixel.hytale.server.core.universe.world.path.IPathWaypoint
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.NPCPlugin
import com.hypixel.hytale.server.npc.entities.NPCEntity
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.CradleState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.BiConsumer

internal class WellAfflictionSystem : EntityTickingSystem<ChunkStore>() {
    private val cursor = Vector3i()

    internal fun startForSelfTest(
        world: World,
        position: BlockPos,
        kind: String,
        count: Int = 1,
    ) {
        spawnFollowers(world, position, kind, count)
    }

    internal fun startRaidForSelfTest(
        world: World,
        position: BlockPos,
    ) {
        spawnRaid(world, position)
    }

    internal fun advanceFeedForSelfTest(
        world: World,
        position: BlockPos,
        well: Glimmerwell,
        seconds: Int,
    ) {
        feedSeconds[WellKey(world.worldConfig.uuid, position.pack())] = seconds
        updateBeamsAndFeed(world, position, well)
    }

    override fun getQuery(): Query<ChunkStore> =
        Query.and(Glimmerwell.type, BlockModule.BlockStateInfo.getComponentType())

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val world = store.externalData.world
        if (world.tick % TICKS_PER_SECOND != 0L) return
        val well = chunk.getComponent(index, Glimmerwell.type) ?: return
        val ref = chunk.getReferenceTo(index)
        var createdAffliction = false
        val affliction =
            chunk.getComponent(index, WellAffliction.type)
                ?: WellAffliction().also {
                    store.putComponent(ref, WellAffliction.type, it)
                    createdAffliction = true
                }
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!blockState.fillWorldPos(commandBuffer, cursor)) return
        val position = BlockPos(cursor.x, cursor.y, cursor.z)
        if (WellSelfTestExclusions.isWell(position) || !CraftoryCore.isAreaActive(store, blockState)) return
        if (createdAffliction) blockState.markNeedsSaving(commandBuffer)
        if (well.state != WellState.SEATED && well.state != WellState.TAPPED) return
        cleanseIfReady(world, position, well, affliction, commandBuffer, blockState)
        processRaid(world, position, well, affliction)
        val crewId = well.claimCrewId ?: return
        if (!hasOnlineCrewMember(crewId, world)) return
        updateBeamsAndFeed(world, position, well)
        spawnOnSchedule(world, position)
    }

    companion object {
        internal fun onFollowerDeath(
            world: World,
            uuid: UUID,
            position: BlockPos,
            kind: String,
        ) {
            feedBeams.remove(uuid)?.takeIf { it.isValid }?.let {
                world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
            }
            siphonBeams.remove(uuid)?.takeIf { it.isValid }?.let {
                world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
            }
            if (kind == WellAfflictionFollower.WALKER) {
                siphonSeconds.remove(WellKey(world.worldConfig.uuid, position.pack()))
            }
        }

        internal fun dropRaidRewards(
            world: World,
            position: BlockPos,
        ) {
            listOf(CraftoryIds.GOB_JAR to 1, "Ingredient_Scraps" to 4).forEach { (itemId, quantity) ->
                val holder =
                    ItemComponent.generateItemDrop(
                        world.entityStore.store,
                        ItemStack(itemId, quantity),
                        Vector3d(position.x + 0.5, position.y + 1.0, position.z + 0.5),
                        Rotation3f.IDENTITY,
                        0f,
                        0f,
                        0f,
                    ) ?: return@forEach
                world.entityStore.store.addEntity(holder, AddReason.SPAWN)
            }
        }
    }
}

private const val TICKS_PER_SECOND = 20L
private const val CLEANSING_RADIUS = 16
private const val FEED_DISTANCE_SQUARED = 36.0
private const val MAX_SPAWN_DISTANCE = 12
private const val BEAM_REFRESH_TICKS = 40L
private const val FEED_BEAM_ID = "Craftory_Riftmoth_Feed"
private const val SIPHON_BEAM_ID = "Craftory_Slurp_Siphon"
private const val SIPHON_DISTANCE_SQUARED = 16.0
private const val WALKER_DEATH_TIMEOUT_SECONDS = 180L
private val SURFACE_OFFSETS = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1, 1 to 1, -1 to 1, -1 to -1, 1 to -1)
private val lastMothAttempt = ConcurrentHashMap<WellKey, Long>()
private val lastVoidAttempt = ConcurrentHashMap<WellKey, Long>()
private val lastRaidAttempt = ConcurrentHashMap<WellKey, Long>()
private val feedSeconds = ConcurrentHashMap<WellKey, Int>()
private val siphonSeconds = ConcurrentHashMap<WellKey, Int>()
private val feedBeams = ConcurrentHashMap<UUID, Ref<EntityStore>>()
private val siphonBeams = ConcurrentHashMap<UUID, Ref<EntityStore>>()

private data class WellKey(
    val worldId: UUID,
    val wellPosition: Long,
)

private data class FeedingMoth(
    val uuid: UUID,
    val position: Vector3d,
)

private fun WellAfflictionSystem.updateBeamsAndFeed(
    world: World,
    position: BlockPos,
    well: Glimmerwell,
) {
    val feeding = feedingMoths(world, position)
    if (feeding.isNotEmpty()) {
        refreshFeedBeams(world, position, feeding)
        if (well.corrupted) return
        val key = WellKey(world.worldConfig.uuid, position.pack())
        val seconds = feedSeconds.merge(key, 1, Int::plus) ?: 1
        if (seconds >= WellAfflictionRuntimeConfig.settings.riftmothFeedSecondsToCorrupt) {
            if (CraftoryCore.wells().setCorrupted(world, position, true)) {
                notifyCrew(world, well.claimCrewId, Message.translation("server.craftory.affliction.corrupted"))
            }
            feedSeconds.remove(key)
        }
    } else {
        clearFeedBeams(world, position)
    }
}

private fun spawnOnSchedule(
    world: World,
    position: BlockPos,
) {
    val settings = WellAfflictionRuntimeConfig.settings
    val key = WellKey(world.worldConfig.uuid, position.pack())
    if (settings.riftmothIntervalSeconds > 0) {
        val last = lastMothAttempt.putIfAbsent(key, world.tick) ?: world.tick
        if (world.tick - last >= settings.riftmothIntervalSeconds * TICKS_PER_SECOND) {
            lastMothAttempt[key] = world.tick
            if (ThreadLocalRandom.current().nextInt(100) < settings.riftmothChancePercent) {
                val count = ThreadLocalRandom.current().nextInt(1, 3)
                world.execute { spawnFollowers(world, position, WellAfflictionFollower.MOTH, count) }
            }
        }
    }
    schedulePoacherRaid(
        world,
        position,
        key,
        settings.poacherRaidIntervalSeconds,
        settings.poacherRaidChancePercent,
    )
    if (!wellIsCorrupted(world, position) || settings.voidSpawnCap == 0) return
    val afflictionRef = wellRef(world, position) ?: return
    val state = world.chunkStore.store.getComponent(afflictionRef, WellAffliction.type) ?: return
    if (state.voidIds.size >= settings.voidSpawnCap) return
    val last = lastVoidAttempt.putIfAbsent(key, world.tick) ?: world.tick
    if (world.tick - last < settings.voidSpawnIntervalSeconds * TICKS_PER_SECOND) return
    lastVoidAttempt[key] = world.tick
    world.execute { spawnFollowers(world, position, WellAfflictionFollower.VOID, 1) }
}

private fun schedulePoacherRaid(
    world: World,
    position: BlockPos,
    key: WellKey,
    intervalSeconds: Int,
    chancePercent: Int,
) {
    if (intervalSeconds <= 0) return
    val currentRef = wellRef(world, position) ?: return
    val current = world.chunkStore.store.getComponent(currentRef, WellAffliction.type) ?: return
    if (current.raidNpcIds.isNotEmpty()) return
    val lastRaid = lastRaidAttempt.putIfAbsent(key, world.tick) ?: world.tick
    if (world.tick - lastRaid < intervalSeconds * TICKS_PER_SECOND) return
    lastRaidAttempt[key] = world.tick
    if (ThreadLocalRandom.current().nextInt(100) < chancePercent) {
        world.execute { spawnRaid(world, position) }
    }
}

private fun spawnRaid(
    world: World,
    position: BlockPos,
) {
    val poachers = spawnOnVerifiedSurfaces(world, position, WellAfflictionFollower.POACHER, 2)
    val walker = spawnOnVerifiedSurfaces(world, position, WellAfflictionFollower.WALKER, 1, 2)
    if (poachers.size != 2 || walker.size != 1) {
        despawnFollowers(world, (poachers + walker).toTypedArray())
        return
    }
    val blockRef = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
    val affliction = world.chunkStore.store.getComponent(blockRef, WellAffliction.type) ?: return
    affliction.raidNpcIds = (poachers + walker).toTypedArray()
    affliction.walkerId = walker.single()
    affliction.raidStartedTick = world.tick
    world.chunkStore.store
        .getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType())
        ?.markNeedsSaving(world.chunkStore.store)
    notifyCrew(
        world,
        CraftoryCore
            .wells()
            .wellsNear(world.chunkStore.store, position, 0)
            .firstOrNull()
            ?.well
            ?.claimCrewId,
        Message.translation("server.craftory.affliction.poachers"),
    )
}

private fun processRaid(
    world: World,
    position: BlockPos,
    well: Glimmerwell,
    affliction: WellAffliction,
) {
    if (affliction.raidNpcIds.isEmpty()) return
    if (affliction.walkerId.isEmpty()) {
        if (world.tick - affliction.raidStartedTick >= WALKER_DEATH_TIMEOUT_SECONDS * TICKS_PER_SECOND) {
            despawnFollowers(world, affliction.raidNpcIds)
            clearRaid(world, position, affliction)
        }
        return
    }
    val uuid = runCatching { UUID.fromString(affliction.walkerId) }.getOrNull() ?: return
    val walkerRef = world.entityStore.getRefFromUUID(uuid) ?: return
    if (!walkerRef.isValid ||
        world.entityStore.store.getComponent(walkerRef, DeathComponent.getComponentType()) != null
    ) {
        return
    }
    val transform = world.entityStore.store.getComponent(walkerRef, TransformComponent.getComponentType()) ?: return
    val distance = transform.position.distanceSquared(position.x + 0.5, position.y + 0.5, position.z + 0.5)
    if (distance > SIPHON_DISTANCE_SQUARED) {
        clearSiphonBeam(world, uuid)
        return
    }
    refreshSiphonBeam(world, position, uuid)
    val key = WellKey(world.worldConfig.uuid, position.pack())
    val elapsed = siphonSeconds.merge(key, 1, Int::plus) ?: 1
    if (elapsed >= WellAfflictionRuntimeConfig.settings.siphonSeconds) {
        val nextStrength = WellAfflictionRules.strengthAfterSiphon(well.strength)
        if (CraftoryCore.wells().setStrength(world, position, nextStrength)) {
            synchronizeCradleStrength(world, position, nextStrength)
            notifyCrew(world, well.claimCrewId, Message.translation("server.craftory.affliction.siphoned"))
        }
        despawnFollowers(world, affliction.raidNpcIds)
        clearRaid(world, position, affliction)
    }
}

private fun synchronizeCradleStrength(
    world: World,
    wellPosition: BlockPos,
    strength: WellStrength,
) {
    val cradlePosition = BlockPos(wellPosition.x, wellPosition.y - 1, wellPosition.z)
    val sectionRef =
        world.chunkStore.getChunkSectionReferenceAtBlock(
            cradlePosition.x,
            cradlePosition.y,
            cradlePosition.z,
        ) ?: return
    val cradleRef =
        BlockModule.getBlockEntity(
            world.chunkStore.store,
            sectionRef,
            cradlePosition.x,
            cradlePosition.y,
            cradlePosition.z,
        ) ?: return
    val store = world.chunkStore.store
    val state = store.getComponent(cradleRef, CradleState.type) ?: return
    if (state.seatedStrength == strength.name) return
    state.seatedStrength = strength.name
    store.getComponent(cradleRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
}

private fun clearRaid(
    world: World,
    position: BlockPos,
    affliction: WellAffliction,
) {
    runCatching { UUID.fromString(affliction.walkerId) }.getOrNull()?.let { clearSiphonBeam(world, it) }
    affliction.raidNpcIds = emptyArray()
    affliction.walkerId = ""
    affliction.raidStartedTick = 0
    world.chunkStore.store
        .getComponent(wellRef(world, position) ?: return, BlockModule.BlockStateInfo.getComponentType())
        ?.markNeedsSaving(world.chunkStore.store)
    val key = WellKey(world.worldConfig.uuid, position.pack())
    siphonSeconds.remove(key)
}

private fun refreshSiphonBeam(
    world: World,
    position: BlockPos,
    walkerId: UUID,
) {
    if (world.tick % BEAM_REFRESH_TICKS != 0L) return
    val beamIndex = Beam.getAssetMap().getIndex(SIPHON_BEAM_ID)
    if (beamIndex < 0) return
    world.execute {
        val ref = world.entityStore.getRefFromUUID(walkerId) ?: return@execute
        val follower = world.entityStore.store.getComponent(ref, WellAfflictionFollower.type) ?: return@execute
        if (follower.kind != WellAfflictionFollower.WALKER ||
            follower.wellPosition != position.pack()
        ) {
            return@execute
        }
        val transform =
            world.entityStore.store.getComponent(ref, TransformComponent.getComponentType()) ?: return@execute
        clearSiphonBeam(world, walkerId)
        BeamComponent
            .spawn(
                world.entityStore.store,
                Vector3d(transform.position),
                0.9f,
                AttachedBeam.toBlock(beamIndex, 1f, null, position.x, position.y, position.z),
            ).also { siphonBeams[walkerId] = it }
    }
}

private fun clearSiphonBeam(
    world: World,
    walkerId: UUID,
) {
    siphonBeams.remove(walkerId)?.takeIf { it.isValid }?.let {
        world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
    }
}

private fun cleanseIfReady(
    world: World,
    position: BlockPos,
    well: Glimmerwell,
    affliction: WellAffliction,
    commandBuffer: CommandBuffer<ChunkStore>,
    blockState: BlockModule.BlockStateInfo,
) {
    if (!well.corrupted) return
    val nearbyAligned =
        CraftoryCore
            .wells()
            .wellsNear(world.chunkStore.store, position, CLEANSING_RADIUS)
            .any { nearby ->
                nearby.pos != position &&
                    nearby.well.state in setOf(WellState.SEATED, WellState.TAPPED) &&
                    nearby.well.temperament in setOf(Temperament.TIDE, Temperament.GROWTH)
            }
    if (!WellAfflictionRules.shouldCleanse(well.corrupted, nearbyAligned, affliction.mothIds.isEmpty())) return
    CraftoryCore.wells().setCorrupted(world, position, false)
    despawnFollowers(world, affliction.voidIds)
    clearFeedBeams(world, position)
    affliction.mothIds = emptyArray()
    affliction.voidIds = emptyArray()
    blockState.markNeedsSaving(commandBuffer)
    notifyCrew(world, well.claimCrewId, Message.translation("server.craftory.affliction.cleansed"))
    feedSeconds.remove(WellKey(world.worldConfig.uuid, position.pack()))
}

private fun spawnFollowers(
    world: World,
    wellPosition: BlockPos,
    kind: String,
    count: Int,
    offset: Int = 0,
) {
    val liveWell =
        CraftoryCore.wells().wellsNear(world.chunkStore.store, wellPosition, 0).firstOrNull()
            ?: return
    val followers = spawnOnVerifiedSurfaces(world, wellPosition, kind, count, offset)
    if (followers.isEmpty()) return
    recordSpawnedFollowers(world, wellPosition, kind, followers)
    val message =
        when (kind) {
            WellAfflictionFollower.MOTH -> Message.translation("server.craftory.affliction.appeared")
            WellAfflictionFollower.POACHER -> Message.translation("server.craftory.affliction.poachers")
            else -> null
        }
    if (message != null && liveWell.well.claimCrewId != null) {
        notifyCrew(world, liveWell.well.claimCrewId, message)
    }
}

private fun recordSpawnedFollowers(
    world: World,
    position: BlockPos,
    kind: String,
    followers: List<String>,
) {
    val blockRef = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
    val store = world.chunkStore.store
    val affliction = store.getComponent(blockRef, WellAffliction.type) ?: return
    when (kind) {
        WellAfflictionFollower.MOTH -> {
            affliction.mothIds = (affliction.mothIds + followers).distinct().toTypedArray()
        }

        WellAfflictionFollower.VOID -> {
            affliction.voidIds = (affliction.voidIds + followers).distinct().toTypedArray()
        }

        WellAfflictionFollower.POACHER, WellAfflictionFollower.WALKER -> {
            affliction.raidNpcIds = (affliction.raidNpcIds + followers).distinct().toTypedArray()
            if (kind == WellAfflictionFollower.WALKER) affliction.walkerId = followers.first()
        }
    }
    store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
}

private fun spawnOnVerifiedSurfaces(
    world: World,
    wellPosition: BlockPos,
    kind: String,
    count: Int,
    offset: Int = 0,
): List<String> {
    val roles =
        when (kind) {
            WellAfflictionFollower.MOTH -> listOf(CraftoryIds.RIFTMOTH)
            WellAfflictionFollower.VOID -> listOf(CraftoryIds.LARVA_VOID)
            WellAfflictionFollower.POACHER -> listOf(CraftoryIds.GOBLIN_POACHER)
            WellAfflictionFollower.WALKER -> listOf(CraftoryIds.SLURP_WALKER)
            else -> emptyList()
        }
    val role = roles.firstOrNull() ?: return emptyList()
    val positions = surfaceCandidates(world, wellPosition).drop(offset).take(count)
    val spawned = ArrayList<String>(positions.size)
    positions.forEach { floorPos ->
        val created =
            NPCPlugin.get().spawnNPC(
                world.entityStore.store,
                role,
                null,
                Vector3d(floorPos.x + 0.5, floorPos.y.toDouble(), floorPos.z + 0.5),
                Rotation3f(0f, 0f, 0f),
            ) ?: return@forEach
        val ref = created.left()
        val npc =
            created.right() as? NPCEntity
                ?: run {
                    world.entityStore.store.removeEntity(ref, RemoveReason.REMOVE)
                    return@forEach
                }
        val uuid =
            world.entityStore.store
                .getComponent(ref, UUIDComponent.getComponentType())
                ?.uuid
                ?: run {
                    world.entityStore.store.removeEntity(ref, RemoveReason.REMOVE)
                    return@forEach
                }
        npc.leashPoint = Vector3d(wellPosition.x + 0.5, wellPosition.y + 0.5, wellPosition.z + 0.5)
        npc.pathManager.setTransientPath(
            WellAfflictionPath(Vector3d(wellPosition.x + 0.5, wellPosition.y + 0.5, wellPosition.z + 0.5)),
        )
        world.entityStore.store.putComponent(
            ref,
            WellAfflictionFollower.type,
            WellAfflictionFollower(wellPosition.pack(), kind),
        )
        spawned.add(uuid.toString())
    }
    return spawned
}

private fun surfaceCandidates(
    world: World,
    well: BlockPos,
): List<BlockPos> =
    buildList {
        for (radius in 4..10 step 2) {
            for (offset in SURFACE_OFFSETS) {
                val x = well.x + offset.first * radius
                val z = well.z + offset.second * radius
                val candidate = WellBirths.highestSurfaceWithin(world, BlockPos(x, well.y, z), 2) ?: continue
                if (distanceSquared(candidate, well) <= MAX_SPAWN_DISTANCE * MAX_SPAWN_DISTANCE &&
                    candidate !in this
                ) {
                    add(candidate)
                }
            }
        }
    }

private fun feedingMoths(
    world: World,
    position: BlockPos,
): List<FeedingMoth> {
    val feeding = mutableListOf<FeedingMoth>()
    val query =
        Query.and(
            WellAfflictionFollower.type,
            TransformComponent.getComponentType(),
            UUIDComponent.getComponentType(),
        )
    world.entityStore.store.forEachChunk(
        query,
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
            for (index in 0 until chunk.size()) {
                feedingMothAt(chunk, index, position)?.let(feeding::add)
            }
        },
    )
    return feeding
}

private fun feedingMothAt(
    chunk: ArchetypeChunk<EntityStore>,
    index: Int,
    position: BlockPos,
): FeedingMoth? {
    val follower = chunk.getComponent(index, WellAfflictionFollower.type) ?: return null
    if (chunk.getComponent(index, DeathComponent.getComponentType()) != null) return null
    if (follower.kind != WellAfflictionFollower.MOTH || follower.wellPosition != position.pack()) return null
    val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: return null
    val distance =
        transform.position.distanceSquared(
            position.x + 0.5,
            position.y + 0.5,
            position.z + 0.5,
        )
    if (distance > FEED_DISTANCE_SQUARED) return null
    val uuid = chunk.getComponent(index, UUIDComponent.getComponentType())?.uuid ?: return null
    return FeedingMoth(uuid, Vector3d(transform.position))
}

private fun refreshFeedBeams(
    world: World,
    position: BlockPos,
    feeding: List<FeedingMoth>,
) {
    val beamIndex = Beam.getAssetMap().getIndex(FEED_BEAM_ID)
    if (beamIndex < 0 || world.tick % BEAM_REFRESH_TICKS != 0L) return
    val feedingIds = feeding.mapTo(HashSet()) { it.uuid }
    feedBeams.keys
        .filter { id -> id !in feedingIds && followerAtWell(world, id, position) }
        .forEach { id ->
            feedBeams.remove(id)?.takeIf { it.isValid }?.let {
                world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
            }
        }
    feeding.forEach { moth ->
        world.execute {
            val ref = world.entityStore.getRefFromUUID(moth.uuid) ?: return@execute
            val follower = world.entityStore.store.getComponent(ref, WellAfflictionFollower.type) ?: return@execute
            if (follower.kind != WellAfflictionFollower.MOTH ||
                follower.wellPosition != position.pack()
            ) {
                return@execute
            }
            val transform =
                world.entityStore.store.getComponent(ref, TransformComponent.getComponentType()) ?: return@execute
            feedBeams.remove(moth.uuid)?.takeIf { it.isValid }?.let {
                world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
            }
            feedBeams[moth.uuid] =
                BeamComponent.spawn(
                    world.entityStore.store,
                    Vector3d(transform.position),
                    0.6f,
                    AttachedBeam.toBlock(beamIndex, 1f, null, position.x, position.y, position.z),
                )
        }
    }
}

private fun clearFeedBeams(
    world: World,
    position: BlockPos,
) {
    val ids = feedBeams.keys.filter { id -> followerAtWell(world, id, position) }
    ids.forEach { id ->
        feedBeams
            .remove(
                id,
            )?.takeIf { it.isValid }
            ?.let { world.entityStore.store.removeEntity(it, RemoveReason.REMOVE) }
    }
}

private fun followerAtWell(
    world: World,
    uuid: UUID,
    position: BlockPos,
): Boolean {
    val ref = world.entityStore.getRefFromUUID(uuid) ?: return false
    val follower = world.entityStore.store.getComponent(ref, WellAfflictionFollower.type) ?: return false
    return follower.wellPosition == position.pack()
}

private fun despawnFollowers(
    world: World,
    ids: Array<String>,
) {
    ids.forEach { raw ->
        val uuid = runCatching { UUID.fromString(raw) }.getOrNull() ?: return@forEach
        world.entityStore.getRefFromUUID(uuid)?.takeIf { it.isValid }?.let {
            world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
        }
        feedBeams.remove(uuid)?.takeIf { it.isValid }?.let {
            world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
        }
    }
}

private fun hasOnlineCrewMember(
    crewId: UUID,
    world: World,
): Boolean =
    Universe.get().getPlayers().any { player ->
        player.worldUuid == world.worldConfig.uuid &&
            CraftoryCore.crewService().crewOf(player.uuid).id == crewId
    }

private fun notifyCrew(
    world: World,
    crewId: UUID?,
    message: Message,
) {
    crewId ?: return
    Universe
        .get()
        .getPlayers()
        .filter {
            it.worldUuid == world.worldConfig.uuid &&
                CraftoryCore.crewService().crewOf(it.uuid).id == crewId
        }.forEach { it.sendMessage(message) }
}

private fun wellIsCorrupted(
    world: World,
    position: BlockPos,
): Boolean =
    CraftoryCore
        .wells()
        .wellsNear(world.chunkStore.store, position, 0)
        .firstOrNull()
        ?.well
        ?.corrupted == true

private fun wellRef(
    world: World,
    position: BlockPos,
): Ref<ChunkStore>? =
    CraftoryCore
        .wells()
        .wellsNear(world.chunkStore.store, position, 0)
        .firstOrNull()
        ?.ref

private fun distanceSquared(
    left: BlockPos,
    right: BlockPos,
): Int {
    val dx = left.x - right.x
    val dy = left.y - right.y
    val dz = left.z - right.z
    return dx * dx + dy * dy + dz * dz
}

internal class WellAfflictionDeathSystem : DeathSystems.OnDeathSystem() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(DeathComponent.getComponentType(), NPCEntity.getComponentType()!!, WellAfflictionFollower.type)

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val follower = commandBuffer.getComponent(ref, WellAfflictionFollower.type) ?: return
        val position = BlockPos.unpack(follower.wellPosition)
        val well =
            CraftoryCore.wells().wellsNear(store.externalData.world.chunkStore.store, position, 0).firstOrNull()
                ?: return
        val uuid = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())?.uuid?.toString() ?: return
        runCatching { UUID.fromString(uuid) }.getOrNull()?.let {
            WellAfflictionSystem.onFollowerDeath(store.externalData.world, it, position, follower.kind)
        }
        val affliction =
            well.ref.let {
                store.externalData.world.chunkStore.store
                    .getComponent(it, WellAffliction.type)
            } ?: return
        when (follower.kind) {
            WellAfflictionFollower.MOTH -> {
                affliction.mothIds =
                    affliction.mothIds.filterNot { it == uuid }.toTypedArray()
            }

            WellAfflictionFollower.VOID -> {
                affliction.voidIds =
                    affliction.voidIds.filterNot { it == uuid }.toTypedArray()
            }

            WellAfflictionFollower.POACHER, WellAfflictionFollower.WALKER -> {
                affliction.raidNpcIds = affliction.raidNpcIds.filterNot { it == uuid }.toTypedArray()
                if (follower.kind == WellAfflictionFollower.WALKER) {
                    affliction.walkerId = ""
                    affliction.raidStartedTick = store.externalData.world.tick
                }
                if (affliction.raidNpcIds.isEmpty() && affliction.walkerId.isEmpty()) {
                    WellAfflictionSystem.dropRaidRewards(store.externalData.world, position)
                    affliction.raidStartedTick = 0
                }
            }
        }
        store.externalData.world.chunkStore.store
            .getComponent(well.ref, BlockModule.BlockStateInfo.getComponentType())
            ?.markNeedsSaving(store.externalData.world.chunkStore.store)
    }
}

private class WellAfflictionPath(
    private val target: Vector3d,
) : IPath<IPathWaypoint> {
    override fun getId(): UUID = UUID.randomUUID()

    override fun getName(): String = "CraftoryWellAffliction"

    override fun getPathWaypoints(): List<IPathWaypoint> = listOf(WellAfflictionWaypoint(target))

    override fun length(): Int = 1

    override fun get(index: Int): IPathWaypoint = getPathWaypoints()[index]
}

private data class WellAfflictionWaypoint(
    private val target: Vector3d,
) : IPathWaypoint {
    override fun getOrder(): Int = 0

    override fun getWaypointPosition(accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>): Vector3d =
        target

    override fun getWaypointRotation(
        accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
    ): Rotation3f = Rotation3f(0f, 0f, 0f)

    override fun getPauseTime(): Double = 0.0

    override fun getObservationAngle(): Float = 0f
}
