package yancey.chelper.data.local

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path

/** Platform hosts supply only a path and the application lifetime; storage is shared. */
fun <T> createDataStore(
    path: Path,
    serializer: OkioSerializer<T>,
    scope: CoroutineScope,
    migrations: List<DataMigration<T>> = emptyList(),
): DataStore<T> =
    DataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, serializer, producePath = { path }),
        migrations = migrations,
        scope = scope,
    )
