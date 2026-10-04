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

namespace CHelper::ErrorReasons {
    struct Range {
        size_t start, end;
        Range(size_t start, size_t end) : start(start), end(end) {}
        Range(const TokensView &tokens) : start(tokens.startIndex), end(tokens.endIndex) {}
    };

    // 每个入口只接收该诊断所需的固定参数；不对外开放 code/通用参数表。
    std::shared_ptr<ErrorReason> customText(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> copy(const ErrorReason &source);
    std::shared_ptr<ErrorReason> requireSpace(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> requireType(ErrorReasonLevel::ErrorReasonLevel level, Range range, ErrorReasonExpectedType expected);
    std::shared_ptr<ErrorReason> typeMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, ErrorReasonExpectedType expected, TokenType::TokenType actual);
    std::shared_ptr<ErrorReason> integerRequired(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> invalidNumber(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> emptyNull(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> invalidNull(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> emptyString(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> quotedStringRequired(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> emptyCommandName(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> unknownCommand(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> excess(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> incomplete(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> unknownMeaning(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> invalidCoordinate(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> emptyRange(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> invalidRange(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> stringContainsSpace(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> unclosedString(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> unexpectedSpace(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> requireSymbol(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character);
    std::shared_ptr<ErrorReason> symbolTypeMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character, std::u16string_view text);
    std::shared_ptr<ErrorReason> symbolContentMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character, std::u16string_view text);
    std::shared_ptr<ErrorReason> invalidBoolean(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> unknownCommandName(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> unknownId(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> mixedCoordinates(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> localCoordinateDisallowed(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> unknownSelectorArgument(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> unknownJsonArgument(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, double min, double max, std::u16string_view text);
    std::shared_ptr<ErrorReason> jsonQuotesRequired(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> incompleteEscape(ErrorReasonLevel::ErrorReasonLevel level, Range range);
    std::shared_ptr<ErrorReason> incompleteUnicodeEscape(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> invalidUnicodeEscapeCharacter(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character, std::u16string_view text);
    std::shared_ptr<ErrorReason> invalidUnicodeEscapeValue(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> unknownEscape(ErrorReasonLevel::ErrorReasonLevel level, Range range, char16_t character);
    std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, int32_t min, int32_t max, std::u16string_view text);
    std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, int64_t min, int64_t max, std::u16string_view text);
    std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, uint64_t min, uint64_t max, std::u16string_view text);
    std::shared_ptr<ErrorReason> numberOutOfRange(ErrorReasonLevel::ErrorReasonLevel level, Range range, float min, float max, std::u16string_view text);
    std::shared_ptr<ErrorReason> requireType(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view text);
    std::shared_ptr<ErrorReason> typeMismatch(ErrorReasonLevel::ErrorReasonLevel level, Range range, std::u16string_view expected, std::u16string_view actual);
}// namespace CHelper::ErrorReasons
