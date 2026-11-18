package yancey.chelper.android

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPermissionsInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test
    fun photoPickerDoesNotRequireBroadMediaPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requested =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .toSet()
        for (permission in
            listOf(
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            )) {
            assertFalse("Unexpected broad media permission: $permission", permission in requested)
        }
    }
}
