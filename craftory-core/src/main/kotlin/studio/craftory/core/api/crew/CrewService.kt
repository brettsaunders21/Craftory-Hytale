package studio.craftory.core.api.crew

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
class CrewService private constructor(
    private val file: Path,
    private val debounceMillis: Long,
    private val scheduler: ScheduledExecutorService,
) : AutoCloseable {
    constructor(file: Path) : this(file, SAVE_DEBOUNCE_MILLIS, createScheduler())

    internal constructor(
        file: Path,
        debounceMillis: Long,
    ) : this(file, debounceMillis, createScheduler())

    private data class CrewData(
        val id: UUID,
        var name: String,
        var owner: UUID,
        val members: MutableSet<UUID>,
        val pendingInvites: MutableSet<UUID>,
        val knowledge: MutableSet<String>,
        var tier: Int,
        var solo: Boolean,
    ) {
        fun snapshot(): Crew =
            Crew(
                id,
                name,
                owner,
                members.toSet(),
                pendingInvites.toSet(),
                knowledge.toSet(),
                tier,
                solo,
            )
    }

    private data class StoredCrew(
        var id: String = "",
        var name: String = "",
        var owner: String = "",
        var members: List<String> = emptyList(),
        var pendingInvites: List<String> = emptyList(),
        var knowledge: List<String> = emptyList(),
        var tier: Int = 1,
        var solo: Boolean = false,
    )

    private data class StoredData(
        var crews: List<StoredCrew> = emptyList(),
    )

    private val lock = Any()
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val crews = LinkedHashMap<UUID, CrewData>()
    private val playerCrews = HashMap<UUID, UUID>()
    private val knowledgeListeners = CopyOnWriteArrayList<(Crew, String) -> Unit>()
    private val crewChangedListeners = CopyOnWriteArrayList<(Crew) -> Unit>()
    private var scheduledFlush: ScheduledFuture<*>? = null
    private var lastFlushNanos = 0L
    private var dirty = false
    private var writing = false
    private var closed = false

    @Volatile
    private var completedWrites = 0

    internal val persistenceWriteCount: Int
        get() = completedWrites

    init {
        require(debounceMillis > 0) { "Crew save delay must be positive" }
        load()
    }

    fun crewOf(playerUuid: UUID): Crew =
        synchronized(lock) {
            ensureOpen()
            crewForPlayerLocked(playerUuid).snapshot()
        }

    fun create(
        owner: UUID,
        name: String,
    ): Crew {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Crew name must not be blank" }
        require(normalizedName.length <= MAX_NAME_LENGTH) { "Crew name must be at most $MAX_NAME_LENGTH characters" }
        val created =
            synchronized(lock) {
                ensureOpen()
                val crew = crewForPlayerLocked(owner)
                check(crew.solo) { "You are already in a crew" }
                crew.name = normalizedName
                crew.owner = owner
                crew.solo = false
                markDirtyLocked()
                crew.snapshot()
            }
        notifyCrewChanged(listOf(created))
        return created
    }

    fun invite(
        crew: Crew,
        target: UUID,
    ): Boolean =
        synchronized(lock) {
            ensureOpen()
            val current = crews[crew.id] ?: return@synchronized false
            if (current.solo || target in current.members) return@synchronized false
            if (!current.pendingInvites.add(target)) return@synchronized false
            markDirtyLocked()
            true
        }

    fun accept(
        target: UUID,
        crewId: UUID,
    ): Crew? {
        val accepted =
            synchronized(lock) {
                ensureOpen()
                val destination = crews[crewId] ?: return@synchronized null
                if (destination.solo || target !in destination.pendingInvites) return@synchronized null
                val current = playerCrews[target]?.let(crews::get)
                if (current != null && !current.solo) return@synchronized null

                val solo = current ?: crewForPlayerLocked(target)
                destination.knowledge.addAll(solo.knowledge)
                destination.tier = maxOf(destination.tier, solo.tier)
                detachMemberLocked(solo, target)
                destination.members.add(target)
                destination.pendingInvites.remove(target)
                playerCrews[target] = destination.id
                markDirtyLocked()
                destination.snapshot()
            }
        accepted?.let { notifyCrewChanged(listOf(it)) }
        return accepted
    }

    fun leave(playerUuid: UUID): Crew {
        val (departing, changedCrews) =
            synchronized(lock) {
                ensureOpen()
                val current = crewForPlayerLocked(playerUuid)
                if (current.solo) return@synchronized current.snapshot() to emptyList()
                val carriedKnowledge = current.knowledge.toSet()
                val carriedTier = current.tier
                detachMemberLocked(current, playerUuid)
                if (current.owner == playerUuid && current.members.isNotEmpty()) {
                    current.owner = current.members.minBy(UUID::toString)
                }
                val solo = createSoloLocked(playerUuid, carriedKnowledge, carriedTier)
                val changed =
                    buildList {
                        add(solo.snapshot())
                        if (current.members.isNotEmpty()) add(current.snapshot())
                    }
                solo.snapshot() to changed
            }
        notifyCrewChanged(changedCrews)
        return departing
    }

    fun kick(
        owner: UUID,
        target: UUID,
    ): Boolean {
        val changedCrews =
            synchronized(lock) {
                ensureOpen()
                val crew = playerCrews[owner]?.let(crews::get) ?: return@synchronized null
                if (crew.solo || crew.owner != owner) return@synchronized null
                if (target == owner || target !in crew.members) return@synchronized null
                val carriedKnowledge = crew.knowledge.toSet()
                val carriedTier = crew.tier
                crew.pendingInvites.remove(target)
                detachMemberLocked(crew, target)
                val solo = createSoloLocked(target, carriedKnowledge, carriedTier)
                markDirtyLocked()
                listOf(solo.snapshot(), crew.snapshot())
            } ?: return false
        notifyCrewChanged(changedCrews)
        return true
    }

    fun hasKnowledge(
        playerUuid: UUID,
        id: String,
    ): Boolean =
        synchronized(lock) {
            ensureOpen()
            crewForPlayerLocked(playerUuid).knowledge.contains(id)
        }

    fun grantKnowledge(
        playerUuid: UUID,
        id: String,
    ): Boolean {
        require(KNOWLEDGE_ID.matches(id)) { "Knowledge id must be namespaced: $id" }
        val granted =
            synchronized(lock) {
                ensureOpen()
                val crew = crewForPlayerLocked(playerUuid)
                if (!crew.knowledge.add(id)) return@synchronized null
                markDirtyLocked()
                crew.snapshot()
            } ?: return false
        knowledgeListeners.forEach { listener ->
            try {
                listener(granted, id)
            } catch (exception: Exception) {
                LOGGER.log(Level.WARNING, "Crew knowledge listener failed for $id", exception)
            }
        }
        return true
    }

    fun tierOf(playerUuid: UUID): Int =
        synchronized(lock) {
            ensureOpen()
            crewForPlayerLocked(playerUuid).tier
        }

    fun setTier(
        crewId: UUID,
        tier: Int,
    ): Boolean {
        require(tier >= 1) { "Crew tier must be at least 1" }
        return synchronized(lock) {
            ensureOpen()
            val crew = crews[crewId] ?: return@synchronized false
            if (crew.tier == tier) return@synchronized false
            crew.tier = tier
            markDirtyLocked()
            true
        }
    }

    fun onKnowledgeGranted(listener: (Crew, String) -> Unit): AutoCloseable {
        knowledgeListeners.add(listener)
        return AutoCloseable { knowledgeListeners.remove(listener) }
    }

    fun onCrewChanged(listener: (Crew) -> Unit): AutoCloseable {
        crewChangedListeners.add(listener)
        return AutoCloseable { crewChangedListeners.remove(listener) }
    }

    fun flush() {
        synchronized(lock) {
            scheduledFlush?.cancel(false)
            scheduledFlush = null
        }
        await(scheduler.submit(::writePending))
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            scheduledFlush?.cancel(false)
            scheduledFlush = null
        }
        try {
            await(scheduler.submit(::writePending))
        } finally {
            scheduler.shutdown()
        }
    }

    private fun crewForPlayerLocked(playerUuid: UUID): CrewData {
        val existing = playerCrews[playerUuid]?.let(crews::get)
        if (existing != null) return existing
        return createSoloLocked(playerUuid, emptySet())
    }

    private fun createSoloLocked(
        playerUuid: UUID,
        knowledge: Set<String>,
        tier: Int = 1,
    ): CrewData {
        val crew =
            CrewData(
                id = UUID.randomUUID(),
                name = SOLO_CREW_NAME,
                owner = playerUuid,
                members = mutableSetOf(playerUuid),
                pendingInvites = mutableSetOf(),
                knowledge = knowledge.toMutableSet(),
                tier = tier,
                solo = true,
            )
        crews[crew.id] = crew
        playerCrews[playerUuid] = crew.id
        markDirtyLocked()
        return crew
    }

    private fun notifyCrewChanged(changedCrews: Iterable<Crew>) {
        changedCrews.forEach { crew ->
            crewChangedListeners.forEach { listener ->
                try {
                    listener(crew)
                } catch (exception: Exception) {
                    LOGGER.log(Level.WARNING, "Crew change listener failed for ${crew.id}", exception)
                }
            }
        }
    }

    private fun detachMemberLocked(
        crew: CrewData,
        playerUuid: UUID,
    ) {
        crew.members.remove(playerUuid)
        playerCrews.remove(playerUuid)
        if (crew.members.isEmpty()) {
            crews.remove(crew.id)
        }
    }

    private fun markDirtyLocked() {
        dirty = true
        scheduleFlushLocked()
    }

    private fun scheduleFlushLocked() {
        if (closed || writing || scheduledFlush != null) return
        val now = System.nanoTime()
        val intervalNanos = TimeUnit.MILLISECONDS.toNanos(debounceMillis)
        val delayNanos =
            if (lastFlushNanos == 0L) {
                intervalNanos
            } else {
                (lastFlushNanos + intervalNanos - now).coerceAtLeast(0L)
            }
        scheduledFlush = scheduler.schedule(::flushScheduled, delayNanos, TimeUnit.NANOSECONDS)
    }

    private fun flushScheduled() {
        try {
            writePending()
        } catch (exception: Exception) {
            LOGGER.log(Level.SEVERE, "Could not persist Craftory crews", exception)
        }
    }

    private fun writePending() {
        val json =
            synchronized(lock) {
                if (!dirty) return
                writing = true
                dirty = false
                gson.toJson(storedDataLocked())
            }
        try {
            writeAtomically(json)
            completedWrites++
        } catch (exception: Exception) {
            synchronized(lock) { dirty = true }
            throw exception
        } finally {
            synchronized(lock) {
                writing = false
                lastFlushNanos = System.nanoTime()
                if (dirty) scheduleFlushLocked()
            }
        }
    }

    private fun storedDataLocked(): StoredData =
        StoredData(
            crews.values
                .sortedBy { it.id.toString() }
                .map { crew ->
                    StoredCrew(
                        id = crew.id.toString(),
                        name = crew.name,
                        owner = crew.owner.toString(),
                        members = crew.members.map(UUID::toString).sorted(),
                        pendingInvites = crew.pendingInvites.map(UUID::toString).sorted(),
                        knowledge = crew.knowledge.sorted(),
                        tier = crew.tier,
                        solo = crew.solo,
                    )
                },
        )

    private fun writeAtomically(json: String) {
        val destination = file.toAbsolutePath()
        val parent = destination.parent ?: error("Crew data file must have a parent directory")
        Files.createDirectories(parent)
        val temporary = parent.resolve("${destination.fileName}.tmp")
        try {
            Files.writeString(
                temporary,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Suppress("ThrowsCount")
    private fun load() {
        if (!Files.exists(file)) return
        try {
            val stored =
                gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), StoredData::class.java)
                    ?: StoredData()
            stored.crews.forEach(::restoreCrew)
        } catch (exception: IOException) {
            throw IllegalStateException("Could not read Craftory crews from $file", exception)
        } catch (exception: JsonParseException) {
            throw IllegalStateException("Could not parse Craftory crews from $file", exception)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException("Craftory crew data is invalid in $file", exception)
        }
    }

    private fun restoreCrew(stored: StoredCrew) {
        val id = UUID.fromString(stored.id)
        val owner = UUID.fromString(stored.owner)
        val members = stored.members.map(UUID::fromString).toMutableSet()
        require(members.isNotEmpty()) { "Crew $id has no members" }
        require(owner in members) { "Crew $id owner is not a member" }
        require(members.none(playerCrews::containsKey)) { "Player belongs to multiple crews" }
        val crew =
            CrewData(
                id = id,
                name = stored.name.ifBlank { SOLO_CREW_NAME },
                owner = owner,
                members = members,
                pendingInvites = stored.pendingInvites.map(UUID::fromString).toMutableSet(),
                knowledge = stored.knowledge.toMutableSet(),
                tier = stored.tier.coerceAtLeast(1),
                solo = stored.solo,
            )
        crews[id] = crew
        crew.members.forEach { playerCrews[it] = id }
    }

    private fun ensureOpen() {
        check(!closed) { "CrewService is closed" }
    }

    private fun <T> await(future: java.util.concurrent.Future<T>) {
        try {
            future.get()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while persisting Craftory crews", exception)
        } catch (exception: java.util.concurrent.ExecutionException) {
            throw IllegalStateException("Could not persist Craftory crews", exception.cause ?: exception)
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 5_000L
        const val MAX_NAME_LENGTH = 32
        const val SOLO_CREW_NAME = "Solo"
        val KNOWLEDGE_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
        val LOGGER: Logger = Logger.getLogger(CrewService::class.java.name)

        fun createScheduler(): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "Craftory Crew Persistence").apply { isDaemon = true }
            }
    }
}
