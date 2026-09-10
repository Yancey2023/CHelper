import wasmUrl from '@/assets/libCHelperWeb.wasm?url'

var Module=typeof Module!="undefined"?Module:{};var ENVIRONMENT_IS_WEB=true;var ENVIRONMENT_IS_WORKER=false;var programArgs=[];var thisProgram="./this.program";var _scriptName=globalThis.document?.currentScript?.src;var scriptDirectory="";function locateFile(path){if(Module["locateFile"]){return Module["locateFile"](path,scriptDirectory)}return scriptDirectory+path}var readAsync,readBinary;if(ENVIRONMENT_IS_WEB||ENVIRONMENT_IS_WORKER){try{scriptDirectory=new URL(".",_scriptName).href}catch{}{readAsync=async url=>{var response=await fetch(url,{credentials:"same-origin"});if(response.ok){return response.arrayBuffer()}throw new Error(response.status+" : "+response.url)}}}else{}var out=console.log.bind(console);var err=console.error.bind(console);var wasmBinary;var ABORT=false;class EmscriptenEH{}class EmscriptenSjLj extends EmscriptenEH{}var runtimeInitialized=false;function getMemoryBuffer(){return wasmMemory.buffer}function updateMemoryViews(){if(HEAP8?.buffer?.resizable)return;var b=getMemoryBuffer();HEAP8=new Int8Array(b);HEAPU8=new Uint8Array(b);HEAP32=new Int32Array(b);HEAPU32=new Uint32Array(b)}function preRun(){var preRun=Module["preRun"];if(preRun){if(typeof preRun=="function")preRun=[preRun];onPreRuns.push(...preRun)}callRuntimeCallbacks(onPreRuns)}function initRuntime(){runtimeInitialized=true;wasmExports["g"]()}function postRun(){var postRun=Module["postRun"];if(postRun){if(typeof postRun=="function")postRun=[postRun];onPostRuns.push(...postRun)}callRuntimeCallbacks(onPostRuns)}function abort(what){Module["onAbort"]?.(what);what=`Aborted(${what})`;err(what);ABORT=true;what+=". Build with -sASSERTIONS for more info.";var e=new WebAssembly.RuntimeError(what);throw e}var wasmBinaryFile;function findWasmBinary(){return wasmUrl;}function getBinarySync(file){if(readBinary){return readBinary(file)}throw"both async and sync fetching of the wasm failed"}async function getWasmBinary(binaryFile){if(!wasmBinary){try{var response=await readAsync(binaryFile);return new Uint8Array(response)}catch{}}return getBinarySync(binaryFile)}async function instantiateArrayBuffer(binaryFile,imports){try{var binary=await getWasmBinary(binaryFile);var instance=await WebAssembly.instantiate(binary,imports);return instance}catch(reason){err(`failed to asynchronously prepare wasm: ${reason}`);abort(reason)}}async function instantiateAsync(binary,binaryFile,imports){if(!binary){try{var response=fetch(binaryFile,{credentials:"same-origin"});var instantiationResult=await WebAssembly.instantiateStreaming(response,imports);return instantiationResult}catch(reason){err(`wasm streaming compile failed: ${reason}`);err("falling back to ArrayBuffer instantiation")}}return instantiateArrayBuffer(binaryFile,imports)}function getWasmImports(){var imports={a:wasmImports};return imports}async function createWasm(){function receiveInstance(instance){wasmExports=instance.exports;assignWasmExports(wasmExports);updateMemoryViews();return wasmExports}function receiveInstantiationResult(result){return receiveInstance(result["instance"])}var info=getWasmImports();var instantiateWasm=Module["instantiateWasm"];if(instantiateWasm){return new Promise(resolve=>{instantiateWasm(info,inst=>resolve(receiveInstance(inst)))})}wasmBinaryFile??=findWasmBinary();var result=await instantiateAsync(wasmBinary,wasmBinaryFile,info);var exports=receiveInstantiationResult(result);return exports}class ExitStatus{name="ExitStatus";constructor(status){this.message=`Program terminated with exit(${status})`;this.status=status}}var HEAP8;var callRuntimeCallbacks=callbacks=>{while(callbacks.length>0){callbacks.shift()(Module)}};var onPostRuns=[];var onPreRuns=[];var noExitRuntime=true;var HEAPU32;class ExceptionInfo{constructor(excPtr){this.excPtr=excPtr;this.ptr=excPtr-24}set_type(type){HEAPU32[this.ptr+4>>2]=type}get_type(){return HEAPU32[this.ptr+4>>2]}set_destructor(destructor){HEAPU32[this.ptr+8>>2]=destructor}get_destructor(){return HEAPU32[this.ptr+8>>2]}set_caught(caught){caught=caught?1:0;HEAP8[this.ptr+12]=caught}get_caught(){return HEAP8[this.ptr+12]!=0}set_rethrown(rethrown){rethrown=rethrown?1:0;HEAP8[this.ptr+13]=rethrown}get_rethrown(){return HEAP8[this.ptr+13]!=0}init(type,destructor){this.set_adjusted_ptr(0);this.set_type(type);this.set_destructor(destructor)}set_adjusted_ptr(adjustedPtr){HEAPU32[this.ptr+16>>2]=adjustedPtr}get_adjusted_ptr(){return HEAPU32[this.ptr+16>>2]}}var uncaughtExceptionCount=0;var __Unwind_RaiseException=ex=>{abort()};var ___cxa_throw=(ptr,type,destructor)=>{var info=new ExceptionInfo(ptr);info.init(type,destructor);uncaughtExceptionCount++;__Unwind_RaiseException(ptr)};var __abort_js=()=>abort("");var stringToUTF8Array=(str,heap,outIdx,maxBytesToWrite)=>{if(!(maxBytesToWrite>0))return 0;var startIdx=outIdx;var endIdx=outIdx+maxBytesToWrite-1;for(var i=0;i<str.length;++i){var u=str.codePointAt(i);if(u<=127){if(outIdx>=endIdx)break;heap[outIdx++]=u}else if(u<=2047){if(outIdx+1>=endIdx)break;heap[outIdx++]=192|u>>6;heap[outIdx++]=128|u&63}else if(u<=65535){if(outIdx+2>=endIdx)break;heap[outIdx++]=224|u>>12;heap[outIdx++]=128|u>>6&63;heap[outIdx++]=128|u&63}else{if(outIdx+3>=endIdx)break;heap[outIdx++]=240|u>>18;heap[outIdx++]=128|u>>12&63;heap[outIdx++]=128|u>>6&63;heap[outIdx++]=128|u&63;i++}}heap[outIdx]=0;return outIdx-startIdx};var HEAPU8;var stringToUTF8=(str,outPtr,maxBytesToWrite)=>stringToUTF8Array(str,HEAPU8,outPtr,maxBytesToWrite);var HEAP32;var getHeapMax=()=>2147483648;var alignMemory=(size,alignment)=>Math.ceil(size/alignment)*alignment;var growMemory=size=>{var oldHeapSize=wasmMemory.buffer.byteLength;var pages=(size-oldHeapSize+65535)/65536|0;try{wasmMemory.grow(pages);updateMemoryViews();return 1}catch(e){}};var _emscripten_resize_heap=requestedSize=>{var oldSize=HEAPU8.length;requestedSize>>>=0;var maxHeapSize=getHeapMax();if(requestedSize>maxHeapSize){return false}for(var cutDown=1;cutDown<=4;cutDown*=2){var overGrownHeapSize=oldSize*(1+.2/cutDown);overGrownHeapSize=Math.min(overGrownHeapSize,requestedSize+100663296);var newSize=Math.min(maxHeapSize,alignMemory(Math.max(requestedSize,overGrownHeapSize),65536));var replacement=growMemory(newSize);if(replacement){return true}}return false};var ENV={};var getExecutableName=()=>thisProgram;var getEnvStrings=()=>{if(!getEnvStrings.strings){var lang=(globalThis.navigator?.language??"C").replace("-","_")+".UTF-8";var env={USER:"web_user",LOGNAME:"web_user",PATH:"/",PWD:"/",HOME:"/home/web_user",LANG:lang,_:getExecutableName()};for(var x in ENV){if(ENV[x]===undefined)delete env[x];else env[x]=ENV[x]}var strings=[];for(var x in env){strings.push(`${x}=${env[x]}`)}getEnvStrings.strings=strings}return getEnvStrings.strings};var _environ_get=(__environ,environ_buf)=>{var bufSize=0;var envp=0;for(var string of getEnvStrings()){var ptr=environ_buf+bufSize;HEAPU32[__environ+envp>>2]=ptr;bufSize+=stringToUTF8(string,ptr,Infinity)+1;envp+=4}return 0};var lengthBytesUTF8=str=>{var len=0;for(var i=0;i<str.length;++i){var c=str.charCodeAt(i);if(c<=127){len++}else if(c<=2047){len+=2}else if(c>=55296&&c<=57343){len+=4;++i}else{len+=3}}return len};var _environ_sizes_get=(penviron_count,penviron_buf_size)=>{var strings=getEnvStrings();HEAPU32[penviron_count>>2]=strings.length;var bufSize=0;for(var string of strings){bufSize+=lengthBytesUTF8(string)+1}HEAPU32[penviron_buf_size>>2]=bufSize;return 0};{if(Module["noExitRuntime"])noExitRuntime=Module["noExitRuntime"];if(Module["print"])out=Module["print"];if(Module["printErr"])err=Module["printErr"];if(Module["arguments"])programArgs=Module["arguments"];if(Module["thisProgram"])thisProgram=Module["thisProgram"];var preInit=Module["preInit"];if(preInit){if(typeof preInit=="function")Module["preInit"]=preInit=[preInit];while(preInit.length>0){preInit.shift()()}}}var _init,_release,_createCommandContext,_releaseCommandContext,_contextGetCommand,_contextGetStructure,_contextGetParamHint,_contextGetErrorReasons,_contextGetSuggestionSize,_contextGetSuggestion,_contextGetAllSuggestions,_contextApplySuggestion,_contextGetSyntaxTokens,_contextGetNodeCount,_malloc,_free,memory,__indirect_function_table,wasmMemory;function assignWasmExports(wasmExports){_init=Module["_init"]=wasmExports["h"];_release=Module["_release"]=wasmExports["i"];_createCommandContext=Module["_createCommandContext"]=wasmExports["j"];_releaseCommandContext=Module["_releaseCommandContext"]=wasmExports["k"];_contextGetCommand=Module["_contextGetCommand"]=wasmExports["l"];_contextGetStructure=Module["_contextGetStructure"]=wasmExports["m"];_contextGetParamHint=Module["_contextGetParamHint"]=wasmExports["n"];_contextGetErrorReasons=Module["_contextGetErrorReasons"]=wasmExports["o"];_contextGetSuggestionSize=Module["_contextGetSuggestionSize"]=wasmExports["p"];_contextGetSuggestion=Module["_contextGetSuggestion"]=wasmExports["q"];_contextGetAllSuggestions=Module["_contextGetAllSuggestions"]=wasmExports["r"];_contextApplySuggestion=Module["_contextApplySuggestion"]=wasmExports["s"];_contextGetSyntaxTokens=Module["_contextGetSyntaxTokens"]=wasmExports["t"];_contextGetNodeCount=Module["_contextGetNodeCount"]=wasmExports["u"];_malloc=Module["_malloc"]=wasmExports["v"];_free=Module["_free"]=wasmExports["w"];memory=wasmMemory=wasmExports["f"];__indirect_function_table=wasmExports["__indirect_function_table"]}var wasmImports={a:___cxa_throw,e:__abort_js,d:_emscripten_resize_heap,b:_environ_get,c:_environ_sizes_get};async function run(){preRun();var setStatus=Module["setStatus"];if(setStatus){setStatus("Running...");await new Promise(resolve=>setTimeout(resolve,1));setTimeout(setStatus,1,"")}if(ABORT)return;initRuntime();Module["onRuntimeInitialized"]?.();postRun()}var wasmExports;export var createWasmFuture = createWasm().then(()=>run());

