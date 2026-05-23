/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * 游龙悬浮窗拖拽规则
 * 通过 OnWindowDraggingListener 在拖拽结束时检测位置
 */

package yancey.chelper.android.window

import com.hjq.window.EasyWindow
import com.hjq.window.draggable.AbstractWindowDraggableRule.OnWindowDraggingListener
import com.hjq.window.draggable.MovingWindowDraggableRule

class LoongFlowDraggableRule(
    private val onEdgeMinimize: () -> Unit
) : MovingWindowDraggableRule() {

    /**
     * 允许窗口被拖出可视区域
     */
    override fun isSupportMoveOffScreen(): Boolean = true

    override fun start(easyWindow: EasyWindow<*>) {
        super.start(easyWindow)

        // 检测拖拽结束位置
        setWindowDraggingListener(object : OnWindowDraggingListener {
            override fun onWindowDraggingStop(easyWindow: EasyWindow<*>) {
                val viewX = viewOnScreenX
                val viewWidth = windowViewWidth
                val sw = screenWidth

                // 窗口中心点 X 坐标
                val centerX = viewX + viewWidth / 2


            }
        })
    }
}
