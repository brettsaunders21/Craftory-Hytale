package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class OvenPower(
    var accumulatedFizz: Long = 0,
    var lastFizzSolveTick: Long = Long.MIN_VALUE,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = OvenPower(accumulatedFizz, lastFizzSolveTick)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "accumulatedFizz" to accumulatedFizz.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, OvenPower>

        val CODEC: BuilderCodec<OvenPower> =
            BuilderCodec
                .builder(OvenPower::class.java, ::OvenPower)
                .append(
                    KeyedCodec("AccumulatedFizz", Codec.LONG),
                    { power, value -> power.accumulatedFizz = value },
                    { it.accumulatedFizz },
                ).add()
                .build()
    }
}
