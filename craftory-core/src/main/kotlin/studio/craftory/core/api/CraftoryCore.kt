package studio.craftory.core.api

import com.hypixel.hytale.server.core.universe.world.World
import studio.craftory.core.api.energy.EnergyAccess
import studio.craftory.core.api.energy.EnergyMemberType
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.energy.EnergyWorld
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

object CraftoryCore {
    private val modules = ConcurrentSkipListMap<String, String>()
    private val energyTypes = ConcurrentHashMap<String, EnergyMemberType>()

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

    fun energyAccess(world: World): EnergyAccess {
        world.debugAssertInTickingThread()
        val energy = world.chunkStore.store.getResource(EnergyWorld.type)
        return EnergyAccess(energy, world)
    }

    private val ENERGY_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
}
