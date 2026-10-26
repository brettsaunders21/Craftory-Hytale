package studio.craftory.engine.transport

enum class TubeNodeKind { TUBE, STATION }

/** World-thread-owned derived topology. Network ids are ephemeral after topology changes. */
class TubeGraph {
    private val nodes = HashMap<Long, TubeNodeKind>()
    private val membership = HashMap<Long, Int>()
    private val networks = HashMap<Int, MutableSet<Long>>()
    private var nextNetwork = 0

    val nodeCount: Int get() = nodes.size
    val networkCount: Int get() = networks.size

    fun networkOf(pos: Long): Int = membership[pos] ?: -1

    fun stationsInNetwork(pos: Long): List<Long> =
        networks[networkOf(pos)].orEmpty().filter { nodes[it] == TubeNodeKind.STATION }

    fun add(
        pos: Long,
        kind: TubeNodeKind = TubeNodeKind.TUBE,
    ) {
        require(pos !in nodes) { "Node already exists: $pos" }
        val adjacent = neighbours(pos).mapNotNull { membership[it] }.distinct()
        val target = adjacent.maxByOrNull { networks.getValue(it).size } ?: nextNetwork++
        val members = networks.getOrPut(target) { LinkedHashSet() }
        for (id in adjacent) {
            if (id == target) continue
            for (member in networks.remove(id).orEmpty()) {
                members.add(member)
                membership[member] = target
            }
        }
        nodes[pos] = kind
        membership[pos] = target
        members.add(pos)
    }

    fun remove(pos: Long) {
        val id = membership.remove(pos) ?: return
        nodes.remove(pos)
        val remaining = networks.remove(id)!!
        remaining.remove(pos)
        while (remaining.isNotEmpty()) {
            val componentId = nextNetwork++
            val component = LinkedHashSet<Long>()
            val queue = ArrayDeque<Long>()
            val start = remaining.first()
            remaining.remove(start)
            queue.addLast(start)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component.add(current)
                membership[current] = componentId
                for (neighbour in neighbours(current)) {
                    if (remaining.remove(neighbour)) queue.addLast(neighbour)
                }
            }
            networks[componentId] = component
        }
    }

    /** Stations are valid intermediate nodes as well as endpoints. */
    fun route(
        from: Long,
        to: Long,
    ): TubeRoute? {
        if (nodes[from] != TubeNodeKind.STATION || nodes[to] != TubeNodeKind.STATION) return null
        if (networkOf(from) != networkOf(to)) return null
        val previous = HashMap<Long, Long>()
        val queue = ArrayDeque<Long>()
        previous[from] = from
        queue.addLast(from)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == to) break
            for (next in neighbours(current)) {
                if (next in nodes && next !in previous) {
                    previous[next] = current
                    queue.addLast(next)
                }
            }
        }
        val path = ArrayList<Long>()
        var current = to
        while (current != from) {
            path.add(current)
            current = previous.getValue(current)
        }
        path.add(from)
        path.reverse()
        return TubeRoute(path)
    }

    private fun neighbours(pos: Long): List<Long> = TubeBlock.unpack(pos).neighbours()
}
