package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.widget.TextFieldWithIconPreview
import yancey.chelper.ui.feature.conversion.ime.*
import yancey.chelper.ui.feature.home.*
import yancey.chelper.ui.feature.library.browse.*
import yancey.chelper.ui.feature.library.components.*

class SharedPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allPreviewsRenderWithoutRepositoriesNavigationOwnersOrApplicationStartup() {
        val previews: List<@Composable () -> Unit> =
            listOf(
                { ImeGuideLightPreview() },
                { ImeGuideDarkPreview() },
                { ImeUndoLightPreview() },
                { ImeUndoDarkPreview() },
                { HomeScreenLightThemePreview() },
                { HomeScreenDarkThemePreview() },
                { PublicLibraryListScreenLightThemePreview() },
                { PublicLibraryListScreenDarkThemePreview() },
                { LibraryDetailLightPreview() },
                { LibraryDetailDarkPreview() },
                { LibraryVersionFieldsLightPreview() },
                { LibraryVersionFieldsDarkPreview() },
                { TextFieldWithIconPreview() },
            )
        val index = mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.requiredSize(420.dp, 740.dp).testTag("preview")) {
                previews[index.intValue]()
            }
        }
        previews.indices.forEach { i ->
            compose.runOnIdle { index.intValue = i }
            val bitmap = compose.onNodeWithTag("preview").captureToImage()
            assertTrue("Preview $i must render", bitmap.width > 0 && bitmap.height > 0)
        }
    }
}
