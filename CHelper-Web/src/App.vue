<script setup lang="ts">
import { CircleAlert, Copy } from '@lucide/vue'
import { useColorMode } from '@vueuse/core'
import { ListboxContent, ListboxRoot, ListboxVirtualizer } from 'reka-ui'
import { onBeforeUnmount, ref, shallowReactive, shallowRef, toRaw } from 'vue'
import { toast } from 'vue-sonner'
import { ALL_BRANCH, ALL_BRANCH_CHINESE, DEFAULT_BRANCH, getCore } from '@/core/CPackManager'
import type { Branch } from '@/core/CPackManager'
import type { CHelperCore, CommandContext, Suggestion } from '@/core/libCHelperWeb'
import Editor from '@/components/Editor.vue'
import EditorMenu from '@/components/EditorMenu.vue'
import IcpFooter from '@/components/IcpFooter.vue'
import SuggestionOption from '@/components/SuggestionOption.vue'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ScrollArea, VirtualScrollViewport } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { Toaster } from '@/components/ui/sonner'
import type { EditorValue } from '@/types'

const branchOptions = ALL_BRANCH.map((value, index) => ({
  value,
  label: ALL_BRANCH_CHINESE[index],
}))
const activeBranch = ref<Branch>()
const { store: theme, state: effectiveTheme } = useColorMode({
  storageKey: 'chelper-web-theme',
})
const structure = ref('CHelper正在加载中，请稍候')
const paramHint = ref('作者：Yancey')
const errorReason = ref('')
// 内核加载失败的提示信息，为空表示没有加载失败；重试按钮加载最近失败的分支
const loadError = ref('')
let failedBranch: Branch | undefined
const suggestions = shallowRef<{
  revision: number
  cursor: number
  indices: number[]
  read: (index: number) => Suggestion | null
  cachedTitle: (index: number) => string
}>()
let suggestionRevision = 0
const editorValue = ref<EditorValue>({
  text: '',
  selectionStart: 0,
  selectionEnd: 0,
})
const syntaxTokens = ref<number[]>([])

// 软件内核与命令上下文不参与模板渲染，不需要是响应式数据
let core: CHelperCore | undefined
let context: CommandContext | undefined
let loadRequest = 0

async function loadBranch(branch: Branch): Promise<void> {
  const request = ++loadRequest
  loadError.value = ''
  if (core === undefined) {
    structure.value = 'CHelper正在加载中，请稍候'
  }
  try {
    const newCore = await getCore(branch)
    if (request !== loadRequest) {
      newCore.release()
      return
    }
    setCore(newCore)
    activeBranch.value = branch
  } catch (error) {
    if (request !== loadRequest) {
      return
    }
    if (core === undefined) {
      structure.value = 'CHelper加载失败'
    }
    console.error(`fail to load cpack (${branch})`, error)
    failedBranch = branch
    loadError.value = error instanceof Error ? error.message : String(error)
  }
}

function retryLoad(): Promise<void> {
  return loadBranch(failedBranch ?? DEFAULT_BRANCH)
}

function setCore(newCore: CHelperCore): void {
  release()
  core = newCore
  onEditorValueChanged(editorValue.value, true)
  failedBranch = undefined
}

function release(): void {
  releaseContext()
  core?.release()
  core = undefined
}

function releaseContext(): void {
  context?.release()
  context = undefined
}

function recreateContext(text: string): CommandContext | undefined {
  releaseContext()
  context = core?.createContext(text)
  return context
}

function updateSuggestions(): void {
  const commandContext = context
  const cursor = editorValue.value.selectionStart
  const count = commandContext?.getSuggestionSize(cursor) ?? 0
  if (!commandContext || count === 0) {
    suggestions.value = undefined
    return
  }

  const items = shallowReactive(new Map<number, Suggestion | null>())
  suggestions.value = {
    revision: ++suggestionRevision,
    cursor,
    indices: Array.from({ length: count }, (_, index) => index),
    read(index) {
      if (context !== commandContext) {
        return null
      }
      // 行渲染只读取当前需要的记录。缓存变化只通知键盘首字查找。
      const cached = toRaw(items).get(index)
      if (cached !== undefined) {
        return cached
      }
      const item = commandContext.getSuggestion(cursor, index)
      items.set(index, item)
      return item
    },
    cachedTitle: (index) => items.get(index)?.title.toLowerCase() ?? '',
  }
}

function onEditorValueChanged(newEditorValue: EditorValue, refresh = false): void {
  if (newEditorValue.text.length === 0) {
    editorValue.value = newEditorValue
    structure.value = '欢迎使用CHelper'
    paramHint.value = '作者：Yancey'
    errorReason.value = ''
    syntaxTokens.value = []
    recreateContext(newEditorValue.text)
    updateSuggestions()
    return
  }
  const textChanged = refresh || editorValue.value.text !== newEditorValue.text
  if (
    !textChanged &&
    editorValue.value.selectionStart === newEditorValue.selectionStart &&
    editorValue.value.selectionEnd === newEditorValue.selectionEnd
  ) {
    return
  }
  editorValue.value = newEditorValue
  // 只有命令文本或资源包改变时才重新解析。
  const commandContext = textChanged ? recreateContext(newEditorValue.text) : context
  if (commandContext === undefined) {
    return
  }
  if (textChanged) {
    structure.value = commandContext.getStructure()
    const errorReasons = commandContext.getErrorReasons()
    if (errorReasons.length === 0) {
      errorReason.value = ''
    } else if (errorReasons.length === 1) {
      errorReason.value = errorReasons[0].errorReason
    } else {
      errorReason.value = '可能的错误原因：'
      for (let i = 0; i < errorReasons.length; i++) {
        errorReason.value += `\n${i + 1}. ${errorReasons[i].errorReason}`
      }
    }
    syntaxTokens.value = commandContext.getSyntaxTokens()
  }
  paramHint.value = commandContext.getParamHint(editorValue.value.selectionStart)
  updateSuggestions()
}

