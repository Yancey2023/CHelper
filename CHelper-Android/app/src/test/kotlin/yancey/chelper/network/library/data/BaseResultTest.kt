package yancey.chelper.network.library.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BaseResult 是几乎所有网络返回的外壳，isSuccess() 的语义被全应用引用：
 * 只要 status == 0 才算成功。这里把"成功 / 业务失败 / 字段缺失"三种情况钉死，
 * 防止有人手贱改成 status != null 之类的事。
 *
 * 顺便用 Json 端到端解析一遍，确认 @SerialName("error_type") 这一类下划线/驼峰
 * 转换没被悄悄漏掉——这是 retrofit + kotlinx.serialization 链路最容易翻车的点。
 */
class BaseResultTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `status 为 0 视为成功`() {
        val r = BaseResult<String>(status = 0, data = "ok")
        assertTrue(r.isSuccess())
    }

    @Test
    fun `status 非 0 一律失败`() {
        assertFalse(BaseResult<String>(status = 1).isSuccess())
        assertFalse(BaseResult<String>(status = -1).isSuccess())
        assertFalse(BaseResult<String>(status = 200).isSuccess()) // HTTP 200 也别误以为成功，业务码才算
    }

    @Test
    fun `status 缺失视为失败`() {
        // 后端某些异常路径不带 status，这时候宁可当失败也不能误判成功
        assertFalse(BaseResult<String>(status = null).isSuccess())
    }

    @Test
    fun `error_type 字段在 JSON 中应能下划线落到 errorType`() {
        val raw = """{"status":1,"error_type":"NEED_LOGIN","message":"请先登录"}"""
        val r = json.decodeFromString<BaseResult<String>>(raw)
        assertFalse(r.isSuccess())
        assertEquals("NEED_LOGIN", r.errorType)
        assertEquals("请先登录", r.message)
        assertNull(r.data)
    }

    @Test
    fun `成功时 data 字段应当被解析出来`() {
        val raw = """{"status":0,"data":"hello","message":""}"""
        val r = json.decodeFromString<BaseResult<String>>(raw)
        assertTrue(r.isSuccess())
        assertEquals("hello", r.data)
        assertNotNull(r.message)
    }
}
