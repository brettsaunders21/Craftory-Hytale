package studio.craftory.tech.well

import com.hypixel.hytale.builtin.beam.AttachedBeam
import com.hypixel.hytale.builtin.beam.BeamComponent
import com.hypixel.hytale.builtin.beam.asset.Beam
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import java.util.UUID
import java.util.function.BiConsumer

internal fun updateBeamsAndFeed(
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

internal fun cleanseIfReady(
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
        transform.position.distanceSquared(position.x + 0.5, position.y + 0.5, position.z + 0.5)
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
            if (follower.kind != WellAfflictionFollower.MOTH || follower.wellPosition != position.pack()) return@execute
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
        feedBeams.remove(id)?.takeIf { it.isValid }?.let {
            world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
        }
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
