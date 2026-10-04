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

#include <chelper/node/NodeWithType.h>
#include <chelper/parser/ErrorReasonList.h>
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

    // AST 数组保存实际分配资源，释放时无需经过线程路由；资源仍由上下文持有。
    inline std::pmr::memory_resource *getASTMemoryResource() noexcept {
        return CPackMemoryRouter::getAllocationResource();
    }

    class ASTNode {
    public:
        ASTNodeMode::ASTNodeMode mode;
        //AST节点ID；与 mode 相邻，避免两个字节字段分别产生对齐填充。
        ASTNodeId::ASTNodeId id;
        //一个Node可能会生成多个ASTNode，这些ASTNode使用id进行区分
        Node::NodeWithType node;
        //子节点为AND类型和OR类型特有
        std::pmr::vector<ASTNode> childNodes;
        TokensView tokens;
        //不要直接用这个，这里不包括ID错误，只有结构错误，应该用getErrorReason()
        ErrorReasonList errorReasons;
        //哪个节点最好，OR类型特有，获取颜色和生成命令格式文本的时候使用
        size_t whichBest;

        ASTNode(ASTNodeMode::ASTNodeMode mode,
                const Node::NodeWithType &node,
                std::pmr::vector<ASTNode> &&childNodes,
                TokensView tokens,
                ErrorReasonList errorReasons,
                ASTNodeId::ASTNodeId id,
                size_t whichBest = -1)
            : mode(mode),
              id(id),
              node(node),
              childNodes(std::move(childNodes)),
              tokens(std::move(tokens)),
              errorReasons(std::move(errorReasons)),
              whichBest(whichBest) {}

        ASTNode(const ASTNode &other);
        ASTNode(ASTNode &&) noexcept = default;
        ASTNode &operator=(const ASTNode &) = default;
        ASTNode &operator=(ASTNode &&) = default;

        // initializer_list 的元素是 const，{std::move(node)} 仍会深拷贝 AST。
        // 显式构造子节点数组，保证调用方交出子树的所有权。
        template<class... Nodes>
        static std::pmr::vector<ASTNode> children(Nodes &&...nodes) {
            static_assert((std::is_same_v<Nodes, ASTNode> && ...));
            std::pmr::vector<ASTNode> result(getASTMemoryResource());
            result.reserve(sizeof...(Nodes));
            (result.emplace_back(std::forward<Nodes>(nodes)), ...);
            return result;
        }

        static ASTNode simpleNode(const Node::NodeWithType &node,
                                  TokensView tokens,
                                  const std::shared_ptr<ErrorReason> &errorReason = nullptr,
                                  const ASTNodeId::ASTNodeId &id = ASTNodeId::NONE);

        static ASTNode andNode(const Node::NodeWithType &node,
                               std::pmr::vector<ASTNode> &&childNodes,
                               TokensView tokens,
                               const std::shared_ptr<ErrorReason> &errorReason = nullptr,
                               const ASTNodeId::ASTNodeId &id = ASTNodeId::NONE);

        static ASTNode orNode(const Node::NodeWithType &node,
                              std::pmr::vector<ASTNode> &&childNodes,
                              const TokensView *tokens,
                              const char16_t *errorReason = nullptr,
                              const ASTNodeId::ASTNodeId &id = ASTNodeId::NONE);

        static ASTNode orNode(const Node::NodeWithType &node,
                              std::pmr::vector<ASTNode> &&childNodes,
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

        [[nodiscard]] bool isAllSpaceError() const {
            return isError() && std::ranges::all_of(errorReasons, [](const auto &item) {
                       return item->level == ErrorReasonLevel::REQUIRE_SPACE;
                   });
        }

        [[nodiscard]] const ASTNode &getBestNode() const {
#if CHelperDebug
            if (mode != ASTNodeMode::OR) {
                throw std::runtime_error("invalid mode");
            }
#endif
            return childNodes[whichBest];
        }
    };

}// namespace CHelper
