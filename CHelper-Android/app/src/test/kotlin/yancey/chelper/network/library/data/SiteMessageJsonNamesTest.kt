package yancey.chelper.network.library.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SiteMessage 用 @JsonNames 同时支持 snake_case 和 camelCase——
 * 因为后端不同版本/不同接口的命名风格不统一。一旦哪天 @JsonNames 被改坏，
 * 用户站内信就会大面积空白。这里两种命名各跑一遍 + 默认值场景兜底。
 *
 * MessageListResponse 的 page_size / pageSize 双名兼容也一并测了。
 */
class SiteMessageJsonNamesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `snake_case 字段应当被识别`() {
        val raw = """
            {
              "id":1,
              "title":"欢迎",
              "content":"hi",
              "msg_type":"system",
              "sender_id":0,
              "created_at":"2024-04-12T12:00:00",
              "is_read":false,
              "read_at":null,
              "is_global":true
            }
        """.trimIndent()
        val msg = json.decodeFromString<SiteMessage>(raw)
        assertEquals(1, msg.id)
        assertEquals("system", msg.msgType)
        assertEquals(0, msg.senderId)
        assertEquals("2024-04-12T12:00:00", msg.createdAt)
        assertEquals(false, msg.isRead)
        assertNull(msg.readAt)
        assertEquals(true, msg.isGlobal)
    }

    @Test
    fun `camelCase 字段也应当被识别`() {
        val raw = """
            {
              "id":2,
              "msgType":"notice",
              "senderId":99,
              "createdAt":"2024-05-01T00:00:00",
              "isRead":true,
              "readAt":"2024-05-02T00:00:00",
              "isGlobal":false
            }
        """.trimIndent()
        val msg = json.decodeFromString<SiteMessage>(raw)
        assertEquals("notice", msg.msgType)
        assertEquals(99, msg.senderId)
        assertEquals(true, msg.isRead)
        assertEquals("2024-05-02T00:00:00", msg.readAt)
    }

    @Test
    fun `字段缺失时所有字段保持 null`() {
        val msg = json.decodeFromString<SiteMessage>("{}")
        assertNull(msg.id)
        assertNull(msg.msgType)
        assertNull(msg.senderId)
        assertNull(msg.isRead)
    }

    @Test
    fun `MessageListResponse 双命名 page_size 都能解`() {
        val snake = """{"messages":[],"total":0,"page":1,"page_size":20}"""
        val camel = """{"messages":[],"total":0,"page":1,"pageSize":20}"""
        assertEquals(20, json.decodeFromString<MessageListResponse>(snake).pageSize)
        assertEquals(20, json.decodeFromString<MessageListResponse>(camel).pageSize)
    }

    @Test
    fun `MessageListResponse 含真实消息应能反序列化`() {
        val raw = """
            {
              "messages":[{"id":1,"title":"t","msg_type":"system"}],
              "total":1,
              "page":1,
              "page_size":10
            }
        """.trimIndent()
        val resp = json.decodeFromString<MessageListResponse>(raw)
        assertNotNull(resp.messages)
        assertEquals(1, resp.messages!!.size)
        assertTrue(resp.messages!![0].title == "t")
    }
}
