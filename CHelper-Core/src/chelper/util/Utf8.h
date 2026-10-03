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
#include <bit>
#include <cstdint>
#include <cstring>
#include <exception>
#include <optional>
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
                output.resize_and_overwrite(input.size(), [&](char16_t *buffer, std::size_t) noexcept {
                    std::copy(input.begin(), input.end(), buffer);
                    return input.size();
                });
                return;
            }
            std::size_t units = asciiEnd;
            std::size_t index = asciiEnd;
            // 每八字节同时统计续字节（10xxxxxx）和四字节序列头（1111xxxx）。
            // 仅用来确定容量，UTF-8 合法性仍交由下方的校验解码器处理。
            constexpr auto highBits = UINT64_C(0x8080808080808080);
            while (input.size() - index >= sizeof(std::uint64_t)) {
                std::uint64_t bytes;
                std::memcpy(&bytes, input.data() + index, sizeof(bytes));
                const auto continuation = bytes & ~(bytes << 1) & highBits;
                const auto fourByteLead = bytes & (bytes << 1) & (bytes << 2) & (bytes << 3) & highBits;
                units += sizeof(bytes) - std::popcount(continuation) + std::popcount(fourByteLead);
                index += sizeof(bytes);
            }
            for (; index < input.size(); ++index) {
                const auto byte = static_cast<unsigned char>(input[index]);
                if ((byte & 0xc0) != 0x80) ++units;
                if (byte >= 0xf0 && byte <= 0xf4) ++units;// 四字节码点对应两个 UTF-16 单元
            }
            // 解码会覆盖整个缓冲区，不必先 resize 清零。回调不能抛出，捕获后
            // 在 resize_and_overwrite 恢复字符串不变量后重抛，目标串仍可安全复用。
            std::optional<std::exception_ptr> error;
            output.resize_and_overwrite(units, [&](char16_t *buffer, std::size_t) noexcept {
                std::copy_n(input.begin(), asciiEnd, buffer);
                try {
                    return static_cast<std::size_t>(utf8::utf8to16(input.begin() + asciiEnd, input.end(), buffer + asciiEnd) - buffer);
                } catch (...) {
                    error.emplace(std::current_exception());
                    return std::size_t{0};
                }
            });
            if (error) std::rethrow_exception(*error);
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
