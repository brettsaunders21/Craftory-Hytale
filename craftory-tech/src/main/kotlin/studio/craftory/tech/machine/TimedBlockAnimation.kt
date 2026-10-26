package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i

class TimedBlockAnimation(
    var idleState: String = "Off",
    var animatedState: String = "",
    var untilTick: Long = Long.MIN_VALUE,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = TimedBlockAnimation(idleState, animatedState, untilTick)

    fun start(
        tick: Long,
        duration: Long = HOLD_TICKS,
    ) {
        untilTick = BlockAnimationTiming.holdUntil(tick, duration)
    }

    fun stateAt(tick: Long): String = BlockAnimationTiming.stateAt(tick, untilTick, idleState, animatedState)

    companion object {
        lateinit var type: ComponentType<ChunkStore, TimedBlockAnimation>

        val CODEC: BuilderCodec<TimedBlockAnimation> =
            BuilderCodec
                .builder(TimedBlockAnimation::class.java, ::TimedBlockAnimation)
                .append(
                    KeyedCodec("IdleState", Codec.STRING),
                    { component, value -> component.idleState = value },
                    { it.idleState },
                ).add()
                .append(
                    KeyedCodec("AnimatedState", Codec.STRING),
                    { component, value -> component.animatedState = value },
                    { it.animatedState },
                ).add()
                .append(
                    KeyedCodec("UntilTick", Codec.LONG),
                    { component, value -> component.untilTick = value },
                    { it.untilTick },
                ).add()
                .build()

        const val HOLD_TICKS = 10L
    }
}

internal object BlockAnimationTiming {
    fun holdUntil(
        tick: Long,
        duration: Long,
    ): Long = tick + duration

    fun stateAt(
        tick: Long,
        untilTick: Long,
        idleState: String,
        animatedState: String,
    ): String = if (tick < untilTick) animatedState else idleState
}

internal class TimedBlockAnimationSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(TimedBlockAnimation.type, BlockModule.BlockStateInfo.getComponentType())

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val animation = chunk.getComponent(index, TimedBlockAnimation.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (setBlockState(
                store.externalData.world,
                blockState,
                commandBuffer,
                animation.stateAt(store.externalData.world.tick),
            )
        ) {
            blockState.markNeedsSaving(commandBuffer)
        }
    }
}

internal fun triggerTimedBlockAnimation(
    world: World,
    x: Int,
    y: Int,
    z: Int,
): Boolean {
    val blockRef = BlockModule.getBlockEntity(world, x, y, z) ?: return false
    val store = world.chunkStore.store
    val animation = store.getComponent(blockRef, TimedBlockAnimation.type) ?: return false
    animation.start(world.tick)
    return setBlockAnimationState(world, x, y, z, animation.animatedState)
}

internal fun setBlockAnimationState(
    world: World,
    x: Int,
    y: Int,
    z: Int,
    state: String,
): Boolean {
    val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(x, y, z) ?: return false
    val store = world.chunkStore.store
    val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return false
    val blockType = BlockType.getAssetMap().getAsset(section.get(x and 31, y and 31, z and 31)) ?: return false
    if (blockType.getStateForBlock(blockType) == state || blockType.getBlockForState(state) == null) return false
    BlockOperations.setBlockInteractionState(world.chunkStore, sectionRef, x, y, z, blockType, state, false)
    return true
}
