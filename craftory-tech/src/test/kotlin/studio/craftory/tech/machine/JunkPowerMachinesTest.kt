package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.energy.EnergyMember

class JunkPowerMachinesTest {
    @Test
    fun `crank pulse rate and spin decay are tick based`() {
        val crank = CrankState()
        assertTrue(CrankMachine.pulse(crank, 100))
        assertFalse(CrankMachine.pulse(crank, 109))
        assertTrue(CrankMachine.pulse(crank, 110))
        assertEquals(80, crank.spinTicks)
        assertEquals(CrankMachine.FIZZ_PER_SOLVE_TICK, CrankMachine.productionPerSolveTick(crank))

        CrankMachine.advanceTo(crank, 150)
        assertEquals(30, crank.spinTicks)
        CrankMachine.advanceTo(crank, 200)
        assertEquals(0, crank.spinTicks)
        assertEquals(0, CrankMachine.productionPerSolveTick(crank))
    }

    @Test
    fun `idle crank stays clean and fresh spin starts aging at activation`() {
        val crank = CrankState()
        assertFalse(CrankMachine.advanceTo(crank, 100))
        assertFalse(CrankMachine.advanceTo(crank, 110))
        assertEquals(0, crank.lastAdvancedTick)
        assertTrue(CrankMachine.pulse(crank, 103))
        assertTrue(CrankMachine.advanceTo(crank, 104))
        assertEquals(39, crank.spinTicks)
    }

    @Test
    fun `burner fuel requires a successful kick and expires`() {
        val burner = BurnerState()
        assertFalse(BurnerMachine.kick(burner, 0))
        assertEquals(0, BurnerMachine.addFuel(burner, "unknown"))
        assertEquals(40, BurnerMachine.addFuel(burner, "Ingredient_Scraps"))
        assertEquals(0, BurnerMachine.productionPerSolveTick(burner))
        assertTrue(BurnerMachine.kick(burner, 0))
        assertFalse(BurnerMachine.kick(burner, 0))
        assertEquals(BurnerMachine.FIZZ_PER_SOLVE_TICK, BurnerMachine.productionPerSolveTick(burner))
        BurnerMachine.advanceTo(burner, 30)
        assertEquals(10, burner.burnTicks)
        BurnerMachine.advanceTo(burner, 50)
        assertEquals(0, burner.burnTicks)
        assertFalse(burner.burning)
    }

    @Test
    fun `kick is consumed only for a fueled unlit burner`() {
        val fueled = BurnerState(burnTicks = 200)
        assertTrue(BurnerMachine.kick(fueled, 103))
        assertFalse(BurnerMachine.kick(fueled, 104))
        assertFalse(BurnerMachine.kick(BurnerState(), 103))
    }

    @Test
    fun `fresh burner fuel starts aging at kick tick`() {
        val burner = BurnerState()
        BurnerMachine.advanceTo(burner, 100)
        BurnerMachine.addFuel(burner, "Plant_Crop_Mushroom_Common_Brown")
        assertTrue(BurnerMachine.kick(burner, 103))
        assertTrue(BurnerMachine.advanceTo(burner, 104))
        assertEquals(199, burner.burnTicks)
    }

    @Test
    fun `reloaded machine state starts advancing from the current world tick`() {
        val crank = CrankState(spinTicks = 40)
        val burner = BurnerState(burnTicks = 40, burning = true)

        MachineClock.initialize(crank, 500)
        MachineClock.initialize(burner, 500)

        assertFalse(CrankMachine.advanceTo(crank, 500))
        assertFalse(BurnerMachine.advanceTo(burner, 500))
        assertEquals(40, crank.spinTicks)
        assertEquals(40, burner.burnTicks)
        assertTrue(CrankMachine.advanceTo(crank, 501))
        assertTrue(BurnerMachine.advanceTo(burner, 501))
        assertEquals(39, crank.spinTicks)
        assertEquals(39, burner.burnTicks)
    }

    @Test
    fun `throttled crank persistence retries until saved`() {
        val crank = CrankState(spinTicks = 12, lastAdvancedTick = 172)
        assertTrue(CrankMachine.advanceTo(crank, 184))
        assertEquals(0, crank.spinTicks)
        val member = EnergyMember(lastSavedTick = 172, unsaved = true)
        assertFalse(member.shouldPersist(184, 20, immediate = false))
        assertTrue(member.unsaved)
        assertFalse(member.shouldPersist(188, 20, immediate = false))
        assertTrue(member.unsaved)
        assertTrue(member.shouldPersist(192, 20, immediate = false))
        member.markSaved(192)
        assertFalse(member.unsaved)
        assertEquals(192, member.lastSavedTick)
        assertFalse(member.shouldPersist(200, 20, immediate = false))
        assertFalse(member.unsaved)
    }
}
