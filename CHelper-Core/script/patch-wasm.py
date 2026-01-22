from os import path

buildDirs = ['cmake-build-emscripten-release', 'cmake-build-emscripten-minsizerel']


def insert(content: str, before: str, end: str, string: str) -> str:
    return content.replace(before + end, before + string + end)


for buildDir in buildDirs:
    with open(path.join(buildDir, 'libCHelperWeb.js'), 'r') as fp:
        content = fp.read()
    content = 'import wasmUrl from \'@/assets/libCHelperWeb.wasm?url\'\n\n' + content.replace('locateFile("libCHelperWeb.wasm")', 'wasmUrl;')
    content = insert(
        content,
        'var wasmExports;',
        'createWasm();',
        'export var createWasmFuture = '
    )
    content += ('''
export class CHelperCore {
  constructor(cpack) {
    const cpackPtr = _malloc(cpack.byteLength)
    HEAP8.set(cpack, cpackPtr)
    this._corePtr = _init(cpackPtr, cpack.byteLength)
    _free(cpackPtr)
    if (this._corePtr === 0) {
      throw 'fail to init CHelper core'
    }
  }

  release() {
    _release(this._corePtr)
    this._corePtr = 0
  }

  onTextChanged(content, index) {
    const ptr = _malloc((content.length + 1) * 2)
    const start = ptr / 2
    const end = start + content.length
    let i = start
    while (i < end) {
      HEAPU16[i] = content.charCodeAt(i - start)
      ++i
    }
    HEAPU16[i] = 0
    _onTextChanged(this._corePtr, ptr, index)
    _free(ptr)
  }

  onSelectionChanged(index) {
    _onSelectionChanged(this._corePtr, index)
  }

  getStructure() {
    let ptr = _getStructure(this._corePtr)
    if (ptr === 0) {
      return ''
    }
    ptr += ptr % 4
    const length = HEAPU32[ptr >> 2]
    ptr += 4
    let structure = ''
    for (let i = 0; i < length; i++) {
      structure += String.fromCharCode(HEAPU16[ptr >> 1])
      ptr += 2
    }
    return structure
  }

  getParamHint() {
    let ptr = _getParamHint(this._corePtr)
    if (ptr === 0) {
      return ''
    }
    ptr += ptr % 4
    const length = HEAPU32[ptr >> 2]
    ptr += 4
    let description = ''
    for (let i = 0; i < length; i++) {
      description += String.fromCharCode(HEAPU16[ptr >> 1])
      ptr += 2
    }
    return description
  }

  getErrorReasons() {
    let ptr = _getErrorReasons(this._corePtr)
    if (ptr === 0) {
      return []
    }
    ptr += ptr % 4
    const length = HEAPU32[ptr >> 2]
    ptr += 4
    let errorReasons = []
    for (let i = 0; i < length; i++) {
      ptr += ptr % 4
      const start = HEAPU32[ptr >> 2]
      ptr += 4
      const end = HEAPU32[ptr >> 2]
      ptr += 4
      const errorReasonLength = HEAPU32[ptr >> 2]
      ptr += 4
      let errorReason = ''
      for (let i = 0; i < errorReasonLength; i++) {
        errorReason += String.fromCharCode(HEAPU16[ptr >> 1])
        ptr += 2
      }
      errorReasons.push({
        start,
        end,
        errorReason,
      })
    }
    return errorReasons
  }

  getSuggestionSize() {
    return _getSuggestionSize(this._corePtr)
  }

  getSuggestion(which) {
    let ptr = _getSuggestion(this._corePtr, which)
    if (ptr === 0) {
      return null
    }
    ptr += ptr % 4
    const titleLength = HEAPU32[ptr >> 2]
    ptr += 4
    const descriptionLength = HEAPU32[ptr >> 2]
    ptr += 4
    let title = ''
    for (let i = 0; i < titleLength; i++) {
      title += String.fromCharCode(HEAPU16[ptr >> 1])
      ptr += 2
    }
    let description = ''
    for (let i = 0; i < descriptionLength; i++) {
      description += String.fromCharCode(HEAPU16[ptr >> 1])
      ptr += 2
    }
    return {
      id: which,
      title,
      description,
    }
  }

  getAllSuggestions() {
    let ptr = _getAllSuggestions(this._corePtr)
    if (ptr === 0) {
      return []
    }
    ptr += ptr % 4
    const length = HEAPU32[ptr >> 2]
    ptr += 4
    let suggestions = []
    for (let i = 0; i < length; i++) {
      ptr += ptr % 4
      const titleLength = HEAPU32[ptr >> 2]
      ptr += 4
      const descriptionLength = HEAPU32[ptr >> 2]
      ptr += 4
      let title = ''
      for (let i = 0; i < titleLength; i++) {
        title += String.fromCharCode(HEAPU16[ptr >> 1])
        ptr += 2
      }
      let description = ''
      for (let i = 0; i < descriptionLength; i++) {
        description += String.fromCharCode(HEAPU16[ptr >> 1])
        ptr += 2
      }
      suggestions.push({
        id: i,
        title,
        description,
      })
    }
    return suggestions
  }

  onSuggestionClick(which) {
    let ptr = _onSuggestionClick(this._corePtr, which)
    if (ptr === 0) {
      return null
    }
    ptr += ptr % 4
    const cursorPosition = HEAPU32[ptr >> 2]
    ptr += 4
    const length = HEAPU32[ptr >> 2]
    ptr += 4
    let newText = ''
    for (let i = 0; i < length; i++) {
      newText += String.fromCharCode(HEAPU16[ptr >> 1])
      ptr += 2
    }
    return {
      cursorPosition,
      newText,
    }
  }

  getSyntaxTokens() {
    let ptr = _getSyntaxTokens(this._corePtr)
    if (ptr === 0) {
      return null
    }
    ptr += ptr % 4
    const length = HEAPU32[ptr >> 2]
    ptr += 4
    let syntaxTokens = []
    for (let i = 0; i < length; i++) {
      syntaxTokens.push(HEAPU8[ptr])
      ptr += 1
    }
    return syntaxTokens
  }
}
''')
    with open(path.join(buildDir, 'libCHelperWeb.js'), 'w') as fp:
        fp.write(content)
