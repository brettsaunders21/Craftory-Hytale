@file:Suppress("TooManyFunctions", "LoopWithTooManyJumpStatements")

package studio.craftory.core.transport

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.NonSerialized
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues
import com.hypixel.hytale.server.core.modules.physics.component.Velocity
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.Config
import org.joml.Vector3d
import studio.craftory.core.CraftoryCoreConfig

internal class ItemTransitDisplaySystem(
    private val config: Config<CraftoryCoreConfig>,
) : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val world = store.externalData.world
        store.getResource(ItemTransitDisplayState.type).tick(
            world,
            store.getResource(ItemTransportWorld.type),
            config.get().transitDisplayCapPerChunk.coerceAtLeast(0),
        )
    }

    companion object {
        fun shutdown() = ItemTransitDisplayState.shutdownAll()
    }
}

internal class ItemTransitDisplayState : Resource<ChunkStore> {
    private val displays = HashMap<Long, Display>()
    private var viewers = DoubleArray(48)
    private var viewerCount = 0
    private var chunkKeys = LongArray(32)
    private var chunkCounts = IntArray(32)
    private var chunkCount = 0
    private var staleIds = LongArray(16)
    private var staleCount = 0
    private val position = Vector3d()
    private var world: World? = null
    private var tick = 0L

    init {
        allStates.add(this)
    }

    override fun clone(): Resource<ChunkStore> = ItemTransitDisplayState()

    fun tick(
        currentWorld: World,
        runtime: ItemTransportWorld,
        capPerChunk: Int,
    ) {
        world = currentWorld
        tick++
        collectViewers(currentWorld)
        chunkCount = 0
        for (index in 0 until runtime.graphItemCount) {
            val x = runtime.itemWorldX(index)
            val y = runtime.itemWorldY(index)
            val z = runtime.itemWorldZ(index)
            if (!isNearViewer(x, y, z)) continue
            val chunkKey = chunkKey(x, z)
            val chunkIndex = countInChunk(chunkKey)
            if (chunkIndex >= capPerChunk) continue
            incrementChunkCount(chunkKey, chunkIndex)
            val id = runtime.itemId(index)
            val display =
                displays[id] ?: createDisplay(currentWorld, runtime, index, x, y, z)?.also { displays[id] = it }
                    ?: continue
            display.lastSeen = tick
            updateDisplay(currentWorld, display, x, y, z)
        }
        collectStaleDisplays()
        removeStaleDisplays(currentWorld)
    }

    private fun collectViewers(currentWorld: World) {
        viewerCount = 0
        val entityStore = currentWorld.entityStore.store
        for (playerRef in currentWorld.playerRefs) {
            val ref = playerRef.reference ?: continue
            if (ref.store !== entityStore) continue
            val transform = entityStore.getComponent(ref, TransformComponent.getComponentType()) ?: continue
            val point = transform.position
            ensureViewerCapacity(viewerCount + 1)
            val offset = viewerCount * 3
            viewers[offset] = point.x
            viewers[offset + 1] = point.y
            viewers[offset + 2] = point.z
            viewerCount++
        }
    }

    private fun ensureViewerCapacity(required: Int) {
        val requiredLength = required * 3
        if (requiredLength <= viewers.size) return
        var size = viewers.size
        while (size < requiredLength) size = size shl 1
        viewers = viewers.copyOf(size)
    }

    private fun isNearViewer(
        x: Double,
        y: Double,
        z: Double,
    ): Boolean {
        for (index in 0 until viewerCount) {
            val offset = index * 3
            val dx = viewers[offset] - x
            val dy = viewers[offset + 1] - y
            val dz = viewers[offset + 2] - z
            if (dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQUARED) return true
        }
        return false
    }

    private fun countInChunk(key: Long): Int {
        for (index in 0 until chunkCount) {
            if (chunkKeys[index] == key) return chunkCounts[index]
        }
        return 0
    }

