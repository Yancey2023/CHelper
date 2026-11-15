package yancey.chelper.data.remote.interceptor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Login and captcha requests must never recursively acquire credentials. */
class AuthInterceptorPathTest {

    private fun isAuthEndpoint(path: String): Boolean {
        return AuthInterceptor.isAuthEndpoint(path)
    }

    @Test
    fun `白名单路径应当被识别为认证端点`() {
        // 这些是不能加 Authorization 的——加了就要么 401，要么递归调 token 接口
        val authPaths =
            listOf(
                "/guest/login",
                "/guest/register",
                "/login",
                "/register",
                "/captcha",
                "/captcha/token",
                "/v2/login", // 含子路径也应命中
                "/api/Captcha/Status", // 大小写不敏感
            )
        for (p in authPaths) {
            assertTrue(isAuthEndpoint(p), "路径 $p 应被识别为认证端点")
        }
    }

    @Test
    fun `普通业务路径不应被误判`() {
        val businessPaths =
            listOf(
                "/library/functions",
                "/leaderboard",
                "/messages/unread_count",
                "/profile/me",
                "/announcement",
                "/auth/chelper/web-sso/authorize",
            )
        for (p in businessPaths) {
            assertFalse(isAuthEndpoint(p), "业务路径 $p 不应被识别为认证端点")
        }
    }

    @Test
    fun `空路径不应崩溃也不应误判`() {
        // 拦截器对未知 url.encodedPath 必须健壮
        assertFalse(isAuthEndpoint(""))
    }
}
