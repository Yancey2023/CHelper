<script setup lang="ts">
import { Pause, Play } from '@lucide/vue'
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Slider } from '@/components/ui/slider'
import type { SuggestionAudioState } from '@/lib/useSuggestionAudio'

const props = defineProps<{ state?: SuggestionAudioState }>()
const emit = defineEmits<{ toggle: []; seek: [time: number] }>()

const canPause = computed(
  () => props.state?.status === 'playing' || props.state?.status === 'loading',
)
const buttonLabel = computed(() => (canPause.value ? '暂停试听' : '播放试听'))
const duration = computed(() => props.state?.duration ?? 0)
const elapsed = computed(() => props.state?.currentTime ?? 0)
const elapsedLabel = computed(() => formatTime(elapsed.value))
const durationLabel = computed(() => (duration.value ? formatTime(duration.value) : '–:––'))

function formatTime(seconds: number): string {
  const wholeSeconds = Math.floor(seconds)
  return `${Math.floor(wholeSeconds / 60)}:${String(wholeSeconds % 60).padStart(2, '0')}`
}

function seek(values: number[] | undefined): void {
  const position = values?.[0]
  if (position !== undefined) emit('seek', position)
}
</script>

<template>
  <div
    class="pointer-events-none flex items-center justify-end gap-2"
    role="group"
    aria-label="音频试听"
  >
    <Button
      type="button"
      variant="outline"
      size="icon"
      class="pointer-events-auto size-(--suggestion-audio-button-size) rounded-full"
      :aria-label="buttonLabel"
      :title="buttonLabel"
      :aria-busy="state?.status === 'loading' || undefined"
      @pointerdown.prevent
      @keydown.enter.stop
      @keydown.space.stop
      @click.stop="$emit('toggle')"
    >
      <Pause v-if="canPause" class="size-[18px]" aria-hidden="true" />
      <Play v-else class="size-[18px]" aria-hidden="true" />
    </Button>
    <div v-if="state" data-slot="audio-timeline" class="order-first min-w-0 flex-1">
      <Slider
        thumb-label="播放进度"
        :thumb-value-text="duration ? `${elapsedLabel} / ${durationLabel}` : undefined"
        :model-value="[elapsed]"
        :max="duration || 1"
        :disabled="!duration"
        :step="0.01"
        class="pointer-events-auto h-6 cursor-pointer data-disabled:cursor-default"
        @update:model-value="seek"
        @keydown.stop
        @click.stop
      />
      <div
        aria-hidden="true"
        class="flex justify-between gap-1 text-[11px] leading-3 tabular-nums text-muted-foreground"
      >
        <span class="text-foreground">{{ elapsedLabel }}</span>
        <span>{{ durationLabel }}</span>
      </div>
    </div>
  </div>
</template>
