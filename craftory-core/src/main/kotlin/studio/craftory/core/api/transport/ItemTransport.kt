package studio.craftory.core.api.transport

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import java.util.concurrent.ConcurrentHashMap

object ItemTransport {
    private val inputAdapters = ConcurrentHashMap<ComponentType<ChunkStore, *>, ItemInputAdapter>()

    fun of(world: World): ItemTransportAccess {
        world.debugAssertInTickingThread()
        return world.chunkStore.store
            .getResource(ItemTransportWorld.type)
            .access
    }

    fun <T : Component<ChunkStore>> registerInput(
        componentType: ComponentType<ChunkStore, T>,
        adapter: ItemInputAdapter,
    ) {
        check(inputAdapters.putIfAbsent(componentType, adapter) == null) {
            "An item input adapter is already registered for $componentType"
        }
    }

    internal fun inputAdapters(): Map<ComponentType<ChunkStore, *>, ItemInputAdapter> = inputAdapters
}

@Suppress("TooManyFunctions")
class ItemTransportAccess internal constructor(
    private val runtime: ItemTransportWorld,
) {
    fun insert(
        pipeX: Int,
        pipeY: Int,
        pipeZ: Int,
        itemId: String,
        quantity: Int,
        colour: TransportColour = TransportColour.NONE,
        originX: Int = Int.MIN_VALUE,
        originY: Int = Int.MIN_VALUE,
        originZ: Int = Int.MIN_VALUE,
    ): Boolean {
        val origin =
            if (originX == Int.MIN_VALUE && originY == Int.MIN_VALUE && originZ == Int.MIN_VALUE) {
                Long.MIN_VALUE
            } else {
                BlockPos(originX, originY, originZ).pack()
            }
        return runtime.insert(BlockPos(pipeX, pipeY, pipeZ).pack(), itemId, quantity, colour.value, origin)
    }

    fun isPipe(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = runtime.isPipe(BlockPos(x, y, z).pack())

    fun itemsInTransit(): Int = runtime.itemsInTransit()

    fun canInsert(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = runtime.canInsert(BlockPos(x, y, z).pack())

    fun itemsOwnedBy(
        x: Int,
        y: Int,
        z: Int,
    ): Int = runtime.itemsOwnedBy(BlockPos(x, y, z).pack())

    fun setColour(
        x: Int,
        y: Int,
        z: Int,
        colour: TransportColour,
    ): Boolean = runtime.setColour(BlockPos(x, y, z).pack(), colour.value)

    fun setColour(
        store: Store<ChunkStore>,
        ref: Ref<ChunkStore>,
        colour: TransportColour,
    ): Boolean = runtime.setColour(store, ref, colour.value)

    fun colourOf(
        x: Int,
        y: Int,
        z: Int,
    ): TransportColour = TransportColour.fromValue(runtime.colourOf(BlockPos(x, y, z).pack()))

    fun progressOf(itemId: Long): Int? = runtime.progressOf(itemId)

    fun itemIdAt(index: Int): Long = runtime.itemId(index)

    fun itemStringAt(index: Int): String? = runtime.itemIdString(index)

    fun itemOwnerAt(index: Int): Long = runtime.itemOwner(index)

    fun itemProgressAt(index: Int): Int = runtime.itemProgress(index)

    fun clearDisplaysInArea(
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int,
    ): Int = runtime.clearDisplaysInArea(minX, minY, minZ, maxX, maxY, maxZ)

    fun displaysInArea(
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int,
    ): Int = runtime.displaysInArea(minX, minY, minZ, maxX, maxY, maxZ)
}
