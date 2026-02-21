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

#include <chelper/node/CommandNode.h>
#include <chelper/parser/ASTNode.h>

namespace CHelper {

    ASTNode::ASTNode(ASTNodeMode::ASTNodeMode mode,
                     const Node::NodeWithType &node,
                     std::vector<ASTNode> &&childNodes,
                     TokensView tokens,
                     const std::vector<std::shared_ptr<ErrorReason>> &errorReasons,
                     ASTNodeId::ASTNodeId id,
                     size_t whichBest)
        : mode(mode),
          node(node),
          childNodes(std::move(childNodes)),
          tokens(std::move(tokens)),
          errorReasons(errorReasons),
          id(id),
          whichBest(whichBest) {}

    ASTNode ASTNode::simpleNode(const Node::NodeWithType &node,
                                const TokensView &tokens,
                                const std::shared_ptr<ErrorReason> &errorReason,
                                const ASTNodeId::ASTNodeId &id) {
        std::vector<std::shared_ptr<ErrorReason>> errorReasons;
        if (errorReason != nullptr) [[likely]] {
            errorReasons.push_back(errorReason);
        }
        return {ASTNodeMode::NONE, node, {}, tokens, errorReasons, id};
    }

    ASTNode ASTNode::andNode(const Node::NodeWithType &node,
                             std::vector<ASTNode> &&childNodes,
                             const TokensView &tokens,
                             const std::shared_ptr<ErrorReason> &errorReason,
                             const ASTNodeId::ASTNodeId &id) {
        if (errorReason != nullptr) [[unlikely]] {
            return {ASTNodeMode::AND, node, std::move(childNodes), tokens, {errorReason}, id};
        }
        for (const auto &item: childNodes) {
            if (item.isError()) [[unlikely]] {
                return {ASTNodeMode::AND, node, std::move(childNodes), tokens, item.errorReasons, id};
            }
        }
        return {ASTNodeMode::AND, node, std::move(childNodes), tokens, {}, id};
    }

    ASTNode ASTNode::orNode(const Node::NodeWithType &node,
                            std::vector<ASTNode> &&childNodes,
                            const TokensView *tokens,
                            const char16_t *errorReason,
                            const ASTNodeId::ASTNodeId &id) {
        // 收集错误的节点数，如果有节点没有错就设为0
        size_t errorCount = 0;
        for (const auto &item: childNodes) {
            if (item.isError()) [[likely]] {
                errorCount++;
            } else {
                errorCount = 0;
                break;
            }
        }
        std::vector<std::shared_ptr<ErrorReason>> errorReasons;
        size_t whichBest = 0;
        if (errorCount == 0) [[unlikely]] {
            // 从没有错误的内容中找出最好的节点
            size_t end = 0;
            errorReasons.clear();
            for (size_t i = 0; i < childNodes.size(); ++i) {
                const ASTNode &item = childNodes[i];
                if (!item.isError() && end < item.tokens.end) {
                    whichBest = i;
                    end = item.tokens.end;
                }
            }
            errorCount++;
        } else {
            // 收集错误原因，尝试找出错误节点中最好的节点
            size_t start = 0;
            for (size_t i = 0; i < childNodes.size(); ++i) {
                const ASTNode &item = childNodes[i];
                for (const auto &item2: item.errorReasons) {
                    if (start > item2->start) [[likely]] {
                        continue;
                    }
                    bool isAdd = true;
                    if (start < item2->start) [[likely]] {
                        start = item2->start;
                        whichBest = i;
                        errorReasons.clear();
                    } else {
                        for (const auto &item3: errorReasons) {
                            if (*item2 == *item3) [[unlikely]] {
                                isAdd = false;
                                break;
                            }
                        }
                    }
                    if (isAdd) [[likely]] {
                        errorReasons.push_back(item2);
                    }
                }
            }
        }
        TokensView tokens1 = tokens == nullptr ? childNodes[whichBest].tokens : *tokens;
        if (errorCount > 1 && errorReason != nullptr) [[unlikely]] {
            errorReasons = {ErrorReason::contentError(tokens1, errorReason)};
        }
        return {ASTNodeMode::OR, node, std::move(childNodes), tokens1, errorReasons, id, whichBest};
    }

    ASTNode ASTNode::orNode(const Node::NodeWithType &node,
                            std::vector<ASTNode> &&childNodes,
                            const TokensView &tokens,
                            const char16_t *errorReason,
                            const ASTNodeId::ASTNodeId &id) {
        return orNode(node, std::move(childNodes), &tokens, errorReason, id);
    }

    bool ASTNode::isAllSpaceError() const {
        return isError() && std::ranges::all_of(errorReasons, [](const auto &item) {
                   return item->level == ErrorReasonLevel::REQUIRE_SPACE;
               });
    }

    [[nodiscard]] const ASTNode &ASTNode::getBestNode() const {
#ifdef CHelperDebug
        if (mode != ASTNodeMode::OR) {
            throw std::runtime_error("invalid mode");
        }
#endif
        return childNodes[whichBest];
    }

}// namespace CHelper
