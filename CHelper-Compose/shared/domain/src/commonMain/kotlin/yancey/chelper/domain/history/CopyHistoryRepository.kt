package yancey.chelper.domain.history

import kotlinx.coroutines.flow.Flow

interface CopyHistoryRepository {
    fun observe(): Flow<List<String>>

    suspend fun record(text: String)
}
