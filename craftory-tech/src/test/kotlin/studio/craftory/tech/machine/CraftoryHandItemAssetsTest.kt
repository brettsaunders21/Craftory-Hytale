package studio.craftory.tech.machine

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class CraftoryHandItemAssetsTest {
    @Test
    fun `every non-block Craftory item has an existing model and texture`() {
        val roots = resourceRoots()
        assertTrue(roots.any { it.toString().contains("craftory-core") }, "Core item resources were not found")
        assertTrue(roots.any { it.toString().contains("craftory-tech") }, "Tech item resources were not found")
        val zipPath = Path.of(System.getProperty("user.home"), ".gradle/caches/hytale-assets/release-0.7.4-Assets.zip")
        val vanillaZipPresent = Files.isRegularFile(zipPath)
        val vanillaEntries =
            if (vanillaZipPresent) {
                ZipFile(zipPath.toFile()).use { zip ->
                    zip
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .toSet()
                }
            } else {
                emptySet()
            }
        val errors = roots.flatMap { itemErrors(it, vanillaEntries, allowBookModelFallback = !vanillaZipPresent) }
        assertFalse(errors.isNotEmpty(), errors.joinToString("\n"))
    }

    private fun resourceRoots(): List<Path> {
        val cwd = Path.of("").toAbsolutePath()
        return listOf(
            cwd.resolve("src/main/resources"),
            cwd.resolve("../craftory-core/src/main/resources"),
            cwd.resolve("craftory-tech/src/main/resources"),
            cwd.resolve("craftory-core/src/main/resources"),
        ).map { it.normalize() }
            .distinct()
            .filter { Files.isDirectory(it.resolve("Server/Item/Items/Craftory")) }
    }

    private fun assetExists(
        resources: Path,
        path: String,
        vanillaEntries: Set<String>,
    ): Boolean =
        Files.isRegularFile(resources.resolve("Common").resolve(path)) ||
            "Common/$path" in vanillaEntries

    private fun itemErrors(
        resources: Path,
        vanillaEntries: Set<String>,
        allowBookModelFallback: Boolean,
    ): List<String> {
        val itemDirectory = resources.resolve("Server/Item/Items/Craftory")
        return Files.list(itemDirectory).use { paths ->
            paths
                .filter {
                    it.fileName.toString().startsWith("Craftory_") &&
                        it.fileName.toString().endsWith(".json")
                }.toList()
                .flatMap { itemErrors(it, resources, vanillaEntries, allowBookModelFallback) }
        }
    }

    private fun itemErrors(
        path: Path,
        resources: Path,
        vanillaEntries: Set<String>,
        allowBookModelFallback: Boolean,
    ): List<String> {
        val item = Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonObject }
        if (item.has("BlockType") && !item.get("BlockType").isJsonNull) return emptyList()

        val id = path.fileName.toString().removeSuffix(".json")
        val model = item.get("Model")?.takeIf { it.isJsonPrimitive }?.asString
        val texture = item.get("Texture")?.takeIf { it.isJsonPrimitive }?.asString
        val errors = mutableListOf<String>()
        if (model.isNullOrBlank()) {
            errors += "$id is missing Model"
        } else if (!assetExists(resources, model, vanillaEntries) &&
            !(allowBookModelFallback && model == "Items/Weapons/Spellbook/Book.blockymodel")
        ) {
            errors += "$id model does not exist: $model"
        }
        if (texture.isNullOrBlank()) {
            errors += "$id is missing Texture"
        } else if (!assetExists(resources, texture, vanillaEntries)) {
            errors += "$id texture does not exist: $texture"
        }
        return errors
    }
}
