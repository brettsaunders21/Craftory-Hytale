package studio.craftory.tech.machine

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.multiblock.MultiblockGeometry
import studio.craftory.tech.CraftoryIds
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal fun hitboxFilesByStem(hitboxes: Path): Map<String, Path> {
    val files =
        Files.walk(hitboxes).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }.toList()
        }
    val byStem = files.groupBy { it.fileName.toString().removeSuffix(".json") }
    val duplicates = byStem.filterValues { it.size > 1 }
    assertTrue(
        duplicates.isEmpty(),
        "duplicate hitbox stems: ${duplicates.mapValues { (_, paths) -> paths.joinToString() }}",
    )
    return byStem.mapValues { (_, paths) -> paths.single() }
}

internal data class ModelBounds(
    val min: DoubleArray,
    val max: DoubleArray,
) {
    fun include(other: ModelBounds) {
        for (axis in 0..2) {
            min[axis] = minOf(min[axis], other.min[axis])
            max[axis] = maxOf(max[axis], other.max[axis])
        }
    }
}

internal object BlockModelGeometry {
    private data class Face(
        val box: Int,
        val node: String,
        val name: String,
        val normal: DoubleArray,
        val plane: Double,
        val vertices: List<DoubleArray>,
    )

    fun bounds(model: JsonObject): ModelBounds {
        val points = mutableListOf<DoubleArray>()
        model.getAsJsonArray("nodes").forEach {
            visit(it.asJsonObject, identity(), DoubleArray(3), DoubleArray(3), points)
        }
        require(points.isNotEmpty()) { "model has no visible geometry" }
        return ModelBounds(
            DoubleArray(3) { axis -> points.minOf { it[axis] } },
            DoubleArray(3) { axis -> points.maxOf { it[axis] } },
        )
    }

    fun coplanarSameNormalOverlaps(model: JsonObject): List<String> {
        val faces = mutableListOf<Face>()
        var boxIndex = 0
        model.getAsJsonArray("nodes").forEach {
            collectFaces(it.asJsonObject, identity(), DoubleArray(3), DoubleArray(3), faces) { boxIndex++ }
        }
        return buildList {
            for (firstIndex in faces.indices) {
                val first = faces[firstIndex]
                for (secondIndex in firstIndex + 1 until faces.size) {
                    val second = faces[secondIndex]
                    if (first.box != second.box &&
                        dot(first.normal, second.normal) > 0.9999 &&
                        kotlin.math.abs(first.plane - second.plane) < 0.01
                    ) {
                        val overlap =
                            polygonIntersectionArea(
                                project(first.vertices, first.normal),
                                project(second.vertices, first.normal),
                            )
                        if (overlap > 0.01) {
                            add(
                                "${first.node}:${first.name} overlaps ${second.node}:${second.name} " +
                                    "($overlap sq units)",
                            )
                        }
                    }
                }
            }
        }
    }

    fun hasAxisAlignedFaceAt(
        model: JsonObject,
        axis: Int,
        coordinate: Double,
    ): Boolean {
        val faces = mutableListOf<Face>()
        var boxIndex = 0
        model.getAsJsonArray("nodes").forEach {
            collectFaces(it.asJsonObject, identity(), DoubleArray(3), DoubleArray(3), faces) { boxIndex++ }
        }
        return faces.any { face ->
            abs(face.normal[axis]) > 0.9999 && face.vertices.all { abs(it[axis] - coordinate) < 0.01 }
        }
    }

    fun nodePosition(
        model: JsonObject,
        nodeName: String,
    ): DoubleArray? {
        var found: DoubleArray? = null
        model.getAsJsonArray("nodes").forEach {
            visit(it.asJsonObject, identity(), DoubleArray(3), DoubleArray(3), mutableListOf(), nodeName) { position ->
                found = position
            }
        }
        return found
    }

