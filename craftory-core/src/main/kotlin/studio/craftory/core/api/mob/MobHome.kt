package studio.craftory.core.api.mob

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos

data class MobHome(
    var roleId: String = "",
    var hasHome: Boolean = false,
    var homeX: Int = 0,
    var homeY: Int = 0,
    var homeZ: Int = 0,
    var leashRadius: Double = 12.0,
) : Component<EntityStore> {
    val home: BlockPos? get() = if (hasHome) BlockPos(homeX, homeY, homeZ) else null

    override fun clone(): Component<EntityStore> = copy()

    companion object {
        lateinit var type: ComponentType<EntityStore, MobHome>

        val CODEC: BuilderCodec<MobHome> =
            BuilderCodec
                .builder(MobHome::class.java, ::MobHome)
                .append(
                    KeyedCodec("RoleId", Codec.STRING),
                    { component, value -> component.roleId = value },
                    { it.roleId },
                ).add()
                .append(
                    KeyedCodec("HasHome", Codec.BOOLEAN),
                    { component, value -> component.hasHome = value },
                    { it.hasHome },
                ).add()
                .append(
                    KeyedCodec("HomeX", Codec.INTEGER),
                    { component, value -> component.homeX = value },
                    { it.homeX },
                ).add()
                .append(
                    KeyedCodec("HomeY", Codec.INTEGER),
                    { component, value -> component.homeY = value },
                    { it.homeY },
                ).add()
                .append(
                    KeyedCodec("HomeZ", Codec.INTEGER),
                    { component, value -> component.homeZ = value },
                    { it.homeZ },
                ).add()
                .append(
                    KeyedCodec("LeashRadius", Codec.DOUBLE),
                    { component, value -> component.leashRadius = value },
                    { it.leashRadius },
                ).add()
                .build()
    }
}
