package studio.craftory.engine.transport

import kotlin.math.floor

data class TubeBend(
    val nodeIndex: Int,
    val position: Long,
)

class TubeRoute internal constructor(
    path: List<Long>,
) {
    val nodes: List<Long> = path.toList()
    val bends: List<TubeBend> =
        (1 until nodes.lastIndex)
            .filter { index ->
                val before = TubeBlock.unpack(nodes[index - 1])
                val here = TubeBlock.unpack(nodes[index])
                val after = TubeBlock.unpack(nodes[index + 1])
                here.x - before.x != after.x - here.x ||
                    here.y - before.y != after.y - here.y ||
                    here.z - before.z != after.z - here.z
            }.map { TubeBend(it, nodes[it]) }
    val lengthBlocks: Double = nodes.lastIndex.toDouble()
    val spline: TubeSpline = TubeSpline(this)
}

/** Arc-length parameterised polyline through block centres, baked only at bends and endpoints. */
class TubeSpline internal constructor(
    route: TubeRoute,
) {
    private val nodes = route.nodes.toLongArray()
    private val indices =
        (listOf(0) + route.bends.map { it.nodeIndex } + listOf(nodes.lastIndex))
            .distinct()
            .toIntArray()
    private val points = indices.map { TubeBlock.unpack(nodes[it]) }.toTypedArray()
    val length: Double = route.lengthBlocks
    val sampleCount: Int get() = points.size

    /** Supply a reusable three-element output buffer to avoid allocations. Distances clamp to the route. */
    fun positionAt(
        distance: Double,
        out: DoubleArray = DoubleArray(3),
    ): DoubleArray {
        require(out.size >= 3)
        val d = clamped(distance)
        var segment = indices.binarySearch(d.toInt())
        if (segment < 0) segment = -segment - 2
        segment = segment.coerceIn(0, points.lastIndex)
        val end = (segment + 1).coerceAtMost(points.lastIndex)
        val span = indices[end] - indices[segment]
        val fraction = if (span == 0) 0.0 else (d - indices[segment]) / span
        val a = points[segment]
        val b = points[end]
        out[0] = a.x + 0.5 + (b.x - a.x) * fraction
        out[1] = a.y + 0.5 + (b.y - a.y) * fraction
        out[2] = a.z + 0.5 + (b.z - a.z) * fraction
        return out
    }

    /** At an exact shared face the next block in travel order owns the rider. */
    fun nodeIndexAt(distance: Double): Int = floor(clamped(distance) + 0.5).toInt().coerceAtMost(nodes.lastIndex)

    fun blockAt(distance: Double): Long = nodes[nodeIndexAt(distance)]

    fun blocksAhead(
        distance: Double,
        lookahead: Double,
    ): List<Long> {
        require(lookahead.isFinite() && lookahead >= 0.0)
        val start = clamped(distance)
        return (nodeIndexAt(start)..nodeIndexAt(start + lookahead)).map { nodes[it] }
    }

    private fun clamped(distance: Double): Double {
        require(!distance.isNaN())
        return distance.coerceIn(0.0, length)
    }
}
