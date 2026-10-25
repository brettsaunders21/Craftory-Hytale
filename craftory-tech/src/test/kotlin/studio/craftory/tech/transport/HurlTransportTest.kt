package studio.craftory.tech.transport

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.transport.HurlArc
import studio.craftory.core.api.transport.HurlPoint
import java.util.UUID

class HurlTransportTest {
    @Test
    fun `mount is the default driver with an explicit teleport escape hatch`() {
        val config = HurlConfig()
        assertEquals("mount", config.rideDriver)
        config.rideDriver = "teleport"
        assertEquals("teleport", config.rideDriver)
        assertThrows(IllegalArgumentException::class.java) { config.rideDriver = "unknown" }
    }

    @Test
    fun `arc ride samples ahead across sections and lands exactly when its timer expires`() {
        val launch = HurlPoint(-32.5, 31.0, 0.5)
        val target = HurlPoint(0.5, 33.0, 0.5)
        val arc = requireNotNull(HurlArc.create(launch, target))
        val ride =
            HurlRide().also {
                it.mode = HurlMode.ARC
                it.arc = arc
                arc.positionAt(0.0, it.output)
            }
        assertEquals(target, ride.landing)
        assertEquals(BlockPos.pack(-33, 31, 0), ride.currentBlock())
        val ahead = ride.blocksAhead().map(BlockPos::unpack)
        assertTrue(ahead.any { it.x < -32 })
        assertTrue(ahead.any { it.x >= -32 })
        assertTrue(ahead.any { it.y >= 32 })
        val copy = ride.clone() as HurlRide
        assertEquals(target, copy.landing)
        assertArrayEquals(ride.output, copy.output)
        var ticks = 0
        while (!ride.stepArc()) ticks++
        assertEquals(kotlin.math.ceil(arc.flightTicks).toInt(), ticks + 1)
        assertArrayEquals(doubleArrayOf(target.x, target.y, target.z), ride.output, 0.00001)
        assertEquals(listOf(BlockPos.pack(0, 33, 0)), ride.blocksAhead())
        assertTrue(ride.stepArc())
        assertEquals(arc.flightTicks, ride.flightTime)
    }

    @Test
    fun `missing arc is rejected and out of range trajectories are unavailable`() {
        val ride = HurlRide().also { it.mode = HurlMode.ARC }
        assertThrows(IllegalArgumentException::class.java) { ride.stepArc() }
        assertThrows(IllegalArgumentException::class.java) { ride.blocksAhead() }
        assertNull(HurlArc.create(HurlPoint(0.0, 0.0, 0.0), HurlPoint(65.0, 0.0, 0.0)))
    }

    @Test
    fun `net protection covers unguarded falls but never other damage or unnecessary block lookups`() {
        assertTrue(protectsFall(null, 200, true) { true })
        assertFalse(protectsFall(null, 200, true) { false })
        assertTrue(protectsFall(HurlFallGuard(200), 200, true) { true })
        assertFalse(protectsFall(HurlFallGuard(200), 200, true) { false })
        assertFalse(protectsFall(null, 0, false) { error("non-fall damage must not look up a block") })
        assertTrue(protectsFall(HurlFallGuard(200), 1, true) { error("active guard needs no block lookup") })
    }

    @Test
    fun `cost config rounds units with a minimum and supports changing grade and price`() {
        val config = HurlConfig()
        assertEquals("fizz", config.costGrade)
        assertEquals(0.5, config.speed)
        assertEquals(100L, config.energyCost(0.0))
        assertEquals(100L, config.energyCost(16.0))
        assertEquals(200L, config.energyCost(16.01))
        config.costGrade = "pressurised_fizz"
        config.blocksPerUnit = 8.0
        config.energyPerUnit = 25L
        config.speed = 2.0
        assertEquals("pressurised_fizz", config.costGrade)
        assertEquals(2.0, config.speed)
        assertEquals(75L, config.energyCost(17.0))
        config.energyPerUnit = 0L
        assertEquals(0L, config.energyCost(17.0))
    }

    @Test
    fun `cost rejects impossible totals without overflow and validates bad configuration`() {
        val config = HurlConfig()
        config.energyPerUnit = Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, config.energyCost(1.0))
        assertNull(config.energyCost(17.0))
        config.blocksPerUnit = Double.MIN_VALUE
        assertNull(config.energyCost(1.0))
        assertThrows(IllegalArgumentException::class.java) { config.costGrade = "" }
        assertThrows(IllegalArgumentException::class.java) { config.blocksPerUnit = 0.0 }
        assertThrows(IllegalArgumentException::class.java) { config.blocksPerUnit = Double.NaN }
        assertThrows(IllegalArgumentException::class.java) { config.energyPerUnit = -1 }
        assertThrows(IllegalArgumentException::class.java) { config.speed = -1.0 }
        assertThrows(IllegalArgumentException::class.java) { config.speed = Double.POSITIVE_INFINITY }
        assertThrows(IllegalArgumentException::class.java) { config.energyCost(-1.0) }
    }

    @Test
    fun `blank names have coordinate defaults and clones retain explicit names`() {
        val position = BlockPos.pack(-3, 25, 7)
        assertEquals("Station -3,25,7", HurlStation().displayName(position))
        assertEquals("Station -3,25,7", HurlStation("  ").displayName(position))
        val station = HurlStation("Workshop")
        assertEquals("Workshop", (station.clone() as HurlStation).displayName(position))
        assertEquals("Workshop", station.name)
    }

    @Test
    fun `picker permits crew owned and unowned destinations but not outsiders`() {
        val owner = UUID.randomUUID()
        val crew = setOf(owner, UUID.randomUUID())
        assertTrue(StationPermissions.mayVisit(null, emptySet()))
        assertTrue(StationPermissions.mayVisit(owner, crew))
        assertFalse(StationPermissions.mayVisit(UUID.randomUUID(), crew))
        assertFalse(StationPermissions.mayVisit(owner, emptySet()))
    }

    @Test
    fun `renaming uses wrench owner crew and admin rules including unowned protection`() {
        val owner = UUID.randomUUID()
        val member = UUID.randomUUID()
        assertTrue(StationPermissions.mayRename(owner, owner, setOf(owner), false))
        assertTrue(StationPermissions.mayRename(owner, member, setOf(owner, member), false))
        assertFalse(StationPermissions.mayRename(owner, member, setOf(member), false))
        assertFalse(StationPermissions.mayRename(null, member, setOf(member), false))
        assertTrue(StationPermissions.mayRename(null, member, emptySet(), true))
        assertTrue(StationPermissions.mayRename(owner, member, emptySet(), true))
    }

    @Test
    fun `guard cancels only falls before expiry and is transiently cloneable`() {
        val guard = HurlFallGuard(200)
        assertTrue(guard.protects(199, true))
        assertFalse(guard.protects(199, false))
        assertFalse(guard.protects(200, true))
        assertFalse(HurlFallGuard().protects(0, true))
        assertEquals(200L, (guard.clone() as HurlFallGuard).expiryTick)
    }
}
