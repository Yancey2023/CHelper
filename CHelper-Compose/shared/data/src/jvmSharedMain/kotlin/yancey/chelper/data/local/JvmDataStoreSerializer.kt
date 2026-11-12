package yancey.chelper.data.local

import androidx.datastore.core.Serializer
import androidx.datastore.core.okio.OkioSerializer
import java.io.InputStream
import java.io.OutputStream
import okio.buffer
import okio.sink
import okio.source

/** Stream adapters for the original binary serializer and JVM DataStore tests. */
fun <T> OkioSerializer<T>.asStreamSerializer(): Serializer<T> =
    object : Serializer<T> {
        override val defaultValue
            get() = this@asStreamSerializer.defaultValue

        override suspend fun readFrom(input: InputStream) = this@asStreamSerializer.readFrom(input)

        override suspend fun writeTo(t: T, output: OutputStream) =
            this@asStreamSerializer.writeTo(t, output)
    }

suspend fun <T> OkioSerializer<T>.readFrom(input: InputStream): T =
    readFrom(input.source().buffer())

suspend fun <T> OkioSerializer<T>.writeTo(value: T, output: OutputStream) {
    val buffered = output.sink().buffer()
    writeTo(value, buffered)
    buffered.flush()
}
