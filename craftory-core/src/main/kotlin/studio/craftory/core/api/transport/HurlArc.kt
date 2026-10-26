package studio.craftory.core.api.transport

import studio.craftory.engine.transport.BallisticArc
import studio.craftory.engine.transport.HurlCost
import studio.craftory.engine.transport.TransportPoint
import kotlin.math.hypot

data class HurlPoint(
    val x: Double,
    val y: Double,
    val z: Double,
)

class HurlArc private constructor(
    private val arc: BallisticArc,
    val landing: HurlPoint,
) {
    val flightTicks: Double get() = arc.flightTicks

    fun positionAt(
        t: Double,
        out: DoubleArray,
    ): DoubleArray = arc.positionAt(t, out)

    companion object {
        /** Range is measured in three dimensions; malformed inputs throw rather than returning null. */
        fun create(
            launch: HurlPoint,
            target: HurlPoint,
            horizontalSpeed: Double = 1.0,
            gravity: Double = 0.08,
            maxRange: Double = 64.0,
        ): HurlArc? {
            require(listOf(launch.x, launch.y, launch.z, target.x, target.y, target.z).all { it.isFinite() })
            require(horizontalSpeed.isFinite() && horizontalSpeed > 0.0)
            require(gravity.isFinite() && gravity > 0.0)
            require(maxRange.isFinite() && maxRange >= 0.0)
            if (hypot(hypot(target.x - launch.x, target.z - launch.z), target.y - launch.y) > maxRange) return null
            return HurlArc(
                BallisticArc(
                    TransportPoint(launch.x, launch.y, launch.z),
                    TransportPoint(target.x, target.y, target.z),
                    horizontalSpeed,
                    gravity,
                    maxRange,
                ),
                target,
            )
        }
    }
}

object HurlMath {
    fun unitsFor(
        lengthBlocks: Double,
        blocksPerUnit: Double = 16.0,
    ): Int = HurlCost.unitsFor(lengthBlocks, blocksPerUnit)
}
