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

package yancey.chelper.android.common.activity

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.toast.Toaster
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import yancey.chelper.android.common.util.CustomTheme
import yancey.chelper.android.common.util.MonitorUtil
import yancey.chelper.android.common.util.Settings
import yancey.chelper.android.window.FloatingWindowManager
import yancey.chelper.ui.NavHost
import yancey.chelper.ui.common.CHelperTheme
import java.io.BufferedInputStream
import java.io.IOException

abstract class BaseComposeActivity : ComponentActivity() {

    private var backgroundUpdateTimes = 0
    protected var backgroundBitmap by mutableStateOf<ImageBitmap?>(null)
    protected var theme by mutableStateOf(CHelperTheme.Theme.Light)
    protected var isSystemDarkMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isSystemDarkMode =
            (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        refreshTheme()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(
                Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                Color.argb(0x80, 0x1b, 0x1b, 0x1b)
            ) {
                theme == CHelperTheme.Theme.Dark
            }
        )
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val updateTimes = CustomTheme.INSTANCE.backgroundBitmap.updateTimes
        if (backgroundUpdateTimes != updateTimes) {
            backgroundUpdateTimes = updateTimes
            backgroundBitmap = CustomTheme.INSTANCE.backgroundBitmap.imageBitmap
        }
        refreshTheme()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshTheme()
    }

    protected fun refreshTheme() {
        val isDarkBefore =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        theme = when (Settings.INSTANCE.themeId) {
            "MODE_NIGHT_NO" -> CHelperTheme.Theme.Light
            "MODE_NIGHT_YES" -> CHelperTheme.Theme.Dark
            else -> if (isSystemDarkMode) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
        }
        val isDarkMode = theme == CHelperTheme.Theme.Dark
        if (isDarkBefore != isDarkMode) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode
                isAppearanceLightNavigationBars = !isDarkMode
            }
            resources.configuration.uiMode =
                if (isDarkMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }

    protected fun setContent(parent: CompositionContext? = null, content: @Composable () -> Unit) {
        val existingComposeView =
            window.decorView.findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as? ComposeView

        if (existingComposeView != null) {
            with(existingComposeView) {
                setParentCompositionContext(parent)
                setContent(content)
            }
        } else {
            ComposeView(this).apply {
                setParentCompositionContext(parent)
                setContent {
                    CHelperTheme(theme, backgroundBitmap) {
                        content()
                    }
                }
                setOwners()
                setContentView(this, DefaultActivityContentLayoutParams)
            }
        }
    }

}

private val DefaultActivityContentLayoutParams =
    ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

private fun ComponentActivity.setOwners() {
    val decorView = window.decorView
    if (decorView.findViewTreeLifecycleOwner() == null) {
        decorView.setViewTreeLifecycleOwner(this)
    }
    if (decorView.findViewTreeViewModelStoreOwner() == null) {
        decorView.setViewTreeViewModelStoreOwner(this)
    }
    if (decorView.findViewTreeSavedStateRegistryOwner() == null) {
        decorView.setViewTreeSavedStateRegistryOwner(this)
    }
}
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
/**
 * 首页
 */
class HomeActivity : BaseComposeActivity() {
    private lateinit var floatingWindowManager: FloatingWindowManager
    private var setBackGroundDrawable: Disposable? = null
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var photoPicker: ActivityResultLauncher<PickVisualMediaRequest>
    private var isShowSavingBackgroundDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        floatingWindowManager = FloatingWindowManager(application)
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (setBackGroundDrawable != null && !setBackGroundDrawable!!.isDisposed) {
                    isShowSavingBackgroundDialog.value = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        photoPicker =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { setBackground(it) }
        setContent {
            NavHost(
                navController = rememberNavController(),
                floatingWindowManager = floatingWindowManager,
                chooseBackground = this::chooseBackground,
                restoreBackground = this::restoreBackground,
                isShowSavingBackgroundDialog = isShowSavingBackgroundDialog,
                onChooseTheme = this::refreshTheme,
                shutdown = this::finishAffinity,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingWindowManager.stopFloatingWindow()
    }

    private fun chooseBackground() {
        if (XXPermissions.isGrantedPermission(
                this,
                PermissionLists.getReadMediaImagesPermission()
            )
        ) {
            photoPicker.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        } else {
            XXPermissions.with(this)
                .permission(PermissionLists.getReadMediaImagesPermission())
                .request { _, deniedList ->
                    if (deniedList.isEmpty()) {
                        Toaster.show("图片访问权限申请成功")
                    } else {
                        Toaster.show("图片访问权限申请失败")
                    }
                }
        }
    }

    private fun setBackground(uri: Uri?) {
        setBackGroundDrawable?.dispose()
        if (uri == null) {
            return
        }
        setBackGroundDrawable =
            Observable.create { emitter ->
                val bitmap: Bitmap
                BufferedInputStream(contentResolver.openInputStream(uri))
                    .use { bitmap = BitmapFactory.decodeStream(it) }
                CustomTheme.INSTANCE.setBackGroundDrawableWithoutSave(bitmap)
                emitter.onNext(bitmap)
                CustomTheme.INSTANCE.setBackGroundDrawable(bitmap)
                emitter.onComplete()
            }.subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally {
                    onBackPressedCallback.isEnabled = false
                    isShowSavingBackgroundDialog.value = false
                }
                .subscribe(
                    { backgroundBitmap = it.asImageBitmap() },
                    { Toaster.show(it.message) }
                )
        onBackPressedCallback.isEnabled = true
    }

    private fun restoreBackground() {
        try {
            CustomTheme.INSTANCE.setBackGroundDrawable(null)
            backgroundBitmap = null
        } catch (e: IOException) {
            Toaster.show(e.message)
            MonitorUtil.generateCustomLog(e, "ResetBackgroundException")
        }
    }

}