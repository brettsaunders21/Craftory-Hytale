package studio.craftory.tech.machine

import com.google.gson.JsonParser
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MachineVentsTest {
    @Test
    fun `jank and bodge block items resolve to a vent or the fallback`() {
        val itemDirectory =
            listOf(
                Path.of("src/main/resources/Server/Item/Items"),
                Path.of("craftory-tech/src/main/resources/Server/Item/Items"),
            ).firstOrNull(Files::isDirectory)
        assertTrue(itemDirectory != null, "Tech item JSON resource directory was not found")

        val itemFiles =
            Files.walk(itemDirectory!!).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .toList()
            }
        val machineIds =
            itemFiles.mapNotNull { path ->
                val components = blockEntityComponents(path)
                if (components.any(JANK_AND_BODGE_COMPONENTS::contains)) {
                    path.fileName.toString().removeSuffix(".json")
                } else {
                    null
                }
            }
        assertTrue(machineIds.isNotEmpty(), "No CraftoryJank/CraftoryBodgeSlots block items were found")

        val x = 4
        val y = -2
        val z = 9
        val fallback = MachineVents.fallback(x, y, z)
        machineIds.forEach { id ->
            val vent = MachineVents.entries[id]
            val expected = vent?.let { Vector3d(x + it.x, y + it.y, z + it.z) } ?: fallback
            assertEquals(expected, MachineVents.position(id, x, y, z), id)
        }
    }

    @Test
    fun `unknown ids and failed block lookups use the shared fallback`() {
        val expected = Vector3d(4.5, -1.0, 9.5)
        assertEquals(expected, MachineVents.fallback(4, -2, 9))
        assertEquals(expected, MachineVents.position("Sloshtank", 4, -2, 9))
        assertEquals(expected, MachineVents.positionForBlockId(null, 4, -2, 9))
    }

    private fun blockEntityComponents(path: Path): Set<String> {
        val root =
            Files.newBufferedReader(path).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        val components =
            root
                .getAsJsonObject("BlockType")
                ?.getAsJsonObject("BlockEntity")
                ?.getAsJsonObject("Components")
                ?: return emptySet()
        return components.keySet()
    }

    private companion object {
        val JANK_AND_BODGE_COMPONENTS = setOf("CraftoryJank", "CraftoryBodgeSlots")
    }
}
