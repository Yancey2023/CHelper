package yancey.chelper.nativecore.enumeration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import redempt.crunch.data.Pair
import redempt.crunch.exceptions.ExpressionCompilationException
import yancey.chelper.domain.enumeration.EnumerationRepository
import yancey.chelper.domain.enumeration.EnumerationVariable

class JvmEnumerationRepository : EnumerationRepository {
    override suspend fun evaluate(
        command: String,
        variables: List<EnumerationVariable>,
        times: Int,
    ): String =
        withContext(Dispatchers.Default) {
            require(times in 0..100000) { "运行次数需要是 0 到 100000 的整数" }
            try {
                EnumerationUtil.run(
                    command,
                    variables.map { Pair(it.name, CustomDoubleSupplier(it.start, it.interval)) },
                    times,
                )
            } catch (e: ExpressionCompilationException) {
                throw IllegalArgumentException("运行时出错：表达式结构有问题", e)
            }
        }
}
