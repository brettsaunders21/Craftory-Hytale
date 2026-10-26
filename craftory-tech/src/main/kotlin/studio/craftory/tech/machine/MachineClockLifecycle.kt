package studio.craftory.tech.machine

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.tech.machine.jank.BodgeSlots
import studio.craftory.tech.machine.jank.Jank

internal class MachineClockLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.or(
            Query.and(CrankState.type),
            Query.and(BurnerState.type),
            Query.and(RattlewheelState.type),
            Query.and(ShroomBedState.type),
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
        commandBuffer.ensureComponent(ref, Jank.type)
        commandBuffer.ensureComponent(ref, BodgeSlots.type)
        val tick = store.externalData.world.tick
        commandBuffer.getComponent(ref, CrankState.type)?.let { MachineClock.initialize(it, tick) }
        commandBuffer.getComponent(ref, BurnerState.type)?.let { MachineClock.initialize(it, tick) }
        commandBuffer.getComponent(ref, RattlewheelState.type)?.let { MachineClock.initialize(it, tick) }
        commandBuffer.getComponent(ref, ShroomBedState.type)?.let { MachineClock.initialize(it, tick) }
        commandBuffer.getComponent(ref, GnasherState.type)?.let { MachineClock.initialize(it, tick) }
        val ovenPower = commandBuffer.getComponent(ref, OvenPower.type)
        if (ovenPower != null) {
            val energy =
                studio.craftory.core.api.CraftoryCore
                    .energyAccess(store.externalData.world)
            ovenPower.lastFizzSolveTick = energy.lastSolveTick()
            MachineClock.initialize(
                ovenPower,
                commandBuffer.getComponent(ref, ProcessingBenchBlock.getComponentType()),
            )
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
    ): Unit = Unit
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
        state: RattlewheelState,
        tick: Long,
    ) {
        if (state.lastSampleTick == Long.MIN_VALUE) state.lastSampleTick = tick - 100L
    }

    fun initialize(
        state: ShroomBedState,
        tick: Long,
    ) {
        if (state.lastUpdateTick == Long.MIN_VALUE) state.lastUpdateTick = tick
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
        bench: ProcessingBenchBlock?,
    ) {
        state.lastInputProgress = bench?.inputProgress
        state.lastRecipeId = bench?.recipe?.id
        state.lastRecipeTimeSeconds =
            bench
                ?.takeIf { it.recipe != null }
                ?.getRecipeTimeSeconds(1)
        state.status = MachineStatus.IDLE
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
}
