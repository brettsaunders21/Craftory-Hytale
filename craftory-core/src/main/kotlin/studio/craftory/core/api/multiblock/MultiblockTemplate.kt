package studio.craftory.core.api.multiblock

import studio.craftory.core.api.BlockPos

data class TemplatePart(
    val offset: BlockPos,
    val blockIds: Set<String>,
    val itemId: String,
) {
    init {
        require(blockIds.isNotEmpty()) { "Template part needs at least one block id" }
        require(blockIds.none(String::isBlank)) { "Template part block ids must not be blank" }
        require(itemId.isNotBlank()) { "Template part item id must not be blank" }
        require(offset != BlockPos(0, 0, 0)) { "Template part must not overlap the controller" }
    }

    val placedBlockId: String get() = blockIds.first()
}

data class MultiblockTemplate(
    val id: String,
    val parts: List<TemplatePart>,
) {
    init {
        require(id.isNotBlank()) { "Template id must not be blank" }
        require(parts.isNotEmpty()) { "Template needs at least one part" }
        require(parts.map(TemplatePart::offset).toSet().size == parts.size) { "Template part offsets must be unique" }
    }

    /** Parts in hammer order: lowest y first, then template list order. */
    val hammerOrder: List<TemplatePart> by lazy { parts.sortedBy { it.offset.y } }
}

data class WorldPart(
    val position: BlockPos,
    val part: TemplatePart,
)

/**
 * Horizontal rotation helpers. Quarter turns follow the controller block's yaw:
 * 0 faces north (-Z), 1 faces west (-X), 2 faces south (+Z), 3 faces east (+X),
 * matching Hytale's `Rotation.rotateY`.
 */
object MultiblockGeometry {
    const val MAX_FOOTPRINT = 7
    const val ROTATIONS = 4

    fun rotate(
        offset: BlockPos,
        quarterTurns: Int,
    ): BlockPos =
        when (Math.floorMod(quarterTurns, ROTATIONS)) {
            0 -> offset
            1 -> BlockPos(offset.z, offset.y, -offset.x)
            2 -> BlockPos(-offset.x, offset.y, -offset.z)
            else -> BlockPos(-offset.z, offset.y, offset.x)
        }

    fun worldPosition(
        controller: BlockPos,
        offset: BlockPos,
        quarterTurns: Int,
    ): BlockPos {
        val rotated = rotate(offset, quarterTurns)
        return BlockPos(controller.x + rotated.x, controller.y + rotated.y, controller.z + rotated.z)
    }

    fun worldParts(
        template: MultiblockTemplate,
        controller: BlockPos,
        quarterTurns: Int,
    ): List<WorldPart> =
        template.hammerOrder.map { part -> WorldPart(worldPosition(controller, part.offset, quarterTurns), part) }

    /** Plan footprint (width along X, depth along Z) including the controller. */
    fun footprint(template: MultiblockTemplate): Pair<Int, Int> {
        val xs = template.parts.map { it.offset.x } + 0
        val zs = template.parts.map { it.offset.z } + 0
        return (xs.max() - xs.min() + 1) to (zs.max() - zs.min() + 1)
    }

    fun fitsFootprint(template: MultiblockTemplate): Boolean {
        val (width, depth) = footprint(template)
        return width <= MAX_FOOTPRINT && depth <= MAX_FOOTPRINT
    }
}
