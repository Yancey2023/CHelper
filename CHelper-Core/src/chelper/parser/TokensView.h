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

#ifndef CHELPER_TOKENSVIEW_H
#define CHELPER_TOKENSVIEW_H

#include <chelper/lexer/LexerResult.h>
#include <chelper/lexer/Token.h>
#include <pch.h>

namespace CHelper {

    class TokensView {
    public:
        std::shared_ptr<LexerResult> lexerResult;
        size_t start, end;
        size_t startIndex, endIndex;

    private:
        std::u16string_view cacheString;

    public:
        TokensView(const std::shared_ptr<LexerResult> &lexerResult, size_t start, size_t end);

        [[nodiscard]] bool isEmpty() const;

        [[nodiscard]] bool hasValue() const;

        const Token &operator[](size_t which) const;

        [[nodiscard]] size_t size() const;

        [[nodiscard]] bool isAllSpace() const;

        void forEach(std::function<void(const Token &token)> function) const;

        [[nodiscard]] std::u16string_view string() const;
    };

}// namespace CHelper

#endif