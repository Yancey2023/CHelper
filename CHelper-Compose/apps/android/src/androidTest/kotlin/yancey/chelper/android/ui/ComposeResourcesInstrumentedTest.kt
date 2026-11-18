package yancey.chelper.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import yancey.chelper.resources.Res
import yancey.chelper.resources.app_name
import yancey.chelper.resources.ic_loong_flow_bubble
import yancey.chelper.resources.pack_icon

/** Exercises Android's actual Compose asset reader, rather than desktop classpath resources. */
@RunWith(AndroidJUnit4::class)
class ComposeResourcesInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sharedRasterVectorAndStringResourcesLoadOnAndroid() {
        compose.setContent {
            Column {
                Image(painterResource(Res.drawable.pack_icon), "Raster icon", Modifier.size(48.dp))
                Image(
                    painterResource(Res.drawable.ic_loong_flow_bubble),
                    "Vector icon",
                    Modifier.size(48.dp),
                )
                BasicText(stringResource(Res.string.app_name))
            }
        }
        compose.onNodeWithContentDescription("Raster icon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vector icon").assertIsDisplayed()
        compose.onNodeWithText("CHelper").assertIsDisplayed()
    }
}
