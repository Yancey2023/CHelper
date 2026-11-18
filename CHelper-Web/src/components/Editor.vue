<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import {
  getRangeOffsets,
  getSelectionOffsets as getDomSelectionOffsets,
  setSelectionOffsets as setDomSelectionOffsets,
} from './EditorSelection'
import { createCompositionHighlightRuns, createHighlightRuns } from './EditorHighlight'
import type { EditorComposition } from './EditorHighlight'
import type { SelectionOffsets } from './EditorSelection'
import type { EditorValue } from '../types'

const props = defineProps<{
  modelValue: EditorValue
  syntaxTokens: number[]
  dark?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: EditorValue): void
}>()

const editorRef = ref<HTMLDivElement | null>(null)
const displayText = ref(props.modelValue.text)
const composition = shallowRef<EditorComposition>()
const scrollLeft = ref(0)
const highlightRuns = computed(() =>
  composition.value
    ? createCompositionHighlightRuns(
        displayText.value,
        props.syntaxTokens,
        composition.value,
        props.dark,
      )
    : createHighlightRuns(displayText.value, props.syntaxTokens, props.dark),
)

let lastSelection: SelectionOffsets = normalizeSelection(
  props.modelValue.selectionStart,
  props.modelValue.selectionEnd,
  props.modelValue.text.length,
)
let lastPublishedValue: EditorValue = { ...props.modelValue }

function clampOffset(offset: number, textLength: number): number {
  return Math.max(0, Math.min(offset, textLength))
}

function normalizeSelection(start: number, end: number, textLength: number): SelectionOffsets {
  const clampedStart = clampOffset(start, textLength)
  const clampedEnd = clampOffset(end, textLength)
  return clampedStart <= clampedEnd
    ? { start: clampedStart, end: clampedEnd }
    : { start: clampedEnd, end: clampedStart }
}

function getText(): string {
  return editorRef.value?.textContent ?? ''
}

function setText(text: string): void {
  if (editorRef.value) {
    editorRef.value.textContent = text
  }
}

function getSelectionOffsets(): SelectionOffsets {
  const editor = editorRef.value
  const textLength = getText().length
  const fallback = normalizeSelection(lastSelection.start, lastSelection.end, textLength)
  return editor ? getDomSelectionOffsets(editor, fallback) : fallback
}

function setSelectionOffsets(start: number, end: number): void {
  const editor = editorRef.value
  if (editor) {
    setDomSelectionOffsets(editor, start, end)
  }
}

function sameEditorValue(left: EditorValue, right: EditorValue): boolean {
  return (
    left.text === right.text &&
    left.selectionStart === right.selectionStart &&
    left.selectionEnd === right.selectionEnd
  )
}

/** 发布浏览器当前状态；该函数只读取 DOM，不会反向修改 Selection。 */
function publishEditorValue(text = getText(), selection = getSelectionOffsets()): void {
  displayText.value = text
  lastSelection = normalizeSelection(selection.start, selection.end, text.length)
  const value: EditorValue = {
    text,
    selectionStart: lastSelection.start,
    selectionEnd: lastSelection.end,
  }
  if (!sameEditorValue(value, lastPublishedValue)) {
    lastPublishedValue = value
    emit('update:modelValue', value)
  }
}

function selectionIsInEditor(): boolean {
  const editor = editorRef.value
  const selection = window.getSelection()
  if (!editor || !selection || selection.rangeCount === 0) {
    return false
  }
  const range = selection.getRangeAt(0)
  return editor.contains(range.startContainer) && editor.contains(range.endContainer)
}

function onDocumentSelectionChange(): void {
  if (composition.value || !selectionIsInEditor()) {
    return
  }
  publishEditorValue(getText(), getSelectionOffsets())
}

function onBeforeInput(event: InputEvent): void {
  // 编辑器保持单行；beforeinput 同时覆盖桌面键盘和移动端虚拟键盘。
  if (event.inputType === 'insertParagraph' || event.inputType === 'insertLineBreak') {
    event.preventDefault()
  }

  const editor = editorRef.value
  const before = composition.value
  if (editor && before?.text === getText() && event.inputType === 'insertCompositionText') {
    // 部分输入法会替换浏览器给出的目标范围，例如重新转换已有文字。
    const range = event.getTargetRanges?.()[0]
    const offsets = range && getRangeOffsets(editor, range)
    if (offsets) {
      composition.value = { ...before, ...offsets }
    }
  }
}

function onEditorInput(): void {
  if (composition.value) {
    displayText.value = getText()
  } else {
    publishEditorValue()
  }
}

