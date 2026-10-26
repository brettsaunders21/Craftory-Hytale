package studio.craftory.core.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyFaces
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.EnergyMemberIds
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.engine.energy.EnergyEventSink

class CoreEnergyTest {
    @Test
    fun `debited storage writes back dirty state and survives adapter reload`() {
        val adapter = EnergyRuntimeAdapter()
        adapter.add(1, MemberSpec(MemberSpec.LINK, 0, 0, 0, 0, 5), longArrayOf())
        adapter.add(2, MemberSpec(MemberSpec.STORAGE, 100, 100, 100, 0, 5), longArrayOf(1), 50, 1)
        val member = EnergyMember(stored = 50, storeRank = 1)
        assertFalse(adapter.graph.drawStored(1, 51, 1))
        assertFalse(member.updateStored(adapter.graph.stored(2), adapter.graph.storedRank(2)))
        assertFalse(member.unsaved)
        assertTrue(adapter.graph.drawStored(1, 20, 1))
        assertTrue(member.updateStored(adapter.graph.stored(2), adapter.graph.storedRank(2)))
        assertEquals(30L, member.stored)
        assertEquals(1, member.storeRank)
        assertTrue(member.unsaved)
        assertTrue(member.shouldPersist(0, 100, immediate = true))
        val saved = adapter.remove(2)
        assertEquals(member.stored, saved.amount)
        assertEquals(member.storeRank, saved.rank)
        adapter.add(2, MemberSpec(MemberSpec.STORAGE, 100, 100, 100, 0, 5), longArrayOf(1), saved.amount, saved.rank)
        assertEquals(30L, adapter.graph.networkStored(1, 1))
        assertTrue(adapter.graph.drawStored(1, 30, 1))
        member.updateStored(adapter.graph.stored(2), adapter.graph.storedRank(2))
        assertEquals(-1, member.storeRank)
    }

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
    fun `converter output ids flip the packed y sign bit and round trip`() {
        val xs = listOf(-(1 shl 25), (1 shl 25) - 1)
        val zs = listOf(-(1 shl 25), (1 shl 25) - 1)
        for (y in listOf(-2048, -1, 0, 319, 2047)) {
            for (x in xs) {
                for (z in zs) {
                    val input = BlockPos.pack(x, y, z)
                    val output = EnergyMemberIds.converterOutput(input)
                    assertEquals(input, EnergyMemberIds.converterOutput(output))
                    assertNotEquals(input, output)
                }
            }
        }
    }

    @Test
    fun `converter id collisions refuse both physical and output nodes`() {
        val converterInput = BlockPos.pack(19, -2048, -7)
        val physicalMember = BlockPos.pack(19, 0, -7)
        val outputId = EnergyMemberIds.converterOutput(converterInput)
        assertEquals(physicalMember, outputId)
        assertFalse(EnergyMemberIds.allowConverterOutput(outputIdTaken = true))
        assertFalse(EnergyMemberIds.allowPhysicalNode(existingConverterOutput = true))
        assertTrue(EnergyMemberIds.allowPhysicalNode(existingConverterOutput = false))
        assertTrue(EnergyMemberIds.allowConverterOutput(outputIdTaken = false))
    }

    @Test
    fun `converter removal ownership cannot remove another converter output`() {
        val converterA = BlockPos.pack(19, 0, -7)
        val converterB = BlockPos.pack(19, -2048, -7)
        val outputA = EnergyMemberIds.converterOutput(converterA)
        assertEquals(converterB, outputA)
        assertFalse(EnergyMemberIds.ownsConverterOutput(false, converterA, converterB))
        assertFalse(EnergyMemberIds.ownsConverterOutput(true, converterA, converterB))
        assertTrue(EnergyMemberIds.ownsConverterOutput(true, converterA, converterA))
    }

    @Test
    fun `converter output network rejects a physical member at its flipped id`() {
        val converterInput = BlockPos.pack(19, -2048, -7)
        val physicalId = BlockPos.pack(19, 0, -7)
        assertEquals(physicalId, EnergyMemberIds.converterOutput(converterInput))
        assertEquals(
            -1,
            EnergyMemberIds.converterOutputNetwork(
                nodePresent = true,
                nodeConverterOutput = false,
                nodeOwnerInputId = physicalId,
                inputId = converterInput,
                networkId = 42,
            ),
        )
        assertEquals(
            42,
            EnergyMemberIds.converterOutputNetwork(
                nodePresent = true,
                nodeConverterOutput = true,
                nodeOwnerInputId = converterInput,
                inputId = converterInput,
                networkId = 42,
            ),
        )
    }

    @Test
    fun `energy type registry rejects duplicates`() {
        val spec = MemberSpec(MemberSpec.PRODUCER, 0, 0, 10, 0, 5)
        val id = "test:core_energy_duplicate"
        assertEquals(id, CraftoryCore.registerEnergyType(id, spec).id)
        assertThrows(IllegalStateException::class.java) { CraftoryCore.registerEnergyType(id, spec) }
    }

