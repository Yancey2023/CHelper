//
// Created by Yancey on 2024-08-07.
//

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
