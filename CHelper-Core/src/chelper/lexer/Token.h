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

#ifndef CHELPER_TOKEN_H
#define CHELPER_TOKEN_H

#include <pch.h>

namespace CHelper {

    namespace TokenType {

        enum TokenType : uint8_t {
            STRING,
            NUMBER,
            SYMBOL,
            SPACE,
            LF
        };

        std::u16string getName(TokenType tokenType);

    }// namespace TokenType

    class Token {
    public:
        const TokenType::TokenType type;
        const size_t pos;
        const std::u16string_view content;

        Token(TokenType::TokenType type, const size_t &pos, const std::u16string_view &content);

        [[nodiscard]] size_t getStartIndex() const;

        [[nodiscard]] size_t getEndIndex() const;
    };

}// namespace CHelper

CODEC_ENUM(CHelper::TokenType::TokenType, uint8_t)

#endif//CHELPER_TOKEN_H
