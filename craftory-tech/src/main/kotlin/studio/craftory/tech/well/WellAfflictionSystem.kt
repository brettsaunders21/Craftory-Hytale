package studio.craftory.tech.well

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellState
import studio.craftory.tech.CraftoryIds

internal class WellAfflictionSystem : EntityTickingSystem<ChunkStore>() {
    private val cursor = Vector3i()

    internal fun startForSelfTest(
        world: World,
        position: BlockPos,
        kind: String,
        count: Int = 1,
    ) {
        spawnFollowers(world, position, kind, count)
        val blockRef = wellRef(world, position) ?: return
        val store = world.chunkStore.store
        val affliction = store.getComponent(blockRef, WellAffliction.type) ?: return
        val trackedCount =
            when (kind) {
                WellAfflictionFollower.MOTH -> affliction.mothIds.size
                WellAfflictionFollower.VOID -> affliction.voidIds.size
                else -> affliction.raidNpcIds.size
            }
        if (trackedCount < count) {
            val followers = spawnFollowersForSelfTest(world, position, kind, count - trackedCount)
            if (followers.isNotEmpty()) recordSpawnedFollowers(world, position, kind, followers)
        }
    }

    internal fun startRaidForSelfTest(
        world: World,
        position: BlockPos,
    ) {
        spawnRaid(world, position)
        val blockRef = wellRef(world, position) ?: return
        val store = world.chunkStore.store
        val affliction = store.getComponent(blockRef, WellAffliction.type) ?: return
        if (affliction.walkerId.isNotEmpty()) return
        val poachers = spawnFollowersForSelfTest(world, position, WellAfflictionFollower.POACHER, 2)
        val walkers = spawnFollowersForSelfTest(world, position, WellAfflictionFollower.WALKER, 1)
        if (poachers.size != 2 || walkers.size != 1) {
            despawnFollowers(world, (poachers + walkers).toTypedArray())
            return
        }
        affliction.raidNpcIds = (poachers + walkers).toTypedArray()
        affliction.walkerId = walkers.single()
        affliction.raidStartedTick = world.tick
        store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
    }

    internal fun advanceFeedForSelfTest(
        world: World,
        position: BlockPos,
        well: Glimmerwell,
        seconds: Int,
    ) {
        feedSeconds[WellKey(world.worldConfig.uuid, position.pack())] = seconds
        updateBeamsAndFeed(world, position, well)
    }

    override fun getQuery(): Query<ChunkStore> =
        Query.and(Glimmerwell.type, BlockModule.BlockStateInfo.getComponentType())

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
        val world = store.externalData.world
        if (world.tick % TICKS_PER_SECOND != 0L) return
        val well = chunk.getComponent(index, Glimmerwell.type) ?: return
        val ref = chunk.getReferenceTo(index)
        var createdAffliction = false
        val affliction =
            chunk.getComponent(index, WellAffliction.type)
                ?: WellAffliction().also {
                    store.putComponent(ref, WellAffliction.type, it)
                    createdAffliction = true
                }
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!blockState.fillWorldPos(commandBuffer, cursor)) return
        val position = BlockPos(cursor.x, cursor.y, cursor.z)
        if (WellSelfTestExclusions.isWell(position) || !CraftoryCore.isAreaActive(store, blockState)) return
        if (createdAffliction) blockState.markNeedsSaving(commandBuffer)
        if (well.state != WellState.SEATED && well.state != WellState.TAPPED) return
        cleanseIfReady(world, position, well, affliction, commandBuffer, blockState)
        val ownerCrewOnline = well.claimCrewId?.let { hasOnlineCrewMember(it, world) } == true
        processRaid(world, position, well, affliction, ownerCrewOnline)
        if (!ownerCrewOnline) return
        updateBeamsAndFeed(world, position, well)
        spawnOnSchedule(world, position)
    }

    companion object {
        internal fun onFollowerDeath(
            world: World,
            uuid: java.util.UUID,
            position: BlockPos,
            kind: String,
        ) {
            feedBeams.remove(uuid)?.takeIf { it.isValid }?.let {
                world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
            }
            clearSiphonBeam(world, uuid)
            if (kind == WellAfflictionFollower.WALKER) {
                siphonSeconds.remove(WellKey(world.worldConfig.uuid, position.pack()))
            }
        }

        internal fun dropRaidRewards(
            world: World,
            position: BlockPos,
        ) {
            listOf(CraftoryIds.GOB_JAR to 1, "Ingredient_Scraps" to 4).forEach { (itemId, quantity) ->
                val holder =
                    ItemComponent.generateItemDrop(
                        world.entityStore.store,
                        ItemStack(itemId, quantity),
                        Vector3d(position.x + 0.5, position.y + 1.0, position.z + 0.5),
                        Rotation3f.IDENTITY,
                        0f,
                        0f,
                        0f,
                    ) ?: return@forEach
                world.entityStore.store.addEntity(holder, AddReason.SPAWN)
            }
        }
    }
}
