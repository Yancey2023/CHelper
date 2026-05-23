package yancey.chelper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Theme.getColorByToken 是个 token -> color 的映射函数，被语法高亮高频调用。
 * 一旦 case 顺序写错，整片代码就会染错色，所以这里把 1..14 全覆盖一遍，
 * 同时验证未知 token 必须走 normalColor 兜底（这是高亮模块依赖的契约）。
 */
class ThemeTest {

    @Test
    fun `已知 token 应当返回主题里对应的颜色字段`() {
        val theme = Theme().apply {
            colorBoolean = 1
            colorFloat = 2
            colorInteger = 3
            colorSymbol = 4
            colorId = 5
            colorTargetSelector = 6
            colorCommand = 7
            colorBrackets1 = 8
            colorBrackets2 = 9
            colorBrackets3 = 10
            colorString = 11
            colorNull = 12
            colorRange = 13
            colorLiteral = 14
        }
        // 用 i 同时作为 token 和期望值，能直观暴露 case 漏写或顺序错位
        for (i in 1..14) {
            assertEquals("token=$i 映射错误", i, theme.getColorByToken(i, -1))
        }
    }

    @Test
    fun `未知 token 应当走兜底颜色`() {
        val theme = Theme()
        assertEquals(0xCAFE, theme.getColorByToken(0, 0xCAFE))
        assertEquals(0xCAFE, theme.getColorByToken(15, 0xCAFE))
        assertEquals(0xCAFE, theme.getColorByToken(-1, 0xCAFE))
    }

    @Test
    fun `内置日夜主题应当被 init 块完整初始化`() {
        // 默认值是 0，如果 init 漏了哪一项就会留 0，测一下避免静默漏赋值
        val day = Theme.THEME_DAY
        val night = Theme.THEME_NIGHT
        assertNotEquals(0, day.colorBoolean)
        assertNotEquals(0, day.colorCommand)
        assertNotEquals(0, day.colorString)
        assertNotEquals(0, night.colorBoolean)
        assertNotEquals(0, night.colorCommand)
        assertNotEquals(0, night.colorString)
        // 日夜主题必然有差异，否则就是初始化逻辑被无意覆盖了
        assertNotEquals(day.colorCommand, night.colorCommand)
    }
}
