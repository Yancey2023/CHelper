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

#include <memory>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

namespace CHelper {

    class ErrorReason;

    namespace JsonUtil {

        class ConvertResult {
        public:
            std::u16string result;
            std::shared_ptr<ErrorReason> errorReason;
            std::vector<size_t> indexConvertList;
            bool isComplete = false;

            [[nodiscard]] size_t convert(size_t index) const;
        };

        std::u16string string2jsonString(const std::u16string_view &input);

        ConvertResult jsonString2String(const std::u16string_view &input);

        // 普通字符串借用输入；有转义或非法输入才使用完整解码及位置映射。
        // 输入须活到视图使用结束，转义后的文本则由此对象持有。
        class DecodedStringView {
            std::u16string_view plain;
            std::optional<ConvertResult> converted;

        public:
            std::shared_ptr<ErrorReason> errorReason;
            bool isComplete = false;

            explicit DecodedStringView(std::u16string_view input);

            [[nodiscard]] std::u16string_view string() const noexcept {
                return converted ? std::u16string_view(converted->result) : plain;
            }

            [[nodiscard]] bool hasDirectMapping() const noexcept { return !converted; }

            [[nodiscard]] size_t convert(size_t index) const {
                if (converted) return converted->convert(index);
#if CHelperDebug
                if (index > plain.size()) [[unlikely]] {
                    throw std::runtime_error("index out of range in DecodedStringView::convert");
                }
#endif
                return index + 1;
            }
        };

    }// namespace JsonUtil

}// namespace CHelper
