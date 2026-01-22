//
// Created by Yancey on 2024-7-13.
//

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