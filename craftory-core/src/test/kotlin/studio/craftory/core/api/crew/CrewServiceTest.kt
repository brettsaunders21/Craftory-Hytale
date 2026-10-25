package studio.craftory.core.api.crew

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class CrewServiceTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `crew membership merges knowledge and leave or kick creates a knowledge preserving solo crew`() {
        val service = CrewService(temporaryDirectory.resolve("crews.json"), 60_000)
        val owner = UUID.randomUUID()
        val member = UUID.randomUUID()
        val kicked = UUID.randomUUID()
        try {
            assertTrue(service.grantKnowledge(member, "craftory:page/crank"))
            val crew = service.create(owner, "The Riveters")
            assertTrue(service.grantKnowledge(owner, "craftory:page/burner"))
            assertTrue(service.invite(crew, member))
            assertFalse(service.invite(crew, member))
            assertEquals(crew.id, service.accept(member, crew.id)?.id)
            assertTrue(service.hasKnowledge(member, "craftory:page/crank"))
            assertTrue(service.hasKnowledge(member, "craftory:page/burner"))
            assertFalse(service.grantKnowledge(member, "craftory:page/crank"))

            val soloAfterLeaving = service.leave(member)
            assertTrue(soloAfterLeaving.solo)
            assertNotEquals(crew.id, soloAfterLeaving.id)
            assertEquals(setOf("craftory:page/crank", "craftory:page/burner"), soloAfterLeaving.knowledge)

            assertTrue(service.invite(crew, kicked))
            assertEquals(crew.id, service.accept(kicked, crew.id)?.id)
            assertTrue(service.kick(owner, kicked))
            assertTrue(service.crewOf(kicked).solo)
            assertTrue(service.hasKnowledge(kicked, "craftory:page/burner"))
            assertFalse(service.kick(member, owner))
            assertNull(service.accept(UUID.randomUUID(), crew.id))
        } finally {
            service.close()
        }
    }

    @Test
    fun `crew state round trips through the data file`() {
        val file = temporaryDirectory.resolve("crews.json")
        val owner = UUID.randomUUID()
        val member = UUID.randomUUID()
        val first = CrewService(file, 60_000)
        val crew =
            try {
                val created = first.create(owner, "Round Trip")
                first.grantKnowledge(owner, "craftory:page/oven")
                first.invite(created, member)
                first.accept(member, created.id)
                first.setTier(created.id, 2)
                first.crewOf(owner)
            } finally {
                first.close()
            }

        val restored = CrewService(file, 60_000)
        try {
            val loaded = restored.crewOf(member)
            assertEquals(crew.id, loaded.id)
            assertEquals("Round Trip", loaded.name)
            assertEquals(setOf(owner, member), loaded.members)
            assertEquals(setOf("craftory:page/oven"), loaded.knowledge)
            assertEquals(2, restored.tierOf(owner))
            assertTrue(Files.exists(file))
        } finally {
            restored.close()
        }
    }

    @Test
    fun `rapid changes are persisted by one debounced flush`() {
        val file = temporaryDirectory.resolve("debounced.json")
        val service = CrewService(file, 100)
        try {
            val owner = UUID.randomUUID()
            val crew = service.create(owner, "Quick Changes")
            service.grantKnowledge(owner, "craftory:page/canister")
            service.setTier(crew.id, 2)

            assertEquals(0, service.persistenceWriteCount)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (service.persistenceWriteCount == 0 && System.nanoTime() < deadline) {
                Thread.sleep(5)
            }
            assertEquals(1, service.persistenceWriteCount)
            assertTrue(Files.exists(file))
        } finally {
            service.close()
        }
    }
}
