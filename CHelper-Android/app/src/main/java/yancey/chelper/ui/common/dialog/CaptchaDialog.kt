/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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

package yancey.chelper.ui.common.dialog

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hjq.toast.Toaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.CaptchaStatusResponse
import yancey.chelper.network.library.data.CaptchaTokenRequest
import java.util.UUID

@Composable
fun CaptchaDialog(
    action: String,
    onDismissRequest: () -> Unit,
    onSuccess: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CaptchaDialogContent(action, onDismissRequest, onSuccess)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaDialogContent(
    action: String,
    onDismissRequest: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val specialCode = remember { UUID.randomUUID().toString() }
    var webView: WebView? by remember { mutableStateOf(null) }
    // 防止 JS 回调和轮询同时触发导致重复执行
    var isCompleted by remember { mutableStateOf(false) }
    // captchaUrl 由 LaunchedEffect 在拿到 token 后设置，触发 WebView 加载
    var captchaUrl by remember { mutableStateOf<String?>(null) }

    fun handleSuccess(verifiedCode: String? = null) {
        if (isCompleted) return
        isCompleted = true
        onSuccess(verifiedCode ?: specialCode)
        onDismissRequest()
    }

    fun handleFailure(message: String) {
        if (isCompleted) return
        isCompleted = true
        Toaster.show(message)
        onDismissRequest()
    }

    // JS 回调运行在 WebView 线程，需要切回主线程
    val handler = remember { Handler(Looper.getMainLooper()) }

    val jsInterface =
        remember { CaptchaJsInterface(handler, ::handleSuccess, ::handleFailure, onDismissRequest) }

    // 当 captchaUrl 和 webView 都就绪时加载页面
    LaunchedEffect(captchaUrl, webView) {
        val url = captchaUrl ?: return@LaunchedEffect
        val wv = webView ?: return@LaunchedEffect
        wv.loadUrl(url)
    }

    // Token 请求 + 轮询（LaunchedEffect 会在 composable 离开时自动取消，不会抛异常到 catch 里）
    LaunchedEffect(specialCode) {
        try {
            val request = CaptchaTokenRequest().apply {
                this.special_code = specialCode
                this.action = action
            }
            val response = withContext(Dispatchers.IO) {
                ServiceManager.CAPTCHA_SERVICE?.requestToken(request)
            }
            if (response?.isSuccess() == true && response.data?.verification_token != null) {
                val token = response.data!!.verification_token
                val baseUrl = ServiceManager.LAB_BASE_URL.removeSuffix("/")
                captchaUrl = "$baseUrl/captcha/verifing?token=$token&callback=androidCallback"

                // 轮询验证状态作为 JS 回调的后备方案
                withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    while (isActive && !isCompleted) {
                        if (System.currentTimeMillis() - startTime > 300_000) {
                            withContext(Dispatchers.Main) { handleFailure("验证超时") }
                            break
                        }
                        delay(1500)
                        try {
                            val status = ServiceManager.CAPTCHA_SERVICE?.getStatus(specialCode)
                            if (status?.isSuccess() == true && status.data != null) {
                                if (status.data!!.status == CaptchaStatusResponse.STATUS_VERIFIED) {
                                    val serverCode = status.data!!.special_code
                                    withContext(Dispatchers.Main) { handleSuccess(serverCode) }
                                    break
                                } else if (status.data!!.status == CaptchaStatusResponse.STATUS_FAILED) {
                                    withContext(Dispatchers.Main) { handleFailure("验证失败") }
                                    break
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            } else {
                handleFailure(response?.message ?: "获取验证凭证失败")
            }
        } catch (e: CancellationException) {
            // Dialog 被关闭时 LaunchedEffect 正常取消，不做任何处理
            throw e
        } catch (e: Exception) {
            handleFailure("网络错误: ${e.message}")
        }
    }

    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.WHITE)

                val innerWebView = WebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    // 部分验证码渲染在硬件加速下会白屏
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    setBackgroundColor(Color.WHITE)

                    @SuppressLint("JavascriptInterface")
                    addJavascriptInterface(jsInterface, "android")
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(
                                """
                                window.handleCaptcha = function(data) {
                                    if (!data) return;
                                    if (data.status === 'verified' && data.special_code) {
                                        android.onSuccess(data.special_code);
                                    }
                                };
                                
                                // Method 2: URL Callback
                                window.androidCallback = function(data) {
                                    window.handleCaptcha(data);
                                };
                                
                                // Method 1: Window Message
                                window.addEventListener('message', function(event) {
                                    if (event.data && event.data.type === 'captcha_result') {
                                        window.handleCaptcha(event.data);
                                    }
                                });
                            """.trimIndent(), null
                            )
                        }
                    }
                }
                addView(innerWebView)
                webView = innerWebView

                val progressBar = ProgressBar(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                }
                addView(progressBar)
            }
        },
        update = { frameLayout ->
            // 根据 URL 是否加载完来控制 ProgressBar 的可见性
            val progressBar = frameLayout.getChildAt(1)
            progressBar?.visibility = if (captchaUrl != null) View.GONE else View.VISIBLE
        },
        modifier = Modifier.fillMaxSize()
    )
}

private class CaptchaJsInterface(
    private val handler: Handler,
    private val onSuccessCallback: (String?) -> Unit,
    private val onFailureCallback: (String) -> Unit,
    private val onCancelCallback: () -> Unit
) {
    @JavascriptInterface
    fun onSuccess(code: String?) {
        handler.post {
            if (!code.isNullOrEmpty()) {
                onSuccessCallback(code)
            } else {
                onFailureCallback("验证返回为空")
            }
        }
    }

    @JavascriptInterface
    fun onFail() {
        handler.post { onFailureCallback("验证失败") }
    }

    @JavascriptInterface
    fun onCancel() {
        handler.post { onCancelCallback() }
    }
}
