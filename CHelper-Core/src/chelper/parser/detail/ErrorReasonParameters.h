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

#include <chelper/parser/ErrorReason.h>

namespace CHelper::Detail::ErrorParameters {
    // 每种 code 的参数布局和格式模板唯一，构造、展示与去重共用这份定义。
    struct Empty {
        bool operator==(const Empty &) const = default;
    };
    struct Text {
        std::u16string_view text;
        bool operator==(const Text &) const = default;
    };
    struct Character {
        char16_t character;
        bool operator==(const Character &) const = default;
    };
    struct CharacterText {
        char16_t character;
        std::u16string_view text;
        bool operator==(const CharacterText &) const = default;
    };
    struct ExpectedType {
        ErrorReasonExpectedType expected;
        bool operator==(const ExpectedType &) const = default;
    };
    struct TypeMismatch {
        ErrorReasonExpectedType expected;
        TokenType::TokenType actual;
        bool operator==(const TypeMismatch &other) const {
            return expected == other.expected && TokenType::getNameView(actual) == TokenType::getNameView(other.actual);
        }
    };
    template<class T>
    struct NumberRange {
        T min, max;
        std::u16string_view text;
        bool operator==(const NumberRange &) const = default;
    };

    template<ErrorReasonCode>
    struct Parameters;
    template<>
    struct Parameters<ErrorReasonCode::CustomText> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"{}";
    };
    template<>
    struct Parameters<ErrorReasonCode::RequireSpace> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"命令不完整，缺少空格";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::RequireType> {
        using Type = ExpectedType;
        static constexpr std::u16string_view pattern = u"命令不完整，需要的参数类型为{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::TypeMismatch> {
        using Type = TypeMismatch;
        static constexpr std::u16string_view pattern = u"类型不匹配，正确的参数类型为{}，但当前参数类型为{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::IntegerRequired> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"类型不匹配，正确的参数类型为整数，但当前参数类型为小数";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::InvalidNumber> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"数字格式错误 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::EmptyNull> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"null参数为空";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::InvalidNull> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"内容不是null -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::EmptyString> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"字符串参数内容为空";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::QuotedStringRequired> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"字符串参数内容应该在双引号内 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::EmptyCommandName> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"命令名字为空";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnknownCommand> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"命令名字不匹配，找不到名为{}的命令";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::Excess> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"命令后面有多余部分 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::Incomplete> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"命令不完整";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnknownMeaning> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"找不到含义 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::InvalidCoordinate> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"类型不匹配，{}不是有效的坐标参数";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::EmptyRange> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"范围的数值为空";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::InvalidRange> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"范围的数值格式不正确，检测非法字符";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::StringContainsSpace> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"字符串参数内容不可以包含空格";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnclosedString> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"字符串参数内容双引号不封闭 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnexpectedSpace> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"意外的空格";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::RequireSymbol> {
        using Type = Character;
        static constexpr std::u16string_view pattern = u"命令不完整，需要符号{:c}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::SymbolTypeMismatch> {
        using Type = CharacterText;
        static constexpr std::u16string_view pattern = u"类型不匹配，需要符号{:c}，但当前内容为{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::SymbolContentMismatch> {
        using Type = CharacterText;
        static constexpr std::u16string_view pattern = u"内容不匹配，正确的符号为{:c}，但当前内容为{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::InvalidBoolean> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"内容不匹配，应该为布尔值，但当前内容为{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnknownCommandName> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"找不到命令名 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnknownId> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"找不到ID -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::MixedCoordinates> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"绝对坐标和相对坐标不能与局部坐标混用";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::LocalCoordinateDisallowed> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"不能使用局部坐标";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnknownSelectorArgument> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"未知的目标选择器参数 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnknownJsonArgument> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"未知的json参数 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::NumberOutOfRange> {
        using Type = NumberRange<double>;
        static constexpr std::u16string_view pattern = u"数值不在范围[{}, {}]内 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::JsonQuotesRequired> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"json字符串必须在双引号内";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::IncompleteEscape> {
        using Type = Empty;
        static constexpr std::u16string_view pattern = u"转义字符缺失后半部分";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::IncompleteUnicodeEscape> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"字符串转义缺失后半部分 -> \\u{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::InvalidUnicodeEscapeCharacter> {
        using Type = CharacterText;
        static constexpr std::u16string_view pattern = u"字符串转义出现非法字符{} -> \\u{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::InvalidUnicodeEscapeValue> {
        using Type = Text;
        static constexpr std::u16string_view pattern = u"字符串转义的Unicode值无效 -> \\u{}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::UnknownEscape> {
        using Type = Character;
        static constexpr std::u16string_view pattern = u"未知的转义字符 -> \\{:c}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::NumberOutOfRangeInt32> {
        using Type = NumberRange<int32_t>;
        static constexpr std::u16string_view pattern = u"数值不在范围[{}, {}]内 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::NumberOutOfRangeInt64> {
        using Type = NumberRange<int64_t>;
        static constexpr std::u16string_view pattern = u"数值不在范围[{}, {}]内 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::NumberOutOfRangeUInt64> {
        using Type = NumberRange<uint64_t>;
        static constexpr std::u16string_view pattern = u"数值不在范围[{}, {}]内 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<>
    struct Parameters<ErrorReasonCode::NumberOutOfRangeFloat> {
        using Type = NumberRange<float>;
        static constexpr std::u16string_view pattern = u"数值不在范围[{}, {}]内 -> {}";
        static constexpr auto prefix = pattern.substr(0, pattern.find(u'{'));
    };
    template<ErrorReasonCode Code>
    using For = typename Parameters<Code>::Type;
}// namespace CHelper::Detail::ErrorParameters
