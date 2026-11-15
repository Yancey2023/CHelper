package yancey.chelper.data.remote.app.service

import io.ktor.client.request.*
import io.ktor.http.*
import yancey.chelper.data.remote.RemoteApi
import yancey.chelper.data.remote.app.model.Announcement
import yancey.chelper.data.remote.app.model.VersionInfo

internal class KtorCHelperService(private val api: RemoteApi) : CHelperService {
    override suspend fun getAnnouncement(): Announcement =
        api.request("announcement.json", HttpMethod.Get) {}

    override suspend fun getLatestVersionInfo(): VersionInfo =
        api.request("apk-latest.json", HttpMethod.Get) {}
}
