package studio.craftory.tech.machine.jank

enum class BodgePart(
    val itemId: String,
) {
    BOOM_VALVE("Craftory_Boom_Valve"),
    GUTTER_GEAR("Craftory_Gutter_Gear"),
    SCRAP_PLATING("Craftory_Scrap_Plating_Bodge"),
    SQUAWK_BOX("Craftory_Squawk_Box"),
    MUFFLER("Craftory_Muffler"),
    ;

    companion object {
        fun fromItemId(id: String?): BodgePart? = entries.firstOrNull { it.itemId == id }
    }
}

data class BodgeModifiers(
    val speed: Double,
    val energyUse: Double,
    val jankRate: Double,
    val squawk: Boolean,
    val muffled: Boolean,
)

fun bodgeModifiers(parts: Collection<BodgePart>): BodgeModifiers {
    var speed = 1.0
    var energyUse = 1.0
    var jankRate = 1.0
    var squawk = false
    var muffled = false

    parts.forEach { part ->
        when (part) {
            BodgePart.BOOM_VALVE -> {
                speed *= 1.25
                jankRate *= 1.5
            }

            BodgePart.GUTTER_GEAR -> {
                energyUse *= 0.8
            }

            BodgePart.SCRAP_PLATING -> {
                jankRate *= 0.5
            }

            BodgePart.SQUAWK_BOX -> {
                squawk = true
            }

            BodgePart.MUFFLER -> {
                muffled = true
            }
        }
    }

    return BodgeModifiers(speed, energyUse, jankRate, squawk, muffled)
}
