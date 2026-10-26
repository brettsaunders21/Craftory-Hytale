package studio.craftory.tech.well

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.npc.NPCPlugin
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.core.api.well.Temperament
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.BurnerState
import studio.craftory.tech.machine.JunkHeartState
import studio.craftory.tech.machine.MachineOwner

@Suppress("MaxLineLength")
internal class FirstSparkSystem : EntityTickingSystem<ChunkStore>() {
    private val scanCursor = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(MachineOwner.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.AFTER, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    @Suppress("CyclomaticComplexMethod")
    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val world = store.externalData.world
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val positionInfo = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, positionInfo)) return
        val position = Vector3i()
        if (!positionInfo.fillWorldPos(commandBuffer, position)) return
        val positionId = BlockPos.pack(position.x, position.y, position.z)
        if (Math.floorMod(world.tick + Math.floorMod(positionId, CHECK_INTERVAL), CHECK_INTERVAL) != 0L) return

        val owner = chunk.getComponent(index, MachineOwner.type)?.placerUuid ?: return
        val crews = CraftoryCore.crewService()
        val crew = crews.crewOf(owner)
        val machineRunning =
            when (member.typeId) {
                CraftoryIds.BURNER_TYPE -> {
                    chunk.getComponent(index, BurnerState.type)?.burning == true
                }

                CraftoryIds.JUNK_HEART_TYPE -> {
                    chunk.getComponent(index, JunkHeartState.type)?.burning == true &&
                        chunk.getComponent(index, MultiblockController.type)?.state == MultiblockState.FORMED
                }

                else -> {
                    false
                }
            }
        if (!machineRunning || crew.tier < 2 || CraftoryIds.FIRST_SPARK_KNOWLEDGE in crew.knowledge) return
        val nearest = nearestCrystal(store, position.x, position.y, position.z) ?: return
        if (!FirstSparkRules.conditionMet(machineRunning, crew.tier, false, nearest)) return

        crews.grantKnowledge(owner, CraftoryIds.FIRST_SPARK_KNOWLEDGE)
        ParticleUtil.spawnParticleEffect(
            SPARK_PARTICLE,
            Vector3d(position.x + 0.5, position.y + 1.0, position.z + 0.5),
            world.entityStore.store,
        )
        val soundIndex = SoundEvent.getAssetMap().getIndex(SPARK_SOUND)
        if (soundIndex > 0) {
            SoundUtil.playSoundEvent3d(
                soundIndex,
                SoundCategory.SFX,
                position.x + 0.5,
                position.y + 0.5,
                position.z + 0.5,
                world.entityStore.store,
            )
        }
        if (CraftoryIds.VESSA_MET_KNOWLEDGE !in crews.crewOf(owner).knowledge) {
            val spawned = VessaSpawner.spawn(world.entityStore.store, position.x, position.y + 1, position.z)
            if (spawned != null) crews.grantKnowledge(owner, CraftoryIds.VESSA_MET_KNOWLEDGE)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private fun nearestCrystal(
        store: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Long? {
        var nearest = Long.MAX_VALUE
        val world = store.externalData.world
        for (dx in -SCAN_RADIUS..SCAN_RADIUS) {
            for (dy in -SCAN_RADIUS..SCAN_RADIUS) {
                for (dz in -SCAN_RADIUS..SCAN_RADIUS) {
                    val distance = dx.toLong() * dx + dy.toLong() * dy + dz.toLong() * dz
                    if (distance > MAX_DISTANCE_SQUARED || distance >= nearest) continue
                    scanCursor.set(x + dx, y + dy, z + dz)
                    val sectionRef =
                        world.chunkStore.getChunkSectionReferenceAtBlock(scanCursor.x, scanCursor.y, scanCursor.z)
                            ?: continue
                    val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: continue
                    val blockId = section.get(scanCursor.x and 31, scanCursor.y and 31, scanCursor.z and 31)
                    val name = BlockType.getAssetMap().getAsset(blockId)?.id ?: continue
                    if (name == CraftoryIds.CYAN_CRYSTAL_BLOCK || name == CraftoryIds.VOID_CRYSTAL_BLOCK) {
                        nearest = distance
                    }
                }
            }
        }
        return nearest.takeIf { it != Long.MAX_VALUE }
    }

    private companion object {
        const val CHECK_INTERVAL = 40L
        const val SCAN_RADIUS = 8
        const val MAX_DISTANCE_SQUARED = 64L
        const val SPARK_PARTICLE = "Impact_Spark"
        const val SPARK_SOUND = "SFX_Interact"
    }
}

internal object VessaSpawner {
    fun spawn(
        store: Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Any? =
        NPCPlugin.get().spawnNPC(
            store,
            CraftoryIds.VESSA_ROLE,
            null,
            Vector3d(x + 0.5, y + 0.5, z + 0.5),
            Rotation3f.IDENTITY,
        )
}
