package studio.craftory.tech.well

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

class WellAfflictionFollower(
    var wellPosition: Long = Long.MIN_VALUE,
    var kind: String = "",
) : Component<EntityStore> {
    override fun clone(): Component<EntityStore> = WellAfflictionFollower(wellPosition, kind)

    companion object {
        const val MOTH = "Moth"
        const val VOID = "Void"
        const val POACHER = "Poacher"
        const val WALKER = "Walker"

        lateinit var type: ComponentType<EntityStore, WellAfflictionFollower>

        val CODEC: BuilderCodec<WellAfflictionFollower> =
            BuilderCodec
                .builder(WellAfflictionFollower::class.java, ::WellAfflictionFollower)
                .append(
                    KeyedCodec("WellPosition", Codec.LONG),
                    { follower, value -> follower.wellPosition = value },
                    { it.wellPosition },
                ).add()
                .append(
                    KeyedCodec("Kind", Codec.STRING),
                    { follower, value -> follower.kind = value },
                    { it.kind },
                ).add()
                .build()
    }
}
