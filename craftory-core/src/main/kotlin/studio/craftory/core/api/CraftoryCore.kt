package studio.craftory.core.api

import java.util.concurrent.ConcurrentSkipListMap

object CraftoryCore {
    private val modules = ConcurrentSkipListMap<String, String>()

    fun registerModule(
        name: String,
        version: String,
    ) {
        require(name.isNotBlank()) { "Module name must not be blank" }
        require(version.isNotBlank()) { "Module version must not be blank" }
        modules[name] = version
    }

    fun loadedModules(): List<String> = modules.map { (name, version) -> "$name v$version" }
}
