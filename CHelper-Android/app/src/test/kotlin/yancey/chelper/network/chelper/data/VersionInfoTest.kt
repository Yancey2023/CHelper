package yancey.chelper.network.chelper.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * VersionInfo 走的是 @SerialName 下划线映射。这玩意挂掉的话"检查更新"
 * 会拿不到版本号或下载链接，发版日影响面很大，所以单独测一组确认。
 */
class VersionInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `下划线字段应当被映射到驼峰属性`() {
        val raw = """
            {
              "version_code":81,
              "version_name":"0.4.5",
              "link":"https://example.com/x.apk",
              "changelog":"修复了若干问题"
            }
        """.trimIndent()
        val v = json.decodeFromString<VersionInfo>(raw)
        assertEquals(81, v.versionCode)
        assertEquals("0.4.5", v.versionName)
        assertEquals("https://example.com/x.apk", v.link)
        assertEquals("修复了若干问题", v.changelog)
    }

    @Test
    fun `字段缺失应当全部为 null`() {
        val v = json.decodeFromString<VersionInfo>("{}")
        assertNull(v.versionCode)
        assertNull(v.versionName)
        assertNull(v.link)
        assertNull(v.changelog)
    }
}
