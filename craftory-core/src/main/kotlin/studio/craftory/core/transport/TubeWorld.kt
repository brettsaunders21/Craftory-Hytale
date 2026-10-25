package studio.craftory.core.transport

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.transport.HurlRoute
import studio.craftory.core.api.transport.TubeNode
import studio.craftory.engine.transport.TubeGraph
import studio.craftory.engine.transport.TubeNodeKind

internal class TubeWorld : Resource<ChunkStore> {
    private val graph = TubeGraph()
    private val present = HashSet<Long>()

    override fun clone(): Resource<ChunkStore> = TubeWorld()

    fun add(
        pos: Long,
        node: TubeNode,
    ) {
        if (!present.add(pos)) return
        graph.add(pos, if (node.kind == TubeNode.STATION) TubeNodeKind.STATION else TubeNodeKind.TUBE)
    }

    fun remove(pos: Long) {
        if (present.remove(pos)) graph.remove(pos)
    }

    fun isPresent(pos: Long): Boolean = pos in present

    fun networkOf(pos: Long): Int = graph.networkOf(pos)

    fun stationsReachableFrom(pos: Long): List<Long> =
        if (pos in graph.stationsInNetwork(pos)) graph.stationsInNetwork(pos).filter { it != pos } else emptyList()

    fun route(
        from: Long,
        to: Long,
    ): HurlRoute? = graph.route(from, to)?.let(::HurlRoute)

    val nodeCount: Int get() = graph.nodeCount
    val networkCount: Int get() = graph.networkCount

    companion object {
        lateinit var type: ResourceType<ChunkStore, TubeWorld>
    }
}
