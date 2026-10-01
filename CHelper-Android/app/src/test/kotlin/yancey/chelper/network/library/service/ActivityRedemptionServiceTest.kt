package yancey.chelper.network.library.service

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import yancey.chelper.network.library.data.RedeemActivityRequest

class ActivityRedemptionServiceTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `Tier redemption creates converters and decodes grant`() = runBlocking {
        val service = service(
            productId = "tier1_7d",
            fulfillmentInfo = null,
            response = """
                {
                  "status":0,
                  "message":"兑换成功",
                  "data":{
                    "remaining_points":20.5,
                    "product":{"id":"tier1_7d","reward_type":"tier","days":7},
                    "redemption":{"id":12,"status":"fulfilled","points_cost":25},
                    "effective_tier":1,
                    "grant":{"tier":1,"starts_at":"2026-09-30T12:00:00","expires_at":"2026-10-07T12:00:00"},
                    "future_field":true
                  }
                }
            """.trimIndent()
        )

        val result = service.redeemActivityProduct("tier1_7d", RedeemActivityRequest())

        assertTrue(result.isSuccess())
        assertEquals("兑换成功", result.message)
        assertEquals(20.5, result.data?.remainingPoints ?: 0.0, 0.0)
        assertEquals(7, result.data?.product?.days)
        assertEquals(12, result.data?.redemption?.id)
        assertEquals(1, result.data?.effectiveTier)
        assertEquals("2026-10-07T12:00:00", result.data?.grant?.expiresAt)
    }

    @Test
    fun `Physical redemption sends fulfillment info and accepts absent grant`() = runBlocking {
        val fulfillmentInfo = "请通过站内信联系"
        val service = service(
            productId = "gift_card",
            fulfillmentInfo = fulfillmentInfo,
            response = """
                {
                  "status":0,
                  "message":"兑换成功",
                  "data":{
                    "remaining_points":3,
                    "product":{"id":"gift_card","reward_type":"physical"},
                    "redemption":{"id":13,"status":"pending","fulfillment_info":"请通过站内信联系"},
                    "effective_tier":0
                  }
                }
            """.trimIndent()
        )

        val result = service.redeemActivityProduct(
            "gift_card",
            RedeemActivityRequest(fulfillmentInfo)
        )

        assertTrue(result.isSuccess())
        assertEquals("pending", result.data?.redemption?.status)
        assertEquals(fulfillmentInfo, result.data?.redemption?.fulfillmentInfo)
        assertNull(result.data?.grant)
    }

    @Test
    fun `Business failure without data preserves server message`() = runBlocking {
        val service = service(
            productId = "tier1_7d",
            fulfillmentInfo = null,
            response = """{"status":1,"message":"积分不足"}"""
        )

        val result = service.redeemActivityProduct("tier1_7d", RedeemActivityRequest())

        assertFalse(result.isSuccess())
        assertEquals("积分不足", result.message)
        assertNull(result.data)
    }

    private fun service(
        productId: String,
        fulfillmentInfo: String?,
        response: String
    ): CommandLabUserService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("POST", request.method)
                assertEquals("/activity/redeem/$productId", request.url.encodedPath)
                val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                assertEquals(
                    json.encodeToJsonElement(
                        RedeemActivityRequest.serializer(),
                        RedeemActivityRequest(fulfillmentInfo)
                    ),
                    json.parseToJsonElement(body)
                )
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(response.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://commandlab.test/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CommandLabUserService::class.java)
    }
}
