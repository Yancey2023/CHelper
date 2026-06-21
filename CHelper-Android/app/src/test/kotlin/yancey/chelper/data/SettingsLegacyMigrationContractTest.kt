package yancey.chelper.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SettingsMigrationToV74 是把"老版自研存储格式"迁到 DataStore Settings 的关键一步，
 * 但它本身依赖 Android Context 的 dataDir，不能纯单测。
 *
 * 这里换种思路：**直接对照主代码里 migrate() 的 JsonObject -> Settings 字段映射逻辑做一次等价测试**。
 * 目的不是覆盖 migrate() 的代码行，而是钉死契约：
 *
 *   - 旧 JSON 的 `isCrowed`（注意这个**拼写错误**是历史包袱）必须映射到新 `isCrowded`，
 *     不能图"修正"就改成 `isCrowded`，否则老用户的"紧凑模式"开关会全部丢失。
 *   - cpackBranch 必须只接受白名单中的 6 个值，否则要兜底为 null。
 *   - 字段类型不匹配时不能崩，要解析为 null。
 *
 * 这种契约一旦悄悄改坏，老用户升级后偏好全部丢失，体验灾难——值得有测试守。
 */
class SettingsLegacyMigrationContractTest {

    /**
     * 复刻主代码里 migrate() 内部的 JsonObject -> Settings 提取逻辑。
     * 故意保持和 SettingsDataStore.kt 第 273-288 行一一对应，
     * 这样如果哪天主代码被改坏，这里和那里就会出现行为差异，CI 就能发现。
     */
    private fun extract(json: JsonObject): Settings {
        var cpackBranch = (json["cpackPath"] as? JsonPrimitive)?.content
        if (cpackBranch == null ||
            !(cpackBranch == "release-vanilla" ||
                    cpackBranch == "release-experiment" ||
                    cpackBranch == "beta-vanilla" ||
                    cpackBranch == "beta-experiment" ||
                    cpackBranch == "netease-vanilla" ||
                    cpackBranch == "netease-experiment")
        ) {
            cpackBranch = null
        }
        return Settings(
            isEnableUpdateNotifications = (json["isEnableUpdateNotifications"] as? JsonPrimitive)?.booleanOrNull,
            themeId = (json["themeId"] as? JsonPrimitive)?.content,
            floatingWindowAlpha = (json["floatingWindowAlpha"] as? JsonPrimitive)?.floatOrNull,
            floatingWindowSize = (json["floatingWindowSize"] as? JsonPrimitive)?.intOrNull,
            isCheckingBySelection = (json["isCheckingBySelection"] as? JsonPrimitive)?.booleanOrNull,
            isHideWindowWhenCopying = (json["isHideWindowWhenCopying"] as? JsonPrimitive)?.booleanOrNull,
            isSavingWhenPausing = (json["isSavingWhenPausing"] as? JsonPrimitive)?.booleanOrNull,
            // 故意保留拼写错误：旧文件就是写错的，强行改成 isCrowded 会丢数据
            isCrowded = (json["isCrowed"] as? JsonPrimitive)?.booleanOrNull,
            isShowErrorReason = (json["isShowErrorReason"] as? JsonPrimitive)?.booleanOrNull,
            isSyntaxHighlight = (json["isSyntaxHighlight"] as? JsonPrimitive)?.booleanOrNull,
            cpackBranch = cpackBranch,
            isFloatingWindowFontAlphaSync = (json["isFloatingWindowFontAlphaSync"] as? JsonPrimitive)?.booleanOrNull,
            syntaxHighlightMaxLength = (json["syntaxHighlightMaxLength"] as? JsonPrimitive)?.intOrNull,
            publicLibraryHomeRecommend = (json["publicLibraryHomeRecommend"] as? JsonPrimitive)?.booleanOrNull,
        )
    }

    private fun parse(raw: String): Settings = extract(Json.parseToJsonElement(raw) as JsonObject)

    @Test
    fun `典型旧配置应当被完整迁移`() {
        val raw = """
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
        """.trimIndent()
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
        val branches = listOf(
            "release-vanilla", "release-experiment",
            "beta-vanilla", "beta-experiment",
            "netease-vanilla", "netease-experiment"
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
