package yancey.chelper.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
class ScreenKeyCompatibilityTest {
    @Test
    fun packageAndScreenRenamesRetainSavedDestinationIdentities() {
        assertEquals(
            "yancey.chelper.ui.HomeScreenKey",
            HomeScreenKey.serializer().descriptor.serialName,
        )
        assertEquals(
            "yancey.chelper.ui.CPLUserScreenKey",
            AccountScreenKey.serializer().descriptor.serialName,
        )
        assertEquals(
            "yancey.chelper.ui.CPLUploadScreenKey",
            LibraryUploadScreenKey.serializer().descriptor.serialName,
        )
        assertEquals(
            "yancey.chelper.ui.LocalLibraryShowScreenKey",
            LocalLibraryShowScreenKey.serializer().descriptor.serialName,
        )
    }

    @Test
    fun savedLibraryRouteArgumentsStillDecodeAfterPackageMove() {
        val restored =
            Json.decodeFromString<LocalLibraryShowScreenKey>(
                """{"localEntryId":"stable-library","id":42}"""
            )
        assertEquals(LocalLibraryShowScreenKey("stable-library", 42), restored)
    }
}
