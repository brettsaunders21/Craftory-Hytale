package studio.craftory.core.api

import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.energy.EnergyAccess
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.EnergyMemberType
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.energy.EnergyClock
import studio.craftory.core.energy.EnergyWorld
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

object CraftoryCore {
    private val modules = ConcurrentSkipListMap<String, String>()
    private val energyTypes = ConcurrentHashMap<String, EnergyMemberType>()
    private val commandHandlers = ConcurrentHashMap<String, (CommandContext) -> CompletableFuture<Void>>()
    private val inspectableComponents =
        ConcurrentHashMap<String, (ComponentAccessor<ChunkStore>, Ref<ChunkStore>) -> Inspectable?>()

    fun registerModule(
        name: String,
        version: String,
    ) {
        require(name.isNotBlank()) { "Module name must not be blank" }
        require(version.isNotBlank()) { "Module version must not be blank" }
        modules[name] = version
    }

    fun loadedModules(): List<String> = modules.map { (name, version) -> "$name v$version" }

    fun registerEnergyType(
        id: String,
        spec: MemberSpec,
    ): EnergyMemberType {
        require(ENERGY_ID.matches(id)) { "Energy type id must be namespaced: $id" }
        val type = EnergyMemberType(id, spec)
        check(energyTypes.putIfAbsent(id, type) == null) { "Energy type already registered: $id" }
        return type
    }

    fun energyType(id: String): EnergyMemberType? = energyTypes[id]

    fun registeredEnergyTypes(): List<EnergyMemberType> = energyTypes.values.sortedBy(EnergyMemberType::id)

    fun registerCommandHandler(
        key: String,
        handler: (CommandContext) -> CompletableFuture<Void>,
    ) {
        require(key.isNotBlank())
        check(commandHandlers.putIfAbsent(key, handler) == null) { "Command handler already registered: $key" }
    }

    fun registerInspectableComponent(
        id: String,
        inspector: (ComponentAccessor<ChunkStore>, Ref<ChunkStore>) -> Inspectable?,
    ) {
        require(id.isNotBlank())
        check(inspectableComponents.putIfAbsent(id, inspector) == null) {
            "Inspectable component already registered: $id"
        }
    }

    fun inspectableComponents(
        accessor: ComponentAccessor<ChunkStore>,
        ref: Ref<ChunkStore>,
    ): List<Pair<String, Inspectable>> =
        buildList {
            accessor.getComponent(ref, EnergyMember.type)?.let { add("Craftory_EnergyMember" to it) }
            inspectableComponents.forEach { (id, inspector) -> inspector(accessor, ref)?.let { add(id to it) } }
        }

    fun dispatchCommandHandler(
        key: String,
        context: CommandContext,
    ): CompletableFuture<Void> = commandHandlers[key]?.invoke(context) ?: CompletableFuture.completedFuture(null)

    fun energyAccess(world: World): EnergyAccess {
        world.debugAssertInTickingThread()
        val energy = world.chunkStore.store.getResource(EnergyWorld.type)
        return EnergyAccess(energy, world)
    }

    fun isEnergyTick(store: Store<ChunkStore>): Boolean {
        val world = store.externalData.world
        return store.getResource(EnergyClock.type).isSolveTick(world.tick)
    }

    private val ENERGY_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
}
