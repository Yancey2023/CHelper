import { useEventListener } from '@vueuse/core'
import { onBeforeUnmount, onMounted } from 'vue'
import type { ShallowRef } from 'vue'

const MIN_KEYBOARD_INSET = 96
const KEYBOARD_OPEN_TIMEOUT = 650
const VIEWPORT_SETTLE_DELAY = 64

export function useEditorViewport(appFrame: Readonly<ShallowRef<HTMLDivElement | null>>) {
  const viewport = window.visualViewport
  const keyboardInsets = new Map<number, number>()
  let viewportBeforeTap: { width: number; height: number } | undefined
  let openingTimer: number | undefined
  let heightAnimation: Animation | undefined

  function cancelOpening(): void {
    window.clearTimeout(openingTimer)
    openingTimer = undefined
    heightAnimation?.cancel()
    heightAnimation = undefined
  }

  function finishKeyboardOpening(): void {
    if (openingTimer === undefined) {
      return
    }

    const frame = appFrame.value
    const animatedHeight = frame?.getBoundingClientRect().height
    cancelOpening()
    if (!frame || animatedHeight === undefined) {
      return
    }

    const viewportHeight = frame.getBoundingClientRect().height
    if (Math.abs(viewportHeight - animatedHeight) > 1) {
      heightAnimation = frame.animate(
        [{ height: `${animatedHeight}px` }, { height: `${viewportHeight}px` }],
        {
          duration: 120,
          easing: 'ease-out',
        },
      )
    }
  }

  function updateVisualViewport(): void {
    const frame = appFrame.value
    if (!frame || !viewport) {
      return
    }

    const { width, height, offsetTop, scale } = viewport
    // 只在浏览器未同步收缩布局视口时补偿，并保留用户的双指缩放。
    const needsFallback =
      scale === 1 && height > 0 && height < document.documentElement.clientHeight - 1
    frame.style.setProperty('--visual-viewport-height', needsFallback ? `${height}px` : '')
    frame.style.setProperty('--visual-viewport-top', needsFallback ? `${offsetTop}px` : '')

    if (viewportBeforeTap && (scale !== 1 || width !== viewportBeforeTap.width)) {
      viewportBeforeTap = undefined
      cancelOpening()
    }
    if (openingTimer === undefined || !viewportBeforeTap) {
      return
    }

    const heightBeforeTap = viewportBeforeTap.height
    const inset = heightBeforeTap - height
    if (inset >= MIN_KEYBOARD_INSET) {
      // 视口如有逐帧更新，等几何数据稳定后再记住键盘高度。
      window.clearTimeout(openingTimer)
      openingTimer = window.setTimeout(() => {
        const settledInset = heightBeforeTap - viewport.height
        if (settledInset >= MIN_KEYBOARD_INSET) {
          keyboardInsets.set(width, settledInset)
        }
        finishKeyboardOpening()
      }, VIEWPORT_SETTLE_DELAY)
    }
  }

  function onEditorTap(event: PointerEvent): void {
    const frame = appFrame.value
    if (
      event.pointerType !== 'touch' ||
      !event.isPrimary ||
      !frame ||
      !viewport ||
      viewport.scale !== 1
    ) {
      return
    }

    const { width, height } = viewport
    const keyboardAlreadyOpen =
      viewportBeforeTap?.width === width && viewportBeforeTap.height - height >= MIN_KEYBOARD_INSET
    if (openingTimer !== undefined || keyboardAlreadyOpen) {
      return
    }
    viewportBeforeTap = { width, height }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return
    }

    // 一些浏览器只在软键盘动画结束后报告视口高度。先按估计值上移，真实高度始终限制布局。
    const frameHeight = frame.getBoundingClientRect().height
    const inset = keyboardInsets.get(width) ?? Math.min(320, height * 0.42)
    const previewHeight = Math.max(height / 2, height - inset)
    cancelOpening()
    heightAnimation = frame.animate(
      [{ height: `${frameHeight}px` }, { height: `${previewHeight}px` }],
      {
        delay: 55,
        duration: 250,
        easing: 'cubic-bezier(0.25, 0.35, 0.25, 1)',
        fill: 'forwards',
      },
    )
    openingTimer = window.setTimeout(finishKeyboardOpening, KEYBOARD_OPEN_TIMEOUT)
  }

  onMounted(updateVisualViewport)
  onBeforeUnmount(cancelOpening)
  useEventListener(viewport, ['resize', 'scroll'], updateVisualViewport, { passive: true })

  return { onEditorTap, finishKeyboardOpening }
}
