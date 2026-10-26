@file:Suppress("TooManyFunctions", "LoopWithTooManyJumpStatements")

package studio.craftory.core.api.transport

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
    private val displayIndexes = TransportLongIntMap()
    private var displayIds = LongArray(16)
    private var displayRefs = arrayOfNulls<Ref<EntityStore>>(16)
    private var displayLastSeen = LongArray(16)
    private var displayCount = 0
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
        if (viewerCount == 0) {
            despawnAll()
            return
        }
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
            var displayIndex = displayIndexes.getOrDefault(id, -1)
            if (displayIndex < 0) {
                val ref = createDisplay(currentWorld, runtime, index, x, y, z) ?: continue
                displayIndex = addDisplay(id, ref)
            }
            displayLastSeen[displayIndex] = tick
            updateDisplay(currentWorld, displayIndex, x, y, z)
        }
        collectStaleDisplays()
        removeStaleDisplays(currentWorld)
    }

    fun countInArea(
        currentWorld: World,
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int,
    ): Int {
        var count = 0
        for (index in 0 until displayCount) {
            val ref = displayRefs[index] ?: continue
            if (!ref.isValid) continue
            val transform =
                currentWorld.entityStore.store.getComponent(ref, TransformComponent.getComponentType()) ?: continue
            val point = transform.position
            if (isWithinArea(point.x, point.y, point.z, minX, minY, minZ, maxX, maxY, maxZ)) {
                count++
            }
        }
        return count
    }

    fun clearInArea(
        currentWorld: World,
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int,
    ): Int {
        var removed = 0
        var index = 0
        while (index < displayCount) {
            val ref = displayRefs[index]
            val transform =
                ref
                    ?.takeIf { it.isValid }
                    ?.let { currentWorld.entityStore.store.getComponent(it, TransformComponent.getComponentType()) }
            val point = transform?.position
            if (point != null && isWithinArea(point.x, point.y, point.z, minX, minY, minZ, maxX, maxY, maxZ)) {
                removeDisplayAt(index, currentWorld)
                removed++
            } else {
                index++
            }
        }
        return removed
    }

    private fun isWithinArea(
        x: Double,
        y: Double,
        z: Double,
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int,
    ): Boolean =
        x in minX.toDouble()..maxX.toDouble() &&
            y in minY.toDouble()..maxY.toDouble() &&
            z in minZ.toDouble()..maxZ.toDouble()

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
    ): Ref<EntityStore>? {
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
        return ref
    }

    private fun addDisplay(
        id: Long,
        ref: Ref<EntityStore>,
    ): Int {
        if (displayCount == displayIds.size) {
            val capacity = displayIds.size shl 1
            displayIds = displayIds.copyOf(capacity)
            displayRefs = displayRefs.copyOf(capacity)
            displayLastSeen = displayLastSeen.copyOf(capacity)
        }
        val index = displayCount++
        displayIds[index] = id
        displayRefs[index] = ref
        displayLastSeen[index] = tick
        displayIndexes.put(id, index)
        return index
    }

    private fun updateDisplay(
        currentWorld: World,
        displayIndex: Int,
        x: Double,
        y: Double,
        z: Double,
    ) {
        val ref = displayRefs[displayIndex] ?: return
        if (!ref.isValid) return
        val entityStore = currentWorld.entityStore.store
        val transform = entityStore.getComponent(ref, TransformComponent.getComponentType()) ?: return
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
        for (index in 0 until displayCount) {
            if (displayLastSeen[index] != tick) {
                ensureStaleCapacity(staleCount + 1)
                staleIds[staleCount++] = displayIds[index]
            }
        }
    }

    private fun ensureStaleCapacity(required: Int) {
        if (required <= staleIds.size) return
        var capacity = staleIds.size
        while (capacity < required) capacity = capacity shl 1
        staleIds = staleIds.copyOf(capacity)
    }

    private fun removeStaleDisplays(currentWorld: World) {
        for (index in 0 until staleCount) {
            val displayIndex = displayIndexes.getOrDefault(staleIds[index], -1)
            if (displayIndex >= 0) removeDisplayAt(displayIndex, currentWorld)
        }
    }

    private fun removeDisplayAt(
        index: Int,
        currentWorld: World,
    ) {
        val ref = displayRefs[index]
        if (ref != null && ref.isValid) currentWorld.entityStore.store.removeEntity(ref, RemoveReason.REMOVE)
        val id = displayIds[index]
        displayIndexes.remove(id)
        val last = --displayCount
        if (index != last) {
            val movedId = displayIds[last]
            displayIds[index] = movedId
            displayRefs[index] = displayRefs[last]
            displayLastSeen[index] = displayLastSeen[last]
            displayIndexes.put(movedId, index)
        }
        displayRefs[last] = null
    }

    private fun despawnAll() {
        val currentWorld = world ?: return
        val entityStore = currentWorld.entityStore.store
        for (index in 0 until displayCount) {
            val ref = displayRefs[index]
            if (ref != null && ref.isValid) entityStore.removeEntity(ref, RemoveReason.REMOVE)
            displayRefs[index] = null
        }
        displayCount = 0
        displayIndexes.clear()
    }

    private fun chunkKey(
        x: Double,
        z: Double,
    ): Long {
        val chunkX = kotlin.math.floor(x).toInt() shr 5
        val chunkZ = kotlin.math.floor(z).toInt() shr 5
        return (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xffffffffL)
    }

    companion object {
        private const val MAX_RENDER_DISTANCE_SQUARED = 64.0 * 64.0
        private val allStates =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<ItemTransitDisplayState>()
        lateinit var type: ResourceType<ChunkStore, ItemTransitDisplayState>

        fun shutdownAll() {
            allStates.forEach { state ->
                val currentWorld = state.world
                if (currentWorld?.isAlive == true) currentWorld.execute { state.despawnAll() }
            }
            allStates.clear()
        }
    }
}
