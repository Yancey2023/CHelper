package yancey.chelper.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandLabWebSsoTest {
    @Test
    fun `四种邀请链接会转换为原始站内 next`() {
        val links =
            listOf(
                "https://abyssous.site/invite?code=general" to "/invite?code=general",
                "https://www.abyssous.site/invite/title?code=title%2Bcode" to
                    "/invite/title?code=title%2Bcode",
                "https://abyssous.site/invite/quota?code=quota&utm_source=app" to
                    "/invite/quota?code=quota&utm_source=app",
                "http://abyssous.site/invite/points?code=points" to "/invite/points?code=points",
            )

        links.forEach { (url, expected) ->
            assertEquals(expected, CommandLabWebSso.inviteNext(url))
        }
    }

    @Test
    fun `外部域名和不支持的站内路径不会触发 SSO`() {
        assertNull(CommandLabWebSso.inviteNext("https://example.com/invite?code=test"))
        assertNull(CommandLabWebSso.inviteNext("https://abyssous.site/wiki?code=test"))
        assertNull(CommandLabWebSso.inviteNext("https://abyssous.site/invite/unknown?code=test"))
    }

    @Test
    fun `缺少有效 code 的邀请路径不会触发 SSO`() {
        assertNull(CommandLabWebSso.inviteNext("https://abyssous.site/invite"))
        assertNull(CommandLabWebSso.inviteNext("https://abyssous.site/invite?code="))
        assertNull(CommandLabWebSso.inviteNext("https://abyssous.site/invite?other=value"))
    }
}
