package yancey.chelper.domain.assets
interface AppContentRepository {
    suspend fun announcement(): Announcement

    suspend fun latestVersion(): VersionInfo
}
