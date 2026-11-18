package yancey.chelper.ui.feature.rawtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.*
import kotlinx.serialization.json.*
import yancey.chelper.domain.rawtext.RawtextDocumentSnapshot
import yancey.chelper.domain.rawtext.RawtextMockSnapshot
import yancey.chelper.domain.rawtext.RawtextSegmentSnapshot

class RawtextRendererTest {
    private fun render(vararg segments: RawtextSegmentSnapshot) =
        RawtextRenderer(RawtextDocumentSnapshot(segments.toList(), RawtextMockSnapshot())).render()

    @Test
    fun `adjacent text and line breaks form one JSON item`() {
        val result =
            render(
                RawtextSegmentSnapshot("Text", "hello"),
                RawtextSegmentSnapshot("LineBreak"),
                RawtextSegmentSnapshot("Text", "world"),
            )
        assertEquals("hello\nworld", result.preview.text)
        assertEquals(
            Json.parseToJsonElement("""{"rawtext":[{"text":"hello\nworld"}]}"""),
            Json.parseToJsonElement(result.compressedJson),
        )
        assertEquals(
            Json.parseToJsonElement(result.formattedJson),
            Json.parseToJsonElement(result.compressedJson),
        )
        assertFalse(result.isEmpty)
    }

    @Test
    fun `Minecraft styling continues across segments and resets`() {
        val result =
            render(RawtextSegmentSnapshot("Text", "§c§lA"), RawtextSegmentSnapshot("Text", "B§rC"))
        assertEquals("ABC", result.preview.text)
        val bold = result.preview.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue(bold.any { it.start == 0 && it.end == 1 })
        assertTrue(bold.any { it.start == 1 && it.end == 2 })
        assertTrue(bold.all { it.item.color == Color(0xFFFF5555) })
        assertFalse(bold.any { it.start <= 2 && it.end > 2 })
    }

    @Test
    fun `invalid embedded JSON yields diagnostics and valid fallback output`() {
        val result =
            render(
                RawtextSegmentSnapshot("Translate", translateWithJson = "["),
                RawtextSegmentSnapshot("Conditional", conditionJson = "{", thenJson = "["),
            )
        assertEquals(3, result.validation.size)
        val items =
            Json.parseToJsonElement(result.compressedJson).jsonObject.getValue("rawtext").jsonArray
        assertEquals(JsonArray(emptyList()), items[0].jsonObject["with"])
        val conditional =
            items[1].jsonObject.getValue("with").jsonObject.getValue("rawtext").jsonArray
        assertEquals("@p", conditional[0].jsonObject.getValue("selector").jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), conditional[1].jsonObject["rawtext"])
    }

    @Test
    fun `mock settings alter preview without altering generated commands`() {
        val segments =
            listOf(
                RawtextSegmentSnapshot("Selector", selector = "@p"),
                RawtextSegmentSnapshot("Score"),
            )
        val a =
            RawtextRenderer(
                    RawtextDocumentSnapshot(
                        segments,
                        RawtextMockSnapshot(p = "Alice", score = "42"),
                    )
                )
                .render()
        val b =
            RawtextRenderer(
                    RawtextDocumentSnapshot(segments, RawtextMockSnapshot(p = "Bob", score = "7"))
                )
                .render()
        assertEquals(a.compressedJson, b.compressedJson)
        assertTrue(a.preview.text.contains("Alice"))
        assertTrue(a.preview.text.contains("42"))
        assertTrue(b.preview.text.contains("Bob"))
    }

    @Test
    fun `empty editor still produces valid rawtext`() {
        assertTrue(render().isEmpty)
        assertEquals("", render().preview.text)
        assertEquals("""{"rawtext":[{"text":""}]}""", render().compressedJson)
        assertFalse(render(RawtextSegmentSnapshot("LineBreak")).isEmpty)
    }
}
