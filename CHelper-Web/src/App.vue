<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ALL_BRANCH, ALL_BRANCH_CHINESE, DEFAULT_BRANCH, getCore } from '@/core/CPackManager'
import type { Branch } from '@/core/CPackManager'
import type { CHelperCore, CommandContext, Suggestion } from '@/core/libCHelperWeb'
import Editor from '@/components/Editor.vue'
import SelectorModal from '@/components/SelectorModal.vue'
import IcpFooter from '@/components/IcpFooter.vue'
import type { EditorValue } from '@/types'

const structure = ref('CHelper正在加载中，请稍候')
const paramHint = ref('作者：Yancey')
const errorReason = ref('')
// 内核加载失败的提示信息，为空表示没有加载失败；重试按钮加载最近失败的分支
const loadError = ref('')
let failedBranch: Branch | undefined
const suggestions = ref<Suggestion[]>([])
// 补全提示对应的光标位置，加载更多和点击补全提示时使用
const suggestionIndex = ref(0)
const realSuggestionSize = ref(0)
const isBranchSelectorVisible = ref(false)
const editorValue = ref<EditorValue>({
  text: '',
  selectionStart: 0,
  selectionEnd: 0,
})
const syntaxTokens = ref<number[]>([])

const listRef = ref<HTMLElement | null>(null)

// 软件内核与命令上下文不参与模板渲染，不需要是响应式数据
let core: CHelperCore | undefined
let context: CommandContext | undefined
let resizeObserver: ResizeObserver | undefined

async function init(): Promise<void> {
  try {
    setCore(await getCore(DEFAULT_BRANCH))
  } catch (error) {
    // 网络失败、资源包解析失败等都会走到这里，必须给出提示，不能让页面一直停在加载中
    structure.value = 'CHelper加载失败'
    reportLoadFailure(DEFAULT_BRANCH, error)
  }
}

function reportLoadFailure(branch: Branch, error: unknown): void {
  console.error(`fail to load cpack (${branch})`, error)
  failedBranch = branch
  loadError.value = error instanceof Error ? error.message : String(error)
}

async function retryLoad(): Promise<void> {
  const branch = failedBranch ?? DEFAULT_BRANCH
  loadError.value = ''
  // 只有当前没有可用内核时才改动顶部状态文字，避免覆盖切换分支失败后仍然可用的界面
  if (core === undefined) {
    structure.value = 'CHelper正在加载中，请稍候'
  }
  try {
    setCore(await getCore(branch))
  } catch (error) {
    if (core === undefined) {
      structure.value = 'CHelper加载失败'
    }
    reportLoadFailure(branch, error)
  }
}

