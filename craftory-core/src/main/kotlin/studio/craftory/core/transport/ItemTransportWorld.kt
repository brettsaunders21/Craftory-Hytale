@file:Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements", "NestedBlockDepth")

package studio.craftory.core.transport

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.transport.ItemInputAdapter
import studio.craftory.core.api.transport.ItemPipe
import studio.craftory.core.api.transport.ItemTransport
import studio.craftory.core.api.transport.ItemTransportAccess
import studio.craftory.engine.transport.ItemKeyTable
import studio.craftory.engine.transport.ItemTransitGraph
import studio.craftory.engine.transport.NodeActivity
import studio.craftory.engine.transport.NodeRemovalReason
import studio.craftory.engine.transport.PackedPosition
import studio.craftory.engine.transport.TransitItemCodec
import studio.craftory.engine.transport.TransitItemRecord
import studio.craftory.engine.transport.TransitSink

@Suppress("TooManyFunctions")
internal class ItemTransportWorld : Resource<ChunkStore> {
    private val graph = ItemTransitGraph()
    private val itemKeys = ItemKeyTable()
    private val refsByPosition = HashMap<Long, Ref<ChunkStore>>()
    private val activityCache = ChunkActivityCache()
    private var store: Store<ChunkStore>? = null
    private var currentTick = Long.MIN_VALUE
    private val activity =
        NodeActivity { position ->
            val currentStore = store ?: return@NodeActivity false
            val chunkKey = chunkKey(position)
            val cached = activityCache.get(chunkKey)
            if (cached != null) return@NodeActivity cached
            val ref = refsByPosition[position] ?: return@NodeActivity false
            val blockState = currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
            val active = blockState?.let { CraftoryCore.isAreaActive(currentStore, it) } ?: false
            activityCache.put(chunkKey, active)
            active
        }
    private val sink = TransportSink()
    private val dirtyVisitor = DirtyVisitor()
    val access = ItemTransportAccess(this)

    override fun clone(): Resource<ChunkStore> = ItemTransportWorld()

    fun bind(currentStore: Store<ChunkStore>) {
        currentStore.externalData.world.debugAssertInTickingThread()
        store = currentStore
    }

    fun addPipe(
        position: Long,
        ref: Ref<ChunkStore>,
        pipe: ItemPipe,
    ) {
        if (graph.containsNode(position)) return
        refsByPosition[position] = ref
        val persisted =
            TransitItemCodec.decode(pipe.items).map { record ->
                record.itemId?.let { record.copy(itemKey = itemKeys.intern(it)) } ?: record
            }
        graph.addNode(position, pipe.colour.coerceIn(0, 4), persisted)
    }

    fun removePipe(
        position: Long,
        pipe: ItemPipe,
        reason: NodeRemovalReason,
        blockState: BlockModule.BlockStateInfo,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
        if (!graph.containsNode(position)) return
        val persisted = graph.removeNode(position, reason, sink)
        refsByPosition.remove(position)
        if (reason == NodeRemovalReason.UNLOAD) {
            pipe.items =
                TransitItemCodec.encode(
                    persisted.map { record ->
                        record.copy(itemId = itemKeys.key(record.itemKey))
                    },
                )
            blockState.markNeedsSaving(accessor)
        }
    }

    fun tick(
        currentStore: Store<ChunkStore>,
        tick: Long,
    ) {
        bind(currentStore)
        if (currentTick != tick) {
            currentTick = tick
            activityCache.beginTick()
        }
        graph.tick(activity, sink)
        if (tick % SAVE_INTERVAL_TICKS == 0L) {
            graph.drainDirty(dirtyVisitor)
        }
    }

    fun insert(
        position: Long,
        itemId: String,
        quantity: Int,
        colour: Int,
        originPosition: Long,
    ): Boolean {
        val key = itemKeys.intern(itemId)
        return graph.insert(position, key, quantity, colour, originPosition, sink) != ItemTransitGraph.REJECTED_ITEM_ID
    }

    fun isPipe(position: Long): Boolean = graph.containsNode(position)

    fun itemsInTransit(): Int = graph.itemCount

    fun itemsOwnedBy(position: Long): Int = graph.itemsOwnedByCount(position)

