package studio.craftory.core.api.well

enum class Temperament(
    val id: String,
    val tint: String,
) {
    SPARK("spark", "#5ff3ff"),
    EMBER("ember", "#ff8a2b"),
    TIDE("tide", "#3f8cff"),
    GROWTH("growth", "#5fd35a"),
    STONE("stone", "#a58c6a"),
    GALE("gale", "#d6f2ff"),
    CLANK("clank", "#8bff3a"),
    HOLLOW("hollow", "#6a2a9a"),
    ;

    companion object {
        fun fromId(id: String): Temperament? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
