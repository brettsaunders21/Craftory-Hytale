package studio.craftory.core.api

import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyAccess
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.EnergyMemberType
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.api.transport.TubeAccess
import studio.craftory.core.energy.AnchorCoverageIndex
import studio.craftory.core.energy.EnergyClock
import studio.craftory.core.energy.EnergyWorld
import studio.craftory.core.transport.TubeWorld
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

@Suppress("TooManyFunctions")
object CraftoryCore {
    private val modules = ConcurrentSkipListMap<String, String>()
    private val energyTypes = ConcurrentHashMap<String, EnergyMemberType>()
    private val commandHandlers = ConcurrentHashMap<String, (CommandContext) -> CompletableFuture<Void>>()
    private val inspectableComponents =
        ConcurrentHashMap<String, (ComponentAccessor<ChunkStore>, Ref<ChunkStore>) -> Inspectable?>()

    @Volatile
    private var currentCrewService: CrewService? = null

    @Volatile
    private var currentAnchorMaxChargeMinutes = 240

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

    fun registerCrewService(service: CrewService) {
        check(currentCrewService == null) { "Crew service is already registered" }
        currentCrewService = service
    }

    fun crewService(): CrewService = checkNotNull(currentCrewService) { "Crew service is unavailable" }

    fun unregisterCrewService(service: CrewService) {
        if (currentCrewService === service) currentCrewService = null
    }

    fun energyAccess(world: World): EnergyAccess {
        world.debugAssertInTickingThread()
        val energy = world.chunkStore.store.getResource(EnergyWorld.type)
        return EnergyAccess(energy, world)
    }

    fun tubeAccess(world: World): TubeAccess {
        world.debugAssertInTickingThread()
        return TubeAccess(world.chunkStore.store.getResource(TubeWorld.type), world)
    }

    fun gradeRank(id: String): Int? = GradeTable.DEFAULT.rankOf(id)

    fun isEnergyTick(store: Store<ChunkStore>): Boolean {
        val world = store.externalData.world
        return store.getResource(EnergyClock.type).isSolveTick(world.tick)
    }

    fun anchorMaxChargeMinutes(): Int = currentAnchorMaxChargeMinutes

    fun setAnchorMaxChargeMinutes(minutes: Int) {
        setAnchorMaxChargeMinutesInMemory(minutes)
    }

    fun setAnchorMaxChargeMinutesInMemory(minutes: Int) {
        require(minutes >= 0) { "Anchor maximum charge must not be negative" }
        currentAnchorMaxChargeMinutes = minutes
    }

    fun setAnchorCoverage(
        store: Store<ChunkStore>,
        id: Long,
        chunkX: Int,
        chunkZ: Int,
        active: Boolean,
    ) {
        store.getResource(AnchorCoverageIndex.type).setAnchor(id, chunkX, chunkZ, active)
    }

    fun removeAnchorCoverage(
        store: Store<ChunkStore>,
        id: Long,
    ) {
        store.getResource(AnchorCoverageIndex.type).removeAnchor(id)
    }

    fun isAreaActive(
        store: Store<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
    ): Boolean = isAreaActive(store, blockState.sectionRef)

    fun isAreaActive(
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
    ): Boolean {
        if (!sectionRef.isValid) return false
        val nonTicking = ChunkStore.REGISTRY.getNonTickingComponentType()
        if (!store.getArchetype(sectionRef).contains(nonTicking)) return true
        val section = store.getComponent(sectionRef, ChunkSection.getComponentType()) ?: return false
        val chunkIndex = ChunkUtil.indexChunk(section.x, section.z)
        return store.getResource(AnchorCoverageIndex.type).covers(chunkIndex)
    }

    private val ENERGY_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
}
