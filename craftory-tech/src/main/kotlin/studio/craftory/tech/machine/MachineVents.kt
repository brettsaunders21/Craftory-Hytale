package studio.craftory.tech.machine

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.tech.CraftoryIds

internal data class MachineVent(
    val nodeName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val modelPath: String? = null,
)

internal object MachineVents {
    val entries =
        mapOf(
            CraftoryIds.BURNER to MachineVent("Smoke-Emitter", 0.5 + 2.14286 / 32, 63.35923 / 32, 0.5 + 1.07143 / 32),
            CraftoryIds.CRANK to MachineVent("Steam-Emitter", 0.5 - 1.45833 / 32, 30.0 / 32, 0.5),
            CraftoryIds.CANISTER to MachineVent("Smoke-Emitter", 0.5 + 0.39181 / 32, 30.32399 / 32, 0.5 - 0.1145 / 32),
            CraftoryIds.FIZZ_DAMPER to MachineVent("Steam-Emitter", 0.5, 30.52631 / 32, 0.5),
            CraftoryIds.GNASHER_PIT to MachineVent("Spark-Emitter", 1.0, 29.8994 / 32, 0.5 - 1.73873 / 32),
            CraftoryIds.GUTTER_BOILER to
                MachineVent(
                    "Steam-Emitter",
                    0.5,
                    31.5 / 32,
                    0.5 + 7.0 / 32,
                    "Blocks/Craftory/Boiler_Whistle_Stack/Boiler_Whistle_Stack.blockymodel",
                ),
            CraftoryIds.JUNK_HEART to
                MachineVent(
                    "Smoke-Emitter",
                    0.5,
                    33.0 / 32,
                    0.5,
                    "Blocks/Craftory/Junk_Heart_Chimney/Junk_Heart_Chimney_Cap.blockymodel",
                ),
            CraftoryIds.RATTLEWHEEL to MachineVent("Spark-Emitter", 0.5, 63.80003 / 32, 0.5),
            CraftoryIds.SCORCH_OVEN to MachineVent("Smoke-Emitter", 1.0, 62.47329 / 32, 0.5 + 15.6051 / 32),
            CraftoryIds.SHROOM_BED to MachineVent("Spore-Emitter", 1.0, 29.29885 / 32, 0.5),
            CraftoryIds.STORM_VANE to MachineVent("Spark-Emitter", 0.5, 63.0 / 32, 0.5),
        )

    fun position(
        blockId: String,
        x: Int,
        y: Int,
        z: Int,
    ): Vector3d = positionForBlockId(blockId, x, y, z)

    internal fun positionForBlockId(
        blockId: String?,
        x: Int,
        y: Int,
        z: Int,
    ): Vector3d {
        val vent = blockId?.let(entries::get) ?: return fallback(x, y, z)
        return Vector3d(x + vent.x, y + vent.y, z + vent.z)
    }

    fun fallback(
        x: Int,
        y: Int,
        z: Int,
    ): Vector3d = Vector3d(x + 0.5, y + 1.0, z + 0.5)

    fun position(
        blockState: BlockModule.BlockStateInfo,
        blockPos: Vector3i,
        store: Store<ChunkStore>,
    ): Vector3d {
        val blockId =
            store.getComponent(blockState.sectionRef, BlockSection.getComponentType())?.let { section ->
                BlockType
                    .getAssetMap()
                    .getAsset(section.get(blockPos.x, blockPos.y, blockPos.z))
                    ?.let(::baseBlockId)
            }
        return positionForBlockId(blockId, blockPos.x, blockPos.y, blockPos.z)
    }
}
