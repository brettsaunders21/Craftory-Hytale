package studio.craftory.tech.machine

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.tech.CraftoryIds
import java.nio.file.Files
import java.nio.file.Path

class BlockEntityComponentIdsTest {
    @Test
    fun `item block entity component keys are registered or explicitly supported`() {
        val itemDirectory =
            listOf(
                Path.of("src/main/resources/Server/Item/Items"),
                Path.of("craftory-tech/src/main/resources/Server/Item/Items"),
            ).firstOrNull(Files::isDirectory)
        assertTrue(itemDirectory != null, "Tech item JSON resource directory was not found")

        val allowed =
            techComponentIds() +
                setOf(
                    "Craftory_EnergyMember",
                    "Craftory_FluidTank",
                    "Craftory_FluidPipe",
                    "Craftory_ItemPipe",
                    "Craftory_MultiblockController",
                    "CraftoryTubeNode",
                    "BenchBlock",
                    "ProcessingBenchBlock",
                )
        val jsonFiles =
            Files.walk(itemDirectory!!).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .toList()
            }
        val unknown =
            jsonFiles.flatMap { path ->
                componentKeys(path)
                    .filterNot(allowed::contains)
                    .map { key -> "${path.fileName}: $key" }
            }

        assertTrue(unknown.isEmpty(), "Unregistered block-entity component keys: ${unknown.joinToString()}")
        assertTrue(
            componentKeys(itemDirectory.resolve("Craftory/Craftory_Gutter_Boiler.json"))
                .contains(CraftoryIds.BOILER_COMPONENT),
            "Boiler JSON must use ${CraftoryIds.BOILER_COMPONENT}",
        )
        assertTrue(
            componentKeys(itemDirectory.resolve("Craftory/Craftory_Gasbag.json"))
                .contains(CraftoryIds.GASBAG_COMPONENT),
            "Gasbag JSON must use ${CraftoryIds.GASBAG_COMPONENT}",
        )
    }

    private fun techComponentIds(): Set<String> =
        CraftoryIds::class.java.declaredFields
            .filter { it.name.endsWith("_COMPONENT") && it.type == String::class.java }
            .mapNotNull { it.get(null) as? String }
            .toSet()

    private fun componentKeys(path: Path): Set<String> {
        val root =
            Files.newBufferedReader(path).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        val blockEntityComponents =
            root
                .getAsJsonObject("BlockType")
                ?.getAsJsonObject("BlockEntity")
                ?.getAsJsonObject("Components")
                ?: return emptySet()
        return blockEntityComponents.keySet()
    }
}
