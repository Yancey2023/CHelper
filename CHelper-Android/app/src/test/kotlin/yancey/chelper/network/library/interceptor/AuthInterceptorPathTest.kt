package yancey.chelper.network.library.interceptor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AuthInterceptor.isAuthEndpoint 是认证流程的"避免循环"开关——
 * 登录、注册、验证码这几条路径自己就是去拿 token 的，再加 Authorization 就死循环了。
 *
 * 该方法是 private + 单例的 INSTANCE 上挂着，主代码暴露面极小，
 * 这里用反射把它扒出来直接测，避免污染主代码 API 表面。
 *
 * 测试覆盖：
 *   - 5 个白名单关键字都能匹配（前缀 / 大小写 / 子路径）
 *   - 普通业务路径不应误判为认证端点
 */
class AuthInterceptorPathTest {

    private fun isAuthEndpoint(path: String): Boolean {
        val instance = AuthInterceptor.INSTANCE
        val method = AuthInterceptor::class.java
            .getDeclaredMethod("isAuthEndpoint", String::class.java)
        method.isAccessible = true
        return method.invoke(instance, path) as Boolean
    }

    @Test
    fun `白名单路径应当被识别为认证端点`() {
        // 这些是不能加 Authorization 的——加了就要么 401，要么递归调 token 接口
        val authPaths = listOf(
            "/guest/login",
            "/guest/register",
            "/login",
            "/register",
            "/captcha",
            "/captcha/token",
            "/v2/login",        // 含子路径也应命中
            "/api/Captcha/Status" // 大小写不敏感
        )
        for (p in authPaths) {
            assertTrue("路径 $p 应被识别为认证端点", isAuthEndpoint(p))
        }
    }

    @Test
    fun `普通业务路径不应被误判`() {
        val businessPaths = listOf(
            "/library/functions",
            "/leaderboard",
            "/messages/unread_count",
            "/profile/me",
            "/announcement"
        )
        for (p in businessPaths) {
            assertFalse("业务路径 $p 不应被识别为认证端点", isAuthEndpoint(p))
        }
    }

    @Test
    fun `空路径不应崩溃也不应误判`() {
        // 拦截器对未知 url.encodedPath 必须健壮
        assertFalse(isAuthEndpoint(""))
    }
}
