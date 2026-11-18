<script setup lang="ts">
import { useEventListener, useResizeObserver } from '@vueuse/core'
import { injectScrollAreaRootContext } from 'reka-ui'
import { onMounted, shallowRef, useTemplateRef } from 'vue'
import type { CSSProperties } from 'vue'

const viewport = useTemplateRef<HTMLDivElement>('viewport')
const content = shallowRef<HTMLElement>()
const { onViewportChange, onContentChange } = injectScrollAreaRootContext()
const needsFadeFallback = !globalThis.CSS?.supports('animation-timeline: scroll()')
const fadeStyle = shallowRef<CSSProperties | undefined>(
  needsFadeFallback ? { '--scroll-fade-t-size': '0px', '--scroll-fade-b-size': '0px' } : undefined,
)

if (needsFadeFallback) {
  function updateFade(): void {
    const element = viewport.value
    if (!element) {
      return
    }
    const { scrollTop, scrollHeight, clientHeight } = element
    const above = Math.max(0, scrollTop)
    const below = Math.max(0, scrollHeight - clientHeight - scrollTop)
    fadeStyle.value = {
      '--scroll-fade-t-size': `min(var(--scroll-fade-size), ${above}px)`,
      '--scroll-fade-b-size': `min(var(--scroll-fade-size), ${below}px)`,
    }
  }

  // 虚拟列表重新测量行高后，总滚动长度也可能改变。
  useEventListener(viewport, 'scroll', updateFade, { passive: true })
  useResizeObserver([viewport, content], updateFade)
}

onMounted(() => {
  const element = viewport.value
  if (!element) {
    return
  }

  // Reka 的虚拟列表使用直接父元素滚动，滚动条也需要监听同一个视口。
  onViewportChange(element)
  const child = element.firstElementChild
  content.value = child instanceof HTMLElement ? child : element
  onContentChange(content.value)
})
</script>

<template>
  <div
    ref="viewport"
    data-slot="scroll-area-viewport"
    class="virtual-viewport scroll-fade-y scroll-fade-4 size-full [--scroll-fade-reveal:16px]"
    :style="fadeStyle"
  >
    <slot />
  </div>
</template>

<style scoped>
.virtual-viewport {
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: none;
}

.virtual-viewport::-webkit-scrollbar {
  display: none;
}
</style>
