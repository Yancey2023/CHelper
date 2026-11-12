package yancey.chelper.ui.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These names are persisted by Navigation; package moves must not change destination IDs.

@Serializable @SerialName("yancey.chelper.ui.HomeScreenKey") object HomeScreenKey

@Serializable @SerialName("yancey.chelper.ui.CompletionScreenKey") object CompletionScreenKey

@Serializable @SerialName("yancey.chelper.ui.HistoryScreenKey") object HistoryScreenKey

@Serializable @SerialName("yancey.chelper.ui.SettingsScreenKey") object SettingsScreenKey

@Serializable @SerialName("yancey.chelper.ui.Old2NewScreenKey") object Old2NewScreenKey

@Serializable
@SerialName("yancey.chelper.ui.Old2NewIMEGuideScreenKey")
object Old2NewIMEGuideScreenKey

@Serializable @SerialName("yancey.chelper.ui.EnumerationScreenKey") object EnumerationScreenKey

@Serializable
@SerialName("yancey.chelper.ui.LocalLibraryListScreenKey")
object LocalLibraryListScreenKey

@Serializable
@SerialName("yancey.chelper.ui.LocalLibraryShowScreenKey")
data class LocalLibraryShowScreenKey(val localEntryId: String? = null, val id: Int? = null)

@Serializable
@SerialName("yancey.chelper.ui.LibraryEditScreenKey")
data class LibraryEditScreenKey(val localEntryId: String? = null, val id: Int? = null)

@Serializable @SerialName("yancey.chelper.ui.RawtextScreenKey") object RawtextScreenKey

@Serializable @SerialName("yancey.chelper.ui.AboutScreenKey") object AboutScreenKey

@Serializable
@SerialName("yancey.chelper.ui.PublicLibraryListScreenKey")
object PublicLibraryListScreenKey

@Serializable @SerialName("yancey.chelper.ui.LibraryMainScreenKey") object LibraryMainScreenKey

@Serializable
@SerialName("yancey.chelper.ui.PublicLibraryShowScreenKey")
data class PublicLibraryShowScreenKey(
    val id: Int,
    val isPrivate: Boolean = false,
    val importToLocal: Boolean = false,
)

@Serializable
@SerialName("yancey.chelper.ui.ShowTextScreenKey")
data class ShowTextScreenKey(val title: String, val assetPath: String)

@Serializable
@SerialName("yancey.chelper.ui.LibrarySearchScreenKey")
data class LibrarySearchScreenKey(val initialKeyword: String? = null)

@Serializable @SerialName("yancey.chelper.ui.CPLUserScreenKey") object AccountScreenKey

@Serializable
@SerialName("yancey.chelper.ui.CPLUploadScreenKey")
data class LibraryUploadScreenKey(val editLibraryId: Int = -1)

@Serializable @SerialName("yancey.chelper.ui.LeaderboardScreenKey") object LeaderboardScreenKey

@Serializable
@SerialName("yancey.chelper.ui.UserProfileScreenKey")
data class UserProfileScreenKey(val id: Int)

@Serializable @SerialName("yancey.chelper.ui.MessageScreenKey") object MessageScreenKey

@Serializable
@SerialName("yancey.chelper.ui.FavoriteLibraryListScreenKey")
object FavoriteLibraryListScreenKey

@Serializable
@SerialName("yancey.chelper.ui.ActivityCenterScreenKey")
data class ActivityCenterScreenKey(val initialSection: Int = 0)
