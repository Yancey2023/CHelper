package yancey.chelper.data.mcd

import kotlin.test.*
import yancey.chelper.domain.library.BlockType
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.MCDBlock
import yancey.chelper.domain.library.parseMCDStructure

class McdSerializerTest {
    @Test
    fun attributesAndLiteralCommandsSurviveExportAndParse() {
        val blocks =
            listOf(
                MCDBlock(
                    BlockType.IMPULSE,
                    conditional = true,
                    alwaysActive = false,
                    needsRedstone = true,
                    tickDelay = 8,
                    command = "say impulse",
                ),
                MCDBlock(BlockType.REPEAT, command = "say repeat"),
                MCDBlock(BlockType.CHAT, command = "say chat"),
            )
        val source = serializeBlocksToMCDv2("迁移测试", blocks, mapOf("author" to "tester"))
        val parsed = parseMCDStructure(source)
        assertTrue(parsed.isV2)
        assertEquals("tester", parsed.metaInfo.single { it.key == "author" }.value)
        assertEquals("迁移测试", parsed.chains.single().name)
        assertEquals(
            blocks,
            parsed.chains.single().items.filterIsInstance<ChainItem.Block>().map { it.block },
        )
    }

    @Test
    fun v1UpgradeRetainsCommentsAndCommandOrder() {
        val original =
            parseMCDStructure("@author=tester\n# root\n---chain---\n# note\nsay one\nsay two")
        val parsed = parseMCDStructure(serializeParsedToMCDv2(original))
        assertTrue(parsed.isV2)
        assertEquals(original.rootComments, parsed.rootComments)
        assertEquals(
            listOf("say one", "say two"),
            parsed.chains.flatMap { chain ->
                chain.items.filterIsInstance<ChainItem.Block>().map { it.block.command }
            },
        )
        assertTrue(
            parsed.chains.any { chain ->
                chain.items.any { it is ChainItem.Comment && it.text == "note" }
            }
        )
    }
}
