# CHelper

I want to make a command helper for Minecraft Bedrock Edition, so here is CHelper, which means Command Helper.

## Clone Project

```bash
git clone --recursive https://github.com/Yancey2023/CHelper-Core
# if you clone it without --recursive, you should run the following:
cd CHelper-Core
git submodule update --init --recursive --progress
```

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

CHelper-Web: <https://yancey2023.github.io/chelper_web>

CHelper-Web lacks some function. To experience more complete functions, please use [CHelper-Android](https://github.com/Yancey2023/CHelper-Android).

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

see: <https://yancey2023.github.io/CHelper-Doc/>

## Third-party

### CHelper-Core

|                      project                      |                   description                   |                                   license                                   |
| :-----------------------------------------------: | :---------------------------------------------: | :-------------------------------------------------------------------------: |
|       [fmt](https://github.com/fmtlib/fmt)        |               formatting library                |      [MIT license](https://github.com/fmtlib/fmt/blob/master/LICENSE)       |
|    [spdlog](https://github.com/gabime/spdlog)     |            Fast C++ logging library             |      [MIT license](https://github.com/gabime/spdlog/blob/v1.x/LICENSE)      |
| [rapidjson](https://github.com/Tencent/rapidjson) |       a JSON parser and generator for C++       | [MIT license](https://github.com/Tencent/rapidjson/blob/master/license.txt) |
|    [utfcpp](https://github.com/nemtrif/utfcpp)    |        UTF-8 with C++ in a Portable Way         |  [BSL-1.0 license](https://github.com/nemtrif/utfcpp/blob/master/LICENSE)   |
|   [xxHash](https://github.com/Cyan4973/xxHash)    | Extremely fast non-cryptographic hash algorithm |    [BSD-2-Clause ](https://github.com/Cyan4973/xxHash/blob/dev/LICENSE)     |

### CHelper-Qt

|         project          |          description          |                     license                      |
| :----------------------: | :---------------------------: | :----------------------------------------------: |
| [Qt](https://www.qt.io/) | desktop application framework | [LGPL license](https://doc.qt.io/qt-6/lgpl.html) |

### CHelper-Android

|                 project                  |          description           |                           license                            |
| :--------------------------------------: | :----------------------------: | :----------------------------------------------------------: |
| [NDK](https://developer.android.com/ndk) | android native development kit | [Apache License](http://www.apache.org/licenses/LICENSE-2.0) |

### CHelper-Web

|               project                |                description                 |                                                                  license                                                                   |
| :----------------------------------: | :----------------------------------------: | :----------------------------------------------------------------------------------------------------------------------------------------: |
| [Emscripten](https://emscripten.org) | complete compiler toolchain to WebAssembly | [the MIT license and the University of Illinois/NCSA Open Source License](https://github.com/emscripten-core/emscripten/blob/main/LICENSE) |

### CHelper-Test

|                      project                       |         description         |                                              license                                              |
| :------------------------------------------------: | :-------------------------: | :-----------------------------------------------------------------------------------------------: |
| [GoogleTest](https://github.com/google/googletest) | Google's C++ test framework | [BSD 3-Clause "New" or "Revised" License](https://github.com/google/googletest/blob/main/LICENSE) |

## Special Thanks

|                       project                       |                        description                         |                                   license                                    |
| :-------------------------------------------------: | :--------------------------------------------------------: | :--------------------------------------------------------------------------: |
|  [caidlist](https://github.com/XeroAlpha/caidlist)  | provide IDs and descriptions for Minecraft Bedrock Edition | [GPL-3.0 license](https://github.com/XeroAlpha/caidlist/blob/master/LICENSE) |
| [chinese minecraft wiki](https://zh.minecraft.wiki) |              provide information in Minecraft              |     [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0)     |

- JetBrains License for Open Source Development

  ![JetBrains Logo (Main) logo](https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg)
