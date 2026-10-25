package studio.craftory.core.api.energy

import com.hypixel.hytale.server.core.universe.world.World
import studio.craftory.core.energy.EnergyWorld

class EnergyAccess internal constructor(
    private val energy: EnergyWorld,
    private val world: World,
) {
    fun setProduction(
        pos: Long,
        amount: Long,
        rank: Int,
    ) {
        assertWorldThread()
        energy.setProduction(pos, amount, rank)
    }

    fun setDemand(
        pos: Long,
        amount: Long,
    ) {
        assertWorldThread()
        energy.setDemand(pos, amount)
    }

    fun stored(pos: Long): Long {
        assertWorldThread()
        return energy.stored(pos)
    }

    fun delivered(pos: Long): Long {
        assertWorldThread()
        return energy.delivered(pos)
    }

    fun deliveredRank(pos: Long): Int {
        assertWorldThread()
        return energy.deliveredRank(pos)
    }

    fun networkOf(pos: Long): Int {
        assertWorldThread()
        return energy.networkOf(pos)
    }

    fun networkMembers(pos: Long): Int {
        assertWorldThread()
        return energy.diagnostics(pos)?.members ?: 0
    }

    fun networkGrade(pos: Long): Int {
        assertWorldThread()
        return energy.diagnostics(pos)?.grade ?: 0
    }

    private fun assertWorldThread() {
        world.debugAssertInTickingThread()
    }
}
