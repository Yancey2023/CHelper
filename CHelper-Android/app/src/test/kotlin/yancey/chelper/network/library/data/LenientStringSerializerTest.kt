package yancey.chelper.network.library.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LenientStringSerializer 之所以存在，是因为后端 createdAt 历史上有时候是字符串
 * "2024-04-12T12:00:00"，有时候是 Unix 时间戳数字。这个序列化器把数字也按字符串吃下来，
 * 配合 TimeFormatExt.formatUnixTime 才能统一展示。
 *
 * 测试要钉死：字符串原样、数字转字符串、布尔转字符串、null 仍是 null。
 * 任何一种行为变化都会让站内函数列表的"创建时间"列乱套。
 */
class LenientStringSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `字符串原样吃进 createdAt`() {
        val raw = """{"name":"x","create_time":"2024-04-12T12:00:00"}"""
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertEquals("2024-04-12T12:00:00", fn.createdAt)
    }

    @Test
    fun `Unix 时间戳数字应被转成字符串`() {
        val raw = """{"name":"x","create_time":1700000000}"""
        val fn = json.decodeFromString<LibraryFunction>(raw)
        // 关键点：拿到的必须是 "1700000000"，不是 1700000000 或 1.7e9，
        // 否则下游 formatUnixTime 走不到 toLong 分支
        assertEquals("1700000000", fn.createdAt)
    }

    @Test
    fun `null 应当保留为 null`() {
        val raw = """{"name":"x","create_time":null}"""
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertNull(fn.createdAt)
    }

    @Test
    fun `布尔类型也应被转成字符串`() {
        // 这是序列化器 lenient 的边角行为——后端不该这么传，但传了也不能崩
        val raw = """{"name":"x","create_time":true}"""
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertEquals("true", fn.createdAt)
    }
}
