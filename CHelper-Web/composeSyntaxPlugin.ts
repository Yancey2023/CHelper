import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'

const syntaxFile = fileURLToPath(
  new URL(
    '../CHelper-Compose/shared/ui/src/commonMain/kotlin/yancey/chelper/ui/designsystem/text/SyntaxTheme.kt',
    import.meta.url,
  ),
)
const syntaxModule = 'virtual:chelper-compose-syntax'
const resolvedSyntaxModule = '\0' + syntaxModule

function argbToCss(literal: string): string {
  const argb = (Number.parseInt(literal, 16) >>> 0).toString(16).padStart(8, '0')
  const rgb = argb.slice(2)
  const alpha = argb.slice(0, 2)
  return `#${rgb}${alpha === 'ff' ? '' : alpha}`
}

function readDarkSyntax(): Record<number, string> {
  const source = readFileSync(syntaxFile, 'utf8')
  const night = Array.from(source.matchAll(/\binit\s*\{([^{}]*)\}/g), ([, body]) => body).find(
    (body) => body.includes('THEME_NIGHT.'),
  )
  const literals = new Map(
    Array.from(night?.matchAll(/\bval\s+(\w+)\s*=\s*(-?0x[\da-f]+)/gi) ?? [], ([, name, value]) => [
      name,
      value,
    ]),
  )
  const assignments = new Map(
    Array.from(night?.matchAll(/THEME_NIGHT\.(\w+)\s*=\s*(\w+)/g) ?? [], ([, field, name]) => [
      field,
      name,
    ]),
  )
  const tokens = Array.from(source.matchAll(/^\s*(\d+)\s*->\s*(color\w+)\s*$/gm))
  if (tokens.length === 0) {
    throw new Error(`Cannot read syntax token roles from ${syntaxFile}`)
  }
  return Object.fromEntries(
    tokens.map(([, token, field]) => {
      const literal = literals.get(assignments.get(field) ?? '')
      if (literal === undefined) {
        throw new Error(`Cannot read THEME_NIGHT.${field} from ${syntaxFile}`)
      }
      return [Number(token), argbToCss(literal)]
    }),
  )
}

export function composeSyntaxPlugin(): Plugin {
  return {
    name: 'chelper-compose-syntax',
    resolveId(id) {
      if (id === syntaxModule) {
        return resolvedSyntaxModule
      }
    },
    load(id) {
      if (id === resolvedSyntaxModule) {
        this.addWatchFile(syntaxFile)
        return `export const darkSyntax = ${JSON.stringify(readDarkSyntax())};`
      }
    },
  }
}
