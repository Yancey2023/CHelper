<script setup lang="ts">
import { ListboxItem } from 'reka-ui'
import { computed } from 'vue'
import type { ComponentPublicInstance, StyleValue } from 'vue'
import SuggestionAudioControls from '@/components/SuggestionAudioControls.vue'
import SuggestionIcon from '@/components/SuggestionIcon.vue'
import { Card } from '@/components/ui/card'
import type { Suggestion } from '@/core/libCHelperWeb'
import {
  dyeColor,
  easingGraph,
  isWikiSuggestionIconUrl,
  isWikiSuggestionMediaUrl,
} from '@/lib/suggestionPreview'
import type { SuggestionAudioState } from '@/lib/useSuggestionAudio'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  index: number
  suggestion: Suggestion | null
  measure: (element: HTMLElement | null) => void
  gapAfter: boolean
  audioState?: SuggestionAudioState
}>()

defineEmits<{
  apply: [index: number]
  audio: [index: number, url: string]
  seekAudio: [index: number, time: number]
}>()

const preview = computed(() => props.suggestion?.preview)
const icon = computed(() => {
  const data = preview.value
  return data?.kind === 'icon' && isWikiSuggestionIconUrl(data.value) ? data.value : undefined
})
const audio = computed(() => {
  const data = preview.value
  return data?.kind === 'audio' &&
    (isWikiSuggestionMediaUrl(data.value, '.ogg') || isWikiSuggestionMediaUrl(data.value, '.wav'))
    ? data.value
    : undefined
})
const graph = computed(() =>
  preview.value?.kind === 'easing' ? easingGraph(preview.value.value) : undefined,
)
const swatch = computed(() =>
  preview.value?.kind === 'dyeColor' ? dyeColor(preview.value.value) : undefined,
)
function measureRow(row: Element | ComponentPublicInstance | null): void {
  const element = row instanceof Element ? row : row?.$el
  props.measure(element instanceof HTMLElement ? element : null)
}
</script>

<template>
  <!-- Position the whole row, while Reka measures and finds the option by its data-index. -->
  <div
    class="suggestion-row relative w-full"
    :class="[
      gapAfter ? '[--suggestion-gap:var(--component-gap)]' : '[--suggestion-gap:0px]',
      { 'has-audio-timeline': audio && audioState },
    ]"
    :style="$attrs.style as StyleValue"
  >
    <ListboxItem
      v-bind="{ ...$attrs, style: undefined }"
      :ref="measureRow"
      :value="index"
      :disabled="!suggestion"
      class="group w-full pb-(--suggestion-gap) outline-none"
      @select.prevent="$emit('apply', index)"
    >
      <Card
        v-if="suggestion"
        class="cursor-pointer flex-row items-center gap-2 p-(--card-padding) wrap-break-word ring-inset hover:bg-accent group-focus-visible:bg-accent group-focus-visible:ring-ring"
        :class="{ 'audio-card': audio }"
      >
        <SuggestionIcon v-if="icon" :url="icon" />
        <svg
          v-if="graph"
          class="h-8 w-16 shrink-0 text-muted-foreground"
          viewBox="-1 0 66 32"
          role="img"
          aria-label="缓动曲线"
        >
          <path d="M0 7.11H64 M0 24.89H64" class="stroke-border" fill="none" />
          <polyline :points="graph" fill="none" stroke="currentColor" stroke-width="1.5" />
        </svg>
        <span
          v-if="swatch"
          class="size-5 shrink-0 rounded border"
          :style="{ backgroundColor: swatch }"
          role="img"
          aria-label="染料色样"
        />
        <div class="min-w-0 flex-1">
          <div class="font-medium">{{ suggestion.title }}</div>
          <div v-if="suggestion.description" class="text-muted-foreground">
            {{ suggestion.description }}
          </div>
        </div>
      </Card>
    </ListboxItem>
    <SuggestionAudioControls
      v-if="audio"
      :state="audioState"
      class="audio-controls"
      @toggle="$emit('audio', index, audio)"
      @seek="$emit('seekAudio', index, $event)"
    />
  </div>
</template>

<style scoped>
.suggestion-row {
  --suggestion-audio-button-size: 38px;
  --suggestion-audio-width: var(--suggestion-audio-button-size);
}

.has-audio-timeline {
  --suggestion-audio-width: clamp(7rem, 32%, 12rem);
}

.audio-card {
  padding-inline-end: calc(var(--card-padding) + var(--suggestion-audio-width) + 0.5rem);
}

.audio-controls {
  position: absolute;
  inset-block: 0 var(--suggestion-gap);
  inset-inline-end: var(--card-padding);
  inline-size: var(--suggestion-audio-width);
}

@container suggestion-list (max-width: 36rem) and (min-height: 8rem) {
  .has-audio-timeline .audio-card {
    padding-inline-end: var(--card-padding);
    padding-block-end: calc(var(--card-padding) + var(--suggestion-audio-button-size) + 0.5rem);
  }

  .has-audio-timeline .audio-controls {
    inset-block: auto calc(var(--suggestion-gap) + var(--card-padding));
    inset-inline: var(--card-padding);
    inline-size: auto;
  }
}
</style>
