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

#include <chelper/lexer/Token.h>

namespace CHelper {

    namespace TokenType {

        std::u16string getName(TokenType tokenType) {
            switch (tokenType) {
                case STRING:
                    return u"字符串类型";
                case NUMBER:
                    return u"数字类型";
                case SYMBOL:
                    return u"符号类型";
                case SPACE:
                    return u"空格";
                case LF:
                    return u"换行符类型";
                default:
                    return u"未知类型";
            }
        }

    }// namespace TokenType

    Token::Token(TokenType::TokenType type,
                 const size_t &pos,
                 const std::u16string_view &content)
        : type(type),
          pos(pos),
          content(content) {}

    size_t Token::getStartIndex() const {
        return pos;
    }

    size_t Token::getEndIndex() const {
        return pos + content.size();
    }

}// namespace CHelper