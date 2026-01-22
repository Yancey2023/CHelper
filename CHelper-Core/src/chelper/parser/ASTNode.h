//
// Created by Yancey on 2023/12/15.
//

#pragma once

#ifndef CHELPER_ASTNODE_H
#define CHELPER_ASTNODE_H

#include <chelper/node/NodeWithType.h>
#include <chelper/parser/ErrorReason.h>
#include <pch.h>

namespace CHelper {

    namespace ASTNodeMode {
        enum ASTNodeMode : uint8_t {
            //没有向下的分支
            NONE,
            //有向下的分支，子节点为and关系
            AND,
            //有向下的分支，子节点为or关系
            OR
        };
    }// namespace ASTNodeMode

    namespace ASTNodeId {
        enum ASTNodeId : uint8_t {
            NONE,
            NODE_JSON_ALL_LIST,
            NODE_STRING_INNER,
            NODE_BLOCK_BLOCK_ID,
            NODE_BLOCK_BLOCK_STATE,
            NODE_BLOCK_BLOCK_AND_BLOCK_STATE,
            NODE_COMMAND_COMMAND_NAME,
            NODE_COMMAND_COMMAND,
            NODE_POSITION_POSITIONS,
            NODE_POSITION_POSITIONS_WITH_ERROR,
            NODE_RELATIVE_FLOAT_NUMBER,
            NODE_RELATIVE_FLOAT_WITH_ERROR,
        };
    }// namespace ASTNodeId

    class ASTNode {
    public:
        ASTNodeMode::ASTNodeMode mode;
        //一个Node可能会生成多个ASTNode，这些ASTNode使用id进行区分
        Node::NodeWithType node;
        //子节点为AND类型和OR类型特有
        std::vector<ASTNode> childNodes;
        TokensView tokens;
        //不要直接用这个，这里不包括ID错误，只有结构错误，应该用getErrorReason()
        std::vector<std::shared_ptr<ErrorReason>> errorReasons;
        //AST节点ID
        ASTNodeId::ASTNodeId id;
        //哪个节点最好，OR类型特有，获取颜色和生成命令格式文本的时候使用
        size_t whichBest;

        ASTNode(ASTNodeMode::ASTNodeMode mode,
                const Node::NodeWithType &node,
                std::vector<ASTNode> &&childNodes,
                TokensView tokens,
                const std::vector<std::shared_ptr<ErrorReason>> &errorReasons,
                ASTNodeId::ASTNodeId id,
                size_t whichBest = -1);

    public:
        static ASTNode simpleNode(const Node::NodeWithType &node,
                                  const TokensView &tokens,
                                  const std::shared_ptr<ErrorReason> &errorReason = nullptr,
                                  const ASTNodeId::ASTNodeId &id = ASTNodeId::NONE);

        static ASTNode andNode(const Node::NodeWithType &node,
                               std::vector<ASTNode> &&childNodes,
                               const TokensView &tokens,
                               const std::shared_ptr<ErrorReason> &errorReason = nullptr,
                               const ASTNodeId::ASTNodeId &id = ASTNodeId::NONE);

        // TODO 为什么当时我用的是char*，而不是std::shared_ptr<ErrorReason>

        static ASTNode orNode(const Node::NodeWithType &node,
                              std::vector<ASTNode> &&childNodes,
                              const TokensView *tokens,
                              const char16_t *errorReason = nullptr,
                              const ASTNodeId::ASTNodeId &id = ASTNodeId::NONE);

        static ASTNode orNode(const Node::NodeWithType &node,
                              std::vector<ASTNode> &&childNodes,
                              const TokensView &tokens,
                              const char16_t *errorReason = nullptr,
                              const ASTNodeId::ASTNodeId &id = ASTNodeId::NONE);

        //是否有结构错误（不包括ID错误）
        [[nodiscard]] bool isError() const {
            return !errorReasons.empty();
        }

        [[nodiscard]] bool hasChildNode() const {
            return !childNodes.empty();
        }

        [[nodiscard]] bool isAllSpaceError() const;

        [[nodiscard]] const ASTNode &getBestNode() const;
    };

}// namespace CHelper

#endif//CHELPER_ASTNODE_H
