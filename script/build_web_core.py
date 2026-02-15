import os
import shutil
import subprocess


def ensure_download_emsdk(toolchain_dir: str):
    ndk_path = os.path.join(toolchain_dir, "emsdk")
    current_dir = os.getcwd()
    os.chdir(toolchain_dir)
    if not os.path.exists(ndk_path):
        subprocess.run(
            ["git", "clone", "https://github.com/emscripten-core/emsdk"], check=True
        )
        os.chdir("./emsdk")
    else:
        os.chdir("./emsdk")
        subprocess.run(["git", "pull"], check=True)
    subprocess.run(["python", "./emsdk.py", "install", "latest"], check=True)
    subprocess.run(["python", "./emsdk.py", "activate", "latest"], check=True)
    os.chdir(current_dir)


def build_web_core(toolchain_dir: str):
    emsdk_path = os.path.join(toolchain_dir, "emsdk")
    build_directory = "./build/web_core"
    subprocess.run(
        [
            "cmake",
            "-S",
            "./CHelper-Core",
            "-D",
            "CMAKE_BUILD_TYPE=MinSizeRel",
            "-D",
            f'CMAKE_TOOLCHAIN_FILE={emsdk_path}/upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake',
            "-B",
            build_directory,
            "-G",
            "Ninja",
        ],
        check=True,
    )
    subprocess.run(
        ["cmake", "--build", build_directory, "--target", "CHelperWeb"],
        check=True,
    )
    subprocess.run(
        [
            "python",
            os.path.join(emsdk_path, "upstream", "emscripten", "emcc.py"),
            f"{build_directory}/libCHelperWeb.a",
            f"{build_directory}/libCHelperNoFilesystemCore.a",
            f"{build_directory}/3rdparty/fmt/libfmt.a",
            f"{build_directory}/3rdparty/spdlog/libspdlog.a",
            f"{build_directory}/3rdparty/xxHash/cmake_unofficial/libxxhash.a",
            "-Os",
            "-o",
            f"{build_directory}/libCHelperWeb.js",
            "-s",
            "FILESYSTEM=0",
            "-s",
            "DISABLE_EXCEPTION_CATCHING=1",
            "-s",
            "ALLOW_MEMORY_GROWTH",
            "-s",
            'ENVIRONMENT=["web"]',
            "-s",
            "EXPORTED_FUNCTIONS=['_init','_release','_onTextChanged','_onSelectionChanged','_getParamHint','_getErrorReasons','_getSuggestionSize','_getSuggestion','_getAllSuggestions','_onSuggestionClick','_getSyntaxTokens','_malloc','_free']",
            "-s",
            "WASM=1",
            "-s",
            "EXPORTED_RUNTIME_METHODS=[]",
        ],
        check=True,
    )
    shutil.copyfile(
        os.path.join(build_directory, "libCHelperWeb.wasm"),
        os.path.join(".", "CHelper-Web", "src", "assets", "libCHelperWeb.wasm"),
    )
    shutil.copyfile(
        os.path.join(build_directory, "libCHelperWeb.js"),
        os.path.join(".", "CHelper-Web", "src", "core", "libCHelperWeb.js"),
    )
    with open(os.path.join(build_directory, "libCHelperWeb.js"), "r") as fp:
        content = fp.read()
        content = "import wasmUrl from '@/assets/libCHelperWeb.wasm?url'\n\n" + content
        content = content.replace('locateFile("libCHelperWeb.wasm")', "wasmUrl;")
        content = content.replace(
            "var wasmExports;createWasm();",
            "var wasmExports;export var createWasmFuture = createWasm();",
        )
        content += """
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
"""
    with open(
        os.path.join(".", "CHelper-Web", "src", "core", "libCHelperWeb.js"), "w"
    ) as fp:
        fp.write(content)


if __name__ == "__main__":
    # check toolchain
    if subprocess.run(["node", "-v"], capture_output=True).returncode != 0:
        print("please download nodejs")
        exit(-1)
    if subprocess.run(["cmake", "--version"], capture_output=True).returncode != 0:
        print("please download cmake")
        exit(-1)
    if subprocess.run(["ninja", "--version"], capture_output=True).returncode != 0:
        print("please download ninja")
        exit(-1)
    toolchain_dir = os.path.join(os.getcwd(), "toolchain")
    os.makedirs(toolchain_dir, exist_ok=True)
    ensure_download_emsdk(toolchain_dir)

    # build web core
    print("building web core...")
    build_web_core(toolchain_dir)
