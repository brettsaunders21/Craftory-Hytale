package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

private val allowedUiProperties =
    setOf(
        "Text",
        "TextSpans",
        "Value",
        "Visible",
        "Background",
        "ItemId",
        "AssetPath",
        "Style.TextColor",
        "Disabled",
    )

private val vanillaMacroIds =
    setOf(
        "Content", // Common.ui page macro content container.
        "Title", // Common.ui page macro title label.
        "TitleLabel", // Common.ui page macro title label target.
    )

internal fun validateSelector(
    selector: String,
    declaredIds: Set<String>,
    allowedIds: Set<String> = vanillaMacroIds,
): List<String> {
    val errors = mutableListOf<String>()
    val tokens = selector.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    tokens.forEachIndexed { index, rawToken ->
        if (rawToken.startsWith("$") || rawToken.startsWith("@")) return@forEachIndexed
        val token = rawToken.replace(Regex("\\[[^]]*]"), "")
        val match = Regex("^#([A-Za-z][A-Za-z0-9_]*)(?:\\.(.+))?$").matchEntire(token)
        if (match == null) {
            errors += "$selector: invalid selector token '$rawToken'"
            return@forEachIndexed
        }
        val id = match.groupValues[1]
        val path = match.groupValues[2].takeIf(String::isNotEmpty)
        if (index < tokens.lastIndex && path != null) {
            errors += "$selector: non-final selector token must be '#$id'"
        }
        if (path != null && path !in allowedUiProperties) {
            if (path.substringBefore('.') !in allowedUiProperties) {
                errors += "$selector: dot-chained nested id, use '#$id #${path.substringBefore('.')}'"
            } else {
                errors += "$selector: unsupported property path '$path'"
            }
        }
        if (id !in declaredIds && id !in allowedIds) {
            errors += "$selector: undeclared id '#$id'"
        }
    }
    return errors
}

