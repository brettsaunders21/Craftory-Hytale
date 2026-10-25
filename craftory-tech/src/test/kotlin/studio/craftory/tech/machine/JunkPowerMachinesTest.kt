package studio.craftory.tech.machine

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

        assertFalse(burner.kick(0))
        assertEquals(0, burner.addFuel("unknown"))
        assertEquals(40, burner.addFuel("scraps"))
        assertEquals(0, burner.productionPerSolveTick())
        assertTrue(burner.kick(0))
        assertFalse(burner.kick(0))
        assertEquals(BurnerMachine.FIZZ_PER_SOLVE_TICK, burner.productionPerSolveTick())

        burner.advanceTo(10)
        burner.advanceTo(30)
        assertEquals(10, burner.burnTicks)
        assertEquals(40, burner.productionPerSolveTick())

        burner.advanceTo(50)
        assertEquals(0, burner.burnTicks)
        assertFalse(burner.isBurning)
        assertEquals(0, burner.productionPerSolveTick())
    }

    @Test
    fun `burner kick consumes a hit only when ignition succeeds`() {
        val fueled = BurnerMachine(fuelDefinitions = listOf(FuelDefinition("mushroom", 200)))
        fueled.addFuel("mushroom")
        assertTrue(fueled.kick(0))

        val unfueled = BurnerMachine(fuelDefinitions = listOf(FuelDefinition("mushroom", 200)))
        assertFalse(unfueled.kick(0))

        assertFalse(fueled.kick(0))
    }

    @Test
    fun `burner fuel can be added while burning`() {
        val burner = BurnerMachine(fuelDefinitions = listOf(FuelDefinition("mushroom", 200)))

        burner.addFuel("mushroom")
        burner.kick(0)
        burner.advanceTo(10)
        burner.addFuel("mushroom")
        burner.advanceTo(20)

        assertEquals(380, burner.burnTicks)
        assertTrue(burner.isBurning)
    }

    @Test
    fun `idle crank remains clean while advancing`() {
        val crank = CrankMachine()

        assertFalse(crank.advanceTo(100))
        assertFalse(crank.advanceTo(110))
        assertEquals(0, crank.spinTicks)
    }

    @Test
    fun `fresh crank spin starts aging at its pulse tick`() {
        val crank = CrankMachine()

        assertFalse(crank.advanceTo(100))
        assertTrue(crank.pulse(103))
        assertTrue(crank.advanceTo(104))
        assertEquals(39, crank.spinTicks)
    }

    @Test
    fun `fresh burner fuel starts aging at its kick tick`() {
        val burner = BurnerMachine(fuelDefinitions = listOf(FuelDefinition("mushroom", 200)))
        burner.addFuel("mushroom")

        assertFalse(burner.advanceTo(100))
        assertTrue(burner.kick(103))
        assertTrue(burner.advanceTo(104))
        assertEquals(199, burner.burnTicks)
    }
}
