@file:Suppress("ComplexCondition", "CyclomaticComplexMethod")

package studio.craftory.engine.transport

data class TransitItemRecord(
    val id: Long,
    val itemKey: Int,
    val quantity: Int,
    val colour: Int,
    val fromPos: Long,
    val toPos: Long,
    val progress: Int,
    val originPos: Long,
    val itemId: String? = null,
)

object TransitItemCodec {
    private const val VERSION = "2"
    private const val LEGACY_VERSION = "1"
    private const val NONE_ORIGIN = "-"
    private const val NONE_ITEM_ID = "-"

    fun encode(records: List<TransitItemRecord>): String {
        if (records.isEmpty()) return "$VERSION;"
        return buildString {
            append(VERSION)
            append(';')
            records.forEachIndexed { index, record ->
                if (index > 0) append(';')
                append(record.id.toString(36))
                append(',')
                append(record.itemKey.toString(36))
                append(',')
                append(record.quantity.toString(36))
                append(',')
                append(record.colour.toString(36))
                append(',')
                append(record.fromPos.toString(36))
                append(',')
                append(record.toPos.toString(36))
                append(',')
                append(record.progress.toString(36))
                append(',')
                if (record.originPos == Long.MIN_VALUE) {
                    append(NONE_ORIGIN)
                } else {
                    append(record.originPos.toString(36))
                }
                append(',')
                append(record.itemId?.let(::encodeItemId) ?: NONE_ITEM_ID)
            }
        }
    }

    fun decode(encoded: String): List<TransitItemRecord> {
        val versionEnd = encoded.indexOf(';')
        if (versionEnd < 0) return emptyList()
        val version = encoded.substring(0, versionEnd)
        if (version != VERSION && version != LEGACY_VERSION) return emptyList()
        if (versionEnd == encoded.lastIndex) return emptyList()
        val result = ArrayList<TransitItemRecord>()
        var start = versionEnd + 1
        while (start <= encoded.length) {
            val end = encoded.indexOf(';', start).let { if (it < 0) encoded.length else it }
            if (end > start) {
                parseRecord(encoded.substring(start, end), version == VERSION)?.let(result::add)
            }
            if (end == encoded.length) break
            start = end + 1
        }
        return result
    }

    private fun parseRecord(
        encoded: String,
        hasItemId: Boolean,
    ): TransitItemRecord? {
        val fields = encoded.split(',')
        if (fields.size != if (hasItemId) FIELD_COUNT else LEGACY_FIELD_COUNT) return null
        val id = fields[0].toLongOrNull(36) ?: return null
        val key = fields[1].toIntOrNull(36) ?: return null
        val quantity = fields[2].toIntOrNull(36) ?: return null
        val colour = fields[3].toIntOrNull(36) ?: return null
        val from = fields[4].toLongOrNull(36) ?: return null
        val to = fields[5].toLongOrNull(36) ?: return null
        val progress = fields[6].toIntOrNull(36) ?: return null
        val origin = if (fields[7] == NONE_ORIGIN) Long.MIN_VALUE else fields[7].toLongOrNull(36) ?: return null
        val itemId =
            if (!hasItemId || fields[8] == NONE_ITEM_ID) {
                null
            } else {
                decodeItemId(fields[8]) ?: return null
            }
        if (id <= 0 || key < 0 || quantity <= 0 || colour !in 0..4 || progress !in 0..ItemTransitGraph.SEGMENT_LENGTH) {
            return null
        }
        return TransitItemRecord(id, key, quantity, colour, from, to, progress, origin, itemId)
    }

    private fun encodeItemId(itemId: String): String =
        java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(itemId.toByteArray(Charsets.UTF_8))

    private fun decodeItemId(itemId: String): String? =
        runCatching {
            String(
                java.util.Base64
                    .getUrlDecoder()
                    .decode(itemId),
                Charsets.UTF_8,
            )
        }.getOrNull()?.takeIf(String::isNotBlank)

    private const val FIELD_COUNT = 9
    private const val LEGACY_FIELD_COUNT = 8
}

object PackedPosition {
    fun pack(
        x: Int,
        y: Int,
        z: Int,
    ): Long =
        ((x.toLong() and XZ_MASK) shl 38) or
            ((y.toLong() and Y_MASK) shl 26) or
            (z.toLong() and XZ_MASK)

    fun x(position: Long): Int = (position shr 38).toInt()

    fun y(position: Long): Int = ((position shr 26) and Y_MASK).toInt().let { (it shl 20) shr 20 }

    fun z(position: Long): Int = (position shl 38 shr 38).toInt()

    private const val XZ_MASK = 0x3ffffffL
    private const val Y_MASK = 0xfffL
}
