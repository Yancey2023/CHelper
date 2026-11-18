import { describe, expect, it } from 'vitest'
import { darkSyntax } from 'virtual:chelper-compose-syntax'
import { createHighlightRuns } from './EditorHighlight'

describe('createHighlightRuns', () => {
  it('groups adjacent characters with the same token color', () => {
    expect(createHighlightRuns('give', [7, 7, 7, 7])).toEqual([{ text: 'give', color: '#9f20a7' }])
  })

  it('keeps token boundaries and falls back to black for unknown tokens', () => {
    expect(createHighlightRuns('a b', [7, 0, 11])).toEqual([
      { text: 'a', color: '#9f20a7' },
      { text: ' ', color: '#000000' },
      { text: 'b', color: '#d95a53' },
    ])
  })

  it('handles empty text and token arrays shorter than the text', () => {
    expect(createHighlightRuns('', [])).toEqual([])
    expect(createHighlightRuns('abc', [7])).toEqual([
      { text: 'a', color: '#9f20a7' },
      { text: 'bc', color: '#000000' },
    ])
  })

  it('uses Compose token colors and the shadcn foreground in dark mode', () => {
    expect(createHighlightRuns('a b', [7, 0, 11], true)).toEqual([
      { text: 'a', color: darkSyntax[7] },
      { text: ' ', color: 'var(--foreground)' },
      { text: 'b', color: darkSyntax[11] },
    ])
  })
})
