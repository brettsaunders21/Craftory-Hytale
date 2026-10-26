package studio.craftory.tech.machine

import org.bson.BsonDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OvenPowerMathTest {
    @Test
    fun `delivered fizz is only counted once for a completed solve tick`() {
        val charged =
            OvenFizzAccounting.update(
                storedFizz = 100,
                lastFizzSolveTick = 8,
                completedSolveTick = 9,
                deliveredFizz = 30,
                energyUse = 1.0,
            )
        val duplicate =
            OvenFizzAccounting.update(
                storedFizz = charged.storedFizz,
                lastFizzSolveTick = charged.lastFizzSolveTick,
                completedSolveTick = 9,
                deliveredFizz = 30,
                energyUse = 1.0,
            )

        assertEquals(130L, duplicate.storedFizz)
        assertEquals(9L, duplicate.lastFizzSolveTick)
    }

    @Test
    fun `charging demand is independent of recipes and stored fizz is capped`() {
        val charged =
            OvenFizzAccounting.update(
                storedFizz = 570,
                lastFizzSolveTick = 100,
                completedSolveTick = 101,
                deliveredFizz = 90,
                energyUse = 1.0,
            )

        assertEquals(OvenFizzAccounting.FIZZ_PER_SOLVE_TICK, charged.demand)
        assertEquals(OvenPower.CAPACITY, charged.storedFizz)
        assertEquals(101L, charged.lastFizzSolveTick)

        val full =
            OvenFizzAccounting.update(
                storedFizz = charged.storedFizz,
                lastFizzSolveTick = charged.lastFizzSolveTick,
                completedSolveTick = 102,
                deliveredFizz = 30,
                energyUse = 1.0,
            )

        assertEquals(0L, full.demand)
        assertEquals(OvenPower.CAPACITY, full.storedFizz)
    }

    @Test
    fun `processing drains fizz when progress advances`() {
        val processing =
            OvenFizzAccounting.process(
                storedFizz = 300,
                previousProgress = 0f,
                currentProgress = 1f,
                previousRecipeTimeSeconds = null,
            )

        assertEquals(240L, processing.storedFizz)
        assertEquals(1f, processing.inputProgress)
        assertEquals(MachineStatus.RUNNING, processing.status)
        assertTrue(processing.advanced)
    }

    @Test
    fun `processing freezes at the previous progress when fizz is insufficient`() {
        val processing =
            OvenFizzAccounting.process(
                storedFizz = 59,
                previousProgress = 1f,
                currentProgress = 2f,
                previousRecipeTimeSeconds = 3f,
            )

        assertEquals(59L, processing.storedFizz)
        assertEquals(1f, processing.inputProgress)
        assertEquals(MachineStatus.NO_POWER, processing.status)
        assertFalse(processing.advanced)
    }

    @Test
    fun `processing charges the remaining time when recipe progress wraps`() {
        val processing =
            OvenFizzAccounting.process(
                storedFizz = 300,
                previousProgress = 1.9f,
                currentProgress = 0.2f,
                previousRecipeTimeSeconds = 2f,
            )

        assertEquals(0.3f, processing.deltaSeconds, 0.0001f)
        assertEquals(282L, processing.storedFizz)
        assertEquals(0.2f, processing.inputProgress)
        assertEquals(MachineStatus.RUNNING, processing.status)
    }

    @Test
    fun `old accumulated fizz save values are clamped to capacitor capacity`() {
        val encoded = BsonDocument.parse("""{"AccumulatedFizz":${OvenPower.CAPACITY + 1}}""")
        val decoded = OvenPower.CODEC.decode(encoded) ?: error("oven power codec returned null")

        assertEquals(OvenPower.CAPACITY, decoded.storedFizz)
    }
}
