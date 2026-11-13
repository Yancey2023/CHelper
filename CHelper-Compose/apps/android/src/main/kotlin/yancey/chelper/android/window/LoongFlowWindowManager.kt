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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hjq.window.EasyWindow
import com.hjq.window.draggable.MovingWindowDraggableRule
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.android.startupState
import yancey.chelper.android.storage.clientDataStores
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.loongflow.LoongFlowMiniPanel
import yancey.chelper.ui.feature.loongflow.LoongFlowMode
import yancey.chelper.ui.feature.loongflow.LoongFlowPanel
import yancey.chelper.ui.feature.loongflow.LoongFlowViewModel
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.ClientMessages

/** 游龙悬浮窗管理器 */
class LoongFlowWindowManager(private val application: Application) {
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
    private var bubbleLifecycleOwner: ComposeLifecycleOwner? = null
    private var viewModel: LoongFlowViewModel? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDismissPending = false
    private var isToggleSizePending = false
    private var isExpandPending = false
    private val dismissTask = Runnable {
        isDismissPending = false
        dismissImmediately()
    }
    private val toggleSizeTask = Runnable {
        isToggleSizePending = false
        toggleSizeImmediately()
    }
    private val expandTask = Runnable {
        isExpandPending = false
        if (!isDismissPending) expandImmediately()
    }
    private var isEnableImportMiniIcon by mutableStateOf(true)
    private var isPanelInputActive = false

    val isShowing: Boolean
        get() = panelWindow != null || bubbleWindow != null

    /** 以导入模式启动游龙面板。 从 PublicLibraryShowScreen 菜单触发，携带当前查看的命令库数据 */
    fun showImport(context: Context, library: LibraryFunction): Boolean {
        return runCatching {
                dismissImmediately()

                val vm = createViewModel()
                vm.initImport(library)
                viewModel = vm

                showPanel(context, LoongFlowMode.IMPORT, library)
                true
            }
            .getOrElse {
                dismissImmediately()
                false
            }
    }

    /** 以导出模式启动游龙面板 从 HomeScreen / 悬浮窗菜单触发，不依赖任何现有库数据 */
    fun showExport(context: Context) {
        dismissImmediately()

        val vm = createViewModel()
        vm.initExport()
        viewModel = vm

        showPanel(context, LoongFlowMode.EXPORT, null)
    }

