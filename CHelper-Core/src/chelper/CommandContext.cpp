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

#include <chelper/CommandContext.h>
#include <chelper/auto_suggestion/AutoSuggestion.h>
#include <chelper/command_structure/CommandStructure.h>
#include <chelper/linter/Linter.h>
#include <chelper/parameter_hint/ParameterHint.h>
#include <chelper/parser/Parser.h>
#include <chelper/syntax_highlight/SyntaxHighlight.h>

namespace CHelper {

    namespace {

        bool hasSemanticContent(const ASTNode &astNode) {
            if (astNode.isError()) {
                return false;
            }
            bool result = false;
            astNode.tokens.forEach([&result](const Token &token) {
                if (token.type != TokenType::SPACE && token.type != TokenType::LF) {
                    result = true;
                }
            });
            return result;
        }

        size_t countChildNodes(const ASTNode &astNode) {
            if (astNode.mode == ASTNodeMode::OR) {
                if (astNode.whichBest >= astNode.childNodes.size()) [[unlikely]] {
                    return 0;
                }
                return countSemanticNodes(astNode.childNodes[astNode.whichBest]);
            }
            if (astNode.mode == ASTNodeMode::AND) {
                size_t result = 0;
                for (const auto &childNode: astNode.childNodes) {
                    result += countSemanticNodes(childNode);
                }
                return result;
            }
            return 0;
        }

        size_t countWrappedNode(const ASTNode &astNode) {
            if (astNode.childNodes.empty()) [[unlikely]] {
                return 0;
            }
            const ASTNode &currentNode = astNode.childNodes[0];
            size_t result;
            switch (currentNode.node.nodeTypeId) {
                case Node::NodeTypeId::COMMAND:
                case Node::NodeTypeId::REPEAT:
                    result = countSemanticNodes(currentNode);
                    break;
                case Node::NodeTypeId::LF:
                    result = 0;
                    break;
                default:
                    result = hasSemanticContent(currentNode) ? 1 : 0;
                    break;
            }
            for (size_t i = 1; i < astNode.childNodes.size(); ++i) {
                result += countSemanticNodes(astNode.childNodes[i]);
            }
            return result;
        }

    }// namespace

    size_t countSemanticNodes(const ASTNode &astNode) {
        if (astNode.node.nodeTypeId == Node::NodeTypeId::WRAPPED) {
            return countWrappedNode(astNode);
        }
        if (astNode.node.nodeTypeId == Node::NodeTypeId::COMMAND) {
            if (astNode.id == ASTNodeId::NODE_COMMAND_COMMAND_NAME) {
                return hasSemanticContent(astNode) ? 1 : 0;
            }
            // 未知命令只有命令名子节点，不能算作已经匹配的语义节点。
            if (astNode.id == ASTNodeId::NODE_COMMAND_COMMAND && astNode.childNodes.size() < 2) {
                return 0;
            }
        }
        return countChildNodes(astNode);
    }

    CommandContext::CommandContext(std::shared_ptr<const CPack> cpack, std::u16string command)
        : cpack(std::move(cpack)),
          memory(),
          memoryScope(memory.getResource(), this->cpack->getMemoryResource()),
          command(command.data(), command.size()),
          astNode(Parser::parse(this->command, *this->cpack)) {
        memoryScope.release();
    }

    CommandContext::~CommandContext() {
        memoryScope.prepareForDestruction();
    }

    const CPack &CommandContext::getCPack() const {
        return *cpack;
    }

    std::u16string_view CommandContext::getCommand() const {
        return command;
    }

    const ASTNode *CommandContext::getAstNode() const {
        return &astNode;
    }

    std::u16string CommandContext::getStructure() const {
        return CommandStructure::getStructure(astNode);
    }

    std::u16string CommandContext::getParamHint(size_t index) const {
        return ParameterHint::getParameterHint(astNode, index).value_or(u"未知");
    }

    std::vector<AutoSuggestion::Suggestion> CommandContext::getSuggestions(size_t index) const {
        return AutoSuggestion::getSuggestions(astNode, index).collect();
    }

    SyntaxHighlight::SyntaxResult CommandContext::getSyntaxResult() const {
        return SyntaxHighlight::getSyntaxResult(astNode);
    }

    std::vector<std::shared_ptr<ErrorReason>> CommandContext::getErrorReasons() const {
        return Linter::getErrorReasons(astNode);
    }

    size_t CommandContext::getNodeCount() const {
        return countSemanticNodes(astNode);
    }

    std::optional<std::pair<std::u16string, size_t>>
    CommandContext::applySuggestion(size_t index, size_t which) const {
        std::vector<AutoSuggestion::Suggestion> suggestions = getSuggestions(index);
        if (which >= suggestions.size()) [[unlikely]] {
            return std::nullopt;
        }
        const AutoSuggestion::Suggestion &suggestion = suggestions[which];
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
        // 和CHelperCore::onSuggestionClick一致：在命令末尾补全后重新解析，
        // 决定是否需要额外补一个空格，但不修改当前上下文的状态
        ASTNode newAstNode = Parser::parse(result.first, *cpack);
        if (suggestion.isAddSpace && newAstNode.isAllSpaceError()) [[likely]] {
            result.first.append(u" ");
            result.second++;
        }
        return result;
    }

}// namespace CHelper
