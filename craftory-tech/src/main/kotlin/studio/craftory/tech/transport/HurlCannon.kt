package studio.craftory.tech.transport

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.transport.HurlPoint
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.MachineOwner
import java.util.function.IntPredicate

internal fun blockId(
    world: World,
    x: Int,
    y: Int,
    z: Int,
): String? {
    val section = world.chunkStore.getChunkSectionReferenceAtBlock(x, y, z)?.takeIf { it.isValid } ?: return null
    val blocks = world.chunkStore.store.getComponent(section, BlockSection.getComponentType()) ?: return null
    return BlockType.getAssetMap().getAsset(blocks.get(x and 31, y and 31, z and 31))?.id
}

internal fun useHurlCannon(
    world: World,
    position: Vector3i,
    rider: Ref<EntityStore>,
): Boolean {
    if (blockId(world, position.x, position.y, position.z) != CraftoryIds.HURL_CANNON) return false
    val source = Vector3i(position)
    world.execute { if (rider.isValid) fireCannon(world, source, rider) }
    return true
}

private fun fireCannon(
    world: World,
    position: Vector3i,
    rider: Ref<EntityStore>,
) {
    if (blockId(world, position.x, position.y, position.z) != CraftoryIds.HURL_CANNON) return
    val store = world.entityStore.store
    val player = store.getComponent(rider, PlayerRef.getComponentType()) ?: return
    val block = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
    val owner =
        world.chunkStore.store
            .getComponent(block, MachineOwner.type)
            ?.placerUuid
    if (!StationPermissions.mayVisit(owner, CraftoryCore.crewService().crewOf(player.uuid).members)) {
        player.sendMessage(Message.translation("server.craftory.hurl.cannon-foreign"))
        return
    }
    val nets = IntPredicate { BlockType.getAssetMap().getAsset(it)?.id == CraftoryIds.CATCH_NET }
    val target = TargetUtil.getTargetBlock(rider, nets, 64.0, store)
    if (target == null || blockId(world, target.x, target.y, target.z) != CraftoryIds.CATCH_NET) {
        player.sendMessage(Message.translation("server.craftory.hurl.no-net"))
        return
    }
    val result =
        HurlRides.startArc(
            world,
            HurlPoint(position.x + 0.5, position.y + 1.0, position.z + 0.5),
            HurlPoint(target.x + 0.5, target.y + 1.0, target.z + 0.5),
            rider,
            player.uuid,
        )
    result.failure?.let {
        val key =
            when (it) {
                HurlStartFailure.ALREADY_RIDING -> "already-riding"
                HurlStartFailure.SPAWN_FAILED -> "spawn-failed"
                else -> "no-net"
            }
        player.sendMessage(Message.translation("server.craftory.hurl.$key"))
    }
}
