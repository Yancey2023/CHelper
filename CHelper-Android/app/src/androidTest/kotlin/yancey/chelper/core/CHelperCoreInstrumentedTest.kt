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
 * 如果是 x86 模拟器，compose 会因为找不到 .so 抛异常——这就是这里 try-catch 的意义：
 * 让测试只在能加载 .so 的环境下真正断言，其他环境下"跳过"而不是误报。
 *
 * 内核按主包段 compose（main-pack.chepack 内置资产，段取 beta/vanilla 与桌面测试一致），
 * 旧 assets/cpack/*.cpack 加载通道（fromAssets/fromFile）已随主路径切换删除。
 */
@RunWith(AndroidJUnit4::class)
class CHelperCoreInstrumentedTest {

    private val segment = "beta/vanilla"

    /** 打开主包并合成段内核；环境不满足（主包缺失/JNI 未就绪/合成失败）时跳过而非失败 */
    private fun openCore(): CHelperCore? {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val mainPack = MainPackProvider.get(ctx)
        if (mainPack == null) {
            Assume.assumeTrue("跳过：main-pack.chepack 不可用", false)
            return null
        }
        return try {
            CHelperCore.compose(mainPack, arrayOf(segment), emptyList())
        } catch (e: Throwable) {
            Assume.assumeNoException("跳过：compose 在当前环境失败", e)
            null
        }
    }

    /**
     * 把"加载内核+做点事+关掉"这套流程封一层，避免每个测试方法重复写
     * try-finally。同时统一处理"环境没 .so 就跳过"的情况。
     */
    private fun withCore(block: (CHelperCore) -> Unit) {
        val core = openCore() ?: return
        try {
            block(core)
        } finally {
            core.close()
        }
    }

    @Test
    fun composeFromMainPackCreatesCore() {
        withCore { /* 能走到这里即说明主包打开 + compose 成功 */ }
    }

    @Test
    fun `输入命令后应当能拿到补全提示和语法结构`() {
        withCore { core ->
            // 输入 "/" 是最常见的触发点：用户敲第一个字符就期待出补全
            // 如果这都拿不到 suggestion，说明 JNI 通道整体出问题
            core.createContext("/").use { context ->
                assertTrue("/ 之后应有至少一条补全", context.getSuggestionsSize(1) > 0)
                assertNotNull("第 0 条补全不应为 null", context.getSuggestion(1, 0))

                // structure 不强求非空（部分残缺命令可能为空），只要不崩就算过
                context.structure
                // syntaxToken 也是同理：只验证调用通路打通，不锁具体值（C++ 侧规则会演进）
                context.syntaxToken
            }
        }
    }

    @Test
    fun closedCoreApisRemainSafe() {
        val core = openCore() ?: return
        val context = core.createContext("list")
        core.close()
        // CommandContext 持有资源包的共享引用，core 关闭后依然可用
        assertTrue("core关闭后context依然应能获取结构", !context.structure.isNullOrEmpty())
        context.close()
        // 关闭后 pointer == 0，所有 getter 都应走早退分支
        // 这是防 use-after-free 的最后一道闸，必须保住
        assertEquals(null, context.structure)
        assertEquals(0, context.nodeCount)
        assertEquals(null, context.errorReasons)
        assertEquals(0, context.getSuggestionsSize(0))
        // 多次 close 也得幂等，否则会触发 release0 的 double-free
        context.close()
        // 已关闭的内核不能再创建上下文
        try {
            core.createContext("list")
            throw IllegalStateException("已关闭的内核创建上下文应当抛出异常")
        } catch (expected: RuntimeException) {
            // 符合预期
        }
    }

    @Test
    fun semanticNodeCountCrossesEditorHintThresholdAtNineteen() {
        withCore { core ->
            val eighteenNodes =
                "execute as @a as @a as @a as @a as @a as @a as @a run say hi"
            core.createContext(eighteenNodes).use { context ->
                assertEquals(18, context.nodeCount)
            }

            val nineteenNodes =
                "execute as @a as @a as @a as @a as @a as @a as @a as @a run list"
            core.createContext(nineteenNodes).use { context ->
                assertEquals(19, context.nodeCount)
            }
        }
    }

    @Test
    fun createContextProvidesAllReadOnlyOperations() {
        withCore { core ->
            val command = "give @s stone 12 1"
            core.createContext(command).use { context ->
                assertEquals(command, context.command)
                assertTrue("完整命令应能获取到命令结构", !context.structure.isNullOrEmpty())
                assertTrue("完整命令不应有错误原因", context.errorReasons?.isEmpty() == true)
                assertTrue("应能获取到语法高亮", context.syntaxToken?.size == command.length)
                assertTrue("应能获取到语义节点数量", context.nodeCount > 0)
                assertTrue("应能获取到参数注释", !context.getParamHint(5).isNullOrEmpty())
                // 完整的命令在末尾没有补全
                assertEquals(0, context.getSuggestionsSize(command.length))
            }
        }
    }

    @Test
    fun contextAppliesSuggestionWithoutMutatingItself() {
        withCore { core ->
            val command = "give @s sto"
            core.createContext(command).use { context ->
                assertTrue("未输入完成的命令应有补全提示", context.getSuggestionsSize(command.length) > 0)
                assertNotNull("第 0 条补全不应为 null", context.getSuggestion(command.length, 0))

                val first = context.applySuggestion(command.length, 0)
                assertNotNull(first)
                assertTrue("应用补全后文本应变长", first!!.text.length > command.length)

                // 上下文是只读的，同样的操作可以重复执行且结果一致
                val second = context.applySuggestion(command.length, 0)
                assertEquals(first.text, second?.text)
                assertEquals(first.selection, second?.selection)
            }
        }
    }

    @Test
    fun contextOutlivesClosedCore() {
        val core = openCore() ?: return
        val context = core.createContext("list")
        core.close()
        try {
            // CommandContext持有资源包的共享引用，core关闭后依然可用
            assertTrue("core关闭后context依然应能获取结构", !context.structure.isNullOrEmpty())
            assertEquals(1, context.nodeCount)
        } finally {
            context.close()
        }
    }

    @Test
    fun contextsRunInParallelOnSharedCore() {
        withCore { core ->
            val commands = listOf(
                "list",
                "give @s stone 12 1",
                "execute if block ~~~ anvil run say hi",
                "tellraw @a {\"rawtext\":[{\"text\":\"aaa\"}]}",
            )
            // 先串行生成每个命令的标准结构
            val expected = commands.map { command ->
                core.createContext(command).use { it.structure }
            }
            // 再用多个线程同时创建并读取CommandContext，共享同一个内核
            val threadCount = 4
            val rounds = 4
            val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val threads = (0 until threadCount).map {
                Thread {
                    repeat(rounds) {
                        commands.forEachIndexed { index, command ->
                            try {
                                core.createContext(command).use { context ->
                                    if (context.structure != expected[index]) {
                                        failures.add("结构不一致: $command")
                                    }
                                }
                            } catch (e: Throwable) {
                                failures.add("异常: ${e.message}")
                            }
                        }
                    }
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            assertTrue("并行使用CommandContext不应失败: ${failures.firstOrNull()}", failures.isEmpty())
        }
    }
}
