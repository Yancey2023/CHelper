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

#include <ParamDeliver.h>

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

//分发层的调用链必须完全内联，否则每个节点分发要穿过多层真实函数调用，
//调用开销与跨函数的寄存器隔离会造成可测的性能回退
#if defined(_MSC_VER) && !defined(__clang__)
#define CHELPER_FORCEINLINE __forceinline
#else
#define CHELPER_FORCEINLINE [[gnu::always_inline]]
#endif

// 禁止编译器丢弃"只为强制生成符号、本身永不被调用"的函数：
// GCC / Clang / clang-cl 需要 used 属性，MSVC 会保留未使用的非 static 函数
#if defined(__GNUC__) || defined(__clang__)
#define CHELPER_USED gnu::used
#else
#define CHELPER_USED maybe_unused
#endif

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
// KMP字符串匹配算法
#include <chelper/util/KMPMatcher.h>
