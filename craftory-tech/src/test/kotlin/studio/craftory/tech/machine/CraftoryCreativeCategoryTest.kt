package studio.craftory.tech.machine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CraftoryCreativeCategoryTest {
    @Test
    fun `all Craftory items use localized categories from the creative tab`() {
        val categoryAsset = parse(techResource("Server/Item/Category/CreativeLibrary/Craftory.json"))
        val children = categoryAsset.getAsJsonArray("Children").map { it.asJsonObject }
        val childIds = children.map { it.get("Id").asString }.toSet()
        assertTrue(childIds.size == children.size, "Craftory category child IDs must be unique")

        val itemFiles = itemDirectories().flatMap(::jsonFiles)
        assertTrue(itemFiles.isNotEmpty(), "Craftory item JSONs were not found")
        val invalidCategories =
            itemFiles.flatMap { path ->
                val itemId = path.fileName.toString().removeSuffix(".json")
                val categories = parse(path).getAsJsonArray("Categories")
                when {
                    categories == null -> {
                        listOf("$itemId has no Categories")
                    }

                    categories.size() != 1 -> {
                        listOf("$itemId must have exactly one category, found ${categories.size()}")
                    }

                    else -> {
                        val category = categories[0].asString
                        when {
                            !category.startsWith("Craftory.") -> {
                                listOf("$itemId must use a Craftory category, found $category")
                            }

                            category.removePrefix("Craftory.") !in childIds -> {
                                listOf("$itemId references missing child ${category.removePrefix("Craftory.")}")
                            }

                            else -> {
                                emptyList()
                            }
                        }
                    }
                }
            }
        assertTrue(invalidCategories.isEmpty(), invalidCategories.joinToString("\n"))

        val languageKeys =
            Files
                .readAllLines(techResource("Server/Languages/en-US/server.lang"))
                .map { it.substringBefore('=').trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        val requiredLanguageKeys =
            children.map { it.get("Name").asString.removePrefix("server.") } +
                "ui.itemcategory.craftory"
        val missingLanguageKeys = requiredLanguageKeys.filterNot(languageKeys::contains)
        assertTrue(
            missingLanguageKeys.isEmpty(),
            "Missing Craftory category lang keys: ${missingLanguageKeys.joinToString()}",
        )
    }

    @Test
    fun `all Craftory items use valid survival qualities`() {
        val itemFiles = itemDirectories().flatMap(::jsonFiles)
        val allowedQualities = setOf("Common", "Uncommon", "Rare", "Technical")
        val invalidQualities =
            itemFiles.flatMap { path ->
                val itemId = path.fileName.toString().removeSuffix(".json")
                val item = parse(path)
                when {
                    !item.has("Quality") || item.get("Quality").isJsonNull -> {
                        listOf("$itemId has no Quality")
                    }

                    item.get("Quality").asString !in allowedQualities -> {
                        listOf(
                            "$itemId has invalid Quality ${item.get("Quality").asString}",
                        )
                    }

                    else -> {
                        emptyList()
                    }
                }
            }
        assertTrue(invalidQualities.isEmpty(), invalidQualities.joinToString("\n"))

        val technicalItems =
            itemFiles
                .filter { parse(it).get("Quality").asString == "Technical" }
                .map { it.fileName.toString().removeSuffix(".json") }
                .toSet()
        assertTrue(
            technicalItems == setOf("Craftory_Glimmerwell_Anchor"),
            "Technical quality is reserved for unholdable internal blocks: ${technicalItems.joinToString()}",
        )
    }

    private fun itemDirectories(): List<Path> =
        listOf(
            Path.of("src/main/resources/Server/Item/Items/Craftory"),
            Path.of("../craftory-core/src/main/resources/Server/Item/Items/Craftory"),
            Path.of("craftory-tech/src/main/resources/Server/Item/Items/Craftory"),
            Path.of("craftory-core/src/main/resources/Server/Item/Items/Craftory"),
        ).filter(Files::isDirectory).distinct()

    private fun techResource(relativePath: String): Path =
        listOf(
            Path.of("src/main/resources").resolve(relativePath),
            Path.of("craftory-tech/src/main/resources").resolve(relativePath),
        ).firstOrNull(Files::isRegularFile)
            ?: error("Tech resource $relativePath was not found")

    private fun jsonFiles(directory: Path): List<Path> =
        Files.list(directory).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted()
                .toList()
        }

    private fun parse(path: Path): JsonObject =
        Files.newBufferedReader(path).use { reader -> JsonParser.parseReader(reader).asJsonObject }
}
