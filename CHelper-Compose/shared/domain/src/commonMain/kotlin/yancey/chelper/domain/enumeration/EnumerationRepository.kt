package yancey.chelper.domain.enumeration

data class EnumerationVariable(val name: String, val start: Double, val interval: Double)

fun interface EnumerationRepository {
    suspend fun evaluate(command: String, variables: List<EnumerationVariable>, times: Int): String
}
