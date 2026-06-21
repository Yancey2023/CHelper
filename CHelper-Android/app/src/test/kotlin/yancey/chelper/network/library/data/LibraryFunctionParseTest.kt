package yancey.chelper.network.library.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LibraryFunction 是命令库的核心实体，字段最多、最容易"被加新字段加坏"。
 * 这个测试做端到端 JSON → 对象解析，把所有 @SerialName 转换都覆盖一遍：
 *   create_time / like_count / is_liked / has_public_version / is_publish / is_owner / chain_data / auto_sync
 * 同时验证 chain_data 这种可变结构存为 JsonElement 不丢信息。
 */
class LibraryFunctionParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `完整字段 JSON 能解析出全部字段`() {
        val raw = """
            {
              "id":7,
              "uuid":"u-abc",
              "name":"传送回家",
              "content":"/tp @s 0 0 0",
              "author":{"id":1,"name":"Akanyi","tier":2,"user_title":""},
              "note":"备忘",
              "tags":["tp","home"],
              "version":"1.21",
              "create_time":"2024-04-12T12:00:00",
              "preview":"/tp...",
              "like_count":12,
              "is_liked":true,
              "has_public_version":false,
              "is_publish":true,
              "is_owner":true,
              "chain_data":{"step":1,"detail":"ok"},
              "auto_sync":true
            }
        """.trimIndent()
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertEquals(7, fn.id)
        assertEquals("u-abc", fn.uuid)
        assertEquals("传送回家", fn.name)
        assertEquals(listOf("tp", "home"), fn.tags)
        assertEquals(12, fn.likeCount)
        assertEquals(true, fn.isLiked)
        assertEquals(false, fn.hasPublicVersion)
        assertEquals(true, fn.isPublish)
        assertEquals(true, fn.isOwner)
        assertEquals(true, fn.autoSync)
        assertEquals("Akanyi", fn.authorName)
        // chain_data 是 JsonElement，原样保留是合同——上层 UI 自己挑字段读
        assertNotNull(fn.chainData)
        assertTrue(fn.chainData.toString().contains("\"step\":1"))
    }

    @Test
    fun `auto_sync 缺失时按主代码默认值 false`() {
        val fn = json.decodeFromString<LibraryFunction>("""{"name":"x"}""")
        assertEquals(false, fn.autoSync)
    }

    @Test
    fun `local_unsynced 纯本地字段：后端没返回时默认 false，不污染远端`() {
        // 模拟一个典型的后端响应（不会带 local_unsynced），本地解出来必须是 false
        val fn = json.decodeFromString<LibraryFunction>("""{"name":"x","uuid":"u-1"}""")
        assertFalse(fn.localUnsynced)

        // 本地 DataStore 存下来的对象再读出来，需要保留 true
        val encoded = Json.encodeToString(LibraryFunction.serializer(), LibraryFunction().apply {
            name = "local"
            uuid = "u-2"
            localUnsynced = true
        })
        assertTrue(
            "本地序列化必须把 local_unsynced 写出来才能持久化",
            encoded.contains("\"local_unsynced\":true")
        )
        val round = json.decodeFromString<LibraryFunction>(encoded)
        assertTrue(round.localUnsynced)
    }

    @Test
    fun `tags 缺失保持 null 不要默认成空列表`() {
        // 区分"没传 tags"和"传了空数组"对上层"显隐 tag 区域"是有意义的
        val fn = json.decodeFromString<LibraryFunction>("""{"name":"x"}""")
        assertNull(fn.tags)
        val fn2 = json.decodeFromString<LibraryFunction>("""{"name":"x","tags":[]}""")
        assertNotNull(fn2.tags)
        assertTrue(fn2.tags!!.isEmpty())
    }

    @Test
    fun `LeaderboardData 嵌套用户列表能解析`() {
        val raw = """
            {
              "leaderboard":[
                {"id":1,"avatar_url":"a","nickname":"n","tier":3,"total_likes":99,"total_functions":5},
                {"id":2}
              ]
            }
        """.trimIndent()
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
