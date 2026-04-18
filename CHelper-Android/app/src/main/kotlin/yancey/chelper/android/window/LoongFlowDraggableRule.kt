/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * 游龙悬浮窗专用拖拽规则。
 * 继承 MovingWindowDraggableRule，override isSupportMoveOffScreen 解除屏幕边界限制，
 * 并通过 OnWindowDraggingListener 在拖拽结束时检测位置——
 * 如果窗口中心超出屏幕可视区 85%，触发最小化回调。
 */

package yancey.chelper.android.window

import com.hjq.window.EasyWindow
import com.hjq.window.draggable.AbstractWindowDraggableRule.OnWindowDraggingListener
import com.hjq.window.draggable.MovingWindowDraggableRule

class LoongFlowDraggableRule(
    private val onEdgeMinimize: () -> Unit
) : MovingWindowDraggableRule() {

    /**
     * 解除屏幕边界限制，允许窗口被拖出可视区域——
     * 这样玩家才能把面板拖到边缘触发气泡最小化。
     */
    override fun isSupportMoveOffScreen(): Boolean = true

    override fun start(easyWindow: EasyWindow<*>) {
        super.start(easyWindow)

        // 利用官方回调机制检测拖拽结束位置
        setWindowDraggingListener(object : OnWindowDraggingListener {
            override fun onWindowDraggingStop(easyWindow: EasyWindow<*>) {
                val viewX = viewOnScreenX
                val viewWidth = windowViewWidth
                val sw = screenWidth

                // 窗口中心点 X 坐标
                val centerX = viewX + viewWidth / 2

                // 窗口中心超出屏幕右边缘 85% 或左边缘 15% → 触发最小化
                if (centerX > sw * 0.85 || centerX < sw * 0.15) {
                    onEdgeMinimize()
                }
            }
        })
    }
}
