package studio.craftory.tech.transport

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.transport.HurlArc
import studio.craftory.core.api.transport.HurlPoint
import studio.craftory.core.api.transport.HurlRoute
import java.util.UUID
import kotlin.math.floor

enum class HurlMode { TUBE, ARC }

class HurlRide : Component<EntityStore> {
    lateinit var route: HurlRoute
    var arc: HurlArc? = null
    var flightTime = 0.0
    var rider: Ref<EntityStore>? = null
    var riderUuid: UUID? = null
    var distance = 0.0
    var speed = HurlConfig.DEFAULT_SPEED
    var mode = HurlMode.TUBE
    var mounted = false
    var exitPosition: HurlPoint? = null
    private var leases = HurlLeaseState()
    val heldChunks get() = leases.heldChunks
    internal val wantedChunks get() = leases.wantedChunks
    internal val pendingChunks get() = leases.pendingChunks
    internal val pendingSections get() = leases.pendingSections
    internal val loadedSections get() = leases.loadedSections
    internal val output = DoubleArray(3)
    internal val position = Vector3d()
    internal var loadFailed: Boolean
        get() = leases.loadFailed
        set(value) {
            leases.loadFailed = value
        }
    internal var ended: Boolean
        get() = leases.ended
        set(value) {
            leases.ended = value
        }

    override fun clone(): Component<EntityStore> =
        HurlRide().also {
            if (::route.isInitialized) it.route = route
            it.arc = arc
            it.flightTime = flightTime
            it.rider = rider
            it.riderUuid = riderUuid
            it.distance = distance
            it.speed = speed
            it.mode = mode
            it.leases = leases
            it.mounted = mounted
            it.exitPosition = exitPosition
            it.position.set(position)
            output.copyInto(it.output)
        }

    val landing: HurlPoint
        get() =
            arc?.landing
                ?: BlockPos.unpack(route.nodes.last()).let { HurlPoint(it.x + 0.5, it.y + 1.0, it.z + 0.5) }

    fun currentBlock(): Long = if (mode == HurlMode.TUBE) route.blockAt(distance) else packed(output)

    internal fun blocksAhead(): List<Long> {
        if (mode == HurlMode.TUBE) return route.blocksAhead(distance, HurlConfig.LOOKAHEAD + speed)
        val trajectory = requireNotNull(arc)
        val end = (flightTime + HurlConfig.LOOKAHEAD).coerceAtMost(trajectory.flightTicks)
        val sample = DoubleArray(3)
        val blocks = linkedSetOf<Long>()
        var time = flightTime
        while (time < end) {
            trajectory.positionAt(time, sample)
            blocks.add(packed(sample))
            time += 0.25
        }
        trajectory.positionAt(end, sample)
        blocks.add(packed(sample))
        return blocks.toList()
    }

    internal fun stepArc(): Boolean {
        val trajectory = requireNotNull(arc)
        flightTime = (flightTime + 1.0).coerceAtMost(trajectory.flightTicks)
        trajectory.positionAt(flightTime, output)
        return flightTime >= trajectory.flightTicks
    }

    private fun packed(point: DoubleArray): Long =
        BlockPos.pack(floor(point[0]).toInt(), floor(point[1]).toInt(), floor(point[2]).toInt())

    companion object {
        lateinit var type: ComponentType<EntityStore, HurlRide>
    }
}

private class HurlLeaseState {
    val heldChunks = mutableSetOf<Long>()
    val wantedChunks = mutableSetOf<Long>()
    val pendingChunks = mutableSetOf<Long>()
    val pendingSections = mutableSetOf<Long>()
    val loadedSections = mutableSetOf<Long>()
    var loadFailed = false
    var ended = false
}

class HurlRider(
    var capsule: Ref<EntityStore>? = null,
) : Component<EntityStore> {
    override fun clone(): Component<EntityStore> = HurlRider(capsule)

    companion object {
        lateinit var type: ComponentType<EntityStore, HurlRider>
    }
}

class HurlFallGuard(
    var expiryTick: Long = 0,
) : Component<EntityStore> {
    fun protects(
        tick: Long,
        fall: Boolean,
    ): Boolean = fall && tick < expiryTick

    override fun clone(): Component<EntityStore> = HurlFallGuard(expiryTick)

    companion object {
        lateinit var type: ComponentType<EntityStore, HurlFallGuard>
    }
}

class HurlCounters(
    val config: HurlConfig = HurlConfig(),
) : Resource<EntityStore> {
    var capsules: Int = 0
        internal set
    var chunkHolders: Int = 0
        internal set

    override fun clone(): Resource<EntityStore> = HurlCounters(config)

    companion object {
        lateinit var type: ResourceType<EntityStore, HurlCounters>
    }
}
