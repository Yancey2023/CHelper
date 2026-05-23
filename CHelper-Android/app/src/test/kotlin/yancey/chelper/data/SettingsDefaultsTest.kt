package yancey.chelper.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings 是 DataStore 的根数据类，所有字段都是 nullable + 默认值 null。
 * 这是 DataStore 兼容老配置文件的关键策略——任何字段加了非 null 默认值，
 * 都会让旧 JSON 无法反序列化（kotlinx.serialization 在 missing key 时会查默认值）。
 *
 * 这组测试钉死两件事：
 *   1) 默认实例所有字段都是 null（"全部交给上层 DataStore wrapper 决定 fallback"）
 *   2) 空 JSON 也能解出全 null 实例（"老版本 JSON 字段缺失时不崩"）
 */
class SettingsDefaultsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Settings 默认实例字段全部为 null`() {
        val s = Settings()
        assertTrue(
            "isEnableUpdateNotifications=${s.isEnableUpdateNotifications}, " +
                    "themeId=${s.themeId}, " +
                    "floatingWindowAlpha=${s.floatingWindowAlpha}, " +
                    "floatingWindowSize=${s.floatingWindowSize}",
            listOf(
                s.isEnableUpdateNotifications,
                s.themeId,
                s.floatingWindowAlpha,
                s.floatingWindowScreenAlpha,
                s.floatingWindowSize,
                s.isCheckingBySelection,
                s.isHideWindowWhenCopying,
                s.isSavingWhenPausing,
                s.isCrowded,
                s.isShowErrorReason,
                s.isSyntaxHighlight,
                s.cpackBranch,
                s.isShowPublicLibrary,
                s.publicLibraryMinVersion,
                s.tagClickBehavior,
                s.ambiguousLineDefault,
                s.isHideMetadataPreview,
                s.isFloatingWindowFontAlphaSync,
                s.syntaxHighlightMaxLength,
                s.publicLibraryHomeRecommend,
            ).all { it == null }
        )
    }

    @Test
    fun `空 JSON 应当解析出和默认实例等价的 Settings`() {
        val s = json.decodeFromString<Settings>("{}")
        assertEquals(Settings(), s)
    }

    @Test
    fun `部分字段的 JSON 解析应当只填这些字段`() {
        val raw = """{"themeId":"X","syntaxHighlightMaxLength":1234}"""
        val s = json.decodeFromString<Settings>(raw)
        assertEquals("X", s.themeId)
        assertEquals(1234, s.syntaxHighlightMaxLength)
        assertEquals(null, s.cpackBranch)
        assertEquals(null, s.isEnableUpdateNotifications)
    }

    @Test
    fun `Settings 对象 copy 语义正常`() {
        // 这是 DataStore.updateData 的依赖前提，跑一下确保 data class copy 能用
        val s = Settings(themeId = "A").copy(themeId = "B")
        assertEquals("B", s.themeId)
    }
}
