import createCHelperModule from '../../../build/web_core/libCHelperWeb.js'
import wasmUrl from '../../../build/web_core/libCHelperWeb.wasm?url'

let wasmModule
export const createWasmFuture = createCHelperModule({
  locateFile: () => wasmUrl,
}).then((module) => {
  wasmModule = module
})

// 内存视图可能因内存增长（ALLOW_MEMORY_GROWTH）而失效，每次读取前通过 wasmModule.HEAPU8 获取底层 buffer
let u16View = null
function getHEAPU16() {
  const buffer = wasmModule.HEAPU8.buffer
  if (u16View === null || u16View.buffer !== buffer) {
    u16View = new Uint16Array(buffer)
  }
  return u16View
}

// ---- 手写内存协议（与 CHelper-Core/src/apps/CHelperWeb.cpp 一一对应）----
// 规则：
//   1. 所有 uint32 字段都位于 4 字节对齐地址，读取前先检查对齐与内存边界
//   2. UTF-16 字符串按 uint16 连续存储（只需 2 字节对齐）
//   3. 可变长字符串之后如果还有 uint32 字段，C++ 端在 buffer 中补齐到 4 字节，
//      JS 端在读取下一条记录前按相同公式跳过这些 padding
//   4. JS 读取什么布局，C++ 就必须真实写出什么布局；两边使用完全相同的 align4

// 4 字节向上对齐，与 C++ 的 align4 完全一致。
// 不用位运算公式 (value + 3) & ~3，避免 32 位有符号溢出影响地址计算。
function align4(value) {
  return value + ((4 - (value % 4)) % 4)
}

// 校验 [start, start + byteLength) 完全落在 WASM 线性内存内。
// 防御性检查：损坏的长度字段必须在这里抛出明确错误，而不是进入巨大的循环。
function checkMemRange(start, byteLength, what) {
  if (!Number.isFinite(byteLength) || byteLength < 0) {
    throw new Error(`Invalid WASM buffer: ${what} 长度非法: ${byteLength}`)
  }
  const end = start + byteLength
  if (start < 0 || end > wasmModule.HEAPU8.byteLength || end < start) {
    throw new Error(
      `Invalid WASM buffer: ${what} 越界 [${start}, ${end}), 内存大小 ${wasmModule.HEAPU8.byteLength}`,
    )
  }
}

// 读取 4 字节无符号整数，返回 { value, next }；ptr 必须 4 字节对齐（协议不变量）
function readU32(ptr) {
  if (ptr % 4 !== 0) {
    throw new Error(`Invalid WASM buffer: u32 读取位置未 4 字节对齐: ${ptr}`)
  }
  checkMemRange(ptr, 4, 'u32')
  return { value: wasmModule.HEAPU32[ptr >>> 2], next: ptr + 4 }
}

// 读取 [u32 长度][u16 字符串]（ptr 已 4 对齐），返回 { value, next }
function readUtf16(ptr) {
  const length = readU32(ptr).value
  const dataStart = ptr + 4
  checkMemRange(dataStart, length * 2, `字符串数据(length=${length})`)
  let result = ''
  for (let i = 0; i < length; i++) {
    result += String.fromCharCode(getHEAPU16()[(dataStart + i * 2) >> 1])
  }
  return { value: result, next: dataStart + length * 2 }
}

// 写入 utf16 编码并以0结尾的字符串，返回起始指针（仅用于向 C++ 传入命令文本）
function writeString(content) {
  const ptr = wasmModule._malloc((content.length + 1) * 2)
  const start = ptr / 2
  const end = start + content.length
  let i = start
  while (i < end) {
    getHEAPU16()[i] = content.charCodeAt(i - start)
    ++i
  }
  getHEAPU16()[i] = 0
  return ptr
}

// 读取预览类型、三个 UTF-16 长度和字符串数据（记录头按 4 字节对齐）
function readSuggestionRecord(ptr, which) {
  checkMemRange(ptr, 16, `补全建议头部(记录 ${which})`)
  const header = ptr >>> 2
  const words = wasmModule.HEAPU32
  const kind = words[header]
  const nameLength = words[header + 1]
  const descriptionLength = words[header + 2]
  const previewLength = words[header + 3]
  let p = ptr + 16
  checkMemRange(
    p,
    (nameLength + descriptionLength + previewLength) * 2,
    `补全建议数据(记录 ${which})`,
  )
  const read = (length) => {
    let value = ''
    const memory = getHEAPU16()
    for (let i = 0; i < length; i++) {
      value += String.fromCharCode(memory[p >>> 1])
      p += 2
    }
    return value
  }
  const title = read(nameLength)
  const description = read(descriptionLength)
  const payload = read(previewLength)
  const previewKind = [undefined, 'icon', 'audio', 'easing', 'dyeColor'][kind]
  const preview = previewKind && payload ? { kind: previewKind, value: payload } : undefined
  return { value: { id: which, title, description, preview }, next: p }
}

// 读取 [u32 数量]([u32 start][u32 end][u32 长度][u16 字符串][补齐到4])*
function readErrorReasons(basePtr) {
  let ptr = align4(basePtr)
  const count = readU32(ptr)
  ptr = count.next
  // 防御: 每条错误至少 12 字节定长头，阻止损坏的 count 触发巨大循环
  checkMemRange(ptr, count.value * 12, '错误数量')
  const errorReasons = []
  for (let i = 0; i < count.value; i++) {
    ptr = align4(ptr) // C++ 在每条记录末尾写入了补齐到 4 的 padding
    checkMemRange(ptr, 12, `错误记录 ${i}`)
    const start = wasmModule.HEAPU32[ptr >>> 2]
    const end = wasmModule.HEAPU32[(ptr + 4) >>> 2]
    const reason = readUtf16(ptr + 8)
    errorReasons.push({ start, end, errorReason: reason.value })
    ptr = reason.next
  }
  return errorReasons
}

