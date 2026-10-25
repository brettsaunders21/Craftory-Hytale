@file:Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")

package studio.craftory.tech.machine

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import org.joml.Vector3ic
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.ItemTransport
import studio.craftory.tech.CraftoryIds

internal class SnatcherClawSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            SnatcherClawState.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, SnatcherClawState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        val tick = world.tick
        val energy = CraftoryCore.energyAccess(world)
        val completedSolveTick = energy.lastSolveTick()
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            state.lastFizzSolveTick = completedSolveTick
            val blockPosition = Vector3i()
            if (blockState.fillWorldPos(commandBuffer, blockPosition)) {
                energy.setDemand(BlockPos.pack(blockPosition.x, blockPosition.y, blockPosition.z), 0)
            }
            return
        }
        if (!CraftoryCore.isEnergyTick(store)) return

        var changed = false
        if (completedSolveTick != Long.MIN_VALUE && completedSolveTick != state.lastFizzSolveTick) {
            val buffered =
                (
                    state.fizzBuffer +
                        member.delivered.coerceAtLeast(
                            0,
                        )
                ).coerceAtMost(SnatcherClawState.FIZZ_PER_ITEM)
            if (buffered != state.fizzBuffer) {
                state.fizzBuffer = buffered
                changed = true
            }
            state.lastFizzSolveTick = completedSolveTick
        }

        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val packedPosition = BlockPos.pack(position.x, position.y, position.z)
        val demand = (SnatcherClawState.FIZZ_PER_ITEM - state.fizzBuffer).coerceAtLeast(0)
        if (member.demand != demand) energy.setDemand(packedPosition, demand)

        if (changed) blockState.markNeedsSaving(commandBuffer)
        if (
            state.fizzBuffer < SnatcherClawState.FIZZ_PER_ITEM ||
            tick - state.lastExtractTick < SnatcherClawState.EXTRACT_INTERVAL_TICKS
        ) {
            return
        }

        val direction = frontDirection(blockState, commandBuffer) ?: return
        val sourcePosition = Vector3i(position.x + direction.x(), position.y, position.z + direction.z())
        val sourceRef =
            BlockModule.getBlockEntity(world, sourcePosition.x, sourcePosition.y, sourcePosition.z) ?: return
        val sourceStore = world.chunkStore.store
        val sourceContainer =
            sourceStore.getComponent(sourceRef, ItemContainerBlock.getComponentType())?.itemContainer
                ?: sourceStore.getComponent(sourceRef, ProcessingBenchBlock.getComponentType())?.outputContainer
                ?: return

        val target = findTargetPipe(world, position, direction) ?: return
        val transport = ItemTransport.of(world)
        var slot = 0.toShort()
        while (slot < sourceContainer.capacity) {
            val item = sourceContainer.getItemStack(slot)
            if (item == null) {
                slot++
                continue
            }
            if (
                item.isEmpty ||
                (state.filterItemId.isNotEmpty() && item.itemId != state.filterItemId)
            ) {
                slot++
                continue
            }
            if (
                transport.insert(
                    target.x,
                    target.y,
                    target.z,
                    item.itemId,
                    1,
                    studio.craftory.core.api.transport.TransportColour
                        .fromValue(state.filterColour),
                    sourcePosition.x,
                    sourcePosition.y,
                    sourcePosition.z,
                )
            ) {
                val transaction = sourceContainer.removeItemStackFromSlot(slot, 1, true, false)
                if (transaction.succeeded()) {
                    state.fizzBuffer -= SnatcherClawState.FIZZ_PER_ITEM
                    state.lastExtractTick = tick
                    changed = true
                    val blockTypeId = BlockType.getAssetMap().getIndex(CraftoryIds.SNATCHER_CLAW)
                    BlockOperations.spawnBlockParticles(
                        world.chunkStore,
                        blockTypeId,
                        BlockType.EMPTY_ID,
                        position.x,
                        position.y,
                        position.z,
                        false,
                    )
                    SoundUtil.playSoundEvent2dToWorld(world, "SFX_GoblinLab_Broken_Device", SoundCategory.SFX)
                }
            }
            break
        }
        if (changed) blockState.markNeedsSaving(commandBuffer)
    }

    private fun frontDirection(
        blockState: BlockModule.BlockStateInfo,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ): org.joml.Vector3ic? {
        val section = accessor.getComponent(blockState.sectionRef, BlockSection.getComponentType()) ?: return null
        return section
            .getRotation(blockState.index)
            .yaw
            .axisDirection
    }

    private fun findTargetPipe(
        world: com.hypixel.hytale.server.core.universe.world.World,
        position: Vector3i,
        front: Vector3ic,
    ): Vector3i? {
        val candidates =
            arrayOf(
                Vector3i(position.x - front.x(), position.y, position.z - front.z()),
                Vector3i(position.x + 1, position.y, position.z),
                Vector3i(position.x - 1, position.y, position.z),
                Vector3i(position.x, position.y + 1, position.z),
                Vector3i(position.x, position.y - 1, position.z),
                Vector3i(position.x, position.y, position.z + 1),
                Vector3i(position.x, position.y, position.z - 1),
            )
        val transport = ItemTransport.of(world)
        for (candidate in candidates) {
            if (transport.isPipe(candidate.x, candidate.y, candidate.z)) return candidate
        }
        return null
    }
}
