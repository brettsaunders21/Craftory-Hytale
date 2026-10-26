package studio.craftory.tech.well

import studio.craftory.core.api.well.Temperament

internal object HarmonyRules {
    fun harmony(matchingCount: Int): Int =
        when {
            matchingCount <= 0 -> 1
            matchingCount <= 2 -> 2
            matchingCount <= 5 -> 3
            matchingCount <= 9 -> 4
            else -> 5
        }
}

internal object TasteBlocks {
    private val blocksByTaste =
        mapOf(
            "Ingredient_Scraps" to setOf("Craftory_Scrap_Plating"),
        )

    fun blocksFor(taste: String): Set<String> = blocksByTaste[taste] ?: setOf(taste)

    fun matches(
        taste: String,
        blockId: String,
    ): Boolean = blockId in blocksFor(taste)
}
