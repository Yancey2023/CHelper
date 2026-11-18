package yancey.chelper.domain.assets

fun interface AssetRepository {
    suspend fun readText(path: String): String
}
