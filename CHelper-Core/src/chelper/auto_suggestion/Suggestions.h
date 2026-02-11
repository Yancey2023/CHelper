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

#ifndef CHELPER_SUGGESTIONS_H
#define CHELPER_SUGGESTIONS_H

#include <chelper/auto_suggestion/Suggestion.h>

namespace CHelper::AutoSuggestion {

    class Suggestions {
    public:
        std::vector<Suggestion> spaceSuggestions;
        std::vector<Suggestion> symbolSuggestions;
        std::vector<Suggestion> literalSuggestions;
        std::vector<Suggestion> idSuggestions;

    private:
        std::unordered_set<XXH64_hash_t> seenSuggestionHashes;

        void addSuggestion(std::vector<Suggestion> &suggestions, Suggestion &&suggestion);

    public:
        void addSpaceSuggestion(Suggestion &&suggestion);

        void addSymbolSuggestion(Suggestion &&suggestion);

        void addLiteralSuggestion(Suggestion &&suggestion);

        void reserveIdSuggestion(size_t size);

        void addIdSuggestion(Suggestion &&suggestion);

        void combine(Suggestions &suggestions, const std::function<bool(Suggestion &suggestion)> &function);

        std::vector<Suggestion> collect();
    };

}// namespace CHelper::AutoSuggestion

#endif//CHELPER_SUGGESTIONS_H
