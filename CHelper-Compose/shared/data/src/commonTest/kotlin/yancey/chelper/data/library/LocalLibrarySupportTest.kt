package yancey.chelper.data.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import yancey.chelper.data.library.document.localBody
import yancey.chelper.data.library.document.toFullLocalMcd
import yancey.chelper.data.library.document.usesLocalMcdV2
import yancey.chelper.domain.library.AuthorInfo
import yancey.chelper.domain.library.BlockType
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.LocalLibrarySort
import yancey.chelper.domain.library.parseMCDStructure

class LocalLibrarySupportTest {
    private fun library(
        id: String,
        name: String,
        note: String = "",
        tags: List<String> = emptyList(),
        content: String = "say hello",
    ) =
        LibraryFunction(
            localEntryId = id,
            name = name,
            note = note,
            tags = tags,
            content = content,
            author = AuthorInfo(name = "Tester"),
        )

    @Test
    fun `全文搜索保留原始条目身份并支持多关键词`() {
        val libraries =
            listOf(
                library("a", "传送大厅", tags = listOf("utility"), content = "tp @s 0 80 0"),
                library("b", "红石工具", note = "自动清理", content = "kill @e[type=item]"),
            )

        val result = filterAndSortLocalLibraries(libraries, "自动 item", LocalLibrarySort.DEFAULT)

        assertEquals(1, result.size)
        assertEquals("b", result.single().localEntryId)
        assertEquals(1, result.single().storageIndex)
    }

    @Test
    fun `名称排序不会修改条目身份`() {
        val result =
            filterAndSortLocalLibraries(
                listOf(library("z", "Zulu"), library("a", "alpha")),
                "",
                LocalLibrarySort.NAME_ASC,
            )

        assertEquals(listOf("a", "z"), result.map { it.localEntryId })
    }

    @Test
    fun `导入同时兼容单对象和数组`() {
        val single = library("a", "单库")
        val array = listOf(single, library("b", "第二个"))

        assertEquals(
            listOf("单库"),
            decodeLocalLibraryImport(Json.encodeToString(single)).map { it.name },
        )
        assertEquals(
            listOf("单库", "第二个"),
            decodeLocalLibraryImport(Json.encodeToString(array)).map { it.name },
        )
    }

    @Test
    fun `导入备份清理同步任务和云端身份但保留文档内容`() {
        val backup =
            library("original-local", "待同步备份", content = "> C\nsay restored")
                .copy(
                    id = 123,
                    uuid = "original-cloud",
                    autoSync = true,
                    hasUnsyncedChanges = true,
                    localUnsynced = true,
                    localSyncOwnerId = 7,
                    localSyncRevision = "pending-write",
                    localSyncError = "offline",
                    localIsV2 = true,
                )
        for (json in listOf(Json.encodeToString(backup), Json.encodeToString(listOf(backup)))) {
            val imported = decodeLocalLibraryImport(json).single()
            assertNull(imported.id)
            assertNull(imported.uuid)
            assertNull(imported.localEntryId)
            assertFalse(imported.autoSync == true)
            assertFalse(imported.localUnsynced)
            assertNull(imported.hasUnsyncedChanges)
            assertNull(imported.localSyncOwnerId)
            assertNull(imported.localSyncRevision)
            assertNull(imported.localSyncError)
            assertEquals(backup.content, imported.content)
            assertEquals(backup.name, imported.name)
            assertEquals(backup.tags, imported.tags)
            assertTrue(imported.localIsV2 == true)
        }
    }

    @Test
    fun `复制副本清理云端绑定和本地身份`() {
        val duplicate =
            library("local", "原库")
                .copy(id = 7, uuid = "cloud", autoSync = true, localUnsynced = true)
                .toLocalDuplicate()

        assertEquals("原库 - 副本", duplicate.name)
        assertNull(duplicate.id)
        assertNull(duplicate.uuid)
        assertNull(duplicate.localEntryId)
        assertFalse(duplicate.autoSync == true)
        assertFalse(duplicate.localUnsynced)
    }

    @Test
    fun `复制副本会固化从完整源码推断出的 V2 状态`() {
        val duplicate =
            library(
                    "local",
                    "V2 原库",
                    content =
                        """
                        @name=V2 原库
                        @mcd_version=2

                        ###Function###
                        say hello
                        ###End###
                        """
                            .trimIndent(),
                )
                .toLocalDuplicate()

        assertTrue(duplicate.localIsV2 == true)
    }

    @Test
    fun `外链导入会清理云端身份并规范化为本地正文`() {
        val imported =
            library(
                    "remote",
                    "外链库",
                    content =
                        """
                        @name=外链库
                        @version=1.0
                        @mcd_version=2
                        @uuid=cloud-uuid

                        ###Function###
                        > C
                        say imported
                        ###End###
                        """
                            .trimIndent(),
                )
                .copy(id = 42, uuid = "cloud-uuid")
                .toLocalImportedCopy()

        assertNull(imported.id)
        assertNull(imported.uuid)
        assertNull(imported.localEntryId)
        assertEquals("> C\nsay imported", imported.content)
        assertTrue(imported.localIsV2 == true)
    }

    @Test
    fun `外链导入去重兼容完整源码和仅正文存储`() {
        val remote =
            library(
                "remote",
                "同一个库",
                content =
                    """
                    @name=同一个库
                    @version=1.0
                    ###Function###
                    say hello
                    ###End###
                    """
                        .trimIndent(),
            )
        val local = library("local", "同一个库", content = "say hello")

        assertTrue(remote.hasSameLocalContent(local))
    }

