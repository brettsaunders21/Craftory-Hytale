package studio.craftory.core.api.multiblock

/** How unfinished frames show their missing parts: `ghost-mode` in the Core config. */
enum class GhostMode(
    val configValue: String,
) {
    PREVIEW("preview"),
    PARTICLES("particles"),
    BOTH("both"),
    ;

    val showsPreview: Boolean get() = this != PARTICLES
    val showsParticles: Boolean get() = this != PREVIEW

    companion object {
        val DEFAULT = BOTH

        fun parse(value: String?): GhostMode =
            entries.firstOrNull { it.configValue.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