    @Test
    fun `energy registry rejects cross type collisions without partial writes`() {
        val producer = MemberSpec(MemberSpec.PRODUCER, 0, 0, 100, 0, 5)
        val consumer = MemberSpec(MemberSpec.CONSUMER, 0, 100, 0, 0, 5)
        val energyId = "test:core_energy_cross_collision"
        val energyType = CraftoryCore.registerEnergyType(energyId, producer)
        assertThrows(IllegalStateException::class.java) {
            CraftoryCore.registerConverterType(energyId, consumer, producer, EnergyFaces.ALL, EnergyFaces.ALL)
        }
        assertEquals(energyType, CraftoryCore.energyType(energyId))
        assertEquals(null, CraftoryCore.converterType(energyId))

        val converterId = "test:core_converter_cross_collision"
        val converter =
            CraftoryCore.registerConverterType(converterId, consumer, producer, EnergyFaces.ALL, EnergyFaces.ALL)
        assertThrows(IllegalStateException::class.java) {
            CraftoryCore.registerEnergyType(converterId, producer)
        }
        assertEquals(converter, CraftoryCore.converterType(converterId))
        assertEquals(converterId, CraftoryCore.energyType(converterId)?.id)
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
    fun `debit clamps to stored energy and survives a solve`() {
        val adapter = EnergyRuntimeAdapter()
        val storage = MemberSpec(MemberSpec.STORAGE, 100, 100, 100, 0, 5)
        adapter.add(51, storage, longArrayOf(), stored = 35, storeRank = 2)
        adapter.add(52, MemberSpec(MemberSpec.PRODUCER, 0, 0, 100, 0, 5), longArrayOf())

        assertEquals(0, adapter.requestDebit(52, 10))
        assertEquals(0, adapter.requestDebit(999, 10))
        assertEquals(0, adapter.requestDebit(51, 0))
        assertEquals(13, adapter.requestDebit(51, 13))
        assertEquals(22, adapter.graph.stored(51))
        assertEquals(2, adapter.graph.storedRank(51))

        adapter.tick(EnergyEventSink.NONE)
        assertEquals(22, adapter.graph.stored(51))
        assertEquals(2, adapter.graph.storedRank(51))
        assertEquals(22, adapter.requestDebit(51, 100))
        assertEquals(0, adapter.graph.stored(51))
        assertEquals(-1, adapter.graph.storedRank(51))
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

    @Test
    fun `face links require both members to expose the shared world axis`() {
        assertTrue(EnergyFaces.connects(EnergyFaces.DOWN, EnergyFaces.UP, EnergyFaces.DOWN))
        assertTrue(EnergyFaces.connects(EnergyFaces.NORTH, EnergyFaces.SOUTH, EnergyFaces.NORTH))
        assertFalse(EnergyFaces.connects(EnergyFaces.DOWN, EnergyFaces.DOWN, EnergyFaces.DOWN))
        assertFalse(EnergyFaces.connects(EnergyFaces.UP, EnergyFaces.DOWN, EnergyFaces.DOWN))
    }

    @Test
    fun `converter output ids avoid real block positions and each other`() {
        val positions =
            listOf(
                BlockPos(10_020, 155, 10_009),
                BlockPos(-8_000, 70, 200),
                BlockPos(0, 0, 0),
            ).map(BlockPos::pack)
        val outputIds = positions.map(EnergyMemberIds::converterOutput)

        assertEquals(outputIds.size, outputIds.toSet().size)
        assertTrue(positions.none { it in outputIds })
        positions.zip(outputIds).forEach { (position, output) -> assertNotEquals(position, output) }
    }

    @Test
    fun `converter input and output remain separate energy networks`() {
        val adapter = EnergyRuntimeAdapter()
        val fizzProducer = MemberSpec(MemberSpec.PRODUCER, 0, 0, 100, 0, 1)
        val fizzInput = MemberSpec(MemberSpec.CONSUMER, 0, 100, 0, 0, 1)
        val dimOutput = MemberSpec(MemberSpec.PRODUCER, 0, 0, 100, 1, 1)
        val dimConsumer = MemberSpec(MemberSpec.CONSUMER, 0, 100, 0, 1, 1)
        adapter.add(1, fizzProducer, longArrayOf())
        adapter.add(4, dimConsumer, longArrayOf())
        adapter.add(2, fizzInput, longArrayOf(1))
        adapter.add(3, dimOutput, longArrayOf(4))
        assertNotEquals(adapter.graph.networkOf(2), adapter.graph.networkOf(3))

        adapter.setProduction(1, 10, 0)
        adapter.setDemand(2, 5)
        adapter.setProduction(3, 4, 1)
        adapter.setDemand(4, 4)
        adapter.tick(EnergyEventSink.NONE)

        assertEquals(5L, adapter.graph.delivered(2))
        assertEquals(4L, adapter.graph.delivered(4))
        assertNotEquals(adapter.graph.networkOf(1), adapter.graph.networkOf(4))
    }
}
