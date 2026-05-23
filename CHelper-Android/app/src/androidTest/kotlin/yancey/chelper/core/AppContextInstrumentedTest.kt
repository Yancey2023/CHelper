package yancey.chelper.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 最基础的环境烟雾测试：保证 androidTest 链路（AGP、AndroidJUnitRunner、AndroidX Test）
 * 真的能跑起来。如果连这个都失败，下面更复杂的仪器测试就不用看了，
 * 直接去查依赖或者构建配置就行。
 *
 * 这里用 startsWith 而不是 equals，是因为 debug / beta 构建会给 applicationId
 * 加 ".debug" / ".beta" 后缀，硬编码会让本地跑测试看运气。
 */
@RunWith(AndroidJUnit4::class)
class AppContextInstrumentedTest {

    @Test
    fun `applicationContext 的包名应当以基础包名打头`() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(ctx)
        assertTrue(
            "实际包名=${ctx.packageName}",
            ctx.packageName.startsWith("yancey.chelper")
        )
    }

    @Test
    fun `assetManager 应当能列出 cpack 目录`() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val files = ctx.assets.list("cpack") ?: emptyArray()
        // 资源包文件是软件运行的前置条件，少了就说明打包流程或目录被改坏了
        assertTrue("cpack 下没文件，资源打包可能挂了", files.isNotEmpty())
        assertTrue(
            "cpack 下应有 .cpack 文件，实际：${files.toList()}",
            files.any { it.endsWith(".cpack") }
        )
    }
}
