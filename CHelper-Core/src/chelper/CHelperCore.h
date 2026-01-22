//
// Created by Yancey on 2024/2/24.
//

#pragma once

#ifndef CHELPER_CHELPERCORE_H
#define CHELPER_CHELPERCORE_H

#include "old2new/Old2New.h"
#include <chelper/auto_suggestion/Suggestion.h>
#include <chelper/parser/ASTNode.h>
#include <chelper/resources/CPack.h>
#include <chelper/syntax_highlight/SyntaxResult.h>
#include <../../pch.h>

namespace CHelper {

    class CHelperCore {
    private:
        std::u16string input;
        size_t index = 0;
        std::unique_ptr<CPack> cpack;
        ASTNode astNode;
        std::shared_ptr<std::vector<AutoSuggestion::Suggestion>> suggestions;

    public:
        CHelperCore(std::unique_ptr<CPack> cpack, ASTNode astNode);

        static CHelperCore *create(const std::function<std::unique_ptr<CPack>()> &getCPack);

#ifndef CHELPER_NO_FILESYSTEM
        static CHelperCore *createByDirectory(const std::filesystem::path &cpackPath);

        static CHelperCore *createByJson(const std::filesystem::path &cpackPath);

        static CHelperCore *createByBinary(const std::filesystem::path &cpackPath);
#endif

        void onTextChanged(const std::u16string &content, size_t index);

        void onSelectionChanged(size_t index0);

        [[nodiscard]] const CPack &getCPack() const;

        [[nodiscard]] const ASTNode *getAstNode() const;

        [[nodiscard]] std::u16string getParamHint() const;

        [[nodiscard]] std::vector<std::shared_ptr<ErrorReason>> getErrorReasons() const;

        std::vector<AutoSuggestion::Suggestion> *getSuggestions();

        [[nodiscard]] std::u16string getStructure() const;

        [[nodiscard]] SyntaxHighlight::SyntaxResult getSyntaxResult() const;

        [[nodiscard]] std::optional<std::pair<std::u16string, size_t>> onSuggestionClick(size_t which);

        static std::u16string old2new(const Old2New::BlockFixData &blockFixData, const std::u16string &old);
    };

}// namespace CHelper

#endif//CHELPER_CHELPERCORE_H
