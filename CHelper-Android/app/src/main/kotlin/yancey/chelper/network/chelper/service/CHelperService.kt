/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.network.chelper.service

import retrofit2.http.GET
import yancey.chelper.network.chelper.data.Announcement
import yancey.chelper.network.chelper.data.VersionInfo

interface CHelperService {
    @GET("announcement.json")
    suspend fun getAnnouncement(): Announcement

    @GET("apk-latest.json")
    suspend fun getLatestVersionInfo(): VersionInfo
}
