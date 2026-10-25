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
    override fun getQuery(): Query<ChunkStore> = Query.or(Query.and(CrankState.type), Query.and(BurnerState.type))

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
}
