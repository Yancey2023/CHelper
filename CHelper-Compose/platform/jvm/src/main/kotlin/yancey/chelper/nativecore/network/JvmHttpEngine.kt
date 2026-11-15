package yancey.chelper.nativecore.network

import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.job
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor

/** Shared Android/JVM transport adapter; application policy belongs to common data. */
fun jvmHttpEngine(
    cacheDirectory: File,
    cacheEnabled: Boolean = true,
    configure: (OkHttpClient.Builder) -> Unit = {},
) = run {
    val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cache(
                if (cacheEnabled) Cache(File(cacheDirectory, "http_cache"), 10L * 1024 * 1024)
                else null
            )
            .addInterceptor(BrotliInterceptor)
            .apply { configure(this) }
            .build()
    OkHttp.create { preconfigured = client }
        .also { engine ->
            // Ktor releases its derived clients; this adapter also owns their shared disk cache.
            engine.coroutineContext.job.invokeOnCompletion {
                client.cache?.close()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
}
