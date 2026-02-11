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

#include <chelper/syntax_highlight/SyntaxResult.h>

namespace CHelper::SyntaxHighlight {

    SyntaxResult::SyntaxResult(const std::u16string_view &str)
        : str(str),
          tokenTypes(str.length(), SyntaxTokenType::UNKNOWN) {}

    void SyntaxResult::update(size_t index, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
#ifdef CHelperDebug
        if (tokenTypes[index] != SyntaxTokenType::UNKNOWN && str[index] != '[' && str[index] != ']' && str[index] != '{' && str[index] != '}') {
            SPDLOG_ERROR("replace syntax");
        }
        if (index > str.length()) {
            SPDLOG_ERROR("index out of range");
            return;
        }
#endif
        tokenTypes[index] = syntaxTokenType;
    }

    void SyntaxResult::update(size_t start, size_t end, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
#ifdef CHelperDebug
        for (size_t i = start; i < end; i++) {
            if (tokenTypes[i] != SyntaxTokenType::UNKNOWN && str[i] != '[' && str[i] != ']' && str[i] != '{' && str[i] != '}') {
                SPDLOG_ERROR("replace syntax");
            }
        }
        if (start > str.length() || end > str.length()) {
            SPDLOG_ERROR("index out of range");
            return;
        }
        if (start > end) {
            SPDLOG_ERROR("start should less than end");
            return;
        }
#endif
        std::fill(tokenTypes.begin() + static_cast<std::u16string::difference_type>(start),
                  tokenTypes.begin() + static_cast<std::u16string::difference_type>(end),
                  syntaxTokenType);
    }
    void SyntaxResult::update(const TokensView &tokensView, SyntaxTokenType::SyntaxTokenType syntaxTokenType) {
        update(tokensView.startIndex, tokensView.endIndex, syntaxTokenType);
    }

}// namespace CHelper::SyntaxHighlight
