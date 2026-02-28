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

#include <chelper/CHelperCore.h>
#include <chelper/auto_suggestion/AutoSuggestion.h>
#include <chelper/command_structure/CommandStructure.h>
#include <chelper/linter/Linter.h>
#include <chelper/parameter_hint/ParameterHint.h>
#include <chelper/parser/Parser.h>
#include <chelper/syntax_highlight/SyntaxHighlight.h>

namespace CHelper {

    CHelperCore::CHelperCore(std::unique_ptr<CPack> cpack, ASTNode astNode)
        : cpack(std::move(cpack)),
          astNode(std::move(astNode)) {}

    CHelperCore *CHelperCore::create(const std::function<std::unique_ptr<CPack>()> &getCPack) {
        try {
#if SPDLOG_ACTIVE_LEVEL <= SPDLOG_LEVEL_INFO
            const auto start = std::chrono::high_resolution_clock::now();
#endif
            std::unique_ptr<CPack> cPack = getCPack();
#if SPDLOG_ACTIVE_LEVEL <= SPDLOG_LEVEL_INFO
            const auto end = std::chrono::high_resolution_clock::now();
#endif
            SPDLOG_INFO("CPack load successfully ({})", FORMAT_ARG(std::chrono::duration_cast<std::chrono::milliseconds>(end - start)));
            ASTNode astNode = Parser::parse(u"", *cPack);
            return new CHelperCore(std::move(cPack), std::move(astNode));
        } catch (const std::exception &e) {
            SPDLOG_ERROR("CPack load failed");
            CHelper::Profile::printAndClear(e);
            return nullptr;
        }
    }

#ifndef CHELPER_NO_FILESYSTEM
    CHelperCore *CHelperCore::createByDirectory(const std::filesystem::path &cpackPath) {
        return create([&cpackPath]() {
            return CPack::createByDirectory(cpackPath);
        });
    }

    CHelperCore *CHelperCore::createByJson(const std::filesystem::path &cpackPath) {
        return create([&cpackPath]() {
            return CPack::createByJson(serialization::get_json_from_file(cpackPath));
        });
    }

    CHelperCore *CHelperCore::createByBinary(const std::filesystem::path &cpackPath) {
        return create([&cpackPath]() {
            // 检查文件名后缀
            std::string fileType = ".cpack";
            std::string cpackPathStr = cpackPath.string();
            if (cpackPathStr.size() < fileType.size() || cpackPathStr.substr(cpackPathStr.length() - fileType.size()) != fileType) [[unlikely]] {
                Profile::push("error file type -> {}", FORMAT_ARG(cpackPathStr));
                throw std::runtime_error("error file type");
            }
            // 打开文件
            std::ifstream is(cpackPath, std::ios::binary);
            if (!is.is_open()) [[unlikely]] {
                Profile::push("fail to read file -> {}", FORMAT_ARG(cpackPathStr));
                throw std::runtime_error("fail to read file");
            }
            // 读取文件
            std::unique_ptr<CPack> result = CPack::createByBinary(is);
            // 检查文件是否读完
            if (!is.eof()) [[unlikely]] {
                char ch;
                is.read(&ch, 1);
                if (is.gcount() > 0) [[unlikely]] {
                    Profile::push("file is not read completed -> {}", FORMAT_ARG(cpackPathStr));
                    throw std::runtime_error("file is not read completed");
                }
            }
            // 关闭文件
            is.close();
            return result;
        });
    }
#endif

    void CHelperCore::onTextChanged(const std::u16string &content, size_t index0) {
        if (input != content) [[likely]] {
            input = content;
            astNode = Parser::parse(input, *cpack);
            suggestions = nullptr;
        }
        onSelectionChanged(index0);
    }

    void CHelperCore::onSelectionChanged(size_t index0) {
        if (index != index0) [[likely]] {
            index = index0;
            suggestions = nullptr;
        }
    }

    [[nodiscard]] const CPack &CHelperCore::getCPack() const {
        return *cpack;
    }

    [[nodiscard]] const ASTNode *CHelperCore::getAstNode() const {
        return &astNode;
    }

    [[nodiscard]] std::u16string CHelperCore::getParamHint() const {
        return ParameterHint::getParameterHint(astNode, index).value_or(u"未知");
    }

    [[nodiscard]] std::vector<std::shared_ptr<ErrorReason>> CHelperCore::getErrorReasons() const {
        return Linter::getErrorReasons(astNode);
    }

    std::vector<AutoSuggestion::Suggestion> *CHelperCore::getSuggestions() {
        if (suggestions == nullptr) [[likely]] {
            suggestions = std::make_shared<std::vector<AutoSuggestion::Suggestion>>(AutoSuggestion::getSuggestions(astNode, index).collect());
        }
        return suggestions.get();
    }

    [[nodiscard]] std::u16string CHelperCore::getStructure() const {
        return CommandStructure::getStructure(astNode);
    }

    [[nodiscard]] SyntaxHighlight::SyntaxResult CHelperCore::getSyntaxResult() const {
        return SyntaxHighlight::getSyntaxResult(astNode);
    }

    std::optional<std::pair<std::u16string, size_t>> CHelperCore::onSuggestionClick(size_t which) {
        if (suggestions == nullptr || which >= suggestions->size()) [[unlikely]] {
            return std::nullopt;
        }
        const auto &suggestion = (*suggestions)[which];
        std::u16string_view before = astNode.tokens.string();
        if (suggestion.content->name == u" " && (suggestion.start == 0 || before[suggestion.start - 1] == u' ')) {
            return {{std::u16string(before), suggestion.start}};
        }
        std::pair<std::u16string, size_t> result = {
                std::u16string().append(before.substr(0, suggestion.start)).append(suggestion.content->name).append(before.substr(suggestion.end)),
                suggestion.start + suggestion.content->name.length()};
        if (suggestion.end != before.length()) [[unlikely]] {
            return result;
        }
        onTextChanged(result.first, result.second);
        if (suggestion.isAddSpace && astNode.isAllSpaceError()) [[likely]] {
            result.first.append(u" ");
            result.second++;
        }
        return result;
    }

    std::u16string CHelperCore::old2new(const Old2New::BlockFixData &blockFixData, const std::u16string &old) {
        return Old2New::old2new(blockFixData, old);
    }

}// namespace CHelper