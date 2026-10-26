package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GutterpipeMaskTest {
    @Test
    fun `mask face bits are world ordered positive and negative xyz`() {
        assertEquals(listOf(1, 2, 4, 8, 16, 32), GutterpipeMask.FACES.map { it.mask })
        assertEquals(
            listOf(
                Triple(1, 0, 0),
                Triple(-1, 0, 0),
                Triple(0, 1, 0),
                Triple(0, -1, 0),
                Triple(0, 0, 1),
                Triple(0, 0, -1),
            ),
            GutterpipeMask.FACES.map { Triple(it.dx, it.dy, it.dz) },
        )
    }

    @Test
    fun `state names cover all 64 six-bit masks`() {
        assertEquals("C00", GutterpipeMask.stateName(0))
        assertEquals("C01", GutterpipeMask.stateName(1))
        assertEquals("C09", GutterpipeMask.stateName(9))
        assertEquals("C63", GutterpipeMask.stateName(63))
        assertEquals((0..63).map { "C${it.toString().padStart(2, '0')}" }, (0..63).map(GutterpipeMask::stateName))
    }
}
