package studio.craftory.tech.well

import studio.craftory.core.api.BlockPos

internal object WellSelfTestFixtures {
    fun fixturePositions(): List<BlockPos> {
        val stake = WellSelfTest.STAKED_POS
        val damper = WellSelfTest.SMOTHERED_POS
        return listOf(
            WellSelfTest.CAUGHT_POS,
            WellSelfTest.FORCED_POS,
            WellSelfTest.BLOCK_REMOVED_POS,
            WellSelfTest.VANE_POS,
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
}
