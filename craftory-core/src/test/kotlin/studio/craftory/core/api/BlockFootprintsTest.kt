package studio.craftory.core.api

import org.joml.Vector3i
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockFootprintsTest {
    @Test
    fun `filler offsets resolve back to their origin`() {
        val fillerPosition = Vector3i(12, 9, 20)
        val filler = packFillerOffset(2, 1, -1)

        assertEquals(
            Vector3i(10, 8, 21),
            BlockFootprints.originFromFiller(fillerPosition.x, fillerPosition.y, fillerPosition.z, filler),
        )
    }

    @Test
    fun `non-filler positions resolve to themselves`() {
        assertEquals(Vector3i(12, 9, 20), BlockFootprints.originFromFiller(12, 9, 20, 0))
    }

    @Test
    fun `rotated footprint offsets map to unique world cells`() {
        val origin = Vector3i(10, 20, 30)
        val rotatedOffsets = listOf(Vector3i(0, 1, 0), Vector3i(1, 0, 0), Vector3i(1, 1, 0))

        assertEquals(
            listOf(Vector3i(10, 20, 30), Vector3i(10, 21, 30), Vector3i(11, 20, 30), Vector3i(11, 21, 30)),
            BlockFootprints.cellsAt(origin.x, origin.y, origin.z, rotatedOffsets),
        )
    }

    private fun packFillerOffset(
        x: Int,
        y: Int,
        z: Int,
    ): Int = (x and 31) or ((z and 31) shl 5) or ((y and 31) shl 10)
}
