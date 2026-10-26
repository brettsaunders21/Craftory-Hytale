package studio.craftory.engine.transport

/** Same signed 26/12/26-bit layout as Core BlockPos. */
data class TubeBlock(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun pack(): Long {
        require(x in MIN_XZ..MAX_XZ && z in MIN_XZ..MAX_XZ)
        require(y in MIN_Y..MAX_Y)
        return ((x.toLong() and 0x3ffffffL) shl 38) or
            ((y.toLong() and 0xfffL) shl 26) or (z.toLong() and 0x3ffffffL)
    }

    internal fun neighbours(): List<Long> =
        buildList {
            if (x < MAX_XZ) add(copy(x = x + 1).pack())
            if (x > MIN_XZ) add(copy(x = x - 1).pack())
            if (y < MAX_Y) add(copy(y = y + 1).pack())
            if (y > MIN_Y) add(copy(y = y - 1).pack())
            if (z < MAX_XZ) add(copy(z = z + 1).pack())
            if (z > MIN_XZ) add(copy(z = z - 1).pack())
        }

    companion object {
        private const val MIN_XZ = -(1 shl 25)
        private const val MAX_XZ = (1 shl 25) - 1
        private const val MIN_Y = -(1 shl 11)
        private const val MAX_Y = (1 shl 11) - 1

        fun unpack(value: Long): TubeBlock =
            TubeBlock(
                (value shr 38).toInt(),
                (value shl 26 shr 52).toInt(),
                (value shl 38 shr 38).toInt(),
            )
    }
}
