package studio.craftory.tech.machine

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class MachineClockLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.or(
            Query.and(CrankState.type),
            Query.and(BurnerState.type),
            Query.and(GnasherState.type),
            Query.and(OvenPower.type),
            Query.and(SnatcherClawState.type),
        )

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val tick = store.externalData.world.tick
        commandBuffer.getComponent(ref, CrankState.type)?.let { MachineClock.initialize(it, tick) }
        commandBuffer.getComponent(ref, BurnerState.type)?.let { MachineClock.initialize(it, tick) }
        commandBuffer.getComponent(ref, GnasherState.type)?.let { MachineClock.initialize(it, tick) }
        val ovenPower = commandBuffer.getComponent(ref, OvenPower.type)
        if (ovenPower != null) {
            val energy =
                studio.craftory.core.api.CraftoryCore
                    .energyAccess(store.externalData.world)
            ovenPower.lastFizzSolveTick = energy.lastSolveTick()
            MachineClock.initialize(ovenPower, tick)
        }
        val claw = commandBuffer.getComponent(ref, SnatcherClawState.type)
        if (claw != null) {
            claw.lastFizzSolveTick =
                studio.craftory.core.api.CraftoryCore
                    .energyAccess(store.externalData.world)
                    .lastSolveTick()
            claw.lastExtractTick = tick
        }
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) = Unit
}

internal object MachineClock {
    fun initialize(
        state: CrankState,
        tick: Long,
    ) {
        state.lastAdvancedTick = tick
    }

    fun initialize(
        state: BurnerState,
        tick: Long,
    ) {
        state.lastAdvancedTick = tick
    }

    fun initialize(
        state: GnasherState,
        tick: Long,
    ) {
        state.lastAutoCrankTick = tick
        state.lastPulseTick = null
    }

    fun initialize(
        state: OvenPower,
        tick: Long,
    ) {
        state.lastDeliveredTick = tick
    }

    fun pause(
        state: CrankState,
        tick: Long,
    ) {
        state.lastAdvancedTick = tick
    }

    fun pause(
        state: BurnerState,
        tick: Long,
    ) {
        state.lastAdvancedTick = tick
    }

    fun pause(
        state: GnasherState,
        tick: Long,
    ) {
        state.lastAutoCrankTick = tick
    }

    fun pause(
        state: OvenPower,
        tick: Long,
    ) {
        state.lastDeliveredTick = tick
    }
}
