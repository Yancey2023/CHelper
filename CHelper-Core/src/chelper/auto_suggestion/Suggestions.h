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

#include <chelper/auto_suggestion/Suggestion.h>
#include <chelper/util/HashContainer.h>
#include <concepts>
#include <functional>

namespace CHelper::AutoSuggestion {

    class Suggestions {
    public:
        std::vector<Suggestion> spaceSuggestions;
        std::vector<Suggestion> symbolSuggestions;
        std::vector<Suggestion> literalSuggestions;
        std::vector<Suggestion> idSuggestions;

    private:
        DenseSet<XXH64_hash_t, XXHashDigest> seenSuggestionHashes;

        void addSuggestion(std::vector<Suggestion> &suggestions, Suggestion &&suggestion);

        // 非回调部分留在 .cpp，避免模板中的 reserve 实例改变其他热函数的内联决策。
        void reserveForCombine(const Suggestions &suggestions);

    public:
        void addSpaceSuggestion(Suggestion &&suggestion);

        void addSymbolSuggestion(Suggestion &&suggestion);

        void addLiteralSuggestion(Suggestion &&suggestion);

        void reserveIdSuggestion(size_t size);

        void addIdSuggestion(Suggestion &&suggestion);

        template<class Function>
            requires requires(Function &function, Suggestion &suggestion) {
                { std::invoke(function, suggestion) } -> std::convertible_to<bool>;
            }
        // MSVC 展开合并循环后会使 NodeOptional 处理器超出内联预算，
        // 给高频节点分发增加调用。保留原来的合并调用边界，回调仍在模板实例内优化。
#if defined(_MSC_VER) && !defined(__clang__)
        __declspec(noinline)
#endif
        void combine(Suggestions &suggestions, Function &&function) {
            reserveForCombine(suggestions);
            for (auto &item: suggestions.spaceSuggestions) {
                if (std::invoke(function, item)) {
                    addSuggestion(spaceSuggestions, std::move(item));
                }
            }
            for (auto &item: suggestions.symbolSuggestions) {
                if (std::invoke(function, item)) {
                    addSuggestion(symbolSuggestions, std::move(item));
                }
            }
            for (auto &item: suggestions.literalSuggestions) {
                if (std::invoke(function, item)) {
                    addSuggestion(literalSuggestions, std::move(item));
                }
            }
            for (auto &item: suggestions.idSuggestions) {
                if (std::invoke(function, item)) {
                    addSuggestion(idSuggestions, std::move(item));
                }
            }
        }

        std::vector<Suggestion> collect();
    };

}// namespace CHelper::AutoSuggestion
