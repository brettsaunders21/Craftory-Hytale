package studio.craftory.tech.transport

import com.hypixel.hytale.builtin.mounts.MountedByComponent
import com.hypixel.hytale.builtin.mounts.MountedComponent
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Holder
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.MountController
import com.hypixel.hytale.server.core.asset.type.model.config.Model
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3f
import java.util.logging.Logger

internal object HurlMounts {
    fun occupied(
        rider: Ref<EntityStore>,
        store: Store<EntityStore>,
    ): Boolean =
        !rider.isValid || store.getComponent(rider, HurlRider.type) != null ||
            store.getComponent(rider, MountedComponent.getComponentType()) != null

    fun prepare(
        holder: Holder<EntityStore>,
        store: Store<EntityStore>,
    ) {
        val asset = ModelAsset.getAssetMap().getAsset("Minecart") ?: ModelAsset.DEBUG
        val model = Model.createScaledModel(asset, 1.0f)
        holder.addComponent(NetworkId.getComponentType(), NetworkId(store.externalData.takeNextNetworkId()))
        holder.addComponent(PersistentModel.getComponentType(), PersistentModel(model.toReference()))
        holder.addComponent(ModelComponent.getComponentType(), ModelComponent(model))
        val bounds = model.boundingBox
        if (bounds != null) {
            holder.addComponent(BoundingBox.getComponentType(), BoundingBox(bounds))
        } else {
            holder.ensureComponent(BoundingBox.getComponentType())
        }
        holder.addComponent(MountedByComponent.getComponentType(), MountedByComponent())
    }

    @Suppress("TooGenericExceptionCaught")
    fun attach(
        store: Store<EntityStore>,
        capsule: Ref<EntityStore>,
        ride: HurlRide,
        config: HurlConfig,
    ) {
        val rider = ride.rider ?: return
        if (config.rideDriver != "mount") return
        try {
            store.getComponent(capsule, MountedByComponent.getComponentType())?.addPassenger(rider)
            store.putComponent(
                rider,
                MountedComponent.getComponentType(),
                MountedComponent(capsule, Vector3f(), MountController.Minecart),
            )
            ride.mounted = true
        } catch (exception: RuntimeException) {
            if (store.getComponent(rider, MountedComponent.getComponentType()) != null) {
                store.removeComponent(rider, MountedComponent.getComponentType())
            }
            store.getComponent(capsule, MountedByComponent.getComponentType())?.removePassenger(rider)
            Logger
                .getLogger(
                    "CraftoryTech",
                ).warning("Hurl mount failed; using Teleport: ${exception.javaClass.simpleName}")
        }
    }

    fun detach(
        rider: Ref<EntityStore>,
        commands: CommandBuffer<EntityStore>,
    ) {
        commands.tryRemoveComponent(rider, MountedComponent.getComponentType())
    }

    fun isMountedTo(
        rider: Ref<EntityStore>,
        capsule: Ref<EntityStore>,
        commands: CommandBuffer<EntityStore>,
    ): Boolean = commands.getComponent(rider, MountedComponent.getComponentType())?.mountedToEntity == capsule
}
