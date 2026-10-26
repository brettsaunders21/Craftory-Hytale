package studio.craftory.tech.machine

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.protocol.packets.interface_.BlockChange
import com.hypixel.hytale.protocol.packets.interface_.FluidChange
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.entity.component.PrefabPreview
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.core.api.multiblock.WorldPart
import java.util.function.BiConsumer

/**
 * Ghost frame through the documented `PrefabPreview` entity component: one non-persisted preview entity per
 * assembling controller, listing its missing parts relative to the controller. The entity tracker sends it to every
 * nearby client. Refs live in [AssemblyViewIndex] as a derived cache; the world blocks stay the source of truth.
 */
internal object GhostPreviews {
    private const val ALL_LAYERS = Int.MAX_VALUE
    private const val DEFAULT_BIOME_TINT = 6004264
    private const val DEFAULT_WATER_TINT = 668501

    /** Spawns or refreshes the preview for [controllerPos]. Must run on the world thread outside a store tick. */
    fun show(
        world: World,
        controllerPos: BlockPos,
        missing: List<WorldPart>,
    ) {
        val index = world.chunkStore.store.getResource(AssemblyViewIndex.type)
        val signature = missing.map(WorldPart::position).toSet()
        val current = index.preview(controllerPos)
        if (current != null && current.ref.isValid && current.parts == signature) return
        hide(world, controllerPos)
        val blocks = missing.mapNotNull { part -> blockChange(controllerPos, part) }.toTypedArray()
        if (blocks.isEmpty()) return
        val entities = world.entityStore.store
        val holder = entities.registry.newHolder()
        holder.addComponent(NetworkId.getComponentType(), NetworkId(entities.externalData.takeNextNetworkId()))
        holder.addComponent(
            TransformComponent.getComponentType(),
            TransformComponent(
                Vector3d(controllerPos.x.toDouble(), controllerPos.y.toDouble(), controllerPos.z.toDouble()),
                Rotation3f(),
            ),
        )
        holder.addComponent(
            PrefabPreview.getComponentType(),
            PrefabPreview(blocks, arrayOf<FluidChange>(), ALL_LAYERS, DEFAULT_BIOME_TINT, DEFAULT_WATER_TINT),
        )
        holder.ensureComponent(UUIDComponent.getComponentType())
        holder.ensureComponent(EntityStore.REGISTRY.nonSerializedComponentType)
        val ref: Ref<EntityStore> = entities.addEntity(holder, AddReason.SPAWN) ?: return
        index.putPreview(controllerPos, AssemblyViewIndex.Preview(ref, signature))
    }

    /** Refreshes an existing preview after a hammer hit or recheck; never spawns one (that needs a nearby viewer). */
    fun sync(
        world: World,
        controllerRef: Ref<ChunkStore>,
    ) {
        val store = world.chunkStore.store
        val position = CraftoryCore.multiblocks().controllerPosition(store, controllerRef) ?: return
        if (!store.getResource(AssemblyViewIndex.type).hasPreview(position)) return
        val controller = store.getComponent(controllerRef, MultiblockController.type)
        if (controller?.state != MultiblockState.ASSEMBLING || !CraftoryCore.ghostMode().showsPreview) {
            return hide(world, position)
        }
        show(world, position, CraftoryCore.multiblocks().missingParts(store, controllerRef))
    }

    fun hide(
        world: World,
        controllerPos: BlockPos,
    ) {
        val preview =
            world.chunkStore.store
                .getResource(AssemblyViewIndex.type)
                .removePreview(controllerPos) ?: return
        if (preview.ref.isValid) world.entityStore.store.removeEntity(preview.ref, RemoveReason.REMOVE)
    }

    /** Number of missing-part entries on the live preview for [controllerPos], or null when none is spawned. */
    fun entries(
        world: World,
        controllerPos: BlockPos,
    ): Int? {
        val preview =
            world.chunkStore.store
                .getResource(AssemblyViewIndex.type)
                .preview(controllerPos) ?: return null
        if (!preview.ref.isValid) return null
        return world.entityStore.store
            .getComponent(preview.ref, PrefabPreview.getComponentType())
            ?.blocks
            ?.size
    }

    /** Live preview entities in [world], for selftest residue checks. */
    fun liveCount(world: World): Int {
        var count = 0
        world.entityStore.store.forEachChunk(
            PrefabPreview.getComponentType(),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ -> count += chunk.size() },
        )
        return count
    }

    private fun blockChange(
        controllerPos: BlockPos,
        part: WorldPart,
    ): BlockChange? {
        val type = BlockType.fromString(part.part.placedBlockId) ?: return null
        return BlockChange(
            part.position.x - controllerPos.x,
            part.position.y - controllerPos.y,
            part.position.z - controllerPos.z,
            BlockType.getAssetMap().getIndex(type.id),
            0,
        )
    }
}
