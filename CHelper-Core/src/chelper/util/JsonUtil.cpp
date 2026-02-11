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

#include <chelper/parser/ErrorReason.h>
#include <chelper/util/JsonUtil.h>

namespace CHelper::JsonUtil {

    size_t ConvertResult::convert(size_t index) const {
        return indexConvertList[index];
    }

    std::u16string string2jsonString(const std::u16string_view &input) {
        std::u16string result;
        result.reserve(static_cast<size_t>(static_cast<double>(input.size()) * 1.2));
        auto it = input.begin();
        while (true) {
            if (it == input.end()) [[unlikely]] {
                return result;
            }
            char16_t ch = *it;
            switch (ch) {
                case u'\"':
                case u'\\':
                case u'/':
                case u'\b':
                case u'\f':
                case u'\n':
                case u'\r':
                case u'\t':
                    result.push_back(u'\\');
                    break;
                default:
                    break;
            }
            result.push_back(ch);
            ++it;
        }
    }

    ConvertResult jsonString2String(const std::u16string_view &input) {
        ConvertResult result;
        if (input.empty() || input[0] != '\"') [[unlikely]] {
            result.errorReason = ErrorReason::incomplete(0, 0, u"json字符串必须在双引号内");
            return result;
        }
        size_t index = 0;
        result.result.reserve(input.size());
        result.indexConvertList.reserve(input.size());
        int32_t unicodeValue;
        std::u16string escapeSequence;
        while (true) {
            //结束字符
            ++index;
            result.indexConvertList.push_back(index);
            if (index >= input.size()) [[unlikely]] {
                result.isComplete = false;
                break;
            }
            char16_t ch = input[index];
            if (ch == u'\"') [[unlikely]] {
                result.isComplete = true;
                break;
            }
            //正常字符
            if (ch != u'\\') [[likely]] {
                result.result.push_back(ch);
                continue;
            }
            //转义字符
            ++index;
            if (index >= input.size()) [[unlikely]] {
                result.errorReason = ErrorReason::incomplete(
                        index - 1,
                        index,
                        u"转义字符缺失后半部分");
            } else {
                ch = input[index];
                switch (ch) {
                    case u'\"':
                    case u'\\':
                    case u'/':
                    case u'b':
                    case u'f':
                    case u'n':
                    case u'r':
                    case u't':
                        result.result.push_back(ch);
                        break;
                    case u'u':
                        index += 4;
                        if (index >= input.size()) [[unlikely]] {
                            result.errorReason = ErrorReason::contentError(
                                    index - 5,
                                    input.size(),
                                    fmt::format(u"字符串转义缺失后半部分 -> \\u{}", escapeSequence));
                            break;
                        }
                        escapeSequence = input.substr(index - 4, 4);
                        if (std::ranges::any_of(escapeSequence,
                                                [&result, &index, &escapeSequence](const auto &item) {
                                                    if (std::isxdigit(item)) [[likely]] {
                                                        return false;
                                                    } else {
                                                        result.errorReason = ErrorReason::incomplete(
                                                                index - 5,
                                                                index + 1,
                                                                fmt::format(u"字符串转义出现非法字符{} -> \\u{}", item, escapeSequence));
                                                        return true;
                                                    }
                                                })) [[unlikely]] {
                            break;
                        }
                        unicodeValue = std::stoi(utf8::utf16to8(escapeSequence), nullptr, 16);
                        if (unicodeValue <= 0 || unicodeValue > 0x10FFFF) [[unlikely]] {
                            result.errorReason = ErrorReason::contentError(
                                    index - 5, index + 1,
                                    fmt::format(u"字符串转义的Unicode值无效 -> \\u{}", escapeSequence));
                            break;
                        }
                        escapeSequence.clear();
                        if (unicodeValue <= 0xFFFF) [[likely]] {
                            result.result.push_back(static_cast<char16_t>(unicodeValue));
                        } else {
                            uint32_t adjusted = unicodeValue - 0x10000;
                            result.result.push_back(static_cast<char16_t>(0xD800 | (adjusted >> 10)));
                            result.result.push_back(static_cast<char16_t>(0xDC00 | (adjusted & 0x3FF)));
                            result.indexConvertList.push_back(index - 5);
                        }
                        break;
                    default:
                        result.errorReason = ErrorReason::contentError(
                                index, index + 1,
                                fmt::format(u"未知的转义字符 -> \\{:c}", ch));
                        break;
                }
            }
            if (result.errorReason != nullptr) [[unlikely]] {
                break;
            }
        }
        return result;
    }

}// namespace CHelper::JsonUtil