    private fun createViewModel(): LoongFlowViewModel {
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return LoongFlowViewModel(
            copyText = { clipboard.setPrimaryClip(ClipData.newPlainText("CHelper", it)) },
            readClipboard = {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0)
                    clip.getItemAt(0).coerceToText(application)?.toString()
                else null
            },
            shareText = { text ->
                application.startActivity(
                    Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            "分享 MCD",
                        )
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            },
        )
    }

    /** 面板 → 气泡：收缩为迷你气泡 面板隐藏但不销毁（保留 ViewModel 状态），气泡显示在屏幕右侧边缘 */
    fun minimizeToBubble() {
        val panel = panelWindow ?: return
        showBubble()
        setPanelInputActive(false, hideKeyboard = true)
        panel.contentView?.clearFocus()
        pausePanelLifecycle()
        panel.windowViewVisibility = View.INVISIBLE
    }

    /** 气泡 → 面板：从气泡展开回面板 */
    fun expandFromBubble() {
        if (isDismissPending || isExpandPending) return
        if (panelWindow == null) {
            dismiss()
            return
        }
        if (bubbleWindow == null) return

        isExpandPending = true
        mainHandler.post(expandTask)
    }

    /** 彻底关闭游龙所有窗口并释放资源 */
    fun dismiss() {
        if (isDismissPending) return
        if (!isShowing && viewModel == null && composeLifecycleOwner == null) return

        cancelPendingSizeAndExpandTasks()
        isDismissPending = true
        // EasyWindow 使用 removeViewImmediate，不能在当前 View / Compose 点击回调里同步回收自身。
        mainHandler.post(dismissTask)
    }

    private var isCompactMode = false

    /**
     * 切换小窗流尺寸（正常/精简） 为了防止缩小时底层 WindowManager 与 Compose 视图测量脱节导致裁切问题 此处保存 ViewModel
     * 状态，销毁旧窗口，按新参数重建新窗口
     */
    fun toggleSize() {
        if (isDismissPending || isToggleSizePending || panelWindow == null) return

        isToggleSizePending = true
        // 重建会销毁当前 Compose 树，等点击事件分发完成后再执行。
        mainHandler.post(toggleSizeTask)
    }

    private fun toggleSizeImmediately() {
        val vm = viewModel ?: return
        val panel = panelWindow ?: return
        val owner = composeLifecycleOwner
        val currentMode = vm.mode
        isCompactMode = !isCompactMode

        // 记录此时的位置，方便原地重建
        val oldX = panel.windowParams.x
        val oldY = panel.windowParams.y

        // 回收旧的 Panel，但不清空 viewModel
        panelWindow = null
        composeLifecycleOwner = null
        isPanelInputActive = false
        panel.contentView?.clearFocus()
        hidePanelKeyboard(panel.contentView)
        destroyPanelLifecycle(panel, owner)
        panel.recycle()

        if (viewModel !== vm || isDismissPending) return

        // 以新尺寸重新创建
        showPanel(application, currentMode, null)

        // 恢复旧的位置
        panelWindow?.windowParams?.x = oldX
        panelWindow?.windowParams?.y = oldY
        panelWindow?.update()
    }

    private fun expandImmediately() {
        val panel =
            panelWindow
                ?: run {
                    dismissImmediately()
                    return
                }
        if (bubbleWindow == null) return

        panel.windowViewVisibility = View.VISIBLE
        resumePanelLifecycle()
        hideBubbleImmediately()
    }

    private fun dismissImmediately() {
        mainHandler.removeCallbacks(dismissTask)
        cancelPendingSizeAndExpandTasks()
        isDismissPending = false

        val bubble = bubbleWindow
        val bubbleOwner = bubbleLifecycleOwner
        bubbleLifecycleOwner = null
        val panel = panelWindow
        val owner = composeLifecycleOwner

        // 先断开全局引用，销毁期间产生的补发回调就只能安全返回。
        bubbleWindow = null
        panelWindow = null
        composeLifecycleOwner = null
        viewModel = null
        isPanelInputActive = false

        bubble?.contentView?.apply {
            setOnClickListener(null)
            setOnLongClickListener(null)
        }
        bubble?.let {
            destroyPanelLifecycle(it, bubbleOwner)
            it.recycle()
        }

        panel?.let {
            it.contentView?.clearFocus()
            hidePanelKeyboard(it.contentView)
            destroyPanelLifecycle(it, owner)
            it.recycle()
        }
    }

    private fun cancelPendingSizeAndExpandTasks() {
        mainHandler.removeCallbacks(toggleSizeTask)
        mainHandler.removeCallbacks(expandTask)
        isToggleSizePending = false
        isExpandPending = false
    }

    /** 根据拖拽增量移动面板位置 */
    fun movePanel(deltaX: Float, deltaY: Float) {
        val panel = panelWindow ?: return
        val params = panel.windowParams
        params.x += deltaX.toInt()
        params.y += deltaY.toInt()
        panel.update()
    }

    /** 拖拽结束时检测面板是否靠近屏幕边缘，如果是则触发最小化 */
    fun checkEdgeMinimize() {
        val params = panelWindow?.windowParams ?: return
        val sw = currentScreenMetrics().widthPx
        // Gravity.CENTER 意味着 params.x 是偏离中心的偏移量
        // 如果左右偏移超过屏幕宽度的 35%，则触发最小化
        if (Math.abs(params.x) > sw * 0.35) {
            minimizeToBubble()
        }
    }

    //  内部实现

    @Suppress("DEPRECATION")
    private fun showPanel(context: Context, mode: LoongFlowMode, library: LibraryFunction?) {
        val settingsDataStore = context.clientDataStores().settings
        val metrics = currentScreenMetrics()
        val isLandscape = metrics.widthPx > metrics.heightPx

        // 初始默认尺寸
        val panelWidth: Int
        val panelHeight: Int
        if (isLandscape) {
            panelWidth =
                if (isCompactMode) (metrics.widthPx * 0.40).toInt()
                else (metrics.widthPx * 0.70).toInt()
            panelHeight = (metrics.heightPx * 0.35).toInt()
        } else {
            panelWidth = (metrics.widthPx * 0.80).toInt()
            panelHeight =
                if (isCompactMode) (metrics.heightPx * 0.35).toInt()
                else (metrics.heightPx * 0.65).toInt()
        }

        val vm = viewModel ?: return

        val panelView =
            object : FrameLayout(context) {
                    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                        // 如果按下了返回键，关闭游龙面板
                        if (
                            event?.keyCode == KeyEvent.KEYCODE_BACK &&
                                event.action == KeyEvent.ACTION_UP
                        ) {
                            if (isPanelInputActive) {
                                setPanelInputActive(false, hideKeyboard = true)
                                return true
                            }
                            composeLifecycleOwner?.back()
                            return true
                        }
                        return super.dispatchKeyEvent(event)
                    }

                    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                        if (ev?.action == MotionEvent.ACTION_OUTSIDE) {
                            setPanelInputActive(false, hideKeyboard = false)
                        }
                        return super.dispatchTouchEvent(ev)
                    }
                }
                .apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                }

        val composeView =
            ComposeView(context).apply {
                setContent {
                    val startupSettings by
                        context.startupState.settings.collectAsStateWithLifecycle()
                    val ready = startupSettings ?: return@setContent
                    val initialTheme =
                        CHelperTheme.themeOf(
                            ready.themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM",
                            isSystemDarkMode(),
                        )
                    CHelperTheme(initialTheme, backgroundBitmap = null) {
                        ClientInteractionHost {
                            LoongFlowPanel(
                                mode = mode,
                                library = library,
                                viewModel = vm,
                                isEnableImportMiniIcon = isEnableImportMiniIcon,
                                onMinimize = { minimizeToBubble() },
                                onDismiss = { dismiss() },
                                onToggleSize = { toggleSize() },
                                onRequestInputFocus = {
                                    setPanelInputActive(true, hideKeyboard = false)
                                },
                                onMove = { dx, dy -> movePanel(dx, dy) },
                                onDragEnd = { checkEdgeMinimize() },
                                onMessage = ClientMessages::show,
                            )
                        }
                    }
                }
            }
        panelView.addView(composeView)

        panelWindow =
            EasyWindow.with(application)
                .setContentView(panelView)
                .setWindowSize(panelWidth, panelHeight)
                .setWindowLocation(Gravity.CENTER, 0, 0)
                .setOutsideTouchable(true)
                .removeWindowFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                .addWindowFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                )
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                .setWindowAnim(0)

        composeLifecycleOwner =
            ComposeLifecycleOwner().apply {
                attachToDecorView(panelWindow!!.rootLayout)
                onCreate()

                lifecycleScope.launch {
                    settingsDataStore.isEnableLoongFlowImportMiniIcon().collect {
                        isEnableImportMiniIcon = it
                        vm.importMiniIconEnabled = it
                    }
                }
            }

        panelWindow?.show()
        resumePanelLifecycle()
    }

    private fun setPanelInputActive(active: Boolean, hideKeyboard: Boolean) {
        val panel = panelWindow ?: return
        if (isPanelInputActive == active) {
            if (!active && hideKeyboard) hidePanelKeyboard(panel.contentView)
            return
        }

        val params = panel.windowParams
        var flags = params.flags
        flags =
            if (active) {
                flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                flags = flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
            }
        params.flags = flags
        panel.update()
        isPanelInputActive = active

        if (!active) {
            panel.contentView?.clearFocus()
            if (hideKeyboard) hidePanelKeyboard(panel.contentView)
        }
    }

    private fun hidePanelKeyboard(view: View?) {
        val imm = application.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
    }

    private fun showBubble() {
        if (bubbleWindow != null) return

        val metrics = currentScreenMetrics()
        val density = metrics.density
        val isImportMini = shouldShowImportMiniBubble()
        val bubbleWidth: Int
        val bubbleHeight: Int
        val bubbleView: View

        if (isImportMini) {
            val maxAllowedWidth =
                (metrics.widthPx - (12 * density).toInt()).coerceAtLeast((160 * density).toInt())
            val minDesiredWidth = (236 * density).toInt().coerceAtMost(maxAllowedWidth)
            bubbleWidth =
                (318 * density)
                    .toInt()
                    .coerceAtMost((metrics.widthPx * 0.78f).toInt())
                    .coerceAtMost(maxAllowedWidth)
                    .coerceAtLeast(minDesiredWidth)
            bubbleHeight = (88 * density).toInt()
            bubbleView = createImportMiniBubbleView(bubbleWidth, bubbleHeight)
        } else {
            val bubbleSize = (48 * density).toInt()
            bubbleWidth = bubbleSize
            bubbleHeight = bubbleSize
            bubbleView =
                ImageView(application).apply {
                    setImageResource(R.drawable.ic_loong_flow_bubble)
                    layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize)
                }
        }

        bubbleWindow =
            EasyWindow.with(application)
                .setContentView(bubbleView)
                .setWindowSize(bubbleWidth, bubbleHeight)
                .setWindowDraggableRule(MovingWindowDraggableRule())
                .setOutsideTouchable(true)
                .setWindowLocation(Gravity.END or Gravity.CENTER_VERTICAL, 0, 0)
                .setWindowAnim(0)
                .setWindowAlpha(if (isImportMini) 0.96f else 0.85f)

        if (isImportMini) {
            bubbleLifecycleOwner =
                ComposeLifecycleOwner().apply {
                    attachToDecorView(bubbleWindow!!.rootLayout)
                    onCreate()
                }
        } else {
            bubbleView.setOnClickListener { expandFromBubble() }
            bubbleView.setOnLongClickListener {
                dismiss()
                true
            }
        }

        bubbleWindow?.show()
        bubbleLifecycleOwner?.show()
    }

    private fun hideBubbleImmediately() {
        val bubble = bubbleWindow ?: return
        val owner = bubbleLifecycleOwner
        bubbleLifecycleOwner = null
        bubbleWindow = null
        bubble.contentView?.apply {
            setOnClickListener(null)
            setOnLongClickListener(null)
        }
        destroyPanelLifecycle(bubble, owner)
        bubble.recycle()
    }

    private fun pausePanelLifecycle() {
        composeLifecycleOwner?.hide()
    }

    private fun resumePanelLifecycle() {
        composeLifecycleOwner?.show()
    }

    private fun destroyPanelLifecycle(panel: EasyWindow<*>, owner: ComposeLifecycleOwner?) {
        owner ?: return
        owner.onDestroy()
        owner.detachFromDecorView(panel.rootLayout)
    }

    private fun shouldShowImportMiniBubble(): Boolean {
        val vm = viewModel ?: return false
        return vm.mode == LoongFlowMode.IMPORT &&
            vm.importStep == 1 &&
            isEnableImportMiniIcon &&
            vm.importMiniIconEnabled
    }

    private fun createImportMiniBubbleView(width: Int, height: Int): ComposeView =
        ComposeView(application).apply {
            layoutParams = FrameLayout.LayoutParams(width, height)
            val vm = checkNotNull(viewModel)
            setContent {
                val settings by application.startupState.settings.collectAsStateWithLifecycle()
                CHelperTheme(
                    CHelperTheme.themeOf(
                        settings?.themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM",
                        isSystemDarkMode(),
                    ),
                    backgroundBitmap = null,
                ) {
                    ClientInteractionHost {
                        LoongFlowMiniPanel(
                            model = vm,
                            onExpand = ::expandFromBubble,
                            onDismiss = ::dismiss,
                            onMessage = ClientMessages::show,
                        )
                    }
                }
            }
        }

    private fun isSystemDarkMode(): Boolean =
        (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    @Suppress("DEPRECATION")
    private fun currentScreenMetrics(): LoongFlowScreenMetrics {
        val resourceMetrics = application.resources.displayMetrics
        val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return LoongFlowScreenMetrics(
                    widthPx = bounds.width(),
                    heightPx = bounds.height(),
                    density = resourceMetrics.density,
                )
            }
        }

        val realMetrics = DisplayMetrics()
        runCatching { windowManager.defaultDisplay.getRealMetrics(realMetrics) }
        if (realMetrics.widthPixels > 0 && realMetrics.heightPixels > 0) {
            return LoongFlowScreenMetrics(
                widthPx = realMetrics.widthPixels,
                heightPx = realMetrics.heightPixels,
                density = realMetrics.density.takeIf { it > 0f } ?: resourceMetrics.density,
            )
        }

        return LoongFlowScreenMetrics(
            widthPx = resourceMetrics.widthPixels,
            heightPx = resourceMetrics.heightPixels,
            density = resourceMetrics.density,
        )
    }

    private data class LoongFlowScreenMetrics(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
    )
}
