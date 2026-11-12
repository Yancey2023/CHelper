package yancey.chelper.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandLabDeepLinkParserTest {
    @Test
    fun `自定义协议和网页分享链接解析到同一公开库`() {
        val expected = CommandLabDeepLink.Library(42)

        assertEquals(expected, CommandLabDeepLinkParser.parse("chelper://commandlab/library/42"))
        assertEquals(
            expected,
            CommandLabDeepLinkParser.parse("https://abyssous.site/app/library/42"),
        )
        assertEquals(
            expected,
            CommandLabDeepLinkParser.parse("https://www.abyssous.site/app?library_id=42"),
        )
    }

    @Test
    fun `私有库标记在网页和自定义协议间保持一致`() {
        val expected = CommandLabDeepLink.Library(42, isPrivate = true)

        assertEquals(
            expected,
            CommandLabDeepLinkParser.parse("chelper://commandlab/library/42?private=1"),
        )
        assertEquals(
            expected,
            CommandLabDeepLinkParser.parse("https://abyssous.site/app/library/42?is_private=true"),
        )
    }

    @Test
    fun `本地导入外链在网页和自定义协议间保持一致`() {
        val expected = CommandLabDeepLink.Library(42, importToLocal = true)

        assertEquals(
            expected,
            CommandLabDeepLinkParser.parse("chelper://commandlab/library/42?local=1"),
        )
        assertEquals(
            expected,
            CommandLabDeepLinkParser.parse("https://abyssous.site/app/library/42?local=true"),
        )
        assertEquals(
            CommandLabDeepLink.Library(42, isPrivate = true, importToLocal = true),
            CommandLabDeepLinkParser.parse("https://abyssous.site/app/library/42?private=1&local=1"),
        )
    }

    @Test
    fun `命令库首页协议可解析且非法目标被拒绝`() {
        assertEquals(
            CommandLabDeepLink.LibraryHome,
            CommandLabDeepLinkParser.parse("chelper://commandlab/library"),
        )
        assertEquals(
            CommandLabDeepLink.LibraryHome,
            CommandLabDeepLinkParser.parse("https://abyssous.site/app"),
        )
        assertNull(CommandLabDeepLinkParser.parse("https://example.com/app/library/42"))
        assertNull(CommandLabDeepLinkParser.parse("chelper://commandlab/library/0"))
        assertNull(CommandLabDeepLinkParser.parse("not a url"))
    }
}
