package studio.craftory.core.api.multiblock

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.multiblock.MultiblockIndex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

fun interface MultiblockListener {
    fun handle(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        controller: MultiblockController,
    )
}

@Suppress("TooManyFunctions")
class MultiblockRegistry internal constructor() {
    private val templates = ConcurrentHashMap<String, MultiblockTemplate>()
    private val formedListeners = CopyOnWriteArrayList<MultiblockListener>()
    private val damagedListeners = CopyOnWriteArrayList<MultiblockListener>()

    fun register(template: MultiblockTemplate): MultiblockTemplate {
        val (width, depth) = MultiblockGeometry.footprint(template)
        require(MultiblockGeometry.fitsFootprint(template)) {
            "Multiblock ${template.id} footprint ${width}x$depth exceeds " +
                "${MultiblockGeometry.MAX_FOOTPRINT}x${MultiblockGeometry.MAX_FOOTPRINT}"
        }
        check(templates.putIfAbsent(template.id, template) == null) { "Multiblock already registered: ${template.id}" }
        return template
    }

    fun unregister(id: String): Boolean = templates.remove(id) != null

    fun template(id: String): MultiblockTemplate? = templates[id]

    fun templates(): List<MultiblockTemplate> = templates.values.sortedBy(MultiblockTemplate::id)

    fun onFormed(listener: MultiblockListener): AutoCloseable = subscribe(formedListeners, listener)

    fun onDamaged(listener: MultiblockListener): AutoCloseable = subscribe(damagedListeners, listener)

    fun controllerPosition(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ): BlockPos? {
        val blockState = store.getComponent(controllerRef, BlockModule.BlockStateInfo.getComponentType()) ?: return null
        val position = Vector3i()
        if (!blockState.fillWorldPos(store, position)) return null
        return BlockPos(position.x, position.y, position.z)
    }

    /** Every part of the controller's template in world space, in hammer order. */
    fun worldParts(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ): List<WorldPart> {
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return emptyList()
        val template = template(controller.templateId) ?: return emptyList()
        val position = controllerPosition(store, controllerRef) ?: return emptyList()
        return MultiblockGeometry.worldParts(template, position, controller.rotation.coerceAtLeast(0))
    }

    /** Missing parts recomputed from world blocks, in hammer order. Refreshes the transient cache. */
    fun missingParts(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ): List<WorldPart> = scan(store, controllerRef)?.missing ?: emptyList()

    fun isFormed(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ): Boolean {
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return false
        if (controller.state != MultiblockState.FORMED) return false
        val scan = scan(store, controllerRef) ?: return false
        return scan.complete && scan.missing.isEmpty()
    }

    /** Controller whose footprint contains [position], from the derived per-world index. */
    fun controllerAt(
        store: Store<ChunkStore>,
        position: BlockPos,
    ): Ref<ChunkStore>? {
        val controller = store.getResource(MultiblockIndex.type).controllerFor(position.pack()) ?: return null
        val world = store.externalData.world
        val at = BlockPos.unpack(controller)
        return BlockModule.getBlockEntity(world, at.x, at.y, at.z)
    }

    /**
     * Re-checks one controller against the world and applies the state transition:
     * ASSEMBLING <-> READY_TO_START while building, FORMED <-> DAMAGED once kicked.
     * Returns null when a footprint section is not loaded, so nothing changes.
     */
    fun recheck(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ): MultiblockState? {
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return null
        val scan = scan(store, controllerRef) ?: return null
        if (!scan.complete) return null
        val complete = scan.missing.isEmpty()
        val previous = controller.state
        val next =
            when (previous) {
                MultiblockState.ASSEMBLING -> if (complete) MultiblockState.READY_TO_START else previous
                MultiblockState.READY_TO_START -> if (complete) previous else MultiblockState.ASSEMBLING
                MultiblockState.FORMED -> if (complete) previous else MultiblockState.DAMAGED
                MultiblockState.DAMAGED -> if (complete) MultiblockState.FORMED else previous
            }
        if (next != previous) {
            controller.state = next
            if (next == MultiblockState.READY_TO_START) {
                controller.readySinceTick = store.externalData.world.tick
            }
            markNeedsSaving(store, controllerRef)
            when (next) {
                MultiblockState.FORMED -> notify(formedListeners, store, controllerRef, controller)
                MultiblockState.DAMAGED -> notify(damagedListeners, store, controllerRef, controller)
                else -> Unit
            }
        }
        return next
    }

    /** Finishes a READY_TO_START structure with the kick-start [quality]. */
    fun completeKickStart(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        quality: MultiblockQuality,
    ): Boolean {
        require(quality != MultiblockQuality.NONE) { "Kick-start quality must be set" }
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return false
        if (recheck(store, controllerRef) != MultiblockState.READY_TO_START) return false
        controller.state = MultiblockState.FORMED
        controller.quality = quality
        markNeedsSaving(store, controllerRef)
        notify(formedListeners, store, controllerRef, controller)
        return true
    }

    internal fun matches(
        part: TemplatePart,
        blockType: BlockType?,
    ): Boolean {
        val id = blockType?.id ?: return false
        if (id in part.blockIds) return true
        return part.blockIds.any { BlockType.fromString(it)?.getStateForBlock(id) != null }
    }

    internal fun blockTypeAt(
        store: Store<ChunkStore>,
        position: BlockPos,
    ): Result<BlockType?> {
        val world = store.externalData.world
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?: return Result.failure(IllegalStateException("section not loaded"))
        val section =
            store.getComponent(sectionRef, BlockSection.getComponentType())
                ?: return Result.failure(IllegalStateException("section not loaded"))
        val blockId = section.get(position.x, position.y, position.z)
        return Result.success(BlockType.getAssetMap().getAsset(blockId))
    }

    private class Scan(
        val missing: List<WorldPart>,
        val complete: Boolean,
    )

    private fun scan(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ): Scan? {
        val controller = store.getComponent(controllerRef, MultiblockController.type) ?: return null
        val parts = worldParts(store, controllerRef)
        if (parts.isEmpty()) return null
        var complete = true
        val missing =
            parts.filter { part ->
                val blockType = blockTypeAt(store, part.position)
                if (blockType.isFailure) complete = false
                !matches(part.part, blockType.getOrNull())
            }
        if (complete) controller.missing = missing.map { it.position.pack() }.toSet()
        return Scan(missing, complete)
    }

    private fun markNeedsSaving(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
    ) {
        store.getComponent(controllerRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
    }

    private fun notify(
        listeners: List<MultiblockListener>,
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        controller: MultiblockController,
    ) {
        listeners.forEach { it.handle(store, controllerRef, controller) }
    }

    private fun subscribe(
        listeners: MutableList<MultiblockListener>,
        listener: MultiblockListener,
    ): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }
}
