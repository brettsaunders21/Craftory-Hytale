package studio.craftory.tech.notebook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotebookEventDataCodecTest {
    @Test
    fun `event data codec round trips action and page id`() {
        val source =
            NotebookPageUi.EventData().apply {
                action = "SelectPage"
                pageId = NotebookPages.pages.first().id
            }
        val encoded = NotebookPageUi.EventData.CODEC.encode(source) ?: error("codec encode returned null")
        val decoded = NotebookPageUi.EventData.CODEC.decode(encoded) ?: error("codec decode returned null")

        assertEquals(source.action, decoded.action)
        assertEquals(source.pageId, decoded.pageId)
    }
}
