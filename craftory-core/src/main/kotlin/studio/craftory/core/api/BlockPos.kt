package studio.craftory.core.api

data class BlockPos(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun pack(): Long {
        require(x in MIN_XZ..MAX_XZ) { "x out of signed 26-bit range" }
        require(z in MIN_XZ..MAX_XZ) { "z out of signed 26-bit range" }
        require(y in MIN_Y..MAX_Y) { "y out of signed 12-bit range" }
        return ((x.toLong() and XZ_MASK) shl 38) or
            ((y.toLong() and Y_MASK) shl 26) or
            (z.toLong() and XZ_MASK)
    }

    companion object {
        private const val XZ_MASK = 0x3ffffffL
        private const val Y_MASK = 0xfffL
        private const val MIN_XZ = -(1 shl 25)
        private const val MAX_XZ = (1 shl 25) - 1
        private const val MIN_Y = -(1 shl 11)
        private const val MAX_Y = (1 shl 11) - 1

        fun pack(
            x: Int,
            y: Int,
            z: Int,
        ): Long = BlockPos(x, y, z).pack()

        fun unpack(value: Long): BlockPos {
            val x = (value shr 38).toInt()
            val y = ((value shr 26) and Y_MASK).toInt().let { (it shl 20) shr 20 }
            val z = (value shl 38 shr 38).toInt()
            return BlockPos(x, y, z)
        }
    }
}