// 读取 [u32 数量]([补全建议][补齐到4])*
function readSuggestions(basePtr) {
  let ptr = align4(basePtr)
  const count = readU32(ptr)
  ptr = count.next
  // 防御: 每条补全建议至少 16 字节定长头，阻止损坏的 count 触发巨大循环
  checkMemRange(ptr, count.value * 16, '补全建议数量')
  const suggestions = []
  for (let i = 0; i < count.value; i++) {
    ptr = align4(ptr) // C++ 在每条记录末尾写入了补齐到 4 的 padding
    const record = readSuggestionRecord(ptr, i)
    suggestions.push(record.value)
    ptr = record.next
  }
  return suggestions
}

// 软件内核，负责持有资源包并通过createContext创建命令上下文
// 所有和命令相关的功能都在CommandContext上执行
export class CHelperCore {
  constructor(cpack) {
    const cpackPtr = wasmModule._malloc(cpack.byteLength)
    wasmModule.HEAPU8.set(cpack, cpackPtr)
    this._corePtr = wasmModule._init(cpackPtr, cpack.byteLength)
    wasmModule._free(cpackPtr)
    if (this._corePtr === 0) {
      throw 'fail to init CHelper core'
    }
  }

  release() {
    wasmModule._release(this._corePtr)
    this._corePtr = 0
  }

  // 把命令文本解析成AST，生成独立的命令上下文
  createContext(command) {
    return new CommandContext(this._corePtr, command)
  }
}

// 读取 [u32 光标位置][u32 长度][u16 字符串]
function readClickSuggestionResult(ptr) {
  if (ptr === 0) {
    return null
  }
  const cursor = readU32(align4(ptr))
  const text = readUtf16(cursor.next)
  return {
    cursorPosition: cursor.value,
    newText: text.value,
  }
}

// 读取 [u32 数量][u8]*
function readSyntaxTokens(ptr) {
  if (ptr === 0) {
    return null
  }
  const count = readU32(align4(ptr))
  checkMemRange(count.next, count.value, '语法 token 数量')
  const syntaxTokens = []
  for (let i = 0; i < count.value; i++) {
    syntaxTokens.push(wasmModule.HEAPU8[count.next + i])
  }
  return syntaxTokens
}

// 命令上下文，持有某条命令解析好的AST
// 每个上下文在 WASM 中缓存最近一次光标位置的补全结果，供单线程 Web 接口复用。
export class CommandContext {
  // corePtr 是CHelperCore对应的指针，command 是命令文本
  constructor(corePtr, command) {
    const ptr = writeString(command)
    this._contextPtr = wasmModule._createCommandContext(corePtr, ptr)
    wasmModule._free(ptr)
    if (this._contextPtr === 0) {
      throw 'fail to create CommandContext'
    }
  }

  release() {
    wasmModule._releaseCommandContext(this._contextPtr)
    this._contextPtr = 0
  }

  // 获取这个上下文对应的命令文本
  getCommand() {
    const ptr = wasmModule._contextGetCommand(this._contextPtr)
    if (ptr === 0) {
      return ''
    }
    return readUtf16(align4(ptr)).value
  }

  // 获取命令结构
  getStructure() {
    const ptr = wasmModule._contextGetStructure(this._contextPtr)
    if (ptr === 0) {
      return ''
    }
    return readUtf16(align4(ptr)).value
  }

  // 获取指定位置的参数注释
  getParamHint(index) {
    const ptr = wasmModule._contextGetParamHint(this._contextPtr, index)
    if (ptr === 0) {
      return ''
    }
    return readUtf16(align4(ptr)).value
  }

  // 获取命令的错误原因
  getErrorReasons() {
    const ptr = wasmModule._contextGetErrorReasons(this._contextPtr)
    if (ptr === 0) {
      return []
    }
    return readErrorReasons(ptr)
  }

  // 获取指定位置的补全提示数量
  getSuggestionSize(index) {
    return wasmModule._contextGetSuggestionSize(this._contextPtr, index)
  }

  // 获取指定位置的其中一个补全提示
  getSuggestion(index, which) {
    const ptr = wasmModule._contextGetSuggestion(this._contextPtr, index, which)
    if (ptr === 0) {
      return null
    }
    return readSuggestionRecord(align4(ptr), which).value
  }

  // 获取指定位置的所有补全提示
  getAllSuggestions(index) {
    const ptr = wasmModule._contextGetAllSuggestions(this._contextPtr, index)
    if (ptr === 0) {
      return []
    }
    return readSuggestions(ptr)
  }

  // 把指定位置的其中一个补全提示应用到命令文本
  // 不会修改上下文持有的命令文本，同样的操作可以重复执行
  applySuggestion(index, which) {
    return readClickSuggestionResult(
      wasmModule._contextApplySuggestion(this._contextPtr, index, which),
    )
  }

  // 获取每个字符的token类型，用于语法高亮
  getSyntaxTokens() {
    return readSyntaxTokens(wasmModule._contextGetSyntaxTokens(this._contextPtr))
  }

  // 获取最佳解析路径中已经匹配的命令语义节点数量
  getNodeCount() {
    return wasmModule._contextGetNodeCount(this._contextPtr)
  }
}
