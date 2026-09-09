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

#include <chelper/FragmentContext.h>
#include <chelper/auto_suggestion/AutoSuggestion.h>
#include <chelper/linter/Linter.h>
#include <chelper/node/NodeInitialization.h>
#include <chelper/parser/Parser.h>

namespace CHelper {

    std::unique_ptr<FragmentContext> FragmentContext::createTargetSelector(
            std::shared_ptr<const CPack> cpack, std::u16string content) {
        auto ctx = std::unique_ptr<FragmentContext>(new FragmentContext());
        ctx->cpack = std::move(cpack);
        ctx->content = std::move(content);
        // 装配选择器根节点（包装 OR 引用 cpack.targetSelectorData，见 NodeInitialization<NodeTargetSelector>）
        Node::initNode(Node::NodeWithType(ctx->selectorNode), *ctx->cpack);
        ctx->root = Node::NodeWithType(ctx->selectorNode);
        ctx->astNode = std::make_unique<ASTNode>(Parser::parse(ctx->content, ctx->root));
        return ctx;
    }

    std::unique_ptr<FragmentContext> FragmentContext::createId(
            std::shared_ptr<const CPack> cpack, std::string key, std::u16string content) {
        auto ctx = std::unique_ptr<FragmentContext>(new FragmentContext());
        ctx->cpack = std::move(cpack);
        ctx->content = std::move(content);
        // ID 表节点：key 引用合成 CPack 候选表（如 translate）；initNode 解析 key→customContents
        ctx->idNode.key = std::move(key);
        Node::initNode(Node::NodeWithType(ctx->idNode), *ctx->cpack);
        ctx->root = Node::NodeWithType(ctx->idNode);
        ctx->astNode = std::make_unique<ASTNode>(Parser::parse(ctx->content, ctx->root));
        return ctx;
    }

    const CPack &FragmentContext::getCPack() const {
        return *cpack;
    }

    const std::u16string &FragmentContext::getContent() const {
        return content;
    }

    const ASTNode *FragmentContext::getAstNode() const {
        return astNode.get();
    }

    std::vector<AutoSuggestion::Suggestion> FragmentContext::getSuggestions(size_t index) const {
        return AutoSuggestion::getSuggestions(*astNode, index).collect();
    }

    std::vector<std::shared_ptr<ErrorReason>> FragmentContext::getErrorReasons() const {
        return Linter::getErrorReasons(*astNode);
    }

    std::optional<std::pair<std::u16string, size_t>>
    FragmentContext::applySuggestion(size_t index, size_t which) const {
        std::vector<AutoSuggestion::Suggestion> suggestions = getSuggestions(index);
        if (which >= suggestions.size()) [[unlikely]] {
            return std::nullopt;
        }
        const AutoSuggestion::Suggestion &suggestion = suggestions[which];
        std::u16string_view before = astNode->tokens.string();
        if (suggestion.content->name == u" " && (suggestion.start == 0 || before[suggestion.start - 1] == u' ')) {
            return {{std::u16string(before), suggestion.start}};
        }
        std::pair<std::u16string, size_t> result = {
                std::u16string().append(before.substr(0, suggestion.start)).append(suggestion.content->name).append(before.substr(suggestion.end)),
                suggestion.start + suggestion.content->name.length()};
        if (suggestion.end != before.length()) [[unlikely]] {
            return result;
        }
        // 与 CommandContext 一致：命令末尾补全后重新解析，决定是否补空格
        ASTNode newAstNode = Parser::parse(result.first, root);
        if (suggestion.isAddSpace && newAstNode.isAllSpaceError()) [[likely]] {
            result.first.append(u" ");
            result.second++;
        }
        return result;
    }

}// namespace CHelper
