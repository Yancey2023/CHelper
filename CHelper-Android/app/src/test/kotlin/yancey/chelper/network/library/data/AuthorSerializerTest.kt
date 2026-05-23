package yancey.chelper.network.library.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AuthorSerializer 是兼容性逻辑的典型雷区——后端的 `author` 字段历史上同时存在三种形态：
 *  1) 旧版：直接是字符串 "Akanyi"
 *  2) 新版：JsonObject { id, name, tier, user_title }
 *  3) 没作者：null
 *
 * 任何一种解析挂了，命令库列表就会整列空白甚至崩溃。这里三种形态各覆盖一遍，
 * 再加一组"含未知字段"的健壮性测试——后端加新字段是常态，旧客户端必须不崩。
 */
class AuthorSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `JsonObject 形态应当映射到 AuthorInfo 的对应字段`() {
        val raw = """
            {
              "name":"测试函数",
              "author":{"id":42,"name":"Akanyi","tier":3,"user_title":"打工人"}
            }
        """.trimIndent()
        val fn = json.decodeFromString<LibraryFunction>(raw)
        val a = fn.author
        assertEquals(42, a?.id)
        assertEquals("Akanyi", a?.name)
        assertEquals(3, a?.tier)
        assertEquals("打工人", a?.userTitle)
        // authorName 派生属性必须跟 author.name 同步，不然历史调用方拿到的就是空
        assertEquals("Akanyi", fn.authorName)
    }

    @Test
    fun `字符串形态应当作为 name 兜进 AuthorInfo`() {
        // 早期接口返回 "author":"Akanyi"，新客户端必须能继续读出来
        val raw = """{"name":"老函数","author":"Akanyi"}"""
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertEquals("Akanyi", fn.author?.name)
        assertNull("字符串形态没有 id", fn.author?.id)
        assertNull("字符串形态没有 tier", fn.author?.tier)
        assertEquals("Akanyi", fn.authorName)
    }

    @Test
    fun `author 为 null 时应当解析为 null AuthorInfo`() {
        val raw = """{"name":"匿名函数","author":null}"""
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertNull(fn.author)
        assertNull(fn.authorName)
    }

    @Test
    fun `author 字段直接缺失时也应当为 null`() {
        // 默认值 null + ignoreUnknownKeys，不应该因为字段缺失就抛异常
        val raw = """{"name":"匿名函数"}"""
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertNull(fn.author)
    }

    @Test
    fun `JsonObject 中含未知字段不应让旧客户端崩溃`() {
        // 后端将来加 "badge"、"verified" 一类字段是迟早的事，自定义 deserialize 必须健壮
        val raw = """
            {
              "name":"测试",
              "author":{"id":1,"name":"X","tier":1,"badge":"vip","verified":true}
            }
        """.trimIndent()
        val fn = json.decodeFromString<LibraryFunction>(raw)
        assertEquals("X", fn.author?.name)
        assertEquals(1, fn.author?.id)
    }

    // 编辑公有 mlk 闪退 bug (#36) 的核心回归：把从后端拿到的 LibraryFunction
    // 再 encodeToString 后通过 nav arg 传给上传/编辑页时，如果 author 字段
    // 序列化失败，整个 navigate 调用就会抛异常导致 app 闪退。
    @Test
    fun `LibraryFunction 序列化后能再反序列化回相同的 AuthorInfo`() {
        val original = LibraryFunction(
            name = "测试库",
            author = AuthorInfo(id = 7, name = "栀", tier = 2, userTitle = "测试员")
        )
        val raw = json.encodeToString(LibraryFunction.serializer(), original)
        val round = json.decodeFromString<LibraryFunction>(raw)
        assertEquals(7, round.author?.id)
        assertEquals("栀", round.author?.name)
        assertEquals(2, round.author?.tier)
        assertEquals("测试员", round.author?.userTitle)
    }

    @Test
    fun `author 为 null 的 LibraryFunction 也能正常序列化`() {
        val original = LibraryFunction(name = "无作者", author = null)
        val raw = json.encodeToString(LibraryFunction.serializer(), original)
        val round = json.decodeFromString<LibraryFunction>(raw)
        assertNull(round.author)
        assertEquals("无作者", round.name)
    }

    // 还原 #36 的真实流程：从后端拿到 JSON → decode 成 LibraryFunction →
    // PublicLibraryShowScreen 点编辑时把它再 encodeToString → CPLUploadViewModel
    // 收到后再 decodeFromString。任何一步抛异常都会让进编辑页面直接闪退。
    @Test
    fun `完整后端 JSON 经 编辑入口 二次序列化不丢字段`() {
        val backendRaw = """
            {
              "id": 123,
              "uuid": "abc-uuid",
              "name": "炸服小工具",
              "content": "@name=test\n###Function###\nsay hi\n###End###",
              "author": {"id": 42, "name": "Akanyi", "tier": 3, "user_title": "打工人"},
              "note": "随便测的",
              "tags": ["搞笑", "测试"],
              "version": "1.0.0",
              "create_time": "2026-05-17 12:00:00",
              "preview": "say hi",
              "like_count": 5,
              "is_liked": false,
              "has_public_version": null,
              "is_publish": true
            }
        """.trimIndent()

        val fromBackend = json.decodeFromString<LibraryFunction>(backendRaw)
        // 模拟 PublicLibraryShowScreen 点 "编辑" 的那一行：把整个 library 序列化进 nav arg
        val libJson = json.encodeToString(LibraryFunction.serializer(), fromBackend)
        // 模拟 CPLUploadViewModel.loadFromCloudJson 的反序列化
        val roundTripped = json.decodeFromString<LibraryFunction>(libJson)

        assertEquals(123, roundTripped.id)
        assertEquals("炸服小工具", roundTripped.name)
        assertEquals(42, roundTripped.author?.id)
        assertEquals("Akanyi", roundTripped.author?.name)
        assertEquals(3, roundTripped.author?.tier)
        assertEquals("打工人", roundTripped.author?.userTitle)
        assertEquals(listOf("搞笑", "测试"), roundTripped.tags)
    }
}
