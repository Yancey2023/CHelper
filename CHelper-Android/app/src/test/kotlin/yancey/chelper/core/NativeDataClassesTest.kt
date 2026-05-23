package yancey.chelper.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Suggestion / ErrorReason / ClickSuggestionResult 都是 JNI 侧用 @Native 直接写字段的容器。
 * Kotlin 这边一旦把可空声明改死、或把默认值改了，C++ 侧就会写崩或读到旧值。
 * 这里锁住默认值和可空性，相当于给 JNI 契约加个静态检查。
 */
class NativeDataClassesTest {

    @Test
    fun `Suggestion 的 name 与 description 默认应为 null`() {
        val s = Suggestion()
        assertNull(s.name)
        assertNull(s.description)

        s.name = "execute"
        s.description = "执行命令"
        assertEquals("execute", s.name)
        assertEquals("执行命令", s.description)
    }

    @Test
    fun `ErrorReason 默认值符合 JNI 约定`() {
        val e = ErrorReason()
        assertNull(e.errorReason)
        // start / end 默认必须是 0，否则 C++ 写入前的"未初始化"判断会失效
        assertEquals(0, e.start)
        assertEquals(0, e.end)
    }

    @Test
    fun `ClickSuggestionResult 默认值符合 JNI 约定`() {
        val r = ClickSuggestionResult()
        assertNotNull(r.text)
        assertEquals("", r.text)
        assertEquals(0, r.selection)

        r.text = "/say hello"
        r.selection = 4
        assertEquals("/say hello", r.text)
        assertEquals(4, r.selection)
    }
}