function onSuggestionClick(which: number): void {
  const query = suggestions.value
  if (!context || !query) {
    return
  }
  const clickSuggestionResult = context.applySuggestion(query.cursor, which)
  if (clickSuggestionResult == null) {
    return
  }
  onEditorValueChanged({
    text: clickSuggestionResult.newText,
    selectionStart: clickSuggestionResult.cursorPosition,
    selectionEnd: clickSuggestionResult.cursorPosition,
  })
}

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(editorValue.value.text)
    toast.dismiss('copy-command')
  } catch (reason) {
    toast.error('复制失败', { id: 'copy-command', description: String(reason) })
  }
}

onBeforeUnmount(() => {
  loadRequest++
  release()
})

void loadBranch(DEFAULT_BRANCH)
</script>

<template>
  <div class="app-frame">
    <header class="command-info">
      <Card class="gap-0 p-(--card-padding) wrap-break-word whitespace-pre-wrap ring-inset">
        {{ structure }}
      </Card>
      <Card
        class="gap-0 p-(--card-padding) wrap-break-word whitespace-pre-wrap text-muted-foreground ring-inset"
      >
        {{ paramHint }}
      </Card>
      <Alert
        v-if="errorReason"
        variant="destructive"
        class="p-(--card-padding) wrap-break-word whitespace-pre-wrap ring-inset"
      >
        {{ errorReason }}
      </Alert>
      <Alert v-if="loadError" variant="destructive" class="p-(--card-padding) ring-inset">
        <CircleAlert aria-hidden="true" />
        <AlertTitle>CHelper 内核加载失败</AlertTitle>
        <AlertDescription class="wrap-anywhere">
          <p>{{ loadError }}</p>
          <p>请检查网络后重试，或在底栏切换分支。</p>
          <Button type="button" size="sm" variant="outline" class="mt-1 w-fit" @click="retryLoad">
            重试
          </Button>
        </AlertDescription>
      </Alert>
      <Separator />
    </header>

    <main class="min-h-0">
      <ScrollArea v-if="suggestions" :key="suggestions.revision" type="auto" class="h-full">
        <template #viewport>
          <ListboxRoot class="h-full">
            <ListboxContent aria-label="命令补全" as-child>
              <VirtualScrollViewport class="suggestion-viewport">
                <ListboxVirtualizer
                  v-slot="{ option, virtualizer }"
                  :options="suggestions.indices"
                  :estimate-size="76"
                  :overscan="4"
                  :text-content="suggestions.cachedTitle"
                >
                  <SuggestionOption
                    :index="option"
                    :suggestion="suggestions.read(option)"
                    :measure="virtualizer.measureElement"
                    :class="{ 'pb-(--component-gap)': option < suggestions.indices.length - 1 }"
                    @apply="onSuggestionClick"
                  />
                </ListboxVirtualizer>
              </VirtualScrollViewport>
            </ListboxContent>
          </ListboxRoot>
        </template>
      </ScrollArea>
    </main>

    <footer class="command-footer">
      <div class="below">
        <EditorMenu
          v-model:theme="theme"
          :branches="branchOptions"
          :active-branch="activeBranch"
          @select-branch="loadBranch"
        />
        <Editor
          :model-value="editorValue"
          :syntax-tokens="syntaxTokens"
          :dark="effectiveTheme === 'dark'"
          @update:modelValue="onEditorValueChanged"
        />
        <Button
          type="button"
          variant="outline"
          size="icon"
          class="h-auto w-[38px] rounded-full"
          aria-label="复制"
          title="复制"
          @click="copy"
        >
          <Copy class="size-[18px]" aria-hidden="true" />
        </Button>
      </div>
      <IcpFooter />
    </footer>
  </div>
  <Toaster
    :theme="effectiveTheme"
    position="top-right"
    close-button
    container-aria-label="通知"
    :toast-options="{ closeButtonAriaLabel: '关闭复制提示' }"
  />
</template>

<style scoped>
.app-frame {
  --component-gap: 0.5rem;
  --card-padding: 0.75rem;

  display: grid;
  height: calc(var(--vh, 1vh) * 100);
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--component-gap);
  padding: var(--component-gap);
}

.command-info,
.command-footer {
  display: grid;
  gap: var(--component-gap);
  min-width: 0;
}

.suggestion-viewport {
  /* 为 shadcn 的 w-2.5 滚动条留出轨道宽度及卡片间距。 */
  padding-inline-end: calc(var(--component-gap) + var(--spacing) * 2.5);
}

.below {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--component-gap);
}
</style>
