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
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
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
    private val position = Vector3i()
    private val sourcePosition = Vector3i()
    private val targetPosition = Vector3i()

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
            if (blockState.fillWorldPos(commandBuffer, position)) {
                energy.setDemand(BlockPos.pack(position.x, position.y, position.z), 0)
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

        val direction = snatcherClawFrontDirection(blockState, commandBuffer) ?: return
        sourcePosition.set(position.x + direction.x(), position.y, position.z + direction.z())
        val sourceRef =
            BlockModule.getBlockEntity(world, sourcePosition.x, sourcePosition.y, sourcePosition.z) ?: return
        val sourceStore = world.chunkStore.store
        val sourceContainer =
            sourceStore.getComponent(sourceRef, ItemContainerBlock.getComponentType())?.getItemContainer()
                ?: sourceStore.getComponent(sourceRef, ProcessingBenchBlock.getComponentType())?.outputContainer
                ?: return

        if (!findTargetPipe(world, position, direction, targetPosition)) return
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
            if (!transport.canInsert(targetPosition.x, targetPosition.y, targetPosition.z)) {
                slot++
                continue
            }
            val removal = sourceContainer.removeItemStackFromSlot(slot, 1, true, false)
            if (removal.succeeded()) {
                val removedItem = removal.output ?: item.withQuantity(1) ?: ItemStack(item.itemId, 1)
                val inserted =
                    transport.insert(
                        targetPosition.x,
                        targetPosition.y,
                        targetPosition.z,
                        removedItem.itemId,
                        1,
                        studio.craftory.core.api.transport.TransportColour
                            .fromValue(state.filterColour),
                        sourcePosition.x,
                        sourcePosition.y,
                        sourcePosition.z,
                    )
                if (inserted) {
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
                    val soundIndex = SoundEvent.getAssetMap().getIndex(CLAW_SOUND_EVENT)
                    SoundUtil.playSoundEvent3d(
                        soundIndex,
                        SoundCategory.SFX,
                        position.x + 0.5,
                        position.y + 0.5,
                        position.z + 0.5,
                        world.entityStore.store,
                    )
                } else {
                    sourceContainer.setItemStackForSlot(slot, removal.slotBefore ?: item)
                }
            }
            break
        }
        if (changed) blockState.markNeedsSaving(commandBuffer)
    }

    private fun findTargetPipe(
        world: com.hypixel.hytale.server.core.universe.world.World,
        position: Vector3i,
        front: Vector3ic,
        target: Vector3i,
    ): Boolean {
        val transport = ItemTransport.of(world)
        var x = position.x - front.x()
        var y = position.y
        var z = position.z - front.z()
        if (transport.isPipe(x, y, z)) {
            target.set(x, y, z)
            return true
        }
        if (transport.isPipe(position.x + 1, position.y, position.z)) {
            target.set(position.x + 1, position.y, position.z)
            return true
        }
        if (transport.isPipe(position.x - 1, position.y, position.z)) {
            target.set(position.x - 1, position.y, position.z)
            return true
        }
        if (transport.isPipe(position.x, position.y + 1, position.z)) {
            target.set(position.x, position.y + 1, position.z)
            return true
        }
        if (transport.isPipe(position.x, position.y - 1, position.z)) {
            target.set(position.x, position.y - 1, position.z)
            return true
        }
        x = position.x
        y = position.y
        z = position.z + 1
        if (transport.isPipe(x, y, z)) {
            target.set(x, y, z)
            return true
        }
        z = position.z - 1
        if (transport.isPipe(x, y, z)) {
            target.set(x, y, z)
            return true
        }
        return false
    }

    companion object {
        private const val CLAW_SOUND_EVENT = "SFX_GoblinLab_Broken_Device"
    }
}

internal fun snatcherClawFrontDirection(
    blockState: BlockModule.BlockStateInfo,
    accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
): Vector3ic? {
    val section = accessor.getComponent(blockState.sectionRef, BlockSection.getComponentType()) ?: return null
    val blockType = BlockType.fromString(CraftoryIds.SNATCHER_CLAW) ?: return null
    val yaw = section.getRotation(blockState.index).yaw.add(blockType.rotationYawPlacementOffset)
    return yaw.axisDirection
}
