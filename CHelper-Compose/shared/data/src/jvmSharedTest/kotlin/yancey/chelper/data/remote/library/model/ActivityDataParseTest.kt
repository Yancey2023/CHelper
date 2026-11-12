package yancey.chelper.data.remote.library.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import yancey.chelper.data.remote.library.service.CommandLabPublicService
import yancey.chelper.data.remote.library.service.CommandLabUserService

class ActivityDataParseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `点赞积分回执兼容 snake case`() {
        val response =
            json.decodeFromString<CommandLabPublicService.LibraryLikeResponse>(
                """{"like_count":8,"is_liked":true,"daily_first_like_points":1.0,"points_awarded":1.0}"""
            )

        assertEquals(8, response.likeCount)
        assertTrue(response.isLiked == true)
        assertEquals(1.0, response.dailyFirstLikePoints ?: 0.0, 0.0)
        assertEquals(1.0, response.pointsAwarded ?: 0.0, 0.0)
    }

    @Test
    fun `活动摘要和 Tier 时间线能完整解析`() {
        val raw =
            """
            {
              "points":21.5,
              "total_earned":30.0,
              "total_spent":8.5,
              "total_reversed":0.0,
              "earned_by_type":{"daily_first_like":1.0},
              "tier":{
                "base_tier":0,
                "effective_tier":1,
                "effective_tier_name":"基础认证",
                "tier_summaries":[{"tier":1,"tier_name":"基础认证","status":"active"}],
                "effective_timeline":[{"tier":1,"tier_name":"基础认证","starts_at":"2026-07-26T12:00:00"}],
                "grants":[]
              }
            }
            """
                .trimIndent()

        val summary = json.decodeFromString<ActivitySummary>(raw)
        assertEquals(21.5, summary.points ?: 0.0, 0.0)
        assertEquals(1, summary.tier?.effectiveTier)
        assertEquals("active", summary.tier?.tierSummaries?.first()?.status)
    }

    @Test
    fun `收藏响应读取服务端状态`() {
        val response =
            json.decodeFromString<CommandLabUserService.FavoriteLibraryResponse>(
                """{"is_favorited":true,"uuid":"library-uuid"}"""
            )

        assertTrue(response.isFavorited == true)
        assertEquals("library-uuid", response.uuid)
    }

    @Test
    fun `分享响应读取 snake case 链接字段`() {
        val response =
            json.decodeFromString<CommandLabUserService.LibraryShareResponse>(
                """{"code":"Ab12Cd34Ef","share_url":"https://abyssous.site/s/Ab12Cd34Ef","share_path":"/s/Ab12Cd34Ef","library_id":7,"library_name":"传送大厅","sharer_nickname":"Akanyi","import_to_local":false,"expires_at":1787000000,"download_url":"https://autopatch.lansn.icu/download"}"""
            )

        assertEquals("Ab12Cd34Ef", response.code)
        assertEquals("https://abyssous.site/s/Ab12Cd34Ef", response.shareUrl)
        assertEquals("/s/Ab12Cd34Ef", response.sharePath)
        assertEquals(7, response.libraryId)
        assertEquals(false, response.importToLocal)
        assertEquals(1787000000L, response.expiresAt)
    }

    @Test
    fun `网页 SSO 响应读取后端签发地址`() {
        val response =
            json.decodeFromString<CommandLabUserService.WebSsoAuthorizeResponse>(
                """{"web_url":"https://abyssous.site/auth/chelper/web-sso#code=one-time"}"""
            )

        assertEquals("https://abyssous.site/auth/chelper/web-sso#code=one-time", response.webUrl)
    }

    @Test
    fun `网页 SSO 请求只发送站内 next`() {
        val request =
            CommandLabUserService.WebSsoAuthorizeRequest(next = "/invite/points?code=invite-code")

        assertEquals("""{"next":"/invite/points?code=invite-code"}""", json.encodeToString(request))
    }
}
