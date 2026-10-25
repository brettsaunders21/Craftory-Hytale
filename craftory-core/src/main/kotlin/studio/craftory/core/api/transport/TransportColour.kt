package studio.craftory.core.api.transport

enum class TransportColour(
    val value: Int,
) {
    NONE(0),
    RED(1),
    BLUE(2),
    GREEN(3),
    YELLOW(4),
    ;

    companion object {
        fun fromValue(value: Int): TransportColour = entries.firstOrNull { it.value == value } ?: NONE
    }
}
