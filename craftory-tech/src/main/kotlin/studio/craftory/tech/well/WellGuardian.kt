package studio.craftory.tech.well

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.Inspectable

class SparkjawGuardian(
    var wellId: String = "",
) : Component<EntityStore>,
    Inspectable {
    override fun clone(): Component<EntityStore> = SparkjawGuardian(wellId)

    override fun describe(): List<Pair<String, String>> = listOf("wellId" to wellId)

    companion object {
        lateinit var type: ComponentType<EntityStore, SparkjawGuardian>
        val CODEC: BuilderCodec<SparkjawGuardian> =
            BuilderCodec
                .builder(SparkjawGuardian::class.java, ::SparkjawGuardian)
                .append(KeyedCodec("WellId", Codec.STRING), { state, value -> state.wellId = value }, { it.wellId })
                .add()
                .build()
    }
}

internal class WellGuardianIndex : Resource<EntityStore> {
    val refsByWellId = HashMap<String, Ref<EntityStore>>()

    override fun clone(): Resource<EntityStore> = WellGuardianIndex().also { it.refsByWellId.putAll(refsByWellId) }

    companion object {
        lateinit var type: ResourceType<EntityStore, WellGuardianIndex>
    }
}
