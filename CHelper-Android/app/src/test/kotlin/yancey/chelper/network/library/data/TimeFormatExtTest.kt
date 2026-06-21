package yancey.chelper.network.library.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * formatUnixTime 同时处理"ISO 字符串"和"Unix 时间戳（秒/毫秒）"两种输入，
 * 又有 dateOnly 开关，组合很容易写错。这里按输入形态分桶覆盖：
 * null/空、ISO、纯数字（秒）、纯数字（毫秒）、非法字符串。
 *
 * 时间戳的预期值由 SimpleDateFormat 当场算出来，不写死字符串——
 * 因为构建机时区不一定和我本机一样，写死会随机挂掉 CI。
 */
class TimeFormatExtTest {

    @Test
    fun `null 与空串应当返回 未知`() {
        assertEquals("未知", null.formatUnixTime())
        assertEquals("未知", "".formatUnixTime())
        assertEquals("未知", "".formatUnixTime(dateOnly = true))
    }

    @Test
    fun `ISO 字符串 dateOnly 取前 10 位`() {
        assertEquals("2024-04-12", "2024-04-12T12:00:00".formatUnixTime(dateOnly = true))
    }

    @Test
    fun `ISO 字符串完整模式去掉 T 并截到 19 位`() {
        assertEquals("2024-04-12 12:00:00", "2024-04-12T12:00:00".formatUnixTime())
        // 后面带毫秒/时区也得截掉，不能漏出去
        assertEquals("2024-04-12 12:00:00", "2024-04-12T12:00:00.123Z".formatUnixTime())
    }

    @Test
    fun `毫秒级时间戳应当用本地时区格式化`() {
        val ms = 1_700_000_000_000L
        val expected = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
        assertEquals(expected, ms.toString().formatUnixTime())
    }

    @Test
    fun `秒级时间戳应当被自动放大成毫秒`() {
        // 函数里以 1e11 为分界判断秒/毫秒，传秒级数字进去要自动 ×1000，
        // 否则会得到一个 1970 年附近的错误时间
        val s = 1_700_000_000L
        val expected = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(s * 1000))
        assertEquals(expected, s.toString().formatUnixTime())
    }

    @Test
    fun `dateOnly 模式只输出年月日`() {
        val ms = 1_700_000_000_000L
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
        assertEquals(expected, ms.toString().formatUnixTime(dateOnly = true))
    }

    @Test
    fun `不含横杠且不是数字的字符串原样返回`() {
        // 既不带 -（不会被当 ISO），也 toLong 失败，那就保留原值给上游展示，不能丢数据
        assertEquals("notADate", "notADate".formatUnixTime())
        assertEquals("garbage", "garbage".formatUnixTime(dateOnly = true))
    }

    @Test
    fun `含横杠但短于 19 位的串走 ISO 分支会被 take 截留原长`() {
        // 锁住"只要带 - 就走字符串截断"这个现行行为，
        // 否则未来有人改成"先 toLong 再 fallback"会悄悄改变上游展示
        assertEquals("abc-def", "abc-def".formatUnixTime())
    }
}
