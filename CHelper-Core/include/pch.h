/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#pragma once

#ifndef CHELPER_PCH_H
#define CHELPER_PCH_H

#include <ParamDeliver.h>

#if _CHELPER_DEBUG == true
#define CHelperDebug
#endif

#ifdef CHELPER_NO_FILESYSTEM
#define SPDLOG_ACTIVE_LEVEL SPDLOG_LEVEL_OFF
#else
#define SPDLOG_ACTIVE_LEVEL SPDLOG_LEVEL_INFO
#endif

#if defined(__ANDROID__) || defined(CHELPER_NO_FILESYSTEM)
#define FORMAT_ARG(arg) arg
#else
#define FORMAT_ARG(arg) fmt::styled(arg, fg(fmt::color::medium_purple))
#endif

#if defined(_MSC_VER) && !defined(__clang__)
#define CHELPER_UNREACHABLE() __assume(false)
#else
#define CHELPER_UNREACHABLE() __builtin_unreachable()
#endif

// 宏展开工具：CHELPER_PASTE(func, v1, v2, ...) 展开为 func(v1) func(v2) ...
#define CHELPER_EXPAND(x) x
#define CHELPER_GET_MACRO(_1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, _20, \
                          _21, _22, _23, _24, _25, _26, _27, _28, _29, _30, _31, _32, _33, _34, _35, _36, _37, _38,  \
                          _39, _40, _41, _42, _43, _44, _45, _46, _47, _48, NAME, ...)                               \
    NAME
#define CHELPER_PASTE(...)                                                                               \
    CHELPER_EXPAND(CHELPER_GET_MACRO(__VA_ARGS__, CHELPER_PASTE48, CHELPER_PASTE47, CHELPER_PASTE46,     \
                                     CHELPER_PASTE45, CHELPER_PASTE44, CHELPER_PASTE43, CHELPER_PASTE42, \
                                     CHELPER_PASTE41, CHELPER_PASTE40, CHELPER_PASTE39, CHELPER_PASTE38, \
                                     CHELPER_PASTE37, CHELPER_PASTE36, CHELPER_PASTE35, CHELPER_PASTE34, \
                                     CHELPER_PASTE33, CHELPER_PASTE32, CHELPER_PASTE31, CHELPER_PASTE30, \
                                     CHELPER_PASTE29, CHELPER_PASTE28, CHELPER_PASTE27, CHELPER_PASTE26, \
                                     CHELPER_PASTE25, CHELPER_PASTE24, CHELPER_PASTE23, CHELPER_PASTE22, \
                                     CHELPER_PASTE21, CHELPER_PASTE20, CHELPER_PASTE19, CHELPER_PASTE18, \
                                     CHELPER_PASTE17, CHELPER_PASTE16, CHELPER_PASTE15, CHELPER_PASTE14, \
                                     CHELPER_PASTE13, CHELPER_PASTE12, CHELPER_PASTE11, CHELPER_PASTE10, \
                                     CHELPER_PASTE9, CHELPER_PASTE8, CHELPER_PASTE7, CHELPER_PASTE6,     \
                                     CHELPER_PASTE5, CHELPER_PASTE4, CHELPER_PASTE3, CHELPER_PASTE2)(__VA_ARGS__))
#define CHELPER_PASTE2(func, v1) func(v1)
#define CHELPER_PASTE3(func, v1, v2) CHELPER_PASTE2(func, v1) CHELPER_PASTE2(func, v2)
#define CHELPER_PASTE4(func, v1, v2, v3) CHELPER_PASTE2(func, v1) CHELPER_PASTE3(func, v2, v3)
#define CHELPER_PASTE5(func, v1, v2, v3, v4) CHELPER_PASTE2(func, v1) CHELPER_PASTE4(func, v2, v3, v4)
#define CHELPER_PASTE6(func, v1, v2, v3, v4, v5) CHELPER_PASTE2(func, v1) CHELPER_PASTE5(func, v2, v3, v4, v5)
#define CHELPER_PASTE7(func, v1, v2, v3, v4, v5, v6) CHELPER_PASTE2(func, v1) CHELPER_PASTE6(func, v2, v3, v4, v5, v6)
#define CHELPER_PASTE8(func, v1, v2, v3, v4, v5, v6, v7) \
    CHELPER_PASTE2(func, v1)                             \
    CHELPER_PASTE7(func, v2, v3, v4, v5, v6, v7)
#define CHELPER_PASTE9(func, v1, v2, v3, v4, v5, v6, v7, v8) \
    CHELPER_PASTE2(func, v1)                                 \
    CHELPER_PASTE8(func, v2, v3, v4, v5, v6, v7, v8)
#define CHELPER_PASTE10(func, v1, v2, v3, v4, v5, v6, v7, v8, v9) \
    CHELPER_PASTE2(func, v1)                                      \
    CHELPER_PASTE9(func, v2, v3, v4, v5, v6, v7, v8, v9)
#define CHELPER_PASTE11(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10) \
    CHELPER_PASTE2(func, v1)                                           \
    CHELPER_PASTE10(func, v2, v3, v4, v5, v6, v7, v8, v9, v10)
#define CHELPER_PASTE12(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11) \
    CHELPER_PASTE2(func, v1)                                                \
    CHELPER_PASTE11(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11)
#define CHELPER_PASTE13(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12) \
    CHELPER_PASTE2(func, v1)                                                     \
    CHELPER_PASTE12(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12)
#define CHELPER_PASTE14(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13) \
    CHELPER_PASTE2(func, v1)                                                          \
    CHELPER_PASTE13(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13)
