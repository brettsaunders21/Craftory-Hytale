package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellStrength
import java.util.UUID
import kotlin.random.Random

class WellLogicTest {
    @Test
    fun `scan scoring distinguishes perfect and sloppy rings`() {
        val perfect = ScanScoring.score(100, listOf(100, 100, 100), 40, 8)
        val sloppy = ScanScoring.score(100, listOf(110, 125, 135), 40, 8)
        assertTrue(perfect.perfect)
        assertEquals(listOf(0L, 0L, 0L), perfect.errors)
        assertFalse(sloppy.perfect)
        assertEquals(listOf(10L, 25L, 25L), sloppy.errors)
    }

    @Test
    fun `ring timing helpers share scan scoring periods and phase errors`() {
        assertEquals(40L, ScanScoring.ringPeriod(0, 40))
        assertEquals(50L, ScanScoring.ringPeriod(1, 40))
        assertEquals(60L, ScanScoring.ringPeriod(2, 40))
        assertEquals(10L, ScanScoring.phaseError(100, 110, 0, 40))
    }

    @Test
    fun `insight values use strength and perfect well bonus`() {
        assertEquals(20L, InsightRules.well(WellStrength.FLICKER, true, false))
        assertEquals(30L, InsightRules.well(WellStrength.FLICKER, true, true))
        assertEquals(2L, InsightRules.well(WellStrength.FLICKER, false, true))
    }

    @Test
    fun `repeat scans grant ten percent with minimum one insight`() {
        assertEquals(4L, InsightRules.well(WellStrength.STEADY, false, false))
        assertEquals(1L, InsightRules.mob(false))
        assertEquals(1L, InsightRules.oreCrystal(false))
        assertEquals(1L, InsightRules.machine(false, null))
    }

    @Test
    fun `birth rules support deterministic forced outcomes`() {
        val rules = WellBirthRules(Random(42))
        assertTrue(rules.stormcall(1.0))
        assertFalse(rules.rainFlicker(0.0))
        assertTrue(rules.surfaceOffset() in 8..24)
    }

    @Test
    fun `cooldown helper handles missing and elapsed timestamps`() {
        assertTrue(WellBirthRules.cooldownElapsed(null, 100, 20))
        assertFalse(WellBirthRules.cooldownElapsed(100, 119, 20))
        assertTrue(WellBirthRules.cooldownElapsed(100, 120, 20))
    }

    @Test
    fun `storm vane bonus raises steady chance to a capped maximum`() {
        assertEquals(0.15, StormHuntRules.stormSteadyChance(0))
        assertEquals(0.25, StormHuntRules.stormSteadyChance(1))
        assertEquals(0.55, StormHuntRules.stormSteadyChance(4))
        assertEquals(0.55, StormHuntRules.stormSteadyChance(20))
        assertEquals(WellStrength.STEADY, StormHuntRules.stormStrength(0, 0.14))
        assertEquals(WellStrength.FLICKER, StormHuntRules.stormStrength(0, 0.15))
        assertEquals(WellStrength.STEADY, StormHuntRules.stormStrength(20, 0.54))
    }

    @Test
    fun `strong wild well budget is scoped to 512 block regions`() {
        assertEquals(0 to 0, StormHuntRules.regionOf(511, 511))
        assertEquals(1 to 1, StormHuntRules.regionOf(512, 512))
        assertEquals(-1 to -1, StormHuntRules.regionOf(-1, -1))
        assertTrue(StormHuntRules.canSpawnStrongWell(0, 1))
        assertFalse(StormHuntRules.canSpawnStrongWell(1, 1))
    }

    @Test
    fun `Stormcall budget uses strike region when it crosses a border`() {
        val existingStrongWellPositions = listOf(511 to 0)
        val playerRegionCount = StormHuntRules.strongWellCountInRegion(511, 0, existingStrongWellPositions)
        val strikeRegionCount = StormHuntRules.strongWellCountInRegion(520, 0, existingStrongWellPositions)
        assertEquals(1, playerRegionCount)
        assertEquals(0, strikeRegionCount)
        assertFalse(StormHuntRules.canSpawnStrongWell(playerRegionCount, 1))
        assertTrue(StormHuntRules.canSpawnStrongWell(strikeRegionCount, 1))
    }

    @Test
    fun `per-jar drain applies each temperament weather independently`() {
        val spark = JarCarrierRules.drainForSecond(50.0, Temperament.SPARK, WeatherKind.STORM)
        val ember = JarCarrierRules.drainForSecond(50.0, Temperament.EMBER, WeatherKind.STORM)
        assertEquals(49.8, spark, 0.0001)
        assertEquals(49.7, ember, 0.0001)
    }

    @Test
    fun `burst consumes only after a successful Well spawn`() {
        assertEquals(JarCarrierRules.BurstResult.RETRY_AT_ONE, JarCarrierRules.burstResult(0.0, false))
        assertEquals(JarCarrierRules.BurstResult.CONSUME, JarCarrierRules.burstResult(0.0, true))
        assertEquals(JarCarrierRules.BurstResult.RETAIN, JarCarrierRules.burstResult(1.0, false))
    }

