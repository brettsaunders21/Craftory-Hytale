package studio.craftory.core.transport

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.NonTicking
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.transport.TubeNode

internal class TubeSectionLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(ChunkSection.getComponentType(), BlockComponentSection.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commands: CommandBuffer<ChunkStore>,
    ) {
        scheduleReconciliation(ref, store)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commands: CommandBuffer<ChunkStore>,
    ) = Unit
}

internal class TubeSectionActivation : RefChangeSystem<ChunkStore, NonTicking<ChunkStore>>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(ChunkSection.getComponentType(), BlockComponentSection.getComponentType())

    override fun componentType(): ComponentType<ChunkStore, NonTicking<ChunkStore>> =
        ChunkStore.REGISTRY.nonTickingComponentType

    override fun onComponentAdded(
        ref: Ref<ChunkStore>,
        component: NonTicking<ChunkStore>,
        store: Store<ChunkStore>,
        commands: CommandBuffer<ChunkStore>,
    ) = Unit

    override fun onComponentSet(
        ref: Ref<ChunkStore>,
        old: NonTicking<ChunkStore>?,
        component: NonTicking<ChunkStore>,
        store: Store<ChunkStore>,
        commands: CommandBuffer<ChunkStore>,
    ) = Unit

    override fun onComponentRemoved(
        ref: Ref<ChunkStore>,
        component: NonTicking<ChunkStore>,
        store: Store<ChunkStore>,
        commands: CommandBuffer<ChunkStore>,
    ) = scheduleReconciliation(ref, store)
}

private fun scheduleReconciliation(
    ref: Ref<ChunkStore>,
    store: Store<ChunkStore>,
) {
    store.externalData.world.execute {
        if (ref.isValid) reconcile(ref, store)
    }
}

private fun reconcile(
    ref: Ref<ChunkStore>,
    store: Store<ChunkStore>,
) {
    val section = store.getComponent(ref, ChunkSection.getComponentType()) ?: return
    val blocks = store.getComponent(ref, BlockComponentSection.getComponentType()) ?: return
    store.getResource(TubeWorld.type).reconcileSection(section.x, section.y, section.z) { position ->
        val pos = BlockPos.unpack(position)
        val index = ChunkUtil.indexBlock(pos.x and 31, pos.y and 31, pos.z and 31)
        val block = blocks.getBlockReference(index)?.takeIf { it.isValid }
        val node =
            if (block != null) {
                store.getComponent(block, TubeNode.type)
            } else {
                blocks.getBlockHolder(index)?.getComponent(TubeNode.type)
            }
        node != null
    }
}
