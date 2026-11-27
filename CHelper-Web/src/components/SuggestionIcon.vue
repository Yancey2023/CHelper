<script setup lang="ts">
import { useTemplateRef, watch } from 'vue'
import { paintSuggestionIcon, suggestionIconPixels } from '@/lib/suggestionIconCache'

const props = defineProps<{ url: string }>()
const canvas = useTemplateRef<HTMLCanvasElement>('canvas')

watch([canvas, () => props.url], ([element, url], _, onCleanup) => {
  const context = element?.getContext('2d')
  if (!context) return
  context.clearRect(0, 0, suggestionIconPixels, suggestionIconPixels)
  const cancel = paintSuggestionIcon(url, (icon) => context.drawImage(icon, 0, 0))
  if (cancel) onCleanup(cancel)
})
</script>

<template>
  <canvas
    ref="canvas"
    :width="suggestionIconPixels"
    :height="suggestionIconPixels"
    aria-hidden="true"
    class="size-7 shrink-0"
  />
</template>
