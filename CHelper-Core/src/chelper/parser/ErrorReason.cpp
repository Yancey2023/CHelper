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
#include <chelper/parser/detail/ErrorReasonParameters.h>

namespace CHelper {
    namespace ErrorReasonLevel {
        ErrorReasonLevel maxLevel = ID_ERROR;
    }
    namespace {
        std::u16string_view typeName(ErrorReasonExpectedType type) {
            switch (type) {
                case ErrorReasonExpectedType::String:
                    return TokenType::getNameView(TokenType::STRING);
                case ErrorReasonExpectedType::Integer:
                    return u"整数类型";
                case ErrorReasonExpectedType::Float:
                    return TokenType::getNameView(TokenType::NUMBER);
                case ErrorReasonExpectedType::Symbol:
                    return TokenType::getNameView(TokenType::SYMBOL);
            }
            throw std::invalid_argument("Unknown expected error type");
        }

        // 前缀只用于排除不可能相等的消息，不负责格式化或拼接参数。
        std::u16string_view messagePrefix(ErrorReasonCode code) {
            switch (code) {
                case ErrorReasonCode::RequireSpace:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::RequireSpace>::prefix;
                case ErrorReasonCode::RequireType:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::RequireType>::prefix;
                case ErrorReasonCode::TypeMismatch:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::TypeMismatch>::prefix;
                case ErrorReasonCode::IntegerRequired:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::IntegerRequired>::prefix;
                case ErrorReasonCode::InvalidNumber:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::InvalidNumber>::prefix;
                case ErrorReasonCode::EmptyNull:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::EmptyNull>::prefix;
                case ErrorReasonCode::InvalidNull:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::InvalidNull>::prefix;
                case ErrorReasonCode::EmptyString:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::EmptyString>::prefix;
                case ErrorReasonCode::QuotedStringRequired:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::QuotedStringRequired>::prefix;
                case ErrorReasonCode::EmptyCommandName:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::EmptyCommandName>::prefix;
                case ErrorReasonCode::UnknownCommand:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnknownCommand>::prefix;
                case ErrorReasonCode::Excess:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::Excess>::prefix;
                case ErrorReasonCode::Incomplete:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::Incomplete>::prefix;
                case ErrorReasonCode::UnknownMeaning:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnknownMeaning>::prefix;
                case ErrorReasonCode::InvalidCoordinate:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::InvalidCoordinate>::prefix;
                case ErrorReasonCode::EmptyRange:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::EmptyRange>::prefix;
                case ErrorReasonCode::InvalidRange:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::InvalidRange>::prefix;
                case ErrorReasonCode::StringContainsSpace:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::StringContainsSpace>::prefix;
                case ErrorReasonCode::UnclosedString:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnclosedString>::prefix;
                case ErrorReasonCode::UnexpectedSpace:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnexpectedSpace>::prefix;
                case ErrorReasonCode::RequireSymbol:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::RequireSymbol>::prefix;
                case ErrorReasonCode::SymbolTypeMismatch:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::SymbolTypeMismatch>::prefix;
                case ErrorReasonCode::SymbolContentMismatch:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::SymbolContentMismatch>::prefix;
                case ErrorReasonCode::InvalidBoolean:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::InvalidBoolean>::prefix;
                case ErrorReasonCode::UnknownCommandName:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnknownCommandName>::prefix;
                case ErrorReasonCode::UnknownId:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnknownId>::prefix;
                case ErrorReasonCode::MixedCoordinates:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::MixedCoordinates>::prefix;
                case ErrorReasonCode::LocalCoordinateDisallowed:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::LocalCoordinateDisallowed>::prefix;
                case ErrorReasonCode::UnknownSelectorArgument:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnknownSelectorArgument>::prefix;
                case ErrorReasonCode::UnknownJsonArgument:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnknownJsonArgument>::prefix;
                case ErrorReasonCode::NumberOutOfRange:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::NumberOutOfRange>::prefix;
                case ErrorReasonCode::JsonQuotesRequired:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::JsonQuotesRequired>::prefix;
                case ErrorReasonCode::IncompleteEscape:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::IncompleteEscape>::prefix;
                case ErrorReasonCode::IncompleteUnicodeEscape:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::IncompleteUnicodeEscape>::prefix;
                case ErrorReasonCode::InvalidUnicodeEscapeCharacter:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::InvalidUnicodeEscapeCharacter>::prefix;
                case ErrorReasonCode::InvalidUnicodeEscapeValue:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::InvalidUnicodeEscapeValue>::prefix;
                case ErrorReasonCode::UnknownEscape:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::UnknownEscape>::prefix;
                case ErrorReasonCode::NumberOutOfRangeInt32:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::NumberOutOfRangeInt32>::prefix;
                case ErrorReasonCode::NumberOutOfRangeInt64:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::NumberOutOfRangeInt64>::prefix;
                case ErrorReasonCode::NumberOutOfRangeUInt64:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::NumberOutOfRangeUInt64>::prefix;
                case ErrorReasonCode::NumberOutOfRangeFloat:
                    return Detail::ErrorParameters::Parameters<ErrorReasonCode::NumberOutOfRangeFloat>::prefix;
                case ErrorReasonCode::CustomText:
                    return {};
            }
            throw std::invalid_argument("Unknown error reason code");
        }
    }// namespace

