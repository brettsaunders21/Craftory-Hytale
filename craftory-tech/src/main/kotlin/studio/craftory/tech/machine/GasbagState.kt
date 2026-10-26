package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class GasbagState(
    var leaking: Boolean = false,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = GasbagState(leaking)

    companion object {
        lateinit var type: ComponentType<ChunkStore, GasbagState>
        val CODEC: BuilderCodec<GasbagState> =
            BuilderCodec
                .builder(GasbagState::class.java, ::GasbagState)
                .append(KeyedCodec("Leaking", Codec.BOOLEAN), { state, value -> state.leaking = value }, { it.leaking })
                .add()
                .build()
    }
}

object GasbagRules {
    fun leakAmount(
        capacity: Long,
        seconds: Int = 1,
    ): Long = capacity.coerceAtLeast(0) / 100 * seconds.coerceAtLeast(0)

    fun patched(state: GasbagState) {
        state.leaking = false
    }

    fun patch(state: GasbagState): Boolean {
        if (!state.leaking) return false
        patched(state)
        return true
    }
}
