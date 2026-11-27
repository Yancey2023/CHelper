/**
 * CHelper 内核（emscripten）JS 绑定模块的类型声明。
 *
 * 运行时实现由 scripts/build_web_core.py 在每次构建内核时生成：
 * emscripten 胶水代码 + 追加的 JS wrapper（包含 CHelperCore / CommandContext）。
 * 该文件为生成代码，不要手工修改；若 wrapper 类发生变化，请同步本文件。
 */

/** 补全提示中的一条建议。 */
export interface Suggestion {
  /** 建议在列表中的序号（从 0 开始）。 */
  id: number
  /** 建议文本。 */
  title: string
  /** 建议描述。 */
  description: string
  preview?: SuggestionPreview
}

export interface SuggestionPreview {
  kind: 'icon' | 'audio' | 'easing' | 'dyeColor'
  value: string
}

/** 命令错误信息。 */
export interface ErrorReason {
  start: number
  end: number
  errorReason: string
}

/** 点击补全提示后的结果。 */
export interface ClickSuggestionResult {
  cursorPosition: number
  newText: string
}

/** 等到 wasm 运行时初始化完成后再使用 CHelperCore。 */
export const createWasmFuture: Promise<void>

/** 软件内核：持有资源包并通过 createContext 创建命令上下文。 */
export class CHelperCore {
  /** cpack 是资源包的二进制内容。 */
  constructor(cpack: Uint8Array)
  release(): void
  createContext(command: string): CommandContext
}

/** 命令上下文：持有解析好的 AST，并缓存最近一个光标位置的补全结果。 */
export class CommandContext {
  constructor(corePtr: number, command: string)
  release(): void
  getCommand(): string
  getStructure(): string
  getParamHint(index: number): string
  getErrorReasons(): ErrorReason[]
  getSuggestionSize(index: number): number
  getSuggestion(index: number, which: number): Suggestion | null
  getAllSuggestions(index: number): Suggestion[]
  applySuggestion(index: number, which: number): ClickSuggestionResult | null
  getSyntaxTokens(): number[]
  getNodeCount(): number
}
