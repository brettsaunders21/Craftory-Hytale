package studio.craftory.core.api.well

import org.bson.BsonDocument
import org.bson.BsonString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class GlimmerwellCodecTest {
    @Test
    fun `codec round trips all fields with and without a crew claim`() {
        val crewId = UUID.randomUUID()
        val source =
            Glimmerwell(
                Temperament.TIDE,
                WellStrength.ROARING,
                WellState.TETHERED,
                "Ingredient_Scraps",
                1234L,
                crewId,
                corrupted = true,
            )
        val encoded = Glimmerwell.CODEC.encode(source) ?: error("codec encode returned null")
        val decoded = Glimmerwell.CODEC.decode(encoded) ?: error("codec decode returned null")
        assertEquals(source.temperament, decoded.temperament)
        assertEquals(source.strength, decoded.strength)
        assertEquals(source.state, decoded.state)
        assertEquals(source.taste, decoded.taste)
        assertEquals(source.birthTick, decoded.birthTick)
        assertEquals(crewId, decoded.claimCrewId)
        assertEquals(source.corrupted, decoded.corrupted)

        val noClaimSource =
            Glimmerwell(
                source.temperament,
                source.strength,
                source.state,
                source.taste,
                source.birthTick,
            )
        val encodedNoClaim = Glimmerwell.CODEC.encode(noClaimSource) ?: error("codec encode returned null")
        val noClaim = Glimmerwell.CODEC.decode(encodedNoClaim) ?: error("codec decode returned null")
        assertNull(noClaim.claimCrewId)
        assertEquals(false, noClaim.corrupted)
    }

    @Test
    fun `codec falls back safely for unknown enum names`() {
        val bson = Glimmerwell.CODEC.encode(Glimmerwell()) as? BsonDocument ?: error("codec returned no document")
        bson.put("Temperament", BsonString("UNKNOWN"))
        bson.put("Strength", BsonString("UNKNOWN"))
        bson.put("State", BsonString("UNKNOWN"))
        val decoded = Glimmerwell.CODEC.decode(bson) ?: error("codec decode returned null")
        assertEquals(Temperament.SPARK, decoded.temperament)
        assertEquals(WellStrength.FLICKER, decoded.strength)
        assertEquals(WellState.WILD, decoded.state)
    }
}
