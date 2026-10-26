package studio.craftory.tech.machine

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CraftoryResourceSourceTest {
    @Test
    fun `every grit table entry has its matching furnace recipe and item`() {
        val itemDirectory = resourceDirectory("Server/Item/Items/Craftory")
        val itemFiles = jsonFiles(itemDirectory)
        val itemsById = itemFiles.associate { it.fileName.toString().removeSuffix(".json") to parse(it) }
        val gritIds = GritTable.definitions.mapTo(mutableSetOf()) { it.gritItemId }

        GritTable.definitions.forEach { definition ->
            val item = itemsById[definition.gritItemId]
            assertTrue(item != null, "${definition.gritItemId} has no Craftory item JSON")
            val recipe = item?.getAsJsonObject("Recipe")
            assertTrue(recipe != null, "${definition.gritItemId} has no processing recipe")
            val inputs = recipe?.getAsJsonArray("Input").orEmpty()
            val outputs = recipe?.getAsJsonArray("Output").orEmpty()
            val requirements = recipe?.getAsJsonArray("BenchRequirement").orEmpty()
            val inputMatches =
                inputs.any { it.asJsonObject.get("ItemId")?.asString == definition.gritItemId }
            val expectedBar = "Ingredient_Bar_${definition.metal}"
            val outputMatches = outputs.any { it.asJsonObject.get("ItemId")?.asString == expectedBar }
            val furnaceRecipe =
                requirements.any {
                    val requirement = it.asJsonObject
                    requirement.get("Type")?.asString == "Processing" &&
                        requirement.get("Id")?.asString == "Furnace"
                }
            assertTrue(
                inputMatches && outputMatches && furnaceRecipe,
                "${definition.gritItemId} must process into $expectedBar in a Furnace",
            )
        }

        itemFiles
            .filter { it.fileName.toString().startsWith("Craftory_Grit_") }
            .forEach { path ->
                val gritId = path.fileName.toString().removeSuffix(".json")
                assertTrue(gritId in gritIds, "$gritId has no GritTable entry")
            }
    }

    @Test
    fun `every Craftory item has a survival source`() {
        val itemFiles = jsonFiles(resourceDirectory("Server/Item/Items/Craftory"))
        val itemIds = itemFiles.map { it.fileName.toString().removeSuffix(".json") }
        val barterOutputs =
            jsonFiles(resourceDirectory("Server/BarterShops"))
                .flatMap { path ->
                    parse(path)
                        .getAsJsonArray("TradeSlots")
                        .mapNotNull { slot ->
                            slot.asJsonObject
                                .getAsJsonObject("Trade")
                                ?.getAsJsonObject("Output")
                                ?.get("ItemId")
                                ?.asString
                        }
                }.toSet()
        val dropFiles = jsonFiles(resourceDirectory("Server/Drops"))
        val gritIds = GritTable.definitions.mapTo(mutableSetOf()) { it.gritItemId }
        val allowlist =
            mapOf(
                "Craftory_Gob_Jar_Filled" to "made by capturing a well in a Gob-Jar",
            )

        val missingSources =
            itemFiles.mapNotNull { path ->
                val id = path.fileName.toString().removeSuffix(".json")
                if (id in allowlist) return@mapNotNull null
                val item = parse(path)
                val hasRecipe = item.has("Recipe")
                val hasBarterOutput = id in barterOutputs
                val hasDropReference = dropFiles.any { containsString(parse(it), id) }
                val hasGritOutput = id in gritIds
                if (listOf(hasRecipe, hasBarterOutput, hasDropReference, hasGritOutput).any { it }) {
                    null
                } else {
                    "$id has no Recipe, BarterShop output, Drop reference, or GritTable output"
                }
            }

        assertTrue(missingSources.isEmpty(), missingSources.joinToString("\n"))
        assertTrue(
            allowlist.keys.all(itemIds::contains),
            "Every survival-source allowlist id must have an item JSON",
        )
    }

    private fun resourceDirectory(relativePath: String): Path =
        Path.of(checkNotNull(javaClass.classLoader.getResource(relativePath)) { "$relativePath is missing" }.toURI())

    private fun jsonFiles(directory: Path): List<Path> =
        Files.list(directory).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted()
                .toList()
        }

    private fun parse(path: Path): JsonObject =
        Files.newBufferedReader(path).use { reader -> JsonParser.parseReader(reader).asJsonObject }

    private fun containsString(
        element: JsonElement,
        expected: String,
    ): Boolean =
        when {
            element.isJsonPrimitive -> element.asJsonPrimitive.isString && element.asString == expected
            element.isJsonArray -> element.asJsonArray.any { containsString(it, expected) }
            element.isJsonObject -> element.asJsonObject.entrySet().any { containsString(it.value, expected) }
            else -> false
        }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()
}
