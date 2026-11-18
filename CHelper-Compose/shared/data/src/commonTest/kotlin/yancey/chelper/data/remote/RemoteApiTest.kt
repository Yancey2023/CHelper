package yancey.chelper.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable

class RemoteApiTest {
    @Serializable private data class Payload(val content: String)

    @Test
    fun responseTransformationRunsOnTheWorkerDispatcher() = runTest {
        val transformationDispatcher = CompletableDeferred<ContinuationInterceptor?>()
        HttpClient(
                MockEngine {
                    respond(
                        """{"content":"say hello"}""",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            ) {
                install(ContentNegotiation) { json() }
            }
            .use { client ->
                client.responsePipeline.intercept(HttpResponsePipeline.Transform) {
                    transformationDispatcher.complete(
                        currentCoroutineContext()[ContinuationInterceptor]
                    )
                }
                val response =
                    RemoteApi(client, "https://example.test").request<Payload>(
                        "detail",
                        HttpMethod.Get,
                    ) {}
                assertEquals("say hello", response.content)
                assertSame(Dispatchers.Default, transformationDispatcher.await())
            }
    }
}
