package studio.craftory.engine.transport

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

data class TransportPoint(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    internal fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
}

/** Tick-based ballistic trajectory with positive downward gravity. */
class BallisticArc(
    private val launch: TransportPoint,
    val landing: TransportPoint,
    horizontalSpeed: Double = 1.0,
    private val gravity: Double = 0.08,
    maxRange: Double = 64.0,
) {
    val flightTicks: Double
    private val velocityY: Double

    init {
        require(launch.isFinite() && landing.isFinite())
        require(horizontalSpeed.isFinite() && horizontalSpeed > 0.0)
        require(gravity.isFinite() && gravity > 0.0)
        require(maxRange.isFinite() && maxRange >= 0.0)
        val horizontal = hypot(landing.x - launch.x, landing.z - launch.z)
        val height = landing.y - launch.y
        require(hypot(horizontal, height) <= maxRange) { "Target beyond maximum range" }
        flightTicks = max(1.0, max(horizontal / horizontalSpeed, sqrt(2.0 * kotlin.math.abs(height) / gravity)))
        velocityY = height / flightTicks + gravity * flightTicks / 2.0
    }

    fun positionAt(
        t: Double,
        out: DoubleArray = DoubleArray(3),
    ): DoubleArray {
        require(!t.isNaN() && out.size >= 3)
        val time = t.coerceIn(0.0, flightTicks)
        val fraction = time / flightTicks
        out[0] = launch.x + (landing.x - launch.x) * fraction
        out[1] = launch.y + velocityY * time - gravity * time * time / 2.0
        out[2] = launch.z + (landing.z - launch.z) * fraction
        return out
    }
}

object HurlCost {
    fun unitsFor(
        lengthBlocks: Double,
        blocksPerUnit: Double = 16.0,
    ): Int {
        require(lengthBlocks.isFinite() && lengthBlocks >= 0.0)
        require(blocksPerUnit.isFinite() && blocksPerUnit > 0.0)
        val units = ceil(lengthBlocks / blocksPerUnit)
        require(units <= Int.MAX_VALUE)
        return max(1, units.toInt())
    }
}
