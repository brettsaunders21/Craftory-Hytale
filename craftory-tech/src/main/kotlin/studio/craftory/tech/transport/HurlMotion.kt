package studio.craftory.tech.transport

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d

internal object HurlMotion {
    fun move(
        capsule: Ref<EntityStore>,
        ride: HurlRide,
        commands: CommandBuffer<EntityStore>,
    ) {
        // Runs after HandleMountInput, overwriting client movement even while waiting for chunks.
        commands.getComponent(capsule, TransformComponent.getComponentType())?.setPosition(vector(ride))
        val attached = ride.rider?.takeIf { it.isValid }?.let { HurlMounts.isMountedTo(it, capsule, commands) } == true
        if (ride.mounted && !attached) ride.mounted = false
        if (!ride.mounted) teleport(ride, commands, vector(ride))
    }

    fun teleport(
        ride: HurlRide,
        commands: CommandBuffer<EntityStore>,
        position: Vector3d,
    ) {
        ride.rider?.takeIf { it.isValid }?.let { rider ->
            val rotation =
                commands.getComponent(rider, TransformComponent.getComponentType())?.rotation ?: Rotation3f.IDENTITY
            commands.putComponent(rider, Teleport.getComponentType(), Teleport.createForPlayer(position, rotation))
        }
    }

    fun vector(ride: HurlRide): Vector3d = ride.position.set(ride.output[0], ride.output[1], ride.output[2])
}