#define CHELPER_PASTE15(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14) \
    CHELPER_PASTE2(func, v1)                                                               \
    CHELPER_PASTE14(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14)
#define CHELPER_PASTE16(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15) \
    CHELPER_PASTE2(func, v1)                                                                    \
    CHELPER_PASTE15(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15)
#define CHELPER_PASTE17(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16) \
    CHELPER_PASTE2(func, v1)                                                                         \
    CHELPER_PASTE16(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16)
#define CHELPER_PASTE18(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17) \
    CHELPER_PASTE2(func, v1)                                                                              \
    CHELPER_PASTE17(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17)
#define CHELPER_PASTE19(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18) \
    CHELPER_PASTE2(func, v1)                                                                                   \
    CHELPER_PASTE18(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18)
#define CHELPER_PASTE20(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19) \
    CHELPER_PASTE2(func, v1)                                                                                        \
    CHELPER_PASTE19(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19)
#define CHELPER_PASTE21(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20) \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE20(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20)
#define CHELPER_PASTE22(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21)                                                                                             \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE21(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21)
#define CHELPER_PASTE23(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22)                                                                                        \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE22(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21, v22)
#define CHELPER_PASTE24(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23)                                                                                   \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE23(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23)
#define CHELPER_PASTE25(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24)                                                                              \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE24(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24)
#define CHELPER_PASTE26(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25)                                                                         \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE25(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25)
#define CHELPER_PASTE27(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26)                                                                    \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE26(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26)
#define CHELPER_PASTE28(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27)                                                               \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE27(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27)
#define CHELPER_PASTE29(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28)                                                          \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE28(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28)
#define CHELPER_PASTE30(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29)                                                     \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE29(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29)
#define CHELPER_PASTE31(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30)                                                \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE30(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30)
#define CHELPER_PASTE32(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31)                                           \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE31(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31)
#define CHELPER_PASTE33(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32)                                      \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE32(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32)
#define CHELPER_PASTE34(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33)                                 \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE33(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33)
#define CHELPER_PASTE35(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34)                            \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE34(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34)
#define CHELPER_PASTE36(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35)                       \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE35(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35)
#define CHELPER_PASTE37(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36)                  \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE36(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36)
#define CHELPER_PASTE38(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37)             \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE37(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37)
#define CHELPER_PASTE39(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38)        \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE38(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38)
#define CHELPER_PASTE40(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39)   \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE39(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39)
#define CHELPER_PASTE41(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40)                                                                                             \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE40(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40)
#define CHELPER_PASTE42(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40, v41)                                                                                        \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE41(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40, v41)
#define CHELPER_PASTE43(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40, v41, v42)                                                                                   \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE42(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40, v41,  \
                    v42)
#define CHELPER_PASTE44(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40, v41, v42, v43)                                                                              \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE43(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40, v41,  \
                    v42, v43)
#define CHELPER_PASTE45(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40, v41, v42, v43, v44)                                                                         \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE44(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40, v41,  \
                    v42, v43, v44)
#define CHELPER_PASTE46(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40, v41, v42, v43, v44, v45)                                                                    \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE45(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40, v41,  \
                    v42, v43, v44, v45)
#define CHELPER_PASTE47(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40, v41, v42, v43, v44, v45, v46)                                                               \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE46(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40, v41,  \
                    v42, v43, v44, v45, v46)
#define CHELPER_PASTE48(func, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, \
                        v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39,   \
                        v40, v41, v42, v43, v44, v45, v46, v47)                                                          \
    CHELPER_PASTE2(func, v1)                                                                                             \
    CHELPER_PASTE47(func, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21,    \
                    v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34, v35, v36, v37, v38, v39, v40, v41,  \
                    v42, v43, v44, v45, v46, v47)

// 数据结构
#include <algorithm>
#include <array>
#include <cmath>
#include <functional>
#include <optional>
#include <sstream>
#include <stack>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <variant>
#include <vector>
// 抛出的错误
#include <exception>
// 文件读写
#ifndef CHELPER_NO_FILESYSTEM
#include <filesystem>
#endif
// 用于字符串转整数或小数
#include <cinttypes>
// 字符串格式化
#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4702)
#endif
#define FMT_ENFORCE_COMPILE_STRING
#include <fmt/base.h>
#ifdef _MSC_VER
#pragma warning(pop)
#endif
#include <fmt/chrono.h>
#include <fmt/format.h>
#include <fmt/xchar.h>
#if !defined(__ANDROID__) && !defined(CHELPER_NO_FILESYSTEM)
#include <fmt/color.h>
#endif
// 日志库
#include <spdlog/spdlog.h>
// 哈希算法
#define XXH_STATIC_LINKING_ONLY
#include <xxhash.h>
// UTF编码处理
#include <chelper/util/Utf8.h>
#include <utf8.h>
// 序列化（glaze：JSON / BEVE / CBOR / BSON / MessagePack 等 + 自定义二进制格式）
#include <glaze/bson.hpp>
#include <glaze/cbor.hpp>
#include <glaze/glaze.hpp>
#include <glaze/msgpack.hpp>
// clang-format off：BinaryFormat.h 依赖上述 glaze 头文件，需保持在其后
#include <chelper/serialization/BinaryFormat.h>
// clang-format on
// json工具
#include <chelper/util/JsonUtil.h>
// 简单的调用栈
#include <chelper/util/Profile.h>
// KMP字符串匹配算法
#include <chelper/util/KMPMatcher.h>

#endif// CHELPER_PCH_H
