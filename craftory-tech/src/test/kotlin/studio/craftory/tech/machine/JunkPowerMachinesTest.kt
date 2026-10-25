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

    @Test
    fun `dirty crank saves after the throttle interval and stays clean`() {
        val crank = CrankMachine(initialSpinTicks = 12)
        assertFalse(crank.advanceTo(172))
        assertTrue(crank.advanceTo(184))
        assertEquals(0, crank.spinTicks)

        var lastSavedTick = 172L
        var unsaved = true

        fun persist(currentTick: Long) {
            if (!MachineSaveThrottle.shouldPersist(unsaved, currentTick, lastSavedTick, 20, immediate = false)) {
                return
            }
            lastSavedTick = currentTick
            unsaved = false
        }

        persist(184)
        assertTrue(unsaved)
        assertEquals(172, lastSavedTick)
        persist(188)
        assertTrue(unsaved)
        persist(192)
        assertFalse(unsaved)
        assertEquals(192, lastSavedTick)
        persist(200)
        assertFalse(unsaved)
        assertEquals(192, lastSavedTick)
    }

    @Test
    fun `gnasher accepts mapped ore variants and doubles one ore after six pulses`() {
        val gnasher = GnasherPitMachine()

        assertFalse(gnasher.deposit("Ore_Unknown"))
        assertTrue(gnasher.deposit("Ore_Copper_Stone"))
        assertTrue(gnasher.deposit("Ore_Copper"))
        assertEquals(2, gnasher.queuedOreCount)

        repeat(GnasherPitMachine.PULSES_PER_ORE) { index ->
            assertEquals(
                if (index == GnasherPitMachine.PULSES_PER_ORE - 1) "Craftory_Grit_Copper" else null,
                gnasher.crank(index * CrankMachine.PULSE_INTERVAL_TICKS),
            )
        }
        assertEquals(1, gnasher.queuedOreCount)
        assertEquals(2, GnasherPitMachine.GRIT_OUTPUT_QUANTITY)
    }

    @Test
    fun `gnasher accepts ninth ore then jams and a kick clears the jam`() {
        val gnasher = GnasherPitMachine()

        repeat(GnasherPitMachine.HOPPER_CAPACITY + 1) {
            assertTrue(gnasher.deposit("Ore_Copper"))
        }
        assertEquals(GnasherPitMachine.HOPPER_CAPACITY + 1, gnasher.queuedOreCount)
        assertTrue(gnasher.isJammed)
        assertEquals(null, gnasher.crank(100))
        assertFalse(gnasher.deposit("Ore_Copper"))
        assertTrue(gnasher.kick())
        assertFalse(gnasher.kick())
        assertFalse(gnasher.isJammed)
    }

    @Test
    fun `gnasher auto cranks once per twenty ticks only with delivered fizz`() {
        val gnasher = GnasherPitMachine()
        gnasher.deposit("Ore_Copper")

        assertEquals(null, gnasher.autoCrank(0, 0))
        assertEquals(null, gnasher.autoCrank(0, GnasherPitMachine.FIZZ_PER_SOLVE_TICK))
        assertEquals(null, gnasher.autoCrank(19, GnasherPitMachine.FIZZ_PER_SOLVE_TICK))
        assertEquals(null, gnasher.autoCrank(20, GnasherPitMachine.FIZZ_PER_SOLVE_TICK))
        assertEquals(2, gnasher.progressPulses)
    }

    @Test
    fun `gnasher queue snapshot persists and restores valid grit ids`() {
        val gnasher = GnasherPitMachine()
        gnasher.deposit("Ore_Copper")
        gnasher.deposit("Ore_Iron_Slate")

        val restored = GnasherPitMachine(GritTable.decodeQueue(GritTable.encodeQueue(gnasher.snapshot())))

        assertEquals(2, restored.queuedOreCount)
        assertEquals(mapOf("Craftory_Grit_Copper" to 1, "Craftory_Grit_Iron" to 1), restored.snapshot())
    }

    @Test
    fun `grit table covers all eligible metal ore variants`() {
        assertEquals(
            setOf(
                "Adamantite",
                "Cobalt",
                "Copper",
                "Gold",
                "Iron",
                "Mithril",
                "Onyxium",
                "Prisma",
                "Silver",
                "Thorium",
            ),
            GritTable.definitions.map { it.metal }.toSet(),
        )
        assertEquals(
            GritTable.definitions.sumOf { it.oreItemIds.size },
            GritTable.definitions
                .flatMap { it.oreItemIds }
                .toSet()
                .size,
        )
    }
}
