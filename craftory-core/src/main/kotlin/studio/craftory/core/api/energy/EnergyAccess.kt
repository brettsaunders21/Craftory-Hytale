package studio.craftory.core.api.energy

import com.hypixel.hytale.server.core.universe.world.World
import studio.craftory.core.energy.EnergyWorld

@Suppress("TooManyFunctions")
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

    fun converterInputDelivered(pos: Long): Long {
        assertWorldThread()
        return energy.converterInputDelivered(pos)
    }

    fun setConverterOutput(
        pos: Long,
        amount: Long,
        rank: Int,
    ) {
        assertWorldThread()
        energy.setConverterOutput(pos, amount, rank)
    }

    fun stored(pos: Long): Long {
        assertWorldThread()
        return energy.stored(pos)
    }

    fun networkStored(
        pos: Long,
        minRank: Int,
    ): Long {
        assertWorldThread()
        return energy.networkStored(pos, minRank)
    }

    fun drawStored(
        pos: Long,
        amount: Long,
        minGradeRank: Int,
    ): Boolean {
        assertWorldThread()
        return energy.drawStored(pos, amount, minGradeRank)
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

    fun capacity(pos: Long): Long {
        assertWorldThread()
        return energy.capacity(pos)
    }

    fun networkGrade(pos: Long): Int {
        assertWorldThread()
        return energy.diagnostics(pos)?.grade ?: 0
    }

    fun networkSupply(pos: Long): Long {
        assertWorldThread()
        return energy.diagnostics(pos)?.supply ?: 0L
    }

    fun networkDemand(pos: Long): Long {
        assertWorldThread()
        return energy.diagnostics(pos)?.demand ?: 0L
    }

    fun networkFlow(pos: Long): Long = networkSupply(pos) - networkDemand(pos)

    fun lastSolveTick(): Long {
        assertWorldThread()
        return energy.lastSolveTick
    }

    private fun assertWorldThread() {
        world.debugAssertInTickingThread()
    }
}
