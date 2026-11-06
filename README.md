# CHelper

I want to make a command helper for Minecraft Bedrock Edition, so here is CHelper, which means Command Helper.

## Multi-platform

|               project                |                                   description                                    |
| :----------------------------------: | :------------------------------------------------------------------------------: |
|    [CHelper-Core](./CHelper-Core)    |               CHelper-Core is the core of CHelper, written in c++                |
| [CHelper-Android](./CHelper-Android) |                CHelper-Android is the Android version of the app                 |
|     [CHelper-Web](./CHelper-Web)     |                    CHelper-Web is the web version of the app                     |
|      [CHelper-Qt](./CHelper-Qt)      | maintain in [CHelper-Core](./CHelper-Core) for development and debugging purpose |

## Usage

### Help in editing command

When you are editing command, the app will show you command structure, param hint, command structure, and auto suggestions. It also supports syntax highlight.

### Help in converting old version command to new version command

Dur to the Minecraft updates, some command has been changed.

It supports convert block data value to block state, and so on.

old command example:

```mcfunction
execute @a ~ ~-1 ~ detect ~ ~-1 ~ stone 2 setblock ~ ~ ~ command_block 2
```

new command example:

```mcfunction
execute as @a at @s positioned ~ ~-1 ~ if block ~ ~-1 ~ stone["stone_type"="granite_smooth"] run setblock ~ ~ ~ command_block["facing_direction"=2,"conditional_bit"=false]
```

## Try it

> Note: This app is mainly aimed at Chinese users, so the language is Chinese. You can help me support more languages.

CHelper-Web: <https://yancey2023.github.io/CHelper/>

CHelper-Web lacks some function. To experience more complete functions, please use [CHelper-Android](./CHelper-Android).

## Plan

The desired function has been completed, and the new function to be developed is yet to be determined.

- [x] **CPack** - a resource pack containing IDs and commands
- [x] **Lexer** - transforms command strings into a token list
- [x] **Parser** - builds an Abstract Syntax Tree (AST) and find structural errors
- [x] **Linter** - finds errors based on the AST
- [x] **Description** - get the description of the currently written command param based on the AST
- [x] **Auto Suggestion** - get auto completion suggestions based on the AST
- [x] **Command Structure** - get command structure string based on the AST
- [x] **Old 2 New** - convert old command to new command
- [x] **Syntax Highlight** - get the syntax tokens for syntax highlight

## Document

see: <https://www.yanceymc.cn/chelper_doc/>

## Third-party

The following lists the main libraries, assets, and development tools used by each module. Versions are declared in [CMake](./CHelper-Core/CMakeLists.txt), the [Android version catalog](./CHelper-Android/gradle/libs.versions.toml), and the [pnpm workspace catalog](./pnpm-workspace.yaml). Each project retains its own license.

### CHelper-Core

