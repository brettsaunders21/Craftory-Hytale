package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.machine.jank.BodgePart
import studio.craftory.tech.machine.jank.bodgeModifiers

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
    fun `paused machines do not advance spin or fuel`() {
        val crank = CrankState(spinTicks = 40, lastAdvancedTick = 100)
        val burner = BurnerState(burnTicks = 200, burning = true, lastAdvancedTick = 100)

        MachineClock.pause(crank, 400)
        MachineClock.pause(burner, 400)

        assertEquals(40, crank.spinTicks)
        assertEquals(200, burner.burnTicks)
        assertFalse(CrankMachine.advanceTo(crank, 400))
        assertFalse(BurnerMachine.advanceTo(burner, 400))
        assertEquals(40, crank.spinTicks)
        assertEquals(200, burner.burnTicks)
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
    fun `oven collects surplus fizz and charges whenever fuel space is available`() {
        val secondCharge =
            OvenFizzAccounting.update(
                accumulatedFizz = 90,
                lastFizzSolveTick = 100,
                completedSolveTick = 101,
                deliveredFizz = 30,
                hasRecipeInput = true,
                chargeCount = 1,
                fuelSlotAvailable = true,
            )

        assertEquals(OvenFizzAccounting.FIZZ_PER_SOLVE_TICK, secondCharge.demand)
        assertEquals(120, secondCharge.accumulatedFizz)
        assertEquals(101, secondCharge.lastFizzSolveTick)
        assertTrue(secondCharge.shouldInsertCharge)

        val surplus =
            OvenFizzAccounting.update(
                accumulatedFizz = secondCharge.accumulatedFizz - OvenFizzAccounting.FIZZ_PER_CHARGE,
                lastFizzSolveTick = secondCharge.lastFizzSolveTick,
                completedSolveTick = 102,
                deliveredFizz = 30,
                hasRecipeInput = true,
                chargeCount = 2,
                fuelSlotAvailable = true,
            )

        assertEquals(0, surplus.demand)
        assertEquals(30, surplus.accumulatedFizz)
        assertEquals(102, surplus.lastFizzSolveTick)
        assertFalse(surplus.shouldInsertCharge)
    }

    @Test
    fun `oven keeps full demand while plain fizz slows only the gnasher`() {
        assertEquals(100, TinkeredSpeed.percent(1))
        assertEquals(50, TinkeredSpeed.percent(0))
        val oven =
            OvenFizzAccounting.update(
                accumulatedFizz = 90,
                lastFizzSolveTick = 100,
                completedSolveTick = 101,
                deliveredFizz = OvenFizzAccounting.FIZZ_PER_SOLVE_TICK,
                hasRecipeInput = true,
                chargeCount = 0,
                fuelSlotAvailable = true,
            )
        assertEquals(OvenFizzAccounting.FIZZ_PER_SOLVE_TICK, oven.demand)
        assertEquals(OvenFizzAccounting.FIZZ_PER_CHARGE, oven.accumulatedFizz)
        assertTrue(oven.shouldInsertCharge)

        val gnasher = GnasherState(lastAutoCrankTick = 0)
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper"))
        assertFalse(
            GnasherMachine
                .autoCrank(gnasher, 39, GnasherMachine.FIZZ_PER_SOLVE_TICK, deliveredRank = 0)
                .accepted,
        )
        assertTrue(
            GnasherMachine
                .autoCrank(gnasher, 40, GnasherMachine.FIZZ_PER_SOLVE_TICK, deliveredRank = 0)
                .accepted,
        )
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

    @Test
    fun `gnasher accepts ore variants and returns two grit after six pulses per ore`() {
        val gnasher = GnasherState()

        assertFalse(GnasherMachine.deposit(gnasher, "Ore_Unknown"))
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper_Stone"))
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper"))

        val outputs =
            (0 until GnasherMachine.PULSES_PER_ORE * 2)
                .map { pulse -> GnasherMachine.pulse(gnasher, pulse * GnasherMachine.MANUAL_PULSE_INTERVAL_TICKS) }
                .filter { it.outputItemId != null }
                .mapNotNull { it.outputItemId }

        assertEquals(listOf("Craftory_Grit_Copper", "Craftory_Grit_Copper"), outputs)
        assertEquals(0, GritTable.queuedOreCount(gnasher.oreQueue))
        assertEquals(GnasherMachine.GRIT_OUTPUT_QUANTITY, 2)
    }

    @Test
    fun `gnasher processes ores in arrival order`() {
        val gnasher = GnasherState()
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper"))
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Iron"))
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper"))

        val outputs =
            (0 until GnasherMachine.PULSES_PER_ORE * 3)
                .map { pulse -> GnasherMachine.pulse(gnasher, pulse * GnasherMachine.MANUAL_PULSE_INTERVAL_TICKS) }
                .filter { it.outputItemId != null }
                .mapNotNull { it.outputItemId }

        assertEquals(
            listOf("Craftory_Grit_Copper", "Craftory_Grit_Iron", "Craftory_Grit_Copper"),
            outputs,
        )
    }

    @Test
    fun `gnasher accepts ninth ore then jams until a kick clears it`() {
        val gnasher = GnasherState()

        repeat(GnasherMachine.HOPPER_CAPACITY + 1) {
            assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper"))
        }
        assertEquals(9, GritTable.queuedOreCount(gnasher.oreQueue))
        assertTrue(gnasher.jammed)
        assertFalse(GnasherMachine.pulse(gnasher, 100).accepted)
        assertFalse(GnasherMachine.deposit(gnasher, "Ore_Copper"))
        assertTrue(GnasherMachine.kick(gnasher))
        assertFalse(gnasher.jammed)
        assertFalse(GnasherMachine.kick(gnasher))
    }

    @Test
    fun `gnasher powered mode requires delivered fizz and pulses at most every twenty ticks`() {
        val gnasher = GnasherState(lastAutoCrankTick = 0)
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper"))

        assertFalse(GnasherMachine.autoCrank(gnasher, 0, GnasherMachine.FIZZ_PER_SOLVE_TICK).accepted)
        assertFalse(GnasherMachine.autoCrank(gnasher, 20, 0).accepted)
        assertFalse(GnasherMachine.autoCrank(gnasher, 19, GnasherMachine.FIZZ_PER_SOLVE_TICK).accepted)
        assertTrue(GnasherMachine.autoCrank(gnasher, 20, GnasherMachine.FIZZ_PER_SOLVE_TICK).accepted)
        assertFalse(GnasherMachine.autoCrank(gnasher, 39, GnasherMachine.FIZZ_PER_SOLVE_TICK).accepted)
        assertTrue(GnasherMachine.autoCrank(gnasher, 40, GnasherMachine.FIZZ_PER_SOLVE_TICK).accepted)
        assertEquals(2, gnasher.progressPulses)
    }

    @Test
    fun `gutter gear gnasher grinds at reduced delivered demand`() {
        val requiredDelivery =
            (GnasherMachine.FIZZ_PER_SOLVE_TICK * bodgeModifiers(listOf(BodgePart.GUTTER_GEAR)).energyUse).toLong()
        val gnasher = GnasherState()
        assertTrue(GnasherMachine.deposit(gnasher, "Ore_Copper"))

        assertTrue(GnasherMachine.autoCrank(gnasher, 0, requiredDelivery, requiredDelivery = requiredDelivery).accepted)
    }

    @Test
    fun `gnasher hopper persistence preserves item ids and counts`() {
        val gnasher = GnasherState()
        GnasherMachine.deposit(gnasher, "Ore_Copper")
        GnasherMachine.deposit(gnasher, "Ore_Copper")
        GnasherMachine.deposit(gnasher, "Ore_Iron_Slate")

        val restored = GnasherState(oreQueue = gnasher.oreQueue, progressPulses = 3)

        assertEquals(
            listOf("Ore_Copper", "Ore_Copper", "Ore_Iron_Slate"),
            GritTable.decodeOreQueue(restored.oreQueue),
        )
        assertEquals(3, restored.progressPulses)
    }

    @Test
    fun `grit table covers all mapped metal ore variants`() {
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
        assertTrue(GritTable.definitions.flatMap { it.oreItemIds }.all { GritTable.gritForOre(it) != null })
    }
}
