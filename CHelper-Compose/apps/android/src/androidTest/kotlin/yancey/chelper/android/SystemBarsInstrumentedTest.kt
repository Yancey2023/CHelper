package yancey.chelper.android

import android.content.Context
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import yancey.chelper.android.activity.HomeActivity
import yancey.chelper.android.storage.clientDataStores

@RunWith(AndroidJUnit4::class)
class SystemBarsInstrumentedTest {
    @Test
    fun unchangedDarkThemeReappliesBothSystemBarAppearances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = context.clientDataStores().settings
        val originalTheme = settings.themeId().first()
        val originalCrowded = settings.isCrowded().first()
        try {
            settings.setThemeId("MODE_NIGHT_YES")
            context.startupState.settings.first { it?.themeId == "MODE_NIGHT_YES" }
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                        isAppearanceLightStatusBars = true
                        isAppearanceLightNavigationBars = true
                    }
                }
                settings.setIsCrowded(!originalCrowded)
                withTimeout(5000) {
                    while (true) {
                        var correct = false
                        scenario.onActivity { activity ->
                            val bars =
                                WindowInsetsControllerCompat(
                                    activity.window,
                                    activity.window.decorView,
                                )
                            correct =
                                !bars.isAppearanceLightStatusBars &&
                                    !bars.isAppearanceLightNavigationBars
                        }
                        if (correct) break
                        delay(20)
                    }
                }
            }
        } finally {
            settings.setThemeId(originalTheme)
            settings.setIsCrowded(originalCrowded)
        }
    }
}
