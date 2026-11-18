<script setup lang="ts">
import { ListboxItem } from 'reka-ui'
import type { ComponentPublicInstance } from 'vue'
import { Card } from '@/components/ui/card'
import type { Suggestion } from '@/core/libCHelperWeb'

const props = defineProps<{
  index: number
  suggestion: Suggestion | null
  measure: (element: HTMLElement | null) => void
}>()

defineEmits<{ apply: [index: number] }>()

function measureRow(row: Element | ComponentPublicInstance | null): void {
  const element = row instanceof Element ? row : row?.$el
  props.measure(element instanceof HTMLElement ? element : null)
}
</script>

<template>
  <ListboxItem
    :ref="measureRow"
    :value="index"
    :disabled="!suggestion"
    class="group w-full outline-none"
    @select.prevent="$emit('apply', index)"
  >
    <Card
      v-if="suggestion"
      class="cursor-pointer gap-1 p-(--card-padding) wrap-break-word ring-inset hover:bg-accent group-focus-visible:bg-accent group-focus-visible:ring-ring"
    >
      <div class="font-medium">{{ suggestion.title }}</div>
      <div v-if="suggestion.description" class="text-muted-foreground">
        {{ suggestion.description }}
      </div>
    </Card>
  </ListboxItem>
</template>
