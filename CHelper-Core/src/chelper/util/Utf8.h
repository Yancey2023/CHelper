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

// 自带依赖，不依赖 pch.h 的包含顺序：BinaryFormat.h 会在 pch.h 之前被包含
#include <algorithm>
#include <cstdint>
#include <cstring>
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
         * ASCII 批量判定后直接宽化；非 ASCII 先计算 UTF-16 单元数再交给校验解码器，
         * 避免按 UTF-8 字节数为中文串申请约三倍容量，也保留短串的内联存储。
         */
        template<class String>
        inline void convertToU16(const std::string_view input, String &output) {
            output.clear();
            if (input.empty()) {
                return;
            }
            std::size_t asciiEnd = 0;
            // memcpy 支持未对齐输入，不要求资源包中的字符串起始地址对齐。
            while (input.size() - asciiEnd >= sizeof(std::uint64_t)) {
                std::uint64_t bytes;
                std::memcpy(&bytes, input.data() + asciiEnd, sizeof(bytes));
                if ((bytes & UINT64_C(0x8080808080808080)) != 0) break;
                asciiEnd += sizeof(bytes);
            }
            while (asciiEnd < input.size() && static_cast<unsigned char>(input[asciiEnd]) < 0x80) ++asciiEnd;
            if (asciiEnd == input.size()) {
                output.resize(input.size());
                std::copy(input.begin(), input.end(), output.begin());
                return;
            }
            std::size_t units = asciiEnd;
            for (std::size_t index = asciiEnd; index < input.size(); ++index) {
                const auto byte = static_cast<unsigned char>(input[index]);
                if ((byte & 0xc0) != 0x80) ++units;
                if (byte >= 0xf0 && byte <= 0xf4) ++units;// 四字节码点对应两个 UTF-16 单元
            }
            output.reserve(units);
            // 单元计数只决定容量，非法/截断 UTF-8 仍由 utf8cpp 拒绝。
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
