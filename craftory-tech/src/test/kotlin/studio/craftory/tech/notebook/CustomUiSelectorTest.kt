package studio.craftory.tech.notebook

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CustomUiSelectorTest {
    @Test
    fun `Craftory UI selectors reference declared ids with descendant syntax`() {
        val root = techRoot()
        val uiRoot = root.resolve("src/main/resources/Common/UI/Custom/Craftory")
        val uiFiles = Files.walk(uiRoot).use { paths -> paths.filter { it.toString().endsWith(".ui") }.toList() }
        val declaredIds =
            uiFiles
                .flatMap { file ->
                    Regex("""#([A-Za-z][A-Za-z0-9_]*)\s*\{""")
                        .findAll(Files.readString(file))
                        .map { it.groupValues[1] }
                        .toList()
                }.toSet()
        val kotlinRoot = root.resolve("src/main/kotlin")
        val sourceFiles =
            Files.walk(kotlinRoot).use { paths ->
                paths.filter { it.toString().endsWith(".kt") }.toList()
            }
        val violations = sourceFiles.flatMap { selectorViolations(it, declaredIds) }

        assertTrue(violations.none { it.startsWith("missing:") }, violations.joinToString())
        assertTrue(violations.none { it.startsWith("chained:") }, violations.joinToString())
    }

    private fun selectorViolations(
        file: Path,
        declaredIds: Set<String>,
    ): List<String> {
        val source = Files.readString(file)
        val calls = Regex("""\.(?:set|append|addEventBinding)\s*\(""").findAll(source)
        return calls
            .flatMap { call ->
                val body = callBody(source, call.range.last)
                stringLiterals.findAll(body).flatMap { literal ->
                    selectorViolations(file.fileName.toString(), literal.groupValues[1], declaredIds)
                }
            }.toList()
    }

    private fun selectorViolations(
        fileName: String,
        selector: String,
        declaredIds: Set<String>,
    ): List<String> {
        val missing =
            selectors
                .findAll(selector)
                .filterNot { it.value.matches(hexColor) }
                .map { it.groupValues[1] }
                .filter { it !in declaredIds }
                .map { "missing: $fileName #$it" }
        val chained =
            dotChainedIds
                .findAll(selector)
                .filter { it.groupValues[2] in declaredIds }
                .map { "chained: $fileName ${it.value}" }
        return missing.toList() + chained.toList()
    }

    private fun callBody(
        source: String,
        openingIndex: Int,
    ): String {
        var depth = 1
        var inString = false
        var escaped = false
        for (index in openingIndex + 1 until source.length) {
            val char = source[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
            } else {
                when (char) {
                    '"' -> {
                        inString = true
                    }

                    '(' -> {
                        depth++
                    }

                    ')' -> {
                        depth--
                        if (depth == 0) return source.substring(openingIndex + 1, index)
                    }
                }
            }
        }
        return ""
    }

    companion object {
        private val stringLiterals = Regex(""""((?:\\.|[^"\\])*)"""")
        private val selectors = Regex("""#([A-Za-z][A-Za-z0-9_]*)(?:\[[^\]]*])?""")
        private val hexColor = Regex("""#[0-9A-Fa-f]{6,8}""")
        private val dotChainedIds =
            Regex("""#([A-Za-z][A-Za-z0-9_]*)\.([A-Za-z][A-Za-z0-9_]*)""")
    }

    private fun techRoot(): Path =
        listOf(Path.of("."), Path.of("craftory-tech"))
            .firstOrNull { Files.isDirectory(it.resolve("src/main/resources/Common/UI/Custom/Craftory")) }
            ?: error("Craftory Tech resource root was not found")
}
