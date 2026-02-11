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

#include <chelper/parser/TokensView.h>

namespace CHelper {

    TokensView::TokensView(const std::shared_ptr<LexerResult> &lexerResult, size_t start, size_t end)
        : lexerResult(lexerResult),
          start(start),
          end(end) {
        startIndex = lexerResult->getIndex(start);
        endIndex = lexerResult->getIndex(end);
        cacheString = {lexerResult->content.c_str() + startIndex, endIndex - startIndex};
#ifdef CHelperDebug
        if (start > end) [[unlikely]] {
            throw std::runtime_error(fmt::format("TokensView: wrong range: ({}, {})", start, end));
        }
#endif
    }

    [[nodiscard]] bool TokensView::isEmpty() const {
        return start >= end;
    }

    [[nodiscard]] bool TokensView::hasValue() const {
        return start < end;
    }

    const Token &TokensView::operator[](size_t which) const {
        return lexerResult->allTokens[start + which];
    }

    [[nodiscard]] size_t TokensView::size() const {
        return end - start;
    }

    [[nodiscard]] bool TokensView::isAllSpace() const {
        return std::all_of(lexerResult->allTokens.begin() + static_cast<std::u16string::difference_type>(start),
                           lexerResult->allTokens.begin() + static_cast<std::u16string::difference_type>(end),
                           [](const auto &item) {
                               return item.type == TokenType::SPACE;
                           });
    }

    void TokensView::forEach(std::function<void(const Token &token)> function) const {
        std::for_each(lexerResult->allTokens.begin() + static_cast<std::u16string::difference_type>(start),
                      lexerResult->allTokens.begin() + static_cast<std::u16string::difference_type>(end),
                      std::move(function));
    }

    [[nodiscard]] std::u16string_view TokensView::string() const {
        return cacheString;
    }

}// namespace CHelper