// 内存视图可能因内存增长（ALLOW_MEMORY_GROWTH）而失效，每次读取前通过 HEAPU8 获取底层 buffer
let u16View = null
function getHEAPU16() {
  const buffer = HEAPU8.buffer
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
  if (start < 0 || end > HEAPU8.byteLength || end < start) {
    throw new Error(
      `Invalid WASM buffer: ${what} 越界 [${start}, ${end}), 内存大小 ${HEAPU8.byteLength}`,
    )
  }
}

// 读取 4 字节无符号整数，返回 { value, next }；ptr 必须 4 字节对齐（协议不变量）
function readU32(ptr) {
  if (ptr % 4 !== 0) {
    throw new Error(`Invalid WASM buffer: u32 读取位置未 4 字节对齐: ${ptr}`)
  }
  checkMemRange(ptr, 4, 'u32')
  return { value: HEAPU32[ptr >>> 2], next: ptr + 4 }
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
  const ptr = _malloc((content.length + 1) * 2)
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

// 读取一条补全建议记录 [u32 name长度][u32 description长度][u16 name][u16 description]（ptr 已 4 对齐）
// 返回 { value, next }，value 形如 { id, title, description }
function readSuggestionRecord(ptr, which) {
  checkMemRange(ptr, 8, `补全建议头部(记录 ${which})`)
  const nameLength = HEAPU32[ptr >>> 2]
  const descriptionLength = HEAPU32[(ptr + 4) >>> 2]
  checkMemRange(
    ptr + 8,
    (nameLength + descriptionLength) * 2,
    `补全建议数据(记录 ${which}, name=${nameLength}, description=${descriptionLength})`,
  )
  let title = ''
  let p = ptr + 8
  for (let i = 0; i < nameLength; i++) {
    title += String.fromCharCode(getHEAPU16()[p >> 1])
    p += 2
  }
  let description = ''
  for (let i = 0; i < descriptionLength; i++) {
    description += String.fromCharCode(getHEAPU16()[p >> 1])
    p += 2
  }
  return { value: { id: which, title, description }, next: p }
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
    const start = HEAPU32[ptr >>> 2]
    const end = HEAPU32[(ptr + 4) >>> 2]
    const reason = readUtf16(ptr + 8)
    errorReasons.push({ start, end, errorReason: reason.value })
    ptr = reason.next
  }
  return errorReasons
}

