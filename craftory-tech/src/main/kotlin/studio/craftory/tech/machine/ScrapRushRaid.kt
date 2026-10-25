package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class ScrapRushRaid(
    var npcIds: Array<String> = emptyArray(),
    var adjacentTicks: Int = 0,
    var crewId: String = "",
    var startedAt: Long = 0L,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = ScrapRushRaid(npcIds.copyOf(), adjacentTicks, crewId, startedAt)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "goblins" to npcIds.joinToString(),
            "adjacentTicks" to adjacentTicks.toString(),
            "crewId" to crewId,
            "startedAt" to startedAt.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, ScrapRushRaid>

        val CODEC: BuilderCodec<ScrapRushRaid> =
            BuilderCodec
                .builder(ScrapRushRaid::class.java, ::ScrapRushRaid)
                .append(KeyedCodec("NpcIds", Codec.STRING_ARRAY), { raid, value -> raid.npcIds = value }, { it.npcIds })
                .add()
                .append(
                    KeyedCodec("AdjacentTicks", Codec.INTEGER),
                    { raid, value -> raid.adjacentTicks = value },
                    { it.adjacentTicks },
                ).add()
                .append(KeyedCodec("CrewId", Codec.STRING), { raid, value -> raid.crewId = value }, { it.crewId })
                .add()
                .append(
                    KeyedCodec("StartedAt", Codec.LONG),
                    { raid, value -> raid.startedAt = value },
                    { it.startedAt },
                ).add()
                .build()
    }
}
