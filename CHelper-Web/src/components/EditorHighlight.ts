import { darkSyntax } from 'virtual:chelper-compose-syntax'

export interface HighlightRun {
  text: string
  color: string
}

const lightSyntax: Record<number, string> = {
  1: '#4fad63', // boolean
  2: '#4fad63', // float
  3: '#4fad63', // integer
  4: '#4fad63', // symbol
  5: '#d4ac0d', // id
  6: '#07c160', // target selector
  7: '#9f20a7', // command
  8: '#836c0a', // bracket1
  9: '#9f20a7', // bracket2
  10: '#4571e1', // bracket3
  11: '#d95a53', // string
  12: '#0fa0c8', // null
  13: '#0fa0c8', // range
  14: '#0fa0c8', // literal
}

/** 将逐字符 token 合并成连续颜色片段，供只读高亮层渲染。 */
export function createHighlightRuns(text: string, tokens: number[], dark = false): HighlightRun[] {
  const colors = dark ? darkSyntax : lightSyntax
  const defaultColor = dark ? 'var(--foreground)' : '#000000'
  const runs: HighlightRun[] = []
  for (let index = 0; index < text.length; index++) {
    const color = colors[tokens[index]] ?? defaultColor
    const previous = runs[runs.length - 1]
    if (previous?.color === color) {
      previous.text += text[index]
    } else {
      runs.push({ text: text[index], color })
    }
  }
  return runs
}
