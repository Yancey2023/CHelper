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

#include <chelper/parser/TokensView.h>
#include <pch.h>
#include <span>

namespace CHelper::SyntaxHighlight {

    namespace SyntaxTokenType {
        enum SyntaxTokenType : uint8_t {
            UNKNOWN,
            BOOLEAN,
            FLOAT,
            INTEGER,
            SYMBOL,
            ID,
            TARGET_SELECTOR,
            COMMAND,
            BRACKET1,
            BRACKET2,
            BRACKET3,
            STRING,
            NULL_TOKEN,
            RANGE,
            LITERAL
        };
    }// namespace SyntaxTokenType

    class SyntaxResultView {
    public:
        std::u16string_view str;
        std::span<SyntaxTokenType::SyntaxTokenType> tokenTypes;

        SyntaxResultView(std::u16string_view str, std::span<SyntaxTokenType::SyntaxTokenType> tokenTypes)
            : str(str), tokenTypes(tokenTypes) {}

        void update(size_t index, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
#if CHelperDebug
            if (index >= tokenTypes.size() || index >= str.size()) {
                throw std::out_of_range("syntax token index out of range");
            }
#endif
            tokenTypes[index] = syntaxTokenType;
        }

        void update(size_t start, size_t end, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
#if CHelperDebug
            if (start > end || end > tokenTypes.size() || end > str.size()) {
                throw std::out_of_range("syntax token range out of range");
            }
#endif
            std::fill(tokenTypes.begin() + static_cast<std::ptrdiff_t>(start),
                      tokenTypes.begin() + static_cast<std::ptrdiff_t>(end), syntaxTokenType);
        }

        void update(const TokensView &tokensView, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
            update(tokensView.startIndex, tokensView.endIndex, syntaxTokenType);
        }
    };

    class SyntaxResult {
    public:
        // Borrows the command text. The source AST/context must outlive this view.
        std::u16string_view str;
        std::vector<SyntaxTokenType::SyntaxTokenType> tokenTypes;

        explicit SyntaxResult(const std::u16string_view &str);

        [[nodiscard]] SyntaxResultView view() noexcept { return {str, tokenTypes}; }

        void update(size_t index, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
            view().update(index, syntaxTokenType);
        }

        void update(size_t start, size_t end, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
            view().update(start, end, syntaxTokenType);
        }

        void update(const TokensView &tokensView, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
            view().update(tokensView, syntaxTokenType);
        }
    };

}// namespace CHelper::SyntaxHighlight
