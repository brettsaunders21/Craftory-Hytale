package studio.craftory.core.api

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.util.FillerBlockUtil
import org.joml.Vector3i

object BlockFootprints {
    @JvmStatic
    fun origin(
        store: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Vector3i {
        val sectionRef =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(x, y, z) ?: return Vector3i(x, y, z)
        val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return Vector3i(x, y, z)
        return originFromFiller(x, y, z, section.getFiller(x and 31, y and 31, z and 31))
    }

    @JvmStatic
    fun cells(
        store: Store<ChunkStore>,
        originX: Int,
        originY: Int,
        originZ: Int,
    ): List<Vector3i> {
        val origin = Vector3i(originX, originY, originZ)
        val sectionRef =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(originX, originY, originZ) ?: return listOf(origin)
        val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return listOf(origin)
        val localX = originX and 31
        val localY = originY and 31
        val localZ = originZ and 31
        val blockType =
            BlockType.getAssetMap().getAsset(section.get(localX, localY, localZ)) ?: return listOf(origin)
        return cells(blockType, section.getRotation(localX, localY, localZ), originX, originY, originZ)
    }

    @JvmStatic
    fun cells(
        blockType: BlockType,
        rotationIndex: Int,
        originX: Int,
        originY: Int,
        originZ: Int,
    ): List<Vector3i> = cells(blockType, RotationTuple.get(rotationIndex), originX, originY, originZ)

    @JvmStatic
    fun cells(
        blockType: BlockType,
        rotation: RotationTuple,
        originX: Int,
        originY: Int,
        originZ: Int,
    ): List<Vector3i> {
        val origin = Vector3i(originX, originY, originZ)
        val hitbox =
            BlockBoundingBoxes.getAssetMap().getAsset(blockType.hitboxTypeIndex) ?: return listOf(origin)
        val rotatedBoxes = hitbox.get(rotation.yaw, rotation.pitch, rotation.roll)
        val offsets = mutableListOf<Vector3i>()
        FillerBlockUtil.forEachFillerBlock(rotatedBoxes) { x, y, z ->
            offsets += Vector3i(x, y, z)
        }
        return cellsAt(originX, originY, originZ, offsets)
    }

    internal fun originFromFiller(
        x: Int,
        y: Int,
        z: Int,
        filler: Int,
    ): Vector3i =
        if (filler == 0) {
            Vector3i(x, y, z)
        } else {
            Vector3i(
                x - FillerBlockUtil.unpackX(filler),
                y - FillerBlockUtil.unpackY(filler),
                z - FillerBlockUtil.unpackZ(filler),
            )
        }

    internal fun cellsAt(
        originX: Int,
        originY: Int,
        originZ: Int,
        offsets: Iterable<Vector3i>,
    ): List<Vector3i> =
        linkedSetOf(Vector3i(originX, originY, originZ))
            .apply {
                offsets.forEach { offset ->
                    add(Vector3i(originX + offset.x, originY + offset.y, originZ + offset.z))
                }
            }.toList()
}
