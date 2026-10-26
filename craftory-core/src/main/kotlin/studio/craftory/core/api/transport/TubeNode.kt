package studio.craftory.core.api.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class TubeNode(
    kind: String = TUBE,
) : Component<ChunkStore> {
    var kind: String = kind
        set(value) {
            require(value == TUBE || value == STATION) { "Unknown tube node kind: $value" }
            field = value
        }

    init {
        require(kind == TUBE || kind == STATION) { "Unknown tube node kind: $kind" }
    }

    override fun clone(): Component<ChunkStore> = TubeNode(kind)

    companion object {
        const val TUBE = "tube"
        const val STATION = "station"
        lateinit var type: ComponentType<ChunkStore, TubeNode>

        val CODEC: BuilderCodec<TubeNode> =
            BuilderCodec
                .builder(TubeNode::class.java, ::TubeNode)
                .append(
                    KeyedCodec("Kind", Codec.STRING),
                    { node, value -> node.kind = value },
                    { it.kind },
                ).add()
                .build()
    }
}
