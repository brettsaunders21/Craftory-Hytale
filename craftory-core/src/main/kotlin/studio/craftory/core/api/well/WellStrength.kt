package studio.craftory.core.api.well

enum class WellStrength(
    val maxGrade: Int,
) {
    FLICKER(1),
    STEADY(2),
    ROARING(3),
    PRIMAL(3),
}
