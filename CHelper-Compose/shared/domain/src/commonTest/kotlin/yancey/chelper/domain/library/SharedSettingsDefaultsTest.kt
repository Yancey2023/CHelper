package yancey.chelper.domain.library

import kotlin.test.*
import kotlinx.serialization.json.Json
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults

class SharedSettingsDefaultsTest {
    @Test
    fun missingStoredFieldsKeepTheAndroidDefaultsOnBothHosts() {
        val persisted = Json.decodeFromString<Settings>("{}")
        assertEquals(Settings(), persisted)
        val effective = persisted.withDefaults()
        assertEquals("release-experiment", effective.cpackBranch)
        assertEquals(4000, effective.syntaxHighlightMaxLength)
        assertEquals(true, effective.isShowErrorReason)
        assertEquals(true, effective.isSyntaxHighlight)
        assertEquals("MODE_NIGHT_FOLLOW_SYSTEM", effective.themeId)
        assertEquals(Settings(), persisted)
    }

    @Test
    fun explicitPreferencesOverrideDefaultsWithoutLosingFalseValues() {
        val settings =
            Settings(
                    isSyntaxHighlight = false,
                    isShowErrorReason = false,
                    cpackBranch = "release-vanilla",
                )
                .withDefaults()
        assertEquals(false, settings.isSyntaxHighlight)
        assertEquals(false, settings.isShowErrorReason)
        assertEquals("release-vanilla", settings.cpackBranch)
    }
}
