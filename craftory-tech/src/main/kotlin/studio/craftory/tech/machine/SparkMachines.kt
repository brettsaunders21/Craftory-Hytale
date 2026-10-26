package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.ConverterMath
import studio.craftory.core.api.energy.EnergyFaces
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.HarmonyRules
import studio.craftory.tech.well.TasteBlocks
import studio.craftory.tech.well.WellSelfTestExclusions

class ResonatorState(
    var crystalItemId: String = "",
    var conversions: Long = 0,
    var tuning: Int = 30,
    var clouded: Boolean = false,
    var lastSavedConversions: Long = 0,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> =
        ResonatorState(crystalItemId, conversions, tuning, clouded, lastSavedConversions)

    companion object {
        lateinit var type: ComponentType<ChunkStore, ResonatorState>

        val CODEC: BuilderCodec<ResonatorState> =
            BuilderCodec
                .builder(ResonatorState::class.java, ::ResonatorState)
                .append(
                    KeyedCodec("CrystalItemId", Codec.STRING),
                    { state, value -> state.crystalItemId = value },
                    { it.crystalItemId },
                ).add()
                .append(
                    KeyedCodec("Conversions", Codec.LONG),
                    { state, value -> state.conversions = value },
                    { it.conversions },
                ).add()
                .append(
                    KeyedCodec("Tuning", Codec.INTEGER),
                    { state, value -> state.tuning = value.coerceIn(25, 40) },
                    { it.tuning },
                ).add()
                .append(
                    KeyedCodec("Clouded", Codec.BOOLEAN),
                    { state, value -> state.clouded = value },
                    { it.clouded },
                ).add()
                .build()
    }
}

class CradleState(
    var harmony: Int = 0,
    var seatedStrength: String = "",
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = CradleState(harmony, seatedStrength)

    companion object {
        lateinit var type: ComponentType<ChunkStore, CradleState>

        val CODEC: BuilderCodec<CradleState> =
            BuilderCodec
                .builder(CradleState::class.java, ::CradleState)
                .append(
                    KeyedCodec("Harmony", Codec.INTEGER),
                    { state, value -> state.harmony = value.coerceIn(0, 5) },
                    { it.harmony },
                ).add()
                .append(
                    KeyedCodec("SeatedStrength", Codec.STRING),
                    { state, value -> state.seatedStrength = value },
                    { it.seatedStrength },
                ).add()
                .build()
    }
}

internal class CrackleCoilSystem : EntityTickingSystem<ChunkStore>() {
    private val cursor = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (!CraftoryCore.isEnergyTick(store)) return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        if (member.typeId != CraftoryIds.CRACKLE_COIL_TYPE) return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        val energy = CraftoryCore.energyAccess(world)
        if (!blockState.fillWorldPos(commandBuffer, cursor)) return
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            energy.setDemand(BlockPos.pack(cursor.x, cursor.y, cursor.z), 0)
            return
        }
        val production = burningProduction(store, cursor.x, cursor.y - 1, cursor.z)
        val id = BlockPos.pack(cursor.x, cursor.y, cursor.z)
        energy.setDemand(id, ConverterMath.skim(production, 5))
        energy.setConverterOutput(id, energy.converterInputDelivered(id), DIM_GLIMMER_RANK)
    }

    private fun burningProduction(
        store: Store<ChunkStore>,
        x: Int,
        y: Int,
        z: Int,
    ): Long {
        val section =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(x, y, z) ?: return 0
        val ref = BlockModule.getBlockEntity(store, section, x, y, z) ?: return 0
        val member = store.getComponent(ref, EnergyMember.type) ?: return 0
        val burner = store.getComponent(ref, BurnerState.type)
        if (burner?.burning == true && member.typeId == CraftoryIds.BURNER_TYPE) return member.production
        val heart = store.getComponent(ref, JunkHeartState.type)
        val controller = store.getComponent(ref, MultiblockController.type)
        return if (
            heart?.burning == true && controller?.state == MultiblockState.FORMED &&
            member.typeId == CraftoryIds.JUNK_HEART_TYPE
        ) {
            member.production
        } else {
            0
        }
    }

    private companion object {
        val DIM_GLIMMER_RANK = requireNotNull(CraftoryCore.gradeRank("dim_glimmer"))
    }
}

