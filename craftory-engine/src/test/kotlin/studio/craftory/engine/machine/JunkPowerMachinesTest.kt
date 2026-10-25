package studio.craftory.engine.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JunkPowerMachinesTest {
    @Test
    fun `crank clamps pulse rate and restores decaying spin`() {
        val crank = CrankMachine()

        assertTrue(crank.pulse(100))
        assertFalse(crank.pulse(109))
        assertTrue(crank.pulse(110))
        assertEquals(80, crank.spinTicks)
        assertEquals(CrankMachine.FIZZ_PER_SOLVE_TICK, crank.productionPerSolveTick())

        val restored = CrankMachine(initialSpinTicks = crank.spinTicks)
        restored.advanceTo(200)
        assertEquals(20, restored.productionPerSolveTick())
        restored.advanceTo(280)
        assertEquals(0, restored.spinTicks)
        assertEquals(0, restored.productionPerSolveTick())
    }

    @Test
    fun `burner uses data fuel and requires a kick before burning`() {
        val burner =
            BurnerMachine(
                fuelDefinitions =
                    listOf(
                        FuelDefinition("mushroom", 200),
                        FuelDefinition("scraps", 40),
                    ),
            )

        assertEquals(0, burner.addFuel("unknown"))
        assertEquals(40, burner.addFuel("scraps"))
        assertEquals(0, burner.productionPerSolveTick())
        assertTrue(burner.kick())
        assertFalse(burner.kick())
        assertEquals(BurnerMachine.FIZZ_PER_SOLVE_TICK, burner.productionPerSolveTick())

        burner.advanceTo(10)
        burner.advanceTo(30)
        assertEquals(20, burner.burnTicks)
        assertEquals(40, burner.productionPerSolveTick())

        burner.advanceTo(50)
        assertEquals(0, burner.burnTicks)
        assertFalse(burner.isBurning)
        assertEquals(0, burner.productionPerSolveTick())
    }

    @Test
    fun `burner fuel can be added while burning`() {
        val burner = BurnerMachine(fuelDefinitions = listOf(FuelDefinition("mushroom", 200)))

        burner.addFuel("mushroom")
        burner.kick()
        burner.advanceTo(10)
        burner.addFuel("mushroom")
        burner.advanceTo(20)

        assertEquals(390, burner.burnTicks)
        assertTrue(burner.isBurning)
    }
}
