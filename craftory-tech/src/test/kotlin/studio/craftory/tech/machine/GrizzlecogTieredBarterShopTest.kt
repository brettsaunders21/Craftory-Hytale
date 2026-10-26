package studio.craftory.tech.machine

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrizzlecogTieredBarterShopTest {
    @Test
    fun `shop files contain the expected cumulative tier outputs`() {
        val tierOne = outputs(1)
        val tierTwo = outputs(2)
        val tierThree = outputs(3)

        assertEquals(commonOutputs, tierOne)
        assertEquals(commonOutputs + uncommonOutputs, tierTwo)
        assertEquals(commonOutputs + uncommonOutputs, tierThree)
        assertTrue(tierTwo.containsAll(tierOne))
        assertTrue(tierThree.containsAll(tierTwo))
    }

    @Test
    fun `tier one crew resolves the tier one shop`() {
        assertEquals("Craftory_Grizzlecog_T1", grizzlecogShopId(GRIZZLECOG_SHOP_PREFIX, 1))
        assertEquals("Craftory_Grizzlecog_T1", grizzlecogShopId(GRIZZLECOG_SHOP_PREFIX, 0))
        assertEquals("Craftory_Grizzlecog_T3", grizzlecogShopId(GRIZZLECOG_SHOP_PREFIX, 4))
    }

    private fun outputs(tier: Int): Set<String> {
        val url =
            requireNotNull(
                javaClass.classLoader.getResource("Server/BarterShops/Craftory_Grizzlecog_T$tier.json"),
            )
        val shop = JsonParser.parseString(url.readText()).asJsonObject
        return shop
            .getAsJsonArray("TradeSlots")
            .map { slot ->
                slot.asJsonObject
                    .getAsJsonObject("Trade")
                    .getAsJsonObject("Output")
                    .get("ItemId")
                    .asString
            }.toSet()
    }

    companion object {
        private val commonOutputs =
            setOf(
                "Craftory_Boom_Valve",
                "Craftory_Boomshroom_Burner",
                "Craftory_Catch_Net",
                "Craftory_Codex",
                "Craftory_Crank_A_Fizz",
                "Craftory_Fizz_Canister",
                "Craftory_Fizz_Charge",
                "Craftory_Fizz_Damper",
                "Craftory_Glimmer_Lens",
                "Craftory_Gob_Jar",
                "Craftory_Grizzlecog_Receipt",
                "Craftory_Gutter_Gear",
                "Craftory_Gutterpipe",
                "Craftory_Junk_Heart_Firebox",
                "Craftory_Muffler",
                "Craftory_Rattlewheel",
                "Craftory_Scrap_Plating",
                "Craftory_Scrap_Plating_Bodge",
                "Craftory_Shroom_Bed",
                "Craftory_Sloshtank",
                "Craftory_Squawk_Box",
                "Craftory_Squint_Goggles",
                "Craftory_Storm_Vane",
                "Craftory_Tinkers_Hammer",
                "Craftory_Tinkers_Notebook",
                "Craftory_Tinkers_Wrench",
                "Craftory_Wellhead",
            )

        private val uncommonOutputs =
            setOf(
                "Craftory_Gasbag",
                "Craftory_Gutter_Boiler",
                "Craftory_Hurl_Tube",
                "Craftory_Snatcher_Claw",
            )
    }
}
