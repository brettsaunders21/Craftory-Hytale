package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.machine.jank.BodgeSlots
import studio.craftory.tech.machine.jank.Jank
import studio.craftory.tech.machine.jank.JankTuning
import studio.craftory.tech.machine.jank.isRunning
import studio.craftory.tech.machine.jank.step
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.random.RandomGenerator

private data class WorldMemberId(
    val worldUuid: UUID,
    val memberId: Long,
)

internal class JankSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            Jank.type,
            BodgeSlots.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.AFTER, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        super.tick(dt, chunk, store, commandBuffer)
    }

    @Suppress("CyclomaticComplexMethod")
    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val jank = chunk.getComponent(index, Jank.type) ?: return
        val slots = chunk.getComponent(index, BodgeSlots.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState)) return

        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val ventPosition = MachineVents.position(blockState, position, store) ?: return
        val memberId = BlockPos.pack(position.x, position.y, position.z)
        val worldUuid = store.externalData.world.worldConfig.uuid
        var dirty = false

        if (store.externalData.world.tick % JankTuning.CYCLE_TICKS == 0L) {
            val before = jank.pureState()
            val flags = EnergyConditionFlags.forMember(worldUuid, memberId)
            val result =
                step(
                    before,
                    running =
                        isRunning(
                            member.production,
                            member.demand,
                            member.delivered,
                            flags.overGraded,
                            flags.overloaded,
                        ),
                    overGraded = flags.overGraded,
                    overloaded = flags.overloaded,
                    mods = slots.modifiers(),
                    rng = random,
                )
            jank.apply(result.state)
            dirty = result.state != before
            if (result.fizzled) {
                emitFizzle(ventPosition, position, store.externalData.world.entityStore.store)
                if (slots.modifiers().squawk) {
                    alertCrew(
                        chunk.getComponent(index, MachineOwner.type)?.placerUuid,
                        machineName(member.typeId),
                        position,
                    )
                }
            }
        }

        if (jank.value >= JankTuning.RATTLE_THRESHOLD) {
            if (jank.effectTicks <= 0) {
                emitRattle(
                    ventPosition,
                    position,
                    store.externalData.world.entityStore.store,
                    greySmoke = jank.value >= JankTuning.GREY_SMOKE_FIZZLE_THRESHOLD,
                    muffled = slots.modifiers().muffled,
                )
                jank.effectTicks = EFFECT_INTERVAL_TICKS
            } else {
                jank.effectTicks--
            }
        } else {
            jank.effectTicks = 0
        }
        if (dirty) blockState.markNeedsSaving(commandBuffer)
    }

    private fun emitFizzle(
        position: Vector3d,
        blockPosition: Vector3i,
        entityStore: Store<EntityStore>,
    ) {
        ParticleUtil.spawnParticleEffect(FIZZLE_PARTICLE, position, entityStore)
        playSound(blockPosition, entityStore)
    }

    private fun emitRattle(
        position: Vector3d,
        blockPosition: Vector3i,
        entityStore: Store<EntityStore>,
        greySmoke: Boolean,
        muffled: Boolean,
    ) {
        ParticleUtil.spawnParticleEffect(if (greySmoke) GREY_SMOKE_PARTICLE else RATTLE_PARTICLE, position, entityStore)
        if (!muffled) playSound(blockPosition, entityStore)
    }

    private fun playSound(
        position: Vector3i,
        entityStore: Store<EntityStore>,
    ) {
        val soundIndex = SoundEvent.getAssetMap().getIndex(RATTLE_SOUND)
        if (soundIndex > 0) {
            SoundUtil.playSoundEvent3d(
                soundIndex,
                SoundCategory.SFX,
                position.x + 0.5,
                position.y + 0.5,
                position.z + 0.5,
                entityStore,
            )
        }
    }

    private fun alertCrew(
        owner: java.util.UUID?,
        machineType: String,
        position: Vector3i,
    ) {
        owner ?: return
        val crew = CraftoryCore.crewService().crewOf(owner)
        val recipients = if (crew.solo) setOf(owner) else crew.members
        Universe
            .get()
            .getPlayers()
            .filter { it.uuid in recipients }
            .forEach { player ->
                player.sendMessage(
                    Message
                        .translation("server.craftory.bodge.squawk")
                        .param("machine", machineType)
                        .param("position", "${position.x}, ${position.y}, ${position.z}"),
                )
            }
    }

    companion object EnergyConditionFlags {
        const val FLAG_TTL_NANOS = 2_000_000_000L
        private val overGraded = ConcurrentHashMap<WorldMemberId, Long>()
        private val overloaded = ConcurrentHashMap<WorldMemberId, Long>()

        const val EFFECT_INTERVAL_TICKS = 60
        const val RATTLE_SOUND = "SFX_GoblinLab_Broken_Device"
        const val FIZZLE_PARTICLE = "Cosmetic/Goblin/Cosmetic_Stink"
        const val RATTLE_PARTICLE = "Decorative/Goblin/Goblin_Decorative_Steam"
        const val GREY_SMOKE_PARTICLE = "Deployables/Healing_Totem/Totem_Heal_Smoke"
        val random: RandomGenerator = RandomGenerator.getDefault()

        fun recordOverGraded(
            worldUuid: UUID,
            memberId: Long,
            expiresAt: Long,
        ) {
            overGraded[WorldMemberId(worldUuid, memberId)] = expiresAt
        }

        fun recordOverloaded(
            worldUuid: UUID,
            memberId: Long,
            expiresAt: Long,
        ) {
            overloaded[WorldMemberId(worldUuid, memberId)] = expiresAt
        }

        fun forMember(
            worldUuid: UUID,
            memberId: Long,
        ): Flags {
            val now = System.nanoTime()
            val key = WorldMemberId(worldUuid, memberId)
            val gradeExpires = overGraded[key]
            val overloadExpires = overloaded[key]
            if (gradeExpires != null && gradeExpires <= now) overGraded.remove(key, gradeExpires)
            if (overloadExpires != null && overloadExpires <= now) overloaded.remove(key, overloadExpires)
            return Flags(gradeExpires != null && gradeExpires > now, overloadExpires != null && overloadExpires > now)
        }
    }

    data class Flags(
        val overGraded: Boolean,
        val overloaded: Boolean,
    )
}
