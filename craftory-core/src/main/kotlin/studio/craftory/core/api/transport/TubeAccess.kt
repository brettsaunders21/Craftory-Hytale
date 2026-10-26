package studio.craftory.core.api.transport

import com.hypixel.hytale.server.core.universe.world.World
import studio.craftory.core.transport.TubeWorld

class TubeAccess internal constructor(
    private val tubes: TubeWorld,
    private val world: World,
) {
    fun isPresent(pos: Long): Boolean {
        world.debugAssertInTickingThread()
        return tubes.isPresent(pos)
    }

    fun networkOf(pos: Long): Int {
        world.debugAssertInTickingThread()
        return tubes.networkOf(pos)
    }

    fun stationsReachableFrom(stationPos: Long): List<Long> {
        world.debugAssertInTickingThread()
        return tubes.stationsReachableFrom(stationPos)
    }

    fun route(
        fromStation: Long,
        toStation: Long,
    ): HurlRoute? {
        world.debugAssertInTickingThread()
        return tubes.route(fromStation, toStation)
    }

    val nodeCount: Int get() {
        world.debugAssertInTickingThread()
        return tubes.nodeCount
    }

    val networkCount: Int get() {
        world.debugAssertInTickingThread()
        return tubes.networkCount
    }
}
