package studio.craftory.tech.machine

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import studio.craftory.tech.machine.jank.Jank

class EnergyHudRenderTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun configureHytaleLogging() {
            System.setProperty(
                "java.util.logging.manager",
                "com.hypixel.hytale.logger.backend.HytaleLogManager",
            )
        }
    }

    @Test
    fun `initial render emits commands`() {
        val commands = UICommandBuilder()

        energyHudCommands(null, snapshot(), commands)

        assertTrue(commands.getCommands().isNotEmpty())
    }

    @Test
    fun `unchanged snapshot emits no commands`() {
        val snapshot = snapshot()
        val commands = UICommandBuilder()

        energyHudCommands(snapshot, snapshot, commands)

        assertEquals(0, commands.getCommands().size)
    }

    @Test
    fun `stored-only change updates stored value and energy progress`() {
        val previous = snapshot(stored = 10)
        val next = previous.copy(stored = 20)
        val commands = UICommandBuilder()

        energyHudCommands(previous, next, commands)

        assertEquals(
            setOf("#StoredValue.Text", "#Energy.Value"),
            commands.getCommands().mapNotNull { it.selector }.toSet(),
        )
        assertEquals(2, commands.getCommands().size)
    }

    @Test
    fun `shroom bed renders and updates ready count`() {
        val previous = snapshot().copy(shroomBed = null)
        val nextShroomBed = ShroomBedHud(30, 3, 4, "open")
        val next = previous.copy(shroomBed = nextShroomBed)
        val initialCommands = UICommandBuilder()

        energyHudCommands(previous, next, initialCommands)

        assertTrue(initialCommands.getCommands().any { it.selector == "#ReadyRow.Visible" })
        assertTrue(initialCommands.getCommands().any { it.selector == "#ReadyValue.Text" })

        val updated = next.copy(shroomBed = nextShroomBed.copy(stored = 5))
        val updateCommands = UICommandBuilder()

        energyHudCommands(next, updated, updateCommands)

        assertEquals(listOf("#ReadyValue.Text"), updateCommands.getCommands().mapNotNull { it.selector })
    }

    @Test
    fun `machine status follows priority and network rules`() {
        assertEquals(
            MachineStatus.STOLEN,
            machineStatus(Jank(stolen = true, stallTicks = 4), null, null, 0),
        )
        assertEquals(
            MachineStatus.STALLED,
            machineStatus(Jank(stallTicks = 4), null, null, 0),
        )
        assertEquals(
            MachineStatus.RUNNING,
            machineStatus(null, RattlewheelHud(2, false, 1), null, 0),
        )
        assertEquals(MachineStatus.IDLE, machineStatus(null, null, null, 0))
        assertEquals(MachineStatus.IDLE, machineStatus(null, null, MemberActivity(true, 0, 0, 0), 0))
        assertEquals(MachineStatus.RUNNING, machineStatus(null, null, MemberActivity(true, 1, 0, 0), 0))
        assertEquals(MachineStatus.RUNNING, machineStatus(null, null, MemberActivity(true, 0, 1, 1), 0))
        assertEquals(MachineStatus.NO_POWER, machineStatus(null, null, MemberActivity(true, 0, 1, 0), 0))
        assertEquals(MachineStatus.RUNNING, machineStatus(null, null, MemberActivity(true, 0, 1, 0), 1))
    }

    private fun snapshot(stored: Long = 10): EnergyHudSnapshot =
        EnergyHudSnapshot(
            itemId = "Craftory_Rattlewheel",
            stored = stored,
            capacity = 100,
            gradeRank = 2,
            members = 3,
            supply = 5,
            demand = 2,
            inNetwork = true,
            jank = 12,
            status = MachineStatus.RUNNING,
            rattlewheel = RattlewheelHud(4, true, 6),
            shroomBed = null,
            carrierStability = null,
        )
}