// 读取 [u32 数量]([u32 name长度][u32 description长度][u16 name][u16 description][补齐到4])*
function readSuggestions(basePtr) {
  let ptr = align4(basePtr)
  const count = readU32(ptr)
  ptr = count.next
  // 防御: 每条补全建议至少 8 字节定长头，阻止损坏的 count 触发巨大循环
  checkMemRange(ptr, count.value * 8, '补全建议数量')
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

  // 把命令文本解析成AST，生成独立的命令上下文
  // 适用于多线程并行的场景：可以创建任意多个CommandContext并行使用
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
    syntaxTokens.push(HEAPU8[count.next + i])
  }
  return syntaxTokens
}

// 命令上下文，持有某条命令解析好的AST
// 和CHelperCore不同，CommandContext没有可变状态，
// 所有操作都通过参数传入位置，因此可以把同一个CommandContext
// 交给多个线程同时读取，也可以创建多个CommandContext并行工作
export class CommandContext {
  // corePtr 是CHelperCore对应的指针，command 是命令文本
  constructor(corePtr, command) {
    const ptr = writeString(command)
    this._contextPtr = _createCommandContext(corePtr, ptr)
    _free(ptr)
    if (this._contextPtr === 0) {
      throw 'fail to create CommandContext'
    }
  }

