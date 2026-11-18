import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import Editor from './Editor.vue'
import { setSelectionOffsets } from './EditorSelection'
import type { EditorValue } from '../types'

const initialValue: EditorValue = {
  text: '',
  selectionStart: 0,
  selectionEnd: 0,
}

const textValue: EditorValue = {
  text: 'hello world',
  selectionStart: 0,
  selectionEnd: 0,
}

const fullTextValue: EditorValue = {
  text: 'hello world',
  selectionStart: 0,
  selectionEnd: 11,
}

const wrappers: VueWrapper[] = []

function getEditor(wrapper: VueWrapper): HTMLDivElement {
  return wrapper.get('.editor').element as HTMLDivElement
}

function latestModelValue(wrapper: VueWrapper): EditorValue {
  const events = wrapper.emitted('update:modelValue')
  return events?.[events.length - 1]?.[0] as EditorValue
}

afterEach(() => {
  for (const wrapper of wrappers.splice(0)) {
    wrapper.unmount()
  }
  vi.restoreAllMocks()
  window.getSelection()?.removeAllRanges()
  document.body.innerHTML = ''
})

describe('Editor', () => {
  it('emits both offsets for a Ctrl+A-like or mobile select-all range', async () => {
    const wrapper = mount(Editor, {
      props: { modelValue: initialValue, syntaxTokens: [] },
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await wrapper.setProps({ modelValue: textValue })
    const editor = getEditor(wrapper)
    const range = document.createRange()
    range.selectNodeContents(editor)
    const selection = window.getSelection()
    selection?.removeAllRanges()
    selection?.addRange(range)

    document.dispatchEvent(new Event('selectionchange'))

    expect(latestModelValue(wrapper)).toEqual({
      text: 'hello world',
      selectionStart: 0,
      selectionEnd: 11,
    })
  })

  it('updates the separate highlight layer without touching editable DOM or Selection', async () => {
    const wrapper = mount(Editor, {
      props: { modelValue: initialValue, syntaxTokens: [] },
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await wrapper.setProps({ modelValue: fullTextValue })
    const editor = getEditor(wrapper)
    const editableHtml = editor.innerHTML
    setSelectionOffsets(editor, 0, 11)

    await wrapper.setProps({ syntaxTokens: new Array(11).fill(7) })

    expect(editor.innerHTML).toBe(editableHtml)
    expect(editor.querySelectorAll('span')).toHaveLength(0)
    expect(wrapper.get('.editor-highlight').text()).toBe('hello world')
    expect(wrapper.get('.editor-highlight').findAll('span[style]')).not.toHaveLength(0)
    expect(window.getSelection()?.toString()).toBe('hello world')
  })

  it('does not restore a stale model selection while the editor owns focus', async () => {
    const wrapper = mount(Editor, {
      props: { modelValue: initialValue, syntaxTokens: [] },
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await wrapper.setProps({ modelValue: textValue })
    const editor = getEditor(wrapper)
    setSelectionOffsets(editor, 0, 11)
    vi.spyOn(document, 'activeElement', 'get').mockReturnValue(editor)

    await wrapper.setProps({
      modelValue: { ...textValue, selectionStart: 11, selectionEnd: 11 },
    })

    expect(window.getSelection()?.toString()).toBe('hello world')
  })

  it('applies external text and its complete selection together', async () => {
    const wrapper = mount(Editor, {
      props: { modelValue: initialValue, syntaxTokens: [] },
      attachTo: document.body,
    })
    wrappers.push(wrapper)

    await wrapper.setProps({
      modelValue: { text: 'abc', selectionStart: 1, selectionEnd: 3 },
    })

    expect(getEditor(wrapper).textContent).toBe('abc')
    expect(window.getSelection()?.toString()).toBe('bc')
  })

  it('does not rewrite editable DOM during composition and publishes on compositionend', async () => {
    const wrapper = mount(Editor, {
      props: { modelValue: initialValue, syntaxTokens: [] },
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await wrapper.setProps({ modelValue: textValue })
    const editor = getEditor(wrapper)
    setSelectionOffsets(editor, 11, 11)
    editor.dispatchEvent(new CompositionEvent('compositionstart'))
    await wrapper.vm.$nextTick()
    editor.textContent = 'hello world命'
    setSelectionOffsets(editor, 12, 12)
    const compositionHtml = editor.innerHTML
    const emittedBeforeInput = wrapper.emitted('update:modelValue')?.length ?? 0

    editor.dispatchEvent(
      new InputEvent('input', { bubbles: true, inputType: 'insertCompositionText' }),
    )
    document.dispatchEvent(new Event('selectionchange'))
    await wrapper.setProps({ modelValue: fullTextValue, syntaxTokens: new Array(11).fill(7) })

    expect(editor.innerHTML).toBe(compositionHtml)
    expect(wrapper.get('.editor-shell').classes()).toContain('composing')
    expect(wrapper.get('.editor-highlight').text()).toBe('hello world命')
    expect(wrapper.get('.editor-highlight-preedit').text()).toBe('命')
    expect(wrapper.emitted('update:modelValue')?.length ?? 0).toBe(emittedBeforeInput)

    editor.dispatchEvent(new CompositionEvent('compositionend'))
    await wrapper.vm.$nextTick()
    expect(latestModelValue(wrapper)).toEqual({
      text: 'hello world命',
      selectionStart: 12,
      selectionEnd: 12,
    })
  })

  it('emits a collapsed range after replacing selected text', async () => {
    const wrapper = mount(Editor, {
      props: { modelValue: initialValue, syntaxTokens: [] },
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await wrapper.setProps({ modelValue: fullTextValue })
    const editor = getEditor(wrapper)
    editor.textContent = 'abc'
    setSelectionOffsets(editor, 3, 3)
    editor.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText' }))

    expect(latestModelValue(wrapper)).toEqual({
      text: 'abc',
      selectionStart: 3,
      selectionEnd: 3,
    })
  })

  it('uses beforeinput for single-line behavior without intercepting keyboard shortcuts', async () => {
    const wrapper = mount(Editor, {
      props: { modelValue: initialValue, syntaxTokens: [] },
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    await wrapper.setProps({ modelValue: textValue })
    const editor = getEditor(wrapper)
    const paragraph = new InputEvent('beforeinput', {
      bubbles: true,
      cancelable: true,
      inputType: 'insertParagraph',
    })
    editor.dispatchEvent(paragraph)
    expect(paragraph.defaultPrevented).toBe(true)

    for (const key of ['a', 'z', 'y', 'Backspace', 'Delete']) {
      const event = new KeyboardEvent('keydown', {
        bubbles: true,
        cancelable: true,
        ctrlKey: key === 'a',
        key,
      })
      editor.dispatchEvent(event)
      expect(event.defaultPrevented).toBe(false)
    }
  })
})
