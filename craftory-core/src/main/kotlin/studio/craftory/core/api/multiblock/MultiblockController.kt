package studio.craftory.core.api.multiblock

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

enum class MultiblockState {
    ASSEMBLING,
    READY_TO_START,
    FORMED,
    DAMAGED,
}

enum class MultiblockQuality(
    val multiplier: Double,
) {
    NONE(0.0),
    WOBBLY(0.9),
    STURDY(1.0),
    MASTERWORK(1.15),
}

/**
 * Persisted controller state. Structure validity is never stored: [missing] is a transient
 * cache rebuilt from world blocks on load and on every footprint place/break.
 */
class MultiblockController(
    var templateId: String = "",
    var rotation: Int = -1,
    var state: MultiblockState = MultiblockState.ASSEMBLING,
    var quality: MultiblockQuality = MultiblockQuality.NONE,
) : Component<ChunkStore>,
    Inspectable {
    @Transient
    var missing: Set<Long>? = null
        internal set

    @Transient
    var readySinceTick: Long = 0L

    @Transient
    var nextValidationTick: Long = 0L

    val isFormed: Boolean get() = state == MultiblockState.FORMED

    override fun clone(): Component<ChunkStore> =
        MultiblockController(templateId, rotation, state, quality).also {
            it.missing = missing
            it.readySinceTick = readySinceTick
        }

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "templateId" to templateId,
            "rotation" to rotation.toString(),
            "state" to state.name,
            "quality" to quality.name,
            "missing" to (missing?.size?.toString() ?: "unknown"),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, MultiblockController>

        val CODEC: BuilderCodec<MultiblockController> =
            BuilderCodec
                .builder(MultiblockController::class.java, ::MultiblockController)
                .append(
                    KeyedCodec("TemplateId", Codec.STRING),
                    { controller, value -> controller.templateId = value },
                    { it.templateId },
                ).add()
                .append(
                    KeyedCodec("Rotation", Codec.INTEGER),
                    { controller, value -> controller.rotation = value },
                    { it.rotation },
                ).add()
                .append(
                    KeyedCodec("State", Codec.STRING),
                    { controller, value ->
                        controller.state =
                            MultiblockState.entries.firstOrNull { it.name == value } ?: MultiblockState.ASSEMBLING
                    },
                    { it.state.name },
                ).add()
                .append(
                    KeyedCodec("Quality", Codec.STRING),
                    { controller, value ->
                        controller.quality =
                            MultiblockQuality.entries.firstOrNull { it.name == value } ?: MultiblockQuality.NONE
                    },
                    { it.quality.name },
                ).add()
                .build()
    }
}
