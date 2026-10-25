package studio.craftory.core.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.engine.energy.EnergyEventSink

class CoreEnergyTest {
    @Test
    fun `block positions round trip signed coordinates`() {
        listOf(
            BlockPos(-1, -1, -1),
            BlockPos(-(1 shl 25), -(1 shl 11), (1 shl 25) - 1),
            BlockPos((1 shl 25) - 1, (1 shl 11) - 1, -(1 shl 25)),
            BlockPos(-812345, 92, 765432),
        ).forEach { position ->
            assertEquals(position, BlockPos.unpack(position.pack()))
        }
    }

    @Test
    fun `energy type registry rejects duplicates`() {
        val spec = MemberSpec(MemberSpec.PRODUCER, 0, 0, 10, 0, 5)
        val id = "test:core_energy_duplicate"
        assertEquals(id, CraftoryCore.registerEnergyType(id, spec).id)
        assertThrows(IllegalStateException::class.java) { CraftoryCore.registerEnergyType(id, spec) }
    }

    @Test
    fun `fake adapter adds removes and solves members`() {
        val adapter = EnergyRuntimeAdapter()
        val producer = MemberSpec(MemberSpec.PRODUCER, 0, 0, 100, 0, 5)
        val consumer = MemberSpec(MemberSpec.CONSUMER, 0, 100, 0, 0, 5)
        adapter.add(11, producer, longArrayOf())
        adapter.add(12, consumer, longArrayOf(11))
        adapter.setProduction(11, 20, 1)
        adapter.setDemand(12, 8)
        adapter.tick(EnergyEventSink.NONE)

        assertEquals(8, adapter.graph.delivered(12))
        assertEquals(1, adapter.graph.deliveredRank(12))
        assertNotNull(adapter.diagnostics(12))
        adapter.remove(11)
        assertEquals(-1, adapter.graph.networkOf(11))
        assertTrue(adapter.graph.networkOf(12) >= 0)
    }

    @Test
    fun `network diagnostics retain the last solve values`() {
        val adapter = EnergyRuntimeAdapter()
        adapter.add(31, MemberSpec(MemberSpec.PRODUCER, 0, 0, 100, 0, 5), longArrayOf())
        adapter.add(32, MemberSpec(MemberSpec.CONSUMER, 0, 100, 0, 0, 5), longArrayOf(31))
        adapter.setProduction(31, 10, 1)
        adapter.setDemand(32, 4)
        adapter.tick(EnergyEventSink.NONE)

        adapter.setProduction(31, 20, 2)
        adapter.setDemand(32, 7)
        assertEquals(10, adapter.diagnostics(31)?.supply)
        assertEquals(4, adapter.diagnostics(31)?.demand)

        adapter.tick(EnergyEventSink.NONE)
        assertEquals(20, adapter.diagnostics(31)?.supply)
        assertEquals(7, adapter.diagnostics(31)?.demand)
    }

    @Test
    fun `disabled storage is excluded from network diagnostics after the next solve`() {
        val adapter = EnergyRuntimeAdapter()
        val storage = MemberSpec(MemberSpec.STORAGE, 100, 100, 100, 0, 5)
        adapter.add(41, storage, longArrayOf(), stored = 40, storeRank = 2)
        adapter.tick(EnergyEventSink.NONE)

        adapter.setStorageEnabled(41, false)
        assertEquals(40, adapter.diagnostics(41)?.supply)
        assertEquals(2, adapter.diagnostics(41)?.grade)

        adapter.tick(EnergyEventSink.NONE)
        val diagnostics = adapter.diagnostics(41)
        assertEquals(0, diagnostics?.supply)
        assertEquals(0, diagnostics?.grade)
        assertEquals(40, adapter.graph.stored(41))
    }

    @Test
    fun `fake adapter restores stored energy and rank`() {
        val adapter = EnergyRuntimeAdapter()
        val storage = MemberSpec(MemberSpec.STORAGE, 100, 100, 100, 0, 5)
        adapter.add(21, storage, longArrayOf(), stored = 35, storeRank = 2)
        assertEquals(35, adapter.graph.stored(21))
        assertEquals(2, adapter.graph.storedRank(21))
        adapter.remove(21)
        adapter.add(21, storage, longArrayOf(), stored = 35, storeRank = 2)
        assertEquals(35, adapter.graph.stored(21))
        assertEquals(2, adapter.graph.storedRank(21))
    }

    @Test
    fun `event sink queues and drains events`() {
        val observed = mutableListOf<studio.craftory.core.energy.CraftoryEnergyEvent>()
        val subscription = CraftoryEnergyEvents.subscribe(observed::add)
        try {
            val events = EnergyEventBuffer()
            events.overload(42, 2)
            events.underGrade(43)
            events.brownout(7, 0.5)
            events.drain()

            assertEquals(3, observed.size)
            assertEquals(OverloadEvent(42, 2), observed[0])
            assertEquals(UnderGradeEvent(43), observed[1])
            assertEquals(BrownoutEvent(7, 0.5), observed[2])
        } finally {
            subscription.close()
        }
    }

    @Test
    fun `block position rejects out of range values`() {
        assertThrows(IllegalArgumentException::class.java) { BlockPos.pack(1 shl 25, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { BlockPos.pack(0, 1 shl 11, 0) }
        assertFalse(BlockPos.unpack(BlockPos.pack(3, 5, -7)) == BlockPos(3, 5, 7))
    }

    @Test
    fun `energy requests reject negative amounts invalid ranks and incompatible roles`() {
        val producer = MemberSpec(MemberSpec.PRODUCER, 0, 0, 100, 0, 5)
        val consumer = MemberSpec(MemberSpec.CONSUMER, 0, 100, 0, 0, 5)

        assertEquals(
            "Production amount must be non-negative",
            assertThrows(IllegalArgumentException::class.java) {
                EnergyRequestValidation.production(1, producer, -1, 0)
            }.message,
        )
        assertEquals(
            "Production rank must be non-negative",
            assertThrows(IllegalArgumentException::class.java) {
                EnergyRequestValidation.production(1, producer, 1, -1)
            }.message,
        )
        assertEquals(
            "Production rank 6 is outside the grade table",
            assertThrows(IllegalArgumentException::class.java) {
                EnergyRequestValidation.production(1, producer, 1, 6)
            }.message,
        )
        assertEquals(
            "Member 2 is not a producer",
            assertThrows(IllegalArgumentException::class.java) {
                EnergyRequestValidation.production(2, consumer, 1, 0)
            }.message,
        )
        assertEquals(
            "Demand amount must be non-negative",
            assertThrows(IllegalArgumentException::class.java) {
                EnergyRequestValidation.demand(2, consumer, -1)
            }.message,
        )
        assertEquals(
            "Member 1 is not a consumer",
            assertThrows(IllegalArgumentException::class.java) {
                EnergyRequestValidation.demand(1, producer, 1)
            }.message,
        )
        EnergyRequestValidation.production(1, producer, 1, 0)
        EnergyRequestValidation.demand(2, consumer, 1)
    }
}