function setCore(newCore: CHelperCore): void {
  releaseContext()
  core?.release()
  core = newCore
  recreateContext(editorValue.value.text)
  onEditorValueChanged(editorValue.value)
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

function recreateContext(text: string): void {
  releaseContext()
  if (core === undefined) {
    return
  }
  context = core.createContext(text)
}

function updateSuggestions(): void {
  if (context === undefined) {
    return
  }
  // 补全提示是按光标位置计算的，记住这个位置，加载更多和点击补全时都要用同一个位置
  suggestionIndex.value = editorValue.value.selectionStart
  realSuggestionSize.value = context.getSuggestionSize(suggestionIndex.value)
  suggestions.value = []
  loadMore(Math.floor((listRef.value?.clientHeight ?? 0) / 25))
}

function onEditorValueChanged(newEditorValue: EditorValue): void {
  if (newEditorValue.text.length === 0) {
    editorValue.value = newEditorValue
    structure.value = '欢迎使用CHelper'
    paramHint.value = '作者：Yancey'
    errorReason.value = ''
    recreateContext(newEditorValue.text)
    updateSuggestions()
    return
  }
  if (context === undefined) {
    return
  }
  if (editorValue.value.text === newEditorValue.text) {
    if (
      editorValue.value.selectionStart === newEditorValue.selectionStart &&
      editorValue.value.selectionEnd === newEditorValue.selectionEnd
    ) {
      return
    }
    // 只有光标或选区改变，无需重新解析，直接用新的光标位置查询
    editorValue.value = newEditorValue
  } else {
    // 文本内容改变，重新解析命令生成新的命令上下文
    editorValue.value = newEditorValue
    recreateContext(newEditorValue.text)
    structure.value = context.getStructure()
    const errorReasons = context.getErrorReasons()
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
    syntaxTokens.value = context.getSyntaxTokens()
  }
  paramHint.value = context.getParamHint(editorValue.value.selectionStart)
  updateSuggestions()
}

function loadMore(count: number): void {
  if (context === undefined) {
    return
  }
  const start = suggestions.value.length
  const end = Math.min(start + count, realSuggestionSize.value)
  for (let i = start; i < end; i++) {
    const suggestion = context.getSuggestion(suggestionIndex.value, i)
    if (suggestion !== null) {
      suggestions.value.push(suggestion)
    }
  }
}

function onSuggestionScroll(): void {
  const list = listRef.value
  if (list && list.scrollTop + 2 * list.clientHeight >= list.scrollHeight) {
    loadMore(Math.floor(list.clientHeight / 25))
  }
}

function onSuggestionClick(which: number): void {
  if (context === undefined) {
    return
  }
  const clickSuggestionResult = context.applySuggestion(editorValue.value.selectionStart, which)
  if (clickSuggestionResult == null) {
    return
  }
  onEditorValueChanged({
    text: clickSuggestionResult.newText,
    selectionStart: clickSuggestionResult.cursorPosition,
    selectionEnd: clickSuggestionResult.cursorPosition,
  })
}

function copy(): void {
  navigator.clipboard.writeText(editorValue.value.text).catch((reason: unknown) => {
    window.alert('复制失败：' + String(reason))
  })
}

function openBranchSelector(): void {
  isBranchSelectorVisible.value = true
}

function closeBranchSelector(): void {
  isBranchSelectorVisible.value = false
}

async function onBranchSelect(branch: string): Promise<void> {
  try {
    setCore(await getCore(branch as Branch))
  } catch (error) {
    // 切换分支失败时旧内核仍然可用，只显示提示，不改动顶部状态文字
    reportLoadFailure(branch as Branch, error)
  }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(() => {
    onSuggestionScroll()
  })
  if (listRef.value) {
    resizeObserver.observe(listRef.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  release()
})

void init()
</script>

<template>
  <div class="container">
    <header class="header">
      <div>
        <div class="text-structure">{{ structure }}</div>
        <div class="text-param-hint">{{ paramHint }}</div>
        <div class="text-error-reason" v-if="errorReason">{{ errorReason }}</div>
        <div class="text-error-reason" v-if="loadError">
          <div>CHelper 内核加载失败：{{ loadError }}</div>
          <div>请检查网络后重试，或在页面右下角切换分支。</div>
          <button class="button retry-button" @click="retryLoad">重试</button>
        </div>
        <div class="line"></div>
      </div>
    </header>
    <main ref="listRef" @scroll="onSuggestionScroll">
      <div class="div-suggestion" v-for="item in suggestions" @click="onSuggestionClick(item.id)">
        <div class="text-suggestion-name">{{ item.title }}</div>
        <div class="text-suggestion-description">{{ item.description }}</div>
      </div>
    </main>
    <footer>
      <div class="below">
        <button class="button" @click="openBranchSelector">分支</button>
        <Editor
          :modelValue="editorValue"
          :syntaxTokens="syntaxTokens"
          @update:modelValue="onEditorValueChanged"
        />
        <button class="button" @click="copy">复制</button>
      </div>
      <IcpFooter />
    </footer>
    <SelectorModal
      :title="'选择分支'"
      :data="ALL_BRANCH"
      :showNames="ALL_BRANCH_CHINESE"
      :show="isBranchSelectorVisible"
      @close="closeBranchSelector"
      @select="onBranchSelect"
    />
  </div>
</template>

<style scoped>
.container {
  display: grid;
  height: calc(var(--vh, 1vh) * 100);
  grid-template-rows: auto 1fr auto;
}

.line {
  margin: 5px 10px;
  height: 1px;
  background: darkgrey;
}

main {
  flex: 1;
  overflow-y: auto;
}

.text-structure {
  height: auto;
  padding: 10px;
  margin: 5px 5px 0 5px;
  color: #000000;
  background-color: #f5f7fa;
  border-radius: 5px;
  white-space: pre-wrap;
}

.text-param-hint {
  height: auto;
  padding: 10px;
  margin: 5px 5px 0 5px;
  color: #666666;
  background-color: #f5f7fa;
  border-radius: 5px;
  white-space: pre-wrap;
}

.text-error-reason {
  height: auto;
  padding: 10px;
  margin: 5px 5px 0 5px;
  color: #ff4444;
  background-color: #f5f7fa;
  border-radius: 5px;
  white-space: pre-wrap;
}

.div-suggestion {
  height: auto;
  padding: 5px;
  margin: 0 5px 5px 5px;
  background-color: #f5f7fa;
  border-radius: 5px;
  cursor: pointer;
}

.div-suggestion:hover {
  background-color: #f5f5f5;
}

.text-suggestion-name {
  height: auto;
  color: #000000;
  margin: 5px;
}

.text-suggestion-description {
  height: auto;
  color: #666666;
  margin: 5px;
}

.below {
  display: grid;
  left: 20px;
  width: calc(100vw - 10px);
  margin: 5px;
  grid-template-columns: auto 1fr auto;
  align-items: center;
}

.button {
  padding: 5px;
  border: 0;
  width: 50px;
  height: 100%;
  color: #f5f7fa;
  text-align: center;
  background: #007bff;
  border-radius: 5px;
  cursor: pointer;
}

.button:hover {
  background-color: #0070ff;
}

.retry-button {
  margin: 5px;
}

* {
  font-size: 15px;
  font-family:
    Inter, 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei',
    '微软雅黑', Arial, sans-serif;
}
</style>
