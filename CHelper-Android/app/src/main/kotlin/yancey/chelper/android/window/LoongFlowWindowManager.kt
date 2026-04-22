/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * LoongFlow（游龙）独立悬浮窗管理器。
 * 管理两个 EasyWindow 实例：米窗面板 + 迷你气泡。
 * 完全独立于基础悬浮窗 FloatingWindowManager 运行。
 */

package yancey.chelper.android.window

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.hjq.window.EasyWindow
import com.hjq.window.draggable.MovingWindowDraggableRule
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.loongflow.LoongFlowMode
import yancey.chelper.ui.loongflow.LoongFlowPanel
import yancey.chelper.ui.loongflow.LoongFlowViewModel

/**
 * 游龙悬浮窗管理器。
 *
 * 核心差异于 FloatingWindowManager：
 * - 窗口为非全屏的米窗样式浮动面板（屏幕 80%×65%），而非全屏覆盖
 * - 支持拖拽到屏幕边缘收缩为迷你气泡
 * - 不走 Navigation 路由，使用内部状态机控制 step 流转
 * - 可获焦点（导出模式需要粘贴输入）
 */
class LoongFlowWindowManager(
    private val application: Application,
) {
    companion object {
        /** Application 级别单例，在 CHelperApplication.onCreate 中初始化 */
        lateinit var INSTANCE: LoongFlowWindowManager
            private set

        fun init(application: Application) {
            INSTANCE = LoongFlowWindowManager(application)
        }

        /** 面板最小尺寸 (dp) */
        private const val MIN_WIDTH_DP = 220
        private const val MIN_HEIGHT_DP = 280
    }

    private var panelWindow: EasyWindow<*>? = null
    private var bubbleWindow: EasyWindow<*>? = null
    private var composeLifecycleOwner: ComposeLifecycleOwner? = null
    private var viewModel: LoongFlowViewModel? = null
    private var theme by mutableStateOf(CHelperTheme.Theme.Light)

    val isShowing: Boolean
        get() = panelWindow != null || bubbleWindow != null

    /**
     * 以导入模式启动游龙面板。
     * 从 PublicLibraryShowScreen 菜单触发，携带当前查看的命令库数据。
     */
    fun showImport(context: Context, library: LibraryFunction) {
        // 如果已经在显示，先关掉旧的
        if (isShowing) dismiss()

        val vm = LoongFlowViewModel(application)
        vm.initImport(library)
        viewModel = vm

        showPanel(context, LoongFlowMode.IMPORT, library)
    }

    /**
     * 以导出模式启动游龙面板。
     * 从 HomeScreen / 悬浮窗菜单触发，不依赖任何现有库数据。
     */
    fun showExport(context: Context) {
        if (isShowing) dismiss()

        val vm = LoongFlowViewModel(application)
        vm.initExport()
        viewModel = vm

        showPanel(context, LoongFlowMode.EXPORT, null)
    }

    /**
     * 面板 → 气泡：收缩为迷你气泡。
     * 面板隐藏但不销毁（保留 ViewModel 状态），气泡显示在屏幕右侧边缘。
     */
    fun minimizeToBubble() {
        panelWindow?.let { panel ->
            composeLifecycleOwner?.onPause()
            panel.windowViewVisibility = View.INVISIBLE
        }
        showBubble()
    }

    /**
     * 气泡 → 面板：从气泡展开回面板。
     */
    fun expandFromBubble() {
        hideBubble()
        panelWindow?.let { panel ->
            composeLifecycleOwner?.onResume()
            panel.windowViewVisibility = View.VISIBLE
        }
    }

    /**
     * 彻底关闭游龙所有窗口并释放资源。
     */
    fun dismiss() {
        hideBubble()

        panelWindow?.let { panel ->
            composeLifecycleOwner?.apply {
                onStop()
                onDestroy()
                detachFromDecorView(panel.contentView)
            }
            composeLifecycleOwner = null
            panel.recycle()
            panelWindow = null
        }

        viewModel = null
    }

    private var isCompactMode = false

    /**
     * 切换小窗流尺寸（正常/精简）。
     * 为了防止缩小时底层 WindowManager 与 Compose 视图测量脱节导致裁切问题，
     * 此处采用【保存 ViewModel 状态 -> 销毁旧窗口 -> 按新参数重建新窗口】的方式。
     */
    fun toggleSize() {
        val vm = viewModel ?: return
        val currentMode = vm.mode
        isCompactMode = !isCompactMode

        // 记录此时的位置，方便原地重建（可选，这里演示还是居中，或按原坐标？）
        val oldParams = panelWindow?.windowParams
        val oldX = oldParams?.x ?: 0
        val oldY = oldParams?.y ?: 0

        // 彻底回收旧的 Panel，但【不要清空 viewModel】
        panelWindow?.let { panel ->
            composeLifecycleOwner?.apply {
                onStop()
                onDestroy()
                detachFromDecorView(panel.contentView)
            }
            composeLifecycleOwner = null
            panel.recycle()
            panelWindow = null
        }

        // 以新尺寸重新创建
        showPanel(application, currentMode, null)

        // 恢复旧的位置
        panelWindow?.windowParams?.x = oldX
        panelWindow?.windowParams?.y = oldY
        panelWindow?.update()
    }

    /**
     * 根据拖拽增量移动面板位置。
     */
    fun movePanel(deltaX: Float, deltaY: Float) {
        val panel = panelWindow ?: return
        val params = panel.windowParams
        params.x += deltaX.toInt()
        params.y += deltaY.toInt()
        panel.update()
    }

    /**
     * 拖拽结束时检测面板是否靠近屏幕边缘，如果是则触发最小化。
     */
    fun checkEdgeMinimize() {
        val params = panelWindow?.windowParams ?: return
        val sw = application.resources.displayMetrics.widthPixels
        // Gravity.CENTER 意味着 params.x 是偏离中心的偏移量
        // 如果左右偏移超过屏幕宽度的 35%，则触发最小化
        if (Math.abs(params.x) > sw * 0.35) {
            minimizeToBubble()
        }
    }

    // ─────────────────────────────────────────
    //  内部实现
    // ─────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun showPanel(context: Context, mode: LoongFlowMode, library: LibraryFunction?) {
        val metrics = application.resources.displayMetrics
        val isLandscape = metrics.widthPixels > metrics.heightPixels
        
        // 初始默认尺寸
        val panelWidth: Int
        val panelHeight: Int
        if (isLandscape) {
            panelWidth = if (isCompactMode) (metrics.widthPixels * 0.40).toInt() else (metrics.widthPixels * 0.70).toInt()
            panelHeight = (metrics.heightPixels * 0.35).toInt()
        } else {
            panelWidth = (metrics.widthPixels * 0.80).toInt()
            panelHeight = if (isCompactMode) (metrics.heightPixels * 0.35).toInt() else (metrics.heightPixels * 0.65).toInt()
        }

        val vm = viewModel ?: return

        val panelView = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: android.view.KeyEvent?): Boolean {
                // 如果按下了返回键，关闭游龙面板
                if (event?.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val composeView = ComposeView(context).apply {
            setContent {
                CHelperTheme(theme, backgroundBitmap = null) {
                    LoongFlowPanel(
                        mode = mode,
                        library = library,
                        viewModel = vm,
                        onMinimize = { minimizeToBubble() },
                        onDismiss = { dismiss() },
                        onToggleSize = { toggleSize() },
                        onMove = { dx, dy -> movePanel(dx, dy) },
                        onDragEnd = { checkEdgeMinimize() }
                    )
                }
            }
        }
        panelView.addView(composeView)

        panelWindow = EasyWindow.with(application)
            .setContentView(panelView)
            .setWindowSize(panelWidth, panelHeight)
            .setWindowLocation(Gravity.CENTER, 0, 0)
            .setOutsideTouchable(true)
            .removeWindowFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            .addWindowFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            .setWindowAnim(0)

        val settingsDataStore = SettingsDataStore(context)
        composeLifecycleOwner = ComposeLifecycleOwner().apply {
            attachToDecorView(panelWindow!!.rootLayout)
            onCreate()
            onStart()

            val isSystemDarkMode =
                (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            lifecycleScope.launch {
                settingsDataStore.themeId().collect {
                    theme = when (it) {
                        "MODE_NIGHT_NO" -> CHelperTheme.Theme.Light
                        "MODE_NIGHT_YES" -> CHelperTheme.Theme.Dark
                        else -> if (isSystemDarkMode) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
                    }
                }
            }
        }

        panelWindow?.show()
        panelView.requestFocus()
    }

    private fun showBubble() {
        if (bubbleWindow != null) return

        val metrics = application.resources.displayMetrics
        val bubbleSize = (48 * metrics.density).toInt()

        val bubbleView = ImageView(application).apply {
            setImageResource(R.drawable.ic_loong_flow_bubble)
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize)
        }

        bubbleWindow = EasyWindow.with(application)
            .setContentView(bubbleView)
            .setWindowDraggableRule(MovingWindowDraggableRule())
            .setOutsideTouchable(true)
            .setWindowLocation(
                Gravity.END or Gravity.CENTER_VERTICAL,
                0, 0
            )
            .setWindowAnim(0)
            .setWindowAlpha(0.85f)

        bubbleView.setOnClickListener { expandFromBubble() }
        bubbleView.setOnLongClickListener {
            dismiss()
            true
        }

        bubbleWindow?.show()
    }

    private fun hideBubble() {
        bubbleWindow?.let {
            it.recycle()
            bubbleWindow = null
        }
    }
}