internal class ResonatorSystem : EntityTickingSystem<ChunkStore>() {
    private val cursor = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(ResonatorState.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    @Suppress("CyclomaticComplexMethod")
    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (!CraftoryCore.isEnergyTick(store)) return
        val state = chunk.getComponent(index, ResonatorState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        if (member.typeId != CraftoryIds.RESONATOR_TYPE) return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!blockState.fillWorldPos(commandBuffer, cursor)) return
        val id = BlockPos.pack(cursor.x, cursor.y, cursor.z)
        val energy = CraftoryCore.energyAccess(store.externalData.world)
        if (!CraftoryCore.isAreaActive(store, blockState)) {
            energy.setDemand(id, 0)
            energy.setConverterOutput(id, 0, DIM_GLIMMER_RANK)
            return
        }
        val clear = state.crystalItemId.isNotEmpty() && !state.clouded
        energy.setDemand(id, if (clear) INPUT_DEMAND else 0)
        val delivered = energy.converterInputDelivered(id)
        val output = if (clear) ConverterMath.convert(delivered.coerceAtMost(INPUT_DEMAND), state.tuning) else 0
        energy.setConverterOutput(id, output, DIM_GLIMMER_RANK)
        if (output > 0) {
            state.conversions++
            if (state.conversions >= ResonatorRuntimeConfig.crystalUses) state.clouded = true
            if (state.conversions - state.lastSavedConversions >= SAVE_CONVERSION_INTERVAL) {
                state.lastSavedConversions = state.conversions
                blockState.markNeedsSaving(commandBuffer)
            }
        }
        if (state.clouded && clear) blockState.markNeedsSaving(commandBuffer)
    }

    private companion object {
        const val INPUT_DEMAND = 40L
        const val SAVE_CONVERSION_INTERVAL = 100L
        val DIM_GLIMMER_RANK = requireNotNull(CraftoryCore.gradeRank("dim_glimmer"))
    }
}

internal class CradleSystem : EntityTickingSystem<ChunkStore>() {
    private val cursor = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(CradleState.type, EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.BEFORE, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (!CraftoryCore.isEnergyTick(store)) return
        val state = chunk.getComponent(index, CradleState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        if (member.typeId != CraftoryIds.WELL_CRADLE_TYPE) return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!blockState.fillWorldPos(commandBuffer, cursor)) return
        if (!CraftoryCore.isAreaActive(store, blockState)) return
        val wellPosition = BlockPos(cursor.x, cursor.y + 1, cursor.z)
        if (WellSelfTestExclusions.isWell(wellPosition)) return
        updateProduction(store, wellPosition, state, member, blockState, commandBuffer, cursor)
    }

    private fun updateProduction(
        store: Store<ChunkStore>,
        wellPosition: BlockPos,
        state: CradleState,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        commandBuffer: CommandBuffer<ChunkStore>,
        cursor: Vector3i,
    ) {
        val wellRef = findWell(store, wellPosition)
        val well = wellRef?.let { store.getComponent(it, Glimmerwell.type) }
        if (well != null && state.seatedStrength != well.strength.name) {
            state.seatedStrength = well.strength.name
            blockState.markNeedsSaving(commandBuffer)
        }
        val base = well?.takeIf { it.state == WellState.SEATED }?.let { WellOutput.base(it.strength) } ?: 0L
        val uncorrupted = base * state.harmony.coerceIn(0, 5)
        val amount = if (well?.corrupted == true) uncorrupted / 2 else uncorrupted
        if (member.production != amount) {
            CraftoryCore
                .energyAccess(store.externalData.world)
                .setProduction(BlockPos.pack(cursor.x, cursor.y, cursor.z), amount, DIM_GLIMMER_RANK)
        }
    }

    companion object {
        private val DIM_GLIMMER_RANK = requireNotNull(CraftoryCore.gradeRank("dim_glimmer"))

        fun findWell(
            store: Store<ChunkStore>,
            pos: BlockPos,
        ): com.hypixel.hytale.component.Ref<ChunkStore>? {
            val section =
                store.externalData.world.chunkStore
                    .getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z)
                    ?: return null
            val ref = BlockModule.getBlockEntity(store, section, pos.x, pos.y, pos.z) ?: return null
            return ref.takeIf { store.getComponent(it, Glimmerwell.type) != null }
        }

        @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
        fun harmonyAt(
            store: Store<ChunkStore>,
            cradle: BlockPos,
            taste: String,
        ): Int {
            var matches = 0
            val wellCell = BlockPos(cradle.x, cradle.y + 1, cradle.z)
            for (x in cradle.x - 4..cradle.x + 4) {
                for (y in cradle.y - 4..cradle.y + 4) {
                    for (z in cradle.z - 4..cradle.z + 4) {
                        if (x == cradle.x && y == cradle.y && z == cradle.z) continue
                        if (x == wellCell.x && y == wellCell.y && z == wellCell.z) continue
                        val sectionRef =
                            store.externalData.world.chunkStore
                                .getChunkSectionReferenceAtBlock(x, y, z)
                                ?: continue
                        val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: continue
                        val id = section.get(x and 31, y and 31, z and 31)
                        val block = BlockType.getAssetMap().getAsset(id)?.id ?: continue
                        if (TasteBlocks.matches(taste, block)) matches++
                    }
                }
            }
            return HarmonyRules.harmony(matches)
        }
    }
}

internal object ResonatorRuntimeConfig {
    var crystalUses: Long = 200
}