| project                                                        | description                        | license                                                              |
| :------------------------------------------------------------- | :--------------------------------- | :------------------------------------------------------------------- |
| [fmt](https://github.com/fmtlib/fmt)                           | C++ formatting                     | [MIT](https://github.com/fmtlib/fmt/blob/HEAD/LICENSE)               |
| [spdlog](https://github.com/gabime/spdlog)                     | C++ logging                        | [MIT](https://github.com/gabime/spdlog/blob/HEAD/LICENSE)            |
| [glaze](https://github.com/stephenberry/glaze)                 | JSON and MessagePack serialization | [MIT](https://github.com/stephenberry/glaze/blob/HEAD/LICENSE)       |
| [unordered_dense](https://github.com/martinus/unordered_dense) | Hash maps and sets                 | [MIT](https://github.com/martinus/unordered_dense/blob/HEAD/LICENSE) |
| [utfcpp](https://github.com/nemtrif/utfcpp)                    | UTF-8 conversion and validation    | [BSL-1.0](https://github.com/nemtrif/utfcpp/blob/HEAD/LICENSE)       |
| [xxHash](https://github.com/Cyan4973/xxHash)                   | Non-cryptographic hashing          | [BSD-2-Clause](https://github.com/Cyan4973/xxHash/blob/HEAD/LICENSE) |

Native tests and benchmarks additionally use [GoogleTest](https://github.com/google/googletest) ([BSD-3-Clause](https://github.com/google/googletest/blob/HEAD/LICENSE)). The optional desktop debugging application uses [Qt](https://www.qt.io/) ([LGPL-3.0 / GPL-3.0 / commercial licensing](https://doc.qt.io/qt/licensing.html)). These development dependencies are not part of the Android or WASM builds.

### CHelper-Android

The Android application includes CHelper-Core and its runtime libraries above, together with the following libraries and assets. Kotlin, Coroutines, Okio, and Brotli include runtime dependencies brought in by the application libraries.

| project                                                                  | description                                                                      | license                                                                             |
| :----------------------------------------------------------------------- | :------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------- |
| [AndroidX](https://github.com/androidx/androidx)                         | AppCompat, Activity, Compose, RecyclerView, Navigation, DataStore, and Lifecycle | [Apache-2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt)   |
| [Kotlin](https://github.com/JetBrains/kotlin)                            | Kotlin standard library                                                          | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/HEAD/license/LICENSE.txt)     |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)       | Coroutines and asynchronous streams                                              | [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)  |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Kotlin serialization                                                             | [Apache-2.0](https://github.com/Kotlin/kotlinx.serialization/blob/HEAD/LICENSE.txt) |
| [Coil](https://github.com/coil-kt/coil)                                  | Compose image loading and OkHttp integration                                     | [Apache-2.0](https://github.com/coil-kt/coil/blob/HEAD/LICENSE.txt)                 |
| [OkHttp](https://github.com/square/okhttp)                               | HTTP client, logging interceptor, and Brotli integration                         | [Apache-2.0](https://github.com/square/okhttp/blob/HEAD/LICENSE.txt)                |
| [Okio](https://github.com/square/okio)                                   | I/O for OkHttp and Coil                                                          | [Apache-2.0](https://github.com/square/okio/blob/HEAD/LICENSE.txt)                  |
| [Brotli](https://github.com/google/brotli)                               | Brotli decompression used by OkHttp                                              | [MIT](https://github.com/google/brotli/blob/master/LICENSE)                         |
| [Retrofit](https://github.com/square/retrofit)                           | HTTP APIs and kotlinx.serialization converter                                    | [Apache-2.0](https://github.com/square/retrofit/blob/HEAD/LICENSE.txt)              |
| [DeviceCompat](https://github.com/getActivity/DeviceCompat)              | Device compatibility                                                             | [Apache-2.0](https://github.com/getActivity/DeviceCompat/blob/HEAD/LICENSE)         |
| [XXPermissions](https://github.com/getActivity/XXPermissions)            | Android permissions                                                              | [Apache-2.0](https://github.com/getActivity/XXPermissions/blob/HEAD/LICENSE)        |
| [Toaster](https://github.com/getActivity/Toaster)                        | Toast notifications                                                              | [Apache-2.0](https://github.com/getActivity/Toaster/blob/HEAD/LICENSE)              |
| [EasyWindow](https://github.com/getActivity/EasyWindow)                  | Floating windows                                                                 | [Apache-2.0](https://github.com/getActivity/EasyWindow/blob/HEAD/LICENSE)           |
| [tabler-icons](https://github.com/tabler/tabler-icons)                   | SVG icons                                                                        | [MIT](https://github.com/tabler/tabler-icons/blob/main/LICENSE)                     |
| [Crunch](https://github.com/boxbeam/Crunch)                              | Expression compiler and evaluator                                                | [MIT](https://github.com/boxbeam/Crunch/blob/HEAD/LICENSE)                          |

Local unit tests additionally use [JUnit 4](https://github.com/junit-team/junit4) ([EPL-1.0](https://github.com/junit-team/junit4/blob/HEAD/LICENSE-junit.txt)); instrumentation tests use AndroidX Test. JUnit's EPL-1.0 notice is also retained in the Android application's open source terms.

The application displays its bundled notices from [open_source_terms.txt](./CHelper-Android/app/src/main/assets/about/open_source_terms.txt). Release and beta builds also use the vendor-provided [UMeng SDKs](https://www.umeng.com/); their terms and the application's [privacy policy](./CHelper-Android/app/src/main/assets/about/privacy_policy.txt) apply separately.

### CHelper-Web and CHelper-Doc

| project                                                               | description                      | license                                                                       |
| :-------------------------------------------------------------------- | :------------------------------- | :---------------------------------------------------------------------------- |
| [Vue](https://github.com/vuejs/core)                                  | Web UI                           | [MIT](https://github.com/vuejs/core/blob/main/LICENSE)                        |
| [markdown-it](https://github.com/markdown-it/markdown-it)             | Documentation Markdown rendering | [MIT](https://github.com/markdown-it/markdown-it/blob/master/LICENSE)         |
| [VitePress](https://github.com/vuejs/vitepress)                       | Documentation site               | [MIT](https://github.com/vuejs/vitepress/blob/main/LICENSE)                   |
| [Vite](https://github.com/vitejs/vite)                                | Development server and bundler   | [MIT](https://github.com/vitejs/vite/blob/main/LICENSE)                       |
| [vite-plugin-vue](https://github.com/vitejs/vite-plugin-vue)          | Vue integration for Vite         | [MIT](https://github.com/vitejs/vite-plugin-vue/blob/main/LICENSE)            |
| [TypeScript](https://github.com/microsoft/TypeScript)                 | Type checking                    | [Apache-2.0](https://github.com/microsoft/TypeScript/blob/main/LICENSE.txt)   |
| [Vue language-tools](https://github.com/vuejs/language-tools)         | vue-tsc type checking            | [MIT](https://github.com/vuejs/language-tools/blob/master/LICENSE)            |
| [Prettier](https://github.com/prettier/prettier)                      | Formatting                       | [MIT](https://github.com/prettier/prettier/blob/main/LICENSE)                 |
| [Vitest](https://github.com/vitest-dev/vitest)                        | Unit tests                       | [MIT](https://github.com/vitest-dev/vitest/blob/main/LICENSE)                 |
| [Vue Test Utils](https://github.com/vuejs/test-utils)                 | Component tests                  | [MIT](https://github.com/vuejs/test-utils/blob/main/LICENSE)                  |
| [jsdom](https://github.com/jsdom/jsdom)                               | DOM test environment             | [MIT](https://github.com/jsdom/jsdom/blob/main/LICENSE.txt)                   |
| [Playwright](https://github.com/microsoft/playwright)                 | Browser tests                    | [Apache-2.0](https://github.com/microsoft/playwright/blob/main/LICENSE)       |
| [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped) | Node.js type definitions         | [MIT](https://github.com/DefinitelyTyped/DefinitelyTyped/blob/master/LICENSE) |

## Special Thanks

|                       project                       |                        description                         |                                   license                                    |
| :-------------------------------------------------: | :--------------------------------------------------------: | :--------------------------------------------------------------------------: |
|  [caidlist](https://github.com/XeroAlpha/caidlist)  | provide IDs and descriptions for Minecraft Bedrock Edition | [GPL-3.0 license](https://github.com/XeroAlpha/caidlist/blob/master/LICENSE) |
| [chinese minecraft wiki](https://zh.minecraft.wiki) |              provide information in Minecraft              |     [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0)     |

- JetBrains License for Open Source Development

  ![JetBrains Logo (Main) logo](https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg)
