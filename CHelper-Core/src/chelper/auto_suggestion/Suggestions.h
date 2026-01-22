//
// Created by Yancey on 2024-03-12.
//

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