    std::u16string ErrorReason::getMessage() const {
        using namespace Detail::ErrorParameters;
        switch (code) {
            case ErrorReasonCode::RequireSpace: {
                return fmt::format(Parameters<ErrorReasonCode::RequireSpace>::pattern);
            }
            case ErrorReasonCode::RequireType: {
                const auto &data = parametersAs<For<ErrorReasonCode::RequireType>>();
                return fmt::format(Parameters<ErrorReasonCode::RequireType>::pattern, typeName(data.expected));
            }
            case ErrorReasonCode::TypeMismatch: {
                const auto &data = parametersAs<For<ErrorReasonCode::TypeMismatch>>();
                return fmt::format(Parameters<ErrorReasonCode::TypeMismatch>::pattern, typeName(data.expected), TokenType::getNameView(data.actual));
            }
            case ErrorReasonCode::IntegerRequired: {
                return fmt::format(Parameters<ErrorReasonCode::IntegerRequired>::pattern);
            }
            case ErrorReasonCode::InvalidNumber: {
                const auto &data = parametersAs<For<ErrorReasonCode::InvalidNumber>>();
                return fmt::format(Parameters<ErrorReasonCode::InvalidNumber>::pattern, data.text);
            }
            case ErrorReasonCode::EmptyNull: {
                return fmt::format(Parameters<ErrorReasonCode::EmptyNull>::pattern);
            }
            case ErrorReasonCode::InvalidNull: {
                const auto &data = parametersAs<For<ErrorReasonCode::InvalidNull>>();
                return fmt::format(Parameters<ErrorReasonCode::InvalidNull>::pattern, data.text);
            }
            case ErrorReasonCode::EmptyString: {
                return fmt::format(Parameters<ErrorReasonCode::EmptyString>::pattern);
            }
            case ErrorReasonCode::QuotedStringRequired: {
                const auto &data = parametersAs<For<ErrorReasonCode::QuotedStringRequired>>();
                return fmt::format(Parameters<ErrorReasonCode::QuotedStringRequired>::pattern, data.text);
            }
            case ErrorReasonCode::EmptyCommandName: {
                return fmt::format(Parameters<ErrorReasonCode::EmptyCommandName>::pattern);
            }
            case ErrorReasonCode::UnknownCommand: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnknownCommand>>();
                return fmt::format(Parameters<ErrorReasonCode::UnknownCommand>::pattern, data.text);
            }
            case ErrorReasonCode::Excess: {
                const auto &data = parametersAs<For<ErrorReasonCode::Excess>>();
                return fmt::format(Parameters<ErrorReasonCode::Excess>::pattern, data.text);
            }
            case ErrorReasonCode::Incomplete: {
                return fmt::format(Parameters<ErrorReasonCode::Incomplete>::pattern);
            }
            case ErrorReasonCode::UnknownMeaning: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnknownMeaning>>();
                return fmt::format(Parameters<ErrorReasonCode::UnknownMeaning>::pattern, data.text);
            }
            case ErrorReasonCode::InvalidCoordinate: {
                const auto &data = parametersAs<For<ErrorReasonCode::InvalidCoordinate>>();
                return fmt::format(Parameters<ErrorReasonCode::InvalidCoordinate>::pattern, data.text);
            }
            case ErrorReasonCode::EmptyRange: {
                return fmt::format(Parameters<ErrorReasonCode::EmptyRange>::pattern);
            }
            case ErrorReasonCode::InvalidRange: {
                return fmt::format(Parameters<ErrorReasonCode::InvalidRange>::pattern);
            }
            case ErrorReasonCode::StringContainsSpace: {
                return fmt::format(Parameters<ErrorReasonCode::StringContainsSpace>::pattern);
            }
            case ErrorReasonCode::UnclosedString: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnclosedString>>();
                return fmt::format(Parameters<ErrorReasonCode::UnclosedString>::pattern, data.text);
            }
            case ErrorReasonCode::UnexpectedSpace: {
                return fmt::format(Parameters<ErrorReasonCode::UnexpectedSpace>::pattern);
            }
            case ErrorReasonCode::RequireSymbol: {
                const auto &data = parametersAs<For<ErrorReasonCode::RequireSymbol>>();
                return fmt::format(Parameters<ErrorReasonCode::RequireSymbol>::pattern, data.character);
            }
            case ErrorReasonCode::SymbolTypeMismatch: {
                const auto &data = parametersAs<For<ErrorReasonCode::SymbolTypeMismatch>>();
                return fmt::format(Parameters<ErrorReasonCode::SymbolTypeMismatch>::pattern, data.character, data.text);
            }
            case ErrorReasonCode::SymbolContentMismatch: {
                const auto &data = parametersAs<For<ErrorReasonCode::SymbolContentMismatch>>();
                return fmt::format(Parameters<ErrorReasonCode::SymbolContentMismatch>::pattern, data.character, data.text);
            }
            case ErrorReasonCode::InvalidBoolean: {
                const auto &data = parametersAs<For<ErrorReasonCode::InvalidBoolean>>();
                return fmt::format(Parameters<ErrorReasonCode::InvalidBoolean>::pattern, data.text);
            }
            case ErrorReasonCode::UnknownCommandName: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnknownCommandName>>();
                return fmt::format(Parameters<ErrorReasonCode::UnknownCommandName>::pattern, data.text);
            }
            case ErrorReasonCode::UnknownId: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnknownId>>();
                return fmt::format(Parameters<ErrorReasonCode::UnknownId>::pattern, data.text);
            }
            case ErrorReasonCode::MixedCoordinates: {
                return fmt::format(Parameters<ErrorReasonCode::MixedCoordinates>::pattern);
            }
            case ErrorReasonCode::LocalCoordinateDisallowed: {
                return fmt::format(Parameters<ErrorReasonCode::LocalCoordinateDisallowed>::pattern);
            }
            case ErrorReasonCode::UnknownSelectorArgument: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnknownSelectorArgument>>();
                return fmt::format(Parameters<ErrorReasonCode::UnknownSelectorArgument>::pattern, data.text);
            }
            case ErrorReasonCode::UnknownJsonArgument: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnknownJsonArgument>>();
                return fmt::format(Parameters<ErrorReasonCode::UnknownJsonArgument>::pattern, data.text);
            }
            case ErrorReasonCode::NumberOutOfRange: {
                const auto &data = parametersAs<For<ErrorReasonCode::NumberOutOfRange>>();
                return fmt::format(Parameters<ErrorReasonCode::NumberOutOfRange>::pattern, data.min, data.max, data.text);
            }
            case ErrorReasonCode::JsonQuotesRequired: {
                return fmt::format(Parameters<ErrorReasonCode::JsonQuotesRequired>::pattern);
            }
            case ErrorReasonCode::IncompleteEscape: {
                return fmt::format(Parameters<ErrorReasonCode::IncompleteEscape>::pattern);
            }
            case ErrorReasonCode::IncompleteUnicodeEscape: {
                const auto &data = parametersAs<For<ErrorReasonCode::IncompleteUnicodeEscape>>();
                return fmt::format(Parameters<ErrorReasonCode::IncompleteUnicodeEscape>::pattern, data.text);
            }
            case ErrorReasonCode::InvalidUnicodeEscapeCharacter: {
                const auto &data = parametersAs<For<ErrorReasonCode::InvalidUnicodeEscapeCharacter>>();
                return fmt::format(Parameters<ErrorReasonCode::InvalidUnicodeEscapeCharacter>::pattern, data.character, data.text);
            }
            case ErrorReasonCode::InvalidUnicodeEscapeValue: {
                const auto &data = parametersAs<For<ErrorReasonCode::InvalidUnicodeEscapeValue>>();
                return fmt::format(Parameters<ErrorReasonCode::InvalidUnicodeEscapeValue>::pattern, data.text);
            }
            case ErrorReasonCode::UnknownEscape: {
                const auto &data = parametersAs<For<ErrorReasonCode::UnknownEscape>>();
                return fmt::format(Parameters<ErrorReasonCode::UnknownEscape>::pattern, data.character);
            }
            case ErrorReasonCode::NumberOutOfRangeInt32: {
                const auto &data = parametersAs<For<ErrorReasonCode::NumberOutOfRangeInt32>>();
                return fmt::format(Parameters<ErrorReasonCode::NumberOutOfRangeInt32>::pattern, data.min, data.max, data.text);
            }
            case ErrorReasonCode::NumberOutOfRangeInt64: {
                const auto &data = parametersAs<For<ErrorReasonCode::NumberOutOfRangeInt64>>();
                return fmt::format(Parameters<ErrorReasonCode::NumberOutOfRangeInt64>::pattern, data.min, data.max, data.text);
            }
            case ErrorReasonCode::NumberOutOfRangeUInt64: {
                const auto &data = parametersAs<For<ErrorReasonCode::NumberOutOfRangeUInt64>>();
                return fmt::format(Parameters<ErrorReasonCode::NumberOutOfRangeUInt64>::pattern, data.min, data.max, data.text);
            }
            case ErrorReasonCode::NumberOutOfRangeFloat: {
                const auto &data = parametersAs<For<ErrorReasonCode::NumberOutOfRangeFloat>>();
                return fmt::format(Parameters<ErrorReasonCode::NumberOutOfRangeFloat>::pattern, data.min, data.max, data.text);
            }
            case ErrorReasonCode::CustomText:
                return fmt::format(Parameters<ErrorReasonCode::CustomText>::pattern, parametersAs<For<ErrorReasonCode::CustomText>>().text);
        }
        throw std::invalid_argument("Unknown error reason code");
    }

    bool ErrorReason::operator==(const ErrorReason &reason) const {
        if (start != reason.start || end != reason.end) return false;
        using namespace Detail::ErrorParameters;
        if (code == reason.code) {
            switch (code) {
                case ErrorReasonCode::RequireSpace:
                    return true;
                case ErrorReasonCode::RequireType:
                    return parametersAs<For<ErrorReasonCode::RequireType>>() == reason.parametersAs<For<ErrorReasonCode::RequireType>>();
                case ErrorReasonCode::TypeMismatch:
                    return parametersAs<For<ErrorReasonCode::TypeMismatch>>() == reason.parametersAs<For<ErrorReasonCode::TypeMismatch>>();
                case ErrorReasonCode::IntegerRequired:
                    return true;
                case ErrorReasonCode::InvalidNumber:
                    return parametersAs<For<ErrorReasonCode::InvalidNumber>>() == reason.parametersAs<For<ErrorReasonCode::InvalidNumber>>();
                case ErrorReasonCode::EmptyNull:
                    return true;
                case ErrorReasonCode::InvalidNull:
                    return parametersAs<For<ErrorReasonCode::InvalidNull>>() == reason.parametersAs<For<ErrorReasonCode::InvalidNull>>();
                case ErrorReasonCode::EmptyString:
                    return true;
                case ErrorReasonCode::QuotedStringRequired:
                    return parametersAs<For<ErrorReasonCode::QuotedStringRequired>>() == reason.parametersAs<For<ErrorReasonCode::QuotedStringRequired>>();
                case ErrorReasonCode::EmptyCommandName:
                    return true;
                case ErrorReasonCode::UnknownCommand:
                    return parametersAs<For<ErrorReasonCode::UnknownCommand>>() == reason.parametersAs<For<ErrorReasonCode::UnknownCommand>>();
                case ErrorReasonCode::Excess:
                    return parametersAs<For<ErrorReasonCode::Excess>>() == reason.parametersAs<For<ErrorReasonCode::Excess>>();
                case ErrorReasonCode::Incomplete:
                    return true;
                case ErrorReasonCode::UnknownMeaning:
                    return parametersAs<For<ErrorReasonCode::UnknownMeaning>>() == reason.parametersAs<For<ErrorReasonCode::UnknownMeaning>>();
                case ErrorReasonCode::InvalidCoordinate:
                    return parametersAs<For<ErrorReasonCode::InvalidCoordinate>>() == reason.parametersAs<For<ErrorReasonCode::InvalidCoordinate>>();
                case ErrorReasonCode::EmptyRange:
                    return true;
                case ErrorReasonCode::InvalidRange:
                    return true;
                case ErrorReasonCode::StringContainsSpace:
                    return true;
                case ErrorReasonCode::UnclosedString:
                    return parametersAs<For<ErrorReasonCode::UnclosedString>>() == reason.parametersAs<For<ErrorReasonCode::UnclosedString>>();
                case ErrorReasonCode::UnexpectedSpace:
                    return true;
                case ErrorReasonCode::RequireSymbol:
                    return parametersAs<For<ErrorReasonCode::RequireSymbol>>() == reason.parametersAs<For<ErrorReasonCode::RequireSymbol>>();
                case ErrorReasonCode::SymbolTypeMismatch:
                    return parametersAs<For<ErrorReasonCode::SymbolTypeMismatch>>() == reason.parametersAs<For<ErrorReasonCode::SymbolTypeMismatch>>();
                case ErrorReasonCode::SymbolContentMismatch:
                    return parametersAs<For<ErrorReasonCode::SymbolContentMismatch>>() == reason.parametersAs<For<ErrorReasonCode::SymbolContentMismatch>>();
                case ErrorReasonCode::InvalidBoolean:
                    return parametersAs<For<ErrorReasonCode::InvalidBoolean>>() == reason.parametersAs<For<ErrorReasonCode::InvalidBoolean>>();
                case ErrorReasonCode::UnknownCommandName:
                    return parametersAs<For<ErrorReasonCode::UnknownCommandName>>() == reason.parametersAs<For<ErrorReasonCode::UnknownCommandName>>();
                case ErrorReasonCode::UnknownId:
                    return parametersAs<For<ErrorReasonCode::UnknownId>>() == reason.parametersAs<For<ErrorReasonCode::UnknownId>>();
                case ErrorReasonCode::MixedCoordinates:
                    return true;
                case ErrorReasonCode::LocalCoordinateDisallowed:
                    return true;
                case ErrorReasonCode::UnknownSelectorArgument:
                    return parametersAs<For<ErrorReasonCode::UnknownSelectorArgument>>() == reason.parametersAs<For<ErrorReasonCode::UnknownSelectorArgument>>();
                case ErrorReasonCode::UnknownJsonArgument:
                    return parametersAs<For<ErrorReasonCode::UnknownJsonArgument>>() == reason.parametersAs<For<ErrorReasonCode::UnknownJsonArgument>>();
                case ErrorReasonCode::NumberOutOfRange:
                    break;
                case ErrorReasonCode::JsonQuotesRequired:
                    return true;
                case ErrorReasonCode::IncompleteEscape:
                    return true;
                case ErrorReasonCode::IncompleteUnicodeEscape:
                    return parametersAs<For<ErrorReasonCode::IncompleteUnicodeEscape>>() == reason.parametersAs<For<ErrorReasonCode::IncompleteUnicodeEscape>>();
                case ErrorReasonCode::InvalidUnicodeEscapeCharacter:
                    return parametersAs<For<ErrorReasonCode::InvalidUnicodeEscapeCharacter>>() == reason.parametersAs<For<ErrorReasonCode::InvalidUnicodeEscapeCharacter>>();
                case ErrorReasonCode::InvalidUnicodeEscapeValue:
                    return parametersAs<For<ErrorReasonCode::InvalidUnicodeEscapeValue>>() == reason.parametersAs<For<ErrorReasonCode::InvalidUnicodeEscapeValue>>();
                case ErrorReasonCode::UnknownEscape:
                    return parametersAs<For<ErrorReasonCode::UnknownEscape>>() == reason.parametersAs<For<ErrorReasonCode::UnknownEscape>>();
                case ErrorReasonCode::NumberOutOfRangeInt32:
                    if (parametersAs<For<ErrorReasonCode::NumberOutOfRangeInt32>>() == reason.parametersAs<For<ErrorReasonCode::NumberOutOfRangeInt32>>()) return true;
                    break;
                case ErrorReasonCode::NumberOutOfRangeInt64:
                    if (parametersAs<For<ErrorReasonCode::NumberOutOfRangeInt64>>() == reason.parametersAs<For<ErrorReasonCode::NumberOutOfRangeInt64>>()) return true;
                    break;
                case ErrorReasonCode::NumberOutOfRangeUInt64:
                    if (parametersAs<For<ErrorReasonCode::NumberOutOfRangeUInt64>>() == reason.parametersAs<For<ErrorReasonCode::NumberOutOfRangeUInt64>>()) return true;
                    break;
                case ErrorReasonCode::NumberOutOfRangeFloat:
                    break;
                case ErrorReasonCode::CustomText:
                    return parametersAs<For<ErrorReasonCode::CustomText>>() == reason.parametersAs<For<ErrorReasonCode::CustomText>>();
            }
        }
        const auto left = messagePrefix(code), right = messagePrefix(reason.code);
        if (!left.starts_with(right) && !right.starts_with(left)) return false;
        // 不同诊断类型或数值可能产生相同文本，按 fmt 的最终结果保持展示去重语义。
        return getMessage() == reason.getMessage();
    }
}// namespace CHelper
