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

#include <pch.h>

namespace CHelper::CommandStructure {

    // 两种构建器共用追加规则：计长只累加字符数，写入直接使用预留的缓冲区。
    template<bool measure>
    class BasicStructureBuilder {
    private:
        char16_t *output = nullptr;
        size_t length = 0;

        void increaseSize(size_t count)
            requires(measure)
        {
            if (count > std::numeric_limits<size_t>::max() - length) [[unlikely]] {
                throw std::length_error("command structure is too long");
            }
            length += count;
        }

    public:
        BasicStructureBuilder()
            requires(measure)
        = default;

        // 调用方必须先用计长构建器遍历，提供至少同样长度的输出缓冲区。
        explicit BasicStructureBuilder(char16_t *output)
            requires(!measure)
            : output(output) {}

        [[nodiscard]] size_t size() const {
            return length;
        }

        BasicStructureBuilder &appendUnknown(bool isMustHave) {
            return appendStringWithBracket(isMustHave, u"未知");
        }

        BasicStructureBuilder &appendSymbol(char16_t ch) {
            if constexpr (measure) increaseSize(1);
            else
                output[length++] = ch;
            return *this;
        }

        BasicStructureBuilder &appendString(std::u16string_view str) {
            if constexpr (measure) increaseSize(str.size());
            else if (!str.empty()) {
                std::memcpy(output + length, str.data(), str.size() * sizeof(char16_t));
                length += str.size();
            }
            return *this;
        }

        BasicStructureBuilder &appendSpace() {
            if (size() == 0) return *this;
            return appendSymbol(u' ');
        }

        BasicStructureBuilder &appendLeftBracket(bool isMustHave) {
            return appendSymbol(isMustHave ? u'<' : u'[');
        }

        BasicStructureBuilder &appendRightBracket(bool isMustHave) {
            return appendSymbol(isMustHave ? u'>' : u']');
        }

        BasicStructureBuilder &appendStringWithBracket(bool isMustHave, std::u16string_view str) {
            return appendSpace().appendLeftBracket(isMustHave).appendString(str).appendRightBracket(isMustHave);
        }
    };

    using StructureBuilder = BasicStructureBuilder<false>;
    using StructureLengthBuilder = BasicStructureBuilder<true>;

}// namespace CHelper::CommandStructure