    private fun visit(
        node: JsonObject,
        rotation: Array<DoubleArray>,
        translation: DoubleArray,
        parentOffset: DoubleArray,
        points: MutableList<DoubleArray>,
        targetName: String? = null,
        onTarget: (DoubleArray) -> Unit = {},
    ) {
        val orientation = node.getAsJsonObject("orientation")
        val localRotation = quaternionMatrix(orientation)
        val worldRotation = multiply(rotation, localRotation)
        val worldTranslation =
            add(translation, multiply(rotation, add(parentOffset, vector(node.getAsJsonObject("position")))))
        val shape = node.getAsJsonObject("shape")
        val offset = vector(shape.getAsJsonObject("offset"))
        val center = add(worldTranslation, multiply(worldRotation, offset))
        if (node.get("name").asString == targetName) onTarget(center)
        if (shape.get("visible").asBoolean && shape.get("type").asString in setOf("box", "quad")) {
            val settings = shape.getAsJsonObject("settings")
            val size = vector(settings.getAsJsonObject("size"))
            val stretch = vector(shape.getAsJsonObject("stretch"))
            if (shape.get("type").asString == "box") {
                require(size.all { it > 0 }) { "zero-size box node ${node.get("name").asString}" }
            }
            val shapeSize =
                if (shape.get("type").asString == "quad") {
                    val normal = settings.get("normal")?.asString ?: "+Z"
                    when (normal) {
                        "+X", "-X" -> doubleArrayOf(0.0, size[1], size[0])
                        "+Y", "-Y" -> doubleArrayOf(size[0], 0.0, size[1])
                        else -> doubleArrayOf(size[0], size[1], 0.0)
                    }
                } else {
                    size
                }
            for (x in listOf(-1.0, 1.0)) {
                for (y in listOf(-1.0, 1.0)) {
                    for (z in listOf(-1.0, 1.0)) {
                        val local =
                            doubleArrayOf(
                                x * shapeSize[0] * stretch[0] / 2,
                                y * shapeSize[1] * stretch[1] / 2,
                                z * shapeSize[2] * stretch[2] / 2,
                            )
                        points.add(add(center, multiply(worldRotation, local)))
                    }
                }
            }
        }
        node.getAsJsonArray("children")?.forEach {
            visit(it.asJsonObject, worldRotation, worldTranslation, offset, points, targetName, onTarget)
        }
    }

    private fun collectFaces(
        node: JsonObject,
        rotation: Array<DoubleArray>,
        translation: DoubleArray,
        parentOffset: DoubleArray,
        faces: MutableList<Face>,
        nextBox: () -> Int,
    ) {
        val worldRotation = multiply(rotation, quaternionMatrix(node.getAsJsonObject("orientation")))
        val worldTranslation =
            add(translation, multiply(rotation, add(parentOffset, vector(node.getAsJsonObject("position")))))
        val shape = node.getAsJsonObject("shape")
        val offset = vector(shape.getAsJsonObject("offset"))
        if (shape.get("visible").asBoolean && shape.get("type").asString == "box") {
            val size = vector(shape.getAsJsonObject("settings").getAsJsonObject("size"))
            val stretch = vector(shape.getAsJsonObject("stretch"))
            val half = DoubleArray(3) { size[it] * stretch[it] / 2 }
            val center = add(worldTranslation, multiply(worldRotation, offset))
            val boxIndex = nextBox()
            for (axis in 0..2) {
                val others = (0..2).filter { it != axis }
                for (sign in listOf(-1.0, 1.0)) {
                    val normal = DoubleArray(3) { row -> worldRotation[row][axis] * sign }
                    val vertices =
                        listOf(
                            doubleArrayOf(sign, -1.0, -1.0),
                            doubleArrayOf(sign, 1.0, -1.0),
                            doubleArrayOf(sign, 1.0, 1.0),
                            doubleArrayOf(sign, -1.0, 1.0),
                        ).map { signs ->
                            val local = DoubleArray(3)
                            local[axis] = signs[0] * half[axis]
                            local[others[0]] = signs[1] * half[others[0]]
                            local[others[1]] = signs[2] * half[others[1]]
                            add(center, multiply(worldRotation, local))
                        }
                    faces.add(
                        Face(
                            boxIndex,
                            node.get("name").asString,
                            "${axisName(axis)}${if (sign > 0) "+" else "-"}",
                            normal,
                            dot(normal, vertices.first()),
                            vertices,
                        ),
                    )
                }
            }
        }
        node.getAsJsonArray("children")?.forEach {
            collectFaces(it.asJsonObject, worldRotation, worldTranslation, offset, faces, nextBox)
        }
    }

