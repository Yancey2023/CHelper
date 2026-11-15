package yancey.chelper.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

internal class RemoteApi(val client: HttpClient, val baseUrl: String) {
    suspend inline fun <reified T> request(
        path: String,
        method: HttpMethod,
        crossinline configure: HttpRequestBuilder.() -> Unit,
    ): T {
        val response =
            client.request(baseUrl.trimEnd('/') + "/" + path) {
                this.method = method
                configure()
            }
        return response.body()
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
