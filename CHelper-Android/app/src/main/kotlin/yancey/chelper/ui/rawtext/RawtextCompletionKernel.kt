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

package yancey.chelper.ui.rawtext

import android.content.Context
import yancey.chelper.core.CHelperCore
import yancey.chelper.core.KernelCache

/**
 * rawtext 编辑器补全内核的持有者：从共享 [KernelCache] 按主包启用段租借内核
 * （与命令补全/库高亮同段复用，同源主包），生命周期由 [RawtextViewModel] 管理。
 *
 * 补全建议（[FragmentCompletion]）在 UI 线程同步读取 [core]；
 * 内核未就绪（尚未加载/合成失败）时返回 null，编辑器不弹候选（补全统一走内核）。
 */
object RawtextCompletionKernel {

    @Volatile
    private var core: CHelperCore? = null

    @Volatile
    private var segment: String? = null

    /** 当前可用的补全内核；未就绪返回 null */
    fun core(): CHelperCore? = core

    /**
     * 确保持有 [segment] 段的内核租约；异段先归还旧的再取新的。
     * 可能触发合成（耗时），**必须在后台线程调用**。
     */
    @Synchronized
    fun ensure(context: Context, segment: String) {
        if (segment.isEmpty()) {
            return
        }
        if (core != null && this.segment == segment) {
            return
        }
        releaseLocked()
        core = KernelCache.acquire(context, segment)
        this.segment = segment
    }

    /** 归还租约（ViewModel.onCleared / 段清空时调用；幂等） */
    @Synchronized
    fun release() {
        releaseLocked()
    }

    private fun releaseLocked() {
        val old = segment ?: return
        segment = null
        core = null
        KernelCache.release(old)
    }
}