    @Test
    fun `weather dislikes match the temperament table`() {
        assertTrue(StormHuntRules.dislikesWeather(Temperament.SPARK, WeatherKind.RAIN))
        assertFalse(StormHuntRules.dislikesWeather(Temperament.SPARK, WeatherKind.STORM))
        assertTrue(StormHuntRules.dislikesWeather(Temperament.EMBER, WeatherKind.RAIN))
        assertTrue(StormHuntRules.dislikesWeather(Temperament.EMBER, WeatherKind.STORM))
        Temperament.entries.filterNot { it == Temperament.SPARK || it == Temperament.EMBER }.forEach {
            assertFalse(StormHuntRules.dislikesWeather(it, WeatherKind.RAIN))
            assertFalse(StormHuntRules.dislikesWeather(it, WeatherKind.STORM))
        }
        assertEquals(WeatherKind.STORM, StormHuntRules.weatherKind("Zone1_Storm"))
        assertEquals(WeatherKind.STORM, StormHuntRules.weatherKind("Zone2_Thunder_Storm"))
        assertEquals(WeatherKind.STORM, StormHuntRules.weatherKind("Zone4_Swamp_Storm"))
        assertEquals(WeatherKind.STORM, StormHuntRules.weatherKind("Zone4_Storm"))
        assertEquals(WeatherKind.RAIN, StormHuntRules.weatherKind("Zone1_Rain"))
        assertEquals(WeatherKind.CLEAR, StormHuntRules.weatherKind("Zone1_Sunny"))
        assertEquals(WeatherKind.CLEAR, StormHuntRules.weatherKind("Zone1_Sunny_Fireflies"))
        listOf(
            "Zone2_Sand_Storm",
            "Zone3_Snow_Storm",
            "Zone4_AshWastes_Storm",
            "Cave_Goblin_Void_Event_Storm",
            "Skylands_Rapid_Marsh_Stormy",
            "Zone1_Cloudy_Medium",
            "Blood_Moon",
        ).forEach { assertEquals(WeatherKind.OTHER, StormHuntRules.weatherKind(it)) }
    }

    @Test
    fun `stake spacing identifies gaps outside forty degrees`() {
        assertEquals(emptySet<Int>(), TetherRules.stakeSpread(listOf(0.0, 120.0, 240.0)))
        assertEquals(setOf(0, 1, 2), TetherRules.stakeSpread(listOf(0.0, 40.0, 180.0)))
        assertEquals(3, TetherRules.requiredStakeCount(WellStrength.FLICKER))
        assertEquals(4, TetherRules.requiredStakeCount(WellStrength.ROARING))
        assertEquals(6, TetherRules.requiredStakeCount(WellStrength.PRIMAL))
        assertTrue(
            TetherRules.tetherComplete(
                studio.craftory.core.api.well.WellState.DORMANT,
                WellStrength.FLICKER,
                3,
                0,
            ),
        )
        assertFalse(
            TetherRules.tetherComplete(
                studio.craftory.core.api.well.WellState.WILD,
                WellStrength.STEADY,
                3,
                0,
            ),
        )
    }

    @Test
    fun `jar capture requires three presses inside the timing window`() {
        assertTrue(TetherRules.capturePressIntervalValid(4))
        assertTrue(TetherRules.capturePressIntervalValid(40))
        assertFalse(TetherRules.capturePressIntervalValid(3))
        assertFalse(TetherRules.capturePressIntervalValid(41))
        assertFalse(TetherRules.captureComplete(2))
        assertTrue(TetherRules.captureComplete(3))
    }

    @Test
    fun `capture press state completes on the third press and resets on a miss`() {
        val state = WellCaptureState()
        val player = UUID.randomUUID()
        assertEquals(CapturePressResult.Progress(1), recordCapturePress(state, player, "well", 100))
        assertEquals(CapturePressResult.Progress(2), recordCapturePress(state, player, "well", 110))
        assertEquals(CapturePressResult.Complete, recordCapturePress(state, player, "well", 120))
        assertFalse(state.progressByPlayer.containsKey(player))

        assertEquals(CapturePressResult.Progress(1), recordCapturePress(state, player, "well", 200))
        assertEquals(CapturePressResult.Miss, recordCapturePress(state, player, "well", 241))
        assertFalse(state.progressByPlayer.containsKey(player))
    }

    @Test
    fun `stability drains for time damage and disliked weather and stops at zero`() {
        assertEquals(87.0, JarCarrierRules.stabilityAfter(100.0, 10.0, 1, 10.0))
        assertEquals(0.0, JarCarrierRules.stabilityAfter(5.0, 60.0, 2, 30.0))
    }

    @Test
    fun `zero ambush chance disables the roll`() {
        assertFalse(JarCarrierRules.ambushRoll(0.0, Random(1)))
        assertTrue(JarCarrierRules.ambushRoll(1.0, Random(1)))
    }

    @Test
    fun `carrier bursts only when stability reaches zero`() {
        assertFalse(JarCarrierRules.shouldBurst(0.01))
        assertTrue(JarCarrierRules.shouldBurst(0.0))
        assertTrue(JarCarrierRules.shouldBurst(-1.0))
    }

    @Test
    fun `jar carrier speed is reduced by thirty percent`() {
        assertEquals(0.7f, JarCarrierRules.slowedSpeed(1.0f))
        assertEquals(0.84f, JarCarrierRules.slowedSpeed(1.2f), 0.000001f)
    }
}
