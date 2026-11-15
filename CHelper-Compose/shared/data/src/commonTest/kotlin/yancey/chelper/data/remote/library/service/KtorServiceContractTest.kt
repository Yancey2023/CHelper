package yancey.chelper.data.remote.library.service

import io.ktor.client.engine.mock.*
import io.ktor.client.request.get
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import yancey.chelper.data.remote.RemoteApi
import yancey.chelper.data.remote.UploadFile
import yancey.chelper.data.remote.createNetworkClient

class KtorServiceContractTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun queriesOmitNullValuesAndEncodeUserText() = runTest {
        createNetworkClient(
                MockEngine { request ->
                    assertEquals("a+b 中文 &", request.url.parameters["keyword"])
                    assertNull(request.url.parameters["author_id"])
                    assertEquals("3", request.url.parameters["page_num"])
                    assertEquals("no-cache", request.headers["Cache-Control"])
                    respond(
                        """{"status":0,"data":null}""",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            )
            .use { client ->
                KtorCommandLabPublicService(RemoteApi(client, "https://commandlab.test/"))
                    .getFunctions(3, 20, "a+b 中文 &")
            }
    }

    @Test
    fun avatarUsesTheExistingMultipartFieldFilenameMimeAndBytes() = runTest {
        createNetworkClient(
                MockEngine { request ->
                    assertEquals("/avatar", request.url.encodedPath)
                    val body = request.body.toByteArray().decodeToString()
                    assertTrue(body.contains("name=\"file\""))
                    assertTrue(body.contains("filename=\"avatar.png\""))
                    assertTrue(body.contains("image/png"))
                    assertTrue(body.contains("image payload"))
                    respond(
                        """{"status":0,"data":null}""",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            )
            .use { client ->
                KtorCommandLabUserService(RemoteApi(client, "https://commandlab.test/"))
                    .uploadAvatar(
                        UploadFile("image payload".encodeToByteArray(), "avatar.png", "image/png")
                    )
            }
    }

    @Test
    fun explicitOwnerTokenAndBackendIdentifierArePreserved() = runTest {
        createNetworkClient(
                MockEngine { request ->
                    assertEquals("Bearer captured-owner", request.headers["Authorization"])
                    assertEquals("chelper-android", request.headers["X-Client"])
                    assertEquals("CHelper-Android", request.headers["User-Agent"])
                    respond(
                        """{"status":0,"data":null}""",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            )
            .use { client ->
                KtorCommandLabUserService(RemoteApi(client, "https://abyssous.site/"))
                    .uploadLibrary(
                        CommandLabUserService.UploadLibraryRequest("content"),
                        "Bearer captured-owner",
                    )
            }
    }
}
