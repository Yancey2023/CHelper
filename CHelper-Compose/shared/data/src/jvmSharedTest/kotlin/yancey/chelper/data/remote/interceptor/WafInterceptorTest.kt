package yancey.chelper.data.remote.interceptor

import java.io.IOException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.waf.WafHelper

class WafInterceptorTest {
    @Test
    fun verificationCookiesStayOnTheLibraryOrigin() {
        WafHelper.install({ "EO_Bot_Ssid=secret" }, { it?.invoke(false) }, { "browser-agent" })
        val client =
            OkHttpClient.Builder()
                .addInterceptor(WafInterceptor())
                .addInterceptor { chain ->
                    assertNull(chain.request().header("Cookie"))
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("ok".toResponseBody())
                        .build()
                }
                .build()
        try {
            client
                .newCall(
                    Request.Builder()
                        .url("https://www.yanceymc.cn/api/chelper/announcement.json")
                        .build()
                )
                .execute()
                .close()
        } finally {
            WafHelper.install({ null }, { it?.invoke(false) })
        }
    }

    @Test
    fun refreshWithoutACookieRaisesAnIoErrorInsteadOfReturningAClosedResponse() {
        WafHelper.install({ null }, { it?.invoke(true) })
        val client =
            OkHttpClient.Builder()
                .addInterceptor(WafInterceptor())
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "text/html")
                        .body(
                            "<script>__tst_status=1</script>"
                                .toResponseBody("text/html".toMediaType())
                        )
                        .build()
                }
                .build()
        try {
            try {
                client
                    .newCall(Request.Builder().url(ServiceManager.LAB_BASE_URL).build())
                    .execute()
                    .close()
                fail("Missing verification cookie must fail the request")
            } catch (expected: IOException) {
                assertTrue(expected.message.orEmpty().contains("验证失败"))
            }
        } finally {
            WafHelper.install({ null }, { it?.invoke(false) })
        }
    }
}
