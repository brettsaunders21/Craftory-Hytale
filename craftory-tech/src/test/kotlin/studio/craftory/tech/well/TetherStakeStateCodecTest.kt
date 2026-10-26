package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TetherStakeStateCodecTest {
    @Test
    fun `codec round trips tether reference leaning state and placer`() {
        val source = TetherStakeState("well-position", true, "placer-uuid")
        val encoded = TetherStakeState.CODEC.encode(source) ?: error("codec encode returned null")
        val decoded = TetherStakeState.CODEC.decode(encoded) ?: error("codec decode returned null")

        assertEquals(source.wellId, decoded.wellId)
        assertEquals(source.leaning, decoded.leaning)
        assertEquals(source.placerUuid, decoded.placerUuid)
    }
}
