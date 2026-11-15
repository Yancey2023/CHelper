package yancey.chelper.ui.designsystem.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone

/** Formatting contracts use an explicit zone and independently known timestamps. */
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
        val expected = "2023-11-15 06:13:20"
        assertEquals(
            expected,
            ms.toString().formatUnixTime(timeZone = TimeZone.of("Asia/Shanghai")),
        )
    }

    @Test
    fun `秒级时间戳应当被自动放大成毫秒`() {
        // 函数里以 1e11 为分界判断秒/毫秒，传秒级数字进去要自动 ×1000，
        // 否则会得到一个 1970 年附近的错误时间
        val s = 1_700_000_000L
        val expected = "2023-11-15 06:13:20"
        assertEquals(expected, s.toString().formatUnixTime(timeZone = TimeZone.of("Asia/Shanghai")))
    }

    @Test
    fun `dateOnly 模式只输出年月日`() {
        val ms = 1_700_000_000_000L
        val expected = "2023-11-15"
        assertEquals(
            expected,
            ms.toString().formatUnixTime(dateOnly = true, timeZone = TimeZone.of("Asia/Shanghai")),
        )
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
