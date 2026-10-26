package studio.craftory.tech.machine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import studio.craftory.tech.CraftoryIds
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class GutterpipeModelTest {
    private val resources = resourceRoot()
    private val common = resources.resolve("Common")
    private val hitboxes = resources.resolve("Server/Item/Block/Hitboxes")
    private val item = parse(resources.resolve("Server/Item/Items/Craftory/Craftory_Gutterpipe.json"))
    private val blockType = item.getAsJsonObject("BlockType")
    private val definitions = blockType.getAsJsonObject("State").getAsJsonObject("Definitions")
    private val hitboxesByStem = hitboxFilesByStem(hitboxes)

    @Test
    fun allSixtyFourStatesMapToTheirOwnModelAndHitbox() {
        assertEquals(64, definitions.size())
        assertEquals("Craftory_Gutterpipe_C00", blockType.get("HitboxType").asString)
        for (mask in 0..63) {
            val name = GutterpipeMask.stateName(mask)
            val definition = definitions.getAsJsonObject(name)
            val modelPath = definition.get("CustomModel").asString
            val hitboxType = definition.get("HitboxType").asString
            assertTrue(modelPath.endsWith("/Gutterpipe_$name.blockymodel"), "$name model path")
            assertEquals("Craftory_Gutterpipe_$name", hitboxType)
            val model = parse(common.resolve(modelPath))
            val bounds = BlockModelGeometry.bounds(model)
            val hitboxPath = assertNotNull(hitboxesByStem[hitboxType], "$name hitbox missing: $hitboxType")
            val hitbox = parse(hitboxPath).getAsJsonArray("Boxes").first().asJsonObject
            val minimum = hitbox.getAsJsonObject("Min")
            val maximum = hitbox.getAsJsonObject("Max")
            for (axis in 0..2) {
                val key = listOf("X", "Y", "Z")[axis]
                val center = if (axis == 1) 16.0 else 0.0
                assertTrue(abs(minimum.get(key).asDouble - (0.5 + (bounds.min[axis] - center) / 32)) <= 1.0 / 32)
                assertTrue(abs(maximum.get(key).asDouble - (0.5 + (bounds.max[axis] - center) / 32)) <= 1.0 / 32)
                val positiveBit = 1 shl (axis * 2)
                val negativeBit = 1 shl (axis * 2 + 1)
                val low = bounds.min[axis] - center
                val high = bounds.max[axis] - center
                val positiveFace = BlockModelGeometry.hasAxisAlignedFaceAt(model, axis, center + 16)
                val negativeFace = BlockModelGeometry.hasAxisAlignedFaceAt(model, axis, center - 16)
                if (mask and positiveBit != 0) {
                    assertEquals(16.0, high, 0.01, "$name +$key connection")
                    assertTrue(positiveFace, "$name has no +$key face on the connection plane")
                } else {
                    assertTrue(high <= 14.01, "$name reaches unconnected +$key face")
                    assertTrue(!positiveFace, "$name has an unconnected +$key face")
                }
                if (mask and negativeBit != 0) {
                    assertEquals(-16.0, low, 0.01, "$name -$key connection")
                    assertTrue(negativeFace, "$name has no -$key face on the connection plane")
                } else {
                    assertTrue(low >= -14.01, "$name reaches unconnected -$key face")
                    assertTrue(!negativeFace, "$name has an unconnected -$key face")
                }
            }
        }
    }

    @Test
    fun generatedModelsUseWorldPositiveXyzAxisConvention() {
        val x = BlockModelGeometry.bounds(model("C01"))
        val y = BlockModelGeometry.bounds(model("C04"))
        val z = BlockModelGeometry.bounds(model("C16"))
        assertEquals(16.0, x.max[0], 0.01)
        assertEquals(32.0, y.max[1], 0.01)
        assertEquals(16.0, z.max[2], 0.01)
    }

    @Test
    fun adjacentStraightPipesHaveMatchingCrossSections() {
        val positive = node(model("C01"), "Tube_X_OnePositive")
        val negative = node(model("C02"), "Tube_X_OneNegative")
        val positiveSize = positive.getAsJsonObject("shape").getAsJsonObject("settings").getAsJsonObject("size")
        val negativeSize = negative.getAsJsonObject("shape").getAsJsonObject("settings").getAsJsonObject("size")
        assertEquals(positiveSize.get("y").asDouble, negativeSize.get("y").asDouble)
        assertEquals(positiveSize.get("z").asDouble, negativeSize.get("z").asDouble)
        assertEquals(10.0, positiveSize.get("y").asDouble)
        assertEquals(10.0, positiveSize.get("z").asDouble)
        val through = model("C03")
        val positiveThrough = node(through, "Tube_X_Through")
        val throughSize = positiveThrough.getAsJsonObject("shape").getAsJsonObject("settings").getAsJsonObject("size")
        assertEquals(positiveSize.get("y").asDouble, throughSize.get("y").asDouble)
        assertEquals(positiveSize.get("z").asDouble, throughSize.get("z").asDouble)
        assertTrue(BlockModelGeometry.hasAxisAlignedFaceAt(model("C01"), 0, 16.0))
        assertTrue(BlockModelGeometry.hasAxisAlignedFaceAt(through, 0, 16.0))
        assertTrue(BlockModelGeometry.hasAxisAlignedFaceAt(through, 0, -16.0))
        assertTrue(BlockModelGeometry.hasAxisAlignedFaceAt(model("C02"), 0, -16.0))
    }

    @Test
    fun neighborEligibilityUsesBlockAndTransportSignals() {
        assertTrue(GutterpipeMask.connects(CraftoryIds.PIPE, false, false, -1))
        assertTrue(GutterpipeMask.connects(CraftoryIds.SNATCHER_CLAW, false, false, -1))
        assertTrue(GutterpipeMask.connects(null, true, false, -1))
        assertTrue(GutterpipeMask.connects(null, false, true, -1))
        assertTrue(GutterpipeMask.connects(null, false, false, 0))
        assertTrue(!GutterpipeMask.connects(null, false, false, -1))
    }

    private fun model(state: String) =
        parse(common.resolve(definitions.getAsJsonObject(state).get("CustomModel").asString))

    private fun node(
        model: JsonObject,
        name: String,
    ): JsonObject {
        fun find(nodes: com.google.gson.JsonArray): JsonObject? {
            nodes.forEach { element ->
                val node = element.asJsonObject
                if (node.get("name").asString == name) return node
                node.getAsJsonArray("children")?.let { find(it) }?.let { return it }
            }
            return null
        }
        return assertNotNull(find(model.getAsJsonArray("nodes")), "missing node $name")
    }

    private fun parse(path: Path) = JsonParser.parseString(Files.readString(path)).asJsonObject

    private fun resourceRoot(): Path {
        val candidates = listOf(Path.of("craftory-tech/src/main/resources"), Path.of("src/main/resources"))
        return candidates.first { Files.exists(it.resolve("Common/Blocks/Craftory/Gutterpipe")) }
    }
}