    fun progressOf(itemId: Long): Int? = graph.progressOf(itemId)

    val graphItemCount: Int
        get() = graph.itemCount

    fun itemId(index: Int): Long = graph.itemId(index)

    fun itemIdString(index: Int): String? = itemKeys.key(graph.itemKey(index))

    fun itemWorldX(index: Int): Double = graph.itemWorldX(index)

    fun itemWorldY(index: Int): Double = graph.itemWorldY(index)

    fun itemWorldZ(index: Int): Double = graph.itemWorldZ(index)

    fun colourOf(position: Long): Int = graph.colourOf(position)

    fun setColour(
        position: Long,
        colour: Int,
    ): Boolean {
        if (!graph.containsNode(position)) return false
        graph.setColour(position, colour.coerceIn(0, 4))
        val ref = refsByPosition[position] ?: return false
        val currentStore = store ?: return false
        val pipe = currentStore.getComponent(ref, ItemPipe.type) ?: return false
        pipe.colour = colour.coerceIn(0, 4)
        currentStore
            .getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
            ?.markNeedsSaving(currentStore)
        return true
    }

    fun setColour(
        currentStore: Store<ChunkStore>,
        ref: Ref<ChunkStore>,
        colour: Int,
    ): Boolean {
        val pipe = currentStore.getComponent(ref, ItemPipe.type) ?: return false
        val blockState = currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return false
        val position = Vector3i()
        if (!blockState.fillWorldPos(currentStore, position)) return false
        val packed = BlockPos.pack(position.x, position.y, position.z)
        if (!graph.containsNode(packed)) return false
        pipe.colour = colour.coerceIn(0, 4)
        graph.setColour(packed, pipe.colour)
        blockState.markNeedsSaving(currentStore)
        return true
    }

    private fun flushNode(position: Long) {
        val currentStore = store ?: return
        val ref = refsByPosition[position] ?: return
        val pipe = currentStore.getComponent(ref, ItemPipe.type) ?: return
        pipe.items =
            TransitItemCodec.encode(
                graph.itemsOwnedBy(position).map { record ->
                    record.copy(itemId = itemKeys.key(record.itemKey))
                },
            )
        currentStore
            .getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
            ?.markNeedsSaving(currentStore)
    }

    private fun chunkKey(position: Long): Long {
        val x = PackedPosition.x(position) shr 5
        val z = PackedPosition.z(position) shr 5
        return (x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)
    }

    private inner class DirtyVisitor : studio.craftory.engine.transport.DirtyNodeVisitor {
        override fun visit(nodePos: Long) = flushNode(nodePos)
    }