function onCompositionStart(): void {
  const text = getText()
  composition.value = { text, ...getSelectionOffsets() }
  displayText.value = text
}

function onCompositionEnd(): void {
  composition.value = undefined
  publishEditorValue()
}

function onEditorScroll(): void {
  scrollLeft.value = editorRef.value?.scrollLeft ?? 0
}

onMounted(() => {
  const editor = editorRef.value
  if (editor) {
    if (getText() !== props.modelValue.text) {
      setText(props.modelValue.text)
    }
    displayText.value = props.modelValue.text
    lastSelection = normalizeSelection(
      props.modelValue.selectionStart,
      props.modelValue.selectionEnd,
      props.modelValue.text.length,
    )
    editor.addEventListener('beforeinput', onBeforeInput)
    editor.addEventListener('input', onEditorInput)
    editor.addEventListener('compositionstart', onCompositionStart)
    editor.addEventListener('compositionend', onCompositionEnd)
    editor.addEventListener('scroll', onEditorScroll)
  }
  document.addEventListener('selectionchange', onDocumentSelectionChange)
})

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor) {
    editor.removeEventListener('beforeinput', onBeforeInput)
    editor.removeEventListener('input', onEditorInput)
    editor.removeEventListener('compositionstart', onCompositionStart)
    editor.removeEventListener('compositionend', onCompositionEnd)
    editor.removeEventListener('scroll', onEditorScroll)
  }
  document.removeEventListener('selectionchange', onDocumentSelectionChange)
})

watch(
  () => props.modelValue,
  (newValue) => {
    const editor = editorRef.value
    const selection = normalizeSelection(
      newValue.selectionStart,
      newValue.selectionEnd,
      newValue.text.length,
    )
    lastPublishedValue = { ...newValue }
    if (newValue.text === composition.value?.text) {
      return
    }
    displayText.value = newValue.text
    if (!editor) {
      lastSelection = selection
      return
    }

    if (newValue.text !== getText()) {
      // 仅外部真正改变文本时才写入 contenteditable，并恢复随该文本传入的选区。
      setText(newValue.text)
      lastSelection = selection
      setSelectionOffsets(selection.start, selection.end)
      return
    }

    // 文本相同时，聚焦中的浏览器 Selection 是唯一事实来源。
    // 父组件回传的旧 selection 绝不能覆盖鼠标、触摸或系统全选产生的新 Range。
    if (!editor.contains(document.activeElement)) {
      lastSelection = selection
    }
  },
)
</script>

<template>
  <div class="editor-shell" :class="{ composing: composition }">
    <div class="editor-highlight" aria-hidden="true">
      <span class="editor-highlight-content" :style="{ transform: `translateX(${-scrollLeft}px)` }">
        <span
          v-for="(run, index) in highlightRuns"
          :key="index"
          :class="{ 'editor-highlight-preedit': run.preedit }"
          :style="{ color: run.color }"
          >{{ run.text }}</span
        >
      </span>
    </div>
    <div
      ref="editorRef"
      class="editor"
      contenteditable="plaintext-only"
      spellcheck="false"
      role="textbox"
      aria-label="命令输入"
    ></div>
  </div>
</template>

<style scoped>
.editor-shell {
  position: relative;
  color: var(--foreground);
  background-color: var(--background);
  border-radius: var(--radius);
  outline: 1px solid var(--input);
  outline-offset: -1px;
  overflow: hidden;
  font-size: 0.875rem;
  line-height: 1.25rem;
}

.editor-shell:focus-within {
  outline: 2px solid var(--ring);
  outline-offset: -2px;
}

.editor,
.editor-highlight {
  box-sizing: border-box;
  width: 100%;
  min-height: 38px;
  padding: 0.5rem var(--card-padding, 0.75rem);
  white-space: pre;
  word-wrap: normal;
}

.editor {
  position: relative;
  z-index: 1;
  height: auto;
  color: transparent;
  caret-color: var(--foreground);
  background: transparent;
  border: 0;
  outline: 0;
  overflow-x: auto;
}

.editor::selection {
  color: HighlightText;
  background-color: Highlight;
  -webkit-text-fill-color: HighlightText;
}

.editor-highlight {
  position: absolute;
  z-index: 0;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.editor-highlight-content {
  display: inline-block;
}

.editor-shell.composing .editor-highlight {
  z-index: 2;
}

.editor-shell.composing .editor-highlight-content > span:not(.editor-highlight-preedit) {
  background-color: var(--background);
}

.editor-shell.composing .editor {
  color: var(--foreground);
}
</style>
