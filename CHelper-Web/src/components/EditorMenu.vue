<script setup lang="ts">
import { Ellipsis, GitBranch, SunMoon } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuPortal,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { Branch } from '@/core/CPackManager'

defineProps<{
  branches: readonly { value: Branch; label: string }[]
  activeBranch?: Branch
}>()

const theme = defineModel<'auto' | 'light' | 'dark'>('theme', { required: true })
defineEmits<{ selectBranch: [branch: Branch] }>()

const themes = [
  { value: 'auto', label: '自动' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
] as const
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button
        type="button"
        variant="outline"
        size="icon"
        class="h-auto w-[38px] rounded-full"
        aria-label="更多"
        title="更多"
      >
        <Ellipsis class="size-[18px]" aria-hidden="true" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent side="top" :collision-padding="5">
      <DropdownMenuSub>
        <DropdownMenuSubTrigger>
          <GitBranch aria-hidden="true" />
          分支
        </DropdownMenuSubTrigger>
        <DropdownMenuPortal>
          <DropdownMenuSubContent
            :collision-padding="5"
            class="max-h-(--reka-dropdown-menu-content-available-height) overflow-y-auto"
          >
            <DropdownMenuRadioGroup :model-value="activeBranch">
              <DropdownMenuRadioItem
                v-for="branch in branches"
                :key="branch.value"
                :value="branch.value"
                class="wrap-anywhere"
                @select="$emit('selectBranch', branch.value)"
              >
                {{ branch.label }}
              </DropdownMenuRadioItem>
            </DropdownMenuRadioGroup>
          </DropdownMenuSubContent>
        </DropdownMenuPortal>
      </DropdownMenuSub>
      <DropdownMenuSub>
        <DropdownMenuSubTrigger>
          <SunMoon aria-hidden="true" />
          主题
        </DropdownMenuSubTrigger>
        <DropdownMenuPortal>
          <DropdownMenuSubContent :collision-padding="5">
            <DropdownMenuRadioGroup :model-value="theme">
              <DropdownMenuRadioItem
                v-for="option in themes"
                :key="option.value"
                :value="option.value"
                @select="theme = option.value"
              >
                {{ option.label }}
              </DropdownMenuRadioItem>
            </DropdownMenuRadioGroup>
          </DropdownMenuSubContent>
        </DropdownMenuPortal>
      </DropdownMenuSub>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
