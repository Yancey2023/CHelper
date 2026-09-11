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

#ifndef CHELPER_UTF8_H
#define CHELPER_UTF8_H

// 自带依赖，不依赖 pch.h 的包含顺序：BinaryFormat.h 会在 pch.h 之前被包含
#include <algorithm>
#include <string>
#include <string_view>

#include <utf8.h>

/**
 * UTF-8 / UTF-16 转换
 *
 * 命名空间不要取成 Utf8：会与 utf8cpp 的全局命名空间 utf8 混淆
 */
namespace CHelper {
    namespace U16Conv {

        /**
         * 把 UTF-8 文本解码到 UTF-16 串（覆盖目标串原有内容）
         *
         * utf8::utf8to16 配 back_inserter 是逐字符 push_back，目标串按几何增长反复重分配。
         * 资源包里含上万条字符串（方块状态值、ID、描述等），逐字符增长会产生上万次分配。
         * 纯 ASCII 是这些字符串的绝大多数形态（ID、状态值），走一次性赋值的快速路径；
         * 含非 ASCII 时仍交给解码器，此时字符串长度通常很短，重分配次数可以忽略
         */
        template<class String>
        inline void convertToU16(const std::string_view input, String &output) {
            output.clear();
            if (input.empty()) {
                return;
            }
            // 纯 ASCII 时逐字符赋值即可，不能用 memcpy：
            // 输入是 char（1 字节），输出是 char16_t（2 字节），字节数并不相等
            if (std::ranges::all_of(input, [](unsigned char ch) { return ch < 0x80; })) {
                output.resize(input.size());
                std::copy(input.begin(), input.end(), output.begin());
                return;
            }
            utf8::utf8to16(input.begin(), input.end(), std::back_inserter(output));
        }

        /**
         * 把 UTF-8 文本解码成新的 UTF-16 串
         */
        [[nodiscard]] inline std::u16string toU16(const std::string_view input) {
            std::u16string output;
            convertToU16(input, output);
            return output;
        }

    }// namespace U16Conv
}// namespace CHelper

#endif//CHELPER_UTF8_H