    private fun project(
        vertices: List<DoubleArray>,
        normal: DoubleArray,
    ): List<DoubleArray> {
        val reference =
            if (kotlin.math.abs(normal[0]) < 0.9) {
                doubleArrayOf(1.0, 0.0, 0.0)
            } else {
                doubleArrayOf(0.0, 1.0, 0.0)
            }
        val u = normalize(cross(normal, reference))
        val v = cross(normal, u)
        return vertices.map { doubleArrayOf(dot(it, u), dot(it, v)) }
    }

    private fun polygonIntersectionArea(
        subject: List<DoubleArray>,
        clip: List<DoubleArray>,
    ): Double {
        var output = subject
        val orientation = if (signedArea(clip) >= 0) 1.0 else -1.0
        for (edgeIndex in clip.indices) {
            val edgeStart = clip[edgeIndex]
            val edgeEnd = clip[(edgeIndex + 1) % clip.size]
            val input = output
            if (input.isEmpty()) return 0.0
            output = mutableListOf()
            var previous = input.last()
            for (current in input) {
                val previousSide = orientation * cross2(edgeStart, edgeEnd, previous)
                val currentSide = orientation * cross2(edgeStart, edgeEnd, current)
                if (previousSide >= -1e-8 && currentSide < -1e-8) {
                    output += lineIntersection(previous, current, edgeStart, edgeEnd)
                } else if (previousSide < -1e-8 && currentSide >= -1e-8) {
                    output += lineIntersection(previous, current, edgeStart, edgeEnd)
                    output += current
                } else if (currentSide >= -1e-8) {
                    output += current
                }
                previous = current
            }
        }
        return kotlin.math.abs(signedArea(output))
    }

    private fun lineIntersection(
        start: DoubleArray,
        end: DoubleArray,
        edgeStart: DoubleArray,
        edgeEnd: DoubleArray,
    ): DoubleArray {
        val segment = doubleArrayOf(end[0] - start[0], end[1] - start[1])
        val edge = doubleArrayOf(edgeEnd[0] - edgeStart[0], edgeEnd[1] - edgeStart[1])
        val denominator = cross2(segment, edge)
        if (kotlin.math.abs(denominator) < 1e-12) return start
        val delta = doubleArrayOf(edgeStart[0] - start[0], edgeStart[1] - start[1])
        val t = cross2(delta, edge) / denominator
        return doubleArrayOf(start[0] + t * segment[0], start[1] + t * segment[1])
    }

    private fun signedArea(polygon: List<DoubleArray>): Double =
        polygon.indices.sumOf { index ->
            val current = polygon[index]
            val next = polygon[(index + 1) % polygon.size]
            current[0] * next[1] - next[0] * current[1]
        } / 2

    private fun cross2(
        a: DoubleArray,
        b: DoubleArray,
    ): Double = a[0] * b[1] - a[1] * b[0]

    private fun cross2(
        origin: DoubleArray,
        a: DoubleArray,
        b: DoubleArray,
    ): Double =
        cross2(
            doubleArrayOf(a[0] - origin[0], a[1] - origin[1]),
            doubleArrayOf(b[0] - origin[0], b[1] - origin[1]),
        )

    private fun dot(
        a: DoubleArray,
        b: DoubleArray,
    ): Double = a.indices.sumOf { a[it] * b[it] }

    private fun cross(
        a: DoubleArray,
        b: DoubleArray,
    ): DoubleArray =
        doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )

    private fun normalize(value: DoubleArray): DoubleArray {
        val magnitude = kotlin.math.sqrt(dot(value, value))
        return DoubleArray(value.size) { value[it] / magnitude }
    }

    private fun axisName(axis: Int): String = listOf("X", "Y", "Z")[axis]

    private fun vector(json: JsonObject): DoubleArray =
        doubleArrayOf(json.get("x")?.asDouble ?: 0.0, json.get("y")?.asDouble ?: 0.0, json.get("z")?.asDouble ?: 0.0)

    private fun identity(): Array<DoubleArray> =
        arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0))

    private fun quaternionMatrix(q: JsonObject): Array<DoubleArray> {
        val x = q.get("x").asDouble
        val y = q.get("y").asDouble
        val z = q.get("z").asDouble
        val w = q.get("w").asDouble
        return arrayOf(
            doubleArrayOf(1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)),
            doubleArrayOf(2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)),
            doubleArrayOf(2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)),
        )
    }

    private fun add(
        a: DoubleArray,
        b: DoubleArray,
    ): DoubleArray = DoubleArray(3) { a[it] + b[it] }

    private fun multiply(
        matrix: Array<DoubleArray>,
        value: DoubleArray,
    ): DoubleArray = DoubleArray(3) { row -> (0..2).sumOf { column -> matrix[row][column] * value[column] } }

    private fun multiply(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>,
    ): Array<DoubleArray> =
        Array(3) { row -> DoubleArray(3) { column -> (0..2).sumOf { k -> a[row][k] * b[k][column] } } }
}

