package studio.craftory.core.api.well

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable
import java.util.UUID

class Glimmerwell(
    var temperament: Temperament = Temperament.SPARK,
    var strength: WellStrength = WellStrength.FLICKER,
    var state: WellState = WellState.WILD,
    var taste: String = "Ingredient_Scraps",
    var birthTick: Long = 0,
    var claimCrewId: UUID? = null,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> =
        Glimmerwell(temperament, strength, state, taste, birthTick, claimCrewId)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "temperament" to temperament.name,
            "strength" to strength.name,
            "state" to state.name,
            "birthTick" to birthTick.toString(),
            "claimCrewId" to claimCrewId?.toString().orEmpty(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, Glimmerwell>

        val CODEC: BuilderCodec<Glimmerwell> =
            BuilderCodec
                .builder(Glimmerwell::class.java, ::Glimmerwell)
                .append(
                    KeyedCodec("Temperament", Codec.STRING),
                    { well, value -> well.temperament = enumOrDefault(value, Temperament.SPARK) },
                    { it.temperament.name },
                ).add()
                .append(
                    KeyedCodec("Strength", Codec.STRING),
                    { well, value -> well.strength = enumOrDefault(value, WellStrength.FLICKER) },
                    { it.strength.name },
                ).add()
                .append(
                    KeyedCodec("State", Codec.STRING),
                    { well, value -> well.state = enumOrDefault(value, WellState.WILD) },
                    { it.state.name },
                ).add()
                .append(
                    KeyedCodec("Taste", Codec.STRING),
                    { well, value -> well.taste = value },
                    { it.taste },
                ).add()
                .append(
                    KeyedCodec("BirthTick", Codec.LONG),
                    { well, value -> well.birthTick = value },
                    { it.birthTick },
                ).add()
                .append(
                    KeyedCodec("ClaimCrewId", Codec.STRING),
                    { well, value -> well.claimCrewId = value.toUuidOrNull() },
                    { it.claimCrewId?.toString().orEmpty() },
                ).add()
                .build()

        private inline fun <reified T : Enum<T>> enumOrDefault(
            value: String,
            default: T,
        ): T = enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default

        private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
    }
}