internal fun validateUiSelectors(
    kotlinRoots: List<Path>,
    uiRoot: Path,
    languageFile: Path,
): List<String> {
    val uiFiles = regularFiles(uiRoot).filter { it.fileName.toString().endsWith(".ui") }
    val declaredIds =
        uiFiles
            .flatMap { path ->
                Regex("#([A-Z][A-Za-z0-9_]*)\\s*(?=\\{|:)")
                    .findAll(Files.readString(path))
                    .map {
                        it.groupValues[1]
                    }.toList()
            }.toSet()
    val errors = mutableListOf<String>()
    kotlinRoots
        .flatMap(::regularFiles)
        .filter { it.fileName.toString().endsWith(".kt") }
        .forEach { path ->
            val source = Files.readString(path)
            selectorLiterals(source).forEach { selector ->
                errors += validateSelector(selector, declaredIds).map { "$path: $it" }
            }
        }

    val languageKeys =
        Files
            .readAllLines(languageFile)
            .mapNotNull { line ->
                line.substringBefore('=').trim().takeIf { it.isNotEmpty() }
            }.toSet()
    uiFiles.forEach { path ->
        Regex("%(server\\.[A-Za-z0-9_.-]+)").findAll(Files.readString(path)).forEach { match ->
            val key = match.groupValues[1].removePrefix("server.")
            if (key !in languageKeys) errors += "$path: missing language key '$key'"
        }
    }
    val machineUi = kotlinRoots.flatMap(::regularFiles).firstOrNull { it.fileName.toString() == "MachineUi.kt" }
    if (machineUi != null) {
        Regex("""Message\.translation\("server\.([A-Za-z0-9_.-]+)"""")
            .findAll(Files.readString(machineUi))
            .forEach { match ->
                val key = match.groupValues[1]
                if (key !in languageKeys) errors += "$machineUi: missing language key '$key'"
            }
    }
    return errors
}

internal fun validateUiTranslationReferences(resourceRoots: List<Path>): List<String> {
    val languageReferences =
        resourceRoots
            .flatMap { regularFiles(it.resolve("Server/Languages/en-US")) }
            .filter { it.fileName.toString().endsWith(".lang") }
            .flatMap { path ->
                val prefix = path.fileName.toString().removeSuffix(".lang") + "."
                Files
                    .readAllLines(path)
                    .mapNotNull { line ->
                        val key = line.substringBefore('=').trim()
                        key.takeIf { '=' in line && it.isNotEmpty() }?.let { prefix + it }
                    }
            }.toSet()
    val errors = mutableListOf<String>()
    resourceRoots
        .flatMap(::regularFiles)
        .filter { it.fileName.toString().endsWith(".ui") }
        .forEach { path ->
            val source = Files.readString(path)
            Regex("""%([^\s;,)}\]"']+)""").findAll(source).forEach { match ->
                val reference = match.groupValues[1]
                val line = source.take(match.range.first).count { it == '\n' } + 1
                if (!Regex("^[A-Za-z0-9.]+$").matches(reference)) {
                    errors += "$path:$line: invalid translation reference '%$reference'"
                }
                if (reference !in languageReferences) {
                    errors += "$path:$line: undeclared translation reference '%$reference'"
                }
            }
        }
    return errors
}

private fun selectorLiterals(source: String): List<String> {
    val selectors = mutableListOf<String>()
    Regex(
        """\.(setObject|setNull|set|clear|remove|appendInline|insertBefore|append)\(\s*"([^"]+)"""",
        RegexOption.DOT_MATCHES_ALL,
    ).findAll(source).forEach { match ->
        val method = match.groupValues[1]
        val selector = match.groupValues[2]
        if (method != "append" || selector.startsWith("#") || selector.startsWith("$")) selectors += selector
    }
    Regex("""addEventBinding\([^,]+,\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        .findAll(source)
        .forEach { selectors += it.groupValues[1] }
    return selectors
}

private fun regularFiles(root: Path): List<Path> =
    if (!Files.isDirectory(root)) {
        emptyList()
    } else {
        Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).toList() }
    }

class UiSelectorsTest {
    @Test
    fun `all Kotlin UI selectors and language keys are declared`() {
        val root = repoRoot()
        val errors =
            validateUiSelectors(
                kotlinRoots =
                    listOf(
                        root.resolve("craftory-tech/src/main/kotlin"),
                        root.resolve("craftory-core/src/main/kotlin"),
                    ),
                uiRoot = root.resolve("craftory-tech/src/main/resources/Common/UI/Custom/Craftory"),
                languageFile = root.resolve("craftory-tech/src/main/resources/Server/Languages/en-US/server.lang"),
            )
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }

    @Test
    fun `all UI translation references are client-parseable and declared`() {
        val root = repoRoot()
        val resourceRoots =
            Files.list(root).use { modules ->
                modules
                    .filter { Files.isDirectory(it) && it.fileName.toString().startsWith("craftory-") }
                    .map { it.resolve("src/main/resources") }
                    .filter { Files.isDirectory(it) }
                    .toList()
            }
        val errors = validateUiTranslationReferences(resourceRoots)
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }

    @Test
    fun `selector validator rejects nested and undeclared ids`() {
        val declared = setOf("Row", "Name", "Destinations", "Go")

        assertTrue(validateSelector("#Row.Name.Text", declared).any { "dot-chained nested id" in it })
        assertTrue(validateSelector("#Nope.Text", declared).any { "undeclared id" in it })
        assertTrue(validateSelector("#Row.Name", declared).any { "dot-chained nested id" in it })
    }

    @Test
    fun `selector validator accepts descendant and indexed selectors`() {
        val declared = setOf("Row", "Name", "Destinations", "Go")

        assertTrue(validateSelector("#Row #Name.Text", declared).isEmpty())
        assertTrue(validateSelector("#Destinations[\$index] #Go.Text", declared).isEmpty())
    }
}

private fun repoRoot(): Path =
    generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isDirectory(it.resolve("craftory-tech/src/main/resources")) }
