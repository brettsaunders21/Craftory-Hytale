package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import java.util.UUID

internal fun hasOnlineCrewMember(
    crewId: UUID,
    world: World,
): Boolean =
    Universe.get().getPlayers().any { player ->
        player.worldUuid == world.worldConfig.uuid &&
            CraftoryCore.crewService().crewOf(player.uuid).id == crewId
    }

internal fun notifyCrew(
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

internal fun despawnFollowers(
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
        siphonBeams.remove(uuid)?.takeIf { it.isValid }?.let {
            world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
        }
    }
}

internal fun wellIsCorrupted(
    world: World,
    position: BlockPos,
): Boolean =
    CraftoryCore
        .wells()
        .wellsNear(world.chunkStore.store, position, 0)
        .firstOrNull()
        ?.well
        ?.corrupted == true

internal fun wellRef(
    world: World,
    position: BlockPos,
): Ref<ChunkStore>? =
    CraftoryCore
        .wells()
        .wellsNear(world.chunkStore.store, position, 0)
        .firstOrNull()
        ?.ref

internal fun distanceSquared(
    left: BlockPos,
    right: BlockPos,
): Int {
    val dx = left.x - right.x
    val dy = left.y - right.y
    val dz = left.z - right.z
    return dx * dx + dy * dy + dz * dz
}
