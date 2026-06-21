package yancey.chelper.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SelectedString 是不可变的数据持有类，测试只需保证构造参数原样落到字段上。
 * 这里覆盖三个有代表性的场景：光标重合、光标范围正常、空文本——
 * 因为它会被命令编辑器在各种边界状态下传入，任何字段错位都会让上游高亮和补全错乱。
 */
class SelectedStringTest {

    @Test
    fun `光标重合时三个字段应当一致`() {
        val s = SelectedString("abc", 1, 1)
        assertEquals("abc", s.text)
        assertEquals(1, s.selectionStart)
        assertEquals(1, s.selectionEnd)
    }

    @Test
    fun `选中范围应当原样保留`() {
        val s = SelectedString("hello world", 2, 7)
        assertEquals("hello world", s.text)
        assertEquals(2, s.selectionStart)
        assertEquals(7, s.selectionEnd)
    }

    @Test
    fun `空文本不应抛异常`() {
        // 用户清空输入框时，光标位置必为 0，是真实可达状态，不能崩
        val s = SelectedString("", 0, 0)
        assertEquals("", s.text)
        assertEquals(0, s.selectionStart)
        assertEquals(0, s.selectionEnd)
    }
}
