package yancey.chelper.network.chelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Announcement 自己重写了 equals/hashCode，**故意**只比较 5 个字段：
 *   isEnable / isForce / isBigDialog / title / message
 * 把 isEnableCommandLab、publicLibraryMinVersion 排除在外。
 *
 * 这不是 bug——这是约定：**只有"用户可见的公告内容"变了才弹新窗**，
 * 后端调整 commandLab 开关或 minVersion 门限不应该让用户被同一公告再骚扰一次。
 *
 * 这种"看着像漏写、其实是关键约定"的代码，必须配测试守住。否则未来某天有人
 * "顺手补全"加几个字段进 equals，用户体验立刻变差。
 */
class AnnouncementEqualsTest {

    private fun base() = Announcement(
        isEnable = true,
        isForce = false,
        isBigDialog = false,
        isEnableCommandLab = true,
        publicLibraryMinVersion = 10,
        title = "维护通知",
        message = "今晚 23 点维护"
    )

    @Test
    fun `等价的两条公告应当相等`() {
        assertEquals(base(), base())
        assertEquals(base().hashCode(), base().hashCode())
    }

    @Test
    fun `title 改变应当不相等`() {
        val a = base()
        val b = base().apply { title = "其他" }
        assertNotEquals(a, b)
    }

    @Test
    fun `message 改变应当不相等`() {
        val a = base()
        val b = base().apply { message = "新文案" }
        assertNotEquals(a, b)
    }

    @Test
    fun `isEnableCommandLab 改变不应触发不等`() {
        // 这是核心约定：服务端开关变更不能让用户被同一公告再弹一次
        val a = base()
        val b = base().apply { isEnableCommandLab = false }
        assertEquals("isEnableCommandLab 不应纳入 equals", a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `publicLibraryMinVersion 改变不应触发不等`() {
        val a = base()
        val b = base().apply { publicLibraryMinVersion = 999 }
        assertEquals("publicLibraryMinVersion 不应纳入 equals", a, b)
    }

    @Test
    fun `跟 null 比较以及跟其它类型比较应当返回 false`() {
        val a = base()
        // 用 .equals() 显式调，绕开编译器对非空类型 == null 的提前断言
        @Suppress("SENSELESS_COMPARISON")
        assertFalse(a.equals(null))
        @Suppress("EqualsBetweenInconvertibleTypes")
        assertFalse(a.equals("not announcement"))
    }

    @Test
    fun `自身相等性`() {
        val a = base()
        assertTrue(a == a)
    }
}
