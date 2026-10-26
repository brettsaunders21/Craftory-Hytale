package studio.craftory.tech.machine

import org.bson.BsonDocument
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `gate budgets no elapsed time when inactive stalled or empty`() {
        val inactive = OvenFizzAccounting.gate(5, false, false, 10_000, 1.0)
        val stalled = OvenFizzAccounting.gate(5, true, true, 10_000, 1.0)
        val empty = OvenFizzAccounting.gate(0, true, false, 10_000, 1.0)
        val unaffordable = OvenFizzAccounting.gate(1, true, false, 10_000, 0.05)

        assertEquals(0L, inactive.allowedGameElapsedMillis)
        assertEquals(MachineStatus.IDLE, inactive.status)
        assertEquals(0L, stalled.allowedGameElapsedMillis)
        assertEquals(MachineStatus.STALLED, stalled.status)
        assertEquals(0L, empty.allowedGameElapsedMillis)
        assertEquals(MachineStatus.NO_POWER, empty.status)
        assertEquals(0L, unaffordable.allowedGameElapsedMillis)
        assertEquals(MachineStatus.NO_POWER, unaffordable.status)
    }

    @Test
    fun `gate caps elapsed time to the affordable balance`() {
        val gate = OvenFizzAccounting.gate(30, true, false, 10_000, 0.05)

        assertEquals(25L, gate.allowedGameElapsedMillis)
        assertEquals(0.5f, gate.effectiveDtSeconds)
        assertEquals(null, gate.status)
    }

    @Test
    fun `gate caps a huge time jump to the affordable balance`() {
        val gate = OvenFizzAccounting.gate(6, true, false, 3_600_000, 0.05)

        assertEquals(5L, gate.allowedGameElapsedMillis)
        assertEquals(0.1f, gate.effectiveDtSeconds, 0.0001f)
    }

    @Test
    fun `short recipe does not complete while unpowered`() {
        val gate = OvenFizzAccounting.gate(0, true, false, 10_000, 1.0)
        val processing =
            OvenFizzAccounting.process(
                storedFizz = 0,
                previousProgress = 0.099f,
                currentProgress = 0.099f,
                previousRecipeTimeSeconds = 0.1f,
                gateStatus = gate.status,
            )

        assertEquals(MachineStatus.NO_POWER, gate.status)
        assertEquals(0L, gate.allowedGameElapsedMillis)
        assertEquals(0.099f, processing.inputProgress)
        assertEquals(MachineStatus.NO_POWER, processing.status)
    }

    @Test
    fun `recipe change accepts reset and charges new progress`() {
        val processing =
            OvenFizzAccounting.process(5, 2f, 0.1f, 3f, recipeChanged = true)
        assertEquals(0L, processing.storedFizz)
        assertEquals(0.1f, processing.inputProgress)
        assertEquals(MachineStatus.RUNNING, processing.status)
    }

    @Test
    fun `processing cost is clamped to available fizz`() {
        val processing = OvenFizzAccounting.process(1, 0f, 1f, null)
        assertEquals(0L, processing.storedFizz)
        assertEquals(1f, processing.inputProgress)
        assertEquals(MachineStatus.RUNNING, processing.status)
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
