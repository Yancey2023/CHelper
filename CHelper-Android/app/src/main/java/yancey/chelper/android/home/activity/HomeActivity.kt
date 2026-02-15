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

package yancey.chelper.android.home.activity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.navigation.compose.rememberNavController
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.toast.Toaster
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import yancey.chelper.android.common.activity.BaseComposeActivity
import yancey.chelper.android.common.style.CustomTheme
import yancey.chelper.android.common.util.MonitorUtil
import yancey.chelper.android.completion.util.CompletionWindowManager
import yancey.chelper.ui.NavHost
import java.io.BufferedInputStream
import java.io.IOException

/**
 * 首页
 */
class HomeActivity : BaseComposeActivity() {
    private var setBackGroundDrawable: Disposable? = null
    lateinit var onBackPressedCallback: OnBackPressedCallback
    lateinit var photoPicker: ActivityResultLauncher<PickVisualMediaRequest>
    private var isShowSavingBackgroundDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (setBackGroundDrawable != null && !setBackGroundDrawable!!.isDisposed) {
                    isShowSavingBackgroundDialog.value = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        photoPicker = registerForActivityResult(PickVisualMedia()) { setBackground(it) }
        setContent {
            NavHost(
                navController = rememberNavController(),
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
        CompletionWindowManager.INSTANCE!!.stopFloatingWindow()
    }

    private fun chooseBackground() {
        if (XXPermissions.isGrantedPermission(
                this,
                PermissionLists.getReadMediaImagesPermission()
            )
        ) {
            photoPicker.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ImageOnly)
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
