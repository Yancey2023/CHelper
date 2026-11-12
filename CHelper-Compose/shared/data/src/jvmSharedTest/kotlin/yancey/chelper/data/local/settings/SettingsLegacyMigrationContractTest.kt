package yancey.chelper.data.local.settings

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import yancey.chelper.data.local.ClientDataStores
import yancey.chelper.domain.settings.Settings

/** Verify the actual shared migration and persisted result, including Android's existing paths. */
class SettingsLegacyMigrationContractTest {
    private fun parse(raw: String): Settings {
        val directory = Files.createTempDirectory("CHelper-settings-migration-").toFile()
        try {
            File(directory, "settings").mkdirs()
            File(directory, "settings/settings.json").writeText(raw)
            return runBlocking {
                ClientDataStores(File(directory, "files/datastore"), legacyDirectory = directory)
                    .use { stores -> stores.settings.settings().first() }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `典型旧配置应当被完整迁移`() {
        val raw =
            """
            {
              "isEnableUpdateNotifications":true,
              "themeId":"MODE_NIGHT_FOLLOW_SYSTEM",
              "floatingWindowAlpha":0.8,
              "floatingWindowSize":40,
              "isCheckingBySelection":false,
              "isCrowed":true,
              "cpackPath":"release-vanilla",
              "syntaxHighlightMaxLength":4000
            }
            """
                .trimIndent()
        val s = parse(raw)
        assertEquals(true, s.isEnableUpdateNotifications)
        assertEquals("MODE_NIGHT_FOLLOW_SYSTEM", s.themeId)
        assertEquals(0.8f, s.floatingWindowAlpha!!, 0.0001f)
        assertEquals(40, s.floatingWindowSize)
        assertEquals(false, s.isCheckingBySelection)
        assertEquals(true, s.isCrowded) // 注意源字段是 isCrowed
        assertEquals("release-vanilla", s.cpackBranch)
        assertEquals(4000, s.syntaxHighlightMaxLength)
    }

    @Test
    fun `cpackPath 不在白名单时应被置 null`() {
        val raw = """{"cpackPath":"this-is-something-weird"}"""
        assertNull(parse(raw).cpackBranch)
    }

    @Test
    fun `cpackPath 缺失时也应为 null`() {
        assertNull(parse("{}").cpackBranch)
    }

    @Test
    fun `白名单 6 个值都应被保留`() {
        val branches =
            listOf(
                "release-vanilla",
                "release-experiment",
                "beta-vanilla",
                "beta-experiment",
                "netease-vanilla",
                "netease-experiment",
            )
        for (br in branches) {
            assertEquals("白名单分支 $br 应当保留", br, parse("""{"cpackPath":"$br"}""").cpackBranch)
        }
    }

    @Test
    fun `历史拼写 isCrowed 必须映射到 isCrowded`() {
        // 这是契约级测试。如果未来"修正"成 isCrowded，老用户升级后紧凑模式偏好就丢了
        val s = parse("""{"isCrowed":true}""")
        assertEquals(true, s.isCrowded)

        val s2 = parse("""{"isCrowded":true}""") // 写"对"的不该被识别
        assertNull("不应识别正确拼写以避免双源真相", s2.isCrowded)
    }

    @Test
    fun `字段类型错配应当解析为 null 而非崩溃`() {
        // 旧配置文件可能被误改坏，单字段错也得能继续走完迁移
        val raw = """{"floatingWindowSize":"not a number","themeId":"OK"}"""
        val s = parse(raw)
        assertNull(s.floatingWindowSize)
        assertEquals("OK", s.themeId)
    }
}
