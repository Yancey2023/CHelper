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
#include <chelper/parser/ErrorReasonFactory.h>

namespace CHelper {

    ASTNode::ASTNode(const ASTNode &other)
        : mode(other.mode),
          id(other.id),
          node(other.node),
          childNodes(other.childNodes, getASTMemoryResource()),
          tokens(other.tokens),
          errorReasons(other.errorReasons, getASTMemoryResource()),
          whichBest(other.whichBest) {}

    ASTNode ASTNode::simpleNode(const Node::NodeWithType &node,
                                TokensView tokens,
                                std::shared_ptr<ErrorReason> errorReason,
                                const ASTNodeId::ASTNodeId &id) {
        ErrorReasonList errorReasons(getASTMemoryResource());
        if (errorReason != nullptr) [[likely]] {
            errorReasons.push_back(std::move(errorReason));
        }
        return {ASTNodeMode::NONE, node, std::pmr::vector<ASTNode>(getASTMemoryResource()),
                std::move(tokens), std::move(errorReasons), id};
    }

    ASTNode ASTNode::andNode(const Node::NodeWithType &node,
                             std::pmr::vector<ASTNode> &&childNodes,
                             TokensView tokens,
                             std::shared_ptr<ErrorReason> errorReason,
                             const ASTNodeId::ASTNodeId &id) {
        if (errorReason != nullptr) [[unlikely]] {
            ErrorReasonList errorReasons(getASTMemoryResource());
            errorReasons.push_back(std::move(errorReason));
            return {ASTNodeMode::AND, node, std::move(childNodes), std::move(tokens),
                    std::move(errorReasons), id};
        }
        for (const auto &item: childNodes) {
            if (item.isError()) [[unlikely]] {
                return {ASTNodeMode::AND, node, std::move(childNodes), std::move(tokens),
                        ErrorReasonList(item.errorReasons, getASTMemoryResource()), id};
            }
        }
        return {ASTNodeMode::AND, node, std::move(childNodes), std::move(tokens),
                ErrorReasonList(getASTMemoryResource()), id};
    }

    ASTNode ASTNode::orNode(const Node::NodeWithType &node,
                            std::pmr::vector<ASTNode> &&childNodes,
                            const TokensView *tokens,
                            const char16_t *errorReason,
                            const ASTNodeId::ASTNodeId &id) {
#if CHelperDebug
        //正常情况下OR节点不会有空的子节点，非法CPack数据应当在加载阶段被CPack::validate拦截，
        //这里是Debug模式下的最后一道防线，防止访问childNodes[whichBest]时越界
        if (childNodes.empty()) [[unlikely]] {
            throw std::runtime_error("OR node must have at least one child node");
        }
#endif
        // 单分支无需选优；最多一个诊断时也无需进行列表去重。
        if (childNodes.size() == 1 && childNodes.front().errorReasons.size() <= 1) {
            ErrorReasonList errorReasons(childNodes.front().errorReasons, getASTMemoryResource());
            TokensView selectedTokens = tokens == nullptr ? childNodes.front().tokens : *tokens;
            return {ASTNodeMode::OR, node, std::move(childNodes), std::move(selectedTokens), std::move(errorReasons), id, 0};
        }
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
        ErrorReasonList errorReasons(getASTMemoryResource());
        size_t whichBest = 0;
        if (errorCount == 0) [[unlikely]] {
            // 从没有错误的内容中找出最好的节点
            size_t end = 0;
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
            errorReasons = {ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, tokens1, errorReason)};
        }
        return {ASTNodeMode::OR, node, std::move(childNodes), std::move(tokens1), std::move(errorReasons), id, whichBest};
    }

    ASTNode ASTNode::orNode(const Node::NodeWithType &node,
                            std::pmr::vector<ASTNode> &&childNodes,
                            const TokensView &tokens,
                            const char16_t *errorReason,
                            const ASTNodeId::ASTNodeId &id) {
        return orNode(node, std::move(childNodes), &tokens, errorReason, id);
    }

}// namespace CHelper
