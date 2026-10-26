package studio.craftory.tech.well

import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.universe.world.path.IPath
import com.hypixel.hytale.server.core.universe.world.path.IPathWaypoint
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import java.util.UUID

internal class WellAfflictionPath(
    private val target: Vector3d,
) : IPath<IPathWaypoint> {
    override fun getId(): UUID = UUID.randomUUID()

    override fun getName(): String = "CraftoryWellAffliction"

    override fun getPathWaypoints(): List<IPathWaypoint> = listOf(WellAfflictionWaypoint(target))

    override fun length(): Int = 1

    override fun get(index: Int): IPathWaypoint = getPathWaypoints()[index]
}

internal data class WellAfflictionWaypoint(
    private val target: Vector3d,
) : IPathWaypoint {
    override fun getOrder(): Int = 0

    override fun getWaypointPosition(accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>): Vector3d =
        target

    override fun getWaypointRotation(
        accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
    ): Rotation3f = Rotation3f(0f, 0f, 0f)

    override fun getPauseTime(): Double = 0.0

    override fun getObservationAngle(): Float = 0f
}
