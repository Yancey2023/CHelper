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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.hjq.window.EasyWindow
import com.hjq.window.draggable.MovingWindowDraggableRule
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.library.mcd.BlockType
import yancey.chelper.ui.loongflow.LoongFlowMode
import yancey.chelper.ui.loongflow.LoongFlowPanel
import yancey.chelper.ui.loongflow.LoongFlowViewModel

/**
 * 游龙悬浮窗管理器
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
    private var isEnableImportMiniIcon by mutableStateOf(true)
    private var importMiniRoot: LinearLayout? = null
    private var importMiniIconText: TextView? = null
    private var importMiniTitleText: TextView? = null
    private var importMiniSubtitleText: TextView? = null
    private var importMiniProgressText: TextView? = null
    private var importMiniPrevButton: TextView? = null
    private var importMiniNextButton: TextView? = null
    private var importMiniExpandButton: TextView? = null
    private var isPanelInputActive = false

    val isShowing: Boolean
        get() = panelWindow != null || bubbleWindow != null

    /**
     * 以导入模式启动游龙面板。
     * 从 PublicLibraryShowScreen 菜单触发，携带当前查看的命令库数据
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
     * 以导出模式启动游龙面板
     * 从 HomeScreen / 悬浮窗菜单触发，不依赖任何现有库数据
     */
    fun showExport(context: Context) {
        if (isShowing) dismiss()

        val vm = LoongFlowViewModel(application)
        vm.initExport()
        viewModel = vm

        showPanel(context, LoongFlowMode.EXPORT, null)
    }

    /**
     * 面板 → 气泡：收缩为迷你气泡
     * 面板隐藏但不销毁（保留 ViewModel 状态），气泡显示在屏幕右侧边缘
     */
    fun minimizeToBubble() {
        val panel = panelWindow ?: return
        showBubble()
        setPanelInputActive(false, hideKeyboard = true)
        panel.contentView?.clearFocus()
        pausePanelLifecycle()
        panel.windowViewVisibility = View.INVISIBLE
    }

    /**
     * 气泡 → 面板：从气泡展开回面板
     */
    fun expandFromBubble() {
        val panel = panelWindow ?: run {
            dismiss()
            return
        }
        panel.windowViewVisibility = View.VISIBLE
        resumePanelLifecycle()
        hideBubble()
    }

    /**
     * 彻底关闭游龙所有窗口并释放资源
     */
    fun dismiss() {
        setPanelInputActive(false, hideKeyboard = true)
        hideBubble()

        panelWindow?.let { panel ->
            setPanelInputActive(false, hideKeyboard = true)
            destroyPanelLifecycle(panel)
            panel.recycle()
            panelWindow = null
        }

        viewModel = null
    }

    private var isCompactMode = false

    /**
     * 切换小窗流尺寸（正常/精简）
     * 为了防止缩小时底层 WindowManager 与 Compose 视图测量脱节导致裁切问题
     * 此处保存 ViewModel 状态，销毁旧窗口，按新参数重建新窗口
     */
    fun toggleSize() {
        val vm = viewModel ?: return
        val currentMode = vm.mode
        isCompactMode = !isCompactMode

        // 记录此时的位置，方便原地重建
        val oldParams = panelWindow?.windowParams
        val oldX = oldParams?.x ?: 0
        val oldY = oldParams?.y ?: 0

        // 回收旧的 Panel，但不清空 viewModel
        panelWindow?.let { panel ->
            destroyPanelLifecycle(panel)
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
     * 根据拖拽增量移动面板位置
     */
    fun movePanel(deltaX: Float, deltaY: Float) {
        val panel = panelWindow ?: return
        val params = panel.windowParams
        params.x += deltaX.toInt()
        params.y += deltaY.toInt()
        panel.update()
    }

    /**
     * 拖拽结束时检测面板是否靠近屏幕边缘，如果是则触发最小化
     */
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
        val metrics = currentScreenMetrics()
        val isLandscape = metrics.widthPx > metrics.heightPx

        // 初始默认尺寸
        val panelWidth: Int
        val panelHeight: Int
        if (isLandscape) {
            panelWidth =
                if (isCompactMode) (metrics.widthPx * 0.40).toInt() else (metrics.widthPx * 0.70).toInt()
            panelHeight = (metrics.heightPx * 0.35).toInt()
        } else {
            panelWidth = (metrics.widthPx * 0.80).toInt()
            panelHeight =
                if (isCompactMode) (metrics.heightPx * 0.35).toInt() else (metrics.heightPx * 0.65).toInt()
        }

        val vm = viewModel ?: return

        val panelView = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                // 如果按下了返回键，关闭游龙面板
                if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (isPanelInputActive) {
                        setPanelInputActive(false, hideKeyboard = true)
                        return true
                    }
                    dismiss()
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
                        isEnableImportMiniIcon = isEnableImportMiniIcon,
                        onMinimize = { minimizeToBubble() },
                        onDismiss = { dismiss() },
                        onToggleSize = { toggleSize() },
                        onRequestInputFocus = { setPanelInputActive(true, hideKeyboard = false) },
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
            .removeWindowFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            .addWindowFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
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
        flags = if (active) {
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
        if (bubbleWindow != null) {
            updateImportMiniBubbleContent()
            return
        }

        val metrics = currentScreenMetrics()
        val density = metrics.density
        val isImportMini = shouldShowImportMiniBubble()
        val bubbleWidth: Int
        val bubbleHeight: Int
        val bubbleView: View

        if (isImportMini) {
            val maxAllowedWidth = (metrics.widthPx - (12 * density).toInt()).coerceAtLeast((160 * density).toInt())
            val minDesiredWidth = (236 * density).toInt().coerceAtMost(maxAllowedWidth)
            bubbleWidth = (318 * density).toInt()
                .coerceAtMost((metrics.widthPx * 0.78f).toInt())
                .coerceAtMost(maxAllowedWidth)
                .coerceAtLeast(minDesiredWidth)
            bubbleHeight = (88 * density).toInt()
            bubbleView = createImportMiniBubbleView(bubbleWidth, bubbleHeight)
        } else {
            val bubbleSize = (48 * density).toInt()
            bubbleWidth = bubbleSize
            bubbleHeight = bubbleSize
            bubbleView = ImageView(application).apply {
                setImageResource(R.drawable.ic_loong_flow_bubble)
                layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize)
            }
        }

        bubbleWindow = EasyWindow.with(application)
            .setContentView(bubbleView)
            .setWindowSize(bubbleWidth, bubbleHeight)
            .setWindowDraggableRule(MovingWindowDraggableRule())
            .setOutsideTouchable(true)
            .setWindowLocation(
                Gravity.END or Gravity.CENTER_VERTICAL,
                0, 0
            )
            .setWindowAnim(0)
            .setWindowAlpha(if (isImportMini) 0.96f else 0.85f)

        if (isImportMini) {
            installImportMiniBubbleGesture(bubbleView)
        } else {
            bubbleView.setOnClickListener { expandFromBubble() }
            bubbleView.setOnLongClickListener {
                dismiss()
                true
            }
        }

        bubbleWindow?.show()
        updateImportMiniBubbleContent()
    }

    private fun hideBubble() {
        bubbleWindow?.let {
            it.recycle()
            bubbleWindow = null
        }
        importMiniRoot = null
        importMiniIconText = null
        importMiniTitleText = null
        importMiniSubtitleText = null
        importMiniProgressText = null
        importMiniPrevButton = null
        importMiniNextButton = null
        importMiniExpandButton = null
    }

    private fun pausePanelLifecycle() {
        val owner = composeLifecycleOwner ?: return
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            owner.onPause()
        }
    }

    private fun resumePanelLifecycle() {
        val owner = composeLifecycleOwner ?: return
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            owner.onResume()
        }
    }

    private fun destroyPanelLifecycle(panel: EasyWindow<*>) {
        val owner = composeLifecycleOwner ?: return
        pausePanelLifecycle()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            owner.onStop()
        }
        if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            owner.onDestroy()
        }
        owner.detachFromDecorView(panel.rootLayout)
        composeLifecycleOwner = null
    }

    private fun shouldShowImportMiniBubble(): Boolean {
        val vm = viewModel ?: return false
        return vm.mode == LoongFlowMode.IMPORT &&
                vm.importStep == 1 &&
                isEnableImportMiniIcon &&
                vm.importMiniIconEnabled
    }

    private fun createImportMiniBubbleView(width: Int, height: Int): LinearLayout {
        val density = currentScreenMetrics().density
        val root = LinearLayout(application).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * density).toInt(), (7 * density).toInt(), (8 * density).toInt(), (7 * density).toInt())
            layoutParams = FrameLayout.LayoutParams(width, height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 8 * density
            }
        }

        val topRow = LinearLayout(application).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val icon = TextView(application).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt())
        }

        val textColumn = LinearLayout(application).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = (8 * density).toInt()
            }
        }
        val title = TextView(application).apply {
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val subtitle = TextView(application).apply {
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (2 * density).toInt()
            }
        }
        textColumn.addView(title)
        textColumn.addView(subtitle)
        topRow.addView(icon)
        topRow.addView(textColumn)

        val bottomRow = LinearLayout(application).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (28 * density).toInt()
            ).apply {
                topMargin = (4 * density).toInt()
            }
        }
        val progress = TextView(application).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(0, (24 * density).toInt(), 1f)
        }
        val prevButton = createImportMiniActionButton("上一条", density)
        val nextButton = createImportMiniActionButton("下一条", density)
        val expandButton = createImportMiniActionButton("详情", density)

        prevButton.setOnClickListener { handleImportMiniBubblePrevClick() }
        nextButton.setOnClickListener {
            val vm = viewModel ?: return@setOnClickListener
            if (vm.isImportComplete) dismiss() else handleImportMiniBubbleClick()
        }
        expandButton.setOnClickListener { expandFromBubble() }

        bottomRow.addView(progress)
        bottomRow.addView(prevButton)
        bottomRow.addView(nextButton)
        bottomRow.addView(expandButton)

        root.addView(topRow)
        root.addView(bottomRow)

        importMiniRoot = root
        importMiniIconText = icon
        importMiniTitleText = title
        importMiniSubtitleText = subtitle
        importMiniProgressText = progress
        importMiniPrevButton = prevButton
        importMiniNextButton = nextButton
        importMiniExpandButton = expandButton
        return root
    }

    private fun createImportMiniActionButton(text: String, density: Float): TextView = TextView(application).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        isClickable = true
        layoutParams = LinearLayout.LayoutParams((47 * density).toInt(), (24 * density).toInt()).apply {
            leftMargin = (5 * density).toInt()
        }
    }

    private fun installImportMiniBubbleGesture(view: View) {
        view.isClickable = true
        view.setOnClickListener { expandFromBubble() }
        view.setOnLongClickListener {
            dismiss()
            true
        }
    }

    private fun handleImportMiniBubbleClick() {
        val vm = viewModel ?: return
        if (vm.isImportComplete) {
            Toast.makeText(application, "全部导入完成", Toast.LENGTH_SHORT).show()
            return
        }
        val hasNext = vm.nextCommand()
        val total = vm.selectedCommands.size
        if (hasNext) {
            Toast.makeText(application, "已复制 (${vm.currentCopyIndex + 1}/$total)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(application, "全部导入完成", Toast.LENGTH_SHORT).show()
        }
        vm.toastMessage = null
        updateImportMiniBubbleContent()
    }

    private fun handleImportMiniBubblePrevClick() {
        val vm = viewModel ?: return
        if (vm.currentCopyIndex <= 0 && !vm.isImportComplete) {
            Toast.makeText(application, "已经是第一条", Toast.LENGTH_SHORT).show()
            return
        }
        vm.prevCommand()
        val total = vm.selectedCommands.size
        Toast.makeText(application, "已复制 (${vm.currentCopyIndex + 1}/$total)", Toast.LENGTH_SHORT).show()
        vm.toastMessage = null
        updateImportMiniBubbleContent()
    }

    private fun updateImportMiniBubbleContent() {
        val root = importMiniRoot ?: return
        val icon = importMiniIconText ?: return
        val title = importMiniTitleText ?: return
        val subtitle = importMiniSubtitleText ?: return
        val progress = importMiniProgressText ?: return
        val prevButton = importMiniPrevButton ?: return
        val nextButton = importMiniNextButton ?: return
        val expandButton = importMiniExpandButton ?: return
        val state = buildImportMiniBubbleState()
        val density = currentScreenMetrics().density
        val surfaceColor = if (theme == CHelperTheme.Theme.Dark) Color.argb(242, 30, 32, 36) else Color.argb(248, 255, 255, 255)
        val titleColor = if (theme == CHelperTheme.Theme.Dark) Color.rgb(246, 247, 250) else Color.rgb(28, 32, 38)
        val subtitleColor = if (theme == CHelperTheme.Theme.Dark) Color.argb(210, 226, 230, 238) else Color.argb(190, 44, 50, 60)
        val mutedButtonBg = if (theme == CHelperTheme.Theme.Dark) Color.argb(34, 255, 255, 255) else Color.argb(24, 20, 28, 38)
        val mutedButtonText = if (theme == CHelperTheme.Theme.Dark) Color.argb(220, 238, 240, 246) else Color.argb(210, 42, 48, 58)
        root.background = roundedRect(
            color = surfaceColor,
            radius = 18 * density,
            strokeColor = state.color,
            strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
        )
        icon.text = state.icon
        icon.background = oval(state.color)
        title.text = state.title
        title.setTextColor(titleColor)
        subtitle.text = state.subtitle
        subtitle.setTextColor(subtitleColor)
        progress.text = state.progress
        progress.setTextColor(state.color)
        progress.background = roundedRect(colorWithAlpha(state.color, 28), 12 * density)

        styleImportMiniActionButton(
            button = prevButton,
            text = "上一条",
            textColor = mutedButtonText,
            backgroundColor = mutedButtonBg,
            density = density,
            enabled = state.canPrev
        )
        styleImportMiniActionButton(
            button = nextButton,
            text = state.nextText,
            textColor = Color.WHITE,
            backgroundColor = state.color,
            density = density,
            enabled = true
        )
        styleImportMiniActionButton(
            button = expandButton,
            text = "详情",
            textColor = state.color,
            backgroundColor = colorWithAlpha(state.color, 24),
            density = density,
            enabled = true
        )
    }

    private fun styleImportMiniActionButton(
        button: TextView,
        text: String,
        textColor: Int,
        backgroundColor: Int,
        density: Float,
        enabled: Boolean,
    ) {
        button.text = text
        button.isEnabled = true
        button.alpha = if (enabled) 1f else 0.45f
        button.setTextColor(textColor)
        button.background = roundedRect(backgroundColor, 12 * density)
    }

    private fun buildImportMiniBubbleState(): ImportMiniBubbleState {
        val vm = viewModel ?: return ImportMiniBubbleState("?", "无导入任务", "请重新打开游龙", "--", "下一条", false, Color.rgb(96, 125, 139))
        val total = vm.selectedCommands.size
        if (vm.isImportComplete) {
            return ImportMiniBubbleState(
                icon = "✓",
                title = "导入完成",
                subtitle = "所有命令都已经复制过一遍",
                progress = "$total/$total",
                nextText = "关闭",
                canPrev = total > 0,
                color = Color.rgb(46, 125, 50)
            )
        }
        val ctx = vm.currentImportCommand
            ?: return ImportMiniBubbleState("空", "没有可导入命令", "回到详情重新选择", "0/0", "下一条", false, Color.rgb(96, 125, 139))
        val block = ctx.blockData
        if (block == null) {
            return ImportMiniBubbleState(
                icon = "纯",
                title = ctx.chainName?.takeIf { it.isNotBlank() } ?: "纯命令",
                subtitle = commandPreview(ctx.command),
                progress = "${vm.currentCopyIndex + 1}/$total",
                nextText = if (vm.currentCopyIndex >= total - 1) "完成" else "下一条",
                canPrev = vm.currentCopyIndex > 0,
                color = Color.rgb(84, 110, 122)
            )
        }

        val baseColor = if (theme == CHelperTheme.Theme.Dark) block.type.darkColor else block.type.lightColor
        val parts = mutableListOf<String>()
        parts += typeName(block.type)
        if (block.type != BlockType.CHAT) {
            if (block.conditional) parts += "条件"
            parts += if (block.needsRedstone) "红石控制" else "始终开启"
            if (block.tickDelay > 0) parts += "延迟 ${block.tickDelay}刻"
        }
        return ImportMiniBubbleState(
            icon = typeIcon(block.type),
            title = ctx.chainName?.takeIf { it.isNotBlank() } ?: typeName(block.type),
            subtitle = parts.joinToString(" · "),
            progress = "${vm.currentCopyIndex + 1}/$total",
            nextText = if (vm.currentCopyIndex >= total - 1) "完成" else "下一条",
            canPrev = vm.currentCopyIndex > 0,
            color = baseColor.toArgb()
        )
    }

    private fun commandPreview(command: String): String {
        val compact = command.replace(Regex("\\s+"), " ").trim()
        return if (compact.length > 42) compact.take(42) + "..." else compact
    }

    private fun typeName(type: BlockType): String = when (type) {
        BlockType.IMPULSE -> "脉冲"
        BlockType.CHAIN -> "连锁"
        BlockType.REPEAT -> "循环"
        BlockType.CHAT -> "手动输入"
    }

    private fun typeIcon(type: BlockType): String = when (type) {
        BlockType.IMPULSE -> "脉"
        BlockType.CHAIN -> "链"
        BlockType.REPEAT -> "循"
        BlockType.CHAT -> "手"
    }

    private fun roundedRect(color: Int, radius: Float, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null && strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }

    private fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private data class ImportMiniBubbleState(
        val icon: String,
        val title: String,
        val subtitle: String,
        val progress: String,
        val nextText: String,
        val canPrev: Boolean,
        val color: Int,
    )

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
