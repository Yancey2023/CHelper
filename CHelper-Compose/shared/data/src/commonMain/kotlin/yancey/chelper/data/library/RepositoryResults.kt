package yancey.chelper.data.library

import kotlinx.serialization.json.JsonElement
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.domain.library.*

internal fun <T, R> BaseResult<T>.outcome(map: (T) -> R): RepositoryResult<R> =
    RepositoryResult(if (isSuccess()) data?.let(map) else null, message, isSuccess())

internal fun BaseResult<JsonElement?>.completion() = outcome { Unit }
