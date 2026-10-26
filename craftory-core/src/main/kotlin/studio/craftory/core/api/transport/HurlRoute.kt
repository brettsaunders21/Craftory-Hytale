package studio.craftory.core.api.transport

import studio.craftory.engine.transport.TubeRoute

data class HurlBend(
    val nodeIndex: Int,
    val position: Long,
)

class HurlRoute internal constructor(
    private val route: TubeRoute,
) {
    val nodes: List<Long> = route.nodes.toList()
    val bends: List<HurlBend> = route.bends.map { HurlBend(it.nodeIndex, it.position) }
    val lengthBlocks: Double get() = route.lengthBlocks

    fun positionAt(
        distance: Double,
        out: DoubleArray,
    ): DoubleArray = route.spline.positionAt(distance, out)

    fun blockAt(distance: Double): Long = route.spline.blockAt(distance)

    fun blocksAhead(
        distance: Double,
        lookahead: Double,
    ): List<Long> = route.spline.blocksAhead(distance, lookahead)
}
