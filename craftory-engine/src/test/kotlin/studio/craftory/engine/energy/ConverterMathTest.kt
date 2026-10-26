package studio.craftory.engine.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConverterMathTest {
    @Test
    fun `skim rounds positive production up`() {
        assertEquals(0L, ConverterMath.skim(0, 5))
        assertEquals(0L, ConverterMath.skim(-10, 5))
        assertEquals(1L, ConverterMath.skim(1, 5))
        assertEquals(1L, ConverterMath.skim(20, 5))
        assertEquals(2L, ConverterMath.skim(21, 5))
        assertEquals(5L, ConverterMath.skim(100, 5))
    }

    @Test
    fun `skim and conversion remain safe for large values`() {
        assertEquals(Long.MAX_VALUE, ConverterMath.skim(Long.MAX_VALUE, 100))
        assertEquals(Long.MAX_VALUE / 4, ConverterMath.convert(Long.MAX_VALUE, 25))
        val expected = (Long.MAX_VALUE / 100) * 40 + (Long.MAX_VALUE % 100) * 40 / 100
        assertEquals(expected, ConverterMath.convert(Long.MAX_VALUE, 40))
    }

    @Test
    fun `conversion floors and enforces its rate independently`() {
        assertEquals(0L, ConverterMath.convert(0, 30))
        assertEquals(0L, ConverterMath.convert(3, 30))
        assertEquals(1L, ConverterMath.convert(4, 30))
        assertEquals(2L, ConverterMath.convert(10, 25))
        assertEquals(4L, ConverterMath.convert(10, 40))
        assertThrows(IllegalArgumentException::class.java) { ConverterMath.convert(100, 24) }
        assertThrows(IllegalArgumentException::class.java) { ConverterMath.convert(100, 41) }
        assertThrows(IllegalArgumentException::class.java) { ConverterMath.convert(-1, 30) }
    }

    @Test
    fun `catalysis subtracts rounded up loss`() {
        assertEquals(0L, ConverterMath.catalyse(0))
        assertEquals(85L, ConverterMath.catalyse(100))
        assertEquals(34L, ConverterMath.catalyse(40))
        assertEquals(0L, ConverterMath.catalyse(1))
        assertEquals(5L, ConverterMath.catalyse(7))
        assertEquals(7L, ConverterMath.catalyse(7, 0))
        assertEquals(0L, ConverterMath.catalyse(7, 100))
    }

    @Test
    fun `catalysis is overflow safe and validates its inputs`() {
        val quotient = Long.MAX_VALUE / 100
        val remainder = Long.MAX_VALUE % 100
        val loss = quotient * 15 + (remainder * 15 + 99) / 100
        assertEquals(Long.MAX_VALUE - loss, ConverterMath.catalyse(Long.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { ConverterMath.catalyse(-1) }
        assertThrows(IllegalArgumentException::class.java) { ConverterMath.catalyse(1, -1) }
        assertThrows(IllegalArgumentException::class.java) { ConverterMath.catalyse(1, 101) }
    }
}
