package yancey.chelper.domain.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * LibraryFunction 是命令库的核心实体，字段最多、最容易"被加新字段加坏"。 这个测试做端到端 JSON → 对象解析，把所有 @SerialName 转换都覆盖一遍：
 * create_time / like_count / is_liked / is_favorited / has_public_version / is_publish / is_owner /
 * chain_data / auto_sync 同时验证 chain_data 这种可变结构存为 JsonElement 不丢信息。
 */
class LibraryFunctionParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `完整字段 JSON 能解析出全部字段`() {
        val raw =
            """
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
              "is_favorited":true,
              "has_public_version":false,
              "is_publish":true,
              "is_owner":true,
              "chain_data":{"step":1,"detail":"ok"},
              "auto_sync":true
            }
            """
                .trimIndent()
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertEquals(7, fn.id)
        assertEquals("u-abc", fn.uuid)
        assertEquals("传送回家", fn.name)
        assertEquals(listOf("tp", "home"), fn.tags)
        assertEquals(12, fn.likeCount)
        assertEquals(true, fn.isLiked)
        assertEquals(true, fn.isFavorited)
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
        val encoded =
            Json.encodeToString(
                LibraryFunction.serializer(),
                LibraryFunction(name = "local", uuid = "u-2", localUnsynced = true),
            )
        assertTrue(encoded.contains("\"local_unsynced\":true"), "本地序列化必须把 local_unsynced 写出来才能持久化")
        val round = json.decodeFromString<LibraryFunction>(encoded)
        assertTrue(round.localUnsynced)
    }

    @Test
    fun `local_is_v2 能随本地 JSON 往返持久化`() {
        val encoded =
            Json.encodeToString(
                LibraryFunction.serializer(),
                LibraryFunction(name = "v2", content = "> I\nsay hello", localIsV2 = true),
            )
        val round = json.decodeFromString<LibraryFunction>(encoded)

        assertTrue(encoded.contains("\"local_is_v2\":true"))
        assertEquals(true, round.localIsV2)
    }

    @Test
    fun `tags 缺失保持 null 不要默认成空列表`() {
        // 区分"没传 tags"和"传了空数组"对上层"显隐 tag 区域"是有意义的
        val fn = json.decodeFromString<LibraryFunction>("""{"name":"x"}""")
        assertNull(fn.tags)
        val fn2 = json.decodeFromString<LibraryFunction>("""{"name":"x","tags":[]}""")
        assertNotNull(fn2.tags)
        assertTrue(fn2.tags.isEmpty())
    }
}
