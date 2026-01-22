//
// Created by Yancey on 2023/11/30.
//

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
#if defined(CHelperAndroid) || defined(CHELPER_NO_FILESYSTEM)
#define FORMAT_ARG(arg) arg
#else
#define FORMAT_ARG(arg) fmt::styled(arg, fg(fmt::color::medium_purple))
#endif

#ifdef CHelperAndroid
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>
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
#pragma warning(disable : 4244)
#endif
#include <fmt/base.h>
#ifdef _MSC_VER
#pragma warning(pop)
#endif
#include <fmt/chrono.h>
#include <fmt/color.h>
#include <fmt/format.h>
#include <fmt/xchar.h>
// 日志库
#include <spdlog/spdlog.h>
// 哈希算法
#define XXH_STATIC_LINKING_ONLY
#include <xxhash.h>
// UTF编码处理
#include <utf8.h>
// 序列化
#ifdef CHELPER_NO_FILESYSTEM
#define SERIALIZATION_NO_FILESYSTEM
#endif
#include <serialization/serialization.h>
// json工具
#include <chelper/util/JsonUtil.h>
// 简单的调用栈
#include <chelper/util/Profile.h>
// 字符串工具类
#include <chelper/util/StringUtil.h>
// KMP字符串匹配算法
#include <chelper/util/KMPMatcher.h>

#if defined(_MSC_VER) && !defined(__clang__)
#define CHELPER_UNREACHABLE() __assume(false)
#else
#define CHELPER_UNREACHABLE() __builtin_unreachable()
#endif

#endif// CHELPER_PCH_H