    private inner class TransportSink : TransitSink {
        override fun tryDeliver(
            nodePos: Long,
            itemKey: Int,
            quantity: Int,
            originPos: Long,
        ): Int {
            val currentStore = store ?: return 0
            val itemId = itemKeys.key(itemKey) ?: return 0
            val nodeX = PackedPosition.x(nodePos)
            val nodeY = PackedPosition.y(nodePos)
            val nodeZ = PackedPosition.z(nodePos)
            var delivered = 0
            for (face in FACES) {
                val x = nodeX + face[0]
                val y = nodeY + face[1]
                val z = nodeZ + face[2]
                if (PackedPosition.pack(x, y, z) == originPos) continue
                val ref = blockEntityAt(currentStore, x, y, z) ?: continue
                val containerBlock = currentStore.getComponent(ref, ItemContainerBlock.getComponentType())
                if (containerBlock != null) {
                    delivered += insertIntoContainer(containerBlock.getItemContainer(), itemId, quantity - delivered)
                }
                val bench = currentStore.getComponent(ref, PROCESSING_BENCH_COMPONENT)
                if (bench != null && delivered < quantity) {
                    delivered += insertIntoContainer(bench.inputContainer, itemId, quantity - delivered)
                }
                if (delivered < quantity) {
                    for ((componentType, adapter) in ItemTransport.inputAdapters()) {
                        if (currentStore.getComponent(
                                ref,
                                componentType as ComponentType<ChunkStore, Component<ChunkStore>>,
                            ) !=
                            null
                        ) {
                            delivered +=
                                adapter
                                    .insert(
                                        currentStore,
                                        ref,
                                        itemId,
                                        quantity - delivered,
                                    ).coerceIn(0, quantity - delivered)
                            if (delivered >= quantity) break
                        }
                    }
                }
                if (delivered >= quantity) break
            }
            return delivered
        }

        override fun dropped(
            itemKey: Int,
            quantity: Int,
            worldX: Double,
            worldY: Double,
            worldZ: Double,
        ) {
            val currentStore = store ?: return
            val itemId = itemKeys.key(itemKey) ?: return
            val entityStore = currentStore.externalData.world.entityStore.store
            val holder =
                ItemComponent.generateItemDrop(
                    entityStore,
                    ItemStack(itemId, quantity),
                    Vector3d(worldX, worldY, worldZ),
                    Rotation3f.ZERO,
                    0f,
                    0f,
                    0f,
                ) ?: return
            entityStore.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN)
        }
    }

    private fun blockEntityAt(
        currentStore: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Ref<ChunkStore>? {
        val sectionRef =
            currentStore.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(x, y, z) ?: return null
        return BlockModule.getBlockEntity(currentStore, sectionRef, x, y, z)
    }

    private fun insertIntoContainer(
        container: com.hypixel.hytale.server.core.inventory.container.ItemContainer,
        itemId: String,
        quantity: Int,
    ): Int {
        if (quantity <= 0) return 0
        val transaction =
            container.addItemStack(
                ItemStack(itemId, quantity),
                false,
                false,
                true,
            )
        val remainder = transaction.remainder
        return quantity - (remainder?.quantity ?: 0)
    }

    companion object {
        private const val SAVE_INTERVAL_TICKS = 100L
        private val PROCESSING_BENCH_COMPONENT =
            com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
                .getComponentType()
        private val FACES =
            arrayOf(
                intArrayOf(1, 0, 0),
                intArrayOf(-1, 0, 0),
                intArrayOf(0, 1, 0),
                intArrayOf(0, -1, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 0, -1),
            )
        lateinit var type: ResourceType<ChunkStore, ItemTransportWorld>
    }
}

private class ChunkActivityCache {
    private var keys = LongArray(32)
    private var values = ByteArray(32)
    private var usedSlots = IntArray(32)
    private var usedCount = 0

    fun beginTick() {
        for (index in 0 until usedCount) values[usedSlots[index]] = EMPTY.toByte()
        usedCount = 0
    }

    fun get(key: Long): Boolean? {
        var slot = hash(key) and (keys.size - 1)
        while (values[slot].toInt() != EMPTY) {
            if (keys[slot] == key) return values[slot].toInt() == ACTIVE
            slot = (slot + 1) and (keys.size - 1)
        }
        return null
    }

    fun put(
        key: Long,
        active: Boolean,
    ) {
        if ((usedCount + 1) * 10 >= keys.size * 7) rehash()
        var slot = hash(key) and (keys.size - 1)
        while (values[slot].toInt() != EMPTY) {
            if (keys[slot] == key) {
                values[slot] = if (active) ACTIVE.toByte() else INACTIVE.toByte()
                return
            }
            slot = (slot + 1) and (keys.size - 1)
        }
        keys[slot] = key
        values[slot] = if (active) ACTIVE.toByte() else INACTIVE.toByte()
        usedSlots[usedCount++] = slot
    }

    private fun rehash() {
        val oldKeys = keys
        val oldValues = values
        keys = LongArray(oldKeys.size shl 1)
        values = ByteArray(keys.size)
        usedSlots = IntArray(keys.size)
        usedCount = 0
        for (index in oldKeys.indices) {
            if (oldValues[index].toInt() == EMPTY) continue
            put(oldKeys[index], oldValues[index].toInt() == ACTIVE)
        }
    }

    private fun hash(value: Long): Int {
        var mixed = value
        mixed = (mixed xor (mixed ushr 33)) * -49064778989728563L
        mixed = (mixed xor (mixed ushr 33)) * -4265267296055464877L
        return (mixed xor (mixed ushr 33)).toInt()
    }

    private companion object {
        const val EMPTY = 0
        const val ACTIVE = 1
        const val INACTIVE = 2
    }
}
