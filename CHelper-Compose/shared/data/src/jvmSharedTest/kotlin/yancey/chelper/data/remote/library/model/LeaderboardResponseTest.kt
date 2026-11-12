package yancey.chelper.data.remote.library.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LeaderboardResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `LeaderboardData 嵌套用户列表能解析`() {
        val raw =
            """
            {
              "leaderboard":[
                {"id":1,"avatar_url":"a","nickname":"n","tier":3,"total_likes":99,"total_functions":5},
                {"id":2}
              ]
            }
            """
                .trimIndent()
        val resp = json.decodeFromString<LeaderboardData>(raw)
        assertNotNull(resp.leaderboard)
        assertEquals(2, resp.leaderboard!!.size)
        val first = resp.leaderboard!![0]
        assertEquals(99, first.totalLikes)
        assertEquals(5, first.totalFunctions)
        // 第二条只有 id，其它字段必须 null 不要崩
        val second = resp.leaderboard!![1]
        assertEquals(2, second.id)
        assertFalse("第二条应该没 nickname", second.nickname != null)
    }
}