internal class BlockModelLintTest {
    private val techResources = requireNotNull(resourceRoot("craftory-tech"))
    private val common = techResources.resolve("Common")
    private val items = techResources.resolve("Server/Item/Items/Craftory")
    private val hitboxes = techResources.resolve("Server/Item/Block/Hitboxes")

    @Test
    fun allCommonModelsStayGroundedAndWithinBlockBounds() {
        val commonRoots =
            listOfNotNull(
                common,
                resourceRoot("craftory-core")?.resolve("Common"),
            ).filter { Files.exists(it) }
        val models =
            commonRoots.flatMap { root ->
                Files.walk(root).use { paths ->
                    paths.filter { path -> path.toString().endsWith(".blockymodel") }.toList()
                }
            }
        assertTrue(models.isNotEmpty())
        val parsedModels = models.associateWith { path -> parse(path) }
        parsedModels.forEach { (path, model) -> BlockModelGeometry.bounds(model) }
        // NPC and held-item models use non-block coordinate spaces.
        val blockModels = models.filter { path -> commonRoots.any { path.startsWith(it.resolve("Blocks")) } }
        blockModels.forEach { path ->
            val model = parsedModels.getValue(path)
            val bounds = BlockModelGeometry.bounds(model)
            val label = path.toString()
            assertTrue(bounds.min[1] >= -0.01, "$label extends below y=0: ${bounds.min[1]}")
            assertTrue(bounds.min[0] >= -17 && bounds.max[0] <= 17, "$label exceeds X block bounds")
            assertTrue(bounds.min[2] >= -17 && bounds.max[2] <= 17, "$label exceeds Z block bounds")
        }
        val overlaps =
            blockModels.flatMap { path ->
                BlockModelGeometry.coplanarSameNormalOverlaps(parsedModels.getValue(path)).map { "$path: $it" }
            }
        assertTrue(overlaps.isEmpty(), "coplanar same-normal face overlaps:\n${overlaps.joinToString("\n")}")
    }

