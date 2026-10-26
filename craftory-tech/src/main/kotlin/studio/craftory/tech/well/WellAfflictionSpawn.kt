package studio.craftory.tech.well

import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.npc.NPCPlugin
import com.hypixel.hytale.server.npc.entities.NPCEntity
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.CraftoryIds

internal fun spawnFollowers(
    world: World,
    wellPosition: BlockPos,
    kind: String,
    count: Int,
    offset: Int = 0,
) {
    val liveWell = CraftoryCore.wells().wellsNear(world.chunkStore.store, wellPosition, 0).firstOrNull() ?: return
    val followers = spawnOnVerifiedSurfaces(world, wellPosition, kind, count, offset)
    if (followers.isEmpty()) return
    recordSpawnedFollowers(world, wellPosition, kind, followers)
    val message =
        when (kind) {
            WellAfflictionFollower.MOTH -> Message.translation("server.craftory.affliction.appeared")
            WellAfflictionFollower.POACHER -> Message.translation("server.craftory.affliction.poachers")
            else -> null
        }
    if (message != null && liveWell.well.claimCrewId != null) notifyCrew(world, liveWell.well.claimCrewId, message)
}

internal fun recordSpawnedFollowers(
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

internal fun spawnFollowersForSelfTest(
    world: World,
    wellPosition: BlockPos,
    kind: String,
    count: Int,
): List<String> {
    val role =
        when (kind) {
            WellAfflictionFollower.MOTH -> CraftoryIds.RIFTMOTH
            WellAfflictionFollower.VOID -> CraftoryIds.LARVA_VOID
            WellAfflictionFollower.POACHER -> CraftoryIds.GOBLIN_POACHER
            WellAfflictionFollower.WALKER -> CraftoryIds.SLURP_WALKER
            else -> return emptyList()
        }
    return buildList {
        repeat(count) { index ->
            val created =
                NPCPlugin.get().spawnNPC(
                    world.entityStore.store,
                    role,
                    null,
                    Vector3d(wellPosition.x + 2.0 + index * 0.25, wellPosition.y + 1.0, wellPosition.z + 0.5),
                    Rotation3f(0f, 0f, 0f),
                ) ?: return@repeat
            val ref = created.left()
            val npc = created.right() as? NPCEntity ?: return@repeat
            val uuid =
                world.entityStore.store
                    .getComponent(ref, UUIDComponent.getComponentType())
                    ?.uuid ?: return@repeat
            npc.leashPoint = Vector3d(wellPosition.x + 0.5, wellPosition.y + 0.5, wellPosition.z + 0.5)
            npc.pathManager.setTransientPath(
                WellAfflictionPath(Vector3d(wellPosition.x + 0.5, wellPosition.y + 0.5, wellPosition.z + 0.5)),
            )
            world.entityStore.store.putComponent(
                ref,
                WellAfflictionFollower.type,
                WellAfflictionFollower(wellPosition.pack(), kind),
            )
            add(uuid.toString())
        }
    }
}

internal fun spawnOnVerifiedSurfaces(
    world: World,
    wellPosition: BlockPos,
    kind: String,
    count: Int,
    offset: Int = 0,
): List<String> {
    val role =
        when (kind) {
            WellAfflictionFollower.MOTH -> CraftoryIds.RIFTMOTH
            WellAfflictionFollower.VOID -> CraftoryIds.LARVA_VOID
            WellAfflictionFollower.POACHER -> CraftoryIds.GOBLIN_POACHER
            WellAfflictionFollower.WALKER -> CraftoryIds.SLURP_WALKER
            else -> return emptyList()
        }
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
                if (distanceSquared(candidate, well) <= MAX_SPAWN_DISTANCE * MAX_SPAWN_DISTANCE && candidate !in this) {
                    add(candidate)
                }
            }
        }
    }
