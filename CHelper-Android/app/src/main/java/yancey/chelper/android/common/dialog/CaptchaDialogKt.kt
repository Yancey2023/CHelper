package yancey.chelper.android.common.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.CaptchaStatusResponse
import yancey.chelper.network.library.data.CaptchaTokenRequest
import java.util.UUID

/**
 * 人机验证对话框 (Kotlin版)
 */
class CaptchaDialogKt(
    context: Context,
    private val action: String,
    private val callback: (Result) -> Unit
) : FixedDialog(context) {

    sealed class Result {
        data class Success(val specialCode: String) : Result()
        data class Failure(val message: String) : Result()
        object Cancelled : Result()
    }

    private var webView: WebView? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val specialCode = UUID.randomUUID().toString()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frameLayout = FrameLayout(context)
        frameLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        frameLayout.setBackgroundColor(Color.WHITE)

        // Force full screen
        window?.let {
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            it.setBackgroundDrawableResource(android.R.color.white)
        }

        // WebView
        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            
            // Disable hardware acceleration to prevent rendering glitches
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(Color.WHITE)
            
            addJavascriptInterface(JsInterface(), "android")
            
            webChromeClient = android.webkit.WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript("""
                        window.androidCallback = function(result) {
                            android.onSuccess(result);
                        };
                    """.trimIndent(), null)
                }
                
                override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed() // Ignore SSL errors for internal testing if needed, though use with caution
                }
            }
        }
        frameLayout.addView(webView)

        // Loading
        val progressBar = ProgressBar(context)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER
        progressBar.layoutParams = params
        frameLayout.addView(progressBar)

        setContentView(frameLayout)
        
        // Override FixedDialog width/height to be full screen
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        
        // 开始流程
        startVerification(progressBar)
        
        setOnCancelListener {
            job?.cancel()
            callback(Result.Cancelled)
        }
    }
    
    override fun onStop() {
        super.onStop()
        job?.cancel()
        scope.cancel()
    }

    private fun startVerification(progressBar: ProgressBar) {
        job = scope.launch {
            try {
                // 1. 请求 Token
                val request = CaptchaTokenRequest().apply {
                    this.special_code = specialCode
                    this.action = this@CaptchaDialogKt.action
                }
                
                val response = withContext(Dispatchers.IO) {
                    ServiceManager.CAPTCHA_SERVICE?.requestToken(request)
                }
                
                if (response?.isSuccess() == true && response.data?.verification_token != null) {
                    val token = response.data!!.verification_token
                    val baseUrl = ServiceManager.LAB_BASE_URL.removeSuffix("/")
                    // 2. 加载页面
                    webView?.loadUrl("$baseUrl/captcha/verifing?token=$token")
                    progressBar.visibility = android.view.View.GONE
                    
                    // 3. 轮询状态
                    pollStatus()
                } else {
                    handleFailure(response?.message ?: "获取验证凭证失败")
                }
            } catch (e: Exception) {
                handleFailure("网络错误: ${e.message}")
            }
        }
    }

    private suspend fun pollStatus() {
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                if (System.currentTimeMillis() - startTime > 300_000) {
                    withContext(Dispatchers.Main) { handleFailure("验证超时") }
                    break
                }
                
                delay(1500)
                
                try {
                    val status = ServiceManager.CAPTCHA_SERVICE?.getStatus(specialCode)
                    if (status?.isSuccess() == true && status.data != null) {
                        if (status.data!!.status == CaptchaStatusResponse.STATUS_VERIFIED) {
                            withContext(Dispatchers.Main) { handleSuccess() }
                            break
                        } else if (status.data!!.status == CaptchaStatusResponse.STATUS_FAILED) {
                             withContext(Dispatchers.Main) { handleFailure("验证失败") }
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleSuccess() {
        if (!isShowing) return
        callback(Result.Success(specialCode))
        dismiss()
    }

    private fun handleFailure(message: String) {
        if (!isShowing) return
        callback(Result.Failure(message))
        dismiss()
    }

    private inner class JsInterface {
        @JavascriptInterface
        fun onSuccess(code: String?) {
            scope.launch(Dispatchers.Main) {
                if (!code.isNullOrEmpty()) {
                    handleSuccess()
                } else {
                    handleFailure("验证返回为空")
                }
            }
        }

        @JavascriptInterface
        fun onFail() {
            scope.launch(Dispatchers.Main) {
                handleFailure("验证失败")
            }
        }

        @JavascriptInterface
        fun onCancel() {
            scope.launch(Dispatchers.Main) {
                callback(Result.Cancelled)
                dismiss()
            }
        }
    }
}
