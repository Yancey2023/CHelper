package yancey.chelper.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class RemoteApi(val client: HttpClient, val baseUrl: String) {
    suspend inline fun <reified T> request(
        path: String,
        method: HttpMethod,
        crossinline configure: HttpRequestBuilder.() -> Unit,
    ): T =
        withContext(Dispatchers.Default) {
            val response =
                client.request(baseUrl.trimEnd('/') + "/" + path) {
                    this.method = method
                    configure()
                }
            // Ktor transformations include JSON encoding/decoding and must not block the caller UI.
            response.body<T>()
        }
}

data class UploadFile(val bytes: ByteArray, val name: String, val mimeType: String) {
    internal fun multipart() =
        MultiPartFormDataContent(
            formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=" + name.quote())
                    },
                )
            }
        )

    private fun String.quote() =
        "\"" + replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_") + "\""
}
