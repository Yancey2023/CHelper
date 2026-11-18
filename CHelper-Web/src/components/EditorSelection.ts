export interface SelectionOffsets {
  start: number
  end: number
}

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

function getTextLength(node: Node): number {
  if (node.nodeType === Node.TEXT_NODE) {
    return node.textContent?.length ?? 0
  }
  let length = 0
  for (const child of node.childNodes) {
    length += getTextLength(child)
  }
  return length
}

function textBeforeNode(root: Node, node: Node): number | null {
  let current: Node | null = node
  let length = 0
  while (current && current !== root) {
    const parent: Node | null = current.parentNode
    if (!parent) {
      return null
    }
    for (const sibling of parent.childNodes) {
      if (sibling === current) {
        break
      }
      length += getTextLength(sibling)
    }
    current = parent
  }
  return current === root ? length : null
}

/** 将 Range 的 DOM 边界转换为相对于编辑器纯文本的字符偏移。 */
function getTextOffset(root: Node, container: Node, offset: number): number | null {
  if (!root.contains(container)) {
    return null
  }
  const beforeContainer = textBeforeNode(root, container)
  if (beforeContainer === null) {
    return null
  }
  if (container.nodeType === Node.TEXT_NODE) {
    const textLength = container.textContent?.length ?? 0
    return beforeContainer + Math.max(0, Math.min(offset, textLength))
  }
  const childCount = container.childNodes.length
  let length = beforeContainer
  for (let index = 0; index < Math.min(offset, childCount); index++) {
    length += getTextLength(container.childNodes[index])
  }
  return length
}

export function getRangeOffsets(root: HTMLElement, range: AbstractRange): SelectionOffsets | null {
  const start = getTextOffset(root, range.startContainer, range.startOffset)
  const end = getTextOffset(root, range.endContainer, range.endOffset)
  return start === null || end === null ? null : { start, end }
}

/** 获取相对于整个编辑器纯文本的完整选区，兼容嵌套 span/text node。 */
export function getSelectionOffsets(
  root: HTMLElement,
  fallback: SelectionOffsets,
): SelectionOffsets {
  const selection = window.getSelection()
  const offsets = selection?.rangeCount ? getRangeOffsets(root, selection.getRangeAt(0)) : null
  return offsets ?? normalizeSelection(fallback.start, fallback.end, getTextLength(root))
}

interface TextPosition {
  node: Node
  offset: number
}

/** 根据纯文本偏移找到对应的 DOM 文本节点，支持多个及嵌套 text node。 */
function findTextPosition(root: HTMLElement, offset: number): TextPosition {
  let remaining = offset
  let lastTextNode: Text | null = null
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode() as Text | null
  while (node) {
    lastTextNode = node
    if (remaining <= node.length) {
      return { node, offset: remaining }
    }
    remaining -= node.length
    node = walker.nextNode() as Text | null
  }
  if (lastTextNode) {
    return { node: lastTextNode, offset: lastTextNode.length }
  }
  return { node: root, offset: 0 }
}

/** 恢复相对于整个编辑器纯文本的完整选区。 */
export function setSelectionOffsets(root: HTMLElement, start: number, end: number): void {
  const selection = window.getSelection()
  if (!selection) {
    return
  }
  const rootTextLength = getTextLength(root)
  const normalizedSelection = normalizeSelection(start, end, rootTextLength)
  const startPosition = findTextPosition(root, normalizedSelection.start)
  const endPosition = findTextPosition(root, normalizedSelection.end)
  const range = document.createRange()
  range.setStart(startPosition.node, startPosition.offset)
  range.setEnd(endPosition.node, endPosition.offset)
  selection.removeAllRanges()
  selection.addRange(range)
}
