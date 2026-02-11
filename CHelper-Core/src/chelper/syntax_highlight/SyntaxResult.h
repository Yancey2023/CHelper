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

#ifndef CHELPER_SYNTAXRESULT_H
#define CHELPER_SYNTAXRESULT_H

#include <chelper/parser/TokensView.h>
#include <pch.h>

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

    class SyntaxResult {
    public:
        std::u16string_view str;
        std::vector<SyntaxTokenType::SyntaxTokenType> tokenTypes;

        explicit SyntaxResult(const std::u16string_view &str);

        void update(size_t index, SyntaxTokenType::SyntaxTokenType syntaxTokenType);

        void update(size_t start, size_t end, SyntaxTokenType::SyntaxTokenType syntaxTokenType);

        void update(const TokensView &tokensView, SyntaxTokenType::SyntaxTokenType syntaxTokenType);
    };

}// namespace CHelper::SyntaxHighlight

#endif//CHELPER_SYNTAXRESULT_H
