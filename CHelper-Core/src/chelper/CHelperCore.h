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

#ifndef CHELPER_CHELPERCORE_H
#define CHELPER_CHELPERCORE_H

#include "old2new/Old2New.h"
#include <chelper/auto_suggestion/Suggestion.h>
#include <chelper/parser/ASTNode.h>
#include <chelper/resources/CPack.h>
#include <chelper/syntax_highlight/SyntaxResult.h>
#include <pch.h>

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