    private fun incrementChunkCount(
        key: Long,
        currentCount: Int,
    ) {
        for (index in 0 until chunkCount) {
            if (chunkKeys[index] == key) {
                chunkCounts[index] = currentCount + 1
                return
            }
        }
        if (chunkCount == chunkKeys.size) {
            chunkKeys = chunkKeys.copyOf(chunkKeys.size shl 1)
            chunkCounts = chunkCounts.copyOf(chunkCounts.size shl 1)
        }
        chunkKeys[chunkCount] = key
        chunkCounts[chunkCount] = 1
        chunkCount++
    }

    private fun createDisplay(
        currentWorld: World,
        runtime: ItemTransportWorld,
        index: Int,
        x: Double,
        y: Double,
        z: Double,
    ): Display? {
        val itemId = runtime.itemIdString(index) ?: return null
        val entityStore = currentWorld.entityStore.store
        val holder =
            ItemComponent.generateItemDrop(
                entityStore,
                ItemStack(itemId, 1),
                Vector3d(x, y, z),
                Rotation3f.ZERO,
                0f,
                0f,
                0f,
            ) ?: return null
        holder.ensureComponent(PreventPickup.getComponentType())
        holder.ensureComponent(PreventItemMerging.getComponentType())
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get())
        holder.tryRemoveComponent(Velocity.getComponentType())
        holder.tryRemoveComponent(PhysicsValues.getComponentType())
        holder.tryRemoveComponent(DespawnComponent.getComponentType())
        val ref = entityStore.addEntity(holder, AddReason.SPAWN) ?: return null
        return Display(ref, tick)
    }

    private fun updateDisplay(
        currentWorld: World,
        display: Display,
        x: Double,
        y: Double,
        z: Double,
    ) {
        if (!display.ref.isValid) return
        val entityStore = currentWorld.entityStore.store
        val transform = entityStore.getComponent(display.ref, TransformComponent.getComponentType()) ?: return
        position.set(x, y, z)
        transform.setPosition(position)
        transform.setSectionLocation(
            currentWorld.chunkStore.getChunkSectionReferenceAtBlock(
                kotlin.math.floor(x).toInt(),
                kotlin.math.floor(y).toInt(),
                kotlin.math.floor(z).toInt(),
            ),
        )
    }

    private fun collectStaleDisplays() {
        staleCount = 0
        displays.forEach { (id, display) ->
            if (display.lastSeen != tick) {
                if (staleCount == staleIds.size) staleIds = staleIds.copyOf(staleIds.size shl 1)
                staleIds[staleCount++] = id
            }
        }
    }

    private fun removeStaleDisplays(currentWorld: World) {
        val entityStore = currentWorld.entityStore.store
        for (index in 0 until staleCount) {
            val display = displays.remove(staleIds[index]) ?: continue
            if (display.ref.isValid) entityStore.removeEntity(display.ref, RemoveReason.REMOVE)
        }
    }

    private fun despawnAll() {
        val currentWorld = world ?: return
        val entityStore = currentWorld.entityStore.store
        displays.values.forEach { display ->
            if (display.ref.isValid) entityStore.removeEntity(display.ref, RemoveReason.REMOVE)
        }
        displays.clear()
    }

    private fun chunkKey(
        x: Double,
        z: Double,
    ): Long {
        val chunkX = kotlin.math.floor(x).toInt() shr 5
        val chunkZ = kotlin.math.floor(z).toInt() shr 5
        return (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xffffffffL)
    }

    private data class Display(
        val ref: Ref<EntityStore>,
        var lastSeen: Long,
    )

    companion object {
        private const val MAX_RENDER_DISTANCE_SQUARED = 64.0 * 64.0
        private val allStates =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<ItemTransitDisplayState>()
        lateinit var type: ResourceType<ChunkStore, ItemTransitDisplayState>

        fun shutdownAll() {
            allStates.forEach { state ->
                state.world?.execute { state.despawnAll() }
            }
            allStates.clear()
        }
    }
}
