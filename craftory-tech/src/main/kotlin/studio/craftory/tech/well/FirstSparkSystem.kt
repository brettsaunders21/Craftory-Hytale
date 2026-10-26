package studio.craftory.tech.well

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.NPCPlugin
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.crew.CrewService
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
    private val cursor = Vector3i()

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
        val positionInfo = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, positionInfo)) return
        if (!positionInfo.fillWorldPos(commandBuffer, cursor)) return
        val positionId = BlockPos.pack(cursor.x, cursor.y, cursor.z)
        if (Math.floorMod(world.tick + Math.floorMod(positionId, CHECK_INTERVAL), CHECK_INTERVAL) != 0L) return
        FirstSpark.tryTrigger(world, store, chunk.getReferenceTo(index), cursor, CraftoryCore.crewService())
    }

    private companion object {
        const val CHECK_INTERVAL = 40L
    }
}

@Suppress("MaxLineLength")
internal object FirstSpark {
    private const val SCAN_RADIUS = 8
    private const val MAX_DISTANCE_SQUARED = 64L
    private const val SPARK_PARTICLE = "Impact_Spark"
    private const val SPARK_SOUND = "SFX_Interact"
    private val pendingVessaSpawns =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<java.util.UUID>()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun tryTrigger(
        world: com.hypixel.hytale.server.core.universe.world.World,
        store: Store<ChunkStore>,
        ref: Ref<ChunkStore>,
        position: Vector3i,
        crews: CrewService,
        shouldContinue: () -> Boolean = { true },
        onVessaSpawned: (Ref<EntityStore>) -> Unit = {},
    ): Boolean {
        val member = store.getComponent(ref, EnergyMember.type) ?: return false
        val owner = store.getComponent(ref, MachineOwner.type)?.placerUuid ?: return false
        val machineX = position.x
        val machineY = position.y
        val machineZ = position.z
        val machineRunning =
            when (member.typeId) {
                CraftoryIds.BURNER_TYPE -> {
                    store.getComponent(ref, BurnerState.type)?.burning == true
                }

                CraftoryIds.JUNK_HEART_TYPE -> {
                    store.getComponent(ref, JunkHeartState.type)?.burning == true &&
                        store.getComponent(ref, MultiblockController.type)?.state == MultiblockState.FORMED
                }

                else -> {
                    false
                }
            }
        if (!machineRunning) return false
        val crew = crews.crewOf(owner)
        val alreadyKnown = CraftoryIds.FIRST_SPARK_KNOWLEDGE in crew.knowledge
        val vessaMet = CraftoryIds.VESSA_MET_KNOWLEDGE in crew.knowledge
        if (crew.tier < 2 || (alreadyKnown && vessaMet)) return false
        val nearest = nearestCrystal(store, machineX, machineY, machineZ, position) ?: return false
        if (!FirstSparkRules.conditionMet(machineRunning, crew.tier, false, nearest.second)) return false
        val action = FirstSparkRules.action(alreadyKnown, vessaMet, conditionMet = true)
        if (action == FirstSparkRules.Action.NONE) return false
        if (!pendingVessaSpawns.add(owner)) return false
        if (action == FirstSparkRules.Action.GRANT_AND_SPAWN) {
            crews.grantKnowledge(owner, CraftoryIds.FIRST_SPARK_KNOWLEDGE)
        }
        val arcPosition = Vector3d()
        world.execute {
            try {
                if (!shouldContinue()) return@execute
                if (action == FirstSparkRules.Action.GRANT_AND_SPAWN) {
                    repeat(6) { index ->
                        val amount = (index + 1) / 7.0
                        arcPosition.set(
                            nearest.first.x + 0.5 + (machineX - nearest.first.x) * amount,
                            nearest.first.y + 0.5 + (machineY - nearest.first.y) * amount,
                            nearest.first.z + 0.5 + (machineZ - nearest.first.z) * amount,
                        )
                        ParticleUtil.spawnParticleEffect(SPARK_PARTICLE, arcPosition, world.entityStore.store)
                    }
                    val soundIndex = SoundEvent.getAssetMap().getIndex(SPARK_SOUND)
                    if (soundIndex > 0) {
                        SoundUtil.playSoundEvent3d(
                            soundIndex,
                            SoundCategory.SFX,
                            machineX + 0.5,
                            machineY + 0.5,
                            machineZ + 0.5,
                            world.entityStore.store,
                        )
                    }
                }
                if (!vessaMet) {
                    val airY = (machineY + 1..machineY + 3).firstOrNull { isAir(store, machineX, it, machineZ) }
                    if (airY != null) {
                        val spawned = VessaSpawner.spawn(world.entityStore.store, machineX, airY, machineZ)
                        if (spawned != null) {
                            (spawned as? Ref<EntityStore>)?.let(onVessaSpawned)
                            crews.grantKnowledge(owner, CraftoryIds.VESSA_MET_KNOWLEDGE)
                        }
                    }
                }
            } finally {
                pendingVessaSpawns.remove(owner)
            }
        }
        return action == FirstSparkRules.Action.GRANT_AND_SPAWN
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private fun nearestCrystal(
        store: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
        cursor: Vector3i,
    ): Pair<BlockPos, Long>? {
        var nearest = Long.MAX_VALUE
        var nearestX = 0
        var nearestY = 0
        var nearestZ = 0
        val world = store.externalData.world
        for (dx in -SCAN_RADIUS..SCAN_RADIUS) {
            for (dy in -SCAN_RADIUS..SCAN_RADIUS) {
                for (dz in -SCAN_RADIUS..SCAN_RADIUS) {
                    val distance = dx.toLong() * dx + dy.toLong() * dy + dz.toLong() * dz
                    if (distance > MAX_DISTANCE_SQUARED || distance >= nearest) continue
                    cursor.set(x + dx, y + dy, z + dz)
                    val sectionRef =
                        world.chunkStore.getChunkSectionReferenceAtBlock(cursor.x, cursor.y, cursor.z)
                            ?: continue
                    val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: continue
                    val blockId = section.get(cursor.x and 31, cursor.y and 31, cursor.z and 31)
                    val name = BlockType.getAssetMap().getAsset(blockId)?.id ?: continue
                    if (name == CraftoryIds.CYAN_CRYSTAL_BLOCK || name == CraftoryIds.VOID_CRYSTAL_BLOCK) {
                        nearest = distance
                        nearestX = cursor.x
                        nearestY = cursor.y
                        nearestZ = cursor.z
                    }
                }
            }
        }
        return if (nearest == Long.MAX_VALUE) null else BlockPos(nearestX, nearestY, nearestZ) to nearest
    }

    private fun isAir(
        store: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val sectionRef =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(x, y, z) ?: return false
        val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return false
        return section.get(x and 31, y and 31, z and 31) == BlockType.EMPTY_ID
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
