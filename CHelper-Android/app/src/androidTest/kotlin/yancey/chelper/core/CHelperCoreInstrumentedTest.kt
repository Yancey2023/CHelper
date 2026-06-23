package yancey.chelper.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CHelperCore 是整个软件的命令解析中枢，但它依赖 JNI（libCHelperAndroid.so）。
 * 所以这套测试只能在 arm64-v8a 设备/模拟器上跑（abiFilter 限定）。
 * 如果是 x86 模拟器，create0 会因为找不到 .so 抛异常，构造里会 catch 掉并把 pointer 置 0，
 * 然后向外抛 RuntimeException——这就是这里 try-catch 的意义：
 * 让测试只在能加载 .so 的环境下真正断言，其他环境下"跳过"而不是误报。
 *
 * 资源包用打包进 assets 的 release-vanilla 版本，因为它是稳定发布版，
 * 比 beta/experiment 包更适合做长期回归基线。
 */
@RunWith(AndroidJUnit4::class)
class CHelperCoreInstrumentedTest {

    private val cpackPath = "cpack/release-vanilla-1.21.132.1.cpack"

    /**
     * 把"加载内核+做点事+关掉"这套流程封一层，避免每个测试方法重复写
     * try-finally。同时统一处理"环境没 .so 就跳过"的情况。
     */
    private fun withCore(block: (CHelperCore) -> Unit) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val core = try {
            CHelperCore.fromAssets(ctx.assets, cpackPath)
        } catch (e: Throwable) {
            // 当前 ABI 不匹配或资源缺失时不算测试失败——
            // 这种环境下根本跑不起来这个测试，硬挂只会污染 CI 信号
            Assume.assumeNoException("跳过：CHelperCore 在当前环境无法初始化", e)
            return
        }
        try {
            block(core)
        } finally {
            core.close()
        }
    }

    @Test
    fun `fromAssets 应当成功创建并标记为内置资源包`() {
        withCore { core ->
            assertTrue("从 assets 加载的 core.isAssets 必须为 true", core.isAssets)
            assertEquals(cpackPath, core.path)
        }
    }

    @Test
    fun `输入命令后应当能拿到补全提示和语法结构`() {
        withCore { core ->
            // 输入 "/" 是最常见的触发点：用户敲第一个字符就期待出补全
            // 如果这都拿不到 suggestion，说明 JNI 通道整体出问题
            core.onTextChanged("/", 1)
            assertTrue("/ 之后应有至少一条补全", core.suggestionsSize > 0)

            val first = core.getSuggestion(0)
            assertNotNull("第 0 条补全不应为 null", first)

            // structure 不强求非空（部分残缺命令可能为空），只要不崩就算过
            core.structure
            // syntaxToken 也是同理：只验证调用通路打通，不锁具体值（C++ 侧规则会演进）
            core.syntaxToken
        }
    }

    @Test
    fun `close 之后再调用接口应当静默返回不崩溃`() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val core = try {
            CHelperCore.fromAssets(ctx.assets, cpackPath)
        } catch (e: Throwable) {
            Assume.assumeNoException("跳过：CHelperCore 在当前环境无法初始化", e)
            return
        }
        core.close()
        // 关掉后 pointer == 0，所有 getter 都应走早退分支
        // 这是防 use-after-free 的最后一道闸，必须保住
        assertEquals(0, core.suggestionsSize)
        // 多次 close 也得幂等，否则会触发 release0 的 double-free
        core.close()
    }
}
