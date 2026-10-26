package studio.craftory.core.api.multiblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.craftory.core.api.BlockPos

class MultiblockGeometryTest {
    private val offset = BlockPos(1, 2, -3)

    @Test
    fun `north keeps the canonical offset`() {
        assertEquals(BlockPos(1, 2, -3), MultiblockGeometry.rotate(offset, 0))
    }

    @Test
    fun `quarter turn matches Hytale rotateY ninety`() {
        assertEquals(BlockPos(-3, 2, -1), MultiblockGeometry.rotate(offset, 1))
    }

    @Test
    fun `half turn mirrors x and z`() {
        assertEquals(BlockPos(-1, 2, 3), MultiblockGeometry.rotate(offset, 2))
    }

    @Test
    fun `three quarter turn matches Hytale rotateY two seventy`() {
        assertEquals(BlockPos(3, 2, 1), MultiblockGeometry.rotate(offset, 3))
        assertEquals(MultiblockGeometry.rotate(offset, 3), MultiblockGeometry.rotate(offset, -1))
    }

    @Test
    fun `world parts follow hammer order and controller position`() {
        val template =
            MultiblockTemplate(
                "test:order",
                listOf(
                    TemplatePart(BlockPos(0, 1, 0), setOf("Top"), "Top"),
                    TemplatePart(BlockPos(1, 0, 0), setOf("East"), "East"),
                    TemplatePart(BlockPos(-1, 0, 0), setOf("West"), "West"),
                ),
            )
        val parts = MultiblockGeometry.worldParts(template, BlockPos(10, 64, 10), 2)
        assertEquals(listOf("East", "West", "Top"), parts.map { it.part.itemId })
        assertEquals(BlockPos(9, 64, 10), parts[0].position)
    }

    @Test
    fun `register enforces the seven by seven footprint`() {
        val registry = MultiblockRegistry()
        val fits =
            MultiblockTemplate(
                "test:fits",
                listOf(
                    TemplatePart(BlockPos(-3, 0, -3), setOf("A"), "A"),
                    TemplatePart(BlockPos(3, 6, 3), setOf("A"), "A"),
                ),
            )
        assertNotNull(registry.register(fits))
        val tooWide =
            MultiblockTemplate(
                "test:wide",
                listOf(TemplatePart(BlockPos(7, 0, 0), setOf("A"), "A")),
            )
        assertThrows<IllegalArgumentException> { registry.register(tooWide) }
        assertThrows<IllegalStateException> { registry.register(fits) }
    }

    @Test
    fun `parts cannot overlap the controller`() {
        assertThrows<IllegalArgumentException> { TemplatePart(BlockPos(0, 0, 0), setOf("A"), "A") }
    }
}