  release() {
    _releaseCommandContext(this._contextPtr)
    this._contextPtr = 0
  }

  // 获取这个上下文对应的命令文本
  getCommand() {
    const ptr = _contextGetCommand(this._contextPtr)
    if (ptr === 0) {
      return ''
    }
    return readUtf16(align4(ptr)).value
  }

  // 获取命令结构
  getStructure() {
    const ptr = _contextGetStructure(this._contextPtr)
    if (ptr === 0) {
      return ''
    }
    return readUtf16(align4(ptr)).value
  }

  // 获取指定位置的参数注释
  getParamHint(index) {
    const ptr = _contextGetParamHint(this._contextPtr, index)
    if (ptr === 0) {
      return ''
    }
    return readUtf16(align4(ptr)).value
  }

  // 获取命令的错误原因
  getErrorReasons() {
    const ptr = _contextGetErrorReasons(this._contextPtr)
    if (ptr === 0) {
      return []
    }
    return readErrorReasons(ptr)
  }

  // 获取指定位置的补全提示数量
  getSuggestionSize(index) {
    return _contextGetSuggestionSize(this._contextPtr, index)
  }

  // 获取指定位置的其中一个补全提示
  getSuggestion(index, which) {
    const ptr = _contextGetSuggestion(this._contextPtr, index, which)
    if (ptr === 0) {
      return null
    }
    return readSuggestionRecord(align4(ptr), which).value
  }

  // 获取指定位置的所有补全提示
  getAllSuggestions(index) {
    const ptr = _contextGetAllSuggestions(this._contextPtr, index)
    if (ptr === 0) {
      return []
    }
    return readSuggestions(ptr)
  }

  // 把指定位置的其中一个补全提示应用到命令文本
  // 不会修改自身的状态，同样的操作可以重复执行
  applySuggestion(index, which) {
    return readClickSuggestionResult(_contextApplySuggestion(this._contextPtr, index, which))
  }

  // 获取每个字符的token类型，用于语法高亮
  getSyntaxTokens() {
    return readSyntaxTokens(_contextGetSyntaxTokens(this._contextPtr))
  }

  // 获取最佳解析路径中已经匹配的命令语义节点数量
  getNodeCount() {
    return _contextGetNodeCount(this._contextPtr)
  }
}
