package studio.craftory.tech.well

import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn
import studio.craftory.core.api.BlockPos

internal object WellSelfTestFixtures {
    fun fixturePositions(world: World): List<BlockPos> {
        val stake = WellSelfTest.STAKED_POS
        val damper = WellSelfTest.SMOTHERED_POS
        return listOf(
            WellSelfTest.CAUGHT_POS,
            WellSelfTest.FORCED_POS,
            WellSelfTest.BLOCK_REMOVED_POS,
            vanePosition(world),
            stake,
            BlockPos(stake.x + 4, stake.y, stake.z),
            BlockPos(stake.x + 4, stake.y, stake.z + 3),
            BlockPos(stake.x - 4, stake.y, stake.z),
            damper,
            BlockPos(damper.x + 2, damper.y, damper.z),
            BlockPos(damper.x - 2, damper.y, damper.z),
            BlockPos(damper.x, damper.y, damper.z + 2),
            WellSelfTest.CALMED_POS,
        )
    }

    fun vanePosition(world: World): BlockPos {
        val configured = WellSelfTest.VANE_POS
        val heightmap =
            world.chunkStore.getChunkComponent(
                ChunkUtil.indexChunkFromBlock(configured.x, configured.z),
                HeightmapColumn.getComponentType(),
            ) ?: error("Storm Vane test heightmap is unavailable")
        val surfaceY = heightmap.getCollisionHeight(configured.x and 31, configured.z and 31)
        check(surfaceY != HeightmapColumn.NO_HEIGHT) { "Storm Vane test surface is unavailable" }
        return BlockPos(configured.x, surfaceY + 1, configured.z)
    }
}