    @Test
    fun `完整 MCD 不会重复套壳且正文可提取`() {
        val source =
            """
            @name=示例
            @version=1.0
            @mcd_version=2

            ###Function###
            > I
            say hello
            ###End###
            """
                .trimIndent()
        val library = library("a", "示例", content = source)

        assertEquals(source, library.toFullLocalMcd())
        assertEquals("> I\nsay hello", library.localBody())
        assertTrue(library.usesLocalMcdV2())
        assertEquals(1, Regex("###Function###").findAll(library.toFullLocalMcd()).count())
    }

    @Test
    fun `仅正文 V2 会生成规范完整源码`() {
        val source =
            library("a", "示例", content = "> R!t20\nsay loop")
                .copy(localIsV2 = true)
                .toFullLocalMcd()

        assertTrue(source.contains("@mcd_version=2"))
        assertTrue(source.contains("> R!t20\nsay loop"))
    }

    @Test
    fun `持久化 V2 标记可修复缺版本头的完整 MCD`() {
        val source =
            library(
                    "a",
                    "示例",
                    content =
                        """
                        @name=示例
                        @version=1.0

                        ###Function###
                        > I
                        say hello
                        ###End###
                        """
                            .trimIndent(),
                )
                .copy(localIsV2 = true)
                .toFullLocalMcd()
        val parsed = parseMCDStructure(source)

        assertEquals(1, Regex("@mcd_version=2").findAll(source).count())
        assertTrue(parsed.isV2)
        assertTrue(parsed.chains.single().items.single() is ChainItem.Block)
    }

    @Test
    fun `旧完整 MCD 可从状态行推断 V2 并补版本头`() {
        val source =
            library(
                    "a",
                    "旧示例",
                    content =
                        """
                        @name=旧示例

                        ###Function###
                        > R!t20
                        say loop
                        ###End###
                        """
                            .trimIndent(),
                )
                .toFullLocalMcd()

        assertTrue(source.contains("@mcd_version=2"))
        assertTrue(parseMCDStructure(source).isV2)
    }

    @Test
    fun `完整 MCD 会写入本地绑定 UUID`() {
        val source =
            library("a", "示例", content = "@name=示例\n###Function###\nsay hello\n###End###")
                .copy(uuid = "stable-uuid")
                .toFullLocalMcd()

        assertTrue(source.substringBefore("###Function###").contains("@uuid=stable-uuid"))
    }

    @Test
    fun `带空格的 V2 版本头仍按命令方块结构渲染`() {
        val parsed =
            parseMCDStructure(
                """
                @name=示例
                @mcd_version = 2

                ###Function###
                > C!
                say hello
                ###End###
                """
                    .trimIndent()
            )

        assertTrue(parsed.isV2)
        assertTrue(parsed.chains.single().items.single() is ChainItem.Block)
    }

    @Test
    fun `多行 note 不会阻断后续 V2 版本头`() {
        val parsed =
            parseMCDStructure(
                """
                @name=基础防熊
                @note=说明第一行
                说明第二行
                @mcd_version=2
                @uuid=stable-uuid

                ###Function###
                ---前置条件---
                > H
                scoreboard objectives add level dummy
                ###End###
                """
                    .trimIndent()
            )

        val block = parsed.chains.single().items.single() as ChainItem.Block
        assertTrue(parsed.isV2)
        assertEquals("说明第一行\n说明第二行", parsed.metaInfo.single { it.key == "note" }.value)
        assertEquals(BlockType.CHAT, block.block.type)
        assertEquals("scoreboard objectives add level dummy", block.block.command)
    }

    @Test
    fun `关闭 V2 会从完整 MCD 移除版本头`() {
        val source =
            library(
                    "a",
                    "示例",
                    content =
                        """
                        @name=示例
                        @mcd_version=2

                        ###Function###
                        say hello
                        ###End###
                        """
                            .trimIndent(),
                )
                .copy(localIsV2 = false)
                .toFullLocalMcd()

        assertFalse(source.contains("@mcd_version"))
        assertFalse(parseMCDStructure(source).isV2)
    }

    @Test
    fun `聊天文本中的元数据样式内容不会被删除`() {
        val library = library("a", "聊天", content = "> H\n@name=Steve").copy(localIsV2 = true)

        assertEquals("> H\n@name=Steve", library.localBody())
        assertTrue(library.toFullLocalMcd().contains("> H\n@name=Steve"))
    }

    @Test
    fun `关闭 V2 只移除头部版本行`() {
        val source =
            library(
                    "a",
                    "聊天",
                    content = "@mcd_version=2\n###Function###\n> H\n@mcd_version=2\n###End###",
                )
                .copy(localIsV2 = false)
                .toFullLocalMcd()

        assertFalse(source.substringBefore("###Function###").contains("@mcd_version"))
        assertTrue(source.substringAfter("###Function###").contains("@mcd_version=2"))
    }

    @Test
    fun `旧备份导入会把 V2 推断结果写入本地字段`() {
        val imported =
            decodeLocalLibraryImport(
                    """
                    {
                      "name":"旧 V2 备份",
                      "content":"@mcd_version=2\n###Function###\nsay hello\n###End###"
                    }
                    """
                        .trimIndent()
                )
                .single()

        assertTrue(imported.localIsV2 == true)
    }
}
