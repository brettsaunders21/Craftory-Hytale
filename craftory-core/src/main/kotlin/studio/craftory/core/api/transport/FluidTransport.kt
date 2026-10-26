package studio.craftory.core.api.transport

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.transport.FluidTransportWorld
import java.util.concurrent.ConcurrentHashMap

fun interface FluidHandler<T : Component<ChunkStore>> {
    fun fill(
        component: T,
        fluid: String,
        amount: Long,
    ): Long
}

object FluidTransport {
    private val handlers = ConcurrentHashMap<ComponentType<ChunkStore, *>, FluidHandler<*>>()

    fun of(world: World): FluidTransportAccess {
        world.debugAssertInTickingThread()
        return world.chunkStore.store
            .getResource(FluidTransportWorld.type)
            .access
    }

    fun <T : Component<ChunkStore>> registerHandler(
        componentType: ComponentType<ChunkStore, T>,
        handler: FluidHandler<T>,
    ) {
        check(handlers.putIfAbsent(componentType, handler) == null) {
            "A fluid handler is already registered for $componentType"
        }
    }

    internal fun handlers(): Map<ComponentType<ChunkStore, *>, FluidHandler<*>> = handlers
}

class FluidTransportAccess internal constructor(
    private val runtime: FluidTransportWorld,
) {
    fun amount(
        x: Int,
        y: Int,
        z: Int,
    ): Long = runtime.amount(BlockPos(x, y, z).pack())

    fun capacity(
        x: Int,
        y: Int,
        z: Int,
    ): Long = runtime.capacity(BlockPos(x, y, z).pack())

    fun fluid(
        x: Int,
        y: Int,
        z: Int,
    ): String? = runtime.fluid(BlockPos(x, y, z).pack())

    fun insert(
        x: Int,
        y: Int,
        z: Int,
        fluid: String,
        amount: Long,
    ): Long = runtime.insert(BlockPos(x, y, z).pack(), fluid, amount)

    fun drain(
        x: Int,
        y: Int,
        z: Int,
        amount: Long,
    ): Long = runtime.drain(BlockPos(x, y, z).pack(), amount)

    fun isNode(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = runtime.isNode(BlockPos(x, y, z).pack())

    fun insert(
        store: Store<ChunkStore>,
        ref: Ref<ChunkStore>,
        fluid: String,
        amount: Long,
    ): Long = runtime.insert(store, ref, fluid, amount)
}
