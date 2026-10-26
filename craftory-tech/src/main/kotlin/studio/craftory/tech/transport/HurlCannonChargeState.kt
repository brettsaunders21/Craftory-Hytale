package studio.craftory.tech.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.transport.HurlPoint
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.MachineOwner
import studio.craftory.tech.machine.TimedBlockAnimation
import studio.craftory.tech.machine.setBlockAnimationState

class HurlCannonChargeState(
    var launchTick: Long = Long.MIN_VALUE,
    var targetX: Int = 0,
    var targetY: Int = 0,
    var targetZ: Int = 0,
) : Component<ChunkStore> {
    @Transient
    var rider: Ref<EntityStore>? = null

    override fun clone(): Component<ChunkStore> =
        HurlCannonChargeState(launchTick, targetX, targetY, targetZ).also { it.rider = rider }

    fun clear() {
        launchTick = Long.MIN_VALUE
        rider = null
    }

    companion object {
        lateinit var type: ComponentType<ChunkStore, HurlCannonChargeState>

        val CODEC: BuilderCodec<HurlCannonChargeState> =
            BuilderCodec
                .builder(HurlCannonChargeState::class.java, ::HurlCannonChargeState)
                .append(
                    KeyedCodec("LaunchTick", Codec.LONG),
                    { state, value -> state.launchTick = value },
                    { it.launchTick },
                ).add()
                .append(KeyedCodec("TargetX", Codec.INTEGER), { state, value -> state.targetX = value }, { it.targetX })
                .add()
                .append(KeyedCodec("TargetY", Codec.INTEGER), { state, value -> state.targetY = value }, { it.targetY })
                .add()
                .append(KeyedCodec("TargetZ", Codec.INTEGER), { state, value -> state.targetZ = value }, { it.targetZ })
                .add()
                .build()
    }
}

internal class HurlCannonChargeSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            HurlCannonChargeState.type,
            TimedBlockAnimation.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, HurlCannonChargeState.type) ?: return
        val world = store.externalData.world
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val animation = chunk.getComponent(index, TimedBlockAnimation.type) ?: return
        val pending = state.launchTick != Long.MIN_VALUE
        val decision =
            CannonChargePolicy.decide(
                CraftoryCore.isAreaActive(store, blockState),
                pending,
                world.tick >= state.launchTick,
            )
        if (decision == CannonChargeDecision.CLEAR) {
            state.clear()
            val source = Vector3i()
            if (blockState.fillWorldPos(commandBuffer, source)) {
                setBlockAnimationState(world, source.x, source.y, source.z, animation.idleState)
            }
            return
        }
        if (decision != CannonChargeDecision.LAUNCH) return
        val rider = state.rider?.takeIf { it.isValid }
        val player = rider?.let { world.entityStore.store.getComponent(it, PlayerRef.getComponentType()) }
        if (rider != null && player != null) {
            val target = Vector3i(state.targetX, state.targetY, state.targetZ)
            if (blockId(world, target.x, target.y, target.z) == CraftoryIds.CATCH_NET) {
                val source = Vector3i()
                if (blockState.fillWorldPos(commandBuffer, source)) {
                    launchHurlCannonArc(world, source, target, rider, player)
                }
            }
        }
        state.clear()
    }
}

internal enum class CannonChargeDecision {
    IDLE,
    WAIT,
    LAUNCH,
    CLEAR,
}

internal object CannonChargePolicy {
    fun decide(
        areaActive: Boolean,
        pending: Boolean,
        due: Boolean,
    ): CannonChargeDecision =
        when {
            !pending -> CannonChargeDecision.IDLE
            !areaActive -> CannonChargeDecision.CLEAR
            due -> CannonChargeDecision.LAUNCH
            else -> CannonChargeDecision.WAIT
        }
}

internal fun beginHurlCannonCharge(
    world: World,
    position: Vector3i,
    rider: Ref<EntityStore>?,
    target: Vector3i,
): HurlStartFailure? {
    val blockRef =
        BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return HurlStartFailure.SPAWN_FAILED
    val store = world.chunkStore.store
    val charge = store.getComponent(blockRef, HurlCannonChargeState.type) ?: return HurlStartFailure.SPAWN_FAILED
    val animation = store.getComponent(blockRef, TimedBlockAnimation.type) ?: return HurlStartFailure.SPAWN_FAILED
    if (rider != null) {
        val player =
            world.entityStore.store.getComponent(rider, PlayerRef.getComponentType())
                ?: return HurlStartFailure.FORBIDDEN
        val owner = store.getComponent(blockRef, MachineOwner.type)?.placerUuid
        if (!StationPermissions.mayVisit(owner, CraftoryCore.crewService().crewOf(player.uuid).members)) {
            return HurlStartFailure.FORBIDDEN
        }
        hurlArcPreflightFailure(world.entityStore.store, rider)?.let { return it }
    }
    val launchTick = world.tick + HURL_CANNON_CHARGE_TICKS
    charge.launchTick = launchTick
    charge.targetX = target.x
    charge.targetY = target.y
    charge.targetZ = target.z
    charge.rider = rider
    animation.start(world.tick, HURL_CANNON_CHARGE_TICKS)
    setBlockAnimationState(world, position.x, position.y, position.z, animation.animatedState)
    return null
}

internal const val HURL_CANNON_CHARGE_TICKS = 5L

internal fun launchHurlCannonArc(
    world: World,
    source: Vector3i,
    target: Vector3i,
    rider: Ref<EntityStore>,
    player: PlayerRef,
) {
    val result =
        HurlRides.startArc(
            world,
            HurlPoint(source.x + 0.5, source.y + 1.0, source.z + 0.5),
            HurlPoint(target.x + 0.5, target.y + 1.0, target.z + 0.5),
            rider,
            player.uuid,
        )
    result.failure?.let {
        val key =
            when (it) {
                HurlStartFailure.ALREADY_RIDING -> "already-riding"

                HurlStartFailure.SPAWN_FAILED -> "spawn-failed"

                HurlStartFailure.PENDING -> "warming-up"

                HurlStartFailure.JAR_CARRIER -> "jar-carrier"

                HurlStartFailure.FORBIDDEN -> return player.sendMessage(
                    Message.translation("server.craftory.wrench.foreign-crew"),
                )

                else -> "no-net"
            }
        player.sendMessage(Message.translation("server.craftory.hurl.$key"))
    }
}
