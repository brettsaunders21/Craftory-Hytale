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
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.tech.CraftoryIds
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
        Query.and(HurlCannonChargeState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, HurlCannonChargeState.type) ?: return
        if (state.launchTick == Long.MIN_VALUE || store.externalData.world.tick < state.launchTick) return
        val world = store.externalData.world
        val rider = state.rider?.takeIf { it.isValid }
        val player = rider?.let { world.entityStore.store.getComponent(it, PlayerRef.getComponentType()) }
        if (rider != null && player != null) {
            val target = Vector3i(state.targetX, state.targetY, state.targetZ)
            if (blockId(world, target.x, target.y, target.z) == CraftoryIds.CATCH_NET) {
                val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType())
                val source = Vector3i()
                if (blockState?.fillWorldPos(commandBuffer, source) == true) {
                    launchHurlCannonArc(world, source, target, rider, player)
                }
            }
        }
        state.clear()
    }
}

internal fun beginHurlCannonCharge(
    world: com.hypixel.hytale.server.core.universe.world.World,
    position: Vector3i,
    rider: Ref<EntityStore>?,
    target: Vector3i,
): Boolean {
    val blockRef = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return false
    val store = world.chunkStore.store
    val charge = store.getComponent(blockRef, HurlCannonChargeState.type) ?: return false
    val animation = store.getComponent(blockRef, TimedBlockAnimation.type) ?: return false
    val launchTick = world.tick + HURL_CANNON_CHARGE_TICKS
    charge.launchTick = launchTick
    charge.targetX = target.x
    charge.targetY = target.y
    charge.targetZ = target.z
    charge.rider = rider
    animation.start(world.tick, HURL_CANNON_CHARGE_TICKS)
    setBlockAnimationState(world, position.x, position.y, position.z, animation.animatedState)
    return true
}

internal const val HURL_CANNON_CHARGE_TICKS = 5L

internal fun launchHurlCannonArc(
    world: com.hypixel.hytale.server.core.universe.world.World,
    source: Vector3i,
    target: Vector3i,
    rider: Ref<EntityStore>,
    player: PlayerRef,
) {
    val result =
        HurlRides.startArc(
            world,
            studio.craftory.core.api.transport
                .HurlPoint(source.x + 0.5, source.y + 1.0, source.z + 0.5),
            studio.craftory.core.api.transport
                .HurlPoint(target.x + 0.5, target.y + 1.0, target.z + 0.5),
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
                    com.hypixel.hytale.server.core.Message
                        .translation("server.craftory.wrench.foreign-crew"),
                )

                else -> "no-net"
            }
        player.sendMessage(
            com.hypixel.hytale.server.core.Message
                .translation("server.craftory.hurl.$key"),
        )
    }
}