    @Test
    fun craftoryModelBlocksHaveFittedHitboxesAndAttachedParticles() {
        val hitboxByStem = hitboxFilesByStem(hitboxes)
        Files.list(items).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".json") }.forEach { itemPath ->
                val item = parse(itemPath)
                val blockType = item.getAsJsonObject("BlockType") ?: return@forEach
                if (blockType.get("DrawType")?.asString != "Model") return@forEach
                val modelPaths = modelPaths(blockType)
                assertTrue(modelPaths.isNotEmpty(), "${itemPath.fileName} has no model")
                val hitboxType = blockType.get("HitboxType")?.asString
                assertNotNull(hitboxType, "${itemPath.fileName} has no hitbox type")
                assertTrue('/' !in hitboxType, "${itemPath.fileName} HitboxType must be a file stem: $hitboxType")

                val definitions = blockType.getAsJsonObject("State")?.getAsJsonObject("Definitions")
                val models = modelPaths.map { path -> parse(common.resolve(path)) }
                val baseModels =
                    blockType
                        .get("CustomModel")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.let(::listOf)
                        ?: modelPaths
                assertParticleTargets(itemPath, blockType.getAsJsonArray("Particles"), baseModels, "base")
                definitions?.entrySet()?.forEach { (stateName, state) ->
                    val stateDefinition = state.asJsonObject
                    val stateModels =
                        stateDefinition
                            .get("CustomModel")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            ?.let(::listOf)
                            ?: baseModels
                    assertParticleTargets(
                        itemPath,
                        stateDefinition.get("Particles")?.takeIf { it.isJsonArray }?.asJsonArray,
                        stateModels,
                        stateName,
                    )
                }

                assertBlockHitboxes(itemPath, definitions, models, hitboxType, hitboxByStem)
            }
        }
    }

    @Test
    fun machineVentTableMatchesEveryModelNode() {
        MachineVents.entries.forEach { (blockId, vent) ->
            val itemPath = items.resolve("$blockId.json")
            assertTrue(Files.exists(itemPath), "missing machine vent block item $blockId")
            val models =
                vent.modelPath?.let(::listOf)
                    ?: modelPaths(parse(itemPath).getAsJsonObject("BlockType"))
            assertTrue(models.isNotEmpty(), "$blockId has no models")
            models.forEach { ref ->
                val model = parse(common.resolve(ref))
                val node = BlockModelGeometry.nodePosition(model, vent.nodeName)
                assertNotNull(node, "${vent.nodeName} missing from $ref")
                assertTrue(abs(0.5 + node[0] / 32 - vent.x) <= 1.0 / 32, "$blockId vent X mismatch")
                assertTrue(abs(node[1] / 32 - vent.y) <= 1.0 / 32, "$blockId vent Y mismatch")
                assertTrue(abs(0.5 + node[2] / 32 - vent.z) <= 1.0 / 32, "$blockId vent Z mismatch")
            }
        }
    }

    @Test
    fun multiblockVentsUseRotatedVentingPartOrigins() {
        val controller = BlockPos(20, 10, 30)
        for (rotation in 0..3) {
            val stack = MultiblockGeometry.worldPosition(controller, GutterBoilerTemplate.WHISTLE_STACK, rotation)
            val boilerVent = MachineVents.entries.getValue(CraftoryIds.GUTTER_BOILER)
            val boilerPosition =
                assertNotNull(MachineVents.position(CraftoryIds.GUTTER_BOILER, stack.x, stack.y, stack.z))
            assertEquals(stack.x + boilerVent.x, boilerPosition.x, 1e-8)
            assertEquals(stack.y + boilerVent.y, boilerPosition.y, 1e-8)
            assertEquals(stack.z + boilerVent.z, boilerPosition.z, 1e-8)

            val chimney = MultiblockGeometry.worldPosition(controller, JunkHeartTemplate.CHIMNEY_TOP, rotation)
            val heartVent = MachineVents.entries.getValue(CraftoryIds.JUNK_HEART)
            val smokePosition =
                assertNotNull(MachineVents.position(CraftoryIds.JUNK_HEART, chimney.x, chimney.y, chimney.z))
            assertEquals(chimney.x + heartVent.x, smokePosition.x, 1e-8)
            assertEquals(chimney.y + heartVent.y, smokePosition.y, 1e-8)
            assertEquals(chimney.z + heartVent.z, smokePosition.z, 1e-8)
        }
    }

    private fun assertParticleTargets(
        itemPath: Path,
        particles: JsonArray?,
        modelRefs: List<String>,
        state: String,
    ) {
        val models = modelRefs.map { ref -> ref to parse(common.resolve(ref)) }
        particles?.forEach { element ->
            val particle = element.asJsonObject
            val target = particle.get("TargetNodeName")?.asString
            assertNotNull(target, "${itemPath.fileName} $state has an unattached particle")
            models.forEach { (ref, model) ->
                assertNotNull(
                    BlockModelGeometry.nodePosition(model, target),
                    "$target missing from $state model $ref for ${itemPath.fileName}",
                )
            }
            particle.getAsJsonObject("PositionOffset")?.entrySet()?.forEach { (_, value) ->
                assertTrue(abs(value.asDouble) <= 0.1, "${itemPath.fileName} particle offset is not tiny")
            }
        }
    }

    private fun assertBlockHitboxes(
        itemPath: Path,
        definitions: JsonObject?,
        models: List<JsonObject>,
        hitboxType: String,
        hitboxByStem: Map<String, Path>,
    ) {
        definitions?.entrySet()?.forEach stateLoop@{ (stateName, state) ->
            val stateDefinition = state.asJsonObject
            val stateHitbox = stateDefinition.get("HitboxType")?.asString ?: return@stateLoop
            val stateModel = stateDefinition.get("CustomModel")?.asString ?: return@stateLoop
            assertTrue(
                '/' !in stateHitbox,
                "${itemPath.fileName} state HitboxType must be a file stem: $stateHitbox",
            )
            val stateHitboxPath =
                assertNotNull(
                    hitboxByStem[stateHitbox],
                    "${itemPath.fileName} state hitbox missing: $stateHitbox",
                )
            assertHitboxFits(
                "${itemPath.fileName} $stateName",
                blockBounds(listOf(BlockModelGeometry.bounds(parse(common.resolve(stateModel))))),
                parse(stateHitboxPath).getAsJsonArray("Boxes"),
            )
        }
        if (itemPath.fileName.toString() == "Craftory_Gutterpipe.json") return
        val union = blockBounds(models.map(BlockModelGeometry::bounds))
        val hitboxPath =
            assertNotNull(
                hitboxByStem[hitboxType],
                "${itemPath.fileName} hitbox missing: $hitboxType",
            )
        assertHitboxFits(itemPath.fileName.toString(), union, parse(hitboxPath).getAsJsonArray("Boxes"))
    }

    private fun modelPaths(blockType: JsonObject): List<String> {
        val models = mutableListOf<String>()
        blockType
            .get("CustomModel")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.let(models::add)
        blockType.getAsJsonObject("State")?.getAsJsonObject("Definitions")?.entrySet()?.forEach { (_, state) ->
            state.asJsonObject
                .get("CustomModel")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.let(models::add)
        }
        return models.distinct()
    }

    private fun boxBounds(boxes: JsonArray): ModelBounds {
        val minimum = DoubleArray(3) { Double.POSITIVE_INFINITY }
        val maximum = DoubleArray(3) { Double.NEGATIVE_INFINITY }
        boxes.forEach { box ->
            val boxObject = box.asJsonObject
            val low = vector(boxObject.getAsJsonObject("Min"))
            val high = vector(boxObject.getAsJsonObject("Max"))
            for (axis in 0..2) {
                minimum[axis] = minOf(minimum[axis], low[axis])
                maximum[axis] = maxOf(maximum[axis], high[axis])
            }
        }
        return ModelBounds(minimum, maximum)
    }

    private fun blockBounds(bounds: List<ModelBounds>): ModelBounds =
        ModelBounds(
            DoubleArray(3) { axis -> bounds.minOf { it.min[axis] / 32 + if (axis == 0 || axis == 2) 0.5 else 0.0 } },
            DoubleArray(3) { axis -> bounds.maxOf { it.max[axis] / 32 + if (axis == 0 || axis == 2) 0.5 else 0.0 } },
        )

    private fun assertHitboxFits(
        label: String,
        model: ModelBounds,
        boxes: JsonArray,
    ) {
        val hitboxBounds = boxBounds(boxes)
        for (axis in 0..2) {
            assertTrue(
                hitboxBounds.min[axis] >= model.min[axis] - 2.0 / 32,
                "$label hitbox undercovers ${listOf(
                    "X",
                    "Y",
                    "Z",
                )[axis]}: ${hitboxBounds.min[axis]} < ${model.min[axis]}",
            )
            assertTrue(
                hitboxBounds.max[axis] <= model.max[axis] + 2.0 / 32,
                "$label hitbox overreaches ${listOf(
                    "X",
                    "Y",
                    "Z",
                )[axis]}: ${hitboxBounds.max[axis]} > ${model.max[axis]}",
            )
        }
        assertTrue(
            volume(hitboxBounds) <= volume(model) * 1.25 + 1e-8,
            "$label hitbox is too large: ${volume(hitboxBounds)} > ${volume(model) * 1.25}; " +
                "hitbox=${hitboxBounds.min.contentToString()}..${hitboxBounds.max.contentToString()}, " +
                "model=${model.min.contentToString()}..${model.max.contentToString()}",
        )
    }

    private fun vector(json: JsonObject) =
        doubleArrayOf(json.get("X").asDouble, json.get("Y").asDouble, json.get("Z").asDouble)

    private fun volume(bounds: ModelBounds) =
        (0..2).fold(1.0) { result, axis -> result * (bounds.max[axis] - bounds.min[axis]) }

    private fun parse(path: Path): JsonObject = JsonParser.parseString(Files.readString(path)).asJsonObject

    private fun resourceRoot(module: String): Path? =
        listOf(Path.of(module, "src/main/resources"), Path.of("src/main/resources"))
            .firstOrNull(Files::exists)
}
