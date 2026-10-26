package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Holder
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.EntityChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.function.BiConsumer

@Suppress("TooManyFunctions")
internal object GnasherSelfTestHolders {
    internal data class Snapshot(
        val nonTicking: Boolean,
        val entityChunkHolders: Int,
        val entityChunkGrit: Int,
        val entitySectionHolders: Int,
        val entitySectionGrit: Int,
    )

    fun clearDrops(
        world: World,
        sectionRef: Ref<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): SelfTestCleanupResult {
        val entityStore = world.entityStore.store
        clearLiveDrops(entityStore, position)
        if (!sectionRef.isValid) {
            return SelfTestCleanupResult(countLiveDrops(entityStore, position), 0, 0)
        }
        val chunkStore = world.chunkStore.store
        clearSectionHolders(chunkStore, sectionRef, position)
        clearChunkHolders(chunkStore, sectionRef, position)
        return SelfTestCleanupResult(
            countLiveDrops(entityStore, position),
            countSectionGritHolders(chunkStore, sectionRef, position),
            countChunkGritHolders(chunkStore, sectionRef, position),
        )
    }

    fun snapshot(
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): Snapshot {
        val nonTicking =
            store.getComponent(sectionRef, ChunkStore.REGISTRY.getNonTickingComponentType()) != null
        val sectionHolders =
            store
                .getComponent(sectionRef, EntitySection.getComponentType())
                ?.getEntityHolders()
                .orEmpty()
        val chunkSection = store.getComponent(sectionRef, ChunkSection.getComponentType())
        val columnRef = chunkSection?.chunkColumnReference
        val columnHolders =
            if (columnRef != null && columnRef.isValid) {
                store
                    .getComponent(columnRef, EntityChunk.getComponentType())
                    ?.let { entityChunk ->
                        (entityChunk.clone() as EntityChunk).takeEntityHolders()?.toList().orEmpty()
                    }.orEmpty()
            } else {
                emptyList()
            }
        return Snapshot(
            nonTicking = nonTicking,
            entityChunkHolders = columnHolders.size,
            entityChunkGrit = gritQuantityInHolders(columnHolders, position),
            entitySectionHolders = sectionHolders.size,
            entitySectionGrit = gritQuantityInHolders(sectionHolders, position),
        )
    }

    fun log(
        run: SelfTestRun.Run,
        label: String,
        snapshot: Snapshot,
    ) {
        run.context.sendMessage(
            Message.raw(
                "selftest: Gnasher holders $label " +
                    "(nonTicking=${snapshot.nonTicking}, " +
                    "EntityChunk=${snapshot.entityChunkHolders}/grit${snapshot.entityChunkGrit}, " +
                    "EntitySection=${snapshot.entitySectionHolders}/grit${snapshot.entitySectionGrit})",
            ),
        )
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun clearLiveDrops(
        entityStore: Store<EntityStore>,
        position: CraftorySelfTest.TestPosition,
    ): Int {
        var removed = 0
        entityStore.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    val item = chunk.getComponent(index, ItemComponent.getComponentType()) ?: continue
                    val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: continue
                    if (
                        GritTable.containsGrit(item.itemStack?.itemId ?: "") &&
                        isNearGnasher(transform.position.x, transform.position.z, position)
                    ) {
                        commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
                        removed++
                    }
                }
            },
        )
        return removed
    }

    private fun countLiveDrops(
        entityStore: Store<EntityStore>,
        position: CraftorySelfTest.TestPosition,
    ): Int {
        var count = 0
        entityStore.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val item = chunk.getComponent(index, ItemComponent.getComponentType())
                    val transform = chunk.getComponent(index, TransformComponent.getComponentType())
                    if (item != null && transform != null) {
                        if (
                            GritTable.containsGrit(item.itemStack?.itemId ?: "") &&
                            isNearGnasher(transform.position.x, transform.position.z, position)
                        ) {
                            count++
                        }
                    }
                }
            },
        )
        return count
    }

    private fun countSectionGritHolders(
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): Int =
        store
            .getComponent(sectionRef, EntitySection.getComponentType())
            ?.getEntityHolders()
            ?.count { isSelfTestGritHolder(it, position) }
            ?: 0

    private fun countChunkGritHolders(
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): Int {
        val chunkSection = store.getComponent(sectionRef, ChunkSection.getComponentType()) ?: return 0
        val columnRef = chunkSection.chunkColumnReference?.takeIf { it.isValid } ?: return 0
        val entityChunk = store.getComponent(columnRef, EntityChunk.getComponentType()) ?: return 0
        val holders = (entityChunk.clone() as EntityChunk).takeEntityHolders().orEmpty()
        return holders.count { isSelfTestGritHolder(it, position) }
    }

    private fun clearSectionHolders(
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): Int {
        val entitySection = store.getComponent(sectionRef, EntitySection.getComponentType()) ?: return 0
        val existing = entitySection.getEntityHolders()
        if (existing.none { isSelfTestGritHolder(it, position) }) return 0
        val holders = entitySection.takeEntityHolders() ?: return 0
        var removed = 0
        holders.forEach { holder ->
            if (isSelfTestGritHolder(holder, position)) {
                removed++
            } else {
                entitySection.addEntityHolder(holder)
            }
        }
        if (removed > 0) entitySection.markNeedsSaving()
        return removed
    }

    private fun clearChunkHolders(
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): Int {
        val chunkSection = store.getComponent(sectionRef, ChunkSection.getComponentType()) ?: return 0
        val columnRef = chunkSection.chunkColumnReference
        if (columnRef == null || !columnRef.isValid) return 0
        val entityChunk = store.getComponent(columnRef, EntityChunk.getComponentType()) ?: return 0
        val copy = entityChunk.clone() as EntityChunk
        val existing = copy.takeEntityHolders()?.toList().orEmpty()
        if (existing.none { isSelfTestGritHolder(it, position) }) return 0
        val holders = entityChunk.takeEntityHolders()?.toList().orEmpty()
        val retained = holders.filterNot { isSelfTestGritHolder(it, position) }
        val removed = holders.size - retained.size
        store.putComponent(columnRef, EntityChunk.getComponentType(), EntityChunk(retained))
        return removed
    }

    private fun gritQuantityInHolders(
        holders: List<Holder<EntityStore>>,
        position: CraftorySelfTest.TestPosition,
    ): Int =
        holders.sumOf { holder ->
            val item = holder.getComponent(ItemComponent.getComponentType()) ?: return@sumOf 0
            val stack = item.itemStack ?: return@sumOf 0
            val transform = holder.getComponent(TransformComponent.getComponentType()) ?: return@sumOf 0
            if (
                GritTable.containsGrit(stack.itemId) &&
                isNearGnasher(transform.position.x, transform.position.z, position)
            ) {
                stack.quantity
            } else {
                0
            }
        }

    private fun isSelfTestGritHolder(
        holder: Holder<EntityStore>,
        position: CraftorySelfTest.TestPosition,
    ): Boolean {
        val item = holder.getComponent(ItemComponent.getComponentType()) ?: return false
        val transform = holder.getComponent(TransformComponent.getComponentType()) ?: return false
        return GritTable.containsGrit(item.itemStack?.itemId ?: "") &&
            isNearGnasher(transform.position.x, transform.position.z, position)
    }

    private fun isNearGnasher(
        x: Double,
        z: Double,
        position: CraftorySelfTest.TestPosition,
    ): Boolean =
        kotlin.math.abs(x - (position.x + 0.5)) < 2.0 &&
            kotlin.math.abs(z - (position.z + 0.5)) < 2.0
}
