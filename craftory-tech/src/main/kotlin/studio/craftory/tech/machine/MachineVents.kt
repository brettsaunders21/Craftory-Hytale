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
            CraftoryIds.BURNER to MachineVent("Smoke-Emitter", 18.0 / 32, 35.0 / 32, 17.0 / 32),
            CraftoryIds.CRANK to MachineVent("Steam-Emitter", 0.5, 23.0 / 32, 0.5),
            CraftoryIds.CANISTER to MachineVent("Smoke-Emitter", 0.5, 28.0 / 32, 0.5),
            CraftoryIds.FIZZ_DAMPER to MachineVent("Steam-Emitter", 0.5, 29.0 / 32, 0.5),
            CraftoryIds.GNASHER_PIT to MachineVent("Spark-Emitter", 0.5, 23.0 / 32, 0.5),
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
            CraftoryIds.RATTLEWHEEL to MachineVent("Spark-Emitter", 0.5, 29.0 / 32, 0.5),
            CraftoryIds.SCORCH_OVEN to MachineVent("Smoke-Emitter", 0.5, 38.0 / 32, 0.5),
            CraftoryIds.SHROOM_BED to MachineVent("Spore-Emitter", 0.5, 30.0 / 32, 0.5),
            CraftoryIds.STORM_VANE to MachineVent("Spark-Emitter", 0.5, 31.5 / 32, 0.5),
        )

    fun position(
        blockId: String,
        x: Int,
        y: Int,
        z: Int,
    ): Vector3d? =
        entries[blockId]?.let { vent ->
            Vector3d(x + vent.x, y + vent.y, z + vent.z)
        }

    fun position(
        blockState: BlockModule.BlockStateInfo,
        blockPos: Vector3i,
        store: Store<ChunkStore>,
    ): Vector3d? {
        val section =
            store.getComponent(blockState.sectionRef, BlockSection.getComponentType())
                ?: return null
        val type =
            BlockType.getAssetMap().getAsset(section.get(blockPos.x, blockPos.y, blockPos.z))
                ?: return null
        return position(baseBlockId(type), blockPos.x, blockPos.y, blockPos.z)
    }
}
