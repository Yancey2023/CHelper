package yancey.chelper.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import yancey.chelper.android.activity.HomeActivity
import yancey.chelper.android.service.Old2NewIMEService

/** Android attaches a base Context after construction; storage must not be accessed earlier. */
@RunWith(AndroidJUnit4::class)
class ComponentStorageInitializationInstrumentedTest {
    @Test
    fun activityAndImeCanBeConstructedBeforeContextAttachment() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            HomeActivity()
            Old2NewIMEService()
        }
    }
}
