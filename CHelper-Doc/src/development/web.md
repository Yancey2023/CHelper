# CHelper 网页接口 / JavaScript 接口文档

如果有不明白的地方，可以直接参考[CHelper 网页版](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Web)的内核对接方式。

## 构建 Web 客户端

从仓库根目录运行 `mise run web:build`。首次运行时，mise 会安装锁定版本的 emsdk，并由 emsdk 安装和激活对应的 Emscripten 工具链。WASM、配套 JavaScript 和 Emscripten 生成的 TypeScript 声明会放到 `build/web_core`，资源包会放到 `build/artifacts/resources/cpack`。单独生成内核可运行 `mise run core:wasm`，它会先准备生成声明所需的 TypeScript。启动开发服务器可运行 `mise run web:dev`。

单元测试使用 `mise run web:test`。首次运行浏览器测试前，在仓库根目录执行 `mise exec -- pnpm --filter @chelper/web exec playwright install chromium`，随后执行 `mise run web:e2e`。

## 与 JavaScript 代码交互

`src/core/libCHelperWeb.ts` 是内核适配器，包含实现和类型。它加载同次构建的 Emscripten 模块和 WASM，并提供以下接口。

```js
export class CHelperCore {
  constructor(cpack) {
    // 构造函数
  }

  release() {
    // 释放内存
  }

  createContext(command) {
    // 把命令文本解析成AST，生成独立的命令上下文
  }
}

export class CommandContext {
  release() {
    // 释放内存
  }

  getCommand() {
    // 获取这个上下文对应的命令文本
  }

  getStructure() {
    // 获取命令结构
  }

  getParamHint(index) {
    // 获取指定位置的参数注释
  }

  getErrorReasons() {
    // 获取错误原因
  }

  getSuggestionSize(index) {
    // 获取指定位置的补全提示数量
  }

  getSuggestion(index, which) {
    // 获取指定位置的其中一个补全提示
  }

  getAllSuggestions(index) {
    // 获取指定位置的所有补全提示
  }

  applySuggestion(index, which) {
    // 把指定位置的其中一个补全提示应用到命令文本，不修改自身状态
  }

  getSyntaxTokens() {
    // 获取每个字符的token类型，用于语法高亮
  }

  getNodeCount() {
    // 获取最佳解析路径中已经匹配的命令语义节点数量
  }
}
```

在 `src/core` 目录下，`CPackManager.ts` 并行获取资源包和初始化 WASM。两者就绪后，`getCore` 使用资源包创建内核。并发调用共享同一次 WASM 初始化，初始化失败后下次调用可以重试。

获取到内核后，即可去调用内核的各种接口了。需要注意的是，在内核要被销毁的时候，记得调用`release()`函数释放内存。

命令相关功能都在 `CommandContext` 上执行。通过 `createContext(command)` 把命令文本解析成 AST，生成独立的命令上下文。

```js
const core = await getCore(DEFAULT_BRANCH);
const context = core.createContext('give @s stone 12 1');
console.log(context.getStructure());
console.log(context.getParamHint(8));
console.log(context.getAllSuggestions(context.getCommand().length));
console.log(context.getSyntaxTokens());
context.release();
```

Web 接口在同一个线程上使用。文本改变时调用 `createContext`，光标改变时用新的位置查询。每个上下文在 WASM 内保留最近一个光标位置的补全结果，计数、按索引读取和应用补全共用该结果，上下文释放时一并清理。
